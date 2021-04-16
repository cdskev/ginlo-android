// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import eu.ginlo_apps.ginlo.BaseActivity
import eu.ginlo_apps.ginlo.context.GinloLifecycleObserver
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.controller.contracts.AppLifecycleCallbacks
import eu.ginlo_apps.ginlo.controller.contracts.LowMemoryCallback
import eu.ginlo_apps.ginlo.log.LogUtil
import java.util.Stack
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GinloAppLifecycleImpl @Inject constructor(
    private val application: SimsMeApplication,
    private val ginloLifecycleObserver: GinloLifecycleObserver
) :
    ActivityLifecycleCallbacks, ComponentCallbacks2, GinloAppLifecycle {

    private val activityStack = Stack<BaseActivity>()
    private val registeredLowMemoryCallbacks = mutableListOf<LowMemoryCallback>()

    init {
        application.registerActivityLifecycleCallbacks(this)
        application.registerComponentCallbacks(this)
    }

    override val isAppInForeground: Boolean
        get() = !ginloLifecycleObserver.isAppInBackground()

    override val topActivity: Activity?
        get() {
            return synchronized(activityStack) {
                activityStack.lastOrNull()
            }
        }

    override val activityStackSize: Int
        get() = activityStack.size

    override fun registerAppLifecycleCallbacks(callbacks: AppLifecycleCallbacks) {
        ginloLifecycleObserver.registeredAppLifecycleCallbacks(callbacks)
    }

    override fun registerLowMemoryCallback(callback: LowMemoryCallback) {
        synchronized(registeredLowMemoryCallbacks) {
            registeredLowMemoryCallbacks.add(callback)
        }
    }

    override fun onStartingExternalActivity(startLogoutService: Boolean) {
        if (!startLogoutService) {
            ginloLifecycleObserver.setAvoidLogoutWhenGoesInBackground()
        }
        if (!startLogoutService) {
            ginloLifecycleObserver.startLogoutService(true)
        }
    }

    override fun restartFromIntent(intent: Intent) {
        ginloLifecycleObserver.startLogoutService(false)
        intent.flags =
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        topActivity?.startActivity(intent)
        synchronized(activityStack) {
            activityStack.clear()
        }
    }

    override fun restartApp() {
        ginloLifecycleObserver.startLogoutService(false)
        application.packageManager.getLaunchIntentForPackage(application.packageName)?.apply {
            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }?.let {
            topActivity?.startActivity(it)
        }
        synchronized(activityStack) {
            activityStack.clear()
        }
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?
    ) {
        addActivity(activity, savedInstanceState)
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity.isFinishing) {
            removeActivity(activity)
        }
    }

    private fun addActivity(
        newActivity: Activity,
        savedInstanceState: Bundle?
    ) {
        if (newActivity !is BaseActivity) return

        synchronized(activityStack) {
            if (savedInstanceState == null) {
                activityStack.add(newActivity)
            } else {
                // check if old destroyed activity of the same type is in the stack
                activityStack.forEach { lActivity ->
                    if (lActivity === newActivity) {
                        activityStack.remove(lActivity)
                        activityStack.add(newActivity)
                        return
                    }
                }
            }
        }
    }

    private fun removeActivity(activity: Activity?) {
        if (activity != null) {
            synchronized(activityStack) {
                activityStack.remove(activity)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    override fun onLowMemory() {}

    override fun onTrimMemory(level: Int) {
        LogUtil.d(GinloAppLifecycleImpl::class.java.name, "onTrimMemory level:$level")

        registeredLowMemoryCallbacks.forEach { it.onLowMemory(level) }
    }
}
