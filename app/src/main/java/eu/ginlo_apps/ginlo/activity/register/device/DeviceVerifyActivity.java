// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.register.device;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.TextView;
import eu.ginlo_apps.ginlo.MainActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity;
import eu.ginlo_apps.ginlo.concurrent.task.HttpBaseTask;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.ChecksumUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.XMLUtil;

public class DeviceVerifyActivity extends NewBaseActivity {

    private final static String TAG = DeviceVerifyActivity.class.getSimpleName();
    private final static String WAKELOCK_TAG = "ginlo:" + TAG;
    private final static int WAKELOCK_FLAGS = PowerManager.PARTIAL_WAKE_LOCK;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {

        PowerManager pm = (PowerManager) getSimsMeApplication().getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(WAKELOCK_FLAGS, WAKELOCK_TAG);
        wl.acquire(60*60*1000L /*60 minutes to be sure*/);

        LogUtil.d(TAG, "onCreateActivity()");

        try {
            String publicKeyString = XMLUtil.getXMLFromPublicKey(getSimsMeApplication().getKeyController().getDeviceKeyPair().getPublic());
            String checksum = ChecksumUtil.getSHA256ChecksumForString(publicKeyString);

            if (StringUtil.isNullOrEmpty(checksum)) {
                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Checksum is null");
            }
            String upperChecksum = checksum.toUpperCase();

            int arrayLength = upperChecksum.length() / 4;
            if (upperChecksum.length() % 4 > 0) {
                arrayLength++;
            }

            String[] splitPK = new String[arrayLength];
            for (int i = 0; i < arrayLength; i++) {
                if (upperChecksum.length() > (i * 4) + 4) {
                    splitPK[i] = upperChecksum.substring(i * 4, (i * 4) + 4);
                } else {
                    splitPK[i] = upperChecksum.substring(i * 4);
                }
            }

            setTextToTextViews(splitPK);

            getSimsMeApplication().getAccountController().coupleDeviceGetCouplingResponse(new GenericActionListener<Void>() {
                @Override
                public void onSuccess(Void object) {
                    LogUtil.d(TAG, "coupleDeviceGetCouplingResponse onSuccess called.");
                    showIdleDialog(R.string.device_create_device_sync_data);

                    getSimsMeApplication().getAccountController().coupleDeviceCreateDevice(new GenericActionListener<Void>() {
                        @Override
                        public void onSuccess(Void object) {
                            LogUtil.d(TAG, "coupleDeviceCreateDevice onSuccess called.");
                            dismissIdleDialog();
                            wl.release();
                            if (wl.isHeld()) {
                                LogUtil.w(TAG, "handleConfirmClick: onSuccess: Wakelock held!");
                            }

                            Intent intent = new Intent(DeviceVerifyActivity.this, RuntimeConfig.getClassUtil().getLoginActivityClass());
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        }

                        @Override
                        public void onFail(String message, String errorIdent) {
                            LogUtil.w(TAG, "coupleDeviceCreateDevice onFail called: " + message + "(" + errorIdent + ")");
                            dismissIdleDialog();
                            wl.release();
                            if (wl.isHeld()) {
                                LogUtil.w(TAG, "handleConfirmClick: onFail: Wakelock held!");
                            }

                            String error = getString(R.string.error_couple_device);
                            if (StringUtil.isNullOrEmpty(errorIdent)) {
                                error = error + getString(R.string.error_couple_device_error_ident);
                            }
                            DialogBuilderUtil.buildErrorDialog(DeviceVerifyActivity.this, error).show();
                        }
                    });
                }

                @Override
                public void onFail(String message, String errorIdent) {
                    LogUtil.w(TAG, "coupleDeviceGetCouplingResponse onFail called." + message + "(" + errorIdent + ")");
                    dismissIdleDialog();
                    wl.release();
                    if (wl.isHeld()) {
                        LogUtil.w(TAG, "handleConfirmClick: onFail: Wakelock held!");
                    }

                    String error = getString(R.string.error_couple_device);
                    if (StringUtil.isNullOrEmpty(errorIdent)) {
                        error = error + getString(R.string.error_couple_device_error_ident);
                    }
                    DialogBuilderUtil.buildErrorDialog(DeviceVerifyActivity.this, error, -1, new DialogBuilderUtil.OnCloseListener() {
                        @Override
                        public void onClose(int ref) {
                            resetCouplingAndRestart();
                        }
                    }).show();
                }
            });
        } catch (LocalizedException e) {
            LogUtil.w(TAG, "LocalizedException: " + e.getMessage(), e);
            if (StringUtil.isEqual(e.getIdentifier(), LocalizedException.KEY_NOT_AVAILABLE)) {
                DialogBuilderUtil.buildErrorDialog(this, getString(R.string.error_couple_device) + LocalizedException.KEY_NOT_AVAILABLE, 0, new DialogBuilderUtil.OnCloseListener() {
                    @Override
                    public void onClose(int ref) {
                        finish();
                    }
                }).show();
            } else {
                finish();
            }

            wl.release();
            if (wl.isHeld()) {
                LogUtil.w(TAG, "LocalizedException: Wakelock held!");
            }
        }
    }

    private void setTextToTextViews(String[] splitPubKey) {
        if (splitPubKey.length > 0) {
            setTextToTextView(splitPubKey[0], R.id.device_verify_sn_0);
        }
        if (splitPubKey.length > 1) {
            setTextToTextView(splitPubKey[1], R.id.device_verify_sn_1);
        }
        if (splitPubKey.length > 2) {
            setTextToTextView(splitPubKey[2], R.id.device_verify_sn_2);
        }
        if (splitPubKey.length > 3) {
            setTextToTextView(splitPubKey[3], R.id.device_verify_sn_3);
        }
        if (splitPubKey.length > 4) {
            setTextToTextView(splitPubKey[4], R.id.device_verify_sn_4);
        }
        if (splitPubKey.length > 5) {
            setTextToTextView(splitPubKey[5], R.id.device_verify_sn_5);
        }
        if (splitPubKey.length > 6) {
            setTextToTextView(splitPubKey[6], R.id.device_verify_sn_6);
        }
        if (splitPubKey.length > 7) {
            setTextToTextView(splitPubKey[7], R.id.device_verify_sn_7);
        }
        if (splitPubKey.length > 8) {
            setTextToTextView(splitPubKey[8], R.id.device_verify_sn_8);
        }
        if (splitPubKey.length > 9) {
            setTextToTextView(splitPubKey[9], R.id.device_verify_sn_9);
        }
        if (splitPubKey.length > 10) {
            setTextToTextView(splitPubKey[10], R.id.device_verify_sn_10);
        }
        if (splitPubKey.length > 11) {
            setTextToTextView(splitPubKey[11], R.id.device_verify_sn_11);
        }
        if (splitPubKey.length > 12) {
            setTextToTextView(splitPubKey[12], R.id.device_verify_sn_12);
        }
        if (splitPubKey.length > 13) {
            setTextToTextView(splitPubKey[13], R.id.device_verify_sn_13);
        }
        if (splitPubKey.length > 14) {
            setTextToTextView(splitPubKey[14], R.id.device_verify_sn_14);
        }
        if (splitPubKey.length > 15) {
            setTextToTextView(splitPubKey[15], R.id.device_verify_sn_15);
        }
    }

    private void setTextToTextView(String text, int textViewId) {
        TextView t = findViewById(textViewId);
        if (t != null) {
            t.setText(text);
        }
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_device_verifiy_device;
    }

    @Override
    protected void onResumeActivity() {
    }

    @Override
    public void onBackPressed() {
        String msg = getString(R.string.device_cancel_coupling_device);
        DialogBuilderUtil.buildResponseDialog(this, msg, getString(R.string.std_warning), getString(R.string.next), getString(R.string.std_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                resetCouplingAndRestart();
            }
        }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //nothing to do
            }
        }).show();
    }

    private void resetCouplingAndRestart() {
        getSimsMeApplication().getAccountController().resetCoupleDevice();
        Intent intent = new Intent(DeviceVerifyActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
