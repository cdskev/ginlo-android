// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.media.MediaRecorder;
import android.media.MediaRecorder.OnInfoListener;
import android.net.Uri;
import eu.ginlo_apps.ginlo.exception.LocalizedException;

import eu.ginlo_apps.ginlo.log.LogUtil;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class AudioRecorder {

    private static final String LOG_TAG = "AudioRecorder";

    private static final int MAX_DURATION = 3 * 60 * 1000;  // 3 minutes

    private static final String PREPARE_AND_START_FAILED = "prepare() and start() failed";

    private final String mFilename;
    private MediaRecorder recorder;

    private long startTime;

    private long endTime;

    public AudioRecorder(File file) {
        mFilename = file.getAbsolutePath();
    }

    /**
     * @throws FileNotFoundException [!EXC_DESCRIPTION!]
     */
    public Uri getUri()
            throws FileNotFoundException {
        File file = new File(mFilename);

        if (file.exists()) {
            return Uri.fromFile(file);
        } else {
            LogUtil.e(LOG_TAG, "getUri: fileNotfound: " + mFilename);
            throw new FileNotFoundException();
        }
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    public void startRec(OnInfoListener listener)
            throws LocalizedException {
        startTime = System.nanoTime();
        endTime = -1;
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncodingBitRate(44100);
        recorder.setAudioSamplingRate(96000);
        recorder.setOnInfoListener(listener);
        recorder.setOutputFile(mFilename);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setMaxDuration(MAX_DURATION);

        try {
            recorder.prepare();
            recorder.start();
        } catch (IOException | IllegalStateException e) {
            LogUtil.w(LOG_TAG, PREPARE_AND_START_FAILED, e);
            throw new LocalizedException(LocalizedException.START_AUDIO_RECORDER_FAILED, PREPARE_AND_START_FAILED,
                    e);
        }
    }

    public void stopRec() {
        try {
            Thread.sleep(800);  // XXX: there is a bug in android's AAC

            // recording, cutting off recording up to a
            // second to early
        } catch (Exception e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            // do nothing
        }

        endTime = System.nanoTime();
        try {
            if (recorder != null) {
                recorder.stop();
            }
        } catch (RuntimeException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            // this happens when recording time is too short
        } finally {
            release();
        }
    }

    public int getRecordDurationMillis() {
        long end = endTime;

        if (endTime < 0) {
            end = System.nanoTime();
        }

        long nanoSeconds = end - startTime;

        return (int) ((nanoSeconds) / 1000000);
    }

    public void release() {
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
    }
}
