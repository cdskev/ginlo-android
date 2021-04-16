// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.contracts

interface ConfigureCompanyAccountListener {
    fun onConfigureStateChanged(state: Int)
    fun onConfigureStateUpdate(state: Int, current: Int, size: Int)
    fun onConfigureFailed(message: String, errorIdentifier: String?, lastState: Int)
}
