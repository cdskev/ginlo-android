// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import eu.ginlo_apps.ginlo.log.LogUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class VideoUtil {
    private static final String TAG = VideoUtil.class.getSimpleName();
    private static final int THUMBNAIL_WIDTH = 200;
    private static final int THUMNAIL_HEIGHT = 200;

    static byte[] decodeUri(Activity activity,
                            Uri videoUri) {
        ByteArrayOutputStream byteOutputStream = null;
        InputStream inputStream = null;

        try {
            byteOutputStream = new ByteArrayOutputStream();
            inputStream = activity.getContentResolver().openInputStream(videoUri);

            if (inputStream == null) return null;

            StreamUtil.copyStreams(inputStream, byteOutputStream);

            return byteOutputStream.toByteArray();
        } catch (IOException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        } finally {
            StreamUtil.closeStream(inputStream);
            StreamUtil.closeStream(byteOutputStream);
        }
        return null;
    }

    static Bitmap getThumbnail(Activity activity,
                               Uri videoUri) {
        return getThumbnail(activity, videoUri, THUMBNAIL_WIDTH, THUMNAIL_HEIGHT);
    }

    public static Bitmap getThumbnail(Context context,
                                      Uri videoUri,
                                      int width,
                                      int height) {
        try {
            MediaMetadataRetriever ret = new MediaMetadataRetriever();
            Uri uri;

            if (videoUri.toString().startsWith("content")) {
                uri = videoUri;
            } else {
                uri = Uri.parse(VideoProvider.CONTENT_URI_BASE + videoUri.getPath());
            }

            ret.setDataSource(context, uri);

            Bitmap still = ret.getFrameAtTime(0);
            Bitmap thumb = ThumbnailUtils.extractThumbnail(still, width, height);

            if (thumb != null) {
                return thumb;
            } else {
                return Bitmap.createBitmap(width, height, Config.ARGB_8888);
            }
        } catch (IllegalArgumentException | SecurityException e) {
            return Bitmap.createBitmap(width, height, Config.ARGB_8888);
        }
    }
}
