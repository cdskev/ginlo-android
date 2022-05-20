// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.NumberPicker
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog
import eu.ginlo_apps.ginlo.BuildConfig
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.preferences.base.PreferencesBaseActivity
import eu.ginlo_apps.ginlo.controller.PreferencesController
import eu.ginlo_apps.ginlo.data.network.AppConnectivity
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil
import kotlinx.android.synthetic.main.activity_preferences_appearance.*
import javax.inject.Inject

class PreferencesAppearanceActivity : PreferencesBaseActivity() {

        companion object {
                private const val TAG = "PreferencesAppearanceActivity"
        }

        @Inject
        internal lateinit var appConnectivity: AppConnectivity

        override fun onCreateActivity(savedInstanceState: Bundle?) {}

        override fun getActivityLayout(): Int {
                return R.layout.activity_preferences_appearance
        }

        override fun onResumeActivity() {
                setThemeModeChooser()
                setThemeNameChooser()

                if(preferencesController.isThemeColorSettingLocked()) {
                        // Disable all configuration which affect color settings
                        val themeModeView = findViewById<RelativeLayout>(R.id.preferences_appearance_layout_theme_mode)
                        if(themeModeView != null) {
                                preferences_appearance_textview_theme_mode.text = "<LOCKED>"
                                themeModeView.isEnabled = false;
                        }

                        /* KS: There are only font size default themes so far. They may be applied even if we have a company layout
                        val themeNameView = findViewById<RelativeLayout>(R.id.preferences_appearance_layout_theme_name)
                        if(themeNameView != null) {
                                preferences_appearance_textview_theme_name.text = "<LOCKED>"
                                themeNameView.isEnabled = false;
                        }

                         */
                }
        }

        private fun setThemeModeChooser() {
                try {
                        when (preferencesController.getThemeMode()) {
                                PreferencesController.THEME_MODE_LIGHT ->
                                        preferences_appearance_textview_theme_mode.setText(R.string.settings_appearance_theme_mode_light)
                                PreferencesController.THEME_MODE_DARK ->
                                        preferences_appearance_textview_theme_mode.setText(R.string.settings_appearance_theme_mode_dark)
                                PreferencesController.THEME_MODE_AUTO ->
                                        preferences_appearance_textview_theme_mode.setText(R.string.settings_appearance_theme_mode_auto)
                                else -> {
                                        LogUtil.w(TAG, "setThemeModeChooser: Out of range " +
                                                preferencesController.getThemeMode() + ". Reset setting.")

                                        preferences_appearance_textview_theme_mode.setText(R.string.settings_appearance_theme_mode_light)
                                        preferencesController.setThemeMode(BuildConfig.DEFAULT_THEME_MODE)
                                        preferencesController.setThemeChanged(true)
                                }
                        }

                } catch (e: LocalizedException) {
                        LogUtil.e(TAG, "setThemeModeChooser: " + e.message)
                }
        }

        @SuppressLint("InflateParams")
        fun handleThemeModeClick(@Suppress("UNUSED_PARAMETER") view : View) {

                val dialogView = layoutInflater.inflate(R.layout.dialog_choose_theme_mode, null)

                dialogView.findViewById<NumberPicker>(R.id.theme_mode_picker).apply {
                        minValue = 0
                        maxValue = 2
                        wrapSelectorWheel = false
                        isClickable = true
                        displayedValues = arrayOf(
                                getString(R.string.settings_appearance_theme_mode_light),
                                getString(R.string.settings_appearance_theme_mode_dark),
                                getString(R.string.settings_appearance_theme_mode_auto)
                        )
                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                }

                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.settings_appearance_darkmode)

                val onClickListener = DialogInterface.OnClickListener { _, _ ->
                        try {
                                when (dialogView.findViewById<NumberPicker>(R.id.theme_mode_picker).value) {
                                        0 -> preferencesController.setThemeMode(PreferencesController.THEME_MODE_LIGHT)
                                        1 -> preferencesController.setThemeMode(PreferencesController.THEME_MODE_DARK)
                                        2 -> preferencesController.setThemeMode(PreferencesController.THEME_MODE_AUTO)
                                }
                                setThemeModeChooser()
                        } catch (e: LocalizedException) {
                                LogUtil.e(TAG, "handleThemeModeClick: " + e.message)
                        }
                        runOnUiThread { recreate() }

                }
                builder.setView(dialogView).setPositiveButton(android.R.string.ok, onClickListener)
                val dialog = builder.create()
                DialogBuilderUtil.colorizeButtons(this, dialog)
                dialog.show()
        }

        private fun setThemeNameChooser() {
                try {
                        when (preferencesController.getThemeName()) {
                                PreferencesController.THEME_NAME_DEFAULT ->
                                        preferences_appearance_textview_theme_name.setText(R.string.settings_appearance_theme_name_default)
                                PreferencesController.THEME_NAME_SMALL ->
                                        preferences_appearance_textview_theme_name.setText(R.string.settings_appearance_theme_name_small)
                                PreferencesController.THEME_NAME_LARGE ->
                                        preferences_appearance_textview_theme_name.setText(R.string.settings_appearance_theme_name_large)
                                else -> {
                                        LogUtil.w(TAG, "setThemeNameChooser: Out of range " +
                                                preferencesController.getThemeName() + ". Reset setting.")

                                        preferences_appearance_textview_theme_name.setText(R.string.settings_appearance_theme_name_default)
                                        preferencesController.setThemeName(BuildConfig.DEFAULT_THEME)
                                        preferencesController.setThemeChanged(true)
                                }
                        }

                } catch (e: LocalizedException) {
                        LogUtil.e(TAG, "setThemeNameChooser: " + e.message)
                }
        }

        @SuppressLint("InflateParams")
        fun handleThemeNameClick(@Suppress("UNUSED_PARAMETER") view : View) {

                val dialogView = layoutInflater.inflate(R.layout.dialog_choose_theme_mode, null)

                dialogView.findViewById<NumberPicker>(R.id.theme_mode_picker).apply {
                        minValue = 0
                        maxValue = 2
                        wrapSelectorWheel = false
                        isClickable = true
                        displayedValues = arrayOf(
                                getString(R.string.settings_appearance_theme_name_default),
                                getString(R.string.settings_appearance_theme_name_small),
                                getString(R.string.settings_appearance_theme_name_large)
                        )
                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                }

                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.settings_appearance_theme)

                val onClickListener = DialogInterface.OnClickListener { _, _ ->
                        try {
                                when (dialogView.findViewById<NumberPicker>(R.id.theme_mode_picker).value) {
                                        0 -> preferencesController.setThemeName(PreferencesController.THEME_NAME_DEFAULT)
                                        1 -> preferencesController.setThemeName(PreferencesController.THEME_NAME_SMALL)
                                        2 -> preferencesController.setThemeName(PreferencesController.THEME_NAME_LARGE)
                                }
                                setThemeNameChooser()
                        } catch (e: LocalizedException) {
                                LogUtil.e(TAG, "handleThemeNameClick: " + e.message)
                        }
                        runOnUiThread { recreate() }

                }
                builder.setView(dialogView).setPositiveButton(android.R.string.ok, onClickListener)
                val dialog = builder.create()
                DialogBuilderUtil.colorizeButtons(this, dialog)
                dialog.show()
        }

        fun handleDesignConfigResetClick(@Suppress("UNUSED_PARAMETER") view: View) {
                try {
                        preferencesController.setThemeMode(BuildConfig.DEFAULT_THEME_MODE)
                        preferencesController.setThemeName(BuildConfig.DEFAULT_THEME)
                        preferencesController.setThemeChanged(true)
                        setThemeModeChooser()
                        setThemeNameChooser()
                } catch (e: LocalizedException) {
                        LogUtil.e(TAG, "handleDesignConfigResetClick: " + e.message)
                }
                runOnUiThread { recreate() }
        }
}
