// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.register.device

import android.content.Intent
import android.os.Bundle
import android.view.View
import eu.ginlo_apps.ginlo.BuildConfig
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity
import eu.ginlo_apps.ginlo.activity.register.PasswordActivity
import eu.ginlo_apps.ginlo.util.SystemUtil
import kotlinx.android.synthetic.main.activity_welcome_back.mask_selected_couple_device
import kotlinx.android.synthetic.main.activity_welcome_back.mask_selected_restore_button
import kotlinx.android.synthetic.main.activity_welcome_back.next_button

class WelcomeBackActivity : NewBaseActivity() {

    companion object {
        const val WELCOME_BACK_MODE = "WelcomeBackActivity.Mode"
        const val UNKNOWN = -1
        const val MODE_BACKUP = 1
        const val MODE_COUPLE = 2
    }

    private var mCurrentMode = -1

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        updateCheckboxes()
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_welcome_back
    }

    override fun onResumeActivity() {
    }

    private fun updateCheckboxes() {
        when (mCurrentMode) {
            UNKNOWN -> {
                mask_selected_couple_device.visibility = View.GONE
                mask_selected_restore_button.visibility = View.GONE
                next_button.isEnabled = false
            }
            MODE_BACKUP -> {
                mask_selected_couple_device.visibility = View.GONE
                mask_selected_restore_button.visibility = View.VISIBLE
                next_button.isEnabled = true
            }
            MODE_COUPLE -> {
                mask_selected_couple_device.visibility = View.VISIBLE
                mask_selected_restore_button.visibility = View.GONE
                next_button.isEnabled = true
            }
        }
    }

    fun handleRestoreBackup(@Suppress("UNUSED_PARAMETER") v: View?) {
        mCurrentMode = MODE_BACKUP
        updateCheckboxes()
    }

    fun handleCoupleDevice(@Suppress("UNUSED_PARAMETER") v: View?) {
        mCurrentMode = MODE_COUPLE
        updateCheckboxes()
    }

    fun handleNextClick(@Suppress("UNUSED_PARAMETER") v: View?) {
        val prefs = simsMeApplication.preferencesController
        try {
            when (mCurrentMode) {
                MODE_BACKUP -> {
                    val classForNextIntent =
                        SystemUtil.getClassForBuildConfigClassname(BuildConfig.ACTIVITY_AFTER_INTRO)
                    val intent = Intent(this, classForNextIntent)
                    prefs?.getSharedPreferences()?.edit()?.putInt(WELCOME_BACK_MODE, MODE_BACKUP)?.apply()
                    startActivity(intent)
                }
                MODE_COUPLE -> {
                    val intent = Intent(this, PasswordActivity::class.java)
                    intent.putExtra(
                        PasswordActivity.REGISTER_TYPE,
                        PasswordActivity.REGISTER_TYPE_COUPLE_DEVICE
                    )
                    prefs?.getSharedPreferences()?.edit()?.putInt(WELCOME_BACK_MODE, MODE_COUPLE)?.apply()
                    startActivity(intent)
                }
            }
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException(e)
        }
    }
}
