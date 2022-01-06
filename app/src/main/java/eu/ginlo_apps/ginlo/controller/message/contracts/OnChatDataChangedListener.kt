// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message.contracts

interface OnChatDataChangedListener {
    fun onChatDataChanged(clearImageCache: Boolean)
    fun onChatDataLoaded(lastMessageId: Long)
}
