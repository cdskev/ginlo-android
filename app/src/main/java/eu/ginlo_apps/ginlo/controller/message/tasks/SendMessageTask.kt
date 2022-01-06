// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message.tasks

import android.content.res.Resources
import android.os.AsyncTask
import android.util.Base64
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.concurrent.manager.SerialExecutor
import eu.ginlo_apps.ginlo.controller.AccountController
import eu.ginlo_apps.ginlo.controller.PreferencesController
import eu.ginlo_apps.ginlo.controller.message.MessageController
import eu.ginlo_apps.ginlo.controller.message.contracts.OnSendMessageListener
import eu.ginlo_apps.ginlo.controller.message.models.AsyncTaskError
import eu.ginlo_apps.ginlo.controller.message.models.AsyncTaskResult
import eu.ginlo_apps.ginlo.controller.message.models.AsyncTaskSuccessful
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.backend.BaseMessageModel
import eu.ginlo_apps.ginlo.model.backend.GroupMessageModel
import eu.ginlo_apps.ginlo.model.backend.PrivateMessageModel
import eu.ginlo_apps.ginlo.util.GuidUtil
import java.nio.charset.StandardCharsets

internal class SendMessageTask(
    private val resources: Resources,
    private val preferencesController: PreferencesController,
    private val accountController: AccountController,
    private val messageController: MessageController,
    private val onSendMessageListener: OnSendMessageListener?,
    private val chatOverviewMessageListener: OnSendMessageListener?
) : AsyncTask<BaseMessageModel, Unit, AsyncTaskResult<BaseMessageModel>>() {

    fun startTask(vararg params: BaseMessageModel) {
        executeOnExecutor(SerialExecutor(), *params)
    }

    public override fun doInBackground(vararg params: BaseMessageModel): AsyncTaskResult<BaseMessageModel> {
        val messageModel = (if (params.isNotEmpty()) params[0] else null)
            ?: return AsyncTaskError(AsyncTaskResult.Error(resources.getString(R.string.service_tryAgainLater), null))

        if (LocalizedException.FILE_TO_BIG_AFTER_COMPRESSION == messageModel.errorIdentifier) {
            return AsyncTaskError(
                AsyncTaskResult.Error(
                    resources.getString(R.string.chat_file_open_error_file_size),
                    null
                )
            )
        }

        if (messageController.isSelfConversation(messageModel)) {
            return AsyncTaskError(AsyncTaskResult.Error(resources.getString(R.string.service_tryAgainLater), null))
        }

        var numSentMessages = preferencesController.sentMessageCount

        if (preferencesController.sentMessageCount >= 0) {
            preferencesController.sentMessageCount = ++numSentMessages
        }

        try {
            if (preferencesController.sendProfileName) {
                val account = accountController.account

                if (account != null && !account.name.isNullOrBlank()) {
                    val encodedNickname =
                        Base64.encodeToString(account.name.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                    if (messageModel is PrivateMessageModel) {
                        messageModel.from.nickname = encodedNickname
                    } else if (messageModel is GroupMessageModel) {
                        messageModel.from.nickname = encodedNickname
                    }
                }
            }
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name,"Failed in SendMessageTask: " + e.message, e)
        }

        if (messageModel.requestGuid == null) {
            messageModel.requestGuid = GuidUtil.generateRequestGuid()
        }

        if (messageModel.databaseId == null) {
            val id = messageController.persistSentMessage(messageModel, messageModel.isSystemMessage == true)

            if (id != -1L) {
                messageController.addMessageToActiveSendingMessages(id)
                messageModel.databaseId = id
            } else {
                return AsyncTaskError(AsyncTaskResult.Error(resources.getString(R.string.service_tryAgainLater), null))
            }
        }

        return AsyncTaskSuccessful(messageModel)
    }

    public override fun onPostExecute(result: AsyncTaskResult<BaseMessageModel>) {
        if (result is AsyncTaskError) {
            LogUtil.e(
                SendMessageTask::class.java.simpleName,
                "Error constructing message to be sent. Reason [${result.error.message}]"
            )
            onSendMessageListener?.onSendMessageError(null, result.error.message, null)
            chatOverviewMessageListener?.onSendMessageError(null, result.error.message, null)
        } else if (result is AsyncTaskSuccessful) {
            val messageModel = result.item
            if (messageModel.databaseId != null) {
                val message = messageController.getMessageById(messageModel.databaseId)
                onSendMessageListener?.onSaveMessageSuccess(message)
                chatOverviewMessageListener?.onSaveMessageSuccess(message)

                if (messageModel.isSystemMessage == null || !messageModel.isSystemMessage) {
                    SendMessageToBackendTask(resources, messageController, onSendMessageListener, messageModel)
                        .executeOnExecutor(SerialExecutor())
                }
            }
        }
    }
}
