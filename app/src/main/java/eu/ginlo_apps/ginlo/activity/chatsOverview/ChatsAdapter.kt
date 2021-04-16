// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.chatsOverview

import android.app.Activity
import android.content.Context
import android.view.Display
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemClick
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemLongClick
import eu.ginlo_apps.ginlo.activity.chatsOverview.viewHolders.ChatOverviewBaseItemVH
import eu.ginlo_apps.ginlo.activity.chatsOverview.viewHolders.ChatOverviewChannelItemVH
import eu.ginlo_apps.ginlo.activity.chatsOverview.viewHolders.ChatOverviewInviteItemVH
import eu.ginlo_apps.ginlo.activity.chatsOverview.viewHolders.ChatOverviewItemVH
import eu.ginlo_apps.ginlo.activity.chatsOverview.viewHolders.ChatOverviewServiceItemVH
import eu.ginlo_apps.ginlo.controller.message.SingleChatController
import eu.ginlo_apps.ginlo.model.chat.overview.BaseChatOverviewItemVO
import eu.ginlo_apps.ginlo.model.chat.overview.ChannelChatOverviewItemVO
import eu.ginlo_apps.ginlo.model.chat.overview.GroupChatOverviewItemInvitationVO
import eu.ginlo_apps.ginlo.model.chat.overview.ServiceChatOverviewItemVO
import eu.ginlo_apps.ginlo.model.chat.overview.SingleChatOverviewItemInvitationVO
import eu.ginlo_apps.ginlo.themedInflater
import eu.ginlo_apps.ginlo.util.ImageLoader
import eu.ginlo_apps.ginlo.util.TimeUtil

class ChatsAdapter(
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val singleChatController: SingleChatController,
    private val display: Display,
    private val source: MutableList<BaseChatOverviewItemVO>,
    private val onChatItemClick: OnChatItemClick?,
    private val onChatItemLongClick: OnChatItemLongClick?
) :
    RecyclerView.Adapter<ChatOverviewBaseItemVH>() {

    companion object {
        private const val ITEM_TYPE_NORMAL = 0
        private const val ITEM_TYPE_INVITE = 1
        private const val ITEM_TYPE_CHANNEL = 2
        private const val ITEM_TYPE_SERVICE = 3
    }

    private val layoutInflater: LayoutInflater by lazy { LayoutInflater.from(context).themedInflater(context) }

    private val timeUtil: TimeUtil by lazy { TimeUtil(context) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatOverviewBaseItemVH {
        return when (viewType) {
            ITEM_TYPE_SERVICE -> ChatOverviewServiceItemVH(
                imageLoader,
                timeUtil,
                layoutInflater.inflate(R.layout.chat_overview_item_single_layout, parent, false),
                onChatItemClick,
                onChatItemLongClick,
                singleChatController
            )
            ITEM_TYPE_CHANNEL -> ChatOverviewChannelItemVH(
                display,
                imageLoader,
                timeUtil,
                layoutInflater.inflate(R.layout.chat_overview_item_channel, parent, false),
                onChatItemClick,
                onChatItemLongClick
            )
            ITEM_TYPE_INVITE -> ChatOverviewInviteItemVH(
                imageLoader,
                timeUtil,
                layoutInflater.inflate(R.layout.chat_overview_invite_item_layout, parent, false),
                null,
                null
            )
            ITEM_TYPE_NORMAL -> ChatOverviewItemVH(
                singleChatController,
                display,
                imageLoader,
                timeUtil,
                layoutInflater.inflate(R.layout.chat_overview_item_single_layout, parent, false),
                onChatItemClick,
                onChatItemLongClick
            )
            else -> throw Throwable("Unrecognized layout type. viewType= $viewType")
        }
    }

    override fun getItemCount(): Int =
        source.count()

    fun getItem(position: Int): BaseChatOverviewItemVO? {
        return source.getOrNull(position)
    }

    fun getPosition(item: BaseChatOverviewItemVO): Int {
        return source.indexOf(item)
    }

    fun setItems(items: List<BaseChatOverviewItemVO>, withNotify: Boolean) {
        source.clear()
        source.addAll(items)

        if (withNotify)
            notifyDataSetChanged()
    }

    fun removeItem(item: BaseChatOverviewItemVO) {
        (context as Activity).runOnUiThread {
            val position = getPosition(item)
            source.remove(item)

            notifyItemRemoved(position)
        }
    }

    override fun onBindViewHolder(holder: ChatOverviewBaseItemVH, position: Int) {
        holder.setItem(source[position])
    }

    override fun getItemViewType(position: Int): Int {
        return when (source[position]) {
            is SingleChatOverviewItemInvitationVO, is GroupChatOverviewItemInvitationVO -> ITEM_TYPE_INVITE
            is ChannelChatOverviewItemVO -> ITEM_TYPE_CHANNEL
            is ServiceChatOverviewItemVO -> ITEM_TYPE_SERVICE
            else -> ITEM_TYPE_NORMAL
        }
    }
}