// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.preferences.base.PreferencesBaseActivity
import eu.ginlo_apps.ginlo.controller.PreferencesController
import eu.ginlo_apps.ginlo.data.network.AppConnectivity
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.exception.LocalizedException.GENERATE_AES_KEY_FAILED
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.ConfigUtil
import eu.ginlo_apps.ginlo.util.RuntimeConfig
import eu.ginlo_apps.ginlo.util.SystemUtil
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_chats_textview_show_preview_hint
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_layout_channels
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_layout_services
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_switch_inapp_notifications
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_switch_notifications_channels
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_switch_notifications_chats
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_switch_notifications_groups
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_switch_notifications_services
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_switch_show_preview
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_switch_sounds_channels
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_switch_sounds_chats
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_switch_sounds_groups
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_switch_sounds_services
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_switch_vibrations_channels
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_switch_vibrations_chats
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_switch_vibrations_groups
import kotlinx.android.synthetic.main.activity_preferences_notifications.preferences_notifications_switch_vibrations_services
import javax.inject.Inject

class PreferencesNotificationsActivity : PreferencesBaseActivity() {

    @Inject
    internal lateinit var appConnectivity: AppConnectivity

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        preferences_notifications_switch_inapp_notifications.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!settingsSwitch) {
                try {
                    preferencesController.setShowInAppNotifications(isChecked)
                } catch (e: LocalizedException) {
                    setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                    LogUtil.w(this.javaClass.name, e.message, e)
                }
            }
        }

        try {
            setCompoundButtonWithoutTriggeringListener(
                preferences_notifications_switch_inapp_notifications,
                preferencesController.getShowInAppNotifications()
            )

            configurePreviewSwitch()
            setChatNotifications()
            setGroupNotifications()

            if (ConfigUtil.channelsEnabled() || RuntimeConfig.isBAMandant() && simsMeApplication.accountController.isDeviceManaged) {
                setChannelNotifications()
            } else {
                preferences_notifications_layout_channels.visibility = View.GONE
            }

            if (ConfigUtil.servicesEnabled()) {
                setServiceNotifications()
            } else {
                preferences_notifications_layout_services.visibility = View.GONE
            }
        } catch (e: LocalizedException) {
            LogUtil.w(this.javaClass.name, e.message, e)
        }
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_preferences_notifications
    }

    override fun onResumeActivity() {}

    private fun configurePreviewSwitch() {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) && !preferencesController.isNotificationPreviewDisabledByAdmin()) {
            preferences_notifications_switch_show_preview.setOnCheckedChangeListener { buttonView, isChecked ->
                if (!settingsSwitch) {
                    try {
                        preferencesController.setNotificationPreviewEnabled(isChecked, false)
                    } catch (e: LocalizedException) {
                        if (GENERATE_AES_KEY_FAILED == e.identifier) {
                            setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                            // TODO fehlermeldung
                        }
                        LogUtil.w(this.javaClass.name, e.message, e)
                    }
                }
            }

            setCompoundButtonWithoutTriggeringListener(
                preferences_notifications_switch_show_preview,
                preferencesController.getNotificationPreviewEnabled()
            )
        } else {
            preferences_notifications_switch_show_preview.visibility = View.GONE
            preferences_chats_textview_show_preview_hint?.visibility = View.GONE
        }
    }

    private fun setChatNotifications() {
        val isSingleNotificationOn = preferencesController.getNotificationForSingleChatEnabled()

        setCompoundButtonWithoutTriggeringListener(
            preferences_notifications_switch_notifications_chats,
            isSingleNotificationOn
        )

        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {

            val isSingleSoundNotificationOn = preferencesController.getSoundForSingleChatEnabled()
            setCompoundButtonWithoutTriggeringListener(
                preferences_notifications_switch_sounds_chats,
                isSingleSoundNotificationOn
            )
            preferences_notifications_switch_sounds_chats.setOnCheckedChangeListener { buttonView, isChecked ->
                if (settingsSwitch) {
                    return@setOnCheckedChangeListener
                }

                if (!appConnectivity.isConnected()) {
                    Toast.makeText(
                        this@PreferencesNotificationsActivity, R.string.backendservice_internet_connectionFailed,
                        Toast.LENGTH_LONG
                    ).show()
                    setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                    return@setOnCheckedChangeListener
                }

                try {
                    showIdleDialog(-1)
                    preferencesController.setSoundForSingleChatEnabled(
                        isChecked,
                        object : PreferencesController.OnPreferenceChangedListener {
                            override fun onPreferenceChangedSuccess() {
                                dismissIdleDialog()
                            }

                            override fun onPreferenceChangedFail() {
                                dismissIdleDialog()
                                Toast.makeText(
                                    this@PreferencesNotificationsActivity,
                                    R.string.settings_save_setting_failed,
                                    Toast.LENGTH_LONG
                                )
                                    .show()

                                setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                            }
                        })
                } catch (e: LocalizedException) {
                    LogUtil.w(this.javaClass.name, e.message, e)
                    dismissIdleDialog()
                    Toast.makeText(
                        this@PreferencesNotificationsActivity,
                        R.string.settings_save_setting_failed,
                        Toast.LENGTH_LONG
                    )
                        .show()
                    setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                }

            }

            val isSingleVibrationNotificationOn = preferencesController.getVibrationForSingleChatsEnabled()

            setCompoundButtonWithoutTriggeringListener(
                preferences_notifications_switch_vibrations_chats,
                isSingleVibrationNotificationOn
            )
            preferences_notifications_switch_vibrations_chats.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, checked ->
                if (settingsSwitch) {
                    return@OnCheckedChangeListener
                }

                if (!appConnectivity.isConnected()) {
                    Toast.makeText(
                        this@PreferencesNotificationsActivity, R.string.backendservice_internet_connectionFailed,
                        Toast.LENGTH_LONG
                    ).show()
                    setCompoundButtonWithoutTriggeringListener(buttonView, !checked)
                    return@OnCheckedChangeListener
                }

                preferencesController.setVibrationForSingleChatsEnabled(checked)
            })

            preferences_notifications_switch_sounds_chats.isEnabled = isSingleNotificationOn
            preferences_notifications_switch_vibrations_chats.isEnabled = isSingleNotificationOn
        } else {
            preferences_notifications_switch_sounds_chats.visibility = View.GONE
            preferences_notifications_switch_vibrations_chats.visibility = View.GONE
        }

        preferences_notifications_switch_notifications_chats.setOnCheckedChangeListener { buttonView, isChecked ->
            if (settingsSwitch) {
                return@setOnCheckedChangeListener
            }

            if (!appConnectivity.isConnected()) {
                Toast.makeText(
                    this@PreferencesNotificationsActivity, R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG
                ).show()
                setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                return@setOnCheckedChangeListener
            }

            try {
                showIdleDialog(-1)
                preferencesController.setNotificationForSingleChatEnabled(
                    isChecked,
                    object : PreferencesController.OnPreferenceChangedListener {
                        override fun onPreferenceChangedSuccess() {
                            // set mit Ausloesen der Funktion!
                            preferences_notifications_switch_sounds_chats.isChecked = isChecked
                            preferences_notifications_switch_vibrations_chats.isChecked = isChecked
                            preferences_notifications_switch_sounds_chats.isEnabled = isChecked
                            preferences_notifications_switch_vibrations_chats.isEnabled = isChecked
                            dismissIdleDialog()
                        }

                        override fun onPreferenceChangedFail() {
                            dismissIdleDialog()
                            Toast.makeText(
                                this@PreferencesNotificationsActivity,
                                R.string.settings_save_setting_failed,
                                Toast.LENGTH_LONG
                            )
                                .show()
                            setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        }
                    })
            } catch (e: LocalizedException) {
                LogUtil.w(this.javaClass.name, e.message, e)
                dismissIdleDialog()
                Toast.makeText(
                    this@PreferencesNotificationsActivity,
                    R.string.settings_save_setting_failed,
                    Toast.LENGTH_LONG
                )
                    .show()
                setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
            }

        }
    }

    private fun setGroupNotifications() {
        val isGroupNotificationOn = preferencesController.getNotificationForGroupChatEnabled()
        setCompoundButtonWithoutTriggeringListener(
            preferences_notifications_switch_notifications_groups,
            isGroupNotificationOn
        )

        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
            val isGroupSoundNotificationOn = preferencesController.getSoundForGroupChatEnabled()
            setCompoundButtonWithoutTriggeringListener(
                preferences_notifications_switch_sounds_groups,
                isGroupSoundNotificationOn
            )
            preferences_notifications_switch_sounds_groups.setOnCheckedChangeListener { buttonView, isChecked ->
                if (settingsSwitch) {
                    return@setOnCheckedChangeListener
                }

                if (!appConnectivity.isConnected()) {
                    Toast.makeText(
                        this@PreferencesNotificationsActivity, R.string.backendservice_internet_connectionFailed,
                        Toast.LENGTH_LONG
                    ).show()
                    setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                    return@setOnCheckedChangeListener
                }

                try {
                    showIdleDialog(-1)
                    preferencesController.setSoundForGroupChatEnabled(
                        isChecked,
                        object : PreferencesController.OnPreferenceChangedListener {
                            override fun onPreferenceChangedSuccess() {
                                dismissIdleDialog()
                            }

                            override fun onPreferenceChangedFail() {
                                dismissIdleDialog()
                                Toast.makeText(
                                    this@PreferencesNotificationsActivity,
                                    R.string.settings_save_setting_failed,
                                    Toast.LENGTH_LONG
                                )
                                    .show()
                                setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                            }
                        })
                } catch (e: LocalizedException) {
                    LogUtil.w(this.javaClass.name, e.message, e)
                    dismissIdleDialog()
                    Toast.makeText(
                        this@PreferencesNotificationsActivity,
                        R.string.settings_save_setting_failed,
                        Toast.LENGTH_LONG
                    )
                        .show()
                    setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                }
            }

            val isGroupVibrationNotificationOn = preferencesController.getVibrationForGroupsEnabled()

            setCompoundButtonWithoutTriggeringListener(
                preferences_notifications_switch_vibrations_groups,
                isGroupVibrationNotificationOn
            )
            preferences_notifications_switch_vibrations_groups.setOnCheckedChangeListener { buttonView, checked ->
                if (settingsSwitch) {
                    return@setOnCheckedChangeListener
                }

                if (!appConnectivity.isConnected()) {
                    Toast.makeText(
                        this@PreferencesNotificationsActivity, R.string.backendservice_internet_connectionFailed,
                        Toast.LENGTH_LONG
                    ).show()
                    setCompoundButtonWithoutTriggeringListener(buttonView, !checked)
                    return@setOnCheckedChangeListener
                }

                preferencesController.setVibrationForGroupsEnabled(checked)
            }
            preferences_notifications_switch_sounds_groups.isEnabled = isGroupNotificationOn
            preferences_notifications_switch_vibrations_groups.isEnabled = isGroupNotificationOn
        } else {
            preferences_notifications_switch_sounds_groups.visibility = View.GONE
            preferences_notifications_switch_vibrations_groups.visibility = View.GONE
        }

        preferences_notifications_switch_notifications_groups.setOnCheckedChangeListener { buttonView, isChecked ->
            if (settingsSwitch) {
                return@setOnCheckedChangeListener
            }

            if (!appConnectivity.isConnected()) {
                Toast.makeText(
                    this@PreferencesNotificationsActivity, R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG
                ).show()
                setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                return@setOnCheckedChangeListener
            }
            try {
                showIdleDialog(-1)
                preferencesController.setNotificationForGroupChatEnabled(
                    isChecked,
                    object : PreferencesController.OnPreferenceChangedListener {
                        override fun onPreferenceChangedSuccess() {
                            // set mit Ausloesen der Funktion!
                            preferences_notifications_switch_sounds_groups.isChecked = isChecked
                            preferences_notifications_switch_vibrations_groups.isChecked = isChecked
                            preferences_notifications_switch_sounds_groups.isEnabled = isChecked
                            preferences_notifications_switch_vibrations_groups.isEnabled = isChecked
                            dismissIdleDialog()
                        }

                        override fun onPreferenceChangedFail() {
                            dismissIdleDialog()
                            Toast.makeText(
                                this@PreferencesNotificationsActivity,
                                R.string.settings_save_setting_failed,
                                Toast.LENGTH_LONG
                            )
                                .show()
                            setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        }
                    })
            } catch (e: LocalizedException) {
                LogUtil.w(this.javaClass.name, e.message, e)
                dismissIdleDialog()
                Toast.makeText(
                    this@PreferencesNotificationsActivity,
                    R.string.settings_save_setting_failed,
                    Toast.LENGTH_LONG
                )
                    .show()
                setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
            }

        }
    }

    private fun setChannelNotifications() {
        val isChannelNotificationOn = preferencesController.getNotificationForChannelChatEnabled()

        setCompoundButtonWithoutTriggeringListener(
            preferences_notifications_switch_notifications_channels,
            isChannelNotificationOn
        )
        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
            val isChannelSoundNotificationOn = preferencesController.getSoundForChannelChatEnabled()

            setCompoundButtonWithoutTriggeringListener(
                preferences_notifications_switch_sounds_channels,
                isChannelSoundNotificationOn
            )
            preferences_notifications_switch_sounds_channels.setOnCheckedChangeListener { buttonView, isChecked ->
                if (settingsSwitch) {
                    return@setOnCheckedChangeListener
                }

                if (!appConnectivity.isConnected()) {
                    Toast.makeText(
                        this@PreferencesNotificationsActivity, R.string.backendservice_internet_connectionFailed,
                        Toast.LENGTH_LONG
                    ).show()
                    setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                    return@setOnCheckedChangeListener
                }

                try {
                    showIdleDialog(-1)
                    preferencesController.setSoundForChannelChatEnabled(
                        isChecked,
                        object : PreferencesController.OnPreferenceChangedListener {
                            override fun onPreferenceChangedSuccess() {
                                dismissIdleDialog()
                            }

                            override fun onPreferenceChangedFail() {
                                dismissIdleDialog()
                                Toast.makeText(
                                    this@PreferencesNotificationsActivity,
                                    R.string.settings_save_setting_failed,
                                    Toast.LENGTH_LONG
                                )
                                    .show()
                                setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                            }
                        })
                } catch (e: LocalizedException) {
                    LogUtil.w(this.javaClass.name, e.message, e)
                    dismissIdleDialog()
                    Toast.makeText(
                        this@PreferencesNotificationsActivity,
                        R.string.settings_save_setting_failed,
                        Toast.LENGTH_LONG
                    )
                        .show()
                    setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                }
            }

            val isChannelVibrationNotificationOn = preferencesController.getVibrationForChannelsEnabled()

            setCompoundButtonWithoutTriggeringListener(
                preferences_notifications_switch_vibrations_channels,
                isChannelVibrationNotificationOn
            )
            preferences_notifications_switch_vibrations_channels.setOnCheckedChangeListener { buttonView, checked ->
                if (settingsSwitch) {
                    return@setOnCheckedChangeListener
                }

                if (!appConnectivity.isConnected()) {
                    Toast.makeText(
                        this@PreferencesNotificationsActivity, R.string.backendservice_internet_connectionFailed,
                        Toast.LENGTH_LONG
                    ).show()
                    setCompoundButtonWithoutTriggeringListener(buttonView, !checked)
                    return@setOnCheckedChangeListener
                }

                preferencesController.setVibrationForChannelsEnabled(checked)
            }

            preferences_notifications_switch_sounds_channels.isEnabled = isChannelNotificationOn
            preferences_notifications_switch_vibrations_channels.isEnabled = isChannelNotificationOn
        } else {
            preferences_notifications_switch_sounds_channels.visibility = View.GONE
            preferences_notifications_switch_vibrations_channels.visibility = View.GONE
        }

        preferences_notifications_switch_notifications_channels.setOnCheckedChangeListener { buttonView, isChecked ->
            if (settingsSwitch) {
                return@setOnCheckedChangeListener
            }

            if (!appConnectivity.isConnected()) {
                Toast.makeText(
                    this@PreferencesNotificationsActivity, R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG
                ).show()
                setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                return@setOnCheckedChangeListener
            }
            try {
                showIdleDialog(-1)
                preferencesController.setNotificationForChannelChatEnabled(
                    isChecked,
                    object : PreferencesController.OnPreferenceChangedListener {
                        override fun onPreferenceChangedSuccess() {
                            // set mit Ausloesen der Funktion!
                            preferences_notifications_switch_sounds_channels.isChecked = isChecked
                            preferences_notifications_switch_vibrations_channels.isChecked = isChecked
                            preferences_notifications_switch_sounds_channels.isEnabled = isChecked
                            preferences_notifications_switch_vibrations_channels.isEnabled = isChecked
                            dismissIdleDialog()
                        }

                        override fun onPreferenceChangedFail() {
                            dismissIdleDialog()
                            Toast.makeText(
                                this@PreferencesNotificationsActivity,
                                R.string.settings_save_setting_failed,
                                Toast.LENGTH_LONG
                            )
                                .show()
                            setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        }
                    })
            } catch (e: LocalizedException) {
                LogUtil.w(this.javaClass.name, e.message, e)
                dismissIdleDialog()
                Toast.makeText(
                    this@PreferencesNotificationsActivity,
                    R.string.settings_save_setting_failed,
                    Toast.LENGTH_LONG
                )
                    .show()
                setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
            }
        }
    }

    private fun setServiceNotifications() {
        val isServiceNotificationOn = preferencesController.getNotificationForServiceChatEnabled()
        setCompoundButtonWithoutTriggeringListener(
            preferences_notifications_switch_notifications_services,
            isServiceNotificationOn
        )

        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {

            val isServiceSoundNotificationOn = preferencesController.getSoundForServiceChatEnabled()

            setCompoundButtonWithoutTriggeringListener(
                preferences_notifications_switch_sounds_services,
                isServiceSoundNotificationOn
            )
            preferences_notifications_switch_sounds_services.setOnCheckedChangeListener { buttonView, isChecked ->
                if (settingsSwitch) {
                    return@setOnCheckedChangeListener
                }

                if (!appConnectivity.isConnected()) {
                    Toast.makeText(
                        this@PreferencesNotificationsActivity, R.string.backendservice_internet_connectionFailed,
                        Toast.LENGTH_LONG
                    ).show()
                    setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                    return@setOnCheckedChangeListener
                }

                try {
                    showIdleDialog(-1)
                    preferencesController.setSoundForServiceChatEnabled(
                        isChecked,
                        object : PreferencesController.OnPreferenceChangedListener {
                            override fun onPreferenceChangedSuccess() {
                                dismissIdleDialog()
                            }

                            override fun onPreferenceChangedFail() {
                                dismissIdleDialog()
                                Toast.makeText(
                                    this@PreferencesNotificationsActivity,
                                    R.string.settings_save_setting_failed,
                                    Toast.LENGTH_LONG
                                )
                                    .show()
                                setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                            }
                        })
                } catch (e: LocalizedException) {
                    LogUtil.w(this.javaClass.name, e.message, e)
                    dismissIdleDialog()
                    Toast.makeText(
                        this@PreferencesNotificationsActivity,
                        R.string.settings_save_setting_failed,
                        Toast.LENGTH_LONG
                    )
                        .show()
                    setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                }
            }

            val isServiceVibrationNotificationOn = preferencesController.getVibrationForServicesEnabled()

            setCompoundButtonWithoutTriggeringListener(
                preferences_notifications_switch_vibrations_services,
                isServiceVibrationNotificationOn
            )
            preferences_notifications_switch_vibrations_services.setOnCheckedChangeListener { buttonView, checked ->
                if (settingsSwitch) {
                    return@setOnCheckedChangeListener
                }

                if (!appConnectivity.isConnected()) {
                    Toast.makeText(
                        this@PreferencesNotificationsActivity, R.string.backendservice_internet_connectionFailed,
                        Toast.LENGTH_LONG
                    ).show()
                    setCompoundButtonWithoutTriggeringListener(buttonView, !checked)
                    return@setOnCheckedChangeListener
                }

                preferencesController.setVibrationForServicesEnabled(checked)
            }
            preferences_notifications_switch_sounds_services.isEnabled = isServiceNotificationOn
            preferences_notifications_switch_vibrations_services.isEnabled = isServiceNotificationOn
        } else {
            preferences_notifications_switch_sounds_services.visibility = View.GONE
            preferences_notifications_switch_vibrations_services.visibility = View.GONE
        }

        preferences_notifications_switch_notifications_services.setOnCheckedChangeListener { buttonView, isChecked ->
            if (settingsSwitch) {
                return@setOnCheckedChangeListener
            }

            if (!appConnectivity.isConnected()) {
                Toast.makeText(
                    this@PreferencesNotificationsActivity, R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG
                ).show()
                setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                return@setOnCheckedChangeListener
            }
            try {
                showIdleDialog(-1)
                preferencesController.setNotificationForServiceChatEnabled(
                    isChecked,
                    object : PreferencesController.OnPreferenceChangedListener {
                        override fun onPreferenceChangedSuccess() {
                            // set mit Ausloesen der Funktion!
                            preferences_notifications_switch_sounds_services.isChecked = isChecked
                            preferences_notifications_switch_vibrations_services.isChecked = isChecked
                            preferences_notifications_switch_sounds_services.isEnabled = isChecked
                            preferences_notifications_switch_vibrations_services.isEnabled = isChecked
                            dismissIdleDialog()
                        }

                        override fun onPreferenceChangedFail() {
                            dismissIdleDialog()
                            Toast.makeText(
                                this@PreferencesNotificationsActivity,
                                R.string.settings_save_setting_failed,
                                Toast.LENGTH_LONG
                            )
                                .show()
                            setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        }
                    })
            } catch (e: LocalizedException) {
                LogUtil.w(this.javaClass.name, e.message, e)
                dismissIdleDialog()
                Toast.makeText(
                    this@PreferencesNotificationsActivity,
                    R.string.settings_save_setting_failed,
                    Toast.LENGTH_LONG
                )
                    .show()
                setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
            }
        }
    }
}
