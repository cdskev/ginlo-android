// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message.tasks

import android.os.AsyncTask
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.controller.message.MessageController
import eu.ginlo_apps.ginlo.controller.message.MessageDataResolver
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Message
import eu.ginlo_apps.ginlo.greendao.MessageDao
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.backend.BackendResponse
import eu.ginlo_apps.ginlo.model.constant.JsonConstants
import eu.ginlo_apps.ginlo.service.BackendService
import eu.ginlo_apps.ginlo.util.JsonUtil
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener
import eu.ginlo_apps.ginlo.util.MessageDaoHelper

class LoadPendingMessagesTask(
        private val accountGuid: String,
        private val messageController: MessageController,
        private val postExecutionListener: GenericActionListener<List<String>>
) : AsyncTask<MessageDao, Unit, Unit>() {
    private var isError: Boolean = false
    private val chatsToRefresh = mutableListOf<String>()

    override fun doInBackground(vararg params: MessageDao) {
        try {
            var nextMessages = getNextMessages()

            while (nextMessages.isNotEmpty()) {
                val guids =
                        nextMessages.filter { it.guid?.isNotBlank() == true && it.data == null }.joinToString(",") { it.guid }

                if (guids.isNotEmpty()) {
                    val messages = nextMessages.toMutableList()

                    BackendService.withSyncConnection(SimsMeApplication.getInstance())
                            .getMessages(guids) { response ->
                                handleBackendResponse(response, messages)
                            }
                }

                if (isError) {
                    break
                }

                nextMessages = getNextMessages()
            }
        } catch (e: LocalizedException) {
            LogUtil.w(LoadPendingMessagesTask::class.java.simpleName, "LoadPendingMessagesTask", e)
        }
    }

    override fun onPostExecute(nothing: Unit) {
        postExecutionListener.onSuccess(chatsToRefresh)
    }

    private fun handleBackendResponse(
            response: BackendResponse,
            messages: MutableList<Message>
    ) {
        if (response.isError) {
            isError = true
            val ident = response.msgException?.ident?.let { ";ident: $it" } ?: ""
            val errorMessage = "Backend Response Error: ${response.errorMessage}$ident"
            LogUtil.w(LoadPendingMessagesTask::class.java.simpleName, errorMessage)
        } else if (response.jsonArray != null) {
            response.jsonArray.filter { it.isJsonObject }.forEach {
                handleJsonElement(it, messages)
            }

            messages.forEach { messageController.deleteMessage(it) }
        }
    }

    private fun handleJsonElement(
            element: JsonElement,
            messages: MutableList<Message>
    ) {
        val messageEntry = getMessageJsonEntry(element.asJsonObject) ?: return

        val type = MessageDaoHelper.getMessageType(messageEntry.key)
        if (type == -1) return

        if (!messageEntry.value.isJsonObject) return

        val messageJsonObject = messageEntry.value.asJsonObject

        val guid = JsonUtil.stringFromJO(JsonConstants.GUID, messageJsonObject)

        if (guid.isBlank()) return

        val message = messages.firstOrNull { guid == it.guid } ?: return

        MessageDaoHelper.setMessageAttributes(message, messageJsonObject, type, accountGuid)

        MessageController.checkAndSetSendMessageProps(message, accountGuid)

        messageController.dao.update(message)

        val chatGuid = MessageDataResolver.getGuidForMessage(message)
        if (!chatsToRefresh.contains(chatGuid)) {
            chatsToRefresh.add(chatGuid)
        }

        messages.remove(message)
    }

    private fun getMessageJsonEntry(jo: JsonObject): Map.Entry<String, JsonElement>? =
            jo.entrySet()?.takeIf { it.isNotEmpty() }?.iterator()?.takeIf { it.hasNext() }?.next()

    private fun getNextMessages(): List<Message> {
        return try {
            val queryBuilder = messageController.dao.queryBuilder()

            queryBuilder.where(MessageDao.Properties.Data.isNull).whereOr(
                    MessageDao.Properties.Type.eq(Message.TYPE_GROUP),
                    MessageDao.Properties.Type.eq(Message.TYPE_GROUP_INVITATION),
                    MessageDao.Properties.Type.eq(Message.TYPE_PRIVATE)
            )
            queryBuilder.orderDesc(MessageDao.Properties.Id).limit(30)

            queryBuilder.build().forCurrentThread().list()
        } catch (e: IllegalStateException) {
            LogUtil.e(this.javaClass.getSimpleName(), e.message, e);
            emptyList()
        }
    }
}
