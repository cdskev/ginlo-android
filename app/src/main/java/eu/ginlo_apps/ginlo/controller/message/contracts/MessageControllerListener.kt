// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message.contracts

import android.util.SparseArray
import eu.ginlo_apps.ginlo.greendao.Message

interface MessageControllerListener {
    fun onNewMessages(types: Int)
    fun onMessagesChanged(messagesListContainer: SparseArray<MutableList<Message>>)
}
