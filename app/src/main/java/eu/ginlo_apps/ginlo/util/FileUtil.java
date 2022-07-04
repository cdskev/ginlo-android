// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.param.SendActionContainer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

public class FileUtil {
    private final static String TAG = FileUtil.class.getSimpleName();
    private static final String TMP_FILES_FOLDER = "tmp_files";
    private static final String META_DIR = "meta_files";
    private static final String CONTENT = "content";
    private static final String FILE = "file";

    private final Context context;
    private final String mediaDir;
    private final File mInternalMediaDir;
    private final File mTmpFilesDir;
    private final MimeUtil mMimeUtil;

    //public static long MAX_RAM_PROCESSING_SIZE = 262144; // 256 KBytes
    public static long MAX_RAM_PROCESSING_SIZE = 5242880; // 5 MBytes

    private File mMetaFilesDir;

    public FileUtil(final Context context) {
        this.context = context;

        // KS: TODO: Refactor to work with SDK 30 and above!
        this.mediaDir = new File(
                new File(Environment.getExternalStorageDirectory(), "ginlo"), "ginlo Media").getAbsolutePath();

        final File media = new File(mediaDir);

        if (!media.exists()) {
            media.mkdirs();
        }

        mInternalMediaDir = new File(context.getFilesDir(), "internalMedia");
        if (!mInternalMediaDir.isDirectory()) {
            mInternalMediaDir.mkdirs();
        }

        mTmpFilesDir = new File(context.getFilesDir(), TMP_FILES_FOLDER);

        if (!mTmpFilesDir.isDirectory()) {
            mTmpFilesDir.mkdirs();
        }

        mMimeUtil = new MimeUtil(context);
    }

    /**
     * Simple helper I to delete a (mostly temp) file.
     * @param fileToDelete
     */
    public static void deleteFile(final File fileToDelete) {
        if(fileToDelete != null) {
            if(!fileToDelete.delete()) {
                LogUtil.w(TAG, "Failed to delete " + fileToDelete.getPath() + "!");
            }
        }
    }

    /**
     * Simple helper II to delete a (mostly temp) file.
     * @param filepathToDelete
     */
    public static void deleteFile(final String filepathToDelete) {

        if(StringUtil.isNullOrEmpty(filepathToDelete)) {
            return;
        }
        deleteFile(new File(filepathToDelete));
    }

    public boolean deleteAllFilesInDir(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }

        File[] files = dir.listFiles();

        if (files == null || files.length < 1) {
            return true;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                if (!this.deleteAllFilesInDir(file)) {
                    return false;
                }
            }

            if (!file.delete()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gzip a file's contents and write to same directory as "file.gzip".
     * Silently overwrite existing "file.gzip".
     * @param fileToGzip Name of file to gzip
     * @return File gzipFile
     */
    public static File gzipFile(@NonNull final File fileToGzip) {
        File gzipFile = new File(fileToGzip.getPath() + ".gzip");
        if(gzipFile.exists()) {
            gzipFile.delete();
        }

        try {
            FileInputStream fis = new FileInputStream(fileToGzip);
            FileOutputStream fos = new FileOutputStream(gzipFile);
            GZIPOutputStream gzip = new GZIPOutputStream(fos);

            int read = 0;
            byte[] data = new byte[StreamUtil.STREAM_BUFFER_SIZE];
            while ((read = fis.read(data, 0, StreamUtil.STREAM_BUFFER_SIZE)) != -1) {
                gzip.write(data, 0, read);
            }
            gzip.close();
            fos.close();
            fis.close();
        } catch (IOException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            return null;
        }

        return gzipFile;
    }

    public static void saveToFile(File file, byte[] content) {
        OutputStream outputStream = null;

        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(file));
            outputStream.write(content);
        } catch (IOException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        } finally {
            StreamUtil.closeStream(outputStream);
        }
    }

    public static String checkPath(String path) {
        if (StringUtil.isNullOrEmpty(path) || path.startsWith("file://") || path.startsWith("content://")) {
            return path;
        } else {
            return "file://" + path;
        }
    }

    /**
     * Check whether a file starts with given "magic bytes"
     * @param fileToCheck
     * @param magicBytes
     * @return
     */
    public static Boolean haveFileMagic(File fileToCheck, byte[] magicBytes) {
        final int magicByteCount = magicBytes.length;
        if (fileToCheck == null || magicByteCount == 0) {
            return false;
        }

        LogUtil.d(TAG, "haveFileMagic: Check file " + fileToCheck.getPath() + " for " + Arrays.toString(magicBytes));

        final int fileSize = new BigDecimal(fileToCheck.length()).intValueExact();
        if (fileSize < magicByteCount) {
            LogUtil.d(TAG, "haveFileMagic: File has size " + fileSize);
            return false;
        }

        byte[] firstBytes = new byte[magicByteCount];
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(fileToCheck);
            StreamUtil.safeRead(fileInputStream, firstBytes, magicByteCount);
        } catch (IOException e) {
            LogUtil.e(TAG, "haveFileMagic: Caught " + e.getMessage());
            return false;
        } finally {
            StreamUtil.closeStream(fileInputStream);
        }
        LogUtil.d(TAG, "haveFileMagic: Checked file starts with " + Arrays.toString(firstBytes));
        return Arrays.equals(magicBytes, firstBytes);
    }

    public File getInternalMediaFile(final String fileName) {
        if (mInternalMediaDir == null) {
            return null;
        } else {
            return new File(mInternalMediaDir, fileName);
        }
    }

    public File getInternalMediaDir() {
        return mInternalMediaDir;
    }

    private Uri getFileProviderUriFromFile(File file) {
        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", file);
    }

    @NonNull
    public File createTmpFileForExternalUsage()
            throws LocalizedException {
        try {
            File tmpFile;
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)) {
                tmpFile = createTmpFile(mTmpFilesDir);
            } else {
                File cacheDir = context.getExternalCacheDir();
                if (cacheDir == null) {
                    throw new LocalizedException(LocalizedException.CREATE_FILE_FAILED, "External Cache Directory not available");
                }

                tmpFile = createTmpFile(cacheDir);
            }
            return tmpFile;
        } catch (IOException e) {
            throw new LocalizedException(LocalizedException.CREATE_FILE_FAILED, "Create Temp File failed.", e);
        }
    }

    @NonNull
    public Uri getUriForExternalUsageFromFile(@NonNull File file) {
        final Uri uri;
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)) {
            uri = getFileProviderUriFromFile(file);
        } else {
            uri = Uri.fromFile(file);
        }

        return uri;
    }

    private File createTmpFile(@NonNull File tmpDirectory)
            throws IOException {
        final String fileName;
        final String fileSuffix;

        fileName = "tmp";
        fileSuffix = null;

        return File.createTempFile(fileName, fileSuffix, tmpDirectory);
    }

    public File getTempFile()
            throws IOException {
        return File.createTempFile("tmp", null, context.getCacheDir());
    }

    @NonNull
    public File createTmpImageFileAddInIntent(@NonNull Intent intent)
            throws LocalizedException {

        final File photoCaptureFile = createTmpFileForExternalUsage();
        final Uri uri = getUriForExternalUsageFromFile(photoCaptureFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);

        return photoCaptureFile;
    }

    public Uri copyFileToInternalDir(Uri from)
            throws LocalizedException {
        return copyFileToInternalDir(from, null);
    }

    public Uri copyFileToInternalDir(Uri from, File internalDirectory)
            throws LocalizedException {
        LogUtil.d(TAG, "copyFileToInternalDir: " + from + " -> " + (internalDirectory != null ? internalDirectory.getPath() : "create new TempFile"));
        if (from == null) {
            return null;
        }

        if (isInternalUri(from)) {
            return from;
        }

        InputStream in = null;
        OutputStream out = null;
        File dest;

        try {
            if (StringUtil.isNullOrEmpty(from.getScheme())) {
                File file = new File(from.getPath());
                from = Uri.fromFile(file);
            }
            in = context.getContentResolver().openInputStream(from);
            if (in == null) {
                throw new LocalizedException(LocalizedException.FILE_NOT_FOUND, "copyFileToInternalDir - InputStream is null");
            }
            if (internalDirectory != null && internalDirectory.isDirectory()) {
                dest = new File(internalDirectory, "" + UUID.randomUUID() + ".tmp");
            } else {
                dest = getTempFile();
            }
            out = new FileOutputStream(dest);
            StreamUtil.copyStreams(in, out);
            return Uri.fromFile(dest);
        } catch (Exception e) {
            LogUtil.w(TAG, e.getMessage(), e);
            throw new LocalizedException(LocalizedException.FILE_NOT_FOUND, "copyFileToInternalDir", e);
        } finally {
            StreamUtil.closeStream(in);
            StreamUtil.closeStream(out);
        }
    }

    private boolean isInternalUri(@NonNull final Uri uri) {
        String path = uri.getPath();

        return !StringUtil.isNullOrEmpty(path) && path.contains(BuildConfig.APPLICATION_ID);
    }

    public void deleteFileByUriAndRevokePermission(Uri uri) {
        if (uri == null) {
            return;
        }

        final File fileToDelete;

        if (StringUtil.isEqual(CONTENT, uri.getScheme())) {
            context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            fileToDelete = new File(context.getFilesDir(), uri.getPath());
        } else {
            fileToDelete = new File(uri.getPath());
        }

        if (!fileToDelete.delete()) {
            LogUtil.w(TAG, "Delete File fails: " + fileToDelete.getName());
        }
    }

    public UrisResultContainer getUrisFromVideoActionIntent(Intent intent) {
        UrisResultContainer result = new UrisResultContainer();
        ArrayList<String> uris = new ArrayList<>();

        ArrayList<Uri> intentUris = getUrisFromIntent(intent);

        if ((intentUris != null) && (intentUris.size() > 0)) {
            for (Uri uri : intentUris) {
                //if (!mMimeUtil.checkUriMimeType(context, uri, mMimeUtil.getAllowedVideoMimeTypes())) {
                // KS: Test
                if (!mMimeUtil.checkVideoUriMimetype(context, uri)) {
                    result.mHasImportError = true;
                    continue;
                }

                long fileSize;
                try {
                    fileSize = getFileSize(uri);
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                    continue;
                }

                if (fileSize > context.getResources().getInteger(R.integer.attachment_video_max_size)) {
                    result.mHasFileTooLargeError = true;
                    continue;
                }

                uris.add(uri.toString());
                LogUtil.d(TAG, "getUrisFromVideoActionIntent: Add " + uri.toString());
            }
        }

        result.mUris = uris;

        return result;
    }

    public UrisResultContainer getUrisFromImageActionIntent(final Intent intent) {
        UrisResultContainer result = new UrisResultContainer();
        ArrayList<String> imgUris = new ArrayList<>();

        ArrayList<Uri> intentUris = getUrisFromIntent(intent);

        if ((intentUris != null) && (intentUris.size() > 0)) {
            for (Uri uri : intentUris) {
                if (!mMimeUtil.checkImageUriMimetype(context, uri)) {
                    result.mHasImportError = true;
                    continue;
                }
                imgUris.add(uri.toString());
                LogUtil.d(TAG, "getUrisFromImageActionIntent: Add " + uri.toString());
            }
        }

        result.mUris = imgUris;

        return result;
    }

    public String getFileName(final Uri uri) {
        if (uri == null) {
            return null;
        } else if (CONTENT.equalsIgnoreCase(uri.getScheme())) {
            return getNameForUri(uri);
        } else if (FILE.equalsIgnoreCase(uri.getScheme())) {
            return uri.getLastPathSegment();
        }
        return null;
    }

    public long getFileSize(final Uri uri)
            throws LocalizedException {
        long fileSize = 0;

        if (uri == null) {
            throw new LocalizedException(LocalizedException.OBJECT_NULL);
        } else if (CONTENT.equalsIgnoreCase(uri.getScheme())) {
            fileSize = getSizeForUri(uri);
        } else if (FILE.equalsIgnoreCase(uri.getScheme())) {
            File file = new File(uri.getPath());

            if (file.exists()) {
                fileSize = file.length();
            }
        }

        return fileSize;
    }

    /**
     * Prueft einen Intent nach ACTION_SEND.
     * Prueft ob er alle benoetigten Attribute beinhaltet.
     * Prueft ob die Datei ein unterstützten Mimetype hat.
     * Prueft die Größe der Datei.
     *
     * @param intent intent
     * @return SendActionContainer
     * @throws LocalizedException - Es wird eine Exception mit dem Identifier LocalizedException#NO_DATA_FOUND,
     *                            wenn kein Intent übergeben wurder oder der MIME Type nicht gesetzt wurde.
     *                            Wenn der Intent nicht der Erwartete ist wird Identifier LocalizedException#NO_ACTION_SEND gesetzt.
     *                            Wenn es bei der Überprüfung zu einen Fehler kommt, wird der Identifier
     *                            LocalizedException#CHECK_ACTION_SEND_INTENT_FAILED gesetzt.
     *                            Im letzten Fall wird ein Fehlertext als Detailmessage gesetzt.
     */
    public SendActionContainer checkFileSendActionIntent(Intent intent)
            throws LocalizedException {
        return checkFileSendActionIntent(intent, false, false);
    }

    /**
     * New overloaded version of checkFileSendActionIntent that allows a file handling override
     * independent of the given mime type.
     * @param intent
     * @param rawFileHandling
     * @return
     * @throws LocalizedException
     */
    public SendActionContainer checkFileSendActionIntent(Intent intent, boolean rawFileHandling, boolean processRichContents)
            throws LocalizedException {
        LogUtil.d(TAG, "checkFileSendActionIntent: intent = " + intent +
                ", rawFileHandling = " + rawFileHandling +
                ", processRichContents = " + processRichContents);

        if (intent == null) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Intent is null.");
        }
        SendActionContainer actionContainer = new SendActionContainer();

        String action = intent.getAction();
        if (StringUtil.isNullOrEmpty(action)) {
            throw new LocalizedException(LocalizedException.NO_ACTION_SEND);
        }

        String type = intent.getType();
        if (StringUtil.isNullOrEmpty(type)) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "No MIME type.");
        }

        if (Intent.ACTION_SEND.equals(action)) {
            actionContainer.action = SendActionContainer.ACTION_SEND;

            if (rawFileHandling) {
                // Process rich contents is prioritized
                if (processRichContents) {
                    if (!processRichContent(intent, actionContainer)) {
                        checkSendFile(intent, actionContainer);
                    } else {
                        // Always process as files
                        checkSendFile(intent, actionContainer);
                    }
                }
            } else {
                // Look what we have for individual handling
                if (type.startsWith("text/")) {
                    checkSendText(intent, actionContainer);
                } else if (processRichContents) {
                    // Fast processing of rich contents instead of individual image handling
                    if (!processRichContent(intent, actionContainer)) {
                        if (MimeUtil.isImageMimetype(type, false)) {
                            checkSendImages(intent, actionContainer);
                        } else if (MimeUtil.isVideoMimetype(type, false)) {
                            checkSendVideo(intent, actionContainer);
                        } else {
                            checkSendFile(intent, actionContainer);
                        }
                    }

                } else  {
                    // Traditional image/video handling
                    if (MimeUtil.isImageMimetype(type, true)) {
                        checkSendImages(intent, actionContainer);
                    } else if (MimeUtil.isVideoMimetype(type, true)) {
                        checkSendVideo(intent, actionContainer);
                    } else {
                        checkSendFile(intent, actionContainer);
                    }
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            if (MimeUtil.isImageMimetype(type, true)) {
                actionContainer.action = SendActionContainer.ACTION_SEND;
                checkSendImages(intent, actionContainer);
            }
        } else {
            throw new LocalizedException(LocalizedException.NO_ACTION_SEND);
        }
        return actionContainer;
    }

    private void checkSendText(Intent intent, SendActionContainer actionContainer)
            throws LocalizedException {
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);

        if (StringUtil.isNullOrEmpty(text)) {
            Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (fileUri != null) {
                checkSendFile(intent, actionContainer);
                return;
            } else {
                String errorMsg = context.getString(R.string.chats_forward_send_action_no_text);
                throw new LocalizedException(LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED, errorMsg);
            }
        }

        actionContainer.type = SendActionContainer.TYPE_TXT;
        actionContainer.text = text;
    }

    private void checkSendImages(Intent intent,
                                 SendActionContainer actionContainer)
            throws LocalizedException {
        boolean hasUris = false;

        ArrayList<Uri> uris = getUrisFromIntent(intent);

        if (uris != null && uris.size() > 0) {
            if (uris.size() > 1) {
                hasUris = true;
            } else {
                Uri imageUri = uris.get(0);
                if (imageUri != null) {
                    //pruefen ob es ein MIME type ist, denn wir als Bild nicht unterstuetzen
                    if (!MimeUtil.checkImageUriMimetype(context, imageUri)) {
                        //dann als normales File verwenden
                        checkSendFile(intent, actionContainer);
                        return;
                    }
                    hasUris = true;
                }
            }
        }

        if (hasUris) {
            UrisResultContainer container = getUrisFromImageActionIntent(intent);

            if ((container.mUris == null) || (container.mUris.size() < 1)) {
                String errorMsg;

                if (container.mHasImportError) {
                    errorMsg = context.getString(R.string.chats_addAttachment_wrong_format_or_error);
                } else if (container.mHasFileTooLargeError) {
                    errorMsg = context.getString(R.string.chats_addAttachment_too_big);
                } else {
                    errorMsg = context.getString(R.string.chats_addAttachments_some_imports_fails);
                }

                throw new LocalizedException(LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED, errorMsg);
            } else if (container.mHasImportError || container.mHasFileTooLargeError) {
                actionContainer.displayMessage = context.getString(R.string.chats_addAttachments_some_imports_fails);
            }

            actionContainer.type = SendActionContainer.TYPE_IMAGE;
            actionContainer.uris = uris;
        } else {
            String errorMsg = context.getString(R.string.chats_addAttachments_some_imports_fails);
            throw new LocalizedException(LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED, errorMsg);
        }
    }

    private void checkSendVideo(Intent intent, SendActionContainer actionContainer)
            throws LocalizedException {
        ArrayList<Uri> uris = getUrisFromIntent(intent);
        Uri videoUri = null;

        if (uris != null && uris.size() > 0) {
            videoUri = uris.get(0);
        }

        if (videoUri != null) {
            /*
            //pruefen ob es ein MIME type ist, denn wir als Video nicht unterstuetzen
            if (!mMimeUtil.checkUriMimeType(context, videoUri, mMimeUtil.getAllowedVideoMimeTypes())) {
                //dann als normales File verwenden

             */
                if (!MimeUtil.checkVideoUriMimetype(context, videoUri)) {
                checkSendFile(intent, actionContainer);
                return;
            }

            UrisResultContainer container = getUrisFromVideoActionIntent(intent);

            if (container.mHasImportError) {
                String errorMsg = context.getString(R.string.chats_addAttachment_wrong_format_or_error);
                throw new LocalizedException(LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED, errorMsg);
            } else if (container.mHasFileTooLargeError) {
                String errorMsg = context.getString(R.string.chats_addAttachment_too_big);
                throw new LocalizedException(LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED, errorMsg);
            } else if ((container.mUris == null) || (container.mUris.size() < 1)) {
                String errorMsg = context.getString(R.string.chats_addAttachments_some_imports_fails);
                throw new LocalizedException(LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED, errorMsg);
            }

            actionContainer.type = SendActionContainer.TYPE_VIDEO;
            actionContainer.uris = uris;
        } else {
            String errorMsg = context.getString(R.string.chats_addAttachments_some_imports_fails);
            throw new LocalizedException(LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED, errorMsg);
        }
    }

    /**
     * These are media types (image/video) like mostly used with animated gifs and stickers
     * They are for fast processing, so we don't start a preview and have only one uri.
     * @param intent
     * @param actionContainer
     * @throws LocalizedException
     */
    private boolean processRichContent(Intent intent,
                                       SendActionContainer actionContainer)
            throws LocalizedException {
        ArrayList<Uri> uris = getUrisFromIntent(intent);
        Uri fileUri = null;

        if (uris != null && uris.size() > 0) {
            fileUri = uris.get(0);
        }

        if (fileUri != null) {
            if (!MimeUtil.checkRichContentUriMimetype(context, fileUri) &&
                !MimeUtil.isLottieFile(null, new File(fileUri.getEncodedPath()))) {
                return false;
            }

            long fileSize;
            try {
                fileSize = getFileSize(fileUri);
            } catch (LocalizedException e) {
                fileSize = 0;
                LogUtil.e(TAG, e.getMessage(), e);
            }

            if (fileSize > context.getResources().getInteger(R.integer.attachment_file_max_size)) {
                String errorMsg = context.getString(R.string.chats_addAttachment_too_big);
                throw new LocalizedException(LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED, errorMsg);
            }

        } else {
            String errorMsg = context.getString(R.string.chats_addAttachments_some_imports_fails);
            throw new LocalizedException(LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED, errorMsg);
        }

        actionContainer.type = SendActionContainer.TYPE_RICH_CONTENT;
        actionContainer.uris = uris;
        return true;
    }

    private void checkSendFile(Intent intent, SendActionContainer actionContainer)
            throws LocalizedException {
        ArrayList<Uri> uris = getUrisFromIntent(intent);
        Uri fileUri = null;

        if (uris != null && uris.size() > 0) {
            fileUri = uris.get(0);
        }

        if (fileUri != null) {
            long fileSize;
            try {
                fileSize = getFileSize(fileUri);
            } catch (LocalizedException e) {
                fileSize = 0;
                LogUtil.e(TAG, e.getMessage(), e);
            }

            if (fileSize > context.getResources().getInteger(R.integer.attachment_file_max_size)) {
                String errorMsg = context.getString(R.string.chats_addAttachment_too_big);
                throw new LocalizedException(LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED, errorMsg);
            }
        } else {
            String errorMsg = context.getString(R.string.chats_addAttachments_some_imports_fails);
            throw new LocalizedException(LocalizedException.CHECK_ACTION_SEND_INTENT_FAILED, errorMsg);
        }

        actionContainer.type = SendActionContainer.TYPE_FILE;
        actionContainer.uris = uris;
    }

    private ArrayList<Uri> getUrisFromIntent(Intent intent) {
        
        ArrayList<Uri> returnValue = null;

        LogUtil.d(TAG, "getUrisFromIntent: Now try to find out what we have. Try ClipData first ...");

        ClipData clipData = intent.getClipData();
        if ((clipData != null) && (clipData.getItemCount() > 0)) {
            returnValue = new ArrayList<>();
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) {
                    returnValue.add(uri);
                }
            }
        }

        if (returnValue == null || returnValue.size() < 1) {
            LogUtil.d(TAG, "getUrisFromIntent: No ClipData, try getParcelableExtra ...");

            Uri uri = null;

            try {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            } catch (Exception e) {
                LogUtil.w(TAG, "getUrisFromIntent: Got exception in getParcelableExtra " + e.getMessage());
            }

            if (uri == null) {
                LogUtil.d(TAG, "getUrisFromIntent: No getParcelableExtra, try intent.getData ...");
                uri = intent.getData();
            }

            if (uri != null) {
                returnValue = new ArrayList<>(1);
                returnValue.add(uri);
            }
        }

        if (returnValue == null || returnValue.size() < 1) {
            LogUtil.d(TAG, "getUrisFromIntent: No intent.getData, try getParcelableArrayListExtra ...");

            try {
                returnValue = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            } catch (Exception e) {
                LogUtil.w(TAG, "getUrisFromIntent: Got exception in getParcelableArrayListExtra " + e.getMessage());
            }
        }

        LogUtil.d(TAG, "getUrisFromIntent: Finally got " + returnValue);
        return returnValue;
    }

    // Uri Utils
    private String getNameForUri(final Uri uri) {
        String name = null;
        try {
            String[] proj = {
                    OpenableColumns.DISPLAY_NAME
            };

            Cursor returnCursor = context.getContentResolver().query(uri, proj, null, null, null);

            if (returnCursor != null) {
                try {
                    if (returnCursor.moveToFirst()) {
                        name = returnCursor.getString(0);
                    }
                } finally {
                    returnCursor.close();
                }
            }
        } catch (IllegalArgumentException e) {
            LogUtil.w(TAG, "getNameForUri", e);
        }

        return name;
    }

    private long getSizeForUri(final Uri uri)
            throws LocalizedException {
        Long size = null;

        if (context == null || context.getContentResolver() == null || uri == null) {
            return 0;
        }

        try {
            String[] proj = {OpenableColumns.SIZE};

            Cursor returnCursor = context.getContentResolver().query(uri, proj, null, null, null);

            if (returnCursor != null) {
                try {
                    if (returnCursor.moveToFirst()) {
                        size = returnCursor.getLong(0);
                    }
                } finally {
                    returnCursor.close();
                }
            }
        } catch (IllegalArgumentException e) {
            LogUtil.w(TAG, "getSizeForUri", e);
        } catch (NullPointerException | SecurityException e) {
            // die Google-Photo-App liefert hier eine kaputte URL. Es kommt intern zu einem Nullpointer
            // andere apps tuerzen hier auch ab (wa, gmail)
            throw new LocalizedException(LocalizedException.FILE_NOT_FOUND, e);
        }

        return (size != null) ? size : 0;
    }

    private File getMetaFilesDir() {
        if (mMetaFilesDir != null) {
            return mMetaFilesDir;
        }

        mMetaFilesDir = new File(context.getFilesDir(), META_DIR);

        if (!mMetaFilesDir.isDirectory()) {
            mMetaFilesDir.mkdirs();
        }

        return mMetaFilesDir;
    }

    public Object loadObjectFromFile(@NonNull final String filename) {
        File dir = getMetaFilesDir();
        File mapFile = new File(dir, filename);

        if (!mapFile.exists()) {
            return null;
        }

        try (FileInputStream fi = new FileInputStream(mapFile);
             ObjectInputStream ois = new ObjectInputStream(fi)) {
            return ois.readObject();
        } catch (Exception e) {
            mapFile.delete();
            return null;
        }
    }

    public boolean saveObjectToFile(@NonNull final Object objectToSave, @NonNull final String filename) {
        File dir = getMetaFilesDir();
        File mapFile = new File(dir, filename);

        if (mapFile.exists()) {
            mapFile.delete();
        }

        try (FileOutputStream fos = new FileOutputStream(mapFile);
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(fos)) {
            mapFile.createNewFile();
            objectOutputStream.writeObject(objectToSave);

            return true;
        } catch (Exception e) {
            LogUtil.w(TAG, "saveObjectToFile()", e);
            return false;
        }
    }

    public class UrisResultContainer {
        ArrayList<String> mUris;

        boolean mHasImportError;

        boolean mHasFileTooLargeError;

        public ArrayList<String> getUris() {
            return mUris;
        }

        public boolean getHasImportError() {
            return mHasImportError;
        }

        public boolean getHasFileTooLargeError() {
            return mHasFileTooLargeError;
        }
    }
}
