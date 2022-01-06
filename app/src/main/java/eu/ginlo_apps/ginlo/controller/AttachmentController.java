// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller;

import android.content.res.Resources;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import android.util.Base64;
import android.util.Base64InputStream;
import android.util.Base64OutputStream;
import android.webkit.MimeTypeMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonStreamParser;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.concurrent.task.HttpBaseTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.BackendError;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.*;
import eu.ginlo_apps.ginlo.view.ProgressDownloadDialog;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class AttachmentController {
    private static final String TAG = AttachmentController.class.getSimpleName();
    private static final String SHARE_FOLDER = "share";
    private static File attachmentsDir;
    private final File cacheDir;
    private final File shareDir;
    private final SimsMeApplication mApplication;

    /**
     *
     * @param application app
     */
    public AttachmentController(final SimsMeApplication application) {
        mApplication = application;
        attachmentsDir = new File(mApplication.getFilesDir().getAbsolutePath() + "/attachments");
        cacheDir = application.getCacheDir();

        if (!attachmentsDir.isDirectory()) {
            attachmentsDir.mkdirs();
        }

        shareDir = new File(mApplication.getFilesDir(), SHARE_FOLDER);

        if (!shareDir.isDirectory()) {
            shareDir.mkdirs();
        } else {
            deleteShareFiles();
        }

        clearCache();
    }

    /**
     * convertJsonArrayFileToEncryptedAttachmentBase64File
     *
     * Convert the contents of an existing JsonArray file
     * to a Base64 encoded encrypted attachment and save this as a new file.
     *
     * @param jsonFilename Name of file containing jsonArray with attachment data
     * @param attachmentGuid attachmentGuid
     * @return The pathname of the Base64 encoded encrypted attachment file
     */
    public static String convertJsonArrayFileToEncryptedAttachmentBase64File(@NonNull final String jsonFilename, @NonNull final String attachmentGuid) {
        if (attachmentsDir == null) {
            return null;
        }

        final File jsonFile = new File(jsonFilename);
        final File encryptedBase64File = new File(attachmentsDir, attachmentGuid + "-Base64");

        try {
            int read = 0;
            boolean firstRun = true;
            byte[] data = new byte[StreamUtil.STREAM_BUFFER_SIZE];
            FileWriter fw = new FileWriter(encryptedBase64File);
            FileInputStream fi = new FileInputStream(jsonFile);

            while ((read = fi.read(data, 0, StreamUtil.STREAM_BUFFER_SIZE)) > 0) {
                String d = new String(data, StandardCharsets.US_ASCII).substring(0, read);
                fw.append(d.replaceAll("\\[\"|\"\\]",""));
            }
            fw.close();
            fi.close();

        } catch (IOException e) {
            LogUtil.e(TAG, "convertJsonArrayFileToEncryptedAttachmentBase64File: Caught " + e.getMessage());
            return null;
        }

        return encryptedBase64File.getPath();
    }

    /**
     * convertEncryptedAttachmentBase64FileToJsonArrayFile
     *
     * Convert the contents of an existing Base64 encoded encrypted attachment file
     * to a JsonArray and save this as a new file.
     * @param attachmentGuid The Guid of the attachment
     * @return The pathname of the JsonArray file
     */
    public static String convertEncryptedAttachmentBase64FileToJsonArrayFile(@NonNull String attachmentGuid) {
        if (attachmentsDir == null) {
            return null;
        }

        final File encryptedBase64File = new File(attachmentsDir, attachmentGuid + "-Base64");
        final File jsonFile = new File(attachmentsDir, attachmentGuid + "-json");

        try {
            int read = 0;
            byte[] data = new byte[StreamUtil.STREAM_BUFFER_SIZE];
            FileWriter fw = new FileWriter(jsonFile);
            FileInputStream fi = new FileInputStream(encryptedBase64File);

            fw.append("[\"");
            while ((read = fi.read(data, 0, StreamUtil.STREAM_BUFFER_SIZE)) > 0) {
                String d = new String(data, StandardCharsets.US_ASCII).substring(0, read);
                fw.append(d.replaceAll("[\\n ]+",""));
            }
            fw.append("\"]");

            fw.close();
            fi.close();

        } catch (IOException e) {
            LogUtil.e(TAG, "convertEncryptedAttachmentBase64FileToJsonArrayFile: Caught " + e.getMessage());
            return null;
        }

        return jsonFile.getPath();
    }

    /**
     * Create a JsonArray-compatible object out of the Base64 encoded encrypted attachment file
     * Warning: This must be used with care (OOM) if big attachments are to be processed.
     * @param attachmentGuid The Guid of the attachment
     * @return The JsonElement object with the attachment data
     */
    public static JsonElement loadEncryptedBase64AttachmentAsJsonElementFromFile(@NonNull String attachmentGuid) {
        if (attachmentsDir == null) {
            return null;
        }
        final String jsonFilename = convertEncryptedAttachmentBase64FileToJsonArrayFile(attachmentGuid);
        if(jsonFilename == null) {
            return null;
        }

        final File jsonFile = new File(jsonFilename);
        JsonElement je = null;

        try {
            JsonStreamParser jsp = new JsonStreamParser(new FileReader(jsonFile));
            // Only 1 element - the attachment
            if(jsp.hasNext()) {
                je = jsp.next();
            }

        } catch (IOException e) {
            LogUtil.e(TAG, "loadEncryptedBase64AttachmentAsJsonElementFromFile: Caught " + e.getMessage());
            return null;
        } finally {
            FileUtil.deleteFile(jsonFile);
        }

        return je;
    }

    public static byte[] loadEncryptedBase64AttachmentFile(@NonNull String attachmentGuid)
            throws LocalizedException {
        if (attachmentsDir == null) {
            return null;
        }

        String fileName = attachmentGuid + "-Base64";

        return loadEncryptedDataFromAttachment(fileName);
    }

    public static File getEncryptedBase64AttachmentFile(@NonNull String attachmentGuid) {
        if (attachmentsDir == null) {
            return null;
        }

        String fileName = attachmentGuid + "-Base64";

        return getAttachmentFile(fileName);
    }

    /**
     * saveEncryptedMessageAttachment
     *
     * @param encryptedData  NICHT Base64 codiert, nur encrypted
     * @param attachmentGuid attachmentGuid
     */
    public static void saveEncryptedMessageAttachment(@NonNull final byte[] encryptedData, @NonNull final String attachmentGuid) {
        if (attachmentsDir == null) {
            return;
        }

        final File encryptedFile = new File(attachmentsDir, attachmentGuid);

        if (!encryptedFile.exists()) {
            FileUtil.saveToFile(encryptedFile, encryptedData);
        }
    }

    private static byte[] loadEncryptedDataFromAttachment(String attachmentGuid) throws LocalizedException {
        byte[] returnValue = null;

        if (attachmentsDir == null) {
            return null;
        }

        final File encryptedFile = new File(attachmentsDir, attachmentGuid);

        if (encryptedFile.exists()) {
            returnValue = loadFile(encryptedFile);
        }

        return returnValue;
    }

    public static File getAttachmentFile(@NonNull String attachmentGuid) {
        if (attachmentsDir == null) {
            return null;
        }

        return new File(attachmentsDir, attachmentGuid);
    }

    public static void saveEncryptedAttachmentFileAsBase64File(final String attachmentGuid, final String base64OutputPath)
            throws LocalizedException {
        if (attachmentsDir == null) {
            LogUtil.e(TAG, "saveEncryptedAttachmentFileAsBase64File: Attachment Directory not given!");
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Attachment Directory not loaded!");
        }
        File encryptedFile = new File(attachmentsDir, attachmentGuid);
        if (!encryptedFile.exists()) {
            LogUtil.w(TAG, "saveEncryptedAttachmentFileAsBase64File: Could not find " + encryptedFile.getPath());
            return;
        }

        try {
            final FileInputStream fileInputStream = new FileInputStream(encryptedFile);
            final BufferedInputStream bufferdInputStream = new BufferedInputStream(fileInputStream);
            final FileOutputStream fileOutputStream = new FileOutputStream(base64OutputPath);
            final Base64OutputStream base64OutputStream = new Base64OutputStream(fileOutputStream, Base64.DEFAULT);

            StreamUtil.copyStreams(bufferdInputStream, base64OutputStream);

            base64OutputStream.close();
            bufferdInputStream.close();
            fileInputStream.close();

        } catch (IOException e) {
            LogUtil.e(TAG, "saveEncryptedAttachmentFileAsBase64File: " + e.getMessage());
            throw new LocalizedException(LocalizedException.LOAD_FILE_FAILED, "saveEncryptedAttachmentFileAsBase64File()", e);
        }
    }

    public static void saveBase64FileAsEncryptedAttachment(final String attachmentGuid, final String base64FileInputPath)
            throws LocalizedException {
        if (attachmentsDir == null) {
            LogUtil.e(TAG, "saveBase64FileAsEncryptedAttachment: Attachment Directory not given!");
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Attachment Directory not loaded!");
        }

        final File attachmentFile = new File(attachmentsDir, attachmentGuid);

        try {
            final FileInputStream fileInputStream = new FileInputStream(base64FileInputPath);
            final Base64InputStream base64InputStream = new Base64InputStream(fileInputStream, Base64.DEFAULT);
            final FileOutputStream fileOutputStream = new FileOutputStream(attachmentFile);

            StreamUtil.copyStreams(base64InputStream, fileOutputStream);

            fileOutputStream.close();
            base64InputStream.close();
            fileInputStream.close();

        } catch (IOException e) {
            LogUtil.e(TAG, "saveBase64FileAsEncryptedAttachment: "+ e.getMessage());
            throw new LocalizedException(LocalizedException.LOAD_FILE_FAILED, "saveBase64FileAsEncryptedAttachment()", e);
        }
    }

    /**
     * @param attachmentFilename attachmentGuid
     */
    public static void deleteBase64AttachmentFile(final String attachmentFilename) {
        FileUtil.deleteFile(getEncryptedBase64AttachmentFile(attachmentFilename));
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    private static byte[] loadFile(File file)
            throws LocalizedException {
        byte[] returnBuffer = null;
        InputStream inputStream = null;

        try {
            inputStream = new BufferedInputStream(new FileInputStream(file));

            int fileSize = new BigDecimal(file.length()).intValueExact();
            byte[] buffer = new byte[fileSize];

            StreamUtil.safeRead(inputStream, buffer, fileSize);

            returnBuffer = buffer;
        } catch (IOException e) {
            throw new LocalizedException(LocalizedException.LOAD_FILE_FAILED, e);
        } finally {
            StreamUtil.closeStream(inputStream);
        }
        return returnBuffer;
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public void loadAttachment(@NonNull final DecryptedMessage decryptedMsg,
                               @NonNull final OnAttachmentLoadedListener listener,
                               final boolean safeToShareFolder,
                               final HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener)
            throws LocalizedException {
        String attachmentGuid = decryptedMsg.getMessage().getAttachment();
        if (attachmentGuid == null) {
            listener.onLoadedFailed(mApplication.getString(R.string.chat_no_attachment_available));
            return;
        }

        // FallBack für alte attachments
        if ((decryptedMsg.getMessage().getIsSentMessage() != null) && decryptedMsg.getMessage().getIsSentMessage()
                && !attachmentGuid.startsWith("3000:") && !attachmentGuid.startsWith("6:")) {
            // Alte Daten
            saveSendMessageAttachment(decryptedMsg.getMessage());
        }

        final File cacheFile = new File(cacheDir, attachmentGuid);

        if (cacheFile.exists() && !safeToShareFolder) {
            callListener(listener, cacheFile, decryptedMsg);
            return;
        } else if (safeToShareFolder) {
            final String fileName = getShareFileName(decryptedMsg);
            final File shareFile = new File(shareDir, fileName);

            if (shareFile.exists()) {
                callListener(listener, shareFile, decryptedMsg);
                return;
            }
        }

        LoadAttachmentBackendTask task = new LoadAttachmentBackendTask(decryptedMsg, listener, safeToShareFolder, onConnectionDataUpdatedListener);

        task.executeOnExecutor(LoadAttachmentBackendTask.THREAD_POOL_EXECUTOR, null, null, null);
    }

    public void saveSendMessageAttachment(final Message message)
            throws LocalizedException {
        synchronized (this) {
            if (StringUtil.isNullOrEmpty(message.getAttachment())) {
                return;
            }

            final String attachmentGuid = message.getRequestGuid();

            final File encryptedFile = new File(attachmentsDir, attachmentGuid);

            if (!encryptedFile.exists()) {
                File base64File = getEncryptedBase64AttachmentFile(attachmentGuid);
                if (base64File != null && base64File.exists()) {
                    saveBase64FileAsEncryptedAttachment(attachmentGuid, base64File.getAbsolutePath());
                } else {
                    // KS: Encoding the attachment Guid and save it?? This is crap.
                    // Note: message.getAttachment() *does not* return the attachment contents!
                    byte[] content = Base64.decode(message.getAttachment(), Base64.DEFAULT);

                    FileUtil.saveToFile(encryptedFile, content);
                }
            }

            message.setAttachment(attachmentGuid);
        }
    }

    public final void clearCache() {
        if ((cacheDir != null) && cacheDir.exists()) {
            for (File file : cacheDir.listFiles()) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        }
    }

    public void deleteAllAttachments() {
        if ((attachmentsDir != null) && attachmentsDir.exists()) {
            for (File file : attachmentsDir.listFiles()) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        }
    }

    public void deleteAttachment(String attachmentGuid) {
        File cacheFile = new File(cacheDir, attachmentGuid);
        File encryptedFile = new File(attachmentsDir, attachmentGuid);
        File decryptedFile = new File(attachmentsDir, attachmentGuid + ".unsec");

        if (cacheFile.exists()) {
            cacheFile.delete();
        }
        if (encryptedFile.exists()) {
            encryptedFile.delete();
        }
        if (decryptedFile.exists()) {
            decryptedFile.delete();
        }
    }

    public final void deleteShareFiles() {
        if ((shareDir != null) && shareDir.exists()) {
            for (File file : shareDir.listFiles()) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        }
    }

    public boolean isAttachmentLocallyAvailable(final String attachmentGuid) {
        if (StringUtil.isNullOrEmpty(attachmentGuid)) {
            return false;
        }
        File encryptedFile = new File(attachmentsDir, attachmentGuid);
        return encryptedFile.exists();
    }

    private void callListener(final OnAttachmentLoadedListener listener,
                              File file,
                              DecryptedMessage message) {
        String contentType = message.getContentType();

        if (contentType != null) {
            if (contentType.equals(MimeType.IMAGE_JPEG)) {
                listener.onBitmapLoaded(file, message);
            } else if (contentType.equals(MimeType.VIDEO_MPEG)) {
                listener.onVideoLoaded(file, message);
            } else if (contentType.equals(MimeType.AUDIO_MPEG)) {
                listener.onAudioLoaded(file, message);
            } else if (contentType.equals(MimeType.TEXT_PLAIN) && (message.getMessage().getType() == Message.TYPE_CHANNEL)) {
                listener.onBitmapLoaded(file, message);
            } else {
                listener.onFileLoaded(file, message);
            }
            // KS: A file may be of any other mime type!
                /*
            } else if (contentType.equals(MimeType.APP_OCTET_STREAM)) {
                listener.onFileLoaded(file, message);
            } else {
                listener.onLoadedFailed(null);
            }

                 */
        }
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    private byte[] decryptData(DecryptedMessage decryptedMessage,
                               byte[] data)
            throws LocalizedException {
        Message message = decryptedMessage.getMessage();
        SecretKey aesKey = message.getAesKeyDataContainer().getAesKey();
        IvParameterSpec iv = message.getAesKeyDataContainer().getIv();

        byte[] decryptedContentBytes = null;
        byte[] decryptedBase64ContentBytes = null;

        if (message.getType() == Message.TYPE_GROUP) {
            byte[] ivBytes = new byte[SecurityUtil.IV_LENGTH / 8];
            byte[] encryptedContentBytes = new byte[data.length - ivBytes.length];

            System.arraycopy(data, 0, ivBytes, 0, ivBytes.length);
            System.arraycopy(data, ivBytes.length, encryptedContentBytes, 0, encryptedContentBytes.length);

            iv = new IvParameterSpec(ivBytes);

            decryptedBase64ContentBytes = SecurityUtil.decryptMessageWithAES(encryptedContentBytes, aesKey, iv);
        } else if ((message.getType() == Message.TYPE_PRIVATE) || (message.getType() == Message.TYPE_CHANNEL)) {
            decryptedBase64ContentBytes = SecurityUtil.decryptMessageWithAES(data, aesKey, iv);
        }

        if (decryptedBase64ContentBytes != null) {
            if (decryptedMessage.getAttachmentEncodingVersion() == DecryptedMessage.ATTACHMENT_ENCODING_VERSION_DEFAULT) {
                decryptedContentBytes = Base64.decode(decryptedBase64ContentBytes, Base64.DEFAULT);
            } else if (decryptedMessage.getAttachmentEncodingVersion() == DecryptedMessage.ATTACHMENT_ENCODING_VERSION_1) {
                decryptedContentBytes = decryptedBase64ContentBytes;
            }
        }

        return decryptedContentBytes;
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    private File decryptFile(DecryptedMessage decryptedMessage,
                             File encryptedFile,
                             final boolean safeToShareFolder)
            throws LocalizedException {
        try {
            File tempFile = getDecryptedFile(decryptedMessage, safeToShareFolder);

            if (decryptedMessage.getAttachmentEncodingVersion() == DecryptedMessage.ATTACHMENT_ENCODING_VERSION_1) {
                Message message = decryptedMessage.getMessage();
                SecretKey aesKey = message.getAesKeyDataContainer().getAesKey();
                IvParameterSpec iv = message.getAesKeyDataContainer().getIv();

                SecurityUtil.decryptFileWithAes(aesKey, iv, encryptedFile, tempFile, message.getType() == Message.TYPE_GROUP);
            } else {
                byte[] data = loadFile(encryptedFile);
                byte[] decryptedData = decryptData(decryptedMessage, data);

                FileUtil.saveToFile(tempFile, decryptedData);
            }

            return tempFile;
        } catch (NullPointerException e) {
            throw new LocalizedException(LocalizedException.LOAD_FILE_FAILED, e);
        }
    }

    private File getDecryptedFile(DecryptedMessage decryptedMessage,
                                  final boolean safeToShareFolder) {
        File tempFile;

        if (safeToShareFolder) {
            final String fileName = getShareFileName(decryptedMessage);
            tempFile = new File(shareDir, fileName);
        } else {
            String attachmentGuid = decryptedMessage.getMessage().getAttachment();
            tempFile = new File(cacheDir, attachmentGuid);
        }

        return tempFile;
    }

    private String getShareFileName(DecryptedMessage decryptedMessage) {
        String filename = decryptedMessage.getFilename();

        if (StringUtil.isNullOrEmpty(filename)) {
            filename = mApplication.getString(R.string.chat_filename_unknown);
        }

        int dotIndex = filename.lastIndexOf('.');

        if (dotIndex < 0) {
            String mimeType = decryptedMessage.getFileMimetype();
            if (!StringUtil.isNullOrEmpty(mimeType)) {
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                if (!StringUtil.isNullOrEmpty(extension)) {
                    filename = filename + "." + extension;
                }
            }
        }

        return filename;
    }

    /**
     * [!INTERFACE_DESCRIPTION!]
     *
     * @author Florian
     * @version $Revision$, $Date$, $Author$
     */
    public interface OnAttachmentLoadedListener {

        void onBitmapLoaded(File file,
                            DecryptedMessage decryptedMsg);

        void onVideoLoaded(File videoFile,
                           DecryptedMessage decryptedMsg);

        void onAudioLoaded(File audioFile,
                           DecryptedMessage decryptedMsg);

        void onFileLoaded(File dataFile,
                          DecryptedMessage decryptedMsg);

        void onLoadedFailed(String message);

        void onHasNoAttachment(final String message);

        void onHasAttachment(boolean finishedWork);
    }

    private class LoadAttachmentBackendTask
            extends AsyncTask<Void, Void, Void> {

        private final DecryptedMessage mDecryptedMsg;
        private final OnAttachmentLoadedListener mListener;
        private final boolean mSafeToShareFolder;
        private final HttpBaseTask.OnConnectionDataUpdatedListener mOnConnectionDataUpdatedListener;
        private String mConnectionErrorText;
        private Integer mErrorTextId;

        /**
         * LoadAttachmentBackendTask
         */
        LoadAttachmentBackendTask(final DecryptedMessage decryptedMsg,
                                  final OnAttachmentLoadedListener listener,
                                  final boolean safeToShareFolder,
                                  final HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener) {
            mDecryptedMsg = decryptedMsg;
            mListener = listener;
            mSafeToShareFolder = safeToShareFolder;
            mOnConnectionDataUpdatedListener = onConnectionDataUpdatedListener;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                String attachmentGuid = mDecryptedMsg.getMessage().getAttachment();

                if (attachmentGuid == null) {
                    mErrorTextId = R.string.chat_no_attachment_available;
                    return null;
                }

                final File encryptedFile = new File(attachmentsDir, attachmentGuid);

                if (encryptedFile.exists()) {
                    decryptFile(mDecryptedMsg, encryptedFile, mSafeToShareFolder);
                } else {
                    IBackendService.OnBackendResponseListener onBackendResponseListener = new IBackendService.OnBackendResponseListener() {
                        @Override
                        public void onBackendResponse(BackendResponse response) {
                            if (response.isError) {
                                mErrorTextId = R.string.chat_load_attachment_error;
                                mConnectionErrorText = response.errorMessage;
                                if (response.msgException != null && response.msgException.getIdent() != null) {
                                    if (response.msgException.getIdent().equals(BackendError.ERR_0026_CANT_OPEN_MESSAGE)) {
                                        mConnectionErrorText = mApplication.getString(R.string.chats_load_attachment_failed);
                                    }
                                }
                            } else {
                                try {
                                    if(response.responseFilename != null) {
                                        String base64file = convertJsonArrayFileToEncryptedAttachmentBase64File(
                                                response.responseFilename, attachmentGuid);
                                        // TODO: Separate (expensive!) file conversion calls right now. Must be combined later.
                                        saveBase64FileAsEncryptedAttachment(attachmentGuid, base64file);
                                        LogUtil.d(TAG, "Saved attachment from file for: " + attachmentGuid);
                                        FileUtil.deleteFile(base64file);

                                    } else if (response.jsonArray != null && response.jsonArray.get(0) != null) {
                                        byte[] content = Base64.decode(response.jsonArray.get(0).getAsString(), Base64.DEFAULT);

                                        FileUtil.saveToFile(encryptedFile, content);
                                        LogUtil.d(TAG, "Saved attachment from memory for: " + attachmentGuid);

                                    } else {
                                        mErrorTextId = R.string.chat_load_attachment_error;
                                        return;
                                    }

                                    decryptFile(mDecryptedMsg, encryptedFile, mSafeToShareFolder);

                                    final JsonArray jsonArray = new JsonArray();
                                    jsonArray.add(new JsonPrimitive(mDecryptedMsg.getMessage().getGuid()));

                                    BackendService.withSyncConnection(mApplication)
                                            .setMessageState(
                                                    jsonArray,
                                                    AppConstants.MESSAGE_STATE_ATTACHMENT_DOWNLOADED,
                                                    null,
                                                    false);
                                } catch (LocalizedException e) {
                                    LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                                    mErrorTextId = R.string.chat_load_attachment_error;
                                }
                            }
                        }
                    };
                    BackendService.withSyncConnection(mApplication)
                            .getAttachment(attachmentGuid, onBackendResponseListener, mOnConnectionDataUpdatedListener);
                }
            } catch (LocalizedException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                mErrorTextId = R.string.chat_load_attachment_error;
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            LogUtil.d(this.getClass().getSimpleName(), "onProgressUpdate called.");
        }

        @Override
        protected void onPostExecute(Void value) {
            if (mErrorTextId == null) {
                File decryptedFile = getDecryptedFile(mDecryptedMsg, mSafeToShareFolder);
                callListener(mListener, decryptedFile, mDecryptedMsg);
            } else {
                try {
                    String errorMessage = mApplication.getString(mErrorTextId);

                    if (!StringUtil.isNullOrEmpty(mConnectionErrorText)) {
                        errorMessage = errorMessage + "\n\n" + mConnectionErrorText;
                    }
                    mListener.onLoadedFailed(errorMessage);
                } catch (Resources.NotFoundException e) {
                    LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                    mListener.onLoadedFailed(null);
                }
            }
        }
    }
}
