// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller

import android.app.Activity
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.util.AudioPlayer
import eu.ginlo_apps.ginlo.util.TimeDisplayUtil
import eu.ginlo_apps.ginlo.util.TimeDisplayUtil.OnClockStoppedHandler
import java.io.File

class AudioManager(
    private val activity: Activity,
    private val view: View
) {
    private var player: AudioPlayer? = null

    private var timeDisplay: TimeDisplayUtil? = null

    var isPlaying: Boolean = false
        private set

    private val button: ImageView? = view.findViewById(R.id.chat_item_data_placeholder)

    private val textView: TextView? = view.findViewById(R.id.chat_item_text_view_clock)

    fun play(voiceFile: File) {
        isPlaying = true

        player = AudioPlayer(Uri.fromFile(voiceFile), activity)

        if (textView != null) {
            timeDisplay = TimeDisplayUtil(textView, OnClockStoppedHandler { stop() })
            timeDisplay?.start(player?.duration ?: 0)
        }
        if (button != null) {
            button.visibility = View.VISIBLE
        }

        player?.play()
    }

    fun stop() {
        isPlaying = false
        player?.pause()
        player?.release()
        player = null
        timeDisplay?.stop()
        timeDisplay = null
        textView?.text = activity.resources.getString(R.string.chats_voiceMessage_play)
        button?.visibility = View.GONE
    }

    fun getHasSameView(view: View): Boolean {
        return this.view == view
    }
}
