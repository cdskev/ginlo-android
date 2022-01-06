// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.contracts

interface UpdateAccountInfoCallback {
    fun updateAccountInfoFinished()
    fun updateAccountInfoFailed(error: String?)
}
