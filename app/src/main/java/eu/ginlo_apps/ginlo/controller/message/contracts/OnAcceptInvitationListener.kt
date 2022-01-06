// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message.contracts

import eu.ginlo_apps.ginlo.greendao.Chat

interface OnAcceptInvitationListener {
    fun onAcceptSuccess(chat: Chat)
    fun onAcceptError(message: String?, chatWasRemoved: Boolean)
}
