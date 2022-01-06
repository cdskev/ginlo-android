// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message.contracts

import eu.ginlo_apps.ginlo.greendao.Chat

interface OnDeclineInvitationListener {
    fun onDeclineSuccess(chat: Chat)
    fun onDeclineError(chat: Chat, message: String?, chatWasRemoved: Boolean)
}
