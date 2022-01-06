// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;
import com.google.zxing.common.BitMatrix;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.log.LogUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class BitmapUtil {

    private final static String TAG = BitmapUtil.class.getSimpleName();

    /**
     * Compress a given jpeg bitmap according to the quality requested.
     * Save result to temp file.
     * @param context
     * @param bitmap
     * @param quality
     * @return
     */
    public static File compress(Context context, Bitmap bitmap, int quality) {
        FileUtil fu = new FileUtil(context);
        File tempFile = null;
        try {
            tempFile = fu.getTempFile();
            FileOutputStream fos = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);
        } catch (IOException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }

        return tempFile;
    }

    public static byte[] compress(Bitmap bitmap,
                                  int quality) {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();

        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteOutputStream);

        return byteOutputStream.toByteArray();
    }

    public static Bitmap scale(int newWidth,
                               int newHeight,
                               Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false);
    }

    public static Bitmap decodeBitMatrix(BitMatrix bitMatrix) {
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();

        Bitmap bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);

        int[] pixels = new int[width * height];
        int index = 0;

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                index = (i * width) + j;
                pixels[index] = bitMatrix.get(j, i) ? 0xff000000 : 0xffffffff;
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private static String getRealPathFromURI(Context context,
                                             Uri imageUri) {
        if (imageUri == null) {
            return null;
        }

        String scheme = imageUri.getScheme();

        if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(scheme)) {
            return imageUri.getPath();
        } else if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(scheme)) {
            return getPathForV19AndUp(context, imageUri);
        }

        return null;
    }

    /**
     * Handles V19 and up uri's
     *
     * @param context
     * @param contentUri
     * @return path
     */
    private static String getPathForV19AndUp(Context context,
                                             Uri contentUri) {
        String filePath = null;
        //Documents
        if (DocumentsContract.isDocumentUri(context, contentUri)) {
            String documentId = DocumentsContract.getDocumentId(contentUri);

            if (StringUtil.isNullOrEmpty(documentId)) {
                return null;
            }

            if (isExternalStorageDocument(contentUri)) {
                final String[] split = documentId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    filePath = Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if (isDownloadsDocument(contentUri)) {
                final Uri fileUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(documentId));

                filePath = getDataColumn(context, fileUri, null, null);
            } else if (isMediaDocument(contentUri)) {
                if (documentId.indexOf(":") > 0) {
                    String id = documentId.split(":")[1];

                    // where id is equal to
                    String sel = MediaStore.Images.Media._ID + "=?";

                    filePath = getDataColumn(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, sel, new String[]{id});
                }
            }
        } else {
            //Mediastore und externe Apps
            filePath = getDataColumn(context, contentUri, null, null);
        }

        return filePath;
    }

    /**
     * getDataColumn
     */
    private static String getDataColumn(Context context, Uri uri, String selection,
                                        String[] selectionArgs) {
        String dataPath = null;

        final String columnData = MediaStore.Images.Media.DATA;

        final String[] projection = {
                columnData,
        };

        try (final Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int columnIndexData = cursor.getColumnIndex(columnData);

                if (columnIndexData > -1) {
                    dataPath = cursor.getString(columnIndexData);
                }
            }
        }
        return dataPath;
    }

    /**
     * isExternalStorageDocument
     *
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static Bitmap decodeUri(Activity context,
                                   Uri imageUri,
                                   int scaleSize,
                                   boolean rotateToRightOrientation) {

        InputStream inputSizeStream = null;
        InputStream inputStream = null;

        try {
            inputSizeStream = context.getContentResolver().openInputStream(imageUri);

            int sampleSize = 0;

            if (scaleSize > 0) {
                sampleSize = getSampleSize(inputSizeStream, scaleSize, scaleSize);
            }

            StreamUtil.closeStream(inputSizeStream);
            inputSizeStream = null;

            inputStream = context.getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = decodeStream(inputStream, sampleSize);
            Bitmap adjustedBitmap = null;

            if (rotateToRightOrientation) {
                int rotationInDegrees = getRotationInDegreesFromImg(imageUri, context);

                if (rotationInDegrees != -1) {
                    Matrix matrix = new Matrix();

                    matrix.preRotate(rotationInDegrees);

                    adjustedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                }
            }

            return (adjustedBitmap != null) ? adjustedBitmap : bitmap;
        } catch (FileNotFoundException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            return null;
        } catch (SecurityException e) {
            LogUtil.w(TAG, "SecurityException uri:" + imageUri.toString(), e);
            return null;
        } finally {
            StreamUtil.closeStream(inputStream);
            StreamUtil.closeStream(inputSizeStream);
        }
    }


    /**
     * Laedt ein Bitmap. Es wird in die richtige Orientierung gebracht. Falls das
     * Bitmap groesser als die angegebene maximal Hoehe oder Breite ist wird es
     * verkleinert.
     *
     * @param context
     * @param imageUri
     * @param maxWidth                 maximale Hoehe
     * @param maxHeight                maximale Breite
     * @param rotateToRightOrientation maximale Breite
     * @return Bitmap
     */
    public static Bitmap decodeUri(Context context,
                                   Uri imageUri,
                                   int maxWidth,
                                   int maxHeight,
                                   boolean rotateToRightOrientation) {
        InputStream inputStream = null;
        InputStream inputSizeStream = null;

        try {
            inputSizeStream = context.getContentResolver().openInputStream(imageUri);
            inputStream = context.getContentResolver().openInputStream(imageUri);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            BitmapFactory.decodeStream(inputSizeStream, null, options);

            int orgImgHeight = options.outHeight;
            int orgImgWidth = options.outWidth;

            LogUtil.i(TAG, "Load Image from (" + imageUri.getPath() + ") with orgImgWidth = " + orgImgWidth + ", orgImgHeight = " + orgImgHeight);

            int imageSampleSize = calculateSampleSize(options, maxWidth, maxHeight);
            options.inSampleSize = imageSampleSize;
            options.inJustDecodeBounds = false;

            Bitmap bitmap;

            try {
                bitmap = BitmapFactory.decodeStream(inputStream, null, options);
            } catch (final OutOfMemoryError e) {
                LogUtil.w(TAG, e.getMessage(), e);
                bitmap = null;
            }

            if (bitmap == null) {
                return null;
            }

            Matrix matrix = new Matrix();

            if (rotateToRightOrientation) {
                int rotationInDegrees = getRotationInDegreesFromImg(imageUri, context);

                if (rotationInDegrees != -1) {
                    matrix.postRotate(rotationInDegrees);
                }
            }

            int scaledWidth;
            int scaledHeight;

            Bitmap returnBitmap;

            if ((orgImgHeight <= maxHeight) && (orgImgWidth <= maxWidth)) {
                if (rotateToRightOrientation) {
                    returnBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                } else {
                    returnBitmap = bitmap;
                }
            } else {
                if (orgImgHeight > orgImgWidth) {
                    scaledHeight = maxHeight;
                    scaledWidth = (orgImgWidth * maxHeight) / orgImgHeight;
                } else {
                    scaledWidth = maxWidth;
                    scaledHeight = (orgImgHeight * maxWidth) / orgImgWidth;
                }

                final int width = bitmap.getWidth();
                final int height = bitmap.getHeight();
                final float sx = scaledWidth / (float) width;
                final float sy = scaledHeight / (float) height;

                matrix.postScale(sx, sy);

                returnBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
                LogUtil.d(TAG, "Built bitmap with width = " + width + ", height = " + height);
            }
            return returnBitmap;

        } catch (FileNotFoundException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        } catch (SecurityException e) {
            LogUtil.w(TAG, "SecurityException uri:" + imageUri.toString(), e);
        } finally {
            StreamUtil.closeStream(inputStream);
            StreamUtil.closeStream(inputSizeStream);
        }

        return null;
    }

    /**
     * @param imageUri
     * @param context
     * @return -1 wenn die Rotation nicht ausgelesen werden konnte
     */
    private static int getRotationInDegreesFromImg(Uri imageUri,
                                                   Context context) {
        int rotationInDegrees = -1;

        try {
            String contentPath = getRealPathFromURI(context, imageUri);

            if (!StringUtil.isNullOrEmpty(contentPath)) {
                ExifInterface exif = new ExifInterface(contentPath);
                int rotation;

                rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

                if (rotation != 0) {
                    rotationInDegrees = exifToDegrees(rotation);
                }
            }
        } catch (Exception e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }

        return rotationInDegrees;
    }

    private static int exifToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }

    private static Bitmap decodeStream(InputStream inputStream,
                                       int sampleSize) {
        if (sampleSize <= 0) {
            return BitmapFactory.decodeStream(inputStream);
        }

        BitmapFactory.Options options = new BitmapFactory.Options();

        options.inSampleSize = sampleSize;

        return BitmapFactory.decodeStream(inputStream, null, options);
    }

    public static Bitmap decodeByteArray(byte[] bitmapBytes) {
        if (bitmapBytes == null) {
            return null;
        }
        return BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
    }

    public static BitmapFactory.Options getDimensions(Resources res,
                                                      int resourceId) {
        BitmapFactory.Options options = new BitmapFactory.Options();

        options.inJustDecodeBounds = true;

        BitmapFactory.decodeResource(res, resourceId, options);
        return options;
    }

    public static Bitmap decodeSampledBitmapFromResource(Resources res, int resId,
                                                         int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);
        options.inSampleSize = calculateSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    private static int calculateSampleSize(BitmapFactory.Options options,
                                           int reqWidth,
                                           int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int imageSampleSize = 1;

        if ((height > reqHeight) || (width > reqWidth)) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (((halfHeight / imageSampleSize) > reqHeight) || ((halfWidth / imageSampleSize) > reqWidth)) {
                imageSampleSize *= 2;
            }
        }

        LogUtil.d(TAG, "Calculated image samplesize of " + imageSampleSize);
        return imageSampleSize;
    }

    public static int getSampleSize(InputStream inputStream,
                                    int reqWidth,
                                    int reqHeight) {

        BitmapFactory.Options options = new BitmapFactory.Options();

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, options);

        return calculateSampleSize(options, reqWidth, reqHeight);
    }

    public static int getSampleSize(Resources res,
                                    int resourceId,
                                    int reqWidth,
                                    int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resourceId, options);

        return calculateSampleSize(options, reqWidth, reqHeight);
    }

    public static Drawable getConfiguredStateDrawable(@NonNull final Application context,
                                                      @NonNull final boolean isAbsent,
                                                      final boolean useContrastforOuterColor) {
        Drawable d = context.getDrawable(R.drawable.contact_state_bubble);
        final ColorUtil colorUtil = ColorUtil.getInstance();
        if (d instanceof LayerDrawable) {
            LayerDrawable contactStateBubble = (LayerDrawable) d;
            int drawableCount = contactStateBubble.getNumberOfLayers();

            if (drawableCount < 2) {
                //muessen zwei sein
                return null;
            }

            for (int i = 0; i < drawableCount; i++) {
                Drawable childDrawable = contactStateBubble.getDrawable(i);
                if (childDrawable == null) {
                    continue;
                }
                if (contactStateBubble.getId(i) == R.id.contact_state_bubble_inner) {
                    final int stateColor = isAbsent ? colorUtil.getLowColor((Application) context.getApplicationContext())
                            : colorUtil.getHighColor((Application) context.getApplicationContext());
                    final PorterDuffColorFilter colorFilter = new PorterDuffColorFilter(stateColor, PorterDuff.Mode.SRC_ATOP);
                    childDrawable.setColorFilter(colorFilter);
                } else if (contactStateBubble.getId(i) == R.id.contact_state_bubble_outter) {
                    final int mainColor = useContrastforOuterColor ? colorUtil.getMainContrastColor((Application) context.getApplicationContext()) : colorUtil.getMainColor((Application) context.getApplicationContext());
                    final PorterDuffColorFilter colorFilter = new PorterDuffColorFilter(mainColor, PorterDuff.Mode.SRC_ATOP);
                    childDrawable.setColorFilter(colorFilter);
                }
            }
        }

        return d;
    }
}
