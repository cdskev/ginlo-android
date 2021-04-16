// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import eu.ginlo_apps.ginlo.exception.LocalizedException;

import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.AudioProvider;
import eu.ginlo_apps.ginlo.util.SystemUtil;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class AudioPlayer {

    private final Uri mUri;
    private final Context mContext;
    private MediaPlayer mPlayer;
    private boolean mPrepared;
    private boolean mPlaying;
    private AssetFileDescriptor mAssertFileDesc;

    private FileDescriptor mFileDesc;

    /**
     *
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public AudioPlayer(Uri audioFileUri,
                       Context context)
            throws LocalizedException {
        mContext = context;
        mUri = audioFileUri;
        mPlayer = new MediaPlayer();
        mPrepared = false;
        mPlaying = false;
        prepare();
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    private void prepare()
            throws LocalizedException {
        try {
            final String path = mUri.getPath();
            Uri uri = Uri.parse(AudioProvider.CONTENT_URI_BASE + path);

            try {
                mPlayer.setDataSource(mContext, uri);
            } catch (IOException | IllegalArgumentException | SecurityException | IllegalStateException | UnsupportedOperationException e) {
                if (SystemUtil.hasNougat() && path != null) {
                    File f = new File(path);

                    if (f.exists()) {
                        mAssertFileDesc = new AssetFileDescriptor(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
                        mPlayer.setDataSource(mAssertFileDesc);
                    }
                } else {
                    if (path != null) {
                        File f = new File(path);

                        if (f.exists()) {
                            try (FileInputStream fis = new FileInputStream(f)) {
                                mFileDesc = fis.getFD();
                                mPlayer.setDataSource(mFileDesc);
                            }
                        }
                    }
                }
            }

            mPlayer.prepare();
            mPrepared = true;
        } catch (IOException | IllegalArgumentException | SecurityException | IllegalStateException | UnsupportedOperationException e) {
            LogUtil.e("AudioPlayer", "prepare() failed", e);
            throw new LocalizedException(LocalizedException.LOAD_AUDIO_PLAYER_FAILED, e);
        }
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public boolean play()
            throws LocalizedException {
        if (mPrepared) {
            mPlayer.start();
            mPlaying = true;
            return true;
        } else {
            prepare();
            return false;
        }
    }

    public void pause() {
        if (mPlaying) {
            mPlayer.pause();
            mPlaying = false;
        }
    }

    public void release() {
        if (mPlaying) {
            pause();
        }
        if (mPlayer != null) {
            if (mAssertFileDesc != null) {
                try {
                    mAssertFileDesc.close();
                } catch (Exception e) {
                    LogUtil.e("AudioPlayer", "release() failed. Unable to close the AssetFileDescriptor.", e);
                }
            }

            mPlayer.release();
        }
        mPlayer = null;
        mPrepared = false;
    }

    public int getDuration() {
        if (mPrepared) {
            return mPlayer.getDuration() / 1000;
        }
        return 0;
    }
}
