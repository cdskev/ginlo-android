// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo

import android.util.Base64
import java.security.MessageDigest

internal fun ByteArray.sha256Sum(): ByteArray =
    MessageDigest.getInstance("SHA-256").also { it.update(this) }.digest()

internal fun ByteArray.base64(): String =
    Base64.encodeToString(this, Base64.NO_WRAP)
