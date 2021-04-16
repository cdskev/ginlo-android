// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.register.device;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.zxing.integration.android.IntentIntegrator;
import eu.ginlo_apps.ginlo.MainActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity;
import eu.ginlo_apps.ginlo.activity.register.device.DeviceVerifyActivity;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.PermissionUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.ViewUtil;

public class DeviceRequestTanActivity extends NewBaseActivity {

    private static final int SCAN_TAN_RESULT_CODE = 453;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {

        final EditText tanEditText1 = findViewById(R.id.device_request_tan_edittext_1);
        final EditText tanEditText2 = findViewById(R.id.device_request_tan_edittext_2);
        final EditText tanEditText3 = findViewById(R.id.device_request_tan_edittext_3);

        ViewUtil.createTextWatcher(this, null, tanEditText1, tanEditText2, 3);
        ViewUtil.createTextWatcher(this, tanEditText1, tanEditText2, tanEditText3, 3);
        ViewUtil.createTextWatcher(this, tanEditText2, tanEditText3, null, 3);

        ViewUtil.createOnKeyListener(tanEditText1, tanEditText2);
        ViewUtil.createOnKeyListener(tanEditText2, tanEditText3);
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_device_request_tan;
    }

    @Override
    protected void onResumeActivity() {
    }

    @Override
    protected void onActivityPostLoginResult(final int requestCode,
                                             final int resultCode,
                                             final Intent intent) {
        super.onActivityPostLoginResult(requestCode, resultCode, intent);
        if (requestCode == SCAN_TAN_RESULT_CODE) {
            if (resultCode == RESULT_OK) {
                final String qrCodeString = intent.getStringExtra("SCAN_RESULT");

                if (!StringUtil.isNullOrEmpty(qrCodeString)) {
                    final int indexPipe = qrCodeString.indexOf("|");
                    if (indexPipe > -1 && qrCodeString.length() > indexPipe + 1) {
                        final String tan = qrCodeString.substring(indexPipe + 1);
                        if (tan.length() == 9) {
                            LogUtil.d("CHECK", "onActivityPostLoginResult()");
                            startCoupling(tan);
                        } else {
                            Toast.makeText(DeviceRequestTanActivity.this,
                                    getString(R.string.device_request_tan_error_coupling_qrcode_failed), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(DeviceRequestTanActivity.this,
                                getString(R.string.device_request_tan_error_coupling_qrcode_failed), Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    public void handleScanClick(final View v) {
        requestPermission(PermissionUtil.PERMISSION_FOR_CAMERA, R.string.permission_rationale_camera, new PermissionUtil.PermissionResultCallback() {
            @Override
            public void permissionResult(final int permission, final boolean permissionGranted) {
                if (permission == PermissionUtil.PERMISSION_FOR_CAMERA && permissionGranted) {
                    final IntentIntegrator intentIntegrator = new IntentIntegrator(DeviceRequestTanActivity.this);
                    intentIntegrator.setOrientationLocked(true);
                    final Intent intent = intentIntegrator.createScanIntent();

                    startActivityForResult(intent, SCAN_TAN_RESULT_CODE);
                }
            }
        });
    }

    public void handleNextClick(final View v) {
        final String tan1;
        final String tan2;
        final String tan3;

        final EditText tanEditText1 = findViewById(R.id.device_request_tan_edittext_1);
        if (tanEditText1 != null) {
            tan1 = tanEditText1.getText().toString();
        } else {
            tan1 = null;
        }

        if (!checkTanPartString(tan1)) {
            DialogBuilderUtil.buildErrorDialog(this, getString(R.string.device_request_tan_error_tan_length)).show();
            return;
        }

        final EditText tanEditText2 = findViewById(R.id.device_request_tan_edittext_2);
        if (tanEditText2 != null) {
            tan2 = tanEditText2.getText().toString();
        } else {
            tan2 = null;
        }

        if (!checkTanPartString(tan2)) {
            DialogBuilderUtil.buildErrorDialog(this, getString(R.string.device_request_tan_error_tan_length)).show();
            return;
        }

        final EditText tanEditText3 = findViewById(R.id.device_request_tan_edittext_3);
        if (tanEditText3 != null) {
            tan3 = tanEditText3.getText().toString();
        } else {
            tan3 = null;
        }

        if (!checkTanPartString(tan3)) {
            DialogBuilderUtil.buildErrorDialog(this, getString(R.string.device_request_tan_error_tan_length)).show();
            return;
        }
        LogUtil.d("CHECK", "handleNextClick()");
        startCoupling(tan1 + tan2 + tan3);
    }

    private void startCoupling(@NonNull final String tan) {
        LogUtil.d("CHECK", "startCoupling()");
        showIdleDialog();
        getSimsMeApplication().getAccountController().coupleDeviceRequestCoupling(tan, new GenericActionListener<Void>() {
            @Override
            public void onSuccess(final Void object) {
                dismissIdleDialog();

                //dismissIdleDialog();
                LogUtil.d("CHECK", "coupleDeviceRequestCoupling() success");
                final Intent intent = new Intent(DeviceRequestTanActivity.this, DeviceVerifyActivity.class);
                startActivity(intent);
            }

            @Override
            public void onFail(final String message, final String errorIdent) {
                dismissIdleDialog();
                String errorMessage = getString(R.string.device_request_tan_error_coupling_failed);
                if (errorIdent.equals(LocalizedException.TOO_MANY_TRIES_FOR_REQUEST_COUPLING)) {
                    errorMessage = getString(R.string.service_ERR_0162);
                }
                DialogBuilderUtil.buildErrorDialog(DeviceRequestTanActivity.this, errorMessage).show();
            }
        });
    }

    private boolean checkTanPartString(final String part) {
        return part != null && part.length() == 3;
    }

    @Override
    public void onBackPressed() {
        final String msg = getString(R.string.device_cancel_coupling_device);
        DialogBuilderUtil.buildResponseDialog(this, msg, getString(R.string.std_warning), getString(R.string.next), getString(R.string.std_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                getSimsMeApplication().getAccountController().resetCoupleDevice();
                final Intent intent = new Intent(DeviceRequestTanActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                //nothing to do
            }
        }).show();
    }
}
