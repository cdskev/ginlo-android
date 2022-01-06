// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.chat.overview

class GroupChatOverviewItemVO : ChatOverviewItemVO() {
    var hasLocalUserRead: Boolean = false

    var isReadOnly: Boolean = false

    var roomType: String? = null

    var isRemoved: Boolean = false
}
