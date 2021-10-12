// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.SetPasswordActivity
import eu.ginlo_apps.ginlo.activity.preferences.base.PreferencesBaseActivity
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask
import eu.ginlo_apps.ginlo.controller.AccountController
import eu.ginlo_apps.ginlo.controller.KeyController
import eu.ginlo_apps.ginlo.controller.PreferencesController
import eu.ginlo_apps.ginlo.data.network.AppConnectivity
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.fragment.FingerprintFragment
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.ColorUtil
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil
import eu.ginlo_apps.ginlo.util.PermissionUtil
import eu.ginlo_apps.ginlo.util.RuntimeConfig
import eu.ginlo_apps.ginlo.util.SystemUtil
import kotlinx.android.synthetic.main.activity_preferences_password.preferences_overview_text_password
import kotlinx.android.synthetic.main.activity_preferences_password.preferences_password_ask_after_text
import kotlinx.android.synthetic.main.activity_preferences_password.preferences_password_delete_after_layout
import kotlinx.android.synthetic.main.activity_preferences_password.preferences_password_delete_after_text
import kotlinx.android.synthetic.main.activity_preferences_password.preferences_password_delete_data_switch
import kotlinx.android.synthetic.main.activity_preferences_password.preferences_password_enable_switch
import kotlinx.android.synthetic.main.activity_preferences_password.preferences_password_fingerprint_switch
import kotlinx.android.synthetic.main.activity_preferences_password.preferences_password_recovery_code_switch
import kotlinx.android.synthetic.main.activity_preferences_password.preferences_password_request_ask_after_layout
import javax.crypto.Cipher
import javax.inject.Inject

class PreferencesPasswordActivity : PreferencesBaseActivity(), FingerprintFragment.AuthenticationListener {
    companion object {
        private const val REQUEST_CHANGE_PASSWORD_AFTER_LOGIN = 0x6f
        private const val FINGERPRINT_FRAGMENT = "fingerprintFragment"
    }

    private var fingerprintFragment: FingerprintFragment? = null
    private val accountController: AccountController by lazy { simsMeApplication.accountController }
    private val preferencesController: PreferencesController by lazy { simsMeApplication.preferencesController }

    @Inject
    internal lateinit var appConnectivity: AppConnectivity

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        preferences_password_enable_switch.setOnCheckedChangeListener { _, isChecked ->
            if (!settingsSwitch) {
                handleCheckPasswordOnStartupClick(isChecked)
            }
        }

        preferences_password_delete_data_switch.setOnCheckedChangeListener { _, isChecked ->
            if (!settingsSwitch) {
                handleDeleteDataClick(isChecked)
            }
        }

        preferences_password_recovery_code_switch.setOnCheckedChangeListener { _, isChecked ->
            if (!settingsSwitch) {
                handleRecoveryCodeClick(isChecked)
            }
        }

        configureFingerprintSwitch()
    }

    override fun onResumeActivity() {

        if (accountController.password == null) {
            finish()
            return
        }
        try {
            if (preferencesController.isPasswordOnStartRequired()) {
                preferences_password_enable_switch.visibility = View.GONE
                setCompoundButtonWithoutTriggeringListener(
                    preferences_password_enable_switch,
                    preferencesController.isPasswordOnStartRequired()
                )
            } else {
                setCompoundButtonWithoutTriggeringListener(
                    preferences_password_enable_switch,
                    preferencesController.passwordEnabled as Boolean
                )
            }

            if (preferencesController.isDeleteDataAfterTriesManaged()) {
                preferences_password_delete_data_switch.visibility = View.GONE
                preferences_password_delete_after_layout.visibility = View.GONE
            } else {
                setCompoundButtonWithoutTriggeringListener(
                    preferences_password_delete_data_switch,
                    preferencesController.getDeleteDataAfterTries()
                )
                preferences_password_delete_after_text.text =
                    preferencesController.getNumberOfPasswordTries().toString()
            }

            preferences_password_ask_after_text.text = preferencesController.checkPasswordAfterMinutes.toString()

            if (accountController.isDeviceManaged || !RuntimeConfig.isBAMandant() || preferencesController.isRecoveryDisabled()) {
                preferences_password_recovery_code_switch.visibility = View.GONE
            } else {
                setCompoundButtonWithoutTriggeringListener(
                    preferences_password_recovery_code_switch,
                    preferencesController.getRecoveryCodeEnabled()
                )
            }

            if (simsMeApplication.preferencesController.getHasSystemGeneratedPasword()) {
                preferences_password_enable_switch.isEnabled = false
                preferences_password_delete_data_switch.isEnabled = false
                preferences_overview_text_password.setText(R.string.settings_password_setPassword)
            } else {
                preferences_password_enable_switch.isEnabled = true
                preferences_password_delete_data_switch.isEnabled = true
                preferences_overview_text_password.setText(R.string.settings_password_changePassword)
            }
        } catch (le: LocalizedException) {
            LogUtil.w(this.javaClass.simpleName, le.message, le)
            finish()
        }

        updateOptions()
    }

    private fun configureFingerprintSwitch() {
        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) || !accountController.isBiometricAuthAvailable) {
            preferences_password_fingerprint_switch.visibility = View.GONE
            return
        }

        setCompoundButtonWithoutTriggeringListener(
            preferences_password_fingerprint_switch,
            preferencesController.getBiometricAuthEnabled()
        )
        preferences_password_fingerprint_switch.setOnCheckedChangeListener { _, isChecked ->
            if (!settingsSwitch) {
                requestPermission(
                    PermissionUtil.PERMISSION_FOR_FINGERPRINT,
                    R.string.permission_rationale_camera
                ) { permission, permissionGranted ->
                    val deleteListener = object : ConcurrentTaskListener() {
                        override fun onStateChanged(task: ConcurrentTask, state: Int) {
                            if (state == ConcurrentTask.STATE_ERROR) {
                                //loeschen fehlgeschlafen -> switch true
                                LogUtil.w(KeyController::class.java.simpleName, "Delete biometric key failed")
                                setCompoundButtonWithoutTriggeringListener(
                                    preferences_password_fingerprint_switch,
                                    true
                                )
                            } else {
                                setCompoundButtonWithoutTriggeringListener(
                                    preferences_password_fingerprint_switch,
                                    false
                                )
                                simsMeApplication.preferencesController.setBiometricAuthEnabled(false)
                            }
                        }
                    }

                    if (permission == PermissionUtil.PERMISSION_FOR_FINGERPRINT && permissionGranted) {
                        if (isChecked) {
                            showFingerprintFragment()
                        } else {
                            accountController.disableBiometricAuthentication(deleteListener)
                        }
                    } else {
                        setCompoundButtonWithoutTriggeringListener(preferences_password_fingerprint_switch, false)
                        accountController.disableBiometricAuthentication(deleteListener)
                    }
                }
            }
        }
    }

    private fun handleDeleteDataClick(isChecked: Boolean) {
        try {
            preferencesController.setDeleteDataAfterTries(isChecked)
        } catch (le: LocalizedException) {
            LogUtil.w(this.javaClass.name, le.identifier, le)
        }

        updateOptions()
    }

    private fun handleRecoveryCodeClick(isChecked: Boolean) {
        if (!appConnectivity.isConnected()) {
            Toast.makeText(
                this, R.string.backendservice_internet_connectionFailed,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        preferencesController.setRecoveryCodeEnabled(isChecked, true)
    }

    private fun handleCheckPasswordOnStartupClick(isChecked: Boolean) {
        if (!isChecked) {
            val positiveClickedListener = DialogInterface.OnClickListener { _, _ -> setPasswordEnabled(false) }

            val negativeClickedListener = DialogInterface.OnClickListener { _, _ ->
                preferences_password_enable_switch.tag = false
                setCompoundButtonWithoutTriggeringListener(preferences_password_enable_switch, true)
            }

            DialogBuilderUtil.buildResponseDialog(
                this,
                resources.getString(R.string.settings_dontAskForPassword_alert),
                resources.getString(R.string.settings_dontAskForPassword_alert_title),
                positiveClickedListener, negativeClickedListener
            ).show()
        } else {
            setPasswordEnabled(true)
        }
    }

    fun handleAskAfterClick(@Suppress("UNUSED_PARAMETER") v: View?) {
        val items = arrayOf<CharSequence>("0", "1", "5", "10")
        val builder = AlertDialog.Builder(this)
        builder.setTitle(this.getString(R.string.password_settings_dialog_request_password))
            .setItems(items, object : DialogInterface.OnClickListener {
                override fun onClick(
                    dialog: DialogInterface,
                    id: Int
                ) {
                    try {
                        val minutes = Integer.parseInt(items[id] as String)

                        preferencesController.setCheckPasswordAfterMinutes(minutes)
                        preferences_password_ask_after_text.text = items[id]
                    } catch (e: LocalizedException) {
                        LogUtil.w(this.javaClass.name, e.message, e)
                        Toast.makeText(
                            this@PreferencesPasswordActivity, R.string.settings_save_setting_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }).show()
    }

    @SuppressLint("InflateParams")
    fun handleDeleteAfterClick(@Suppress("UNUSED_PARAMETER") v: View?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_retry_password_count, null)
        val np = dialogView.findViewById<NumberPicker>(R.id.number_picker).apply {
            minValue = 1
            maxValue = 10
            wrapSelectorWheel = false
        }

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView).setPositiveButton(android.R.string.ok, object : DialogInterface.OnClickListener {
            override fun onClick(
                dialog: DialogInterface,
                id: Int
            ) {
                try {
                    val value = np.value

                    preferencesController.setNumberOfPasswordTries(value)
                    preferences_password_delete_after_text.text = value.toString()
                } catch (le: LocalizedException) {
                    LogUtil.e(this.javaClass.name, le.identifier, le)
                }
            }
        })
        val dialog = builder.create()
        DialogBuilderUtil.colorizeButtons(this, dialog)
        dialog.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CHANGE_PASSWORD_AFTER_LOGIN -> {
                val duration = Toast.LENGTH_SHORT
                val textInt = if (resultCode == Activity.RESULT_OK)
                    R.string.change_password_success
                else
                    R.string.change_password_cancel
                val toast = Toast.makeText(this, getString(textInt), duration)

                toast.show()
            }
            else -> {
                LogUtil.w(this.javaClass.name, LocalizedException.UNDEFINED_ARGUMENT)
            }
        }
    }

    fun handleChangePasswordClick(@Suppress("UNUSED_PARAMETER") v: View?) {
        val intent = Intent(this, SetPasswordActivity::class.java)
        startActivityForResult(intent, REQUEST_CHANGE_PASSWORD_AFTER_LOGIN)
    }

    private fun setPasswordEnabled(isEnabled: Boolean) {
        val listener = object : PreferencesController.OnPreferenceChangedListener {
            override fun onPreferenceChangedSuccess() {
                dismissIdleDialog()
            }

            override fun onPreferenceChangedFail() {
                dismissIdleDialog()
                accountController.clearPassword()
            }
        }

        showIdleDialog(-1)
        try {
            preferencesController.setPasswordEnabled(accountController.password, isEnabled, listener)
        } catch (le: LocalizedException) {
            LogUtil.w(this.javaClass.name, le.identifier, le)
            setCompoundButtonWithoutTriggeringListener(preferences_password_enable_switch, !isEnabled)
            dismissIdleDialog()
        }

        updateOptions()
    }

    private fun updateOptions() {
        if (!preferences_password_enable_switch.isChecked) {
            preferences_password_request_ask_after_layout.alpha = 0.5f
            preferences_password_request_ask_after_layout.isEnabled = false
        } else {
            preferences_password_request_ask_after_layout.alpha = 1f
            preferences_password_request_ask_after_layout.isEnabled = true
        }

        if (!preferences_password_delete_data_switch.isChecked) {
            preferences_password_delete_after_layout.alpha = 0.5f
            preferences_password_delete_after_layout.isEnabled = false
        } else {
            preferences_password_delete_after_layout.alpha = 1f
            preferences_password_delete_after_layout.isEnabled = true
        }
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_preferences_password
    }

    override fun onBackPressed() {
        accountController.clearPassword()
        super.onBackPressed()
    }

    private fun onCloseFingerprintClicked() {
        fingerprintFragment?.apply {
            dismiss()
            cancelListening()
        }
        simsMeApplication.preferencesController.setBiometricAuthEnabled(false)
        setCompoundButtonWithoutTriggeringListener(preferences_password_fingerprint_switch, false)
    }

    private fun showFingerprintFragment() {
        if (fingerprintFragment == null) {
            fingerprintFragment = FingerprintFragment.newInstance(FingerprintFragment.MODE_AUTH, this)
        }
        if (fingerprintFragment?.isVisible == false && fingerprintFragment?.isAdded == false) {
            fingerprintFragment?.show(supportFragmentManager, FINGERPRINT_FRAGMENT)
        }
    }

    override fun onAuthenticationSucceeded(cipher: Cipher?) {
        if (cipher != null) {
            val fingerprintListener = object : KeyController.OnKeysSavedListener {
                override fun onKeysSaveComplete() {
                    simsMeApplication.preferencesController.setBiometricAuthEnabled(true)
                }

                override fun onKeysSaveFailed() {
                    simsMeApplication.preferencesController.setBiometricAuthEnabled(false)
                    setCompoundButtonWithoutTriggeringListener(preferences_password_fingerprint_switch, false)
                }
            }

            simsMeApplication.accountController.enableBiometricAuthenticationPre28(cipher, fingerprintListener)
        }
    }

    override fun onAuthenticationCancelled() {
        onCloseFingerprintClicked()
    }
}
