// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.collection.LruCache;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import eu.ginlo_apps.ginlo.log.LogUtil;

/**
 * This class holds our bitmap caches (memory and disk).
 */
public class ImageCache {

    private static final String TAG = "ImageCache";

    private LruCache<String, Bitmap> mMemoryCache;

    /**
     * Creating a new ImageCache object using the specified parameters.
     *
     * @param memCacheSizePercent The cache size as a percent of available app
     *                            memory.
     */
    private ImageCache(float memCacheSizePercent) {
        init(memCacheSizePercent);
    }

    /**
     * Find and return an existing ImageCache stored in a {@link RetainFragment},
     * if not found a new one is created using the supplied params and saved to a
     * {@link RetainFragment}.
     *
     * @param fragmentManager     The fragment manager to use when dealing with
     *                            the retained fragment.
     * @param memCacheSizePercent The cache size as a percent of available app
     *                            memory.
     * @return An existing retained ImageCache object or a new one if one did not
     * exist
     */
    public static ImageCache getInstance(FragmentManager fragmentManager,
                                         float memCacheSizePercent) {
        // Search for, or create an instance of the non-UI RetainFragment
        final RetainFragment mRetainFragment = findOrCreateRetainFragment(fragmentManager);

        // See if we already have an ImageCache stored in RetainFragment
        ImageCache imageCache = (ImageCache) mRetainFragment.getObject();

        // No existing ImageCache, create one and store it in RetainFragment
        if (imageCache == null) {
            imageCache = new ImageCache(memCacheSizePercent);
            mRetainFragment.setObject(imageCache);
        }

        return imageCache;
    }

    /**
     * deleteImageCache
     *
     * @param fragmentManager fragmentManager
     */
    public static void deleteImageCache(FragmentManager fragmentManager) {
        try {
            if (fragmentManager != null) {
                RetainFragment mRetainFragment = (RetainFragment) fragmentManager.findFragmentByTag(TAG);

                if (mRetainFragment != null) {
                    Object o = mRetainFragment.getObject();

                    if (o != null) {
                        ((ImageCache) o).mMemoryCache.evictAll();
                    }

                    fragmentManager.beginTransaction().remove(mRetainFragment).commitAllowingStateLoss();
                }
            }
        } catch (Exception e) {
            LogUtil.e(ImageCache.class.getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * Get the size in bytes of a bitmap.
     *
     * @param bitmap The bitmap to calculate the size of.
     * @return size of bitmap in bytes.
     */
    @TargetApi(12)
    private static int getBitmapSize(Bitmap bitmap) {
        return bitmap.getByteCount();
    }

    /**
     * Calculates the memory cache size based on a percentage of the max
     * available VM memory. Eg. setting percent to 0.2 would set the memory cache
     * to one fifth of the available memory. Throws {@link
     * IllegalArgumentException} if percent is < 0.05 or > .8. memCacheSize is
     * stored in kilobytes instead of bytes as this will eventually be passed to
     * construct a LruCache which takes an int in its constructor. This value
     * should be chosen carefully based on a number of factors Refer to the
     * corresponding Android Training class for more discussion:
     * http://developer.android.com/training/displaying-bitmaps/
     *
     * @param percent Percent of available app memory to use to size memory
     *                cache.
     * @throws IllegalArgumentException [!EXC_DESCRIPTION!]
     */
    private static int calculateMemCacheSize(float percent) {
        if ((percent < 0.05f) || (percent > 0.8f)) {
            throw new IllegalArgumentException("setMemCacheSizePercent - percent must be "
                    + "between 0.05 and 0.8 (inclusive)");
        }
        return Math.round(percent * Runtime.getRuntime().maxMemory() / 1024);
    }

    /**
     * Locate an existing instance of this Fragment or if not found, create and
     * add it using FragmentManager.
     *
     * @param fm The FragmentManager manager to use.
     * @return The existing instance of the Fragment or the new instance if just
     * created.
     */
    private static RetainFragment findOrCreateRetainFragment(FragmentManager fm) {
        // Check to see if we have retained the worker fragment.
        RetainFragment mRetainFragment = (RetainFragment) fm.findFragmentByTag(TAG);

        // If not retained (or first time running), we need to create and add it.
        if (mRetainFragment == null) {
            mRetainFragment = new RetainFragment();
            fm.beginTransaction().add(mRetainFragment, TAG).commitAllowingStateLoss();
        }

        return mRetainFragment;
    }

    /**
     * Initialize the cache.
     *
     * @param memCacheSizePercent The cache size as a percent of available app
     *                            memory.
     */
    private void init(float memCacheSizePercent) {
        int memCacheSize = calculateMemCacheSize(memCacheSizePercent);

        // Set up memory cache

        mMemoryCache = new LruCache<String, Bitmap>(memCacheSize) {
            /**
             * Measure item size in kilobytes rather than units which is more
             * practical for a bitmap cache
             *
             *
             */
            @Override
            protected int sizeOf(String key,
                                 Bitmap bitmap) {
                final int bitmapSize = getBitmapSize(bitmap) / 1024;

                return (bitmapSize == 0) ? 1 : bitmapSize;
            }
        };
    }

    /**
     * Adds a bitmap to both memory and disk cache.
     *
     * @param data   Unique identifier for the bitmap to store
     * @param bitmap The bitmap to store
     */
    public void addBitmapToCache(String data,
                                 Bitmap bitmap) {
        if ((data == null) || (bitmap == null)) {
            return;
        }

        // Add to memory cache
        if ((mMemoryCache != null) && (mMemoryCache.get(data) == null)) {
            mMemoryCache.put(data, bitmap);
        }
    }

    /**
     * Remove a bitmap from cache.
     *
     * @param data Unique identifier for the bitmap to remove
     */
    public void removeBitmapFromCache(String data) {
        if (data == null) {
            return;
        }

        // remove from memory cache
        if (mMemoryCache != null) {
            mMemoryCache.remove(data);
        }
    }

    /**
     * Get from memory cache.
     *
     * @param data Unique identifier for which item to get
     * @return The bitmap if found in cache, null otherwise
     */
    public Bitmap getBitmapFromMemCache(String data) {
        if (mMemoryCache != null) {
            final Bitmap memBitmap = mMemoryCache.get(data);

            if (memBitmap != null) {
                return memBitmap;
            }
        }
        return null;
    }

    /**
     * A simple non-UI Fragment that stores a single Object and is retained over
     * configuration changes. It will be used to retain the ImageCache object.
     */
    public static class RetainFragment
            extends Fragment {

        private Object mObject;

        /**
         * Empty constructor as per the Fragment documentation
         */
        public RetainFragment() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Make sure this Fragment is retained over a configuration change
            setRetainInstance(true);
        }

        /**
         * Get the stored object.
         *
         * @return The stored object
         */
        Object getObject() {
            return mObject;
        }

        /**
         * Store a single object in this Fragment.
         *
         * @param object The object to store
         */
        void setObject(Object object) {
            mObject = object;
        }
    }
}
