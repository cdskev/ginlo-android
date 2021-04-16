// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message.contracts

import eu.ginlo_apps.ginlo.greendao.Message

interface OnMessageReceivedListener {
    fun onMessageReceived(message: Message)
}
