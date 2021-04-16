// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.UseCases

import android.os.Environment
import eu.ginlo_apps.ginlo.controller.LocalBackupHelper
import eu.ginlo_apps.ginlo.log.LogUtil
import java.io.File

class DeleteLocalBackupsUseCase {
    private val TAG = DeleteLocalBackupsUseCase::class.java.simpleName

    fun deleteLocalBackups()
    {
        val basePath = File(Environment.getExternalStorageDirectory(), LocalBackupHelper.LOCAL_BACKUP_DIRECTORY)
        if (!basePath.exists() ) {
            return
        }

        val backupPath = File(basePath, LocalBackupHelper.LOCAL_BACKUP_SUBDIRECTORY)
        if (!backupPath.exists()) {
            LogUtil.w(TAG, "Backup path couldn't be set up correctly.")
            return
        }

        val dst = File(backupPath, LocalBackupHelper.LOCAL_BACKUP_FILE)
        if (dst.exists() && !dst.delete()) {
            LogUtil.w(TAG, "Couldn't delete old backup.")
        }
        LogUtil.i(TAG, "Backup sucessfully deleted..")
    }
}