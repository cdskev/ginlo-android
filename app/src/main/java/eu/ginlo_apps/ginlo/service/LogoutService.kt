// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.service

import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.log.LogUtil

class LogoutService : JobIntentService() {

    override fun onHandleWork(intent: Intent) {
        startLogout(application as SimsMeApplication)
    }

    private fun startLogout(application: SimsMeApplication) {
        application.loginController.logout()

        LogUtil.i(TAG, "Logout")
    }

    companion object {
        private const val TAG = "LogoutService"

        private const val JOB_ID = 1002

        fun enqueueWork(context: Context) {
            JobIntentService.enqueueWork(context, LogoutService::class.java, JOB_ID, Intent())
        }
    }
}
