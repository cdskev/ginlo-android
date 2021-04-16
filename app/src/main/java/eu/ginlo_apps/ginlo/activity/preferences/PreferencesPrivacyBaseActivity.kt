// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import eu.ginlo_apps.ginlo.BlockedContactsActivity
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.preferences.base.PreferencesBaseActivity
import eu.ginlo_apps.ginlo.controller.ContactController
import eu.ginlo_apps.ginlo.controller.PreferencesController
import eu.ginlo_apps.ginlo.data.network.AppConnectivity
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.ColorUtil
import eu.ginlo_apps.ginlo.util.DateUtil
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener
import eu.ginlo_apps.ginlo.util.RuntimeConfig
import javax.inject.Inject

abstract class PreferencesPrivacyBaseActivity : PreferencesBaseActivity() {
    @Inject
    internal lateinit var appConnectivity: AppConnectivity

    private val contactController: ContactController by lazy { simsMeApplication.contactController }
    private val preferencesController: PreferencesController by lazy { simsMeApplication.preferencesController }
    private lateinit var readConfirmationSwitch: SwitchCompat
    private lateinit var onlineStateSwitch: SwitchCompat
    private lateinit var loadInBackgroundSwitch: SwitchCompat
    private lateinit var profileNameSwitch: SwitchCompat
    private lateinit var sendCrashLogSwitch: SwitchCompat

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        readConfirmationSwitch = findViewById(R.id.preferences_privacy_read_confirmation_switch)
        onlineStateSwitch = findViewById(R.id.preferences_privacy_online_state_switch)
        loadInBackgroundSwitch = findViewById(R.id.preferences_privacy_load_in_background_switch)
        profileNameSwitch = findViewById(R.id.preferences_privacy_profile_name_switch)
        sendCrashLogSwitch = findViewById(R.id.preferences_privacy_send_crash_log_switch)

        readConfirmationSwitch.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                if (!settingsSwitch) {
                    if (!appConnectivity.isConnected()) {
                        Toast.makeText(
                            this@PreferencesPrivacyBaseActivity, R.string.backendservice_internet_connectionFailed,
                            Toast.LENGTH_LONG
                        ).show()
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        return
                    }
                    showIdleDialog(-1)
                    try {
                        preferencesController.setDisableConfirmRead(
                            isChecked,
                            object : PreferencesController.OnPreferenceChangedListener {
                                override fun onPreferenceChangedSuccess() {
                                    dismissIdleDialog()
                                }

                                override fun onPreferenceChangedFail() {

                                    dismissIdleDialog()
                                    Toast.makeText(
                                        this@PreferencesPrivacyBaseActivity,
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
                            this@PreferencesPrivacyBaseActivity,
                            R.string.settings_save_setting_failed,
                            Toast.LENGTH_LONG
                        )
                            .show()
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                    }
                }
            }
        })

        onlineStateSwitch.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (!settingsSwitch) {
                if (isChecked != preferencesController.getPublicOnlineState()) {
                    if (!appConnectivity.isConnected()) {
                        Toast.makeText(
                            this@PreferencesPrivacyBaseActivity, R.string.backendservice_internet_connectionFailed,
                            Toast.LENGTH_LONG
                        ).show()
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        return@OnCheckedChangeListener
                    }

                    val listener = object : GenericActionListener<String> {
                        override fun onSuccess(unused: String?) {
                            dismissIdleDialog()
                        }

                        override fun onFail(message: String?, errorIdent: String?) {
                            dismissIdleDialog()
                            Toast.makeText(
                                this@PreferencesPrivacyBaseActivity,
                                R.string.settings_save_setting_failed,
                                Toast.LENGTH_LONG
                            )
                                .show()
                            setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        }
                    }
                    showIdleDialog()
                    preferencesController.setPublicOnlineState(isChecked, listener)
                }
            }
        })

        loadInBackgroundSwitch.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (!settingsSwitch) {
                if (!appConnectivity.isConnected()) {
                    Toast.makeText(
                        this@PreferencesPrivacyBaseActivity, R.string.backendservice_internet_connectionFailed,
                        Toast.LENGTH_LONG
                    ).show()
                    setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                    return@OnCheckedChangeListener
                }

                preferencesController.setFetchInBackground(isChecked)

                if (isChecked) {
                    showIdleDialog()
                    val genericActionListener = object : GenericActionListener<String> {
                        override fun onSuccess(unused: String?) {
                            dismissIdleDialog()
                        }

                        override fun onFail(message: String?, errorIdent: String?) {
                            dismissIdleDialog()
                            Toast.makeText(
                                this@PreferencesPrivacyBaseActivity,
                                R.string.settings_save_setting_failed,
                                Toast.LENGTH_LONG
                            )
                                .show()
                            setCompoundButtonWithoutTriggeringListener(buttonView, false)
                        }
                    }
                    simsMeApplication.messageController.getBackgroundAccessToken(genericActionListener)
                }
            }
        })

        profileNameSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!settingsSwitch) {
                preferencesController.sendProfileName = isChecked
            }
        }

        sendCrashLogSwitch.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                if (!settingsSwitch) {
                    try {
                        preferencesController.setSendCrashLogSetting(isChecked)
                    } catch (e: LocalizedException) {
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        LogUtil.w(this.javaClass.name, e.message, e)
                    }
                }
            }
        })
    }

    override fun onResumeActivity() {
        try {
            val blockedContactsCount = findViewById<TextView>(R.id.preferences_privacy_blocked_contacts_counter)
            blockedContactsCount.text = contactController.blockedContacts.size.toString()

            setCompoundButtonWithoutTriggeringListener(
                readConfirmationSwitch,
                preferencesController.getConfirmRead()
            )
            setCompoundButtonWithoutTriggeringListener(
                onlineStateSwitch,
                preferencesController.getPublicOnlineState()
            )
            setCompoundButtonWithoutTriggeringListener(
                loadInBackgroundSwitch,
                preferencesController.getFetchInBackground()
            )
            setCompoundButtonWithoutTriggeringListener(
                profileNameSwitch,
                preferencesController.sendProfileName
            )
            setCompoundButtonWithoutTriggeringListener(
                sendCrashLogSwitch,
                preferencesController.getSendCrashLogSetting()
            )

            val ownContact = simsMeApplication.contactController.ownContact

            val absenceText = findViewById<TextView>(R.id.preferences_privacy_absence_text)
            val absenceHeaderValue = findViewById<TextView>(R.id.preferences_privacy_absence_header_value)

            val colorUtil = ColorUtil.getInstance()

            if (RuntimeConfig.isBAMandant()) {
                if (ownContact?.isAbsent == true) {
                    val absentTime = DateUtil.utcWithoutMillisStringToMillis(ownContact.absenceTimeUtcString)
                    if (absentTime == 0L) {
                        absenceText.text = resources.getString(R.string.peferences_absence_absent)
                    } else {
                        absenceText.text = String.format(
                            resources.getString(R.string.peferences_absence_absent_till),
                            DateUtil.getDateAndTimeStringFromMillis(absentTime)
                        )
                    }
                    absenceHeaderValue.text = resources.getString(R.string.peferences_absence_absent)
                    absenceHeaderValue.setTextColor(colorUtil.getLowColor(simsMeApplication))
                } else {
                    absenceText.text = resources.getString(R.string.peferences_absence_present)
                    absenceHeaderValue.text = resources.getString(R.string.peferences_absence_present)
                    absenceHeaderValue.setTextColor(colorUtil.getHighColor(simsMeApplication))
                }
            }
        } catch (e: LocalizedException) {
            LogUtil.w(this.javaClass.name, e.message, e)
        }
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_preferences_privacy
    }

    fun handleAbsenceStateClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val intent = Intent(this, RuntimeConfig.getClassUtil().absenceActivityClass)
        startActivity(intent)
    }

    fun handleBlockedContactsClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val intent = Intent(this, BlockedContactsActivity::class.java)
        startActivity(intent)
    }
}
