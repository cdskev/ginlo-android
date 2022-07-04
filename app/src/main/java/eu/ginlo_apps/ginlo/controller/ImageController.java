package eu.ginlo_apps.ginlo.controller;

import android.content.ComponentCallbacks2;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;

import javax.crypto.spec.IvParameterSpec;

import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.contracts.LowMemoryCallback;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.ImageLoaderImpl;
import eu.ginlo_apps.ginlo.util.ImageUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StorageUtil;
import eu.ginlo_apps.ginlo.util.StreamUtil;

public class ImageController implements LowMemoryCallback {

    private static final String TAG = ImageController.class.getSimpleName();

    private final Resources mResources;
    private final SimsMeApplication mApplication;
    private final ImageLoaderImpl mImageLoader;
    private final StorageUtil mStorageUtil;
    private final ArrayList<OnImageDataChangedListener> mListeners;

    private final File mInternalFilesDir;
    private final File mProfileImageDir;
    private File mBackgroundFile;
    private Bitmap mBackgroundBmp;
    private long lastAsyncRefresh = 0L;
    private final ArrayList<String> mChangedImageGuids;

    public ImageController(final SimsMeApplication application) {
        this.mListeners = new ArrayList<>();
        this.mApplication = application;
        this.mResources = mApplication.getResources();

        this.mImageLoader = ImageLoaderImpl.getInstance(mApplication);
        this.mStorageUtil = new StorageUtil(mApplication);

        this.mInternalFilesDir = mStorageUtil.getInternalFilesDir();
        this.mBackgroundFile = mStorageUtil.getBackgroundImageFile();
        this.mProfileImageDir = mStorageUtil.getProfileImageDir();
        this.mChangedImageGuids = new ArrayList<>();

        mApplication.getAppLifecycleController().registerLowMemoryCallback(this);

    }

    public void clearImageCaches(boolean clearMem, boolean clearDisk) {
        if(clearDisk) {
            mImageLoader.clearDiskCache();
        }
        if(clearMem) {
            mImageLoader.clearMemoryCache();
        }
    }

    public void clearChannelImageCaches() {
        // Called by ChannelChatController only.
        // Do nothing for now.
    }

    /**
     * Clear profile image folder
     */
    public void resetProfileImages() {
        // Normally this should not be necessary
        mStorageUtil.clearProfileImageDir();

        clearImageCaches(true, true);
    }

    /**
     * Delete a profile image for a given Guid
     * @param guid
     */
    public void deleteProfileImage(String guid) {
        FileUtil.deleteFile(new File (mProfileImageDir, guid));
        markChangedImageForGuid(guid);
        //clearImageCaches(true, true);
        notifyListeners(guid);
    }

    /**
     * This is deprecated and should be replaced by markChangedImageForGuid().
     * @param guid
     */
    @Deprecated
    public void updateProfileImageInCache(String guid) {
        /*
        clearImageCaches(true, true);
        notifyListeners(guid);
         */

        markChangedImageForGuid(guid);
    }

    /**
     * Mark a profile image change to update cache entry
     * @param guid
     */
    public void markChangedImageForGuid(String guid) {
        synchronized (mChangedImageGuids) {
            mChangedImageGuids.add(guid);
            notifyListeners(guid);
        }
    }

    public boolean haveChangedImageForGuid(String guid) {
        boolean result = false;
        synchronized (mChangedImageGuids) {
            if(mChangedImageGuids.contains(guid)) {
                result = true;
                mChangedImageGuids.remove(guid);
            }
        }
        return result;
    }

    // Keep it unchanged for now.
    public byte[] loadProfileImageRaw(String guid)
            throws LocalizedException {
        byte[] imageBytes = new byte[0];

        if (guid != null) {
            FileInputStream fileInputStream = null;

            try {
                KeyController keyController = mApplication.getKeyController();
                File file = new File(mProfileImageDir, guid);

                if (!file.exists() || file.length() < (SecurityUtil.IV_LENGTH / 8)) {
                    // This will fail, but we trigger the image reload for the next round.
                    triggerReloadOfProfileImage(guid);
                    return imageBytes;
                }

                int fileSize = new BigDecimal(file.length()).intValueExact();

                LogUtil.d(TAG, "loadProfileImageRaw: " + guid + " size:" + file.length());

                byte[] ivBytes = new byte[SecurityUtil.IV_LENGTH / 8];
                byte[] encryptedImageData = new byte[fileSize - ivBytes.length];

                fileInputStream = new FileInputStream(file);
                StreamUtil.safeRead(fileInputStream, ivBytes, ivBytes.length);
                StreamUtil.safeRead(fileInputStream, encryptedImageData, encryptedImageData.length);

                IvParameterSpec iv = new IvParameterSpec(ivBytes);

                imageBytes = SecurityUtil.decryptMessageWithAES(encryptedImageData, keyController.getInternalEncryptionKey(),
                        iv);
            } catch (IOException | LocalizedException e) {
                LogUtil.e(TAG, "loadProfileImageRaw: Failed with " + e.getMessage());
                throw new LocalizedException(LocalizedException.LOAD_IMAGE_FAILED, e);
            } finally {
                StreamUtil.closeStream(fileInputStream);
            }
        }

        return imageBytes;
    }

    // Trigger reload of profile image from server
    // TODO: Temporarily throttle this (300 seconds) - finally the status for each guid should be kept to prevent unnecessary requests
    private void triggerReloadOfProfileImage(String guid) {
        if (guid != null) {
            ContactController contactController = mApplication.getContactController();
            //if(System.currentTimeMillis() > (lastAsyncRefresh + 300000)) {
                lastAsyncRefresh = System.currentTimeMillis();
                LogUtil.i(TAG, "triggerReloadOfProfileImage: Initialize async to get profile data from server for " + guid);
                contactController.updateContactProfileInfosFromServer(guid);
            //}
        }
    }

    // Keep it unchanged for now.
    public void saveProfileImageRaw(String guid, byte[] bitmapData)
            throws LocalizedException {

        if (guid != null && bitmapData != null) {
            ByteArrayInputStream byteInputStream = null;
            FileOutputStream fileOutputStream = null;

            try {
                KeyController keyController = mApplication.getKeyController();
                IvParameterSpec iv = SecurityUtil.generateIV();

                byte[] ivBytes = iv.getIV();
                byte[] encryptedImageData = SecurityUtil.encryptMessageWithAES(bitmapData,
                        keyController.getInternalEncryptionKey(), iv);

                File file = new File(mProfileImageDir, guid);

                if (file.exists()) {
                    file.delete();
                }

                byteInputStream = new ByteArrayInputStream(encryptedImageData);
                fileOutputStream = new FileOutputStream(file);
                fileOutputStream.write(ivBytes);

                StreamUtil.copyStreams(byteInputStream, fileOutputStream);

                LogUtil.d(TAG, "saveProfileImageRaw: " + guid + " size:" + file.length());
            } catch (IOException e) {
                LogUtil.e(TAG, "saveProfileImageRaw: Failed with " + e.getMessage());
                throw new LocalizedException(LocalizedException.SAVE_IMAGE_FAILED, e);
            } finally {
                StreamUtil.closeStream(byteInputStream);
                StreamUtil.closeStream(fileOutputStream);
            }
            markChangedImageForGuid(guid);
        }
    }

    public void fillViewWithProfileImageByGuidOrOverride(String guid, Bitmap override, ImageView view, int maskResId, boolean noCache) {
        if (view != null) {
            if (override != null) {
                mImageLoader.fillViewWithBitmapImage(override, view, maskResId, noCache);
            } else if (guid != null) {
                boolean imageChanged = haveChangedImageForGuid(guid);
                mImageLoader.fillViewWithProfileImageByGuid(guid, view, maskResId, imageChanged || noCache);
            }
        }
    }

    public void fillViewWithProfileImageByGuid(String guid, ImageView view, int maskResId, boolean noCache) {
        if (guid != null && view != null) {
            boolean imageChanged = haveChangedImageForGuid(guid);
            mImageLoader.fillViewWithProfileImageByGuid(guid, view, maskResId, imageChanged || noCache);
        }
    }

    public void fillViewWithAttachmentImageByGuid(String guid, AttachmentController.AttachmentMapInfo attachmentMapInfo, Drawable placeholder, ImageView view, int maskResId, boolean noCache) {
        if (guid != null && view != null && attachmentMapInfo != null) {
            mImageLoader.fillViewWithAttachmentImageByGuid(guid, attachmentMapInfo, placeholder, view, maskResId, noCache);
        }
    }

    public void fillViewWithImageFromUri(String imageUriString, ImageView view, boolean noCache) {
        if(imageUriString != null && view != null) {
            fillViewWithImageFromUri(Uri.parse(imageUriString), view, noCache, false);
        }
    }

    public void fillViewWithImageFromUri(Uri imageUri, ImageView view, boolean noCache, boolean animate) {
        if(imageUri != null && view != null) {
            mImageLoader.fillViewWithImageFromUri(imageUri, view, noCache, animate);
        }
    }

    public void fillViewWithImageFromResource(int resId, ImageView view, boolean noCache) {
        if(view != null) {
            mImageLoader.fillViewWithImageFromResource(resId, view, noCache);
        }
    }

    public void fillViewWithSVGFromUri(Uri imageUri, ImageView view, boolean noCache) {
        if(imageUri != null && view != null) {
            mImageLoader.fillViewWithSVGFromUri(imageUri, view, noCache);
        }
    }


    // Dealing with app background images

    /**
     * Reset app background image and persist deletion
     */
    public void resetAppBackground() {
        mBackgroundBmp = null;
        FileUtil.deleteFile(mBackgroundFile);
    }

    public boolean isAppBackgroundSet() {
        return mBackgroundBmp != null;
    }

    /**
     * Load app background image from preset background image file, if available
     * @return
     */
    public Bitmap getAppBackground() {
        if (mBackgroundBmp == null) {
            if(mBackgroundFile.exists()) {
                InputStream inputStream = null;
                ByteArrayOutputStream outputStream = null;

                try {
                    inputStream = new BufferedInputStream(new FileInputStream(mBackgroundFile));
                    outputStream = new ByteArrayOutputStream();

                    StreamUtil.copyStreams(inputStream, outputStream);

                    mBackgroundBmp = ImageUtil.decodeByteArray(outputStream.toByteArray());
                } catch (IOException e) {
                    LogUtil.e(TAG, "getBackground: Failed with " + e.getMessage());
                } finally {
                    StreamUtil.closeStream(inputStream);
                    StreamUtil.closeStream(outputStream);
                }
            }
        }

        return mBackgroundBmp;
    }

    /**
     * Make current background image persistent, save it to preset background image file
     * @param bitmap
     */
    public void setAppBackground(Bitmap bitmap) {
        if (bitmap == null) {
            LogUtil.e(TAG, "setBackground: Failed with bitmap = null.");
            return;
        }

        mBackgroundBmp = bitmap;

        byte[] data = ImageUtil.compress(mBackgroundBmp, 100);
        FileOutputStream fileOutputStream = null;

        try {
            fileOutputStream = new FileOutputStream(mBackgroundFile);
            fileOutputStream.write(data);
        } catch (IOException e) {
            LogUtil.e(TAG, "setBackground: Failed with " + e.getMessage());
        } finally {
            StreamUtil.closeStream(fileOutputStream);
        }
    }


    // Interfaces and listeners

    public interface OnImageDataChangedListener {
        void onImageDataChanged(String guid);
    }

    public void addListener(OnImageDataChangedListener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    private void notifyListeners(final String guid) {
        Handler handler = new Handler(mApplication.getMainLooper());
        Runnable runnable = () -> {
            for (OnImageDataChangedListener listener : mListeners) {
                listener.onImageDataChanged(guid);
            }
        };
        handler.post(runnable);
    }


    // Interface stuff

    @Override
    public void onLowMemory(int state) {
        if ((state == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
                || (state == ComponentCallbacks2.TRIM_MEMORY_COMPLETE)) {
            clearImageCaches(true, false);
        }
    }

}
