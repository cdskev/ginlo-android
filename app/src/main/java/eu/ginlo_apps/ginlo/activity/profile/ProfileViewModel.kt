// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.activity.profile

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import eu.ginlo_apps.ginlo.BuildConfig
import eu.ginlo_apps.ginlo.base64
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Account
import eu.ginlo_apps.ginlo.log.Logger
import eu.ginlo_apps.ginlo.util.BitmapUtil
import eu.ginlo_apps.ginlo.util.SecurityUtil
import eu.ginlo_apps.ginlo.model.QRCodeModel
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
                    if("V3".equals(BuildConfig.QR_CODE_VERSION)) {
                        qrm = QRCodeModel(true, false, account.accountID, account.publicKey, null) // QR code version "V3"
                    } else {
                        qrm = QRCodeModel(account.accountID, account.publicKey) // QR code version "V2"
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