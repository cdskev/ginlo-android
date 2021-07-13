// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util

import android.content.Intent

import java.io.UnsupportedEncodingException
import java.net.URLDecoder

object UrlHandlerUtil {
    @Throws(UnsupportedEncodingException::class)
    fun getStringFromIntent(intent: Intent?, stringToDelete: String): String? {

        var query = intent?.data.toString()
        if(query.isBlank()) {
            return null
        }

        if (query.contains("?")) {
            val idx = query.indexOf('?')
            query = query.substring(idx + 1)
        }

        val decoded = URLDecoder.decode(query, "UTF-8")

        // keep only valid parameters bug 33549
        return if (decoded?.startsWith(stringToDelete) == true) {
            decoded.replace(stringToDelete, "")
        } else {
            ""
        }
    }
}
