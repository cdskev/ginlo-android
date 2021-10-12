// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.context

import androidx.lifecycle.LifecycleObserver
import eu.ginlo_apps.ginlo.controller.contracts.AppLifecycleCallbacks

interface GinloLifecycleObserver : LifecycleObserver {
    fun startLogoutService(isMinimumOneMinute: Boolean)
    fun isAppInBackground(): Boolean
    fun registeredAppLifecycleCallbacks(callbacks: AppLifecycleCallbacks)
    fun setAvoidLogoutWhenGoesInBackground()
}