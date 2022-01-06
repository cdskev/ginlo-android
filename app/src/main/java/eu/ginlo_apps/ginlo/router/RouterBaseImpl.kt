// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.router

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycle
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.router.RouterConstants.ADJUST_PICTURE_RESULT_CODE
import eu.ginlo_apps.ginlo.router.RouterConstants.SELECT_GALLARY_RESULT_CODE
import eu.ginlo_apps.ginlo.view.cropimage.CropImageActivity
import java.io.File

abstract class RouterBaseImpl(protected val appLifecycle: GinloAppLifecycle) : RouterBase {
    override fun sendLog(
        recipientAddress: String,
        subject: String,
        file: File,
        chooserTitle: String
    ) {
        Intent.createChooser(
            createEmailWithAttachmentIntent(recipientAddress, subject, file),
            chooserTitle
        )
            .let {
                appLifecycle.topActivity?.startActivity(it)
            }
    }

    override fun shareFile(file: File, chooserTitle: String): Boolean {
        Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
        }.putFileExtra(file).let { sendIntent ->
            val activitiesThatCanHandleRequest =
                appLifecycle.topActivity?.packageManager?.queryIntentActivities(sendIntent, 0)
            if (activitiesThatCanHandleRequest != null && activitiesThatCanHandleRequest.count() <= 0) {
                return false
            }

            startExternalActivity(Intent.createChooser(sendIntent, chooserTitle))

            return true
        }
    }

    override fun cropImage(imgFilePath: String) {
        Intent(appLifecycle.topActivity, CropImageActivity::class.java).apply {
            putExtra(CropImageActivity.IMAGE_PATH, imgFilePath)
            putExtra(CropImageActivity.SCALE, true)
            putExtra(CropImageActivity.RETURN_DATA, true)
            putExtra(CropImageActivity.CIRCLE_CROP, true)

            val profileImageSize =
                appLifecycle.topActivity?.resources?.getInteger(R.integer.profile_image_size)

            putExtra(CropImageActivity.OUTPUT_X, profileImageSize)
            putExtra(CropImageActivity.OUTPUT_Y, profileImageSize)
        }.let {
            appLifecycle.topActivity?.startActivityForResult(it, ADJUST_PICTURE_RESULT_CODE)
        }
    }

    override fun pickImage() {
        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }.let {
            startExternalActivityForResult(it, SELECT_GALLARY_RESULT_CODE)
        }
    }

    override fun startExternalActivityForResult(intent: Intent, requestCode: Int) {
        LogUtil.d(this.javaClass.name, "startExternalActivityForResult: " + intent.action)
        try {
            appLifecycle.onStartingExternalActivity(false)
            appLifecycle.topActivity?.startActivityForResult(intent, requestCode)
        } catch (e: ActivityNotFoundException) {
            LogUtil.w(this.javaClass.name, e.message, e)
        }
    }

    override fun startExternalActivity(intent: Intent) {
        LogUtil.d(this.javaClass.name, "startExternalActivity: " + intent.action)
        try {
            appLifecycle.onStartingExternalActivity(true)
            appLifecycle.topActivity?.startActivityForResult(intent, -1)
        } catch (e: ActivityNotFoundException) {
            LogUtil.w(this.javaClass.name, e.message, e)
        }
    }

    override fun shareText(textToShare: String) {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, textToShare)
        }.let {
            appLifecycle.topActivity?.startActivity(it)
        }
    }

    private fun createEmailWithAttachmentIntent(
        address: String,
        subject: String,
        file: File
    ): Intent =
        createEmailIntent(address).also {
            it.putFileExtra(file)
            it.putExtra(Intent.EXTRA_SUBJECT, subject)
        }

    private fun createEmailIntent(address: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
        }

    private fun Intent.putFileExtra(file: File): Intent =
        this.putExtra(
            Intent.EXTRA_STREAM,
            FileProvider.getUriForFile(
                appLifecycle.topActivity?.applicationContext as Context,
                "${appLifecycle.topActivity?.packageName}.fileprovider",
                file
            )
        )
}
