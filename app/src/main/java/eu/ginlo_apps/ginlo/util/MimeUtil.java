// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.util.ArrayList;
import java.util.Locale;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.model.constant.MimeType;

public class MimeUtil {

    public static final int MIMETYPE_NOT_FOUND = -1;
    private static final String MIME_TYPE_PDF = MimeType.APP_PDF;
    private static final String MIME_TYPE_MSPOWERPOINT = "application/mspowerpoint";
    private static final String MIME_TYPE_MSPOWERPOINT2 = "application/vnd.ms-powerpoint";
    private static final String MIME_TYPE_MSPOWERPOINT_MACRO = "application/vnd.ms-powerpoint.presentation.macroenabled.12";
    private static final String MIME_TYPE_OOPOWERPOINT = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String MIME_TYPE_APPLE_KEYNOTE = "application/x-iwork-keynote-sffkey";
    private static final String MIME_TYPE_MSWORD = "application/msword";
    private static final String MIME_TYPE_OOWORD = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String MIME_TYPE_APPLE_PAGES = "application/x-iwork-pages-sffpages";
    private static final String MIME_TYPE_ZIP = "application/zip";
    private static final String MIME_TYPE_GZIP = "application/gzip";
    private static final String MIME_TYPE_7ZIP = "application/x-7z-compressed";
    private static final String MIME_TYPE_RAR = "application/x-rar-compressed";
    private static final String MIME_TYPE_MSEXCEL = "application/msexcel";
    private static final String MIME_TYPE_VND_MSEXCEL = "application/vnd.ms-excel";
    private static final String MIME_TYPE_OOEXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String MIME_TYPE_CSV = "text/csv";
    private static final String MIME_TYPE_CSV2 = "text/comma-separated-values";
    private static final String MIME_TYPE_APPLE_NUMBERS = "application/x-iwork-numbers-sffnumbers";

    private static final String CONTENT = "content";
    private static final String FILE = "file";

    private final Context context;

    public MimeUtil(Context context) {
        this.context = context;
    }

    public static int getIconForMimeType(String mimeType) {
        int resID;

        switch (mimeType) {
            case MIME_TYPE_PDF:
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

    public String getMimeTypeFromURI(Uri path) {
        ContentResolver cR = context.getContentResolver();
        return cR.getType(path);
    }

    public String getMimeType(Uri contentUri) {
        String type = null;
        if (contentUri == null) {
            return null;
        } else if (CONTENT.equalsIgnoreCase(contentUri.getScheme())) {
            type = getMimeTypeFromURI(contentUri);
        }
        else if (FILE.equalsIgnoreCase(contentUri.getScheme())) {
            type = getMimeTypeFromPath(contentUri.getPath());
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

    public boolean checkUriMimeType(Context context,
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

    public ArrayList<String> getAllowedVideoMimeTypes() {
        ArrayList<String> l = new ArrayList<>();

        l.add("video/mpeg");
        l.add("mpeg");
        l.add("video/mp4");
        l.add("mp4");
        l.add("video/3gpp");
        l.add("3gpp");
        return l;
    }


}
