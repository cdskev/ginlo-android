// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.UseCases

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import eu.ginlo_apps.ginlo.BuildConfig
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.QRCodeModel
import eu.ginlo_apps.ginlo.util.SystemUtil
import java.io.File
import java.io.FileOutputStream

class InviteFriendUseCase {

    companion object {
        val TAG = InviteFriendUseCase::class.java.simpleName
    }

    fun execute(currentActivity : Activity)
    {
        val application = currentActivity.application as SimsMeApplication
        val ownContact = application.contactController.ownContact
        val qrm : QRCodeModel
        val inviteString : String

        if("V3".equals(BuildConfig.QR_CODE_VERSION)) {
            qrm = QRCodeModel(true, false, ownContact.simsmeId, ownContact.publicKey, null)
            inviteString = currentActivity.getString(R.string.contacts_smsMessageBody) + " " + qrm.payload
        } else {
            // No personalized QR code
            val link = "https://ginlo.net/join"
            qrm = QRCodeModel(link)
            inviteString = currentActivity.getString(R.string.contacts_smsMessageBody) + " " + link
        }

        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_TEXT, inviteString)
        sendIntent.type = "text/plain"

        val qrCodeBitmap = qrm.createQRCodeBitmap(512)
        if (qrCodeBitmap != null) {
            //val dir: File? = application.applicationContext.getExternalFilesDir(null)
            val dir: File? = File(application.applicationContext.filesDir, "share")
            if (dir != null && dir.exists()) {
                val qrCodeFile = File(dir, "ginloinvite.png")
                if (qrCodeFile.exists()) {
                    qrCodeFile.delete()
                }

                val fos = FileOutputStream(qrCodeFile)
                qrCodeBitmap.compress(Bitmap.CompressFormat.PNG, 85, fos)
                fos.close()

                if (!qrCodeFile.exists()) {
                    LogUtil.e(TAG, "execute: Could not find file " + qrCodeFile.path)
                } else {
                    val qrCodeUri: Uri
                    if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)) {
                        qrCodeUri = FileProvider.getUriForFile(application.applicationContext, BuildConfig.APPLICATION_ID + ".fileprovider", qrCodeFile)
                    } else {
                        qrCodeUri = Uri.fromFile(qrCodeFile)
                    }

                    sendIntent.type = "image/png"
                    sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    sendIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    sendIntent.putExtra(Intent.EXTRA_STREAM, qrCodeUri)
                    val cd = ClipData.newUri(application.applicationContext.contentResolver, "ginloinvite.png", qrCodeUri)
                    sendIntent.clipData = cd
                }
            }

        } else {
            LogUtil.w(TAG, "Could not build bitmap for QR code of " + qrm.payload)
        }

        currentActivity.startActivity(Intent.createChooser(sendIntent, application.applicationContext.getString(R.string.contact_list_invite_contact)))
    }
}