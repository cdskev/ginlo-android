// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.device

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.widget.TextView
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity
import eu.ginlo_apps.ginlo.concurrent.task.HttpBaseTask
import eu.ginlo_apps.ginlo.controller.AccountController
import eu.ginlo_apps.ginlo.greendao.Account
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.ColorUtil
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener
import kotlinx.android.synthetic.main.activity_device_couple_confirm.*

class DeviceCoupleConfirmActivity : NewBaseActivity() {
    companion object {
        const val EXTRA_TAN = "x_tan"
    }

    private val TAG = DeviceCoupleConfirmActivity::class.java.simpleName
    private val WAKELOCK_TAG = "ginlo:" + TAG
    private val WAKELOCK_FLAGS = PowerManager.PARTIAL_WAKE_LOCK

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
            val pm = simsMeApplication.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(WAKELOCK_FLAGS, WAKELOCK_TAG)
            wl.acquire(10 * 60 * 1000L /*10 minutes*/)

            val tan = mTan.orEmpty()
            mTan = null
            simsMeApplication.accountController.coupleResponseCoupling(
                tan,
                account,
                object : GenericActionListener<Void> {
                    override fun onSuccess(noting: Void?) {
                        wl.release()
                        dismissIdleDialog()
                        if (wl.isHeld) {
                            LogUtil.w(TAG, "handleConfirmClick: onSuccess: Wakelock held!")
                        }

                        val intent = Intent(this@DeviceCoupleConfirmActivity, DeviceCoupleFinishActivity::class.java)
                        startActivity(intent)
                        finish()
                    }

                    override fun onFail(message: String?, errorIdent: String?) {
                        wl.release()
                        dismissIdleDialog()
                        if (wl.isHeld) {
                            LogUtil.w(TAG, "handleConfirmClick: onFail: Wakelock held!")
                        }
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