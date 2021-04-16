// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.chatsOverview.viewHolders

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemClick
import eu.ginlo_apps.ginlo.activity.chatsOverview.contracts.OnChatItemLongClick
import eu.ginlo_apps.ginlo.model.chat.overview.BaseChatOverviewItemVO
import eu.ginlo_apps.ginlo.util.ImageLoader
import eu.ginlo_apps.ginlo.util.TimeUtil

abstract class ChatOverviewBaseItemVH(
    itemView: View,
    protected val imageLoader: ImageLoader,
    protected val timeUtil: TimeUtil,
    protected val onChatItemClick: OnChatItemClick?,
    protected val onChatItemLongClick: OnChatItemLongClick?
) :
    RecyclerView.ViewHolder(itemView) {

    abstract fun setItem(item: BaseChatOverviewItemVO)
}