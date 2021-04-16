// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model

import eu.ginlo_apps.ginlo.util.SecurityUtil
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

class AESKeyDataContainer(val aesKey: SecretKey, private val existingIv: IvParameterSpec?) {
    val iv: IvParameterSpec by lazy {
        existingIv ?: SecurityUtil.generateIV()
    }
}
