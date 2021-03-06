// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message.tasks

import android.content.res.Resources
import android.os.AsyncTask
import com.google.gson.JsonArray
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.controller.AttachmentController
import eu.ginlo_apps.ginlo.controller.message.MessageController
import eu.ginlo_apps.ginlo.controller.message.contracts.OnSendMessageListener
import eu.ginlo_apps.ginlo.controller.message.models.AsyncTaskError
import eu.ginlo_apps.ginlo.controller.message.models.AsyncTaskResult
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Message
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.backend.BackendResponse
import eu.ginlo_apps.ginlo.model.backend.BaseMessageModel
import eu.ginlo_apps.ginlo.model.backend.ConfirmMessageSendModel
import eu.ginlo_apps.ginlo.model.constant.AppConstants
import eu.ginlo_apps.ginlo.service.IBackendService
import eu.ginlo_apps.ginlo.util.JsonUtil
import java.util.concurrent.CountDownLatch

class SendMessageToBackendTask internal constructor(
    private val resources: Resources,
    private val messageController: MessageController,
    private val sendListener: OnSendMessageListener?,
    private val messageModel: BaseMessageModel
) : AsyncTask<Unit, Unit, Unit>() {
    private var confirmMsgSendModel: ConfirmMessageSendModel? = null
    private var newAttachmentGuid: String? = null
    private var message: Message? = null
    private var result: AsyncTaskResult<Unit>? = null
    private val latch = CountDownLatch(1)

    private val onBackendResponseListener = object : IBackendService.OnBackendResponseListener {
        override fun onBackendResponse(response: BackendResponse) {
            with(response) {
                logResponse()

                message = messageController.getMessageByRequestGuid(messageModel.requestGuid) ?: return

                if (!isError) {
                    if (jsonArray != null && jsonArray.size() > 0) {
                        val confirmMessageSendModel = messageController.parseMessageSendModel(
                            jsonArray
                        )

                        if (confirmMessageSendModel?.isNotEmpty() == true) {
                            confirmMsgSendModel = confirmMessageSendModel[0]
                        }
                    }
                    // Clean up temp attachment files on success
                    if (message != null && message?.attachment?.isNotBlank() == true) {
                        LogUtil.d("SendMessageToBackendTask", "Going to delete temp attachment files for: " + message?.attachment)
                        AttachmentController.deleteTempBase64AttachmentFile(message?.attachment)
                        AttachmentController.deleteTempJsonAttachmentFile(message?.attachment)

                        // Check for a new attachmant guid given to us by the backend
                        val jo = JsonUtil.searchJsonObjectRecursive(jsonArray,"ConfirmMessageSend")
                        if (jo != null) {
                            val ja: JsonArray = jo.getAsJsonArray("attachments")
                            newAttachmentGuid = ja.get(0).asString
                            LogUtil.d("SendMessageToBackendTask",
                                "Got new attachment guid from the backend $newAttachmentGuid")
                        }
                    }

                } else {
                    result = toAsyncTaskError()

                    if (message != null && LocalizedException.ACCOUNT_UNKNOWN == (result as AsyncTaskError).error.code
                    ) {
                        //Receiver does not exists anymore
                        messageController.deleteMessage(message)
                    }
                }
            }

            latch.countDown()
        }
    }

    override fun doInBackground(vararg params: Unit) {
        if (messageModel.requestGuid?.isNotBlank() == true) {

            val couldSend = messageController.sendMessageToBackend(messageModel, onBackendResponseListener)

            if (!couldSend) {
                result =
                    AsyncTaskError(AsyncTaskResult.Error(resources.getString(R.string.service_tryAgainLater), null))
            }
        }
    }

    override fun onPostExecute(nothing: Unit?) {
        latch.await()

        if (result is AsyncTaskError) {
            if (message != null) {
                messageController.markAsError(message!!, true)
                messageController.removeMessageFromActiveSendingMessages(message!!.id)
                (result as AsyncTaskError).let {
                    sendListener?.onSendMessageError(message, it.error.message, it.error.code)
                }
            }
        } else {
            if (confirmMsgSendModel != null) {
                var notSendCount = 0

                message?.let {
                    it.guid = confirmMsgSendModel!!.guid

                    // Attachment must be renamed to match guid given by the backend.
                    if(newAttachmentGuid != null) {
                        // KS: Big problems, if the file vanishes - create a link!
                        //if(AttachmentController.renameAttachmentFile(it.attachment, newAttachmentGuid)) {
                        if(AttachmentController.linkAttachmentFile(it.attachment, newAttachmentGuid)) {
                            LogUtil.d("SendMessageToBackendTask",
                                "Renamed attachment ${it.attachment} to $newAttachmentGuid")
                            it.attachment = newAttachmentGuid

                        } else {
                            LogUtil.e("SendMessageToBackendTask",
                                "Renaming attachment ${it.attachment} to $newAttachmentGuid failed!")
                        }
                    }

                    if (confirmMsgSendModel!!.receiver != null) {
                        it.setElementToAttributes(
                            AppConstants.MESSAGE_JSON_RECEIVERS, confirmMsgSendModel!!.receiver
                        )
                    }

                    messageController.markAsSentConfirmed(it, confirmMsgSendModel!!.datesend)
                }

                if (confirmMsgSendModel!!.notSend != null) {
                    notSendCount = confirmMsgSendModel!!.notSend.size
                }

                messageController.removeMessageFromActiveSendingMessages(message!!.id)

                sendListener?.onSendMessageSuccess(message, notSendCount)
            } else if (sendListener != null && message != null) {
                messageController.removeMessageFromActiveSendingMessages(message!!.id)
                sendListener.onSendMessageSuccess(message, 0)
            }
        }
    }

    override fun onCancelled(nothing: Unit?) {
        latch.await()
        super.onCancelled(nothing)

        if (messageModel.databaseId != null) {
            val message = messageController.getMessageById(messageModel.databaseId)

            if (message != null) {
                messageController.markAsError(message, true)
            }

            sendListener?.onSendMessageError(
                message,
                if (result is AsyncTaskError) (result as AsyncTaskError).error.message else null, null
            )
        }
    }

    private fun BackendResponse.logResponse() {
        if (jsonArray != null) {
            LogUtil.i(
                SendMessageToBackendTask::class.java.simpleName,
                "Backend response: $jsonArray"
            )
        }

        if (jsonObject != null) {
            LogUtil.i(
                SendMessageToBackendTask::class.java.simpleName,
                "Backend response: $jsonObject"
            )
        }
    }

    private fun BackendResponse.toAsyncTaskError(): AsyncTaskError<Unit> =
        AsyncTaskError(
            AsyncTaskResult.Error(
                if (errorMessage != null) errorMessage else resources.getString(R.string.service_tryAgainLater),
                msgException?.ident
            )
        )
}
