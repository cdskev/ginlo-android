// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import eu.ginlo_apps.ginlo.BuildConfig
import eu.ginlo_apps.ginlo.LoginActivity
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.preferences.base.PreferencesBaseActivity
import eu.ginlo_apps.ginlo.controller.PreferencesController
import eu.ginlo_apps.ginlo.data.network.AppConnectivity
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.log.LogUtil
import kotlinx.android.synthetic.main.activity_preferences_others.*
import javax.inject.Inject

class PreferencesOthersActivity : PreferencesBaseActivity() {

    private val TAG = PreferencesOthersActivity::class.java.simpleName

    companion object {
        const val PLAY_SERVICES_RESOLUTION_REQUEST = 9000
    }

    @Inject
    internal lateinit var appConnectivity: AppConnectivity

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        // Handle screenshot switch
        // Disabled, if BuildConfig  says so!
        if (BuildConfig.ALWAYS_ALLOW_SCREENSHOTS) {
            preferences_others_switch_screenshot.isEnabled = true
            preferences_others_switch_screenshot.setOnCheckedChangeListener { buttonView, isChecked ->
                if (!settingsSwitch) {
                    try {
                        preferencesController.setScreenshotsEnabled(isChecked)
                    } catch (e: LocalizedException) {
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        LogUtil.w(this.javaClass.name, e.message, e)
                    }
                }
            }

            try {
                setCompoundButtonWithoutTriggeringListener(
                    preferences_others_switch_screenshot,
                    preferencesController.getScreenshotsEnabled()
                )
            } catch (e: LocalizedException) {
                LogUtil.w(this.javaClass.name, e.message, e)
            }

        } else {
            preferences_others_switch_screenshot.isEnabled = false
            preferences_others_switch_screenshot.isChecked = false
        }

        // Handle OSM switch
        // Disabled, if BuildConfig  says so!
        if (BuildConfig.SHOW_OSM_SWITCH) {
            preferences_others_switch_use_osm.isEnabled = true
            preferences_others_switch_use_osm.setOnCheckedChangeListener { buttonView, isChecked ->
                if (!settingsSwitch) {
                    try {
                        preferencesController.setOsmEnabled(isChecked)
                        if(!isChecked) {
                            val resultCode = GoogleApiAvailability.getInstance()
                                .isGooglePlayServicesAvailable(this);
                            if (resultCode != ConnectionResult.SUCCESS) {
                                if (GoogleApiAvailability.getInstance()
                                        .isUserResolvableError(resultCode)
                                ) {
                                    val dialog = GoogleApiAvailability.getInstance()
                                        .getErrorDialog(
                                            this,
                                            resultCode,
                                            PLAY_SERVICES_RESOLUTION_REQUEST
                                        );
                                    dialog?.show();
                                } else {
                                    LogUtil.i(TAG, "This device is not supported.");
                                }
                                preferencesController.setOsmEnabled(true)
                                setCompoundButtonWithoutTriggeringListener(buttonView, true)
                            }
                        }
                    } catch (e: LocalizedException) {
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        LogUtil.w(this.javaClass.name, e.message, e)
                    }
                }
            }

            try {
                setCompoundButtonWithoutTriggeringListener(
                    preferences_others_switch_use_osm,
                    preferencesController.getOsmEnabled()
                )
            } catch (e: LocalizedException) {
                LogUtil.w(this.javaClass.name, e.message, e)
            }

        } else {
            preferences_others_switch_use_osm.isEnabled = false
            preferences_others_switch_use_osm.isChecked = false
        }

        // Handle polling switch
        // Enabled, if BuildConfig  says so or if we need it due to missing play services
        if (BuildConfig.SHOW_POLLING_SWITCH || !mApplication.havePlayServices(this)) {
            preferences_others_switch_use_polling.isEnabled = true
            preferences_others_switch_use_polling.setOnCheckedChangeListener { buttonView, isChecked ->
                if (!settingsSwitch) {
                    try {
                        preferencesController.setPollingEnabled(isChecked)
                        if(isChecked) {
                            if(!mApplication.havePlayServices(this)) {
                                requestBatteryWhitelisting()
                            }
                            startGinloOngoingService()
                        } else {
                            stopGinloOngoingService()
                        }
                    } catch (e: LocalizedException) {
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        LogUtil.w(this.javaClass.name, e.message, e)
                    }
                }
            }

            try {
                setCompoundButtonWithoutTriggeringListener(
                    preferences_others_switch_use_polling,
                    preferencesController.getPollingEnabled()
                )
                if(preferencesController.getPollingEnabled()) {
                    if(!mApplication.havePlayServices(this)) {
                        requestBatteryWhitelisting()
                    }
                    startGinloOngoingService()
                } else {
                    stopGinloOngoingService()
                }
            } catch (e: LocalizedException) {
                LogUtil.w(this.javaClass.name, e.message, e)
            }

        } else {
            preferences_others_header_use_polling.visibility = View.GONE
            preferences_others_switch_use_polling.visibility = View.GONE
            preferences_others_divider_use_polling.visibility = View.GONE
            preferencesController.setPollingEnabled(false)
            stopGinloOngoingService()
        }

        // Handle play services switch
        // Only enabled, if we may use play services
        if (BuildConfig.SHOW_PLAY_SERVICES_SWITCH) {
            preferences_others_switch_use_ps.isEnabled = true
            preferences_others_switch_use_ps.setOnCheckedChangeListener { buttonView, isChecked ->
                if (!settingsSwitch) {
                    try {
                        // When switched on, check for play services availability
                        preferencesController.setPlayServicesEnabled(isChecked)
                        if(isChecked ) {
                            val resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
                            if (resultCode != ConnectionResult.SUCCESS) {
                                if (GoogleApiAvailability.getInstance().isUserResolvableError(resultCode)) {
                                    val dialog = GoogleApiAvailability.getInstance()
                                        .getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST);
                                    dialog?.show();
                                } else {
                                    LogUtil.i(TAG, "This device is not supported.");
                                }
                                preferencesController.setPlayServicesEnabled(false)
                                setCompoundButtonWithoutTriggeringListener(buttonView, false)
                            }
                        }
                    } catch (e: LocalizedException) {
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        LogUtil.w(this.javaClass.name, e.message, e)
                    }
                }
            }

            try {
                setCompoundButtonWithoutTriggeringListener(
                    preferences_others_switch_use_ps,
                    preferencesController.getPlayServicesEnabled()
                )
            } catch (e: LocalizedException) {
                LogUtil.w(this.javaClass.name, e.message, e)
            }

        } else {
            preferences_others_header_use_ps.visibility = View.GONE
            preferences_others_switch_use_ps.visibility = View.GONE
            preferences_others_divider_use_ps.visibility = View.GONE
            preferencesController.setPlayServicesEnabled(false)
        }

    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_preferences_others
    }

    override fun onResumeActivity() {}
}
