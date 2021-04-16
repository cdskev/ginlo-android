// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller

import android.app.Activity
import android.content.Intent
import eu.ginlo_apps.ginlo.controller.contracts.AppLifecycleCallbacks
import eu.ginlo_apps.ginlo.controller.contracts.LowMemoryCallback

interface GinloAppLifecycle {
    val isAppInForeground: Boolean
    val activityStackSize: Int
    val topActivity: Activity?

    fun registerAppLifecycleCallbacks(callbacks: AppLifecycleCallbacks)
    fun registerLowMemoryCallback(callback: LowMemoryCallback)
    fun onStartingExternalActivity(startLogoutService: Boolean)
    fun restartApp()
    fun restartFromIntent(intent: Intent)
}