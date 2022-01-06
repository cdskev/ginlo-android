// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.contracts

interface SetAddressInfoListener {
    fun onSuccess(result: String)
    fun onFail(errorMsg: String)
}
