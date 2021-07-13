// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.preferences

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.CompoundButton
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import eu.ginlo_apps.ginlo.BaseActivity
import eu.ginlo_apps.ginlo.ChatBackgroundActivity
import eu.ginlo_apps.ginlo.ConfigureBackupActivity
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.preferences.base.PreferencesBaseActivity
import eu.ginlo_apps.ginlo.controller.AccountController
import eu.ginlo_apps.ginlo.controller.ChatImageController
import eu.ginlo_apps.ginlo.controller.PreferencesController
import eu.ginlo_apps.ginlo.controller.contracts.BackupUploadListener
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.router.Router
import eu.ginlo_apps.ginlo.util.*
import eu.ginlo_apps.ginlo.view.CameraView
import kotlinx.android.synthetic.main.activity_preferences_chats.chat_settings_image_view_background_thumbnail
import kotlinx.android.synthetic.main.activity_preferences_chats.preferences_chats_switch_send_sound
import kotlinx.android.synthetic.main.activity_preferences_chats.preferences_chats_textview_backup_hint
import kotlinx.android.synthetic.main.activity_preferences_chats.preferences_chats_textview_image_quality
import kotlinx.android.synthetic.main.activity_preferences_chats.preferences_chats_textview_video_quality
import kotlinx.android.synthetic.main.activity_preferences_chats.preferences_pchats_switch_receive_sound
import kotlinx.android.synthetic.main.activity_preferences_chats.preferences_privacy_switch_sd_sound
import kotlinx.android.synthetic.main.activity_preferences_chats.preferences_switch_save_media
import javax.inject.Inject

class PreferencesChatsActivity : PreferencesBaseActivity(), BackupUploadListener {

    private val accountController: AccountController by lazy { simsMeApplication.accountController }

    private val preferencesController: PreferencesController by lazy { simsMeApplication.preferencesController }

    private val chatImageController: ChatImageController by lazy { simsMeApplication.chatImageController }

    @Inject
    internal lateinit var router: Router

    override fun onCreateActivity(savedInstanceState: Bundle?) {

        val slideHeight = resources.getDimension(R.dimen.preferences_slideheight).toInt()

        mAnimationSlideIn = TranslateAnimation(0f, 0f, slideHeight.toFloat(), 0f)
        mAnimationSlideOut = TranslateAnimation(0f, 0f, 0f, slideHeight.toFloat())

        mAnimationSlideIn.duration = BaseActivity.ANIMATION_DURATION.toLong()
        mAnimationSlideOut.duration = BaseActivity.ANIMATION_DURATION.toLong()

        with(DecelerateInterpolator()) {
            mAnimationSlideIn.interpolator = this
            mAnimationSlideOut.interpolator = this
        }

        preferences_switch_save_media.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                if (!settingsSwitch) {
                    try {
                        preferencesController.setSaveMediaToGallery(isChecked)
                    } catch (e: LocalizedException) {
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        LogUtil.w(this.javaClass.name, e.message, e)
                    }
                }
            }
        })

        preferences_privacy_switch_sd_sound.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                if (!settingsSwitch) {
                    try {
                        preferencesController.setPlaySdSound(isChecked)
                    } catch (e: LocalizedException) {
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        LogUtil.w(this.javaClass.name, e.message, e)
                    }
                }
            }
        })

        preferences_chats_switch_send_sound.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                if (!settingsSwitch) {
                    try {
                        preferencesController.setPlaySendSound(isChecked)
                    } catch (e: LocalizedException) {
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        LogUtil.w(this.javaClass.name, e.message, e)
                    }
                }
            }
        })

        preferences_pchats_switch_receive_sound.setOnCheckedChangeListener { _, isChecked ->
            if (!settingsSwitch) {
                preferencesController.setMessageReceivedSound(isChecked)
            }
        }

        if (preferencesController.getDisableBackup()) {
            val backupLayout = findViewById<View>(R.id.preferences_chats_backup_container)
            backupLayout.visibility = View.GONE
        }

        if (!preferencesController.isOpenInAllowed() || preferencesController.isSaveMediaToCameraRollDisabled()) {
            preferences_switch_save_media.visibility = View.GONE
        }
    }

    override fun onResumeActivity() {
        try {
            setImageQualityView()
            setVideoQualityView()

            setCompoundButtonWithoutTriggeringListener(
                preferences_switch_save_media,
                preferencesController.getSaveMediaToGallery()
            )
            setCompoundButtonWithoutTriggeringListener(
                preferences_privacy_switch_sd_sound,
                preferencesController.getPlaySdSound()
            )
            setCompoundButtonWithoutTriggeringListener(
                preferences_chats_switch_send_sound,
                preferencesController.getPlaySendSound()
            )
            setCompoundButtonWithoutTriggeringListener(
                preferences_pchats_switch_receive_sound,
                preferencesController.getMessageReceivedSound()
            )

            setChatBackgroundPreview()
            setBackupText()
            if (preferencesController.getDisableBackup()) {
                accountController.registerBackupUploadListener(this)
            }
        } catch (e: LocalizedException) {
            LogUtil.w(this.javaClass.name, e.message, e)
        }
    }

    override fun onPauseActivity() {
        super.onPauseActivity()

        if (preferencesController.getDisableBackup()) {
            accountController.unRegisterBackupUploadListener(this)
        }
    }

    override fun onBackupUploaded() {
        setBackupText()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        setChatBackgroundPreview()
        if (requestCode == REQUEST_CODE_SELECT_FROM_ALBUM && resultCode == Activity.RESULT_OK) {
            val pictureUri = data?.data

            if (pictureUri != null) {
                val mimeUtil = MimeUtil(this)

                if (mimeUtil.allowedImageMimeTypes?.contains(mimeUtil.getMimeType(pictureUri).orEmpty().toLowerCase()) == false) {
                    Toast.makeText(
                        this@PreferencesChatsActivity,
                        getString(R.string.chats_addAttachment_wrong_format_or_error),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Intent(this, ChatBackgroundActivity::class.java).apply {
                        this.data = pictureUri
                    }.let {
                        startActivity(it)
                    }
                }
            }
        } else {
            LogUtil.w(this.javaClass.name, LocalizedException.UNDEFINED_ARGUMENT)
        }
    }

    @SuppressLint("InflateParams")
    fun handleImageQualityClick(@Suppress("UNUSED_PARAMETER") view: View) {
        try {
            val oldQuality = preferencesController.getImageQuality()

            val dialogView = layoutInflater.inflate(R.layout.dialog_choose_image_quality, null)
            val np = dialogView.findViewById<NumberPicker>(R.id.number_picker).apply {
                minValue = 0
                maxValue = 3
                wrapSelectorWheel = false
                isClickable = true
                displayedValues = arrayOf(
                    getString(R.string.settings_image_quality_s),
                    getString(R.string.settings_image_quality_m),
                    getString(R.string.settings_image_quality_l),
                    getString(R.string.settings_image_quality_xl)
                )
                value = oldQuality
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            }

            val builder = AlertDialog.Builder(this).apply {
                setTitle(R.string.settings_image_quality_title)
                setView(dialogView)
                    .setPositiveButton(
                        android.R.string.ok,
                        object : DialogInterface.OnClickListener {
                            override fun onClick(
                                dialog: DialogInterface,
                                id: Int
                            ) {
                                try {
                                    val value = np.value

                                    preferencesController.setImageQuality(value)
                                    setImageQualityView()
                                } catch (e: LocalizedException) {
                                    LogUtil.e(this.javaClass.name, e.message, e)
                                }
                            }
                        })
            }

            val dialog = builder.create()
            DialogBuilderUtil.colorizeButtons(this, dialog)
            dialog.show()
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }

    @SuppressLint("InflateParams")
    fun handleVideoQualityClick(@Suppress("UNUSED_PARAMETER") view: View) {
        try {
            val oldQuality = preferencesController.getVideoQuality()

            val dialogView = layoutInflater.inflate(R.layout.dialog_choose_image_quality, null)
            val np = dialogView.findViewById<NumberPicker>(R.id.number_picker).apply {
                minValue = CameraView.MODE_LOW_QUALITY
                maxValue = CameraView.MODE_VERY_HIGH_QUALITY
                wrapSelectorWheel = false
                displayedValues = arrayOf(
                    getString(R.string.settings_video_quality_s),
                    getString(R.string.settings_video_quality_m),
                    getString(R.string.settings_video_quality_l),
                    getString(R.string.settings_video_quality_xl)
                )
                value = oldQuality
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            }

            val builder = AlertDialog.Builder(this).apply {
                setTitle(R.string.settings_video_quality_title)
                setView(dialogView)
                    .setPositiveButton(
                        android.R.string.ok,
                        object : DialogInterface.OnClickListener {
                            override fun onClick(
                                dialog: DialogInterface,
                                id: Int
                            ) {
                                try {
                                    val value = np.value
                                    preferencesController.setVideoQuality(value)
                                    setVideoQualityView()
                                } catch (e: LocalizedException) {
                                    LogUtil.e(this.javaClass.name, e.message, e)
                                }
                            }
                        })
            }
            val dialog = builder.create()
            DialogBuilderUtil.colorizeButtons(this, dialog)
            dialog.show()
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }

    fun handleChatBackgroundClick(@Suppress("UNUSED_PARAMETER") view: View) {
        if (!mBottomSheetMoving) {
            val bottomSheetLayoutResourceID: Int = if (chatImageController.isBackgroundSet) {
                R.layout.dialog_chat_background_context_menu_layout
            } else {
                R.layout.dialog_chat_background_context_menu_layout_no_reset
            }

            openBottomSheet(
                bottomSheetLayoutResourceID,
                R.id.preferences_linear_layout_bottom_sheet_container
            )
        }
    }

    private fun setImageQualityView() {
        try {
            when (preferencesController.getImageQuality()) {
                0 -> {
                    preferences_chats_textview_image_quality.setText(R.string.settings_image_quality_s)
                }
                1 -> {
                    preferences_chats_textview_image_quality.setText(R.string.settings_image_quality_m)
                }
                2 -> {
                    preferences_chats_textview_image_quality.setText(R.string.settings_image_quality_l)
                }
                3 -> {
                    preferences_chats_textview_image_quality.setText(R.string.settings_image_quality_xl)
                }
            }
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }

    private fun setVideoQualityView() {
        try {
            when (preferencesController.getVideoQuality()) {
                CameraView.MODE_LOW_QUALITY -> {
                    preferences_chats_textview_video_quality.setText(R.string.settings_video_quality_s)
                }
                CameraView.MODE_MEDIUM_QUALITY -> {
                    preferences_chats_textview_video_quality.setText(R.string.settings_video_quality_m)
                }
                CameraView.MODE_HIGH_QUALITY -> {
                    preferences_chats_textview_video_quality.setText(R.string.settings_video_quality_l)
                }
                CameraView.MODE_VERY_HIGH_QUALITY -> {
                    preferences_chats_textview_video_quality.setText(R.string.settings_video_quality_xl)
                }
            }
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }
    }

    private fun setChatBackgroundPreview() {
        try {
            val background = simsMeApplication.chatImageController.background

            if (background != null) {
                chat_settings_image_view_background_thumbnail.visibility = View.VISIBLE
                chat_settings_image_view_background_thumbnail.setImageBitmap(background)
            } else {
                chat_settings_image_view_background_thumbnail.visibility = View.GONE
            }
        } catch (e: LocalizedException) {
            LogUtil.w(this.javaClass.name, e.message, e)
        }
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

                preferences_chats_textview_backup_hint.text = text
            }
            //preferences_chats_textview_backup_hint.setTextColor(resources.getColor(R.color.kColorSecLevelHigh))
            preferences_chats_textview_backup_hint.setTextColor(ContextCompat.getColor(this, R.color.kColorSecLevelHigh))
        } else {
            preferences_chats_textview_backup_hint.text =
                getString(R.string.settings_chats_create_backup)
        }
    }

    fun handleBackgroundFromStockClick(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity(Intent(this, ChatBackgroundActivity::class.java))
        closeBottomSheet(null)
    }

    fun handleBackgroundFromAlbumClick(@Suppress("UNUSED_PARAMETER") view: View) {
        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }.let {
            router.startExternalActivityForResult(it, REQUEST_CODE_SELECT_FROM_ALBUM)
            closeBottomSheet(null)
        }
    }

    fun handleBackgroundResetClick(@Suppress("UNUSED_PARAMETER") view: View) {
        chatImageController.resetBackground()
        closeBottomSheet(null)
        setChatBackgroundPreview()
    }

    fun handleBackupClick(@Suppress("UNUSED_PARAMETER") view: View) {
        startActivity(Intent(this, ConfigureBackupActivity::class.java))
    }

    override fun getActivityLayout(): Int =
        R.layout.activity_preferences_chats

    companion object {
        private const val REQUEST_CODE_SELECT_FROM_ALBUM = 254
    }
}
