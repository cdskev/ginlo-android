// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.view.Surface;
import eu.ginlo_apps.ginlo.log.LogUtil;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class CameraUtil {
    private static final int DEFAULT_SENSOR_ORIENTATION = 90;

    /**
     * getSensorOrientation
     *
     * @param context
     * @param cameraId
     * @return
     */
    //@SuppressLint("NewApi")
    public static int getSensorOrientation(final Context context, final int cameraId) {
        try {
            final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) {
                return DEFAULT_SENSOR_ORIENTATION;
            }
            final CameraCharacteristics characteristics = manager.getCameraCharacteristics(Integer.toString(cameraId));
            return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        } catch (CameraAccessException e) {
            LogUtil.w(CameraUtil.class.getName(), e.getMessage(), e);
            return DEFAULT_SENSOR_ORIENTATION;
        }
    }

    public static Camera.Size getSupportedSizeFromArea(Camera.Parameters parameters, final int originalArea) {
        Camera.Size result = null;
        int diff = Integer.MAX_VALUE;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (result == null) {
                result = size;
                final int newArea = size.width * size.height;
                diff = Math.abs(originalArea - newArea);
            } else {
                final int newArea = size.width * size.height;
                int newDiff = Math.abs(originalArea - newArea);
                if (newDiff < diff) {
                    result = size;
                    diff = newDiff;
                }
            }
        }
        return (result);
    }

    public static int getCameraDisplayOrientation(Activity activity,
                                                  int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();

        Camera.getCameraInfo(cameraId, info);

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        final int degrees;

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                degrees = 0;
                break;
        }

        int result;

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        return result;
    }
}
