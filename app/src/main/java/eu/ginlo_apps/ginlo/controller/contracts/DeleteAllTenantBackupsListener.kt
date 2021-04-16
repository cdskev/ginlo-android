// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.contracts

interface DeleteAllTenantBackupsListener {
    fun onDeleteAllTenantBackupsFinished()
    fun onDeleteAllTenantBackupsFailed(message: String?)
}
