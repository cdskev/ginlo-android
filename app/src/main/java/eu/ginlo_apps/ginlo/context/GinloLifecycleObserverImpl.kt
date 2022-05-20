// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.context

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import eu.ginlo_apps.ginlo.BuildConfig
import eu.ginlo_apps.ginlo.broadcastreceiver.AlarmReceiver
import eu.ginlo_apps.ginlo.controller.LoginController
import eu.ginlo_apps.ginlo.controller.contracts.AppLifecycleCallbacks
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.service.LogoutService
import eu.ginlo_apps.ginlo.util.SystemUtil
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GinloLifecycleObserverImpl @Inject constructor(private val application: SimsMeApplication) :
    GinloLifecycleObserver {
    companion object {
        private const val TAG = "GinloLifecycleObserverImpl"
        private var inBackground = true
    }

    private val logoutIntent: PendingIntent
    private val alarmManager: AlarmManager
    private val registeredAppLifecycleCallbacks = mutableListOf<AppLifecycleCallbacks>()
    private var avoidLogoutWhenGoesInBackground: Boolean = false

    init {
        Intent(this.application, AlarmReceiver::class.java)
            .apply {
                putExtra(AlarmReceiver.ALARM_TYPE, AlarmReceiver.TYPE_LOGOUT)
            }.let { serviceIntent ->
                logoutIntent = PendingIntent.getBroadcast(this.application, 0, serviceIntent, 0)
            }

        alarmManager = this.application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    override fun isAppInBackground() = inBackground

    override fun setAvoidLogoutWhenGoesInBackground() {
        avoidLogoutWhenGoesInBackground = true
    }

    override fun registeredAppLifecycleCallbacks(callbacks: AppLifecycleCallbacks) {
        synchronized(this) {
            registeredAppLifecycleCallbacks.add(callbacks)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private fun onEnterForeground() {
        if (avoidLogoutWhenGoesInBackground) {
            avoidLogoutWhenGoesInBackground = false
            return
        }

        inBackground = false
        LogUtil.i(TAG, "onEnterForeground: App enters foreground.")
        registeredAppLifecycleCallbacks.forEach { it.appDidEnterForeground() }
        stopLogoutService()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private fun onEnterBackground() {
        if (avoidLogoutWhenGoesInBackground) return

        inBackground = true
        LogUtil.i(TAG, "onEnterBackground: App enters background.")
        // Re-enable all notifications
        application.notificationController.currentChatGuid = null
        registeredAppLifecycleCallbacks.forEach { it.appGoesToBackGround() }
        logOut()
    }

    private fun logOut() {
        LogUtil.i(TAG, "logOut: Do logout.")
        startLogoutService(false)
    }

    override fun startLogoutService(isMinimumOneMinute: Boolean) {
        LogUtil.d(TAG, "startLogoutService")

        if (application.loginController.state != LoginController.STATE_LOGGED_IN) return

        if (application.preferencesController.passwordEnabled != true) return

        // Value expressed in Minutes
        val checkPasswordAfter =
            if (isMinimumOneMinute && application.preferencesController.checkPasswordAfterMinutes == 0) 1
            else application.preferencesController.checkPasswordAfterMinutes


        if (checkPasswordAfter == 0) {
            LogoutService.enqueueWork(application)
        } else {
            val startTime =
                SystemClock.elapsedRealtime() + checkPasswordAfter * 1000 * if (BuildConfig.DEBUG) 30 else 60

            if (checkPasswordAfter > 1 && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, startTime, logoutIntent)
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, startTime, logoutIntent)
            }
        }
    }

    private fun stopLogoutService() {
        alarmManager.cancel(logoutIntent)
    }
}

