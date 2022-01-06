// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences

import android.os.Bundle
import android.view.View
import eu.ginlo_apps.ginlo.BuildConfig
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.preferences.base.PreferencesBaseActivity
import eu.ginlo_apps.ginlo.data.network.AppConnectivity
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.log.LogUtil
import kotlinx.android.synthetic.main.activity_preferences_others.*
import javax.inject.Inject

class PreferencesOthersActivity : PreferencesBaseActivity() {


    @Inject
    internal lateinit var appConnectivity: AppConnectivity

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        // Handle ginloOngoingService switch
        // Disabled, if BuildConfig  says so!
        if (BuildConfig.HAVE_GINLO_ONGOING_SERVICE) {
            preferences_appearance_switch_ginlo_ongoing_service.isEnabled = true
            preferences_appearance_switch_ginlo_ongoing_service.setOnCheckedChangeListener { buttonView, isChecked ->
                if (!settingsSwitch) {
                    try {
                        preferencesController.setGinloOngoingServiceEnabled(isChecked)
                    } catch (e: LocalizedException) {
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        LogUtil.w(this.javaClass.name, e.message, e)
                    }
                }
                if (isChecked) {
                    preferences_appearance_switch_ongoing_notification.visibility = View.VISIBLE
                    preferences_appearance_switch_ongoing_notification.isEnabled = true
                } else {
                    preferences_appearance_switch_ongoing_notification.visibility = View.GONE
                }
            }

            try {
                setCompoundButtonWithoutTriggeringListener(
                        preferences_appearance_switch_ginlo_ongoing_service,
                        preferencesController.getGinloOngoingServiceEnabled()
                )
            } catch (e: LocalizedException) {
                LogUtil.w(this.javaClass.name, e.message, e)
            }

            if (preferencesController.getGinloOngoingServiceEnabled()) {
                preferences_appearance_switch_ongoing_notification.visibility = View.VISIBLE
                preferences_appearance_switch_ongoing_notification.isEnabled = true
            } else {
                preferences_appearance_switch_ongoing_notification.visibility = View.GONE
            }
        } else {
            preferences_appearance_header_ginlo_ongoing_service.visibility = View.GONE
            preferences_appearance_switch_ginlo_ongoing_service.visibility = View.GONE
            preferences_appearance_switch_ongoing_notification.visibility = View.GONE
            preferences_appearance_divider_ongoing_notification.visibility = View.GONE
            // Default setting should always be off
            preferencesController.setGinloOngoingServiceEnabled(false)
        }

        // Handle ginloOngoingServiceNotification switch
        preferences_appearance_switch_ongoing_notification.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!settingsSwitch) {
                try {
                    preferencesController.setGinloOngoingServiceNotificationEnabled(isChecked)
                    if(!isChecked) {
                        notificationController.dismissOngoingNotification()
                    } else {
                        notificationController.showOngoingServiceNotification(simsMeApplication.getString(R.string.notification_gos_running))
                    }
                } catch (e: LocalizedException) {
                    setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                    LogUtil.w(this.javaClass.name, e.message, e)
                }
            }
        }

        try {
            setCompoundButtonWithoutTriggeringListener(
                    preferences_appearance_switch_ongoing_notification,
                    preferencesController.getGinloOngoingServiceNotificationEnabled()
            )
        } catch (e: LocalizedException) {
            LogUtil.w(this.javaClass.name, e.message, e)
        }
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_preferences_others
    }

    override fun onResumeActivity() {}
}
