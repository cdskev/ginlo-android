// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.chatsOverview.contracts

import eu.ginlo_apps.ginlo.model.chat.overview.BaseChatOverviewItemVO

interface OnChatItemClick {
    fun onClick(item: BaseChatOverviewItemVO)
}