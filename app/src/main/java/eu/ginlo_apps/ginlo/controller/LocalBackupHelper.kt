// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller

import android.os.Bundle
import android.os.Environment
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.constant.AppConstants
import eu.ginlo_apps.ginlo.util.RuntimeConfig
import java.io.File
import java.io.IOException

class LocalBackupHelper(val application: SimsMeApplication) {
    companion object {
        const val LOCAL_BACKUP_PATH = "local-backup-path"
        const val LOCAL_BACKUP_DIRECTORY = "ginlo"
        const val LOCAL_BACKUP_SUBDIRECTORY = "Backups"
        const val LOCAL_BACKUP_FILE = "ginlo-backup.zip"
        private const val TEMP_LOCAL_BACKUP_FILE = "ginlo-backup.temp"
        private val TAG = LocalBackupHelper::class.java.simpleName
        const val B2B = "B2B"
        const val B2C = "B2C"
    }
    private val flavor = if (RuntimeConfig.isB2c()) B2C else B2B

    fun copyBackupToFinalPath(tempFilePath: String): Boolean {

        val accountId = application.accountController.account.accountID
        if(accountId.isNullOrEmpty())
            return false;

        val basePath = File(Environment.getExternalStorageDirectory(), LOCAL_BACKUP_DIRECTORY)

        if (!basePath.exists() && !basePath.mkdirs() && !basePath.canWrite()) {
            LogUtil.w(TAG, "Base tempFilePath couldn't be set up correctly.")
            return false
        }

        val backupPath = File(basePath, LOCAL_BACKUP_SUBDIRECTORY)
        if (!backupPath.exists() && !backupPath.mkdirs() && !basePath.canWrite()) {
            LogUtil.w(TAG, "Backup tempFilePath couldn't be set up correctly.")
            return false
        }

        val accountDirectoryPath = File(backupPath, accountId);
        if (!accountDirectoryPath.exists() && !accountDirectoryPath.mkdirs() && !accountDirectoryPath.canWrite()) {
            LogUtil.w(TAG, "Backup accountId directory path couldn't be set up correctly.")
            return false
        }

        val backupFileName : String = flavor + "_" + LOCAL_BACKUP_FILE
        val targetBackupFile = File(accountDirectoryPath, backupFileName)
        if (targetBackupFile.exists() && !targetBackupFile.delete()) {
            LogUtil.w(TAG, "Couldn't delete old backup.")
            return false
        }

        try {
            val sourceFile = File(tempFilePath)
            if (!sourceFile.renameTo(targetBackupFile)) {
                return false
            }

            val preferencesController = SimsMeApplication.getInstance().preferencesController
            preferencesController.latestBackupFileSize = targetBackupFile.length()
            preferencesController.latestBackupDate = targetBackupFile.lastModified()
            preferencesController.latestBackupPath = targetBackupFile.absolutePath
            return true
        } catch (exc: IOException) {
            LogUtil.e(TAG, exc.message, exc)
            return false
        }
    }

    fun getBackupTempPath(): File? {

        val accountId = application.accountController.account.accountID
        if(accountId.isNullOrEmpty())
            return null;

        val basePath = File(Environment.getExternalStorageDirectory(), LOCAL_BACKUP_DIRECTORY)
        if (!basePath.exists() && !basePath.mkdirs() && !basePath.canWrite()) {
            LogUtil.w(TAG, "Base path couldn't be set up correctly.")
            return null
        }

        val backupPath = File(basePath, LOCAL_BACKUP_SUBDIRECTORY)
        if (!backupPath.exists() && !backupPath.mkdirs() && !basePath.canWrite()) {
            LogUtil.w(TAG, "Backup path couldn't be set up correctly.")
            return null
        }

        val accountDirectoryPath = File(backupPath, accountId);
        if (!accountDirectoryPath.exists() && !accountDirectoryPath.mkdirs() && !accountDirectoryPath.canWrite()) {
            LogUtil.w(TAG, "Backup accountId directory path  couldn't be set up correctly.")
            return null
        }


        val tempBackupFile = File(accountDirectoryPath, TEMP_LOCAL_BACKUP_FILE)
        if (tempBackupFile.exists() && !tempBackupFile.delete()) {
            return null
        }

        return tempBackupFile
    }

    private fun fetchBackUpServerAccountIDs(): Array<String>? =
            application.accountController.account.allServerAccountIDs?.split(",".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()


    fun getBundleForLocalBackup():List<Bundle> {

        val basePath = File(Environment.getExternalStorageDirectory(), LOCAL_BACKUP_DIRECTORY)
        if (!basePath.exists()) {
            return emptyList()
        }

        val backupPath = File(basePath, LOCAL_BACKUP_SUBDIRECTORY)
        if (!backupPath.exists()) {
            return emptyList()
        }

        val backupServerAccountIDs = fetchBackUpServerAccountIDs()

        if (backupServerAccountIDs.isNullOrEmpty()) {
            return emptyList();
        }

        return backupServerAccountIDs.mapNotNull{accountID ->
            val accountDirectoryPath = File(backupPath, accountID)
                    .takeIf { dir -> dir.exists()}
                    ?: return@mapNotNull null

            val backupInfo = getBackupInfo(accountDirectoryPath)
                    ?:return@mapNotNull null

            Bundle().apply {
                putString(AppConstants.LOCAL_BACKUP_ITEM_NAME, backupInfo.file.name)
                putString(AppConstants.LOCAL_BACKUP_FLAVOUR, backupInfo.flavor)
                putLong(AppConstants.LOCAL_BACKUP_ITEM_MOD_DATE, backupInfo.file.lastModified())
                putLong(AppConstants.LOCAL_BACKUP_ITEM_SIZE, backupInfo.file.length())
                putString(LOCAL_BACKUP_PATH, backupInfo.file.absolutePath)
            }
        }
    }
    private fun getBackupInfo(basePath: File): BackupInfo? =
            File(basePath, "${flavor}_$LOCAL_BACKUP_FILE")
                    .takeIf { file -> file.exists() }
                    ?.let { BackupInfo(it, flavor) }

}

private class BackupInfo(val file: File, val flavor: String)
{
}