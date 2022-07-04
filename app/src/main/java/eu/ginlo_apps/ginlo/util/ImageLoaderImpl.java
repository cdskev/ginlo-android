package eu.ginlo_apps.ginlo.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.net.Uri;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;

public class ImageLoaderImpl {

    private static final String TAG = "ImageLoaderImpl";
    private static ImageLoaderImpl instance;
    private SimsMeApplication mApplication;
    private AttachmentController mAttachmentController;
    private DiskCacheStrategy defaultDiskCacheStrategy = DiskCacheStrategy.NONE;

    private ImageLoaderImpl(SimsMeApplication application) {
        this.mApplication = application;
        this.mAttachmentController = mApplication.getAttachmentController();

        // Disable disk cache!
        GlideApp.with(mApplication)
                .applyDefaultRequestOptions(new RequestOptions()
                        .diskCacheStrategy(defaultDiskCacheStrategy));
    }

    /**
     * Make us a Singleton. There must be only one instance of this - instantiated by ImageController.
     */
    public static ImageLoaderImpl getInstance(SimsMeApplication application) {
        if (instance == null) {
            instance = new ImageLoaderImpl(application);
        }
        return instance;
    }

    /**
     * Completely clear Glide's disk cache
     * KS: Still thinking of using one of these instead:
     * .diskCacheStrategy(DiskCacheStrategy.NONE)
     * .skipMemoryCache(true)
     */
    public void clearDiskCache() {
        new Thread(() -> {
            GlideApp.get(mApplication).clearDiskCache();
            }).start();
    }

    public void clearMemoryCache() {
        Activity activity = mApplication.getAppLifecycleController().getTopActivity();
        if(activity != null) {
            activity.runOnUiThread(() -> Glide.get(mApplication).clearMemory());
        }
    }

    /**
     * Fill given view with bitmap image
     */
    @SuppressLint("CheckResult")
    public void fillViewWithBitmapImage(@NonNull Bitmap image, @NonNull ImageView view, int size, boolean noCache) {
        RequestBuilder<Bitmap> requestBuilder = GlideApp.with(mApplication).asBitmap();

        requestBuilder.load(image);

        if(noCache) {
            requestBuilder.skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE);

        }

        if (size != ImageUtil.SIZE_ORIGINAL) {
            requestBuilder.override(size);
        }

        requestBuilder.centerInside()
                .into(view);
    }

    public void fillViewWithImageFromUri(@NonNull Uri imageUri, ImageView view, boolean noCache) {
        fillViewWithImageFromUri(imageUri,view, noCache, false);
    }
    public void fillViewWithImageFromUri(@NonNull Uri imageUri, ImageView view, boolean noCache, boolean animate) {
        if(animate) {
            GlideApp.with(mApplication)
                    .load(imageUri)
                    .skipMemoryCache(noCache)
                    .diskCacheStrategy(noCache ? DiskCacheStrategy.NONE : defaultDiskCacheStrategy)
                    .into(view);
        } else {
            GlideApp.with(mApplication)
                    .asBitmap()
                    .load(imageUri)
                    .skipMemoryCache(noCache)
                    .diskCacheStrategy(noCache ? DiskCacheStrategy.NONE : defaultDiskCacheStrategy)
                    .into(view);
        }
    }

    public void fillViewWithImageFromResource(int resId, ImageView view, boolean noCache) {
        GlideApp.with(mApplication)
                .load(resId)
                .skipMemoryCache(noCache)
                .diskCacheStrategy(noCache ? DiskCacheStrategy.NONE : defaultDiskCacheStrategy)
                .into(view);
    }

    public void fillViewWithSVGFromUri(@NonNull Uri svgUri, ImageView view, boolean noCache) {
        RequestBuilder<PictureDrawable> requestBuilder;

        requestBuilder = GlideApp.with(mApplication)
                .as(PictureDrawable.class)
                .skipMemoryCache(noCache)
                .diskCacheStrategy(noCache ? DiskCacheStrategy.NONE : defaultDiskCacheStrategy)
                .listener(new GinloGlideModule.SvgSoftwareLayerSetter());

        requestBuilder.load(svgUri).into(view);
    }

    /**
     * Fill given view with chat profile image
     */
    @SuppressLint("CheckResult")
    public void fillViewWithProfileImageByGuid(@NonNull String guid, @NonNull ImageView view, int maskResId, boolean noCache) {
        fillViewWithProfileImageByGuid(guid, null, view, maskResId, noCache);
    }

    /**
     * Fill given view with chat profile image
     * Fill with given placeholder if image for guid is not available. Use standard placeholder if
     * placeholder = null.
     */
    @SuppressLint("CheckResult")
    public void fillViewWithProfileImageByGuid(@NonNull String guid, @Nullable Drawable placeholder, @NonNull ImageView view, int maskResId, boolean noCache) {
        RequestBuilder<Bitmap> requestBuilder = GlideApp.with(mApplication)
                .asBitmap()
                .load(guid);

        if (guid.startsWith(AppConstants.GUID_CHANNEL_PREFIX) ||
                guid.startsWith(AppConstants.GUID_SERVICE_PREFIX)) {
            ChannelController channelController = mApplication.getChannelController();
            if(channelController != null) {
                Bitmap bitmap = null;
                try {
                    bitmap = channelController.loadLocalImage(guid, ChannelController.IMAGE_TYPE_PROVIDER_ICON);
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, "fillViewWithProfileImageByGuid: Failed with " + e.getMessage() + ". Use default placeholder");
                    bitmap = BitmapFactory.decodeResource(mApplication.getResources(), R.drawable.gfx_profil_placeholder);
                }
                requestBuilder.load(bitmap);
            }
        } else if (GuidUtil.isSystemChat(guid)) {
            //requestBuilder.load(R.drawable.ginlo_avatar);
            requestBuilder.load(R.mipmap.app_icon);
        } else if (GuidUtil.isProfileUser(guid)) {
            requestBuilder.load(R.drawable.gfx_profil_placeholder);
        } else if (GuidUtil.isProfileGroup(guid)) {
            requestBuilder.load(R.drawable.gfx_group_placeholder);
        } else {
            requestBuilder
                    .load(guid)
                    // Cache handling
                    .skipMemoryCache(noCache)
                    .diskCacheStrategy(noCache ? DiskCacheStrategy.NONE : defaultDiskCacheStrategy);
        }

        if (maskResId != ImageUtil.SIZE_ORIGINAL) {
            Bitmap mask = BitmapFactory.decodeResource(mApplication.getResources(), maskResId);
            if(mask != null) {
                requestBuilder.override(mask.getWidth(), mask.getHeight());
            }
        }

        // Choose the right placeholder
        if (placeholder != null) {
            requestBuilder.placeholder(placeholder);
        } else {
            // The default
            requestBuilder.placeholder(R.drawable.data_placeholder_not_loaded);
        }

        requestBuilder.centerInside()
                      .optionalCenterCrop()
                      .into(view);
    }

    @SuppressLint("CheckResult")
    public void fillViewWithAttachmentImageByGuid(String guid, AttachmentController.AttachmentMapInfo attachmentMapInfo, @Nullable Drawable placeholder, @NonNull ImageView view, int maskResId, boolean noCache) {
        if(guid != null && attachmentMapInfo != null) {
            mAttachmentController.addToAttachmentMap(guid, attachmentMapInfo);
            fillViewWithAttachmentImageByGuid(guid, placeholder, view, maskResId, noCache);
        }
    }

    @SuppressLint("CheckResult")
    private void fillViewWithAttachmentImageByGuid(String guid, @Nullable Drawable placeholder, @NonNull ImageView view, int maskResId, boolean noCache) {
        if(guid != null && mAttachmentController.getFromAttachmentMap(guid) != null) {

            GlideRequest<Drawable> requestBuilder = GlideApp.with(mApplication)
                    .load(guid)
                    // Cache handling
                    .skipMemoryCache(noCache)
                    .diskCacheStrategy(noCache ? DiskCacheStrategy.NONE : defaultDiskCacheStrategy);

            if (maskResId != ImageUtil.SIZE_ORIGINAL) {
                Bitmap mask = BitmapFactory.decodeResource(mApplication.getResources(), maskResId);
                if(mask != null) {
                    requestBuilder.override(mask.getWidth(), mask.getHeight());
                }
            }

            // Choose the right placeholder
            if (placeholder != null) {
                requestBuilder.placeholder(placeholder);
            } else {
                // The default
                requestBuilder.placeholder(R.drawable.data_placeholder_not_loaded);
            }

            requestBuilder.centerInside()
                    .into(view);
        }
    }

}
