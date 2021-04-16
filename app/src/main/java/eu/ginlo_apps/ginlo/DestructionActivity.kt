// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaPlayer.OnPreparedListener
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.ImageView
import android.widget.MediaController
import android.widget.TextView
import androidx.core.content.ContextCompat
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.controller.AccountController
import eu.ginlo_apps.ginlo.controller.message.ChatController
import eu.ginlo_apps.ginlo.controller.message.MessageController
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Message
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.chat.SelfDestructionChatItemVO
import eu.ginlo_apps.ginlo.model.constant.MimeType
import eu.ginlo_apps.ginlo.model.param.MessageDestructionParams
import eu.ginlo_apps.ginlo.util.AudioPlayer
import eu.ginlo_apps.ginlo.util.AudioUtil
import eu.ginlo_apps.ginlo.util.BitmapUtil
import eu.ginlo_apps.ginlo.util.ColorUtil
import eu.ginlo_apps.ginlo.util.DateUtil
import eu.ginlo_apps.ginlo.util.SystemUtil
import eu.ginlo_apps.ginlo.util.ToolbarColorizeHelper
import eu.ginlo_apps.ginlo.util.VideoProvider
import kotlinx.android.synthetic.main.activity_destruction.destructionContent
import kotlinx.android.synthetic.main.activity_destruction.destructionScrollView
import kotlinx.android.synthetic.main.activity_destruction.destruction_attachment_description
import kotlinx.android.synthetic.main.activity_destruction.destruction_image_view
import kotlinx.android.synthetic.main.activity_destruction.destruction_text_view
import kotlinx.android.synthetic.main.activity_destruction.destruction_video_view
import kotlinx.android.synthetic.main.activity_destruction.please_touch_layout
import kotlinx.android.synthetic.main.activity_destruction.please_touch_textView
import kotlinx.android.synthetic.main.activity_destruction.timer_layout
import kotlinx.android.synthetic.main.activity_destruction.timer_text_view
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

class DestructionActivity : BaseActivity() {

    companion object {
        const val EXTRA_CHAT_ITEM = "ChatActivity.messageChatItem"

        const val EXTRA_MESSAGE = "ChatActivity.message"

        const val EXTRA_MESSAGE_TYPE = "ChatActivity.messageType"

        const val EXTRA_MESSAGE_GUID = "ChatActivity.messageGuid"

        const val EXTRA_BITMAP_URI = "DestructionActivity.extraBitmapData"

        const val EXTRA_VIDEO_URI = "DestructionActivity.VideoUri"

        const val EXTRA_VOICE_URI = "DestructionActivity.VoiceUri"

        const val EXTRA_ATTACHMENT_DESCRIPTION = "DestructionActivity.AttachmentDescription"

        const val EXTRA_DESTRUCTION_PARAMS = "DestructionActivity.destructionParams"
    }

    private var countdownTimer: Timer? = null

    private val accountController: AccountController by lazy { simsMeApplication.accountController }

    private lateinit var chatController: ChatController

    private val messageController: MessageController by lazy { simsMeApplication.messageController }

    private var destructionParams: MessageDestructionParams? = null

    private var daysLeft: Int = 0

    private var hoursLeft: Int = 0

    private var minutesLeft: Int = 0

    private var secondsLeft: Int = 0

    private var hundredthLeft: Int = 0

    private var timerStarted: Boolean = false

    private var messageShown: Boolean = false

    private var contentType: String? = null

    private var messageGuid: String? = null

    private var audioPlayer: AudioPlayer? = null

    private var attachmentDescription: String? = null

    private fun contentTouchListener(): OnTouchListener {
        return object : OnTouchListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(
                v: View,
                event: MotionEvent
            ): Boolean {

                val updatedDate = messageController.getNewDestructionDate(messageGuid)

                if (updatedDate == null) {
                    val newDestructionDate = destructionParams?.convertTimerToDate()

                    messageController.setNewDestructionDate(messageGuid, newDestructionDate)
                    destructionParams?.countdown = null
                    destructionParams?.date = newDestructionDate
                }

                if (event.action == MotionEvent.ACTION_DOWN) {
                    startTimer()
                    when (contentType) {
                        MimeType.IMAGE_JPEG -> {
                            findViewById<View>(R.id.destruction_background).setBackgroundColor(
                                ContextCompat.getColor(
                                    this@DestructionActivity,
                                    R.color.black_body
                                )
                            )
                            destruction_image_view.visibility = View.VISIBLE
                            if (attachmentDescription?.isNotBlank() == true) {
                                destruction_attachment_description.visibility = View.VISIBLE
                                destruction_attachment_description.text = attachmentDescription
                            }
                        }
                        MimeType.VIDEO_MPEG -> {
                            findViewById<View>(R.id.destruction_background).setBackgroundColor(
                                ContextCompat.getColor(
                                    this@DestructionActivity,
                                    R.color.black_body
                                )
                            )
                            destruction_video_view.start()
                            destruction_video_view.setMediaController(MediaController(this@DestructionActivity))
                            destruction_video_view.visibility = View.VISIBLE
                            if (attachmentDescription?.isNotBlank() == true) {
                                destruction_attachment_description.visibility = View.VISIBLE
                                destruction_attachment_description.text = attachmentDescription
                            }
                        }
                        MimeType.AUDIO_MPEG -> {
                            if (audioPlayer != null) {
                                try {
                                    audioPlayer!!.play()
                                } catch (e: LocalizedException) {
                                    LogUtil.e(this.javaClass.name, e.message, e)
                                }
                            }
                            destruction_image_view.visibility = View.VISIBLE
                        }
                        else -> destruction_text_view.visibility = View.VISIBLE
                    }
                    please_touch_layout.visibility = View.INVISIBLE
                } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    when (contentType) {
                        MimeType.IMAGE_JPEG -> {
                            destruction_image_view.visibility = View.INVISIBLE
                            destruction_attachment_description.visibility = View.GONE
                        }
                        MimeType.VIDEO_MPEG -> {
                            destruction_video_view.pause()
                            destruction_video_view.visibility = View.INVISIBLE
                            destruction_attachment_description.visibility = View.GONE
                        }
                        MimeType.AUDIO_MPEG -> {
                            if (audioPlayer != null) {
                                audioPlayer!!.pause()
                            }
                            destruction_image_view.visibility = View.INVISIBLE
                        }
                        else -> destruction_text_view.visibility = View.INVISIBLE
                    }
                    please_touch_layout.visibility = View.VISIBLE
                }

                return false
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun setUpMessage(message: SelfDestructionChatItemVO) {
        if (message.fromGuid == accountController.account.accountGuid) {
            destruction_text_view.addView(
                LayoutInflater.from(this).inflate(
                    R.layout.chat_item_text_right_destruction_layout,
                    null,
                    false
                )
            )
        } else {
            destruction_text_view.addView(
                LayoutInflater.from(this).inflate(
                    R.layout.chat_item_text_left_destruction_layout,
                    null,
                    false
                )
            )
        }

        destruction_text_view.findViewById<TextView>(R.id.chat_item_text_view_message).text = message.text

        val dateString = DateUtil.getTimeStringFromMillis(message.dateSend)
        val detailString = message.name + " " + dateString
        findViewById<TextView>(R.id.chat_item_text_view_date).text = detailString

        findViewById<View>(R.id.chat_item_text_view_date_only).visibility = View.GONE
        please_touch_textView.text = resources.getText(R.string.chats_showText_pleaseTouch)
    }

    private fun setUpImage(
        image: Bitmap?
    ) {
        destruction_image_view.setImageBitmap(image)
        please_touch_textView.text = resources.getText(R.string.chats_showPicture_pleaseTouch)
    }

    private fun setUpVoice(
        voiceUri: Uri,
        waveform: Bitmap
    ) {
        try {
            audioPlayer = AudioPlayer(voiceUri, this)
        } catch (e: LocalizedException) {
            LogUtil.e(this.javaClass.name, e.message, e)
        }

        destruction_image_view.setImageBitmap(waveform)
        destruction_image_view.scaleType = ImageView.ScaleType.CENTER
        please_touch_textView.text = resources.getText(R.string.chats_showVoice_pleaseTouch)
    }

    private fun getTimeDifference(destructionParams: MessageDestructionParams?): Long {
        if(destructionParams == null) return 0

        return if (destructionParams.countdown != null) {
            (destructionParams.countdown * 1000).toLong()
        } else {
            val currentTime = Date(System.currentTimeMillis())

            (destructionParams.date?.time ?: 0) - currentTime.time
        }
    }

    private fun setTimer(timeDifference: Long) {
        secondsLeft = TimeUnit.MILLISECONDS.toSeconds(timeDifference).toInt()

        daysLeft = secondsLeft / 86400
        secondsLeft -= daysLeft * 86400

        hoursLeft = secondsLeft / 3600
        secondsLeft -= hoursLeft * 3600

        minutesLeft = secondsLeft / 60

        secondsLeft -= minutesLeft * 60

        hundredthLeft = 0
    }

    private fun startTimer() {
        if (!timerStarted) {
            timerStarted = true
            messageShown = true

            countdownTimer = Timer()
            countdownTimer?.schedule(DestructionTimerTask(), 0, 100)
        }
    }

    private fun stopTimer() {
        if (timerStarted) {
            timerStarted = false
            countdownTimer?.cancel()
            countdownTimer?.purge()
        }
    }

    private fun updateCountdownLabel() {
        if (daysLeft == 0 && hoursLeft == 0 && minutesLeft == 1 && secondsLeft == 0 && hundredthLeft == 0) {
            --minutesLeft
            --secondsLeft
            secondsLeft = 59
            hundredthLeft = 9
        } else if (hundredthLeft > 0) {
            hundredthLeft--
        } else if (hundredthLeft == 0 && secondsLeft > 0) {
            secondsLeft--
            hundredthLeft = 9
        } else if (secondsLeft == 0 && minutesLeft > 0) {
            minutesLeft--
            secondsLeft = 59
        } else if (minutesLeft == 0 && hoursLeft > 0) {
            hoursLeft--
            minutesLeft = 59
        } else if (hoursLeft == 0 && daysLeft > 0) {
            daysLeft--
            hoursLeft = 59
        } else {
            fireTheBomb()
        }

        setTimerLabel()
    }

    private fun setTimerLabel() {
        val prefix = getString(R.string.chats_showPicture_destroyedIn)

        val timerLabelString = when {
            daysLeft > 0 ->
                String.format(
                    "%s %02d:%02d:%02d:%02d:%02d",
                    prefix,
                    daysLeft,
                    hoursLeft,
                    minutesLeft,
                    secondsLeft,
                    hundredthLeft
                )
            hoursLeft > 0 ->
                String.format("%s %02d:%02d:%02d:%02d", prefix, hoursLeft, minutesLeft, secondsLeft, hundredthLeft)
            minutesLeft > 0 ->
                String.format("%s %02d:%02d:%02d", prefix, minutesLeft, secondsLeft, hundredthLeft)
            else -> String.format("%s %02d:%02d", prefix, secondsLeft, hundredthLeft)
        }

        runOnUiThread { timer_text_view.text = timerLabelString }
    }

    private fun fireTheBomb() {
        stopTimer()
        chatController.deleteMessage(messageGuid, true, null)
        finishAfterTransition()
    }

    override fun onBackPressed() {
        interruptActivityWorkaround()
        super.onBackPressed()
    }

    public override fun onPauseActivity() {
        if (contentType == MimeType.AUDIO_MPEG && audioPlayer != null) {
            audioPlayer!!.pause()
        }
        super.onPauseActivity()
    }

    override fun onDestroy() {
        if (contentType == MimeType.AUDIO_MPEG && audioPlayer != null) {
            audioPlayer!!.release()
        }
        super.onDestroy()
    }

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        try {

            messageGuid = intent.getStringExtra(EXTRA_MESSAGE_GUID).orEmpty()

            if (messageGuid == null || messageGuid?.isBlank() == true) {
                finish()
                return
            }

            destructionParams = when {
                intent.hasExtra(EXTRA_DESTRUCTION_PARAMS) -> intent.getSerializableExtra(EXTRA_DESTRUCTION_PARAMS) as MessageDestructionParams
                savedInstanceState != null -> savedInstanceState.getSerializable("destructionParams") as MessageDestructionParams
                else -> null
            }

            val updatedDate = messageController.getNewDestructionDate(messageGuid)

            if (updatedDate != null) {
                destructionParams?.countdown = null
                destructionParams?.date = updatedDate
            }

            val onPreparedListener = OnPreparedListener { mp -> mp.isLooping = true }

            val messageType = intent.getIntExtra(EXTRA_MESSAGE_TYPE, 0)
            val application = application as SimsMeApplication

            chatController = when (messageType) {
                Message.TYPE_PRIVATE -> application.singleChatController
                Message.TYPE_CHANNEL -> application.channelChatController
                else -> application.groupChatController
            }

            destruction_video_view.setOnPreparedListener(onPreparedListener)

            val imageUriString = intent.getStringExtra(EXTRA_BITMAP_URI)
            val videoUriString = intent.getStringExtra(EXTRA_VIDEO_URI)
            val voiceUriString = intent.getStringExtra(EXTRA_VOICE_URI)

            attachmentDescription = intent.getStringExtra(EXTRA_ATTACHMENT_DESCRIPTION)

            please_touch_textView.text = resources.getText(R.string.chats_showVideo_pleaseTouch)
            if (imageUriString != null) {
                contentType = MimeType.IMAGE_JPEG

                val imageUri = Uri.parse(imageUriString)
                val bitmap = BitmapUtil.decodeUri(this, imageUri, 0, true)

                setUpImage(bitmap)
            } else if (videoUriString != null) {
                contentType = MimeType.VIDEO_MPEG

                val fileUri = Uri.parse(videoUriString)
                val videoUri = Uri.parse(VideoProvider.CONTENT_URI_BASE.toString() + fileUri.path!!)

                destruction_video_view.setVideoURI(videoUri)
            } else if (voiceUriString != null) {
                contentType = MimeType.AUDIO_MPEG

                val voiceUri = Uri.parse(voiceUriString)
                val waveform = AudioUtil.getPlaceholder(this)

                setUpVoice(voiceUri, waveform)
            } else {
                contentType = MimeType.TEXT_PLAIN
                if (intent.hasExtra(EXTRA_CHAT_ITEM)) {
                    setUpMessage(
                        SystemUtil.dynamicDownCast(
                            intent.getSerializableExtra(EXTRA_CHAT_ITEM),
                            SelfDestructionChatItemVO::class.java
                        )!!
                    )
                }
            }

            if (destructionParams != null) {
                destructionScrollView.setOnTouchListener(contentTouchListener())
                setTimer(getTimeDifference(destructionParams))
            } else {
                when (contentType.orEmpty()) {
                    MimeType.IMAGE_JPEG -> {
                        destruction_image_view.visibility = View.VISIBLE
                        if (attachmentDescription?.isNotBlank() == true) {
                            destruction_attachment_description.visibility = View.VISIBLE
                            destruction_attachment_description.text = attachmentDescription
                        }
                    }
                    MimeType.VIDEO_MPEG -> {
                        destruction_video_view.start()
                        destruction_video_view.setMediaController(MediaController(this))
                        destruction_video_view.visibility = View.VISIBLE
                        if (attachmentDescription?.isNotBlank() == true) {
                            destruction_attachment_description.visibility = View.VISIBLE
                            destruction_attachment_description.text = attachmentDescription
                        }
                    }
                    MimeType.AUDIO_MPEG -> {
                        if (audioPlayer != null) {
                            try {
                                audioPlayer!!.play()
                            } catch (e: LocalizedException) {
                                LogUtil.e(DestructionActivity::class.java.simpleName, e.message, e)
                            }
                        }
                        destruction_image_view.visibility = View.VISIBLE
                    }
                    else -> destruction_text_view.visibility = View.VISIBLE
                }
                please_touch_layout.visibility = View.INVISIBLE
                timer_layout.visibility = View.INVISIBLE
            }


            if (destructionParams?.date != null && destructionParams?.date?.before(Date()) == true) {
                destructionContent.visibility = View.GONE
                destruction_image_view.visibility = View.GONE
                destruction_text_view.visibility = View.GONE
                destruction_video_view.visibility = View.GONE
                please_touch_layout.visibility = View.GONE
                timer_layout.visibility = View.GONE
                chatController.deleteMessage(messageGuid, true, null)
            }
        } catch (e: Throwable) {
            LogUtil.e(e)
            finish()
        }
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_destruction
    }

    override fun onResumeActivity() {
        if (messageController.getNewDestructionDate(messageGuid) != null) {
            startTimer()
        }
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        bundle.putSerializable("destructionParams", destructionParams)
        super.onSaveInstanceState(bundle)
    }

    private fun interruptActivityWorkaround() {
        if (messageShown) {
            stopTimer()

            if (destructionParams?.countdown != null) {
                chatController.deleteMessage(messageGuid, true, null)
            }
        }
    }

    override fun colorizeActivity() {
        if (toolbar != null) {
            ToolbarColorizeHelper.colorizeToolbar(
                toolbar,
                ColorUtil.getInstance().getLowContrastColor(simsMeApplication),
                ColorUtil.getInstance().getLowColor(simsMeApplication),
                this
            )
        }
    }

    internal inner class DestructionTimerTask : TimerTask() {
        override fun run() {
            updateCountdownLabel()
        }
    }
}
