// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.broadcastreceiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.service.LogoutService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        if (intent.getIntExtra(ALARM_TYPE, -1) == TYPE_LOGOUT) {
            LogUtil.i("Logout", "Start Logout")

            LogoutService.enqueueWork(context.applicationContext)
        }

        resultCode = Activity.RESULT_OK
    }

    companion object {
        const val ALARM_TYPE = "AlarmReceiver.alarmType"
        const val TYPE_LOGOUT = 100
    }
}
