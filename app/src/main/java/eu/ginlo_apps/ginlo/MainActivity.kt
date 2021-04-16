// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo

import android.app.Activity
import android.content.DialogInterface.OnDismissListener
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import eu.ginlo_apps.ginlo.activity.register.IntroActivity
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.controller.LoginController
import eu.ginlo_apps.ginlo.greendao.Account
import eu.ginlo_apps.ginlo.model.constant.AppConstants
import eu.ginlo_apps.ginlo.util.RuntimeConfig

class MainActivity : Activity() {

    companion object {
        const val EXTRA_IS_RESTART: String = "MainActivity.extraRestart"
    }

    private var backButtonPressed: Boolean = false
    private var supportsGCM = true

    private val application: SimsMeApplication by lazy { getApplication() as SimsMeApplication }

    private fun isRestartIntent(): Boolean =
        intent?.getBooleanExtra(EXTRA_IS_RESTART, false) ?: false

    private fun isUserLoggedIn(): Boolean =
        application.accountController.hasAccountFullState() && application.loginController.state == LoginController.STATE_LOGGED_IN

    private fun isAccountLoaded(): Boolean =
        application.accountController.accountLoaded && application.accountController.accountState != Account.ACCOUNT_STATE_NO_ACCOUNT

    private fun hasNoAccount(): Boolean =
        application.accountController.accountState == Account.ACCOUNT_STATE_NO_ACCOUNT

    private fun isAutomaticMDMProgress(): Boolean =
        application.accountController.accountState == Account.ACCOUNT_STATE_AUTOMATIC_MDM_PROGRESS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.allowEnterTransitionOverlap = false
        window.allowReturnTransitionOverlap = false
        window.exitTransition = null

        setContentView(R.layout.activity_main)

        // gatherData() here - solving issue #64
        AppConstants.gatherData(this)

        if (!isRestartIntent() && !isTaskRoot) {
            finish()
            return
        }

        supportsGCM = application.gcmController.checkPlayServices(this)

        if (isUserLoggedIn()) {
            Intent(this, RuntimeConfig.getClassUtil().getStartActivityClass(application)).apply {
                flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }.let {
                startActivity(it)
            }

            return
        }

        when {
            hasNoAccount() -> {
                SimsMeApplication.getInstance().securePreferences.reset()
                Intent(this, IntroActivity::class.java).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }.let {
                    startActivity(it)
                }
            }
            isAutomaticMDMProgress() -> {
                SimsMeApplication.getInstance().securePreferences.reset()
                application.accountController.resetCreateAccountRegisterPhone()
                application.safeDeleteAccount()

                Intent(
                    this,
                    RuntimeConfig.getClassUtil().getStartActivityClass(application)
                ).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }.let {
                    startActivity(it)
                }
            }
            else -> navigateNext()
        }
    }

    override fun onBackPressed() {
        backButtonPressed = true
        super.onBackPressed()
    }

    private fun navigateNext() {
        Handler().postDelayed(Runnable {
            if (supportsGCM) {
                if (!backButtonPressed)
                    startApp()
                return@Runnable
            }

            val listener = OnDismissListener {
                startApp()
            }
            application.gcmController.dialog.setOnDismissListener(listener)
        }, 1000)
    }

    private fun startApp() {
        if (isAccountLoaded()) {
            Intent(this, RuntimeConfig.getClassUtil().loginActivityClass)
                    .apply {
                    flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }.let {
                startActivity(it)
            }
        } else {
            Intent(this, IntroActivity::class.java)
                    .apply {
                    flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                .let {
                    startActivity(it)
                }
        }
    }
}
