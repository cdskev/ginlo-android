// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.router

import android.content.Intent
import java.io.File

interface RouterBase {
    fun sendLog(recipientAddress: String, subject: String, file: File, chooserTitle: String)
    fun cropImage(imgFilePath: String)
    fun pickImage()
    fun startExternalActivityForResult(intent: Intent, requestCode: Int)
    fun startExternalActivity(intent: Intent)
    fun shareFile(file: File, chooserTitle: String):Boolean
    fun shareText(textToShare: String)
}
