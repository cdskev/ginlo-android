// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.services

import android.content.Intent
import android.util.Base64
import androidx.core.app.JobIntentService
import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.controller.AttachmentController
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Message
import eu.ginlo_apps.ginlo.greendao.MessageDao
import eu.ginlo_apps.ginlo.greendao.Preference
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.constant.AppConstants
import eu.ginlo_apps.ginlo.model.constant.BackendError
import eu.ginlo_apps.ginlo.model.constant.MimeType
import eu.ginlo_apps.ginlo.service.BackendService
import eu.ginlo_apps.ginlo.service.IBackendService
import eu.ginlo_apps.ginlo.util.FileUtil
import java.util.concurrent.CountDownLatch

class LoadPendingAttachmentTask : JobIntentService() {
    override fun onHandleWork(intent: Intent) {
        try {
            val application = SimsMeApplication.getInstance()

            if (!BackendService.withAsyncConnection(application).isConnected) {
                return
            }

            var messages = getNextMessages(application, -1L)

            while (!messages.isEmpty()) {
                var messageId = -1L

                for (message in messages) {
                    if (messageId < message.id) {
                        messageId = message.id ?: -1
                    }

                    if (SimsMeApplication.getInstance().attachmentController.isAttachmentLocallyAvailable(message.attachment)) {
                        continue
                    }
                    if (message.isAttachmentDeletedServer) {
                        continue
                    }

                    val contentType = message.serverMimeType ?: return
                    contentType.replace("/selfdest".toRegex(), "")

                    if (shouldDownload(contentType, application)) {
                        downloadAttachment(message, application)
                    }
                }

                messages = getNextMessages(application, messageId)
            }
        } catch (e: LocalizedException) {
            LogUtil.e(LoadPendingAttachmentTask::javaClass.name, "Failed to download attachment.", e)
        }
    }

    private fun downloadAttachment(
            message: Message,
            application: SimsMeApplication
    ) {
        val latch = CountDownLatch(1)

        val onBackendResponseListener = IBackendService.OnBackendResponseListener { response ->
            try {
                if (response.isError) {
                    if (response.msgException?.ident == BackendError.ERR_0026_CANT_OPEN_MESSAGE) {
                        message.isAttachmentDeletedServer = true
                        application.messageController.dao.update(message)
                    }
                } else {
                    if (response.responseFilename != null) {
                        val base64file = AttachmentController.
                        convertJsonArrayFileToEncryptedAttachmentBase64File(response.responseFilename, message.attachment).toString()
                        // TODO: Separate (expensive!) file conversion calls right now. Must be combined later.
                        AttachmentController.saveBase64FileAsEncryptedAttachment(message.attachment, base64file)
                        LogUtil.d(LoadPendingAttachmentTask::javaClass.name, "Saved attachment from file for: " + message.attachment)
                        FileUtil.deleteFile(base64file)

                        // TODO: Don't forget to delete intermediate files after testing!
                    } else if (response.jsonArray?.get(0) != null) {
                        val content = Base64.decode(response.jsonArray.get(0).asString, Base64.NO_WRAP)
                        AttachmentController.saveEncryptedMessageAttachment(content, message.attachment)
                        LogUtil.d(LoadPendingAttachmentTask::javaClass.name, "Saved attachment from memory for: " + message.attachment)
                    }

                    val jsonArray = JsonArray().apply { add(JsonPrimitive(message.guid)) }

                    BackendService.withSyncConnection(application)
                            .setMessageState(
                                    jsonArray,
                                    AppConstants.MESSAGE_STATE_ATTACHMENT_DOWNLOADED,
                                    null,
                                    false
                            )

                    application.messageController.sendMessageChangedNotification(listOf(message))
                }
            } finally {
                latch.countDown()
            }
        }

        BackendService.withSyncConnection(application)
                .getAttachment(message.attachment, onBackendResponseListener, null)

        try {
            latch.await()
        } catch (e: InterruptedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }

    private fun shouldDownload(
            contentType: String,
            application: SimsMeApplication
    ): Boolean {
        val isConnectedViaWLAN = BackendService.withSyncConnection(application).isConnectedViaWLAN

        if (contentType.equals(MimeType.IMAGE_JPEG, ignoreCase = true)) {
            if (application.preferencesController.automaticDownloadPicture == Preference.AUTOMATIC_DOWNLOAD_ALWAYS ||
                    application.preferencesController.automaticDownloadPicture == Preference.AUTOMATIC_DOWNLOAD_WLAN
                    && isConnectedViaWLAN
            ) {
                return true
            }
        }
        if (contentType.equals(MimeType.AUDIO_MPEG, ignoreCase = true)) {
            if (application.preferencesController.automaticDownloadVoice == Preference.AUTOMATIC_DOWNLOAD_ALWAYS ||
                    application.preferencesController.automaticDownloadVoice == Preference.AUTOMATIC_DOWNLOAD_WLAN
                    && isConnectedViaWLAN
            ) {
                return true
            }
        }
        if (contentType.equals(MimeType.VIDEO_MPEG, ignoreCase = true)) {
            if (application.preferencesController.automaticDownloadVideo == Preference.AUTOMATIC_DOWNLOAD_ALWAYS ||
                    application.preferencesController.automaticDownloadVideo == Preference.AUTOMATIC_DOWNLOAD_WLAN
                    && isConnectedViaWLAN
            ) {
                return true
            }
        }
        if (contentType.equals(MimeType.APP_OCTET_STREAM, ignoreCase = true)) {
            if (application.preferencesController.automaticDownloadFiles == Preference.AUTOMATIC_DOWNLOAD_ALWAYS ||
                    application.preferencesController.automaticDownloadFiles == Preference.AUTOMATIC_DOWNLOAD_WLAN
                    && isConnectedViaWLAN
            ) {
                return true
            }
        }

        return false
    }

    override fun onStopCurrentWork(): Boolean {
        LogUtil.i(LoadPendingAttachmentTask::javaClass.name, "processorFinished")
        return super.onStopCurrentWork()
    }

    private fun getNextMessages(application: SimsMeApplication, messageId: Long): List<Message> {
        synchronized(application.messageController.dao) {
            val queryBuilder = application.messageController.dao.queryBuilder()

            queryBuilder.where(MessageDao.Properties.Attachment.isNotNull).whereOr(
                    MessageDao.Properties.Type.eq(Message.TYPE_GROUP), MessageDao.Properties.Type.eq(
                    Message.TYPE_PRIVATE
            )
            ).where(MessageDao.Properties.Id.gt(messageId))

            queryBuilder.orderAsc(MessageDao.Properties.Id).limit(30)

            return queryBuilder.build().forCurrentThread().list()
        }
    }

    companion object {
        fun start() {
            LogUtil.d(LoadPendingAttachmentTask::javaClass.name, "start() called.")

            val intent = Intent(SimsMeApplication.getInstance(), LoadPendingAttachmentTask::class.java)
            enqueueWork(SimsMeApplication.getInstance(), LoadPendingAttachmentTask::class.java, 1, intent)
        }
    }
}