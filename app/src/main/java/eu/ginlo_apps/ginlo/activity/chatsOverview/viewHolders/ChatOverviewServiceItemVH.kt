// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.chatsOverview.viewHolders

import android.app.Application
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemClick
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemLongClick
import eu.ginlo_apps.ginlo.controller.message.SingleChatController
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Message
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.chat.overview.BaseChatOverviewItemVO
import eu.ginlo_apps.ginlo.model.chat.overview.ChatOverviewItemVO
import eu.ginlo_apps.ginlo.model.chat.overview.ServiceChatOverviewItemVO
import eu.ginlo_apps.ginlo.util.ChannelColorUtil
import eu.ginlo_apps.ginlo.util.ImageLoader
import eu.ginlo_apps.ginlo.util.StringUtil
import eu.ginlo_apps.ginlo.util.TimeUtil
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_important_icon
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_important_tv
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_mask_image_view_chat_image
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_media_icon
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_send_state
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_text_view_message_counter
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_text_view_message_date
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_text_view_message_preview
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_text_view_title
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.trust_state_divider

class ChatOverviewServiceItemVH(
    imageLoader: ImageLoader,
    timeUtil: TimeUtil,
    itemView: View,
    onChatItemClick: OnChatItemClick?,
    onChatItemLongClick: OnChatItemLongClick?,
    private val singleChatController: SingleChatController
) : ChatOverviewBaseItemVH(itemView, imageLoader, timeUtil, onChatItemClick, onChatItemLongClick) {
    override fun setItem(item: BaseChatOverviewItemVO) {
        bindServiceChatView(item as ServiceChatOverviewItemVO)
        itemView.chat_overview_item_mask_image_view_chat_image?.setForceNoMask(false)
        itemView.setOnClickListener { onChatItemClick?.onClick(item) }
        onChatItemLongClick?.apply {
            itemView.setOnLongClickListener {
                this.onLongClick(item)
            }
        }

        configureAvatarLayout(item)
    }

    private fun bindServiceChatView(chatOverviewItemVO: ServiceChatOverviewItemVO) {
        itemView.trust_state_divider.visibility = View.GONE
        itemView.chat_overview_item_important_tv.visibility = View.GONE
        itemView.chat_overview_item_important_icon.visibility = View.GONE
        itemView.chat_overview_item_media_icon.visibility = View.GONE
        itemView.chat_overview_item_send_state.visibility = View.GONE

        setCompoundDrawables(itemView.chat_overview_item_text_view_message_preview, chatOverviewItemVO)

        if (chatOverviewItemVO.title.isNullOrBlank()) {
            try {
                itemView.chat_overview_item_text_view_title.text =
                    singleChatController.getChatByGuid(chatOverviewItemVO.chatGuid)?.title
            } catch (e: LocalizedException) {
                LogUtil.e(this.javaClass.name, e.message, e)
            }
        } else {
            itemView.chat_overview_item_text_view_title.text = chatOverviewItemVO.title
        }

        if (chatOverviewItemVO.previewText.isNullOrBlank()) {
            itemView.chat_overview_item_text_view_message_preview.text = ""
        } else {
            itemView.chat_overview_item_text_view_message_preview.text = StringUtil.replaceUrlNew(
                chatOverviewItemVO.previewText,
                chatOverviewItemVO.shortLinkText,
                null,
                false
            )
        }

        var channelColorUtil: ChannelColorUtil? = null
        if (chatOverviewItemVO.channelLayoutModel != null) {
            channelColorUtil = ChannelColorUtil(chatOverviewItemVO.channelLayoutModel, itemView.context)

            itemView.chat_overview_item_text_view_message_counter.setTextColor(channelColorUtil.overviewColorBubble)

            if (channelColorUtil.headBkColor != 0) {
                itemView.setBackgroundColor(channelColorUtil.cbColor)
            }

            if (chatOverviewItemVO.messageCount > 0) {
                itemView.chat_overview_item_text_view_message_counter.text = chatOverviewItemVO.messageCount.toString()
                itemView.chat_overview_item_text_view_message_counter.visibility = View.VISIBLE

                itemView.chat_overview_item_text_view_message_counter.background?.apply {
                    val msgCounterBgColor = channelColorUtil.overviewBkColorBubble
                    colorFilter = PorterDuffColorFilter(msgCounterBgColor, PorterDuff.Mode.SRC_ATOP)
                }
            } else {
                itemView.chat_overview_item_text_view_message_counter.visibility = View.GONE
            }
        }

        itemView.chat_overview_item_text_view_message_date.text = timeUtil.getDateLabel(chatOverviewItemVO.datesend)
        if (channelColorUtil != null) {
            itemView.chat_overview_item_text_view_message_date.setTextColor(channelColorUtil.overviewColorTime)
        }
    }

    private fun configureAvatarLayout(chatOverviewItem: BaseChatOverviewItemVO) {
        itemView.chat_overview_item_mask_image_view_chat_image.setForceNoMask(true)
        imageLoader.loadImage(chatOverviewItem.chatGuid, itemView.chat_overview_item_mask_image_view_chat_image, null)
    }

    private fun setCompoundDrawables(
        previewTextView: TextView,
        chatOverviewItemVO: BaseChatOverviewItemVO
    ) {
        val mediaIcon = getMediaIconChatItem(chatOverviewItemVO)

        var textViewDrawables = setDefaultCompoundDrawables(previewTextView, mediaIcon)

        if (chatOverviewItemVO.hasSendError) {
            textViewDrawables[2].level = Message.MESSAGE_STATUS_ERROR
        } else if (chatOverviewItemVO is ChatOverviewItemVO) {

            if (chatOverviewItemVO.isSentMessage) {
                val isSystemInfo = chatOverviewItemVO.isSystemInfo

                if (chatOverviewItemVO.hasRead) {
                    textViewDrawables[2].level = Message.MESSAGE_STATUS_READ
                } else if (chatOverviewItemVO.hasDownloaded) {
                    textViewDrawables[2].level = Message.MESSAGE_STATUS_DOWNLOADED
                } else if (chatOverviewItemVO.isSendConfirm && !isSystemInfo) {
                    textViewDrawables[2].level = Message.MESSAGE_STATUS_SENT
                } else {
                    textViewDrawables = previewTextView.compoundDrawables
                    previewTextView.setCompoundDrawablesWithIntrinsicBounds(
                        textViewDrawables[0], textViewDrawables[1], null,
                        textViewDrawables[3]
                    )
                }//
            } else {
                textViewDrawables = previewTextView.compoundDrawables
                previewTextView.setCompoundDrawablesWithIntrinsicBounds(
                    textViewDrawables[0], textViewDrawables[1], null,
                    textViewDrawables[3]
                )
            }
        } else {
            textViewDrawables = previewTextView.compoundDrawables
            previewTextView.setCompoundDrawablesWithIntrinsicBounds(
                textViewDrawables[0], textViewDrawables[1], null,
                textViewDrawables[3]
            )
        }
    }

    private fun getMediaIconChatItem(chatOverviewItem: BaseChatOverviewItemVO): Drawable? {
        return if (chatOverviewItem.dateSendTimed != null) {
            ContextCompat.getDrawable(itemView.context, R.drawable.send_timed)
        } else {
            when (chatOverviewItem.mediaType) {
                BaseChatOverviewItemVO.MSG_MEDIA_TYPE_IMAGE ->
                    ContextCompat.getDrawable(itemView.context, R.drawable.media_photo)

                BaseChatOverviewItemVO.MSG_MEDIA_TYPE_AUDIO -> ContextCompat.getDrawable(
                    itemView.context,
                    R.drawable.media_audio
                )

                BaseChatOverviewItemVO.MSG_MEDIA_TYPE_MOVIE ->
                    ContextCompat.getDrawable(itemView.context, R.drawable.media_movie)

                BaseChatOverviewItemVO.MSG_MEDIA_TYPE_DESTROY ->
                    ContextCompat.getDrawable(itemView.context, R.drawable.media_destroy)

                BaseChatOverviewItemVO.MSG_MEDIA_TYPE_FILE ->
                    ContextCompat.getDrawable(itemView.context, R.drawable.media_data)
                else -> null
            }
        }
    }

    private fun setDefaultCompoundDrawables(previewTextView: TextView, leftDrawable: Drawable?): Array<Drawable> {
        var textViewDrawables = previewTextView.compoundDrawables

        if (textViewDrawables[2] == null) {
            textViewDrawables[2] = ContextCompat.getDrawable(itemView.context, R.drawable.message_status)
        }

        previewTextView.setCompoundDrawablesWithIntrinsicBounds(
            leftDrawable, textViewDrawables[1], textViewDrawables[2],
            textViewDrawables[3]
        )

        textViewDrawables = previewTextView.compoundDrawables

        return textViewDrawables
    }
}