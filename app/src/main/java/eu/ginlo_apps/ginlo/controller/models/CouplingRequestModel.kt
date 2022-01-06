// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.models

import android.util.Base64
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Account
import eu.ginlo_apps.ginlo.model.backend.DeviceModel
import eu.ginlo_apps.ginlo.model.constant.Encoding
import eu.ginlo_apps.ginlo.model.constant.JsonConstants
import eu.ginlo_apps.ginlo.util.ChecksumUtil
import eu.ginlo_apps.ginlo.util.JsonUtil
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.SecurityUtil
import eu.ginlo_apps.ginlo.util.StringUtil
import eu.ginlo_apps.ginlo.util.XMLUtil
import java.io.UnsupportedEncodingException

class CouplingRequestModel constructor(couplingRequestJson: JsonObject, private val account: Account) {
    private val transId: String? = getStringFromJson(JsonConstants.TRANS_ID, couplingRequestJson)
    private val encryptionVerify: String? = getStringFromJson("encVrfy", couplingRequestJson)
    private val reqType: String? = getStringFromJson("reqType", couplingRequestJson)
    private val signature: String? = getStringFromJson(JsonConstants.SIG, couplingRequestJson)
    val publicKey: String? = getStringFromJson("pubKey", couplingRequestJson)
    val appData: String? = getStringFromJson(JsonConstants.APP_DATA, couplingRequestJson)
    val publicKeySha: String = ChecksumUtil.getSHA256ChecksumForString(publicKey).toUpperCase()

    init {
        if (!checkSignature()) {
            throw LocalizedException(LocalizedException.CHECK_SIGNATURE_FAILED, "Signature is Invalid")
        }
    }

    val deviceName: String?
        get() {
            val parser = JsonParser()

            if (StringUtil.isNullOrEmpty(appData)) {
                return null
            } else {
                val appDataJson = parser.parse(appData).asJsonObject
                if (appDataJson.has("deviceName")) {
                    val deviceNameEnc = appDataJson.get("deviceName").asString
                    try {
                        return String(Base64.decode(deviceNameEnc, Base64.NO_WRAP))
                    } catch (io: UnsupportedEncodingException) {
                        LogUtil.w(this.javaClass.simpleName, io.message, io)
                    }
                }
            }

            return null
        }

    val deviceGuid: String?
        get() {
            val parser = JsonParser()

            if (StringUtil.isNullOrEmpty(appData)) {
                return null
            } else {
                val appDataJson = parser.parse(appData).asJsonObject
                if (appDataJson.has("deviceGuid")) {
                    return appDataJson.get("deviceGuid").asString
                }
            }

            return null
        }

    val deviceImageResource: Int
        get() {
            val parser = JsonParser()

            if (StringUtil.isNullOrEmpty(appData)) {
                return R.drawable.device_smartphone
            } else {
                val appdataJson = parser.parse(appData).asJsonObject
                if (appdataJson.has("deviceOs")) {
                    val deviceOs = appdataJson.get("deviceOs").asString
                    return DeviceModel.getDeviceImageRessource(deviceOs)
                }
            }

            return R.drawable.device_smartphone
        }

    private fun getStringFromJson(key: String, couplingRequestJson: JsonObject): String? {
        if (!JsonUtil.hasKey(key, couplingRequestJson)) {
            throw LocalizedException(LocalizedException.NO_DATA_FOUND, "$key is null")
        }

        return JsonUtil.stringFromJO(key, couplingRequestJson)
    }

    private fun checkSignature(): Boolean {
        try {
            val key = XMLUtil.getPublicKeyFromXML(publicKey) ?: return false

            val accountGuid = account.accountGuid

            val concatSignature = accountGuid + transId + publicKey + encryptionVerify + reqType + appData

            val sigBytes = Base64.decode(signature, Base64.NO_WRAP)
            val concatBytes = concatSignature.toByteArray(charset(Encoding.UTF8))

            return SecurityUtil.verifyData(key, sigBytes, concatBytes, true)
        } catch (e: UnsupportedEncodingException) {
            LogUtil.w(javaClass.simpleName, "checkSignature()", e)
            return false
        } catch (e: LocalizedException) {
            LogUtil.w(javaClass.simpleName, "checkSignature()", e)
            return false
        }
    }
}
