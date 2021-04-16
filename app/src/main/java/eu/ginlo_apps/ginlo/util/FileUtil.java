// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.param.SendActionContainer;
import eu.ginlo_apps.ginlo.util.StreamUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import static eu.ginlo_apps.ginlo.model.constant.NumberConstants.INT_1024;

public class FileUtil {
    public static final int MIMETYPE_NOT_FOUND = -1;
    private static final String MIME_TYPE_PDF = "application/pdf";
    private static final String MIME_TYPE_MSPOWERPOINT = "application/mspowerpoint";
    private static final String MIME_TYPE_MSPOWERPOINT2 = "application/vnd.ms-powerpoint";
    private static final String MIME_TYPE_MSPOWERPOINT_MACRO = "application/vnd.ms-powerpoint.presentation.macroenabled.12";
    private static final String MIME_TYPE_OOPOWERPOINT = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String MIME_TYPE_APPLE_KEYNOTE = "application/x-iwork-keynote-sffkey";
    private static final String MIME_TYPE_MSWORD = "application/msword";
    private static final String MIME_TYPE_OOWORD = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String MIME_TYPE_APPLE_PAGES = "application/x-iwork-pages-sffpages";
    private static final String MIME_TYPE_ZIP = "application/zip";
    private static final String MIME_TYPE_GZIP = "application/gzipp";
    private static final String MIME_TYPE_7ZIP = "application/x-7z-compressed";
    private static final String MIME_TYPE_RAR = "application/x-rar-compressed";
    private static final String MIME_TYPE_MSEXCEL = "application/msexcel";
    private static final String MIME_TYPE_VND_MSEXCEL = "application/vnd.ms-excel";
    private static final String MIME_TYPE_OOEXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String MIME_TYPE_CSV = "text/csv";
    private static final String MIME_TYPE_CSV2 = "text/comma-separated-values";
    private static final String MIME_TYPE_APPLE_NUMBERS = "application/x-iwork-numbers-sffnumbers";
    private static final String BACKUP_ROOT_DIR = "Backup";
    private static final String TMP_FILES_FOLDER = "tmp_files";
    private static final String META_DIR = "meta_files";
    private static final String CONTENT = "content";
    private static final String FILE = "file";

    private final Context context;

    private final String mediaDir;

    private final File mInternalMediaDir;

    private final File mTmpFilesDir;

    private File mMetaFilesDir;

    public FileUtil(final Context context) {
        this.context = context;
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
    }

    public static int getIconForMimeType(String mimeType) {
        int resID;

        switch (mimeType) {
            case FileUtil.MIME_TYPE_PDF:
                resID = R.drawable.data_pdf;
                break;

            case FileUtil.MIME_TYPE_MSPOWERPOINT:
            case FileUtil.MIME_TYPE_MSPOWERPOINT2:
            case FileUtil.MIME_TYPE_MSPOWERPOINT_MACRO:
            case FileUtil.MIME_TYPE_OOPOWERPOINT:
            case FileUtil.MIME_TYPE_APPLE_KEYNOTE:
                resID = R.drawable.data_praesent;
                break;

            case FileUtil.MIME_TYPE_MSWORD:
            case FileUtil.MIME_TYPE_OOWORD:
            case FileUtil.MIME_TYPE_APPLE_PAGES:
                resID = R.drawable.data_doc;
                break;

            case FileUtil.MIME_TYPE_MSEXCEL:
            case FileUtil.MIME_TYPE_VND_MSEXCEL:
            case FileUtil.MIME_TYPE_OOEXCEL:
            case FileUtil.MIME_TYPE_CSV:
            case FileUtil.MIME_TYPE_CSV2:
            case FileUtil.MIME_TYPE_APPLE_NUMBERS:
                resID = R.drawable.data_xls;
                break;

            case FileUtil.MIME_TYPE_ZIP:
            case FileUtil.MIME_TYPE_GZIP:
            case FileUtil.MIME_TYPE_7ZIP:
            case FileUtil.MIME_TYPE_RAR:
                resID = R.drawable.data_zip;
                break;

            default:
                resID = MIMETYPE_NOT_FOUND;
        }
        return resID;
    }

    public static void saveToFile(File file,
                                  byte[] content) {
        OutputStream outputStream = null;

        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(file));
            outputStream.write(content);
        } catch (IOException e) {
            LogUtil.e(FileUtil.class.getName(), e.getMessage(), e);
        } finally {
            StreamUtil.closeStream(outputStream);
        }
    }

    public static String getMimeTypeFromPath(String path) {
        if (path == null) {
            return null;
        }
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(path.replace(" ", ""));

        if (StringUtil.isNullOrEmpty(extension)) {
            int dotIndex = path.lastIndexOf('.');

            if (dotIndex > -1) {
                extension = path.substring(dotIndex + 1);
            }
        }

        if (extension != null) {
            // apple typen werden nicht erkannt...
            // .key wird als pgp-datei erkannt...
            switch (extension.toLowerCase(Locale.US)) {
                case "keynote":
                case "key":
                    return MIME_TYPE_APPLE_KEYNOTE;
                case "numbers":
                    return MIME_TYPE_APPLE_NUMBERS;
                case "pages":
                    return MIME_TYPE_APPLE_PAGES;
                case "odt":
                    return MIME_TYPE_OOWORD;
                case "ods":
                    return MIME_TYPE_OOEXCEL;
                case "odp":
                    return MIME_TYPE_OOPOWERPOINT;
                default:
                    break;
            }
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            type = mime.getMimeTypeFromExtension(extension.toLowerCase(Locale.US));
        }
        return type;
    }

    public static String checkPath(String path) {
        if (StringUtil.isNullOrEmpty(path) || path.startsWith("file://") || path.startsWith("content://")) {
            return path;
        } else {
            return "file://" + path;
        }
    }

    public File getExternalTempFile()
            throws IOException {
        File f = context.getExternalCacheDir();

        if (f == null) {
            f = context.getCacheDir();
        }
        return File.createTempFile("tmp", null, f);
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

    public File getTempFile()
            throws IOException {
        return File.createTempFile("tmp", null, context.getCacheDir());
    }

    private Uri getFileProviderUriFromFile(File file) {
        return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider", file);
    }

    @NonNull
    public File createTmpFileForExternalUsage()
            throws LocalizedException {
        try {
            File tmpFile;
            if (SystemUtil.hasNougat()) {
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
        if (SystemUtil.hasNougat()) {
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

    @NonNull
    public File createTmpImageFileAddInIntent(@NonNull Intent intent)
            throws LocalizedException {

        final File photoCaptureFile = createTmpFileForExternalUsage();
        final Uri uri = getUriForExternalUsageFromFile(photoCaptureFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);

        return photoCaptureFile;
    }

    public Uri copyFileToInternalDir(Uri from, File internalDirectory)
            throws LocalizedException {
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
            LogUtil.w(this.getClass().getName(), e.getMessage(), e);
            throw new LocalizedException(LocalizedException.FILE_NOT_FOUND, "copyFileToInternalDir", e);
        } finally {
            StreamUtil.closeStream(in);
            StreamUtil.closeStream(out);
        }
    }

    public Uri copyFileToInternalDir(Uri from)
            throws LocalizedException {
        return copyFileToInternalDir(from, null);
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
            LogUtil.w(this.getClass().getSimpleName(), "Delete File fails: " + fileToDelete.getName());
        }
    }

    public void savePhoto(Uri source,
                          String ident) {
        String tag = (ident != null) ? ident : getTimestamp();
        String filename = "img_" + tag + ".jpg";
        Uri dest = save(source, filename);

        if (dest != null) {
            scanMedia(dest);
        }
    }

    public void saveVideo(Uri source,
                          String ident) {
        String tag = (ident != null) ? ident : getTimestamp();
        String filename = "vid_" + tag + ".mp4";
        Uri dest = save(source, filename);

        if (dest != null) {
            scanMedia(dest);
        }
    }

    public void saveVoice(Uri source,
                          String ident) {
        String tag = (ident != null) ? ident : getTimestamp();
        String filename = "voice_" + tag + ".m4a";
        Uri dest = save(source, filename);

        if (dest != null) {
            scanMedia(dest);
        }
    }

    private Uri save(Uri sourceUri,
                     String filename) {
        File source = new File(sourceUri.getPath());
        File dest = new File(mediaDir, filename);

        if (dest.exists()) {
            return null;
        }

        if (copy(source, dest)) {
            return Uri.fromFile(dest);
        } else {
            return null;
        }
    }

    private String getTimestamp() {
        SimpleDateFormat s = new SimpleDateFormat("ddMMyyyyhhmmss");

        return s.format(new Date());
    }

    private boolean copy(File src,
                         File dst) {
        InputStream in = null;
        OutputStream out = null;

        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            StreamUtil.copyStreams(in, out);
            return true;
        } catch (IOException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        } finally {
            StreamUtil.closeStream(in);
            StreamUtil.closeStream(out);
        }
        return false;
    }

    private void scanMedia(Uri uri) {
        Intent scanFileIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);

        context.sendBroadcast(scanFileIntent);
    }

    private String getMimeTypeFromURI(Uri path) {
        ContentResolver cR = context.getContentResolver();
        return cR.getType(path);
    }

    public String getMimeType(Uri contentUri) {
        String type;
        if (contentUri == null) {
            return null;
        } else if (CONTENT.equalsIgnoreCase(contentUri.getScheme())) {
            type = getMimeTypeFromURI(contentUri);
        }
        // File
        else if (FILE.equalsIgnoreCase(contentUri.getScheme())) {
            type = getMimeTypeFromPath(contentUri.getPath());
        } else {
            return null;
        }

        if (type == null) {
            String path = getFileName(contentUri);

            type = getMimeTypeFromPath(path);
        }

        return type;
    }

    public String getExtensionForUri(final Uri uri) {
        String extension = null;
        String mimetype = getMimeType(uri);

        if (!StringUtil.isNullOrEmpty(mimetype)) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();

            extension = mime.getExtensionFromMimeType(mimetype);
        }

        return extension;
    }

    public boolean checkImageUriMimetype(Context context,
                                         Uri contentUri) {
        if ((context == null) || (contentUri == null)) {
            return false;
        }

        String mimeType = getMimeType(contentUri);
        ArrayList<String> allowedMimeTypesPhoto = getAllowedImageMimeTypes();

        return StringUtil.isInList(mimeType, allowedMimeTypesPhoto, true);
    }

    private boolean checkUriMimeType(Context context,
                                     Uri contentUri,
                                     ArrayList<String> allowedMimeTypes) {
        if ((context == null) || (contentUri == null)) {
            return false;
        }

        String mimeType = getMimeType(contentUri);

        return StringUtil.isInList(mimeType, allowedMimeTypes, true);
    }

    public ArrayList<String> getAllowedImageMimeTypes() {
        ArrayList<String> l = new ArrayList<>();

        l.add("image/jpeg");
        l.add("jpeg");
        l.add("image/jpg");
        l.add("jpg");
        l.add("image/png");
        l.add("png");
        l.add("image/bmp");
        l.add("bmp");
        return l;
    }

    private ArrayList<String> getAllowedVideoMimeTypes() {
        ArrayList<String> l = new ArrayList<>();

        l.add("video/mpeg");
        l.add("mpeg");
        l.add("video/mp4");
        l.add("mp4");
        l.add("video/3gpp");
        l.add("3gpp");
        return l;
    }

    public UrisResultContainer getUrisFromVideoActionIntent(Intent intent) {
        UrisResultContainer result = new UrisResultContainer();
        ArrayList<String> uris = new ArrayList<>();

        ArrayList<Uri> intentUris = getUrisFromIntent(intent);

        if ((intentUris != null) && (intentUris.size() > 0)) {
            for (Uri uri : intentUris) {
                if (!checkUriMimeType(context, uri, getAllowedVideoMimeTypes())) {
                    result.mHasImportError = true;
                    continue;
                }

                long fileSize;
                try {
                    fileSize = getFileSize(uri);
                } catch (LocalizedException e) {
                    LogUtil.e(this.getClass().getSimpleName(), e.getMessage(), e);
                    continue;
                }

                if (fileSize > context.getResources().getInteger(R.integer.attachment_video_max_size)) {
                    result.mHasFileTooLargeError = true;
                    continue;
                }

                uris.add(uri.toString());
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
                if (!checkImageUriMimetype(context, uri)) {
                    result.mHasImportError = true;
                    continue;
                }
                imgUris.add(uri.toString());
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
            return -1;
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

    byte[] getByteArrayFromUri(final Uri uri) {
        InputStream in = null;
        ByteArrayOutputStream bos = null;

        try {
            in = context.getContentResolver().openInputStream(uri);
            byte[] bytes;

            bos = new ByteArrayOutputStream();

            byte[] data = new byte[INT_1024];
            int count;

            while ((count = in.read(data)) != -1) {
                bos.write(data, 0, count);
            }

            bos.flush();

            bytes = bos.toByteArray();

            return bytes;
        } catch (IOException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        } finally {
            StreamUtil.closeStream(in);
            StreamUtil.closeStream(bos);
        }
        return null;
    }

    public boolean deleteAllFilesInDir(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }

        File[] files = dir.listFiles();

        if (files == null) {
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

    @NonNull
    public File getBackupDirectory()
            throws LocalizedException {
        File buDir = new File(context.getFilesDir(), BACKUP_ROOT_DIR);

        if (!buDir.isDirectory()) {
            if (!buDir.mkdirs()) {
                throw new LocalizedException(LocalizedException.BACKUP_FOLDER_FAILED, "backup root directory: mkdir() failed");
            }
        }

        return buDir;
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
        if (intent == null) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Intent is null.");
        }
        SendActionContainer actionContainer = new SendActionContainer();
        String action = intent.getAction();
        String type = intent.getType();

        if (StringUtil.isNullOrEmpty(action)) {
            throw new LocalizedException(LocalizedException.NO_ACTION_SEND);
        }

        if (StringUtil.isNullOrEmpty(type)) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "No MIME type.");
        }

        if (Intent.ACTION_SEND.equals(action)) {
            actionContainer.action = SendActionContainer.ACTION_SEND;

            if (type.startsWith("text/")) {
                checkSendText(intent, actionContainer);
            } else if (type.startsWith("image/")) {
                checkSendImages(intent, actionContainer);
            } else if (type.startsWith("video/")) {
                checkVideo(intent, actionContainer);
            } else {
                checkSendFile(intent, actionContainer);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            if (type.startsWith("image/")) {
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
                    if (!checkImageUriMimetype(context, imageUri)) {
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

    private void checkVideo(Intent intent, SendActionContainer actionContainer)
            throws LocalizedException {
        ArrayList<Uri> uris = getUrisFromIntent(intent);
        Uri videoUri = null;

        if (uris != null && uris.size() > 0) {
            videoUri = uris.get(0);
        }

        if (videoUri != null) {
            //pruefen ob es ein MIME type ist, denn wir als Video nicht unterstuetzen
            if (!checkUriMimeType(context, videoUri, getAllowedVideoMimeTypes())) {
                //dann als normales File verwenden
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
                LogUtil.e(this.getClass().getSimpleName(), e.getMessage(), e);
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
            try {
                returnValue = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            } catch (Exception e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            }
        }

        if (returnValue == null || returnValue.size() < 1) {
            Uri uri = null;

            try {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            } catch (Exception e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            }

            if (uri == null) {
                uri = intent.getData();
            }

            if (uri != null) {
                returnValue = new ArrayList<>(1);
                returnValue.add(uri);
            }
        }

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
            LogUtil.w(FileUtil.class.getSimpleName(), "getNameForUri", e);
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
            LogUtil.w(FileUtil.class.getSimpleName(), "getSizeForUri", e);
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
            LogUtil.w(this.getClass().getSimpleName(), "saveObjectToFile()", e);
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
