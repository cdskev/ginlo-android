// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.contracts

interface OnCreateAccountListener {
    fun onCreateAccountSuccess()
    fun onCreateAccountFail(errorMsg: String?, haveToResetRegistration: Boolean)
}
