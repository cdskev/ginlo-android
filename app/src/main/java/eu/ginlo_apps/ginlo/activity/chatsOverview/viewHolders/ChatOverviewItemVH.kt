// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.chatsOverview.viewHolders

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.Display
import android.view.View
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemClick
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemLongClick
import eu.ginlo_apps.ginlo.controller.ImageController
import eu.ginlo_apps.ginlo.controller.message.SingleChatController
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Contact
import eu.ginlo_apps.ginlo.greendao.Message
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.chat.overview.BaseChatOverviewItemVO
import eu.ginlo_apps.ginlo.model.chat.overview.ChatOverviewItemVO
import eu.ginlo_apps.ginlo.model.chat.overview.GroupChatOverviewItemVO
import eu.ginlo_apps.ginlo.model.chat.overview.SingleChatOverviewItemVO
import eu.ginlo_apps.ginlo.util.ImageUtil
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil
import eu.ginlo_apps.ginlo.util.TimeUtil
import eu.ginlo_apps.ginlo.util.ViewUtil
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_important_icon
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_important_tv
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_mask_image_view_chat_image
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_media_icon
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_media_layout
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_send_state
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_text_view_message_counter
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_text_view_message_date
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_text_view_message_preview
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.chat_overview_item_text_view_title
import kotlinx.android.synthetic.main.chat_overview_item_single_layout.view.trust_state_divider

class ChatOverviewItemVH(
    private val singleChatController: SingleChatController,
    private val defaultDisplay: Display,
    imageController: ImageController,
    timeUtil: TimeUtil,
    itemView: View,
    onChatItemClick: OnChatItemClick?,
    onChatItemLongClick: OnChatItemLongClick?
) : ChatOverviewBaseItemVH(itemView, imageController, timeUtil, onChatItemClick, onChatItemLongClick) {

    override fun setItem(item: BaseChatOverviewItemVO) {
        configureTrustedState(item)
        configureAvatarLayout(item)
        configureDateTextView(item)
        configureTitleLayout(item)
        configurePreviewLayout(item)

        itemView.setOnClickListener { onChatItemClick?.onClick(item) }
        onChatItemLongClick?.apply {
            itemView.setOnLongClickListener {
                this.onLongClick(item)
            }
        }
    }

    private fun configureTrustedState(chatOverviewItemVO: BaseChatOverviewItemVO) {
        val trustedStateDivider = itemView.trust_state_divider

        if (chatOverviewItemVO is SingleChatOverviewItemVO && chatOverviewItemVO.isSystemChat) {
            trustedStateDivider.visibility = View.INVISIBLE
            return
        }

        when (chatOverviewItemVO) {
            is SingleChatOverviewItemVO, is GroupChatOverviewItemVO -> {
                when (chatOverviewItemVO.state) {
                    Contact.STATE_HIGH_TRUST -> {
                        trustedStateDivider.setBackgroundColor(ScreenDesignUtil.getInstance().getHighColor(itemView.context.applicationContext as Application))
                        trustedStateDivider.visibility = View.VISIBLE
                    }
                    Contact.STATE_MIDDLE_TRUST -> {
                        trustedStateDivider.setBackgroundColor(ScreenDesignUtil.getInstance().getMediumColor(itemView.context.applicationContext as Application))
                        trustedStateDivider.visibility = View.VISIBLE
                    }
                    Contact.STATE_LOW_TRUST -> {
                        trustedStateDivider.setBackgroundColor(ScreenDesignUtil.getInstance().getLowColor(itemView.context.applicationContext as Application))
                        trustedStateDivider.visibility = View.VISIBLE
                    }
                    Contact.STATE_UNSIMSABLE -> {
                        trustedStateDivider.visibility = View.INVISIBLE
                    }
                    else -> LogUtil.w(this.javaClass.name, LocalizedException.UNDEFINED_ARGUMENT)
                }
            }
            else -> {
                trustedStateDivider.visibility = View.GONE
            }
        }
    }

    private fun configureAvatarLayout(item: BaseChatOverviewItemVO) {
        //LogUtil.d("ChatOverviewItemVH", "configureAvatarLayout: Called for ${item.title} (${item.chatGuid})")
        val maskedAvatarView = itemView.chat_overview_item_mask_image_view_chat_image
        imageController.fillViewWithProfileImageByGuid(item.chatGuid, maskedAvatarView, ImageUtil.SIZE_CHAT_OVERVIEW, false)
    }

    private fun getBitmap(resourceId: Int): Bitmap =
        BitmapFactory.decodeResource(itemView.context.resources, resourceId)

    private fun configureDateTextView(item: BaseChatOverviewItemVO) {
        itemView.chat_overview_item_text_view_message_date.text = timeUtil.getDateLabel(item.latestDate)
    }

    private fun configureTitleLayout(item: BaseChatOverviewItemVO) {
        val titleTextView = itemView.chat_overview_item_text_view_title
        val messageCounter = itemView.chat_overview_item_text_view_message_counter

        when (item) {
            is SingleChatOverviewItemVO -> {
                titleTextView.visibility = View.VISIBLE

                if (item.title.isNullOrBlank()) {
                    try {
                        titleTextView.text = singleChatController.getChatByGuid(item.chatGuid)?.title
                    } catch (e: LocalizedException) {
                        LogUtil.w(this.javaClass.name, e.message, e)
                    }
                } else {
                    titleTextView.text = item.title
                }
            }
            is GroupChatOverviewItemVO -> {
                titleTextView.visibility = View.VISIBLE
                titleTextView.text = item.title
            }
        }

        if ((item as ChatOverviewItemVO).messageCount > 0) {
            messageCounter.visibility = View.VISIBLE
            messageCounter.text = item.messageCount.toString()

            val backgroundDrawable = messageCounter.background
            if (backgroundDrawable != null) {
                val screenDesignUtil = ScreenDesignUtil.getInstance()

                val bgColor = screenDesignUtil.getLowColor(itemView.context.applicationContext as Application)
                val bgContrastColor =
                        screenDesignUtil.getLowContrastColor(itemView.context.applicationContext as Application)
                messageCounter.setTextColor(bgContrastColor)
                val colorFilter = PorterDuffColorFilter(bgColor, PorterDuff.Mode.SRC_ATOP)
                backgroundDrawable.colorFilter = colorFilter
            }
        } else {
            messageCounter.visibility = View.GONE
        }
    }

    private fun configurePreviewLayout(item: BaseChatOverviewItemVO) {
        when (item) {
            is SingleChatOverviewItemVO, is GroupChatOverviewItemVO -> {
                val showMediaViews = fillMediaLayout(item as ChatOverviewItemVO)

                val previewTextView = itemView.chat_overview_item_text_view_message_preview

                if (showMediaViews) {
                    val mediaLayout = itemView.chat_overview_item_media_layout
                    ViewUtil.tryFlowText(item.previewText, mediaLayout, previewTextView, defaultDisplay, true)
                } else {
                    previewTextView.text = item.previewText
                }
            }
        }
    }

    private fun fillMediaLayout(chatOverviewItemVO: ChatOverviewItemVO): Boolean {
        var returnValue = false

        val mediaIcon = getMediaIconChatItemNew(chatOverviewItemVO)
        val mediaView = itemView.chat_overview_item_media_icon

        if (mediaIcon == -1) {
            mediaView.visibility = View.GONE
        } else {
            mediaView.visibility = View.VISIBLE
            mediaView.setImageResource(mediaIcon)
            returnValue = true
        }

        val priorityIconView = itemView.chat_overview_item_important_icon
        val priorityTexView = itemView.chat_overview_item_important_tv

        if (chatOverviewItemVO.isPriority) {
            priorityIconView.visibility = View.VISIBLE
            priorityTexView.visibility = View.VISIBLE
            returnValue = true
        } else {
            priorityIconView.visibility = View.GONE
            priorityTexView.visibility = View.GONE
        }

        val sendStateView = itemView.chat_overview_item_send_state

        if (chatOverviewItemVO.isSentMessage) {
            when {
                chatOverviewItemVO.hasSendError -> {
                    sendStateView.visibility = View.VISIBLE
                    sendStateView.drawable.level = Message.MESSAGE_STATUS_ERROR
                    returnValue = true
                }
                chatOverviewItemVO.hasRead -> {
                    sendStateView.visibility = View.VISIBLE
                    sendStateView.drawable.level = Message.MESSAGE_STATUS_READ
                    returnValue = true
                }
                chatOverviewItemVO.hasDownloaded -> {
                    sendStateView.visibility = View.VISIBLE
                    sendStateView.drawable.level = Message.MESSAGE_STATUS_DOWNLOADED
                    returnValue = true
                }
                chatOverviewItemVO.isSendConfirm -> {
                    sendStateView.visibility = View.VISIBLE
                    sendStateView.drawable.level = Message.MESSAGE_STATUS_SENT
                    returnValue = true
                }
                else -> sendStateView.visibility = View.GONE
            }
        } else {
            sendStateView.visibility = View.GONE
        }

        return returnValue
    }

    private fun getMediaIconChatItemNew(chatOverviewItem: BaseChatOverviewItemVO): Int {
        return if (chatOverviewItem.dateSendTimed != null) {
            R.drawable.send_timed
        } else {
            when (chatOverviewItem.mediaType) {
                BaseChatOverviewItemVO.MSG_MEDIA_TYPE_IMAGE -> R.drawable.media_photo

                BaseChatOverviewItemVO.MSG_MEDIA_TYPE_AUDIO -> R.drawable.media_audio

                BaseChatOverviewItemVO.MSG_MEDIA_TYPE_MOVIE -> R.drawable.media_movie

                BaseChatOverviewItemVO.MSG_MEDIA_TYPE_DESTROY -> R.drawable.media_destroy

                BaseChatOverviewItemVO.MSG_MEDIA_TYPE_FILE -> R.drawable.media_data

                BaseChatOverviewItemVO.MSG_MEDIA_TYPE_RICH_CONTENT -> R.drawable.media_camera_roll

                else -> -1
            }
        }
    }
}
