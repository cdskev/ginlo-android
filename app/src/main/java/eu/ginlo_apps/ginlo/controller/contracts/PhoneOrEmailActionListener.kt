// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.contracts

interface PhoneOrEmailActionListener {
    fun onSuccess(result: String?)
    fun onFail(errorMsg: String, emailIsInUse: Boolean)
}
