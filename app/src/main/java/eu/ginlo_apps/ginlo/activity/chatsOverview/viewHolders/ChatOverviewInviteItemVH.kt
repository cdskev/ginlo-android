// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.chatsOverview.viewHolders

import android.app.Application
import android.view.View
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemClick
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemLongClick
import eu.ginlo_apps.ginlo.controller.ImageController
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Contact
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.chat.overview.BaseChatOverviewItemVO
import eu.ginlo_apps.ginlo.model.chat.overview.GroupChatOverviewItemInvitationVO
import eu.ginlo_apps.ginlo.model.chat.overview.SingleChatOverviewItemInvitationVO
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil
import eu.ginlo_apps.ginlo.util.TimeUtil
import kotlinx.android.synthetic.main.chat_overview_invite_item_layout.view.chat_overview_item_invite_description
import kotlinx.android.synthetic.main.chat_overview_invite_item_layout.view.chat_overview_item_mask_image_view_chat_image
import kotlinx.android.synthetic.main.chat_overview_invite_item_layout.view.chat_overview_item_text_view_accept_button
import kotlinx.android.synthetic.main.chat_overview_invite_item_layout.view.chat_overview_item_text_view_decline_button
import kotlinx.android.synthetic.main.chat_overview_invite_item_layout.view.chat_overview_item_text_view_message_date
import kotlinx.android.synthetic.main.chat_overview_invite_item_layout.view.chat_overview_item_text_view_title
import kotlinx.android.synthetic.main.chat_overview_invite_item_layout.view.trust_state_divider

class ChatOverviewInviteItemVH(
    imageController: ImageController,
    timeUtil: TimeUtil,
    itemView: View,
    onChatItemClick: OnChatItemClick?,
    onChatItemLongClick: OnChatItemLongClick?
) : ChatOverviewBaseItemVH(itemView, imageController, timeUtil, onChatItemClick, onChatItemLongClick) {
    override fun setItem(item: BaseChatOverviewItemVO) {
        bindTrustedStateView(item)

        bindAvatarView(item)

        bindDateTextView(item)

        bindTitleView(item)

        bindPreviewView(item)
    }

    private fun bindTrustedStateView(chatOverviewItemVO: BaseChatOverviewItemVO) {

        when (chatOverviewItemVO.state) {
            Contact.STATE_HIGH_TRUST -> {
                itemView.trust_state_divider.setBackgroundColor(ScreenDesignUtil.getInstance().getHighColor(itemView.context.applicationContext as Application))
                itemView.trust_state_divider.visibility = View.VISIBLE
            }
            Contact.STATE_MIDDLE_TRUST -> {
                itemView.trust_state_divider.setBackgroundColor(ScreenDesignUtil.getInstance().getMediumColor(itemView.context.applicationContext as Application))
                itemView.trust_state_divider.visibility = View.VISIBLE
            }
            Contact.STATE_LOW_TRUST -> {
                itemView.trust_state_divider.setBackgroundColor(ScreenDesignUtil.getInstance().getLowColor(itemView.context.applicationContext as Application))
                itemView.trust_state_divider.visibility = View.VISIBLE
            }
            Contact.STATE_UNSIMSABLE -> {
                itemView.trust_state_divider.visibility = View.INVISIBLE
            }
            else -> LogUtil.w(this.javaClass.name, LocalizedException.UNDEFINED_ARGUMENT)
        }
    }

    private fun bindAvatarView(chatOverviewItem: BaseChatOverviewItemVO) {
        if (chatOverviewItem is GroupChatOverviewItemInvitationVO) {
            itemView.chat_overview_item_mask_image_view_chat_image.setImageResource(R.drawable.gfx_group_placeholder)
        } else {
            itemView.chat_overview_item_mask_image_view_chat_image.setImageResource(R.drawable.gfx_profil_placeholder)
        }
    }

    private fun bindDateTextView(chatOverviewItemVO: BaseChatOverviewItemVO) {
        itemView.chat_overview_item_text_view_message_date.text = timeUtil.getDateLabel(chatOverviewItemVO.latestDate)
    }

    private fun bindTitleView(chatOverviewItemVO: BaseChatOverviewItemVO) {
        itemView.chat_overview_item_text_view_title.visibility = View.VISIBLE
        itemView.chat_overview_item_text_view_title.text = chatOverviewItemVO.title
    }

    private fun bindPreviewView(chatOverviewItemVO: BaseChatOverviewItemVO) {
        itemView.chat_overview_item_text_view_accept_button.tag = chatOverviewItemVO.chat

        if (chatOverviewItemVO is SingleChatOverviewItemInvitationVO) {
            itemView.chat_overview_item_text_view_accept_button.setText(R.string.chat_single_invite_accept)
            itemView.chat_overview_item_invite_description.setText(R.string.chat_stream_contactRequestBy)
        } else {
            itemView.chat_overview_item_text_view_accept_button.setText(R.string.chat_stream_confirmContact)
            itemView.chat_overview_item_invite_description.setText(R.string.chat_stream_GroupRequest)
        }

        itemView.chat_overview_item_text_view_decline_button.tag = chatOverviewItemVO.chat
        if (chatOverviewItemVO is SingleChatOverviewItemInvitationVO) {
            itemView.chat_overview_item_text_view_decline_button.setText(R.string.chat_single_invite_blockContact)
        } else {
            itemView.chat_overview_item_text_view_decline_button.setText(R.string.chat_stream_blockContact)
        }
    }
}