// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.activity.profile

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import eu.ginlo_apps.ginlo.base64
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Account
import eu.ginlo_apps.ginlo.log.Logger
import eu.ginlo_apps.ginlo.sha256sum
import eu.ginlo_apps.ginlo.util.BitmapUtil
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

    suspend fun generateQrCode(
        account: Account,
        newQrCodeStyle: Boolean,
        widthPixel: Int,
        heightPixel: Int,
        privateKey: PrivateKey
    ): Bitmap? {
        return withContext(Dispatchers.Default + viewModelJobs) {
            try {
                val payload = getQrCodePayload(newQrCodeStyle, account, privateKey)

                val bitMatrix = getQrCodeSize(widthPixel, heightPixel, payload)

                // TODO: Inject BitmapUtil when possible
                return@withContext BitmapUtil.decodeBitMatrix(bitMatrix)
            } catch (e: Throwable) {
                when (e) {
                    is WriterException,
                    is UnsupportedEncodingException,
                    is LocalizedException -> logger.error(e)
                    else -> throw e
                }
            }
            return@withContext null
        }
    }

    private fun getQrCodeSize(
        widthPixel: Int,
        heightPixel: Int,
        payload: String
    ): BitMatrix {
        val size = (min(widthPixel, heightPixel) * 0.9).roundToInt()
        // TODO: Inject QRCodeWriter when possible
        return QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    }

    private fun getQrCodePayload(
        newQrCodeStyle: Boolean,
        account: Account,
        privateKey: PrivateKey
    ): String {
        return if (newQrCodeStyle && !account.accountID.isNullOrEmpty() && !account.publicKey.isNullOrEmpty())
            "V2\r${account.accountID}\r${account.publicKey.sha256sum().base64()}"
        else
            // TODO: Inject SecurityUtil when possible
            SecurityUtil.signData(privateKey, account.accountGuid.toByteArray(), false).base64()
    }

    override fun onCleared() {
        super.onCleared()

        viewModelJobs.cancel()
    }
}