// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.device

import android.os.Bundle
import android.view.View
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_device_img
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_device_name

class DeviceCoupleFinishActivity : NewBaseActivity() {
    override fun onCreateActivity(savedInstanceState: Bundle?) {
        val accountController = simsMeApplication.accountController

        device_couple_device_name.text = accountController.currentCouplingRequest.deviceName

        device_couple_device_img.setImageResource(accountController.currentCouplingRequest.deviceImageResource)
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_device_couple_finish
    }

    override fun onResumeActivity() {}

    fun handleConfirmClick(@Suppress("UNUSED_PARAMETER") v: View?) {
        finish()
    }
}
