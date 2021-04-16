// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.data.preferences

import android.annotation.TargetApi
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import javax.inject.Inject
import javax.inject.Singleton

private const val ACCOUNT_PASS_TOKEN = "account-pass-token"

@Singleton
class SecurePreferencesImpl @Inject constructor(application: SimsMeApplication) :
    SecurePreferences {

    private val sharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "ginlo-secure-prefs",
            "ginlo-secure-prefs-mk",
            application,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun setAccountPassToken(passToken: String) {
        sharedPreferences.edit().apply {
            putString(ACCOUNT_PASS_TOKEN, passToken)
            apply()
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun getAccountPassToken(): String? {
        return sharedPreferences.getString(ACCOUNT_PASS_TOKEN, null)
    }

    override fun reset() {
        sharedPreferences.edit().clear().apply()
    }
}