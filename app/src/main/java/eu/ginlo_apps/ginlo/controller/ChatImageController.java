// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller;

import android.content.ComponentCallbacks2;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemClock;

import com.google.zxing.Dimension;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.controller.contracts.LowMemoryCallback;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.*;

import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ChatImageController implements LowMemoryCallback {
    
    private static final String TAG = ChatImageController.class.getSimpleName();

    public static final int SIZE_ORIGINAL = -1;
    public static final int SIZE_CHAT = R.drawable.maske_chat;
    public static final int SIZE_CHAT_OVERVIEW = R.drawable.maske_portraet;
    public static final int SIZE_PROFILE_BIG = R.drawable.mask_portraet_big;
    public static final int SIZE_GROUP_INFO_BIG = R.drawable.mask_portraet_big;
    public static final int SIZE_DRAWER = R.drawable.mask_seclevel_about;
    public static final int SIZE_CONTACT = 48;
    public static final int SIZE_CONTACT_GROUP_INFO = 38;
    private static final int MAX_CACHE_SIZE = 10;

    private final File internalFileDir;
    private final File profileImageDir;
    private final File backgroundFile;
    // Cache der Bilder
    private final HashMap<String, Bitmap> bitmapCache;
    // LRU - Liste
    private final List<String> bitmapCacheLRU;
    private final Resources resources;
    private final SimsMeApplication context;
    private final ArrayList<OnImageDataChangedListener> mListeners;
    private Bitmap background;

    private long lastAsyncRefresh = 0;

    public ChatImageController(final SimsMeApplication application) {
        mListeners = new ArrayList<>();

        this.context = application;
        this.internalFileDir = context.getFilesDir();
        this.backgroundFile = new File(internalFileDir, "background.jpg");
        this.resources = context.getResources();
        this.profileImageDir = new File(this.internalFileDir.getAbsolutePath() + "/profileImg");

        if (!profileImageDir.isDirectory()) {
            profileImageDir.mkdirs();
        }

        this.bitmapCache = new HashMap<>();
        this.bitmapCacheLRU = new LinkedList<>();

        context.getAppLifecycleController().registerLowMemoryCallback(this);
    }

    public static Dimension getDimensionForSize(Resources resources,
                                                int size) {
        Drawable drawable = resources.getDrawable(size);
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();

        return new Dimension(width, height);
    }

    /**
     * Löscht alle Bilder vom Filesystem.
     */
    public void resetChatImages() {
        synchronized (this) {
            String[] profileImages = profileImageDir.list();

            for (int i = 0; i < profileImages.length; i++) {
                File image = new File(profileImageDir, profileImages[i]);

                if (!image.exists()) {
                    continue;
                }
                if (image.isDirectory()) {
                    continue;
                }
                if (image.isHidden()) {
                    continue;
                }
                image.delete();
            }
        }
    }

    public void deleteImage(String guid) {
        File file = new File(profileImageDir, guid);

        if (file.exists()) {
            file.delete();
        }
        removeFromCache(guid);
        notifyListener(guid);
    }

    public void resetBackground() {
        background = null;
        if (backgroundFile.exists()) {
            backgroundFile.delete();
        }
    }

    public boolean isBackgroundSet() {
        return background != null;
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public Bitmap getBackground()
            throws LocalizedException {
        if (background == null) {
            loadBackground();
        }

        return background;
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public void setBackground(Bitmap bitmap)
            throws LocalizedException {
        background = bitmap;
        saveBackground(bitmap);
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public void saveImage(String guid,
                          byte[] bitmapData)
            throws LocalizedException {
        ByteArrayInputStream byteInputStream = null;
        FileOutputStream fileOutputStream = null;

        try {
            eu.ginlo_apps.ginlo.controller.KeyController keyController = context.getKeyController();
            IvParameterSpec iv = SecurityUtil.generateIV();

            byte[] ivBytes = iv.getIV();
            byte[] encryptedImageData = SecurityUtil.encryptMessageWithAES(bitmapData,
                    keyController
                            .getInternalEncryptionKey(), iv);

            File file = new File(profileImageDir, guid);

            if (file.exists()) {
                file.delete();
            }

            byteInputStream = new ByteArrayInputStream(encryptedImageData);
            fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(ivBytes);

            StreamUtil.copyStreams(byteInputStream, fileOutputStream);

            LogUtil.i(TAG, "Save Image " + guid + " size:" + file.length());
        } catch (IOException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            throw new LocalizedException(LocalizedException.SAVE_IMAGE_FAILED, e);
        } finally {
            StreamUtil.closeStream(byteInputStream);
            StreamUtil.closeStream(fileOutputStream);
        }
        removeFromCache(guid);
        notifyListener(guid);
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public Bitmap getImageByGuidWithoutCacheing(String guid,
                                                int width,
                                                int height)
            throws LocalizedException {
        Bitmap unscaledBitmap = getImageInOriginalSize(guid);

        if (unscaledBitmap == null) {
            return null;
        }

        return Bitmap.createScaledBitmap(unscaledBitmap, width, height, false);
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    private Bitmap getImageInOriginalSize(final String guid)
            throws LocalizedException {
        Bitmap bitmap = null;

        if (guid.equals(AppConstants.GUID_SYSTEM_CHAT) || guid.equals(AppConstants.GUID_PROFILE_USER)
                || guid.equals(AppConstants.GUID_PROFILE_GROUP)) {
            bitmap = getSpecialUserBitmap(guid);
        } else if (guid.startsWith(AppConstants.GUID_CHANNEL_PREFIX) || guid.startsWith(AppConstants.GUID_SERVICE_PREFIX)) {
            eu.ginlo_apps.ginlo.controller.ChannelController channelController = context.getChannelController();
            bitmap = channelController.loadLocalImage(guid, ChannelController.IMAGE_TYPE_PROVIDER_ICON);
        } else {
            try {
                byte[] imageBytes = loadImage(guid);

                bitmap = BitmapUtil.decodeByteArray(imageBytes);
            } catch (LocalizedException le) {
                if (!LocalizedException.LOAD_IMAGE_FAILED.equals(le.getIdentifier())) {
                    throw le;
                }
            }

            if (bitmap == null) {

                // KS: Update info from server - start async task ...
                // This is not the right place for that. Since method is called by getImageByGuid parents solely, it is ok for now!
                // TODO: Temporarily throttle this (10 seconds) - finally the status for each guid should be kept to prevent unnecessary requests
                ContactController contactController = context.getContactController();
                if(System.currentTimeMillis() > (lastAsyncRefresh + 10000)) {
                    lastAsyncRefresh = System.currentTimeMillis();
                    LogUtil.i(TAG, "getImageInOriginalSize: Initialize async to get profile data from server for " + guid);
                    contactController.updateContactProfileInfosFromServer(guid);
                }

                LogUtil.i(TAG, "getImageInOriginalSize Load fallback image for " + guid);

                // Fallback des Contact-Image Fallbacks sollte die Farbe sein ...
                bitmap = contactController.getFallbackImageByGuid(context, guid, 0);
                if (bitmap != null) {
                    LogUtil.i(TAG, "getImageInOriginalSize Got Fallback Image for " + guid);
                }
            }
        }

        return bitmap;
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public Bitmap getImageByGuidWithoutCacheing(String guid,
                                                int size)
            throws LocalizedException {
        Bitmap returnBitmap = getImageInOriginalSize(guid);

        if ((returnBitmap != null) && (size != SIZE_ORIGINAL)) {
            return getScaledImage(returnBitmap, size);
        }

        if (returnBitmap == null) {
            if ((guid != null) && guid.startsWith(AppConstants.GUID_GROUP_PREFIX)) {
                returnBitmap = getImageByGuidWithoutCacheing(AppConstants.GUID_PROFILE_GROUP, size);
            } else {
                returnBitmap = getImageByGuidWithoutCacheing(AppConstants.GUID_PROFILE_USER, size);
            }
        }

        return returnBitmap;
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public Bitmap getImageByGuid(String guid,
                                 int size)
            throws LocalizedException {
        Bitmap returnBitmap = null;

        synchronized (bitmapCache) {
            if (guid != null) {
                if (size != SIZE_ORIGINAL) {
                    String key = guid + size;

                    if (!bitmapCache.containsKey(key)) {
                        Bitmap unscaledImage = getImageInOriginalSize(guid);
                        if (unscaledImage != null) {
                            Bitmap scaledImage = getScaledImage(unscaledImage, size);

                            if (scaledImage != null) {
                                returnBitmap = scaledImage;
                                addToCache(key, scaledImage);
                            }
                        }
                    } else {
                        returnBitmap = getFromCache(key);
                    }
                } else {
                    if (bitmapCache.containsKey(guid)) {
                        returnBitmap = getFromCache(guid);
                    } else {
                        returnBitmap = getImageInOriginalSize(guid);
                        if (returnBitmap != null) {
                            addToCache(guid, returnBitmap);
                        }
                    }
                }
            } else {
                LogUtil.i(TAG, "guid is null!");
            }

            if (returnBitmap == null) {
                if (!StringUtil.isNullOrEmpty(guid) && guid.startsWith(AppConstants.GUID_GROUP_PREFIX)) {
                    returnBitmap = getImageByGuid(AppConstants.GUID_PROFILE_GROUP, size);
                } else {
                    if (!StringUtil.isNullOrEmpty(guid) && guid.startsWith(AppConstants.GUID_CHANNEL_PREFIX)) {
                        //Icon fuer Channel nicht da, im Hintergrund laden
                        context.getChannelChatController().loadChannelChatIconAsync(guid);
                    }
                    returnBitmap = getImageByGuid(AppConstants.GUID_PROFILE_USER, size);
                }
            }
        }

        return returnBitmap;
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    private void loadBackground()
            throws LocalizedException {
        if (!backgroundFile.exists()) {
            return;
        }

        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;

        try {
            inputStream = new BufferedInputStream(new FileInputStream(backgroundFile));
            outputStream = new ByteArrayOutputStream();

            StreamUtil.copyStreams(inputStream, outputStream);

            background = BitmapUtil.decodeByteArray(outputStream.toByteArray());
        } catch (IOException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            throw new LocalizedException(LocalizedException.LOAD_BACKGROUND_FAILED, e);
        } finally {
            StreamUtil.closeStream(inputStream);
            StreamUtil.closeStream(outputStream);
        }
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    private void saveBackground(Bitmap bitmap)
            throws LocalizedException {
        if (bitmap == null) {
            throw new LocalizedException(LocalizedException.SAVE_BACKGROUND_FAILED);
        }

        byte[] data = BitmapUtil.compress(bitmap, 100);
        FileOutputStream fileOutputStream = null;

        try {
            fileOutputStream = new FileOutputStream(backgroundFile);
            fileOutputStream.write(data);
        } catch (IOException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            throw new LocalizedException(LocalizedException.SAVE_BACKGROUND_FAILED, e);
        } finally {
            StreamUtil.closeStream(fileOutputStream);
        }
    }

    private void addToCache(String guid,
                            Bitmap bitmap) {
        synchronized (bitmapCache) {
            if (bitmapCache.size() >= MAX_CACHE_SIZE) {
                String oldestEntry = bitmapCacheLRU.remove(0);

                bitmapCache.remove(oldestEntry);
            }
            bitmapCache.put(guid, bitmap);
            bitmapCacheLRU.add(guid);
        }
    }

    private Bitmap getFromCache(String guid) {
        synchronized (bitmapCache) {
            if (bitmapCache.containsKey(guid)) {
                bitmapCacheLRU.remove(guid);
                bitmapCacheLRU.add(guid);
                return bitmapCache.get(guid);
            }
            return null;
        }
    }

    public Bitmap getScaledImage(Bitmap unscaledBitmap,
                                 int size) {
        if (unscaledBitmap == null) {
            return null;
        }

        int width;
        int height;

        Dimension dimension = getDimensionForSize(resources, size);

        width = dimension.getWidth();
        height = dimension.getHeight();

        return Bitmap.createScaledBitmap(unscaledBitmap, width, height, false);
    }

    public void removeFromCache(String guid) {
        List<String> removeKeys = new ArrayList<>();

        synchronized (bitmapCache) {
            for (String key : bitmapCache.keySet()) {
                if (key.contains(guid)) {
                    removeKeys.add(key);
                }
            }
            for (String removeKey : removeKeys) {
                bitmapCache.remove(removeKey);
            }
        }

        context.getChatOverviewController().chatChanged(null, null, null, ChatOverviewController.CHAT_CHANGED_IMAGE);
    }

    private Bitmap getSpecialUserBitmap(String guid) {
        final int id;

        switch (guid) {
            case AppConstants.GUID_PROFILE_USER:
                id = R.drawable.gfx_profil_placeholder;
                break;
            case AppConstants.GUID_PROFILE_GROUP:
                id = R.drawable.gfx_group_placeholder;
                break;
            case AppConstants.GUID_SYSTEM_CHAT:
                id = R.drawable.ginlo_avatar;
                break;
            default:
                id = 0;
                break;
        }
        return BitmapFactory.decodeResource(resources, id);
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public byte[] loadImage(String guid)
            throws LocalizedException {
        byte[] imageBytes = new byte[0];

        FileInputStream fileInputStream = null;

        try {
            KeyController keyController = context.getKeyController();
            File file = new File(profileImageDir, guid);

            if (!file.exists() || file.length() < (SecurityUtil.IV_LENGTH / 8)) {
                return imageBytes;
            }

            int fileSize = new BigDecimal(file.length()).intValueExact();

            LogUtil.i(TAG, "Load Image " + guid + " size:" + file.length());

            byte[] ivBytes = new byte[SecurityUtil.IV_LENGTH / 8];
            byte[] encryptedImageData = new byte[fileSize - ivBytes.length];

            fileInputStream = new FileInputStream(file);
            StreamUtil.safeRead(fileInputStream, ivBytes, ivBytes.length);
            StreamUtil.safeRead(fileInputStream, encryptedImageData, encryptedImageData.length);

            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            imageBytes = SecurityUtil.decryptMessageWithAES(encryptedImageData, keyController.getInternalEncryptionKey(),
                    iv);
        } catch (IOException | LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            throw new LocalizedException(LocalizedException.LOAD_IMAGE_FAILED, e);
        } finally {
            StreamUtil.closeStream(fileInputStream);
        }

        return imageBytes;
    }

    public void addListener(OnImageDataChangedListener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    private void notifyListener(final String guid) {
        Handler handler = new Handler(context.getMainLooper());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                for (OnImageDataChangedListener listener : mListeners) {
                    listener.onImageDataChanged(guid);
                }
            }
        };
        handler.post(runnable);
    }

    @Override
    public void onLowMemory(int state) {
        if ((state == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
                || (state == ComponentCallbacks2.TRIM_MEMORY_COMPLETE)) {
            clearChatImageCache();
        }
    }

    /**
     * clearChatImageCache
     */
    public void clearChatImageCache() {
        if (bitmapCache != null) {
            bitmapCache.clear();
        }

        if (bitmapCacheLRU != null) {
            bitmapCacheLRU.clear();
        }

        context.getChatOverviewController().chatChanged(null, null, null, ChatOverviewController.CHAT_CHANGED_IMAGE);
    }
}
