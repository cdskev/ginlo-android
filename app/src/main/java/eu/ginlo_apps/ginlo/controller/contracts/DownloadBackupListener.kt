// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.contracts

interface DownloadBackupListener {
    fun onDownloadBackupStateChanged(state: Int)
    fun onDownloadBackupDownloadUpdate(percent: Int)
    fun onDownloadBackupCopyUpdate(percent: Int)
    fun onDownloadBackupFailed(message: String?)
    fun onDownloadBackupFinished(localBackupPath: String)

    companion object {
        const val STATE_STARTED_DOWNLOAD = 0
        const val STATE_STARTED_COPY_TO_SIMSME = 1
    }
}
