// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.activity.preferences

import android.os.Bundle
import android.view.View
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.RuntimeConfig
import kotlinx.android.synthetic.main.activity_preferences_privacy.*

class PreferencesPrivacyActivity : PreferencesPrivacyBaseActivity() {
    override fun onCreateActivity(savedInstanceState: Bundle?) {
        super.onCreateActivity(savedInstanceState)

        // Don't show status and message persistence values for b2c
        if(RuntimeConfig.isB2c()) {
            preferences_privacy_absence_header.visibility = View.GONE
            preferences_privacy_absence_header_value.visibility = View.GONE
            preferences_privacy_absence_text.visibility = View.GONE
            preferences_privacy_persist_msg_header.visibility = View.GONE
            preferences_privacy_persist_msg_text.visibility = View.GONE
        } else {
            try {
                preferences_privacy_persist_msg_text.text = getString(
                        R.string.settings_chat_persistmessages_detail,
                        simsMeApplication.preferencesController.getPersistMessageDays()
                )
            } catch (e: LocalizedException) {
                LogUtil.w(this.javaClass.name, e.message, e)
            }
        }
    }
}
