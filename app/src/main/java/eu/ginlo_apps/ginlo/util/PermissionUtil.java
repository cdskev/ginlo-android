// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;

public class PermissionUtil {
    public static final int PERMISSION_FOR_READ_CONTACTS = 1;

    public static final int PERMISSION_FOR_CAMERA = 2;

    public static final int PERMISSION_FOR_RECORD_AUDIO = 3;

    public static final int PERMISSION_FOR_LOCATION = 4;

    public static final int PERMISSION_FOR_READ_EXTERNAL_STORAGE = 5;

    public static final int PERMISSION_FOR_WRITE_EXTERNAL_STORAGE = 6;

    public static final int PERMISSION_FOR_VIDEO = 7;

    public static final int PERMISSION_FOR_FINGERPRINT = 8;

    private final BaseActivity mActivity;
    private final PermissionResultCallback mCallback;

    public PermissionUtil(final BaseActivity activity, @NonNull PermissionResultCallback callback) {
        mActivity = activity;
        mCallback = callback;
    }

    private static String[] getManifestPermissions(int permission) {
        final String[] manifestPermissions;

        switch (permission) {
            case PERMISSION_FOR_READ_CONTACTS: {
                manifestPermissions = new String[]{Manifest.permission.READ_CONTACTS};
                break;
            }
            case PERMISSION_FOR_CAMERA: {
                if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
                    manifestPermissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE};
                } else {
                    manifestPermissions = new String[]{Manifest.permission.CAMERA};
                }
                break;
            }
            case PERMISSION_FOR_RECORD_AUDIO: {
                manifestPermissions = new String[]{Manifest.permission.RECORD_AUDIO};
                break;
            }
            case PERMISSION_FOR_LOCATION: {
                manifestPermissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
                break;
            }
            case PERMISSION_FOR_READ_EXTERNAL_STORAGE: {
                manifestPermissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
                break;
            }
            case PERMISSION_FOR_WRITE_EXTERNAL_STORAGE: {
                manifestPermissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
                break;
            }
            case PERMISSION_FOR_VIDEO: {
                if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
                    manifestPermissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE};
                } else {
                    manifestPermissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
                }
                break;
            }
            case PERMISSION_FOR_FINGERPRINT: {
                if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
                    manifestPermissions = new String[]{Manifest.permission.USE_FINGERPRINT};
                } else {
                    manifestPermissions = null;
                }
                break;
            }
            default: {
                manifestPermissions = null;
            }
        }

        return manifestPermissions;
    }

    /**
     * Check that all given permissions have been granted by verifying that each entry in the
     * given array is of the value {@link PackageManager#PERMISSION_GRANTED}.
     *
     * @see Activity#onRequestPermissionsResult(int, String[], int[])
     */
    private static boolean verifyPermissions(int[] grantResults) {
        // At least one result must be checked.
        if (grantResults.length < 1) {
            return false;
        }

        // Verify that each required permission has been granted, otherwise return false.
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Bitte diese Methode nicht direkt aufrufen! Nutzt {@link BaseActivity#requestPermission(int, int, PermissionResultCallback)} Methode!
     *
     * @param permission             Permission die benoetigt wird. Bsp.: {@link PermissionUtil#PERMISSION_FOR_CAMERA}
     * @param permissionRationaleMsg Resource String Id. Der Text wird dem Nutzer vor der Permissionanfrage(Systemmeldung) angezeigt. Soll ein Erklaerungstext sein, wieso die Permission benoetigt wird.
     *                               Wenn keine Meldung angezeigt werden soll, dann {@link Integer#MIN_VALUE} uebergeben
     */
    public void requestPermission(final int permission, final int permissionRationaleMsg) {
        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            mCallback.permissionResult(permission, true);
            return;
        }

        String[] manifestPermissions = getManifestPermissions(permission);

        if (manifestPermissions == null) {
            mCallback.permissionResult(permission, false);
            return;
        }

        if (hasPermission(manifestPermissions)) {
            mCallback.permissionResult(permission, true);
            return;
        }

        boolean showRequest = false;
        final String[] maniPerm = manifestPermissions;

        if (permissionRationaleMsg != Integer.MIN_VALUE) {
            for (String perm : manifestPermissions) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(mActivity, perm)) {
                    showRequest = true;
                    break;
                }
            }

            if (showRequest) {
                final String message = mActivity.getString(permissionRationaleMsg);
                if (!StringUtil.isNullOrEmpty(message)) {
                    final String ok = mActivity.getString(R.string.std_ok);

                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            DialogBuilderUtil.buildResponseDialog(mActivity, message, mActivity.getString(R.string.permission_rationale_title), ok, null, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ActivityCompat.requestPermissions(mActivity, maniPerm, permission);
                                }
                            }, null).show();
                        }
                    });
                }
            }
        }

        if (!showRequest) {
            ActivityCompat.requestPermissions(mActivity, maniPerm, permission);
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull int[] grantResults) {

        mCallback.permissionResult(requestCode, verifyPermissions(grantResults));
    }

    private boolean hasPermission(@NonNull String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(mActivity, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    public static boolean hasPermission(Activity activity, String permission) {
        return ActivityCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public interface PermissionResultCallback {
        void permissionResult(int permission, boolean permissionGranted);
    }
}
