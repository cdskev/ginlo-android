// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.preferences.base.PreferencesBaseActivity
import eu.ginlo_apps.ginlo.controller.PreferencesController
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Preference
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.ColorUtil
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil
import kotlinx.android.synthetic.main.activity_preferences_media_download.preferences_media_download_textview_files
import kotlinx.android.synthetic.main.activity_preferences_media_download.preferences_media_download_textview_fotos
import kotlinx.android.synthetic.main.activity_preferences_media_download.preferences_media_download_textview_videos
import kotlinx.android.synthetic.main.activity_preferences_media_download.preferences_media_download_textview_voice

class PreferencesMediaDownloadActivity : PreferencesBaseActivity() {
    private val preferencesController: PreferencesController by lazy { simsMeApplication.preferencesController }

    override fun onCreateActivity(savedInstanceState: Bundle?) {}

    override fun getActivityLayout(): Int =
        R.layout.activity_preferences_media_download

    override fun onResumeActivity() {
        setAutomaticDownloadView()
    }

    private fun setAutomaticDownloadView() {
        try {

            setDisplayText(preferences_media_download_textview_fotos, preferencesController.automaticDownloadPicture)
            setDisplayText(preferences_media_download_textview_voice, preferencesController.automaticDownloadVoice)
            setDisplayText(preferences_media_download_textview_videos, preferencesController.automaticDownloadVideo)
            setDisplayText(preferences_media_download_textview_files, preferencesController.automaticDownloadFiles)
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }

    private fun setDisplayText(textView: TextView, currentSetting: Int) {
        when (currentSetting) {
            Preference.AUTOMATIC_DOWNLOAD_ALWAYS ->
                textView.setText(R.string.settings_config_automatic_download_always)
            Preference.AUTOMATIC_DOWNLOAD_WLAN ->
                textView.setText(R.string.settings_config_automatic_download_wlan)
            Preference.AUTOMATIC_DOWNLOAD_NEVER ->
                textView.setText(R.string.settings_config_automatic_download_never)
            else -> {
                textView.text = ""
                LogUtil.w(this.javaClass.simpleName, "Out of range setting $currentSetting")
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun handleAutomaticDownloadClickInternal(setter: (Int) -> Unit) {

        val dialogView = layoutInflater.inflate(R.layout.dialog_choose_image_quality, null)

        dialogView.findViewById<NumberPicker>(R.id.number_picker).apply {
            minValue = 0
            maxValue = 2
            wrapSelectorWheel = false
            isClickable = true
            displayedValues = arrayOf(
                getString(R.string.settings_config_automatic_download_always),
                getString(R.string.settings_config_automatic_download_wlan),
                getString(R.string.settings_config_automatic_download_never)
            )
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.settings_config_automatic_download)

        val onClickListener = DialogInterface.OnClickListener { _, _ ->
            try {
                setter(dialogView.findViewById<NumberPicker>(R.id.number_picker).value)
                setAutomaticDownloadView()
            } catch (e: LocalizedException) {
                LogUtil.e(this.javaClass.name, e.message, e)
            }
        }
        builder.setView(dialogView).setPositiveButton(android.R.string.ok, onClickListener)
        val dialog = builder.create()
        DialogBuilderUtil.colorizeButtons(this, dialog)
        dialog.show()
    }

    fun handleAutomaticDownloadPictureClick(@Suppress("UNUSED_PARAMETER") view: View) {
        handleAutomaticDownloadClickInternal { selectedValue: Int ->
            preferencesController.automaticDownloadPicture = selectedValue
        }
    }

    fun handleAutomaticDownloadVoiceClick(@Suppress("UNUSED_PARAMETER") view: View) {
        handleAutomaticDownloadClickInternal { selectedValue: Int ->
            preferencesController.automaticDownloadVoice = selectedValue
        }
    }

    fun handleAutomaticDownloadVideoClick(@Suppress("UNUSED_PARAMETER") view: View) {
        handleAutomaticDownloadClickInternal { selectedValue: Int ->
            preferencesController.automaticDownloadVideo = selectedValue
        }
    }

    fun handleAutomaticDownloadFilesClick(@Suppress("UNUSED_PARAMETER") view: View) {
        handleAutomaticDownloadClickInternal { selectedValue: Int ->
            preferencesController.automaticDownloadFiles = selectedValue
        }
    }

    fun handleAutomaticDownloadResetClick(@Suppress("UNUSED_PARAMETER") view: View) {
        try {
            preferencesController.automaticDownloadPicture = Preference.AUTOMATIC_DOWNLOAD_ALWAYS
            preferencesController.automaticDownloadVoice = Preference.AUTOMATIC_DOWNLOAD_WLAN
            preferencesController.automaticDownloadVideo = Preference.AUTOMATIC_DOWNLOAD_WLAN
            preferencesController.automaticDownloadFiles = Preference.AUTOMATIC_DOWNLOAD_WLAN
            setAutomaticDownloadView()
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }
}
