// Copyright (c) 2020-2022 ginlo.net GmbH
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
import eu.ginlo_apps.ginlo.BaseActivity
import eu.ginlo_apps.ginlo.ChatBackgroundActivity
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.ViewAttachmentActivity
import eu.ginlo_apps.ginlo.activity.preferences.base.PreferencesBaseActivity
import eu.ginlo_apps.ginlo.controller.AccountController
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.router.Router
import eu.ginlo_apps.ginlo.util.*
import eu.ginlo_apps.ginlo.view.CameraView
import kotlinx.android.synthetic.main.activity_preferences_chats.*
import javax.inject.Inject

class PreferencesChatsActivity : PreferencesBaseActivity() {

    private val accountController: AccountController by lazy { simsMeApplication.accountController }

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
                        // KS: Always delete LOCAL_MEDIA_URI_PREF when button changed to allow for new setting
                        preferencesController.getSharedPreferences().edit().remove(ViewAttachmentActivity.LOCAL_MEDIA_URI_PREF).apply()
                    } catch (e: LocalizedException) {
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        LogUtil.w(this.javaClass.name, e.message, e)
                    }
                }
            }
        })

        preferences_switch_use_internal_pdf_viewer.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                if (!settingsSwitch) {
                    try {
                        preferencesController.setUseInternalPdfViewer(isChecked)
                    } catch (e: LocalizedException) {
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        LogUtil.w(this.javaClass.name, e.message, e)
                    }
                }
            }
        })

        preferences_switch_animate_rich_content.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                if (!settingsSwitch) {
                    try {
                        preferencesController.setAnimateRichContent(isChecked)
                        imageController.clearImageCaches(true, true);
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
                        preferencesController.setPlayMessageSdSound(isChecked)
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
                        preferencesController.setPlayMessageSendSound(isChecked)
                    } catch (e: LocalizedException) {
                        setCompoundButtonWithoutTriggeringListener(buttonView, !isChecked)
                        LogUtil.w(this.javaClass.name, e.message, e)
                    }
                }
            }
        })

        preferences_pchats_switch_receive_sound.setOnCheckedChangeListener { _, isChecked ->
            if (!settingsSwitch) {
                preferencesController.setPlayMessageReceivedSound(isChecked)
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
                preferences_switch_use_internal_pdf_viewer,
                preferencesController.getUseInternalPdfViewer()
            )
            setCompoundButtonWithoutTriggeringListener(
                preferences_switch_animate_rich_content,
                preferencesController.getAnimateRichContent()
            )
            setCompoundButtonWithoutTriggeringListener(
                preferences_privacy_switch_sd_sound,
                preferencesController.getPlayMessageSdSound()
            )
            setCompoundButtonWithoutTriggeringListener(
                preferences_chats_switch_send_sound,
                preferencesController.getPlayMessageSendSound()
            )
            setCompoundButtonWithoutTriggeringListener(
                preferences_pchats_switch_receive_sound,
                preferencesController.getPlayMessageReceivedSound()
            )

            setChatBackgroundPreview()

        } catch (e: LocalizedException) {
            LogUtil.w(this.javaClass.name, e.message, e)
        }
    }

    override fun onPauseActivity() {
        super.onPauseActivity()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        setChatBackgroundPreview()
        if (requestCode == REQUEST_CODE_SELECT_FROM_ALBUM && resultCode == Activity.RESULT_OK) {
            val pictureUri = data?.data

            if (pictureUri != null) {
                if (!MimeUtil.checkImageUriMimetype(this, pictureUri)) {
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
            val bottomSheetLayoutResourceID: Int = if (imageController.isAppBackgroundSet) {
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
            val background = imageController.appBackground

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
        imageController.resetAppBackground()
        closeBottomSheet(null)
        setChatBackgroundPreview()
    }

    override fun getActivityLayout(): Int =
        R.layout.activity_preferences_chats

    companion object {
        private const val REQUEST_CODE_SELECT_FROM_ALBUM = 254
    }
}
