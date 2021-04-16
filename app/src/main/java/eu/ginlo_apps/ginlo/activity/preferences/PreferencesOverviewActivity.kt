// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import eu.ginlo_apps.ginlo.LoginActivity
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.constant.AppConstants
import eu.ginlo_apps.ginlo.util.RuntimeConfig
import kotlinx.android.synthetic.main.activity_information.*
import kotlinx.android.synthetic.main.activity_preferences_overview.*

class PreferencesOverviewActivity : NewBaseActivity() {
    var appearanceChanged : Boolean = false

    override fun onCreateActivity(savedInstanceState: Bundle?) {

        // Prepare display of version information
        // (This has moved from PreferencesInformationActivity)
        AppConstants.gatherData(this)
        information_version?.apply {
            text = "${resources.getString(R.string.settings_version)} ${AppConstants.getAppVersionName()}"
        }
    }

    override fun getActivityLayout(): Int =
        R.layout.activity_preferences_overview

    override fun onResumeActivity() {
        if(appearanceChanged) {
            runOnUiThread { recreate() }
            appearanceChanged = false
        }
    }

    fun handlePasswordClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val intent = Intent(this, RuntimeConfig.getClassUtil().loginActivityClass)
        intent.putExtra(LoginActivity.EXTRA_MODE, LoginActivity.EXTRA_MODE_CHECK_PW)
        startActivityForResult(intent, REQUEST_EDIT_PASSWORD_SETTINGS)
    }

    fun handlePrivacyClick(@Suppress("UNUSED_PARAMETER") v: View) {
        startActivity(Intent(this, PreferencesPrivacyActivity::class.java))
    }

    fun handleChatsClick(@Suppress("UNUSED_PARAMETER") v: View) {
        startActivity(Intent(this, PreferencesChatsActivity::class.java))
    }

    fun handleMediaDownloadClick(@Suppress("UNUSED_PARAMETER") v: View) {
        startActivity(Intent(this, PreferencesMediaDownloadActivity::class.java))
    }

    fun handleNotificationsClick(@Suppress("UNUSED_PARAMETER") v: View) {
        startActivity(Intent(this, PreferencesNotificationsActivity::class.java))
    }

    fun handleAppearanceClick(@Suppress("UNUSED_PARAMETER") v: View) {
        startActivity(Intent(this, PreferencesAppearanceActivity::class.java))
        // Make life easier: Always assume changes in app apprearance
        appearanceChanged = true
    }

    fun handleHelpClick(@Suppress("UNUSED_PARAMETER") v: View) {
        startActivity(Intent(this, PreferencesInformationActivity::class.java))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_EDIT_PASSWORD_SETTINGS -> {
                if (resultCode == Activity.RESULT_OK) {
                    startActivity(Intent(this, PreferencesPasswordActivity::class.java))
                }
            }
            else -> {
                LogUtil.w(this.javaClass.name, LocalizedException.UNDEFINED_ARGUMENT)
            }
        }
    }

    companion object {
        private const val REQUEST_EDIT_PASSWORD_SETTINGS = 0x1f
    }
}