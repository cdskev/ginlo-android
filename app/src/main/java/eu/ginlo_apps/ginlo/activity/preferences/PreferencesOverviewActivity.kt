// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import eu.ginlo_apps.ginlo.BuildConfig
import eu.ginlo_apps.ginlo.ConfigureBackupActivity
import eu.ginlo_apps.ginlo.LoginActivity
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.*
import kotlinx.android.synthetic.main.activity_preferences_overview.*


class PreferencesOverviewActivity : NewBaseActivity() {
    companion object {
        private const val REQUEST_EDIT_PASSWORD_SETTINGS = 0x1f
    }

    var appearanceChanged : Boolean = false
    var othersChanged : Boolean = false
    val activity : Activity = this

    override fun onCreateActivity(savedInstanceState: Bundle?) {

        if (preferencesController.getDisableBackup()) {
            val backupLayout = findViewById<View>(R.id.preferences_chats_backup_container)
            backupLayout.visibility = View.GONE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && BuildConfig.USE_SYSTEM_ALERT_WINDOW_FOR_AVC) {
            if (!Settings.canDrawOverlays(this)) {
                LogUtil.d(this.javaClass.name, "onCreateActivity: PERMISSION_FOR_SYSTEM_ALERT_WINDOW not yet granted.")

                val message = mApplication.getString(R.string.permission_rationale_overlay)
                val title = this.getString(R.string.permission_rationale_title)
                val ok = mApplication.getString(R.string.std_ok)

                LogUtil.d(this.javaClass.name, "onCreateActivity: Ask user.")
                val builder = AlertDialog.Builder(activity)
                builder.setMessage(message)
                builder.setTitle(title)
                val onClickListener = DialogInterface.OnClickListener { _, _ ->
                    if (!Settings.canDrawOverlays(activity)) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"))
                        startActivityForResult(intent, 111)
                    }
                }
                builder.setPositiveButton(ok, onClickListener)
                val dialog = builder.create()
                dialog.setCancelable(false)
                dialog.setCanceledOnTouchOutside(false)
                DialogBuilderUtil.colorizeButtons(activity, dialog)
                dialog.show()
            } else {
                LogUtil.d(this.javaClass.name, "onCreateActivity: PERMISSION_FOR_SYSTEM_ALERT_WINDOW granted.")
            }
        }
    }

    override fun getActivityLayout(): Int =
        R.layout.activity_preferences_overview

    override fun onResumeActivity() {
        if(appearanceChanged || othersChanged) {
            appearanceChanged = false
            othersChanged = false
            runOnUiThread { recreate() }
        }

        setBackupText()
    }

    private fun setBackupText() {
        val latestDate = preferencesController.latestBackupDate
        if (latestDate > -1) {

            if (!preferencesController.latestBackupPath.isNullOrBlank()) {

                val dateString = DateUtil.getDateAndTimeStringFromMillis(latestDate)
                var text = getString(R.string.settings_backup_last) + " " + dateString

                val latestBuFileSize = preferencesController.latestBackupFileSize

                if (latestBuFileSize > -1) {
                    val fileSizeString = StringUtil.getReadableByteCount(latestBuFileSize)
                    text = "$text ($fileSizeString)"
                }

                preferences_textview_backup_hint.text = text
            }
            preferences_textview_backup_hint.setTextColor(ScreenDesignUtil.getInstance().getHighColor(simsMeApplication))
            preferences_textview_backup_hint.textSize = ScreenDesignUtil.getInstance().getNamedTextSize("statusTextSize", simsMeApplication)
        } else {
            preferences_textview_backup_hint.text =
                    getString(R.string.settings_chats_create_backup)
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

    fun handleBackupClick(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity(Intent(this, ConfigureBackupActivity::class.java))
    }

    fun handleAppearanceClick(@Suppress("UNUSED_PARAMETER") v: View) {
        startActivity(Intent(this, PreferencesAppearanceActivity::class.java))
        // Make life easier: Always assume changes in app apprearance
        appearanceChanged = true
    }
    fun handleOthersClick(@Suppress("UNUSED_PARAMETER") v: View) {
        startActivity(Intent(this, PreferencesOthersActivity::class.java))
        // Make life easier: Always assume changes in app others
        othersChanged = true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_EDIT_PASSWORD_SETTINGS -> {
                if (resultCode == Activity.RESULT_OK) {
                    startActivity(Intent(this, PreferencesPasswordActivity::class.java))
                }
            }
            111 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (Settings.canDrawOverlays(activity)) {
                        LogUtil.d(this.javaClass.name, "onActivityResult: PERMISSION_FOR_SYSTEM_ALERT_WINDOW granted.")
                    } else {
                        LogUtil.d(this.javaClass.name, "onActivityResult: PERMISSION_FOR_SYSTEM_ALERT_WINDOW not granted.")
                        Toast.makeText(activity, "Permission denied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> {
                LogUtil.w(this.javaClass.name, LocalizedException.UNDEFINED_ARGUMENT)
            }
        }
    }
}