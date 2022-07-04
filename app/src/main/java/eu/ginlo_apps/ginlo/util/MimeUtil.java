// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;

public class MimeUtil {
    private static final String TAG = "MimeUtil";

    // Image mime types
    public static final String MIME_TYPE_IMAGE_WILDCARD = "image/*";
    public static final String MIME_TYPE_IMAGE_JPEG = "image/jpeg";
    public static final String MIME_TYPE_IMAGE_JPG = "image/jpg";
    public static final String MIME_TYPE_IMAGE_JPE = "image/jpe";
    public static final String MIME_TYPE_IMAGE_APNG = "image/apng";
    public static final String MIME_TYPE_IMAGE_AVIF = "image/avif";
    public static final String MIME_TYPE_IMAGE_GIF = "image/gif";
    public static final String MIME_TYPE_IMAGE_PNG = "image/png";
    public static final String MIME_TYPE_IMAGE_PSD = "image/psd";
    public static final String MIME_TYPE_IMAGE_BMP = "image/bmp";
    public static final String MIME_TYPE_IMAGE_XBMP = "image/x-bmp";
    public static final String MIME_TYPE_IMAGE_XMSBMP = "image/x-ms-bmp";
    public static final String MIME_TYPE_IMAGE_TIFF = "image/tiff";
    public static final String MIME_TYPE_IMAGE_TIF = "image/tif";
    public static final String MIME_TYPE_IMAGE_SVG = "image/svg+xml";
    public static final String MIME_TYPE_IMAGE_WEBP = "image/webp";

    // Lottie
    public static final String MIME_TYPE_APP_JSON = "application/json";

    // Audio mime types
    public static final String MIME_TYPE_AUDIO_WILDCARD = "audio/*";
    public static final String MIME_TYPE_AUDIO_MPEG = "audio/mpeg";
    public static final String MIME_TYPE_AUDIO_MP3 = "audio/mp3";
    public static final String MIME_TYPE_AUDIO_OGG = "audio/ogg";
    public static final String MIME_TYPE_AUDIO_FLAC = "audio/flac";
    public static final String MIME_TYPE_AUDIO_AAC = "audio/aac";
    public static final String MIME_TYPE_AUDIO_MP4 = "audio/mp4";
    public static final String MIME_TYPE_AUDIO_WAVE = "audio/wave";
    public static final String MIME_TYPE_AUDIO_WAV = "audio/wav";
    public static final String MIME_TYPE_AUDIO_WEBM = "audio/webm";

    // Video mime types
    public static final String MIME_TYPE_VIDEO_WILDCARD = "video/*";
    public static final String MIME_TYPE_VIDEO_MPEG = "video/mpeg";
    public static final String MIME_TYPE_VIDEO_3GPP = "video/3gpp";
    public static final String MIME_TYPE_VIDEO_OGG = "video/ogg";
    public static final String MIME_TYPE_VIDEO_MP4 = "video/mp4";
    public static final String MIME_TYPE_VIDEO_WEBM = "video/webm";
    public static final String MIME_TYPE_VIDEO_QT = "video/quicktime";
    public static final String MIME_TYPE_VIDEO_AVI = "video/x-msvideo";

    // (Proprietary) document format mime types
    public static final String MIME_TYPE_APP_PDF = "application/pdf";
    public static final String MIME_TYPE_MSPOWERPOINT = "application/mspowerpoint";
    public static final String MIME_TYPE_MSPOWERPOINT2 = "application/vnd.ms-powerpoint";
    public static final String MIME_TYPE_MSPOWERPOINT_MACRO = "application/vnd.ms-powerpoint.presentation.macroenabled.12";
    public static final String MIME_TYPE_OOPOWERPOINT = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    public static final String MIME_TYPE_APPLE_KEYNOTE = "application/x-iwork-keynote-sffkey";
    public static final String MIME_TYPE_MSWORD = "application/msword";
    public static final String MIME_TYPE_OOWORD = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String MIME_TYPE_APPLE_PAGES = "application/x-iwork-pages-sffpages";
    public static final String MIME_TYPE_MSEXCEL = "application/msexcel";
    public static final String MIME_TYPE_VND_MSEXCEL = "application/vnd.ms-excel";
    public static final String MIME_TYPE_OOEXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String MIME_TYPE_CSV = "text/csv";
    public static final String MIME_TYPE_CSV2 = "text/comma-separated-values";
    public static final String MIME_TYPE_APPLE_NUMBERS = "application/x-iwork-numbers-sffnumbers";
    
    // Compression
    public static final String MIME_TYPE_ZIP = "application/zip";
    public static final String MIME_TYPE_GZIP = "application/gzip";
    public static final String MIME_TYPE_7ZIP = "application/x-7z-compressed";
    public static final String MIME_TYPE_RAR = "application/x-rar-compressed";
    
    // ginlo: AVC extensions
    public static final String MIME_TYPE_TEXT_V_CALL = "text/x-ginlo-call-invite";
    public static final String MIME_TYPE_APP_GINLO_CONTROL = "application/x-ginlo-control-message";

    // ginlo: Helper type for rich content
    public static final String MIME_TYPE_APP_GINLO_RICH_CONTENT = "application/x-ginlo-rich-content";
    public static final String MIME_TYPE_APP_GINLO_LOTTIE = "application/x-ginlo-lottie";

    // Others
    public static final String MIME_TYPE_TEXT_PLAIN = "text/plain";
    public static final String MIME_TYPE_TEXT_RSS = "text/rss";
    public static final String MIME_TYPE_TEXT_V_CARD = "text/x-vcard";
    public static final String MIME_TYPE_MODEL_LOCATION = "model/location";
    // To be compatible to apps with typos
    public static final String MIME_TYPE_APP_OCTET_STREAM = "application/octetstream";
    public static final String MIME_TYPE_APP_OCTETSTREAM = "application/octet-stream";

    public static final int MIMETYPE_NOT_FOUND = -1;

    // Schemas
    public static final String CONTENT = "content";
    public static final String FILE = "file";

    private final Context context;
    private static final ArrayList<String> imageMimeTypes = new ArrayList<>();
    private static final ArrayList<String> videoMimeTypes = new ArrayList<>();
    private static final ArrayList<String> audioMimeTypes = new ArrayList<>();
    private static final ArrayList<String> richContentMimeTypes = new ArrayList<>();

    public MimeUtil(Context context) {
        this.context = context;
    }

    /**
     * Build and/or return a list of compatible image mimetypes
     * @return
     */
    public static ArrayList<String> getImageMimeTypes() {
        if(imageMimeTypes.isEmpty()) {
            imageMimeTypes.add(MIME_TYPE_IMAGE_JPEG);
            imageMimeTypes.add(MIME_TYPE_IMAGE_JPG);
            imageMimeTypes.add(MIME_TYPE_IMAGE_JPE);
            imageMimeTypes.add(MIME_TYPE_IMAGE_APNG);
            imageMimeTypes.add(MIME_TYPE_IMAGE_AVIF);
            imageMimeTypes.add(MIME_TYPE_IMAGE_GIF);
            imageMimeTypes.add(MIME_TYPE_IMAGE_PNG);
            //imageMimeTypes.add(MIME_TYPE_IMAGE_PSD);
            imageMimeTypes.add(MIME_TYPE_IMAGE_BMP);
            imageMimeTypes.add(MIME_TYPE_IMAGE_XBMP);
            imageMimeTypes.add(MIME_TYPE_IMAGE_XMSBMP);
            imageMimeTypes.add(MIME_TYPE_IMAGE_TIFF);
            imageMimeTypes.add(MIME_TYPE_IMAGE_TIF);
            imageMimeTypes.add(MIME_TYPE_IMAGE_SVG);
            imageMimeTypes.add(MIME_TYPE_IMAGE_WEBP);
        }

        return imageMimeTypes;
    }

    /**
     * Build and/or return a list of compatible audio mimetypes
     * @return
     */
    public static ArrayList<String> getAudioMimeTypes() {
        if (audioMimeTypes.isEmpty()) {
            audioMimeTypes.add(MIME_TYPE_AUDIO_MPEG);
            audioMimeTypes.add(MIME_TYPE_AUDIO_MP3);
            audioMimeTypes.add(MIME_TYPE_AUDIO_OGG);
            audioMimeTypes.add(MIME_TYPE_AUDIO_FLAC);
            audioMimeTypes.add(MIME_TYPE_AUDIO_AAC);
            audioMimeTypes.add(MIME_TYPE_AUDIO_MP4);
            audioMimeTypes.add(MIME_TYPE_AUDIO_WAVE);
            audioMimeTypes.add(MIME_TYPE_AUDIO_WAV);
            audioMimeTypes.add(MIME_TYPE_AUDIO_WEBM);
        }

        return audioMimeTypes;
    }

    /**
     * Build and/or return a list of compatible video mimetypes
     * @return
     */
    public static ArrayList<String> getVideoMimeTypes() {
        if(videoMimeTypes.isEmpty()) {
            videoMimeTypes.add(MIME_TYPE_VIDEO_MPEG);
            videoMimeTypes.add(MIME_TYPE_VIDEO_3GPP);
            videoMimeTypes.add(MIME_TYPE_VIDEO_OGG);
            videoMimeTypes.add(MIME_TYPE_VIDEO_MP4);
            videoMimeTypes.add(MIME_TYPE_VIDEO_WEBM);
            videoMimeTypes.add(MIME_TYPE_VIDEO_QT);
            videoMimeTypes.add(MIME_TYPE_VIDEO_AVI);
        }

        return videoMimeTypes;
    }

    /**
     * Build and/or return a list of compatible rich content (gifs, stickers etc.) mimetypes
     * @return
     */
    public static ArrayList<String> getRichContentMimeTypes() {
        if(richContentMimeTypes.isEmpty()) {
            richContentMimeTypes.add(MIME_TYPE_APP_GINLO_RICH_CONTENT); // proprietary internal type
            richContentMimeTypes.add(MIME_TYPE_APP_GINLO_LOTTIE); // proprietary internal type
            richContentMimeTypes.add(MIME_TYPE_APP_JSON); // Used for lottie animations
            richContentMimeTypes.add(MIME_TYPE_IMAGE_GIF);
            richContentMimeTypes.add(MIME_TYPE_IMAGE_WEBP);
            richContentMimeTypes.add(MIME_TYPE_IMAGE_PNG);
        }

        return richContentMimeTypes;
    }

    /**
     * Check Uri for known image mimetype
     * @param context
     * @param contentUri
     * @return
     */
    public static boolean checkImageUriMimetype(Context context, Uri contentUri) {
        return isKnownUriMimeType(context, contentUri, getImageMimeTypes());
    }

    /**
     * Check Uri for known audio mimetype
     * @param context
     * @param contentUri
     * @return
     */
    public static boolean checkAudioUriMimetype(Context context, Uri contentUri) {
        return isKnownUriMimeType(context, contentUri, getAudioMimeTypes());
    }

    /**
     * Check Uri for known video mimetype
     * @param context
     * @param contentUri
     * @return
     */
    public static boolean checkVideoUriMimetype(Context context, Uri contentUri) {
        return isKnownUriMimeType(context, contentUri, getVideoMimeTypes());
    }

    /**
     * Check Uri for known rich content mimetypes
     * @param context
     * @param contentUri
     * @return
     */
    public static boolean checkRichContentUriMimetype(Context context, Uri contentUri) {
        return isKnownUriMimeType(context, contentUri, getRichContentMimeTypes());
    }

    /**
     * Check Uri for requested mimetype
     * @param context
     * @param contentUri
     * @return
     */
    public static boolean isKnownUriMimeType(Context context, Uri contentUri, ArrayList<String> knownMimeTypes) {
        if (context == null || contentUri == null) {
            return false;
        }

        LogUtil.d(TAG, "isKnownUriMimeType: Check " + contentUri.getPath() + " against: " + knownMimeTypes);

        String mimeType = getMimeTypeForUri(context, contentUri);
        return StringUtil.isInList(mimeType, knownMimeTypes, true);
    }

    /**
     * Is given mimetype for Glide processing?
     * @param mimeType
     * @return
     */
    public static boolean isGlideMimetype(String mimeType) {
        if(StringUtil.isNullOrEmpty(mimeType)) {
            return false;
        }

        return (mimeType.equals(MIME_TYPE_IMAGE_GIF)
                || mimeType.equals(MIME_TYPE_IMAGE_TIF)
                || mimeType.equals(MIME_TYPE_IMAGE_TIFF)
                || mimeType.equals(MIME_TYPE_IMAGE_WEBP)
                || mimeType.equals(MIME_TYPE_IMAGE_JPEG)
                || mimeType.equals(MIME_TYPE_IMAGE_JPG)
                || mimeType.equals(MIME_TYPE_IMAGE_JPE)
                || mimeType.equals(MIME_TYPE_IMAGE_SVG)
                || mimeType.equals(MIME_TYPE_IMAGE_BMP)
                || mimeType.equals(MIME_TYPE_IMAGE_XBMP)
                || mimeType.equals(MIME_TYPE_IMAGE_XMSBMP)
                || mimeType.equals(MIME_TYPE_IMAGE_PNG)
        );
    }

    /**
     * Must analyze. Lottie files are mostly json files - check "magic bytes"
     * @param fileToCheck
     * @return
     */
    public static boolean isLottieFile(String mimeType, File fileToCheck) {
        boolean returnValue = false;

        if(mimeType != null && mimeType.equals(MIME_TYPE_APP_GINLO_LOTTIE)) {
            // Has already been analyzed. Trust that.
            returnValue = true;

        } else if (mimeType == null || mimeType.equals(MIME_TYPE_APP_JSON)) {
            if (fileToCheck != null) {
                if (fileToCheck.getName().endsWith(".tgs")) {
                    LogUtil.d(TAG, "isLottieFile: Got first hint with file extension .tgs");
                    // This is possible, but we should dig deeper.
                    //returnValue = true;
                }

                LogUtil.d(TAG, "isLottieFile:Analyzing " + fileToCheck);

                byte[] magic = "{\"tgs\"".getBytes(StandardCharsets.UTF_8);
                if (FileUtil.haveFileMagic(fileToCheck, magic)) {
                    LogUtil.d(TAG, "isLottieFile: Identified by magic " + Arrays.toString(magic));
                    returnValue = true;
                } else {
                    magic = "{\"v\"".getBytes(StandardCharsets.UTF_8);
                    if (FileUtil.haveFileMagic(fileToCheck, magic)) {
                        LogUtil.d(TAG, "isLottieFile: Identified by magic " + Arrays.toString(magic));
                        returnValue = true;
                    }
                }
            }
        }
        return returnValue;
    }

    /**
     * Is given mimetype a compatible image mimetype?
     * @param mimeType
     * @return
     */
    public static boolean isImageMimetype(String mimeType, boolean withWildcard) {
        boolean haveImageMimetype = false;
        if(!StringUtil.isNullOrEmpty(mimeType)) {
            if(withWildcard && MIME_TYPE_IMAGE_WILDCARD.contains(mimeType)) {
                haveImageMimetype = true;
            } else {
                haveImageMimetype = getImageMimeTypes().contains(mimeType);
            }
        }

        return  haveImageMimetype;
    }

    /**
     * Is given mimetype a compatible video mimetype?
     * @param mimeType
     * @return
     */
    public static boolean isVideoMimetype(String mimeType, boolean withWildcard) {
        boolean haveVideoMimetype = false;
        if(!StringUtil.isNullOrEmpty(mimeType)) {
            if(withWildcard && MIME_TYPE_VIDEO_WILDCARD.contains(mimeType)) {
                haveVideoMimetype = true;
            } else {
                haveVideoMimetype = getVideoMimeTypes().contains(mimeType);
            }
        }

        return  haveVideoMimetype;
    }

    /**
     * Is given mimetype a compatible rich content mimetype?
     * These are all we have our internal Glide/RLottie components for.
     * @param mimeType
     * @return
     */
    public static boolean isRichContentMimetype(String mimeType) {
        if(StringUtil.isNullOrEmpty(mimeType)) {
            return false;
        }

        return getRichContentMimeTypes().contains(mimeType);
    }

    public static String getMimeTypeForUri(Context context, Uri contentUri) {
        String type = null;
        if (contentUri != null) {
            // First try this
            ContentResolver cR = context.getContentResolver();
            type = cR.getType(contentUri);
            LogUtil.d(TAG, "getMimeTypeForUri: ContentResolver brought: " + type);

            if(MimeUtil.hasEmptyOrUnspecificMimeType(type)) {
                final String keepResult = type;
                // So we should try that
                type = getMimeTypeFromFilename(contentUri.getPath());
                LogUtil.d(TAG, "getMimeTypeForUri: Pathname brought: " + type);
                // Step back if the latest result isn't even better ...
                if(type == null && keepResult != null) {
                    type = keepResult;
                }
            }
        }

        return type;
    }

    public static String getExtensionForUri(Context context, final Uri uri) {
        String extension = null;
        String mimetype = getMimeTypeForUri(context, uri);

        if (!StringUtil.isNullOrEmpty(mimetype)) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            extension = mime.getExtensionFromMimeType(mimetype);
        }

        return extension;
    }

    public static int getIconForMimeType(String mimeType) {
        int resID;

        switch (mimeType) {
            case MIME_TYPE_APP_PDF:
                resID = R.drawable.data_pdf;
                break;

            case MIME_TYPE_MSPOWERPOINT:
            case MIME_TYPE_MSPOWERPOINT2:
            case MIME_TYPE_MSPOWERPOINT_MACRO:
            case MIME_TYPE_OOPOWERPOINT:
            case MIME_TYPE_APPLE_KEYNOTE:
                resID = R.drawable.data_praesent;
                break;

            case MIME_TYPE_MSWORD:
            case MIME_TYPE_OOWORD:
            case MIME_TYPE_APPLE_PAGES:
                resID = R.drawable.data_doc;
                break;

            case MIME_TYPE_MSEXCEL:
            case MIME_TYPE_VND_MSEXCEL:
            case MIME_TYPE_OOEXCEL:
            case MIME_TYPE_CSV:
            case MIME_TYPE_CSV2:
            case MIME_TYPE_APPLE_NUMBERS:
                resID = R.drawable.data_xls;
                break;

            case MIME_TYPE_ZIP:
            case MIME_TYPE_GZIP:
            case MIME_TYPE_7ZIP:
            case MIME_TYPE_RAR:
                resID = R.drawable.data_zip;
                break;

            default:
                resID = MIMETYPE_NOT_FOUND;
        }
        return resID;
    }

    public static Boolean hasUnspecificBinaryMimeType(String mimeType) {
        return  MimeUtil.MIME_TYPE_APP_OCTETSTREAM.equals(mimeType) ||
                MimeUtil.MIME_TYPE_APP_OCTET_STREAM.equals(mimeType);
    }

    public static Boolean hasEmptyOrUnspecificMimeType(String mimeType) {
        return StringUtil.isNullOrEmpty(mimeType) ||
                MimeUtil.MIME_TYPE_APP_GINLO_RICH_CONTENT.equals(mimeType) ||
                MimeUtil.MIME_TYPE_TEXT_PLAIN.equals(mimeType) ||
                hasUnspecificBinaryMimeType(mimeType);
    }

    public static String grabMimeType(String filename, DecryptedMessage decryptedMsg, String defaultMimeType) {

        // Use all we have to get a mime type.
        String mimeType = decryptedMsg.getContentType();
        String tmpMimeType = null;

        LogUtil.d(TAG, "grabMimeType: Check mimetype in ContentType: " + mimeType);

        if(hasEmptyOrUnspecificMimeType(mimeType)) {
            tmpMimeType = mimeType;
            mimeType = decryptedMsg.getFileMimetype();
            LogUtil.d(TAG, "grabMimeType: Check mimetype in FileMimetype: " + mimeType);
            if (hasEmptyOrUnspecificMimeType(mimeType)) {
                // Only alibi? Dig deeper, but keep what we have
                if (tmpMimeType == null) {
                    tmpMimeType = mimeType;
                }
                mimeType = MimeUtil.getMimeTypeFromFilename(filename);
                LogUtil.d(TAG, "grabMimeType: Check mimetype in file pathname: " + mimeType);
                if (StringUtil.isNullOrEmpty(mimeType)) {
                    // Ok, use what we have
                    mimeType = tmpMimeType != null ? tmpMimeType : defaultMimeType;
                    LogUtil.d(TAG, "grabMimeType: No more options - finally we have: " + mimeType);
                }
            }
        }
        LogUtil.d(TAG, "grabMimeType: Using " + mimeType);
        return mimeType;
    }

    public static String getMimeTypeFromFilename(String filename) {
        if (filename == null) {
            return null;
        }
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(filename.replace(" ", ""));

        if (StringUtil.isNullOrEmpty(extension)) {
            int dotIndex = filename.lastIndexOf('.');

            if (dotIndex > -1) {
                extension = filename.substring(dotIndex + 1);
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
                case "json":
                case "tgs": // Stickers / lottie animations come as json-files
                    return MimeUtil.MIME_TYPE_APP_JSON;
                default:
                    break;
            }
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            type = mime.getMimeTypeFromExtension(extension.toLowerCase(Locale.US));
        }
        return type;
    }
}
