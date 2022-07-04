package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Picture;
import android.graphics.drawable.PictureDrawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Priority;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.load.resource.SimpleResource;
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.ImageViewTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;

@GlideModule
public final class GinloGlideModule extends AppGlideModule {
    private static final String TAG = GinloGlideModule.class.getSimpleName();
    private final Context mContext;
    private final KeyController mKeyController;
    private final AttachmentController mAttachmentController;
    private final StorageUtil mStorageUtil;

    public GinloGlideModule(Context context) {
        this.mContext = context;
        this.mKeyController = ((SimsMeApplication) context.getApplicationContext()).getKeyController();
        this.mAttachmentController = ((SimsMeApplication) context.getApplicationContext()).getAttachmentController();
        this.mStorageUtil = new StorageUtil(mContext);
    }

    /**
     * Register customized loaders to Glide
     * @param context
     * @param glide
     * @param registry
     */
    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, Registry registry) {
        registry.prepend(String.class, ByteBuffer.class, new EncryptedImageModelLoaderFactory());
        registry.register(SVG.class, PictureDrawable.class, new SvgDrawableTranscoder())
                .append(InputStream.class, SVG.class, new SvgDecoder());

    }

    @Override
    public boolean isManifestParsingEnabled() {
        return super.isManifestParsingEnabled();
    }

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        super.applyOptions(context, builder);
    }


    //
    // Inner classes for encrypted attachment image handling
    //

    /**
     *
     */
    public final class EncryptedImageModelLoader implements ModelLoader<String, ByteBuffer> {

        @Nullable
        @Override
        public LoadData<ByteBuffer> buildLoadData(@NonNull String model, int width, int height, @NonNull Options options) {
            return new LoadData<>(new ObjectKey(model), new EncryptedImageDataFetcher(model));        }

        @Override
        public boolean handles(String model) {
            return model.contains(":{") && model.endsWith("}");
        }
    }


    public class EncryptedImageDataFetcher implements DataFetcher<ByteBuffer> {

        private final String guid;

        EncryptedImageDataFetcher(String model) {
            this.guid = model;
        }

        @Override
        public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super ByteBuffer> callback) {

            byte[] imageBytes = new byte[0];
            boolean workOnProfileImage = false;
            FileInputStream fileInputStream = null;

            try {
                File file;
                if(guid.startsWith("6:{") || guid.startsWith("3000:{")) {
                    // Regular attachment
                    final AttachmentController.AttachmentMapInfo attachmentMapInfo = mAttachmentController.getFromAttachmentMap(guid);
                    if(attachmentMapInfo != null) {
                        file = new File(mStorageUtil.getAttachmentDir(), guid);
                        SecretKey aesKey = attachmentMapInfo.getAesKeyDataContainer().getAesKey();
                        IvParameterSpec iv;

                        if (file.exists() && file.length() >= (SecurityUtil.IV_LENGTH / 8)) {
                            int fileSize = new BigDecimal(file.length()).intValueExact();
                            byte[] encryptedImageData;

                            LogUtil.i(TAG, "loadData: Load encrypted attachment " + guid + " with size = " + file.length());

                            fileInputStream = new FileInputStream(file);

                            if(attachmentMapInfo.getChatType() == Message.TYPE_GROUP) {
                                // iv is part of the attachment file
                                byte[] ivBytes = new byte[SecurityUtil.IV_LENGTH / 8];
                                encryptedImageData = new byte[fileSize - ivBytes.length];
                                iv = new IvParameterSpec(ivBytes);
                                StreamUtil.safeRead(fileInputStream, ivBytes, ivBytes.length);
                            } else {
                                encryptedImageData = new byte[fileSize];
                                iv = attachmentMapInfo.getAesKeyDataContainer().getIv();
                            }

                            StreamUtil.safeRead(fileInputStream, encryptedImageData, encryptedImageData.length);
                            imageBytes = SecurityUtil.decryptMessageWithAES(encryptedImageData, aesKey, iv);

                            LogUtil.d(TAG, "loadData: Got decrypted attachment image bytes with size = " + imageBytes.length);

                        }
                    }
                } else {
                    // Profile image
                    workOnProfileImage = true;
                    file = new File(mStorageUtil.getProfileImageDir(), guid);

                    if (file.exists() && file.length() >= (SecurityUtil.IV_LENGTH / 8)) {
                        int fileSize = new BigDecimal(file.length()).intValueExact();

                        LogUtil.i(TAG, "loadData: Load profile image " + guid + " size:" + file.length());

                        byte[] ivBytes = new byte[SecurityUtil.IV_LENGTH / 8];
                        byte[] encryptedImageData = new byte[fileSize - ivBytes.length];

                        fileInputStream = new FileInputStream(file);
                        StreamUtil.safeRead(fileInputStream, ivBytes, ivBytes.length);
                        StreamUtil.safeRead(fileInputStream, encryptedImageData, encryptedImageData.length);

                        IvParameterSpec iv = new IvParameterSpec(ivBytes);

                        imageBytes = SecurityUtil.decryptMessageWithAES(encryptedImageData,
                                mKeyController.getInternalEncryptionKey(), iv);
                    }
                }
            } catch (IOException | LocalizedException e) {
                LogUtil.e(TAG, "loadData: Failed with " + e.getMessage());
            } finally {
                StreamUtil.closeStream(fileInputStream);
            }

            if(imageBytes.length != 0) {
                // Despite of @Nullable annotation in Glide code byteBuffer *must not* be null!
                callback.onDataReady(ByteBuffer.wrap(imageBytes));
            } else if (workOnProfileImage){
                // We use a fallback strategy for profile images instead of failing ...
                //callback.onLoadFailed(new LocalizedException(LocalizedException.FILE_NOT_FOUND));
                LogUtil.i(TAG, "loadData: No profile image - use fallback for " + guid);
                String name = null;
                // KS: Don't want instance of ContactController in this class. Keep it for now.
                ContactController cc = ((SimsMeApplication) mContext.getApplicationContext()).getContactController();
                if(cc != null) {
                    try {
                        if(cc.getContactByGuid(guid) != null) {
                            name = cc.getContactByGuid(guid).getName();
                        }
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, "loadData: Could not get fallback contact name: " + e.getMessage());
                    }
                }
                Bitmap profileFallback = ImageUtil.createFallbackProfileImage(mContext, name);
                imageBytes = ImageUtil.compress(profileFallback, 100);
                callback.onDataReady(ByteBuffer.wrap(imageBytes));
            } else {
                // No (non-profile) image available
                callback.onLoadFailed(new LocalizedException(LocalizedException.FILE_NOT_FOUND));
            }
        }

        @Override
        public void cleanup() {
            // Intentionally empty only because we're not returning an open InputStream or another I/O resource!
        }

        @Override
        public void cancel() {
            // Intentionally empty.
        }

        @NonNull
        @Override
        public Class<ByteBuffer> getDataClass() {
            return ByteBuffer.class;
        }

        @NonNull
        @Override
        public DataSource getDataSource() {
            return DataSource.LOCAL;
        }
    }


    public class EncryptedImageModelLoaderFactory implements ModelLoaderFactory<String, ByteBuffer> {

        @Override
        public ModelLoader<String, ByteBuffer> build(@NonNull MultiModelLoaderFactory unused) {
            return new EncryptedImageModelLoader();
        }

        @Override
        public void teardown() {
            // Do nothing.
        }
    }


    //
    // Inner classes for SVG-file handling
    //

    /**
     * Decodes an SVG internal representation from an {@link InputStream}.
     */
    public class SvgDecoder implements ResourceDecoder<InputStream, SVG> {

        @Override
        public boolean handles(@NonNull InputStream source, @NonNull Options options) throws IOException {
            // TODO: Can we tell?
            return true;
        }

        public Resource<SVG> decode(@NonNull InputStream source, int width, int height, @NonNull Options options)
                throws IOException {
            try {
                SVG svg = SVG.getFromInputStream(source);
                return new SimpleResource<SVG>(svg);
            } catch (SVGParseException ex) {
                throw new IOException("Cannot load SVG from stream", ex);
            }
        }
    }

    /**
     * Convert the {@link SVG}'s internal representation to an Android-compatible one
     * ({@link Picture}).
     */
    public class SvgDrawableTranscoder implements ResourceTranscoder<SVG, PictureDrawable> {
        @Nullable
        @Override
        public Resource<PictureDrawable> transcode(@NonNull Resource<SVG> toTranscode, @NonNull Options options) {
            SVG svg = toTranscode.get();
            Picture picture = svg.renderToPicture();
            PictureDrawable drawable = new PictureDrawable(picture);
            return new SimpleResource<PictureDrawable>(drawable);
        }
    }

    /**
     * Listener which updates the {@link ImageView} to be software rendered, because
     * {@link com.caverock.androidsvg.SVG SVG}/{@link android.graphics.Picture Picture} can't render on
     * a hardware backed {@link android.graphics.Canvas Canvas}.
     */
    public static class SvgSoftwareLayerSetter implements RequestListener<PictureDrawable> {

        @Override
        public boolean onLoadFailed(GlideException e, Object model, Target<PictureDrawable> target,
                                    boolean isFirstResource) {
            LogUtil.e(TAG, "onLoadFailed: " + e.getMessage(), e);
            ImageView view = ((ImageViewTarget<?>) target).getView();
            view.setLayerType(ImageView.LAYER_TYPE_NONE, null);
            return false;
        }

        @Override
        public boolean onResourceReady(PictureDrawable resource, Object model,
                                       Target<PictureDrawable> target, DataSource dataSource, boolean isFirstResource) {
            LogUtil.d(TAG, "onResourceReady: Called.");
            ImageView view = ((ImageViewTarget<?>) target).getView();
            view.setLayerType(ImageView.LAYER_TYPE_SOFTWARE, null);
            return false;
        }
    }


}
