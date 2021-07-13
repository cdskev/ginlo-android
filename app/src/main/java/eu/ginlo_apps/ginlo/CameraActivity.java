// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import eu.ginlo_apps.ginlo.view.CameraView;
import eu.ginlo_apps.ginlo.view.FloatingActionButton;
import java.io.File;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class CameraActivity
        extends BaseActivity implements MediaRecorder.OnInfoListener {

    private FloatingActionButton recordButton;

    private CameraView cameraView;

    private Drawable stopIcon;

    private Button.OnClickListener mButtonOnClickListener;

    @Override
    protected void onResumeActivity() {
        //
    }

    @Override
    protected int getActivityLayout() {
        return -1;
    }

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        if (getSimsMeApplication().getPreferencesController().isCameraDisabled()) {
            Toast.makeText(getSimsMeApplication(), getResources().getString(R.string.error_mdm_camera_access_not_allowed), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LayoutInflater inflater = LayoutInflater.from(this);
        final View layout = inflater.inflate(R.layout.activity_camera, null);

        setContentView(layout);

        stopIcon = getResources().getDrawable(R.drawable.ic_stop_white_48dp);

        cameraView = findViewById(R.id.camera_preview);
        cameraView.setOnInfoListener(this);

        int cameraQuality;
        try {
            cameraQuality = ((SimsMeApplication) getApplication()).getPreferencesController().getVideoQuality();
            LogUtil.i(this.getClass().getName(), "cameraQuality: " + cameraQuality);
        } catch (LocalizedException e) {
            LogUtil.w(this.getClass().getName(), "Error getting Video Quality");
            cameraQuality = CameraView.MODE_LOW_QUALITY;
        }

        cameraView.setMode(cameraQuality);
        cameraView.post(new Runnable() {
            @Override
            public void run() {
                LogUtil.i(this.getClass().getName(), "layout size: " + layout.getWidth() + " x " + layout.getHeight());
                // cameraView.setLayoutSize(layout.getWidth(),
                // layout.getHeight());
            }
        });

        recordButton = findViewById(R.id.recordButton);
        if (RuntimeConfig.isBAMandant()) {
            ColorUtil colorUtil = ColorUtil.getInstance();
            final int appAccentContrastColor = colorUtil.getAppAccentContrastColor(getSimsMeApplication());
            final int appAccentColor = colorUtil.getAppAccentColor(getSimsMeApplication());
            recordButton.setColorNormal(appAccentColor);
            recordButton.setColorPressed(appAccentColor);
            recordButton.setColorDisabled(appAccentColor);

            final Drawable drawable = recordButton.getIconDrawable();
            ColorUtil.setColorFilter(drawable, appAccentContrastColor);
            ColorUtil.setColorFilter(stopIcon, appAccentContrastColor);
        }

        mButtonOnClickListener = new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordButton.setEnabled(false);
                if (cameraView.getIsRecording()) {
                    // recordButton.setEnabled(false);
                    File outputFile = cameraView.stopRecording();

                    if (outputFile == null) {
                        setResult(RESULT_CANCELED);

                        AlertDialogWrapper errorDialog = DialogBuilderUtil.buildErrorDialog(CameraActivity.this,
                                getString(R.string.chats_addAttachment_takeVideo_failure));

                        errorDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                CameraActivity.this.finish();
                            }
                        });
                        errorDialog.show();
                        return;
                    }

                    Intent returnIntent = new Intent();

                    returnIntent.putExtra("data", outputFile.getAbsolutePath());
                    setResult(RESULT_OK, returnIntent);
                    finish();
                } else {
                    if (!cameraView.startRecording()) {
                        cameraView.releaseRecorder();
                        cameraView.releaseCamera();
                        Toast.makeText(CameraActivity.this, R.string.chats_addAttachment_takeVideo_failure,
                                Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    recordButton.setIconDrawable(stopIcon);

                    Handler handler = new Handler();
                    Runnable r = new Runnable() {
                        public void run() {
                            recordButton.setEnabled(true);
                        }
                    };
                    handler.postDelayed(r, 1000);
                }
            }
        };
        recordButton.setOnClickListener(mButtonOnClickListener);
    }

    @Override
    public void onPauseActivity() {
        super.onPauseActivity();
        finish();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraView != null) {
            cameraView.releaseRecorder();
            cameraView.releaseCamera();
        }
    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (what == 801) // maximale Dateigroesze erreicht
        {
            mButtonOnClickListener.onClick(recordButton);
            Toast.makeText(CameraActivity.this, R.string.record_video_max_filesize_reached,
                    Toast.LENGTH_LONG).show();
        }
    }
}
