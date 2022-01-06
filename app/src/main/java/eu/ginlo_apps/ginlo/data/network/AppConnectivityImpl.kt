// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.data.network

import android.content.Context
import android.net.ConnectivityManager
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppConnectivityImpl @Inject constructor(private val application: SimsMeApplication) : AppConnectivity {
    override fun isConnected(): Boolean {
        val connectivityManager =
            application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

        return connectivityManager.activeNetworkInfo?.isConnected == true
    }
}