// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.view.View.OnClickListener
import android.widget.AdapterView.OnItemClickListener
import android.widget.Toast
import eu.ginlo_apps.ginlo.adapter.BackgroundGalleryAdapter
import eu.ginlo_apps.ginlo.controller.ChatImageController
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.util.BitmapUtil
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil
import eu.ginlo_apps.ginlo.util.MetricsUtil
import kotlinx.android.synthetic.main.activity_chat_background.chat_background_grid_view
import kotlinx.android.synthetic.main.activity_chat_background.chat_bg
import java.io.FileNotFoundException

class ChatBackgroundActivity : BaseActivity() {
    private lateinit var adapter: BackgroundGalleryAdapter
    private var backgroundPreview: Bitmap? = null
    private val displayMetrics: DisplayMetrics by lazy { MetricsUtil.getDisplayMetrics(this) }
    private var mMode = MODE_SELECT

    private val itemClickListener = OnItemClickListener { _, _, position, _ ->
        val resourceId = adapter.getItem(position) as Int

        setBackgroundPreview(resourceId)
        setMode(MODE_PREVIEW)
    }

    override fun onCreateActivity(savedInstanceState: Bundle?) {
        init()
    }

    override fun getActivityLayout(): Int {
        return R.layout.activity_chat_background
    }

    override fun onResumeActivity() {
        // Weird empty override
    }

    private fun init() {
        initGridView()

        if (intent.data == null) {
            setMode(MODE_SELECT)
        } else {
            try {
                setBackgroundPreview(intent.data)
                setMode(MODE_PREVIEW_URI)
            } catch (e: FileNotFoundException) {
                LogUtil.e(this.javaClass.name, e.message, e)
                setMode(MODE_SELECT)
            }
        }
    }

    private fun initGridView() {
        adapter = BackgroundGalleryAdapter(simsMeApplication)
        chat_background_grid_view.adapter = adapter
        chat_background_grid_view.onItemClickListener = itemClickListener
    }

    private fun setBackgroundPreview(resourceId: Int) {
        backgroundPreview = BitmapUtil.decodeSampledBitmapFromResource(
            resources, resourceId, displayMetrics.widthPixels,
            displayMetrics.heightPixels
        )
    }

    private fun setBackgroundPreview(uri: Uri?) {
        if (uri == null) return

        backgroundPreview = BitmapUtil.decodeUri(
            this, uri, displayMetrics.widthPixels,
            displayMetrics.heightPixels, true
        )

        if (backgroundPreview == null) {
            DialogBuilderUtil.buildErrorDialog(
                this@ChatBackgroundActivity,
                getString(R.string.chats_addAttachment_wrong_format_or_error)
            ).apply {
                setOnDismissListener { this@ChatBackgroundActivity.onBackPressed() }
            }.show()
        }
    }

    private fun setMode(mode: Int) {
        mMode = mode
        if (mMode == MODE_SELECT) {
            removeRightActionBarImage()

            try {
                chatImageController.background
            } catch (e: LocalizedException) {
                LogUtil.e(this.javaClass.name, e.message, e)
                null
            }?.let {
                setBackground(it)
            }

            chat_background_grid_view.animate().alpha(1f).setDuration(FADE_SPEED.toLong()).setListener(null)
        } else if (mMode == MODE_PREVIEW || mMode == MODE_PREVIEW_URI) {
            val rightActionBarClickListener = object : OnClickListener {
                override fun onClick(v: View) {
                    try {
                        chatImageController.background = backgroundPreview
                        finish()
                    } catch (e: LocalizedException) {
                        LogUtil.e(this.javaClass.name, e.message, e)
                        Toast.makeText(
                            this@ChatBackgroundActivity,
                            R.string.settings_save_setting_failed,
                            Toast.LENGTH_LONG
                        )
                            .show()
                    }
                }
            }
            setRightActionBarImage(R.drawable.ic_done_white_24dp, rightActionBarClickListener, null, -1)
            setBackground(backgroundPreview)

            if (mMode == MODE_PREVIEW) {
                chat_background_grid_view.animate().alpha(0f).setDuration(FADE_SPEED.toLong()).setListener(null)
            } else {
                chat_background_grid_view.visibility = View.GONE
            }
        }
    }

    private fun setBackground(background: Bitmap?) {
        chat_bg.visibility = View.VISIBLE
        chat_bg.setImageBitmap(background)
    }

    override fun onBackPressed() {
        when (mMode) {
            MODE_PREVIEW -> {
                setMode(MODE_SELECT)
            }
            MODE_PREVIEW_URI -> {
                super.onBackPressed()
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    companion object {
        private const val MODE_SELECT = 0
        private const val MODE_PREVIEW = 1
        private const val MODE_PREVIEW_URI = 2
        private const val FADE_SPEED = 400
    }
}
