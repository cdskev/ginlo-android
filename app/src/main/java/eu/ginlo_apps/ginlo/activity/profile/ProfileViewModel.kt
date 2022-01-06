// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.activity.profile

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import eu.ginlo_apps.ginlo.BuildConfig
import eu.ginlo_apps.ginlo.base64
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Account
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.log.Logger
import eu.ginlo_apps.ginlo.model.QRCodeModel
import eu.ginlo_apps.ginlo.util.ChecksumUtil
import eu.ginlo_apps.ginlo.util.SecurityUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.UnsupportedEncodingException
import java.security.PrivateKey
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

class ProfileViewModel @Inject constructor(private val logger: Logger) : ViewModel() {
    private val viewModelJobs = Job()

    companion object {
        val TAG = ProfileViewModel::class.java.simpleName
    }

    suspend fun generateQrCode(
        account: Account,
        widthPixel: Int,
        heightPixel: Int,
        privateKey: PrivateKey
    ): Bitmap? {
        return withContext(Dispatchers.Default + viewModelJobs) {
            try {
                val qrm : QRCodeModel
                if (!account.accountID.isNullOrEmpty() && !account.publicKey.isNullOrEmpty()) {
                    val keySignature = ChecksumUtil.getSHA256ChecksumAsBytesForString(account.publicKey).base64()

                    if("V3".equals(BuildConfig.QR_CODE_VERSION)) {
                        LogUtil.d(TAG, "generateQrCode: V3 Using publicKey = " + account.publicKey + " keySignature = " + keySignature)
                        qrm = QRCodeModel(true, false, account.accountID, keySignature, null) // QR code version "V3"
                    } else {
                        LogUtil.d(TAG, "generateQrCode: V2 Using publicKey = " + account.publicKey + " keySignature = " + keySignature)
                        qrm = QRCodeModel(account.accountID, keySignature) // QR code version "V2"
                    }
                } else {
                    val genericPayload = SecurityUtil.signData(privateKey, account.accountGuid.toByteArray(), false).base64()
                    qrm = QRCodeModel(genericPayload) // QR code version "Generic"
                }
                val size = (min(widthPixel, heightPixel) * 0.9).roundToInt()
                return@withContext qrm.createQRCodeBitmap(size)
            } catch (e: Throwable) {
                when (e) {
                    is UnsupportedEncodingException,
                    is LocalizedException -> logger.error(e)
                    else -> throw e
                }
            }
            return@withContext null
        }
    }

    override fun onCleared() {
        super.onCleared()

        viewModelJobs.cancel()
    }
}