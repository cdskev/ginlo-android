// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.router

interface Router : RouterBase {
    fun startNextScreenForRequiredManagementState(mcState: String, firstName: String?, lastName: String?)
}
