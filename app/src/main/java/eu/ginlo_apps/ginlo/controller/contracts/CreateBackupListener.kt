// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.contracts

interface CreateBackupListener {
    fun onCreateBackupStateChanged(state: Int)
    fun onCreateBackupSaveStateUpdate(percent: Int)
    fun onCreateBackupSaveChatsUpdate(current: Int, size: Int)
    fun onCreateBackupFailed(message: String, needToConnectAgain: Boolean)

    companion object {
        const val STATE_NOT_RUNNING = -1
        const val STATE_STARTED = 0
        const val STATE_SAVE_CHATS = 1
        const val STATE_WRITE_BACKUP_FILE = 2
        const val STATE_SAVE_BACKUP_FILE = 3
        const val STATE_FINISHED = 4
        const val STATE_SAVE_SERVICES = 5
        const val STATE_ERROR = 6;
    }
}
