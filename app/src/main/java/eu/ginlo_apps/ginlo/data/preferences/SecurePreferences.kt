// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.data.preferences

interface SecurePreferences {
    fun setAccountPassToken(passToken: String)
    fun getAccountPassToken(): String?
    fun reset()
}