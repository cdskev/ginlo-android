// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.MediaRecorder;
import android.util.AttributeSet;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.CameraUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.MetricsUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class CameraView
        extends SurfaceView {

    public static final int MODE_LOW_QUALITY = 0;

    public static final int MODE_MEDIUM_QUALITY = 1;

    public static final int MODE_HIGH_QUALITY = 2;

    public static final int MODE_VERY_HIGH_QUALITY = 3;

    private static final int WIDTH_LOW_QUALITY = 640;

    private static final int HEIGHT_LOW_QUALITY = 320;

    private static final int BITRATE_LOW_QUALITY = 600_000;

    private static final int WIDTH_MEDIUM_QUALITY = 854;

    private static final int HEIGHT_MEDIUM_QUALITY = 480;

    private static final int BITRATE_MEDIUM_QUALITY = 800_000;

    private static final int WIDTH_HIGH_QUALITY = 1280;

    private static final int HEIGHT_HIGH_QUALITY = 720;

    private static final int BITRATE_HIGH_QUALITY = 1_200_000;

    private static final int WIDTH_VERY_HIGH_QUALITY = 1280;

    private static final int HEIGHT_VERY_HIGH_QUALITY = 720;

    private static final int BITRATE_VERY_HIGH_QUALITY = 1_600_000;

    private final Context mContext;
    private final int mCameraId;
    private final SurfaceHolder mSurfaceHolder;
    private int mRecordWidth = 320;
    private int mRecordHeight = 240;
    private int mBitrate = BITRATE_LOW_QUALITY;
    private boolean mIsPreviewRunning = false;
    private boolean mIsRecorderPrepared = false;
    private boolean mIsRecording = false;
    private Camera mCamera;
    private MediaRecorder mMediaRecorder;
    private Parameters mCameraParameters;

    private File mOutputFile;

    private int mMode = 0;

    private boolean mCameraIsInitiated = false;
    private final SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {
        public void surfaceCreated(final SurfaceHolder holder) {
        }

        public void surfaceChanged(final SurfaceHolder holder,
                                   final int format,
                                   final int width,
                                   final int height) {
            if (mCameraIsInitiated) {
                return;
            }
            mCameraIsInitiated = true;

            if (!mIsRecording) {
                initCamera();
            }
        }

        public void surfaceDestroyed(final SurfaceHolder holder) {
            releaseRecorder();
            releaseCamera();
        }
    };
    private MediaRecorder.OnInfoListener mOnInfoListener;

    public CameraView(final Context context,
                      final AttributeSet attrs) {
        super(context, attrs);

        final int camId = getCameraId();
        final Camera cam = Camera.open(camId);

        this.mContext = context;
        this.mCamera = cam;
        this.mCameraId = camId;

        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(mSurfaceCallback);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        // although
        // deprecated,
        // samsung
        // needs
        // this
    }

    public CameraView(final Context context) {
        super(context);

        final int camId = getCameraId();
        final Camera cam = Camera.open(camId);

        this.mContext = context;
        this.mCamera = cam;
        this.mCameraId = camId;

        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(mSurfaceCallback);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        // although
        // deprecated,
        // samsung
        // needs
        // this
    }

    /**
     * @param defStyle
     */
    public CameraView(final Context context,
                      final AttributeSet attrs,
                      final int defStyle) {
        super(context, attrs, defStyle);

        final int camId = getCameraId();
        final Camera cam = Camera.open(camId);

        this.mContext = context;
        this.mCamera = cam;
        this.mCameraId = camId;

        mSurfaceHolder = getHolder();
        mSurfaceHolder.addCallback(mSurfaceCallback);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        // although
        // deprecated,
        // samsung
        // needs
        // this
    }

    /**
     * setMode
     *
     * @param mode quality
     */
    public void setMode(final int mode) {
        if (mode < MODE_LOW_QUALITY) {
            mMode = MODE_LOW_QUALITY;
        } else if (mode > MODE_VERY_HIGH_QUALITY) {
            mMode = MODE_VERY_HIGH_QUALITY;
        } else {
            mMode = mode;
        }
    }

    public boolean startRecording() {
        LogUtil.i(getClass().getName(), "startRecording()");

        stopPreview();

        if (!initRecorder()) {
            return false;
        }

        prepareRecorder();

        if (mIsRecorderPrepared && !mIsRecording) {
            LogUtil.i(getClass().getName(), "recorder start...");
            mIsRecording = true;

            mMediaRecorder.start();
        }
        return true;
    }

    public File stopRecording() {
        LogUtil.i(getClass().getName(), "stopRecording()");

        if ((mMediaRecorder != null) && mIsRecording) {
            mIsRecording = false;
            LogUtil.i(getClass().getName(), "recorder stop...");
            try {
                mMediaRecorder.stop();
            } catch (final RuntimeException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                mOutputFile.delete();
                mOutputFile = null;
            } finally {
                releaseRecorder();
            }
        }
        return mOutputFile;
    }

    private void stopPreview() {
        if (mIsPreviewRunning) {
            LogUtil.i(getClass().getName(), "stopping preview...");
            mCamera.stopPreview();
            mIsPreviewRunning = false;
        }
    }

    public void releaseCamera() {
        if (mCamera != null) {
            stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    public void releaseRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            mIsRecording = false;
            mIsRecorderPrepared = false;
            mCamera.lock();
        }
    }

    private void initCamera() {
        LogUtil.i(getClass().getName(), "initCamera()");
        stopPreview();

        mCameraParameters = getCameraParameters(mCamera);
        if (mCameraParameters == null) {
            return;
        }
        setSize(mCameraParameters);

        try {
            mCamera.setPreviewDisplay(mSurfaceHolder);
            mCamera.startPreview();
            mIsPreviewRunning = true;
        } catch (IOException e) {
            LogUtil.e(getClass().getName(), "starting preview failed: " + e.getMessage(), e);
        }
    }

    private void calculatePreviewLayout() {
        final float newProportion = (float) mRecordHeight / (float) mRecordWidth;

        final int screenWidth = MetricsUtil.getDisplayMetrics(mContext).widthPixels;
        final int screenHeight = MetricsUtil.getDisplayMetrics(mContext).heightPixels;
        final float screenProportion = (float) screenWidth / (float) screenHeight;

        final android.view.ViewGroup.LayoutParams lp = getLayoutParams();

        if (newProportion > screenProportion) {
            lp.width = screenWidth;
            lp.height = (int) ((float) screenWidth / newProportion);
        } else {
            lp.width = (int) (newProportion * (float) screenHeight);
            lp.height = screenHeight;
        }
        setLayoutParams(lp);
        invalidate();
    }

    private void setSize(final Parameters parameters) {
        mCamera.setParameters(parameters);

        final int rotation = CameraUtil.getCameraDisplayOrientation((Activity) mContext, mCameraId);
        mCamera.setDisplayOrientation(rotation);
    }

    private boolean initRecorder() {
        if (mCamera == null) {
            return false;
        }
        LogUtil.i(getClass().getName(), "initRecorder()");

        mMediaRecorder = new MediaRecorder();
        if (mOnInfoListener != null) {
            mMediaRecorder.setOnInfoListener(mOnInfoListener);
        }
        mCamera.unlock();

        mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);

        mMediaRecorder.setVideoEncodingBitRate(mBitrate);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoSize(mRecordWidth, mRecordHeight);
        mMediaRecorder.setMaxFileSize(20 * 1024 * 1024);

        try {
            mOutputFile = (new FileUtil(mContext)).getTempFile();
        } catch (final IOException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            return false;
        }

        mMediaRecorder.setOutputFile(mOutputFile.getAbsolutePath());

        mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
        final Display display = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

        final int sensorOrientation = CameraUtil.getSensorOrientation(mContext, mCameraId);

        if (display.getRotation() == Surface.ROTATION_0) {
            mMediaRecorder.setOrientationHint(sensorOrientation);
        } else if (display.getRotation() == Surface.ROTATION_270) {
            mMediaRecorder.setOrientationHint(180);
        }

        return true;
    }

    private void prepareRecorder() {
        try {
            mMediaRecorder.prepare();
            mIsRecorderPrepared = true;
            LogUtil.i(getClass().getName(), "recorder prepared");
        } catch (final IllegalStateException | IOException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            releaseRecorder();
        }
    }

    private Parameters getCameraParameters(final Camera camera) {
        if (camera == null) {
            return null;
        }

        final Parameters parameters = camera.getParameters();

        parameters.setRecordingHint(true);
        parameters.set("cam_mode", 1);  // again, thank samsung for this one
        setMinimalParameters(parameters);

        switch (mMode) {

            case MODE_VERY_HIGH_QUALITY:
                mRecordWidth = WIDTH_VERY_HIGH_QUALITY;
                mRecordHeight = HEIGHT_VERY_HIGH_QUALITY;
                mBitrate = BITRATE_VERY_HIGH_QUALITY;
                break;

            case MODE_HIGH_QUALITY:
                mRecordWidth = WIDTH_HIGH_QUALITY;
                mRecordHeight = HEIGHT_HIGH_QUALITY;
                mBitrate = BITRATE_HIGH_QUALITY;
                break;

            case MODE_MEDIUM_QUALITY:
                mRecordWidth = WIDTH_MEDIUM_QUALITY;
                mRecordHeight = HEIGHT_MEDIUM_QUALITY;
                mBitrate = BITRATE_MEDIUM_QUALITY;
                break;

            case MODE_LOW_QUALITY:
            default:
                mRecordWidth = WIDTH_LOW_QUALITY;
                mRecordHeight = HEIGHT_LOW_QUALITY;
                mBitrate = BITRATE_LOW_QUALITY;
                break;
        }

        final Size supportedSize = CameraUtil.getSupportedSizeFromArea(parameters, mRecordWidth * mRecordHeight);

        LogUtil.i(getClass().getName(), "resetting recordingSize: " + supportedSize.width + " x " + supportedSize.height);
        mRecordWidth = supportedSize.width;
        mRecordHeight = supportedSize.height;
        parameters.setPreviewSize(mRecordWidth, mRecordHeight);

        calculatePreviewLayout();

        return parameters;
    }

    private void setMinimalParameters(final Parameters parameters) {
        // White Balance
        {
            final List<String> whiteBalanceModes = parameters.getSupportedWhiteBalance();

            if ((whiteBalanceModes != null) && whiteBalanceModes.contains(Parameters.WHITE_BALANCE_AUTO)) {
                parameters.setWhiteBalance(Parameters.WHITE_BALANCE_AUTO);
            }
        }

        // Focus
        {
            final List<String> focusModes = parameters.getSupportedFocusModes();

            if ((focusModes != null) && focusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
        }

        // Flash
        {
            final List<String> flashModes = parameters.getSupportedFlashModes();

            if ((flashModes != null) && flashModes.contains(Parameters.FLASH_MODE_OFF)) {
                parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
            }
        }
    }

    private int getCameraId() {
        final int cameraId = -1;

        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            final CameraInfo info = new CameraInfo();

            Camera.getCameraInfo(i, info);
            if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
                return i;
            }
        }
        return cameraId;
    }

    /**
     * setOnInfoListener
     */
    public void setOnInfoListener(final MediaRecorder.OnInfoListener onInfoListener) {
        mOnInfoListener = onInfoListener;
    }

    /**
     * getIsRecording
     *
     * @return IsRecording
     */
    public boolean getIsRecording() {
        return mIsRecording;
    }
}
