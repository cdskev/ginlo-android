// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo

import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import dagger.android.AndroidInjection
import eu.ginlo_apps.ginlo.controller.message.contracts.SilenceChatListener
import eu.ginlo_apps.ginlo.data.network.AppConnectivity
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.DateUtil
import eu.ginlo_apps.ginlo.util.ColorUtil
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil
import eu.ginlo_apps.ginlo.util.GuidUtil
import kotlinx.android.synthetic.main.activity_mute_chat.mute_chat_duration_textview
import kotlinx.android.synthetic.main.activity_mute_chat.mute_chat_duration_textview2
import kotlinx.android.synthetic.main.activity_mute_chat.mute_item_15_minutes
import kotlinx.android.synthetic.main.activity_mute_chat.mute_item_1_hour
import kotlinx.android.synthetic.main.activity_mute_chat.mute_item_24_hours
import kotlinx.android.synthetic.main.activity_mute_chat.mute_item_8_hours
import kotlinx.android.synthetic.main.activity_mute_chat.mute_item_deactivate
import kotlinx.android.synthetic.main.activity_mute_chat.mute_item_infinite
import java.util.Calendar
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

class MuteChatActivity : BaseActivity() {
    companion object {
        const val EXTRA_CHAT_GUID = "MuteChatActivity.extraChatGuid"
    }

    private var refreshTimer: Timer? = null
    private lateinit var chatGuid: String

    @Inject
    lateinit var appConnectivity: AppConnectivity

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
    }

    private fun silentTillWithNoContact(): Long {
        return try {
            val contactByGuid = simsMeApplication.contactController.getContactByGuid(chatGuid)
                ?: simsMeApplication.contactController.createContactIfNotExists(chatGuid, null)

            contactByGuid?.silentTill ?: 0L
        } catch (le: LocalizedException) {
            LogUtil.w(MuteChatActivity::class.java.simpleName, le.message, le)
            finish()
            0
        }
    }

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        if (!intent.hasExtra(EXTRA_CHAT_GUID)) {
            LogUtil.w(this.javaClass.simpleName, "Chat guid not found")
            finish()
            return
        }

        chatGuid = intent.getStringExtra(EXTRA_CHAT_GUID) as String

        if (!GuidUtil.isChatSingle(chatGuid) && !GuidUtil.isChatRoom(chatGuid)) {
            throw UnsupportedOperationException("Mute not supported for this type of chat. $chatGuid")
        }

        mute_item_15_minutes.setOnClickListener { setState(MuteDelay.M15) }
        mute_item_1_hour.setOnClickListener { setState(MuteDelay.H1) }
        mute_item_8_hours.setOnClickListener { setState(MuteDelay.H8) }
        mute_item_24_hours.setOnClickListener { setState(MuteDelay.H24) }
        mute_item_infinite.setOnClickListener { setState(MuteDelay.INFINITE) }
        mute_item_deactivate.setOnClickListener { setState(MuteDelay.OFF) }
    }

    private fun setTimeTextViews() {
        val silentTill = getSilentTill()

        if (silentTill - Date().time > 0) {
            var seconds = (silentTill - Date().time) / 1000
            val hours = seconds / 3600
            seconds %= 3600
            val minutes = seconds / 60

            when {
                hours > 1000 -> {
                    mute_chat_duration_textview.text = getString(R.string.chat_mute_remaining_part1_infinite)
                    mute_chat_duration_textview2.text = getString(R.string.chat_mute_infinite_no_cap)
                }
                hours > 0 -> {
                    mute_chat_duration_textview2.text = String.format(
                        getString(R.string.chat_mute_remaining_hours_minutes),
                        hours,
                        minutes
                    )
                    mute_chat_duration_textview.text = getString(R.string.chat_mute_remaining_part1)
                }
                else -> {
                    mute_chat_duration_textview2.text = String.format(
                        getString(R.string.chat_mute_remaining_minutes),
                        minutes
                    )
                    mute_chat_duration_textview.text = getString(R.string.chat_mute_remaining_part1)
                }
            }
        } else {
            mute_chat_duration_textview.text = getString(R.string.chat_mute_off_long)
            mute_chat_duration_textview.setBackgroundColor(ColorUtil.getInstance().getMainContrast50Color(simsMeApplication))
            mute_chat_duration_textview2.visibility = View.GONE
            mute_item_deactivate.visibility = View.GONE

            refreshTimer?.cancel()
            refreshTimer?.purge()
            refreshTimer = null
        }
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_mute_chat
    }

    override fun onResumeActivity() {
        try {
            val silentTill = getSilentTill()

            if (silentTill - Date().time > 0) {
                refreshTimer = Timer()

                val refreshTask = object : TimerTask() {
                    override fun run() {
                        Handler(mainLooper).post {
                            try {
                                setTimeTextViews()
                            } catch (le: LocalizedException) {
                                LogUtil.w(MuteChatActivity::class.java.simpleName, le.message, le)
                            }
                        }
                    }
                }
                refreshTimer?.scheduleAtFixedRate(refreshTask, 0, 5000)
            } else {
                setTimeTextViews()
            }
        } catch (le: LocalizedException) {
            LogUtil.w(MuteChatActivity::class.java.simpleName, le.message, le)
            finish()
        }
    }

    private fun getSilentTill(): Long {
        return if (GuidUtil.isChatSingle(chatGuid)) silentTillWithNoContact() else {
            simsMeApplication.groupChatController.getChatByGuid(chatGuid)?.silentTill ?: 0L
        }
    }

    public override fun onPauseActivity() {
        super.onPauseActivity()
        refreshTimer?.cancel()
        refreshTimer?.purge()
        refreshTimer = null
    }

    private fun setState(muteDelay: MuteDelay) {
        if (!appConnectivity.isConnected()) {
            Toast.makeText(
                this,
                R.string.backendservice_internet_connectionFailed,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        showIdleDialog()
        val dateNow = Date()
        val calendar = DateUtil.getCalendarFromDate(dateNow)
        val dateString: String

        when (muteDelay) {
            MuteDelay.H24 -> {
                calendar.add(Calendar.HOUR, 24)
                dateString = DateUtil.dateToUtcStringWithoutMillis(calendar.time)
            }
            MuteDelay.H8 -> {
                calendar.add(Calendar.HOUR, 8)
                dateString = DateUtil.dateToUtcStringWithoutMillis(calendar.time)
            }
            MuteDelay.H1 -> {
                calendar.add(Calendar.HOUR, 1)
                dateString = DateUtil.dateToUtcStringWithoutMillis(calendar.time)
            }
            MuteDelay.M15 -> {
                calendar.add(Calendar.MINUTE, 15)
                dateString = DateUtil.dateToUtcStringWithoutMillis(calendar.time)
            }
            MuteDelay.OFF -> dateString = ""
            MuteDelay.INFINITE -> {
                calendar.set(Calendar.YEAR, 2100)
                calendar.set(Calendar.MONTH, 1)
                calendar.set(Calendar.DATE, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                dateString = DateUtil.dateToUtcStringWithoutMillis(calendar.time)
            }
        }

        val silenceChatListener = object : SilenceChatListener {
            override fun onSuccess() {
                finish()
                dismissIdleDialog()
            }

            override fun onFail(errorMsg: String) {
                dismissIdleDialog()
                DialogBuilderUtil.buildErrorDialog(this@MuteChatActivity, errorMsg).show()
            }
        }

        when {
            GuidUtil.isChatSingle(chatGuid) -> simsMeApplication.singleChatController.silenceChat(
                chatGuid,
                dateString,
                silenceChatListener
            )
            GuidUtil.isChatRoom(chatGuid) -> simsMeApplication.groupChatController.silenceChat(
                chatGuid,
                dateString,
                silenceChatListener
            )
            else -> finish()
        }
    }

    private enum class MuteDelay {
        H24,
        H8,
        H1,
        M15,
        OFF,
        INFINITE
    }
}
