// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.media.MediaRecorder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.chat.ChatInputActivity
import eu.ginlo_apps.ginlo.appendText
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.insertText
import eu.ginlo_apps.ginlo.model.chat.*
import eu.ginlo_apps.ginlo.themedInflater
import eu.ginlo_apps.ginlo.util.*
import eu.ginlo_apps.ginlo.util.MimeUtil.MIMETYPE_NOT_FOUND
import eu.ginlo_apps.ginlo.util.TimeDisplayUtil.OnClockStoppedHandler
import kotlinx.android.synthetic.main.chat_item_comment_layout_chatinput.*
import kotlinx.android.synthetic.main.fragment_chat_input.*
import kotlinx.android.synthetic.main.fragment_chat_input_default.*
import kotlinx.android.synthetic.main.fragment_chat_input_preview.*
import kotlinx.android.synthetic.main.fragment_chat_input_recording.*
import java.io.FileNotFoundException

class ChatInputFragment : Fragment(), OnClockStoppedHandler {

    private lateinit var activity: ChatInputActivity

    private var clockRecording: TimeDisplayUtil? = null
    private var clockPlayback: TimeDisplayUtil? = null
    private var isPlaying = false
    private var simpleUi: Boolean = false
    private var audioPlayer: AudioPlayer? = null
    private var priorityEnabled: Boolean = false
    private var destructionViewMode: Boolean = false
    private var startText: String? = null
    private var hasAudioPermission: Boolean = false
    private var showKeyboardAfterClosingDestructionPicker = false
    private var hideAudioRecord: Boolean = false

    var isRecording = false
        private set
    var isDestructionViewShown: Boolean = false
        private set
    var destructionEnabled: Boolean = false
        private set
    var timerEnabled: Boolean = false
        private set
    var emojiEnabled: Boolean = false
        private set

    var allowSendWithEmptyMessage: Boolean = false

    private val voiceRecorder: AudioRecorder by lazy {
        AudioRecorder(FileUtil(activity).tempFile)
    }

    override fun onCreateView(
        inflater1: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        activity = requireActivity() as ChatInputActivity
        return LayoutInflater.from(activity)
            .themedInflater(activity)
            .inflate(R.layout.fragment_chat_input, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        resetUI()
        initSimpleUI()
        initClickListeners()
        initChatBoxListener()
        paintDestructionPicker(SelfdestructionFragment.PICKER_MODE_DESTRUCTION)
        paintEmojiPicker()
    }

    override fun onPause() {
        super.onPause()
        releaseAudio()
    }

    override fun onStart() {
        super.onStart()
        hasAudioPermission = PermissionUtil.hasPermission(activity, android.Manifest.permission.RECORD_AUDIO)
    }

    private fun initSimpleUI() {
        if (simpleUi) {
            activity.showChatInputFabButton()
            chat_add_button.visibility = View.GONE
            chat_right_button.visibility = View.GONE
            chat_send_text_button.visibility = View.VISIBLE
            chat_send_text_button.isEnabled = true
        } else {
            chat_add_button.visibility = View.VISIBLE
            chat_right_button.visibility = View.VISIBLE
            chat_send_text_button.visibility = View.GONE
        }
    }

    private fun checkMicrophonePermission() {
        hasAudioPermission = false
        if (activity.canSendMedia() &&
            !hideAudioRecord &&
            !((requireActivity() as ChatInputActivity).simsMeApplication).preferencesController.isMicrophoneDisabled()
        ) {
            activity.requestPermission(
                PermissionUtil.PERMISSION_FOR_RECORD_AUDIO,
                R.string.permission_rationale_rec_audio
            ) { permission, permissionGranted ->
                if (permission == PermissionUtil.PERMISSION_FOR_RECORD_AUDIO) {
                    hasAudioPermission = permissionGranted
                    if (!permissionGranted)
                        view?.let { Snackbar.make(it, R.string.permission_rationale_rec_audio_phone_settings, Snackbar.LENGTH_SHORT).show() }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initClickListeners() {
        chat_add_button.setOnClickListener {
            activity.handleAddAttachmentClick()
        }

        chat_emoji_button.setOnClickListener {
            val wasEnabled = emojiEnabled
            showEmojiPicker(!wasEnabled)
            showKeyboard(wasEnabled)
        }

        chat_send_text_button.setOnClickListener {
            sendTextMessage()
        }

        chat_right_button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startAudioRecording()
                }
                MotionEvent.ACTION_UP -> {
                    stopAudioRecording()
                }
            }
            false
        }

        chat_play_rec_button.setOnClickListener {
            if (isPlaying) stopAudioPlayback()
            else startAudioPlayback()
        }

        chat_delete_rec_button.setOnClickListener {
            releaseAudio()
            resetUI()
        }

        chat_send_rec_button.setOnClickListener {
            sendAudioMessage()
            releaseAudio()
            resetUI()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initChatBoxListener() {
        chat_edit_text_input.setOnKeyListener(View.OnKeyListener { _, keyCode, event ->
            if (isLandscape() && event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                sendTextMessage()
                showKeyboard(false)
                return@OnKeyListener true
            }
            false
        })

        chat_edit_text_input.setOnTouchListener { _, event ->
            var didHandleEvent = false

            when(event.action) {
                MotionEvent.ACTION_DOWN -> {

                    activity.scrollIfLastChatItemIsNotShown()

                    if (!KeyboardUtil.isKeyboardVisible(requireActivity())) {
                        showKeyboard(true)
                        // KS: Setting this to true often shows text selector on newer devices (?)
                        //didHandleEvent = true
                    }
                    if (emojiEnabled) {
                        showEmojiPicker(false)
                        didHandleEvent = true
                    }
                }

                /*
                MotionEvent.ACTION_UP -> {
                    view.performClick()
                }
                 */
            }

            didHandleEvent
        }

        chat_edit_text_input.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

            override fun afterTextChanged(s: Editable) {
                if (s.isNotEmpty() || allowSendWithEmptyMessage) {
                    activity.setOnlineStateToTyping()
                    activity.showChatInputFabButton()
                    activity.scrollIfLastChatItemIsNotShown()
                    chat_send_text_button.visibility = View.VISIBLE
                    chat_send_text_button.isEnabled = true
                    chat_right_button.visibility = View.GONE

                    if (!emojiEnabled)
                        showDestructionPicker(false, false)
                } else {
                    activity.setOnlineStateToOnline()
                    activity.hideChatInputFabButton()
                    chat_send_text_button.visibility = View.GONE
                    chat_right_button.visibility = View.VISIBLE
                    resetDestructionInfoContainer()
                }
            }
        })
    }

    private fun resetUI() {
        fragment_chat_default_layout.visibility = View.VISIBLE
        fragment_chat_recording_layout.visibility = View.GONE
        fragment_chat_playback_layout.visibility = View.GONE
        activity.hideChatInputFabButton()
        if (isRecording)
            stopAudioRecording()
        if (isPlaying)
            stopAudioPlayback()
    }

    private fun showAudioRecordingUI() {
        fragment_chat_default_layout.visibility = View.GONE
        fragment_chat_recording_layout.visibility = View.VISIBLE
        fragment_chat_playback_layout.visibility = View.GONE
    }

    fun showAudioPreviewUI() {
        if (voiceRecorder.recordDurationMillis >= 1000) {
            fragment_chat_default_layout.visibility = View.GONE
            fragment_chat_recording_layout.visibility = View.GONE
            fragment_chat_playback_layout.visibility = View.VISIBLE
            activity.showChatInputFabButton()
        } else {
            fragment_chat_default_layout.visibility = View.VISIBLE
            fragment_chat_recording_layout.visibility = View.GONE
            fragment_chat_playback_layout.visibility = View.GONE
            activity.hideChatInputFabButton()
        }
    }

    private fun startAudioRecording() {
        if (hasAudioPermission) {
            showAudioRecordingUI()
            isRecording = true
            clockRecording = TimeDisplayUtil(rec_clock, OnClockStoppedHandler { showAudioPreviewUI() })
            clockRecording?.start()

            try {
                voiceRecorder.startRec { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)
                        showAudioPreviewUI()
                }
            } catch (e: LocalizedException) {
                Snackbar.make(view as View, e.localizedMessage as CharSequence, Snackbar.LENGTH_SHORT).show()
            }
        } else {
            checkMicrophonePermission()
        }
    }

    private fun stopAudioRecording() {
        if (hasAudioPermission) {
            showAudioPreviewUI()
            clockRecording?.stop()
            isRecording = false
            voiceRecorder.stopRec()
            requestFocusForInput()
        }
    }

    private fun startAudioPlayback() {
        isPlaying = true
        clockPlayback = TimeDisplayUtil(playback_clock, OnClockStoppedHandler { stopAudioPlayback() })
        try {
            audioPlayer = AudioPlayer(voiceRecorder.uri, activity)
            audioPlayer?.let {
                it.play()
                clockPlayback?.start(it.duration)
            }
            chat_play_rec_button.setImageDrawable(
                ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.btn_sound_record_active
                )
            )
            chat_play_rec_button.contentDescription =
                resources.getString(R.string.content_description_chatinput_stop_voice_message)
        } catch (fnf: FileNotFoundException) {
            showAudioPreviewUI()
            isPlaying = false
        }
    }

    private fun stopAudioPlayback() {
        isPlaying = false
        chat_play_rec_button.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_play))
        chat_play_rec_button.contentDescription =
            resources.getString(R.string.content_description_chatinput_play_voice_message)

        clockPlayback?.stop()
        audioPlayer?.pause()
    }

    private fun sendTextMessage() {
        val msg = chat_edit_text_input.text.toString()
        chat_send_text_button.isEnabled = false

        if ((allowSendWithEmptyMessage || chat_edit_text_input.text?.isBlank() == false) &&
            activity.handleSendMessageClick(msg)
        ) {
            chat_edit_text_input.setText("")

            activity.resetChatInputFabButton()
            activity.hideChatInputFabButton()
            resetDestructionInfoContainer()
            chat_input_destruction_info_container.visibility = View.GONE
        }
    }

    private fun sendAudioMessage() {
        try {
            val fileUri = voiceRecorder.uri
            chat_input_destruction_info_container.visibility = View.GONE
            activity.handleSendVoiceClick(fileUri)
            activity.resetChatInputFabButton()
            activity.hideChatInputFabButton()
            resetDestructionInfoContainer()
            closeDestructionPicker(true)
        } catch (e: FileNotFoundException) {
            DialogBuilderUtil.buildErrorDialog(
                activity,
                getString(R.string.chats_voiceMessage_send_error)
            ).show()
        }
    }

    private fun releaseAudio() {
        audioPlayer?.release()
        voiceRecorder.release()
        activity.hideChatInputFabButton()
        resetUI()
    }

    fun setTypingState() {
        activity.setOnlineStateToTyping()
    }

    fun setOnlineState() {
        resetUI()
        activity.setOnlineStateToOnline()
    }

    fun getEditText(): EditText = chat_edit_text_input

    fun isCommenting(): Boolean = comment_root_wrapper?.visibility == View.VISIBLE

    fun setTimerInfoText(text: String?) {
        text?.let { timer_info_textview.text = it }
    }

    fun setDestructionInfoText(text: String?) {
        text?.let { destruction_info_textview.text = it }
    }

    fun onMainFabClick(open: Boolean) {
        if (open) {
            chat_input_destruction_info_container.visibility = View.VISIBLE
        } else if (!priorityEnabled && !timerEnabled && !destructionEnabled) {
            chat_input_destruction_info_container.visibility = View.GONE
        } else {
            activity.hideFragment()
        }
    }

    fun handlePriorityButtonClick() {
        priorityEnabled = !priorityEnabled
        chat_input_prio_icon.visibility = if (priorityEnabled) View.VISIBLE else View.INVISIBLE
    }

    fun handleTimerClick(): Boolean {
        if (!isDestructionViewShown || !timerEnabled) {
            timerEnabled = true
            showDestructionPicker(true, SelfdestructionFragment.PICKER_MODE_TIMER)
            timer_info_textview.visibility = View.VISIBLE
            chat_input_timer_image.visibility = View.VISIBLE
        } else {
            timerEnabled = false
            if (destructionEnabled && destructionViewMode != SelfdestructionFragment.PICKER_MODE_DESTRUCTION) {
                showDestructionPicker(true, SelfdestructionFragment.PICKER_MODE_DESTRUCTION)
            } else {
                showDestructionPicker(false, SelfdestructionFragment.PICKER_MODE_TIMER)
                if (showKeyboardAfterClosingDestructionPicker)
                    showKeyboard(true)
            }
            timer_info_textview.visibility = View.INVISIBLE
            chat_input_timer_image.visibility = View.INVISIBLE
        }
        return timerEnabled
    }

    fun getChatInputText(): String? =
        chat_edit_text_input?.text?.toString()

    fun handleDestructionClick(): Boolean {
        if (!isDestructionViewShown || !destructionEnabled) {
            destructionEnabled = true
            showDestructionPicker(true, SelfdestructionFragment.PICKER_MODE_DESTRUCTION)
            destruction_info_textview.visibility = View.VISIBLE
            chat_input_destruction_image.visibility = View.VISIBLE
        } else {
            destructionEnabled = false
            if (timerEnabled && destructionViewMode != SelfdestructionFragment.PICKER_MODE_TIMER) {
                showDestructionPicker(true, SelfdestructionFragment.PICKER_MODE_TIMER)
            } else {
                showDestructionPicker(false, SelfdestructionFragment.PICKER_MODE_DESTRUCTION)
                if (showKeyboardAfterClosingDestructionPicker)
                    showKeyboard(true)
            }
            destruction_info_textview.visibility = View.INVISIBLE
        }
        return destructionEnabled
    }

    fun closeDestructionPicker(disableDestructionAndTimer: Boolean) {
        if (disableDestructionAndTimer) {
            destructionEnabled = false
            timerEnabled = false
        }
        showDestructionPicker(show = false, mode = false)
        activity.updateFabItems(-1)
    }

    @JvmOverloads
    fun showDestructionPicker(show: Boolean, mode: Boolean, showKeyboard: Boolean = true) {
        isDestructionViewShown = show
        if (isLandscape() && showKeyboard) {
            chat_edit_text_input.requestFocus()
            if (!isDestructionViewShown && chat_edit_text_input.isFocused) {
                showKeyboard(true)
            }
        } else if (!showKeyboard) {
            showKeyboard(false)
        }
        paintDestructionPicker(mode)
    }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == ORIENTATION_LANDSCAPE

    private fun paintEmojiPicker() {
        if (!emojiEnabled) {
            chat_emoji_button.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_emoji))
            chat_emoji_button.contentDescription =
                resources.getString(R.string.content_description_chatinput_show_emojis)
        } else {
            chat_emoji_button.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_keyboard))
            chat_emoji_button.contentDescription =
                resources.getString(R.string.content_description_chatinput_hide_emojis)
        }
    }

    fun showEmojiPicker(enable: Boolean) {
        if (emojiEnabled != enable) {
            emojiEnabled = !emojiEnabled
            if (!emojiEnabled) activity.hideEmojiPicker()
            else activity.showEmojiFragment()
            paintEmojiPicker()
        }
    }

    private fun resetDestructionInfoContainer() {
        closeDestructionPicker(true)
        destruction_info_textview.visibility = View.GONE
        chat_input_destruction_image.visibility = View.GONE
        chat_input_timer_image.visibility = View.GONE
        timer_info_textview.visibility = View.GONE
        chat_input_destruction_info_container.visibility = View.GONE
        chat_input_prio_icon.visibility = View.GONE
        destructionEnabled = false
        timerEnabled = false
    }

    fun showCommentView(item: BaseChatItemVO, showCloseButton: Boolean) {

        val activity = getActivity() ?: return

        DateUtil.setDateToTextView(activity, comment_date, item.dateSend)
        with(comment_name) {
            text = item.name
            visibility = View.VISIBLE
            maxLines = 1
        }

        when (item) {
            is ChannelChatItemVO -> comment_text.text = "${item.messageHeader}\n${item.messageContent}"
            is TextChatItemVO -> {
                comment_text.text = item.message
                comment_text.visibility = View.VISIBLE
            }
            is VCardChatItemVO -> {
                comment_text.text =
                    "${activity.resources.getString(R.string.chat_input_reply_contact)}" + " \"" + item.displayInfo + "\""
                comment_image.visibility = View.GONE
            }
            is LocationChatItemVO -> {
                comment_text.text = activity.resources.getString(R.string.chat_location_selection_navigation_item_title)
                comment_image.setImageBitmap(item.image)
                comment_image.visibility = View.VISIBLE
            }
            is ImageChatItemVO -> {
                if (!item.attachmentDesc.isNullOrEmpty()) {
                    comment_text.visibility = View.VISIBLE
                    comment_text.text = item.attachmentDesc
                } else {
                    comment_text.text = activity.resources.getString(R.string.export_chat_type_image)
                }

                comment_image.setImageBitmap(item.image)
                comment_image.visibility = View.VISIBLE
            }
            is VideoChatItemVO -> {

                if (!item.attachmentDesc.isNullOrEmpty()) {
                    comment_text.visibility = View.VISIBLE
                    comment_text.text = item.attachmentDesc
                } else {
                    comment_text.text = activity.resources.getString(R.string.export_chat_type_video)
                }
                comment_image.setImageBitmap(item.image)
                comment_image.visibility = View.VISIBLE
            }
            // KS: AVC
            // This is shown if the user long clicks on a message for direct answer
            is AVChatItemVO -> {
                comment_text.text = activity.resources.getString(R.string.chats_AVC_title)
                comment_image.setImageResource(R.drawable.ic_phone_call2)
                comment_image.visibility = View.VISIBLE
            }
            is VoiceChatItemVO -> {
                comment_text.text = activity.resources.getString(R.string.chats_voiceMessage_title)
                comment_image.setImageResource(R.drawable.sound_placeholder)
                comment_image.visibility = View.VISIBLE
            }
            is FileChatItemVO -> {
                comment_text.text = item.fileName

                comment_image.setBackgroundResource(R.drawable.data_placeholder)
                val resID = MimeUtil.getIconForMimeType(item.fileMimeType)

                if (resID != MIMETYPE_NOT_FOUND) comment_image.setImageResource(resID)
                else comment_image.setImageResource(R.drawable.data_placeholder)

                comment_image.visibility = View.VISIBLE
            }
        }

        comment_cancel.visibility = if (showCloseButton) View.VISIBLE else View.GONE

        closeDestructionPicker(false)
        if (chat_edit_text_input.text?.isEmpty() == true) {
            chat_right_button.visibility = View.GONE
        }

        this.activity.resetChatInputFabButton(true)

        comment_root_wrapper.visibility = View.VISIBLE
    }

    fun closeCommentView() {
        comment_root_wrapper.visibility = View.GONE
        comment_image.background = null
        comment_cancel.visibility = View.GONE
        comment_name.visibility = View.GONE
        comment_text.visibility = View.GONE
        comment_date.visibility = View.GONE
        comment_image.visibility = View.GONE
        chat_right_button.visibility = View.VISIBLE
        activity.resetChatInputFabButton()
    }

    fun showKeyboard(doShow: Boolean) {
        if (doShow && isLandscape())
            chat_edit_text_input.setImeActionLabel(getString(R.string.general_send), EditorInfo.IME_ACTION_SEND)

        if (getActivity() != null && KeyboardUtil.isKeyboardVisible(getActivity()) != doShow)
            KeyboardUtil.toggleSoftInputKeyboard(getActivity(), chat_edit_text_input, doShow)

        if(doShow)
           showEmojiPicker(false)
    }

    private fun paintDestructionPicker(mode: Boolean) {
        if (!isDestructionViewShown)
            activity.hideDestructionPicker()
        else {
            showKeyboard(false)
            showEmojiPicker(false)
            activity.showSelfdestructionFragment(mode)
        }

        if (!destructionEnabled)
            destruction_info_textview.text = "-"

        if (!timerEnabled)
            timer_info_textview.text = resources.getString(R.string.chat_input_now)
    }

    fun setDestructionViewMode(mode: Boolean) {
        destructionViewMode = mode
    }

    fun setChatInputText(text: String?, forceEmpty: Boolean = false) {
        val text1 = if (forceEmpty && text == null) "" else text

        if (chat_edit_text_input != null && (forceEmpty || !text1.isNullOrEmpty())) {
            chat_edit_text_input.setText(text1)
            chat_edit_text_input.setSelection(text1?.length ?: 0)
        }
    }

    fun setStartText(text: String?) {
        startText = text
    }

    fun requestFocusForInput() {
        chat_edit_text_input.requestFocus()
    }

    fun setSimpleUi(value: Boolean) {
        simpleUi = value
    }

    fun hideAudioRecord() {
        hasAudioPermission = false
        hideAudioRecord = true
    }

    fun clearText() {
        chat_edit_text_input.setText("")
    }

    fun setInputEnabled(enabled: Boolean) {
        chat_edit_text_input?.isEnabled = enabled
    }

    fun setShowKeyboardAfterClosingDestructionPicker(value: Boolean) {
        showKeyboardAfterClosingDestructionPicker = value
    }

    fun appendText(str: String) {
        chat_edit_text_input.appendText(str)
    }

    fun insertText(str: String) {
        val pos = chat_edit_text_input.selectionStart
        chat_edit_text_input.insertText(str, pos)
        chat_edit_text_input.setSelection(pos + str.length)
    }
}
