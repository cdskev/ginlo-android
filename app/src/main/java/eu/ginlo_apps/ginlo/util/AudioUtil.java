// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import eu.ginlo_apps.ginlo.R;

import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.StreamUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class AudioUtil {

    private static final int THUMBNAIL_WIDTH = 133;

    private static final int THUMNAIL_HEIGHT = 100;

    public static byte[] decodeUri(Activity activity,
                                   Uri voiceUri) {
        try (ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
             InputStream inputStream = activity.getContentResolver().openInputStream(voiceUri)) {
            if (inputStream != null) {
                StreamUtil.copyStreams(inputStream, byteOutputStream);
            }
            return byteOutputStream.toByteArray();
        } catch (IOException e) {
            LogUtil.e(AudioUtil.class.getName(), e.getMessage(), e);
        }
        return null;
    }

    /* TODO Diese Funktion sollte entfernt werden
     * hier wird ein Mediaplayer erzeugt, nur um die Laenge des Audiostreams zu messen
     * sga
     *
     * */

    public static int getDuration(final Activity activity,
                                  final Uri voiceUri) {
        MediaPlayer mp = null;

        try {
            mp = MediaPlayer.create(activity, voiceUri);
            if (mp != null) {
                return mp.getDuration() / 1000;
            }
        } finally {
            if (mp != null) {
                mp.release();
            }
        }
        return 0;
    }

    public static int[] getLevels() {

        return new int[]{
                1,
                2,
                3,
                4,
                5,
                6,
                7,
                8
        };
    }

    public static Bitmap getWaveformFromLevels() {
        final Bitmap b = Bitmap.createBitmap(THUMBNAIL_WIDTH, THUMNAIL_HEIGHT, Config.ARGB_8888);

        b.eraseColor(android.graphics.Color.GRAY);
        return b;
    }

    public static Bitmap getPlaceholder(Context ctx) {
        return BitmapFactory.decodeResource(ctx.getResources(), R.drawable.sound_animation_large);
    }

    /**
     * createMediaPlayer
     *
     * @param context mApplication
     * @param resid   resid
     * @return MediaPlayer
     */
    public static MediaPlayer createMediaPlayer(Context context, int resid) {
        try (AssetFileDescriptor afd = context.getResources().openRawResourceFd(resid)) {
            final MediaPlayer mp = new MediaPlayer();

            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            mp.setAudioStreamType(AudioManager.STREAM_RING);
            mp.prepare();
            return mp;
        } catch (IOException | SecurityException e) {
            LogUtil.e(AudioUtil.class.getName(), e.getMessage(), e);
            return null;
        } catch (IllegalStateException e) {
            LogUtil.e(AudioUtil.class.getName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * getAudioVolume
     *
     * @param audioManager audioManager
     * @return AudioVolume
     */
    public static float getAudioVolume(final AudioManager audioManager) {
        final int curVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
        final int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);

        return 1 - (float) (Math.log(maxVolume - curVolume) / Math.log(maxVolume));
    }
}
