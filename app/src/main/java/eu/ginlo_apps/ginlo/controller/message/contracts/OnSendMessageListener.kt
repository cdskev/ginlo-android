// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message.contracts

import eu.ginlo_apps.ginlo.greendao.Message

interface OnSendMessageListener {
    fun onSaveMessageSuccess(message: Message?)
    fun onSendMessageSuccess(message: Message?, countNotSendMessages: Int)
    fun onSendMessageError(message: Message?, errorMessage: String?, localizedErrorIdentifier: String?)
}
