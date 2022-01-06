// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.contracts

interface HasCompanyManagementCallback {
    fun onSuccess(managementState: String?)
    fun onFail(message: String?)
}
