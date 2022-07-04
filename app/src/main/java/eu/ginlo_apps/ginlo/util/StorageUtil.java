package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

import eu.ginlo_apps.ginlo.ConfigureBackupActivity;
import eu.ginlo_apps.ginlo.ViewAttachmentActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;

/**
 * Class for folder and external data storage handling
 */
public class StorageUtil {
    private final static String TAG = StorageUtil.class.getSimpleName();
    private static final String BACKGROUND_IMAGE = "background.jpg";
    private static final String INTERNAL_PROFILE_IMAGES_DIR = "profileImg";
    private static final String INTERNAL_ATTACHMENT_DIR = "attachments";
    private static final String INTERNAL_BACKUP_ROOT_DIR = "backup";
    private static final String INTERNAL_BACKUP_SUBDIR = "backup-" + DateUtil.getDateStringInBackupFormat();
    private static final String INTERNAL_BACKUP_UNZIP_SUBDIR = "unzipped";
    private static final String INTERNAL_BACKUP_ATTACHMENT_SUBDIR = "attachments";
    private static final String OLD_EXTERNAL_BACKUP_SUBDIR = "ginlo";

    private final Context context;
    private final SimsMeApplication mApplication;
    private final PreferencesController mPreferencesController;

    private final FileUtil mFileUtil;

    private final File mInternalFilesDir;
    private final File mProfileImagesDir;
    private final File mAttachmentDir;

    public StorageUtil(Context context) {
        this.context = context;
        this.mFileUtil = new FileUtil(context);
        this.mApplication = (SimsMeApplication) context;
        this.mPreferencesController = mApplication.getPreferencesController();

        this.mInternalFilesDir = mApplication.getFilesDir();

        this.mProfileImagesDir = new File(mInternalFilesDir, INTERNAL_PROFILE_IMAGES_DIR);
        if (!mProfileImagesDir.isDirectory()) {
            if(!mProfileImagesDir.mkdirs()) {
                LogUtil.w(TAG, "Failed to create profileImagesDir.");
            }
        }

        this.mAttachmentDir = new File(mInternalFilesDir, INTERNAL_ATTACHMENT_DIR);
        if (!mAttachmentDir.isDirectory()) {
            if(!mAttachmentDir.mkdirs()) {
                LogUtil.w(TAG, "Failed to create mAttachmentDir.");
            }
        }
    }

    public File getInternalFilesDir() {
        return mInternalFilesDir;
    }

    public File getProfileImageDir() {
        return mProfileImagesDir;
    }

    public File getAttachmentDir() {
        return mAttachmentDir;
    }

    public void clearProfileImageDir() {
        synchronized (this) {
            mFileUtil.deleteAllFilesInDir(mProfileImagesDir);
        }
    }

    public File getBackgroundImageFile() {
        return new File(mInternalFilesDir, BACKGROUND_IMAGE);
    }

    /**
     * Store a media file to the main media storage dir. It must have been choosen by the user.
     * @param sourceFileUri Media file Uri
     * @param destRootDirUri Main media storage dir Uri
     * @param destFilename Filename to be given to the media file
     * @param mimeType Mimetype to be given to the media file
     * @return
     */
    public boolean storeMediaFile(Uri sourceFileUri, Uri destRootDirUri, String destFilename, String mimeType, boolean forceOverwrite) {
        if(sourceFileUri == null || destRootDirUri == null || StringUtil.isNullOrEmpty(destFilename)) {
            LogUtil.e(TAG, "storeMediaFile: No valid media info given: sourceFileUri = "
                    + sourceFileUri + ", destRootDirUri = " + destRootDirUri + ", destFilename = " + destFilename);
            return false;
        }

        String docId = DocumentsContract.getTreeDocumentId(destRootDirUri);
        // Uri destDirUri = DocumentsContract.buildDocumentUriUsingTree(destRootDirUri, docId );
        Uri destDirUri = DocumentsContract.buildChildDocumentsUriUsingTree(destRootDirUri, docId );
        Uri destUri = null;
        InputStream is = null;
        OutputStream os = null;

        try {
        // KS: Warum funktioniert der selector nicht? Es werden immer alle Zeilen geliefert!
            Cursor c = mApplication.getContentResolver().query(destDirUri,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME + " = ?",
                    new String[]{destFilename},
                    null, null);

            if(c != null && c.moveToFirst()) {
                do {
                    String fileName = c.getString(0);
                    LogUtil.d(TAG, "storeMediaFile: Processing directory entry for " + fileName + ", document id = " + c.getString(1));
                    if(destFilename.equals(fileName)) {
                        LogUtil.d(TAG, "storeMediaFile: Matching file found: " + fileName);
                        // File found!
                        // Build the uri for the file
                        destUri = DocumentsContract.buildDocumentUriUsingTree(destDirUri, c.getString(1));
                        break;
                    }
                } while (c.moveToNext());
            }

            if(c != null) {
                c.close();
            }

            if(destUri != null) {
                // File of the same name already exists!
                LogUtil.d(TAG, "storeMediaFile: File " + destFilename + " already exists. Must overwrite = " + forceOverwrite);
                if(forceOverwrite) {
                    DocumentsContract.deleteDocument(mApplication.getContentResolver(), destUri);
                } else {
                    return true;
                }
            }

            destUri = DocumentsContract.createDocument(mApplication.getContentResolver(), destDirUri, mimeType, destFilename);
            LogUtil.d(TAG, "storeMediaFile: createDocument brought " + destUri);

            if(destUri == null) {
                LogUtil.e(TAG, "storeMediaFile: Cannot create " + destRootDirUri + "/" + destFilename);
                return false;
            }

            File sourceFile = new File(sourceFileUri.getPath());
            if(sourceFile == null) {
                LogUtil.e(TAG, "storeMediaFile: Cannot access " + sourceFileUri.getPath());
                return false;
            }

            is = new FileInputStream(sourceFile);
            os = mApplication.getContentResolver().openOutputStream(destUri);
            StreamUtil.copyStreams(is, os);

        } catch (Exception e) {
            LogUtil.e(TAG, "storeMediaFile: Cannot create file: " + e.getMessage());
            return false;

        } finally {
            StreamUtil.closeStream(is);
            StreamUtil.closeStream(os);
        }

        return true;
    }

    public Uri getMediaDestinationUri() {
        final String mediaPref = mPreferencesController.getSharedPreferences().getString(ViewAttachmentActivity.LOCAL_MEDIA_URI_PREF, "");
        if(StringUtil.isNullOrEmpty(mediaPref)) {
            LogUtil.e(TAG, "getMediaDestinationUri: No valid media destination: " + mediaPref);
            return null;
        }
        Uri mediaDestination = Uri.parse(mediaPref);
        try {
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            mApplication.getContentResolver().takePersistableUriPermission(mediaDestination, takeFlags);
        } catch (Exception e) {
            LogUtil.e(TAG, "getMediaDestinationUri: Caught exception in takePersistableUriPermission: " + e.getMessage(), e);
            return null;
        }
        return mediaDestination;
    }

    public Uri getRestoreSourceUri(String restoreSource) {
        Uri restoreSourceUri = Uri.parse(restoreSource);
        try {
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            mApplication.getContentResolver().takePersistableUriPermission(restoreSourceUri, takeFlags);
        } catch (Exception e) {
            LogUtil.e(TAG, "getRestoreSourceUri: Caught exception in takePersistableUriPermission: " + e.getMessage(), e);
            return null;
        }
        return restoreSourceUri;
    }

    public Uri getBackupDestinationUri() {
        final String backupPref = mPreferencesController.getSharedPreferences().getString(ConfigureBackupActivity.LOCAL_BACKUP_URI_PREF, "");
        if(StringUtil.isNullOrEmpty(backupPref)) {
            LogUtil.e(TAG, "getBackupDestinationUri: No valid backup destination: " + backupPref);
            return null;
        }
        Uri backupDestination = Uri.parse(backupPref);
        try {
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            mApplication.getContentResolver().takePersistableUriPermission(backupDestination, takeFlags);
        } catch (Exception e) {
            LogUtil.e(TAG, "getBackupDestinationUri: Caught exception in takePersistableUriPermission: " + e.getMessage(), e);
            return null;
        }
        return backupDestination;
    }

    public long getBackupDestinationSize(Uri backupDestination) {
        long backupSize = 0L;
        try (Cursor cursor = mApplication.getContentResolver()
                .query(backupDestination, null, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (!cursor.isNull(sizeIndex)) {
                    backupSize = cursor.getLong(sizeIndex);
                    LogUtil.d(TAG, "getBackupDestinationSize: Got destination SIZE: " + backupSize);
                }
                cursor.close();
            }
            return backupSize;
        }
    }

    public String getBackupDestinationName(Uri backupDestination) {
        String backupName = "unknown";
        try (Cursor cursor = mApplication.getContentResolver()
                .query(backupDestination, null, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (!cursor.isNull(nameIndex)) {
                    backupName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    LogUtil.d(TAG, "getBackupDestinationSize: Got destination DISPLAY_NAME: " + backupName);
                }
                cursor.close();
            }
            return backupName;
        }
    }

    @NonNull
    public File getInternalBackupRootDirectory(boolean clear) throws LocalizedException {
        File internalBackupRootDir = new File(context.getFilesDir(), INTERNAL_BACKUP_ROOT_DIR);

        if (!internalBackupRootDir.isDirectory()) {
            internalBackupRootDir.delete();
            if (!internalBackupRootDir.mkdirs()) {
                throw new LocalizedException(LocalizedException.BACKUP_FOLDER_FAILED,
                        "getInternalBackupRootDirectory: Could not create INTERNAL_BACKUP_ROOT_DIR.");
            }
        } else if(clear) {
            mFileUtil.deleteAllFilesInDir(internalBackupRootDir);
        }

        return internalBackupRootDir;
    }

    public File getInternalBackupSubDirectory(String subDirectory, boolean clear) throws LocalizedException {
        File internalBackupSubDir = new File(getInternalBackupRootDirectory(false), subDirectory);

        if (internalBackupSubDir.isDirectory()) {
            if (clear) {
                if (!mFileUtil.deleteAllFilesInDir(internalBackupSubDir)) {
                    throw new LocalizedException(LocalizedException.BACKUP_DELETE_FILE_FAILED,
                            "getInternalBackupSubDirectory: Could not delete files in " + internalBackupSubDir.getPath());
                }
            }
        } else {
            internalBackupSubDir.delete();
            if (!internalBackupSubDir.mkdir()) {
                throw new LocalizedException(LocalizedException.BACKUP_CREATE_BACKUP_FAILED,
                        "getInternalBackupSubDirectory: Could not create internal sub directory " + internalBackupSubDir.getPath());
            }
        }

        return internalBackupSubDir;
    }

    public File getInternalBackupDirectory(boolean clear) throws LocalizedException {
        return getInternalBackupSubDirectory(INTERNAL_BACKUP_SUBDIR, clear);
    }

    public File getInternalBackupUnzipDirectory(boolean clear) throws LocalizedException {
        return getInternalBackupSubDirectory(INTERNAL_BACKUP_UNZIP_SUBDIR, clear);
    }

    public File getInternalBackupAttachmentDirectory(boolean clear) throws LocalizedException {
        return getInternalBackupSubDirectory(INTERNAL_BACKUP_ATTACHMENT_SUBDIR, clear);
    }

}
