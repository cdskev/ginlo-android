// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message.contracts

interface OnDeleteTimedMessageListener {
    fun onDeleteMessageError(errorMessage: String)
    fun onDeleteAllMessagesSuccess(chatGuid: String)
    fun onDeleteSingleMessageSuccess(chatGuid: String)
}
