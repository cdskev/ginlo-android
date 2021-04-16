// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.device

import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.View
import android.widget.TextView
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity
import eu.ginlo_apps.ginlo.controller.AccountController
import eu.ginlo_apps.ginlo.greendao.Account
import eu.ginlo_apps.ginlo.util.ColorUtil
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_confirm_couple_btn
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_confirm_device_btn
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_device_img
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_device_name
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_device_type
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_device_type_descr
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_0
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_1
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_10
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_11
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_12
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_13
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_14
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_15
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_2
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_3
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_4
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_5
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_6
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_7
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_8
import kotlinx.android.synthetic.main.activity_device_couple_confirm.device_couple_sn_9

class DeviceCoupleConfirmActivity : NewBaseActivity() {
    companion object {
        const val EXTRA_TAN = "x_tan"
    }

    private val accountController: AccountController by lazy { simsMeApplication.accountController }
    private lateinit var account: Account

    private var mTan: String? = null

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        account = accountController.account

        mTan = intent.getStringExtra(EXTRA_TAN)

        val securityCode = listOf<TextView>(
            device_couple_sn_0,
            device_couple_sn_1,
            device_couple_sn_2,
            device_couple_sn_3,
            device_couple_sn_4,
            device_couple_sn_5,
            device_couple_sn_6,
            device_couple_sn_7,
            device_couple_sn_8,
            device_couple_sn_9,
            device_couple_sn_10,
            device_couple_sn_11,
            device_couple_sn_12,
            device_couple_sn_13,
            device_couple_sn_14,
            device_couple_sn_15
        )

        val requestSha = accountController.currentCouplingRequest.publicKeySha

        if (requestSha.length == securityCode.size * 4) {
            for (i in securityCode.indices) {
                securityCode[i].text = requestSha.substring(i * 4, 4 + i * 4)
            }
        }

        device_couple_device_name.text = accountController.currentCouplingRequest.deviceName

        if (!accountController.currentCouplingRequest.isTempDevice) {
            device_couple_device_type.setText(R.string.device_couple_perm_device)
            device_couple_device_type_descr.setText(R.string.device_couple_perm_descr)
        }
        device_couple_device_img.setImageResource(accountController.currentCouplingRequest.deviceImageResource)
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_device_couple_confirm
    }

    override fun onResumeActivity() {
    }

    fun handleCancelClick(@Suppress("UNUSED_PARAMETER") v: View?) {
        if (mTan != null) {
            showIdleDialog()
            val tan = mTan.orEmpty()
            mTan = null
            simsMeApplication.accountController.coupleCancelCoupling(
                tan,
                account,
                object : GenericActionListener<Void> {
                    override fun onSuccess(nothing: Void?) {
                        dismissIdleDialog()
                        finish()
                    }

                    override fun onFail(message: String?, errorIdent: String?) {
                        dismissIdleDialog()
                        finish()
                    }
                })
        } else {
            finish()
        }
    }

    fun handleConfirmClick(@Suppress("UNUSED_PARAMETER") v: View?) {
        if (mTan != null) {
            showIdleDialog()
            val tan = mTan.orEmpty()
            mTan = null
            simsMeApplication.accountController.coupleResponseCoupling(
                tan,
                account,
                object : GenericActionListener<Void> {
                    override fun onSuccess(noting: Void?) {
                        dismissIdleDialog()
                        val intent = Intent(this@DeviceCoupleConfirmActivity, DeviceCoupleFinishActivity::class.java)
                        startActivity(intent)
                        finish()
                    }

                    override fun onFail(message: String?, errorIdent: String?) {
                        dismissIdleDialog()
                        finish()
                    }
                })
        } else {
            finish()
        }
    }

    override fun colorizeActivity() {
        super.colorizeActivity()

        val colorUtil = ColorUtil.getInstance()
        val appAccent = colorUtil.getAppAccentColor(simsMeApplication)
        val appAccentContrast = colorUtil.getAppAccentContrastColor(simsMeApplication)
        val colorFilterAccent = PorterDuffColorFilter(appAccent, PorterDuff.Mode.SRC_ATOP)
        device_couple_confirm_device_btn.background.colorFilter = colorFilterAccent
        device_couple_confirm_device_btn.setTextColor(appAccent)
        device_couple_confirm_couple_btn.background.colorFilter = colorFilterAccent
        device_couple_confirm_couple_btn.setTextColor(appAccentContrast)
    }
}