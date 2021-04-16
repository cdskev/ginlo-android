// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.router

import android.content.Intent
import eu.ginlo_apps.ginlo.RegisterEmailActivity
import eu.ginlo_apps.ginlo.activity.reregister.ChangePhoneActivity
import eu.ginlo_apps.ginlo.controller.AccountController
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouterImpl @Inject constructor(appLifecycle: GinloAppLifecycle) : RouterBaseImpl(appLifecycle), Router {
    override fun startNextScreenForRequiredManagementState(mcState: String, firstName: String?, lastName: String?) {
        if (mcState == AccountController.MC_STATE_ACCOUNT_ACCEPTED_EMAIL_REQUIRED ||
            mcState == AccountController.MC_STATE_ACCOUNT_ACCEPTED_EMAIL_FAILED
        ) {
            Intent(appLifecycle.topActivity, RegisterEmailActivity::class.java).apply {
                putExtra(RegisterEmailActivity.EXTRA_FIRST_RUN, true)
                putExtra(RegisterEmailActivity.EXTRA_RUN_AFTER_REGISTRATION, true)
                putExtra(RegisterEmailActivity.EXTRA_PREFILLED_FIRST_NAME, firstName.orEmpty())
                putExtra(RegisterEmailActivity.EXTRA_PREFILLED_LAST_NAME, lastName.orEmpty())
            }.let {
                appLifecycle.topActivity?.startActivity(it)
            }
        } else if (mcState == AccountController.MC_STATE_ACCOUNT_ACCEPTED_PHONE_REQUIRED ||
            mcState == AccountController.MC_STATE_ACCOUNT_ACCEPTED_PHONE_FAILED
        ) {
            appLifecycle.topActivity?.startActivity(Intent(appLifecycle.topActivity, ChangePhoneActivity::class.java))
        }
    }
}
