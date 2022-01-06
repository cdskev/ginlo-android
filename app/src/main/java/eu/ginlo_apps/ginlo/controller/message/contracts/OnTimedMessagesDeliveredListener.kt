// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message.contracts

interface OnTimedMessagesDeliveredListener {
    fun timedMessageDelivered(chatGuids: List<String>)
}
