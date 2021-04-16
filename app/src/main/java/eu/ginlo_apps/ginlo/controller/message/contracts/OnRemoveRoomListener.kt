// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message.contracts

interface OnRemoveRoomListener {
    fun onRemoveRoomSuccess()
    fun onRemoveRoomFail(message: String)
}
