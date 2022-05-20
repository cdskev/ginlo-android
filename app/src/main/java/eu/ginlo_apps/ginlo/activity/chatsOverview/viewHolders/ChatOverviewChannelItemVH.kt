// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.chatsOverview.viewHolders

import android.app.Application
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.Display
import android.view.View
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemClick
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemLongClick
import eu.ginlo_apps.ginlo.controller.ChannelController
import eu.ginlo_apps.ginlo.model.chat.overview.BaseChatOverviewItemVO
import eu.ginlo_apps.ginlo.model.chat.overview.ChannelChatOverviewItemVO
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil
import eu.ginlo_apps.ginlo.util.ImageLoader
import eu.ginlo_apps.ginlo.util.TimeUtil
import eu.ginlo_apps.ginlo.util.ViewUtil
import kotlinx.android.synthetic.main.chat_overview_item_channel.view.chat_overview_item_channel_avatar
import kotlinx.android.synthetic.main.chat_overview_item_channel.view.chat_overview_item_media_icon
import kotlinx.android.synthetic.main.chat_overview_item_channel.view.chat_overview_item_text_view_message_counter
import kotlinx.android.synthetic.main.chat_overview_item_channel.view.chat_overview_item_text_view_message_date
import kotlinx.android.synthetic.main.chat_overview_item_channel.view.chat_overview_item_text_view_message_preview
import kotlinx.android.synthetic.main.chat_overview_item_channel.view.chat_overview_item_text_view_title

class ChatOverviewChannelItemVH(
    private val defaultDisplay: Display,
    imageLoader: ImageLoader,
    timeUtil: TimeUtil,
    itemView: View,
    onChatItemClick: OnChatItemClick?,
    onChatItemLongClick: OnChatItemLongClick?
) : ChatOverviewBaseItemVH(itemView, imageLoader, timeUtil, onChatItemClick, onChatItemLongClick) {
    override fun setItem(item: BaseChatOverviewItemVO) {
        configureAvatarLayout(item as ChannelChatOverviewItemVO)

        itemView.chat_overview_item_text_view_message_date.text = timeUtil.getDateLabel(item.latestDate)

        configureTitleLayout(item)

        configurePreviewLayout(item)

        itemView.setOnClickListener { onChatItemClick?.onClick(item) }
        onChatItemLongClick?.apply {
            itemView.setOnLongClickListener {
                this.onLongClick(item)
            }
        }
    }

    private fun configurePreviewLayout(item: ChannelChatOverviewItemVO) {
        val showMediaViews = fillMediaLayout(item)

        val previewTextView = itemView.chat_overview_item_text_view_message_preview

        if (showMediaViews) {
            ViewUtil.tryFlowText(
                item.previewText,
                itemView.chat_overview_item_media_icon,
                previewTextView,
                defaultDisplay,
                true
            )
        } else {
            previewTextView.text = item.previewText
        }
    }

    private fun configureTitleLayout(item: ChannelChatOverviewItemVO) {
        itemView.chat_overview_item_text_view_title.text = item.title

        if (item.messageCount > 0) {
            itemView.chat_overview_item_text_view_message_counter.text = item.messageCount.toString()
            itemView.chat_overview_item_text_view_message_counter.visibility = View.VISIBLE
            val screenDesignUtil = ScreenDesignUtil.getInstance()
            val bgColor = screenDesignUtil.getLowColor(itemView.context.applicationContext as Application)
            val bgContrastColor =
                    screenDesignUtil.getLowContrastColor(itemView.context.applicationContext as Application)

            itemView.chat_overview_item_text_view_message_counter.setTextColor(bgContrastColor)
            itemView.chat_overview_item_text_view_message_counter.background.colorFilter =
                PorterDuffColorFilter(bgColor, PorterDuff.Mode.SRC_ATOP)
        } else {
            itemView.chat_overview_item_text_view_message_counter.visibility = View.GONE
        }
    }

    private fun configureAvatarLayout(item: ChannelChatOverviewItemVO) {
        itemView.chat_overview_item_channel_avatar.contentDescription = item.title
        imageLoader.loadImage(
            ChannelController.ChannelIdentifier(
                item.chatGuid,
                ChannelController.IMAGE_TYPE_PROVIDER_ICON
            ), itemView.chat_overview_item_channel_avatar
        )
    }

    private fun fillMediaLayout(item: ChannelChatOverviewItemVO): Boolean {
        val mediaIcon = getMediaIconChatItemNew(item)

        return if (mediaIcon == -1) {
            itemView.chat_overview_item_media_icon.visibility = View.GONE
            false
        } else {
            itemView.chat_overview_item_media_icon.visibility = View.VISIBLE
            itemView.chat_overview_item_media_icon.setImageResource(mediaIcon)
            true
        }
    }

    private fun getMediaIconChatItemNew(item: BaseChatOverviewItemVO): Int {
        return when (item.mediaType) {
            BaseChatOverviewItemVO.MSG_MEDIA_TYPE_IMAGE -> R.drawable.media_photo

            BaseChatOverviewItemVO.MSG_MEDIA_TYPE_AUDIO -> R.drawable.media_audio

            BaseChatOverviewItemVO.MSG_MEDIA_TYPE_MOVIE -> R.drawable.media_movie

            BaseChatOverviewItemVO.MSG_MEDIA_TYPE_FILE -> R.drawable.media_data

            else -> -1
        }
    }
}