// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.device;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.common.BitMatrix;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.models.CouplingRequestModel;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.model.QRCodeModel;
import eu.ginlo_apps.ginlo.util.BitmapUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.MetricsUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.Timer;
import java.util.TimerTask;

public class DeviceCoupleNewActivity extends NewBaseActivity {
    private Account mAccount;
    private Timer mCountdownTimer;
    private long mEndTime;
    private TextView mTimerLabel;
    private String mTan;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        mTimerLabel = findViewById(R.id.device_couple_new_timer_tv);
        TextView mTanLabel1 = findViewById(R.id.device_couple_new_tan_1_tv);
        TextView mTanLabel2 = findViewById(R.id.device_couple_new_tan_2_tv);
        TextView mTanLabel3 = findViewById(R.id.device_couple_new_tan_3_tv);

        ImageView qrCode = findViewById(R.id.device_couple_new_qr_img);
        AccountController accountController = getSimsMeApplication().getAccountController();

        mAccount = accountController.getAccount();
        if (mAccount == null) {
            finish();
            return;
        }

        String tan = StringUtil.generatePassword(9);

        if (mTanLabel1 != null
                && mTanLabel2 != null
                && mTanLabel3 != null
                && tan.length() == 9) {
            mTanLabel1.setText(tan.substring(0, 3));
            mTanLabel2.setText(tan.substring(3, 6));
            mTanLabel3.setText(tan.substring(6, 9));
        }

        mTan = tan;

        // Endzeit = Startzeit + 5 Minuten
        mEndTime = System.currentTimeMillis() + (1000 * 60 * 5);
        mCountdownTimer = new Timer();

        mCountdownTimer.schedule(new CountdownTimerTask(), 0, 100);

        final GenerateQrCodeTask qrTask = new GenerateQrCodeTask(MetricsUtil.getDisplayMetrics(this).widthPixels,
                MetricsUtil.getDisplayMetrics(this).heightPixels,
                qrCode, mAccount, tan);

        qrTask.execute(null, null, null);

        showIdleDialog();
        getSimsMeApplication().getAccountController().coupleInitialiseCoupling(tan, mAccount, new GenericActionListener<Void>() {
            @Override
            public void onSuccess(final Void object) {
                dismissIdleDialog();
                startListening();
            }

            @Override
            public void onFail(final String message, final String errorIdent) {
                dismissIdleDialog();
                DialogBuilderUtil.buildErrorDialog(DeviceCoupleNewActivity.this, getString(R.string.device_request_tan_error_coupling_failed)).show();
                mTan = null;
            }
        });
    }

    private void startListening() {
        getSimsMeApplication().getAccountController().coupleGetCouplingRequest(mTan, mAccount, new GenericActionListener<CouplingRequestModel>() {
            @Override
            public void onSuccess(final CouplingRequestModel object) {
                final Intent intent = new Intent(DeviceCoupleNewActivity.this, DeviceCoupleConfirmActivity.class);
                intent.putExtra(DeviceCoupleConfirmActivity.EXTRA_TAN, mTan);
                startActivity(intent);
                finish();
            }

            @Override
            public void onFail(final String message, final String errorIdent) {
                if (mTan != null) {
                    if (StringUtil.isEqual(LocalizedException.BACKEND_REQUEST_FAILED, errorIdent) && getSecondsLeft() > 0) {
                        startListening();
                    } else {
                        DialogBuilderUtil.buildErrorDialog(DeviceCoupleNewActivity.this, getString(R.string.device_request_tan_error_coupling_failed)).show();
                        mTan = null;
                    }
                }
            }
        });
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_device_couple_new;
    }

    @Override
    protected void onResumeActivity() {

    }

    public void handleCancelClick(View v) {
        if (mTan != null) {
            showIdleDialog();
            String tan = mTan;
            mTan = null;
            getSimsMeApplication().getAccountController().coupleCancelCoupling(tan, mAccount, new GenericActionListener<Void>() {
                @Override
                public void onSuccess(final Void object) {
                    dismissIdleDialog();
                    finish();
                }

                @Override
                public void onFail(final String message, final String errorIdent) {
                    dismissIdleDialog();
                    finish();
                }
            });
        } else {
            finish();
        }
    }

    private long getSecondsLeft() {
        long secondsLeft = (mEndTime - System.currentTimeMillis()) / 1000L;
        if (secondsLeft < 0) {
            secondsLeft = 0;
        }
        return secondsLeft;
    }

    private void updateCountdownLabel() {
        long secondsLeft = getSecondsLeft();

        int minutesLeft = (int) (secondsLeft / 60L);
        secondsLeft = (int) (secondsLeft % 60L);

        String timeLabel = minutesLeft + ":";
        if (secondsLeft < 10) {
            timeLabel += "0";
        }
        timeLabel += secondsLeft;

        if (mTimerLabel != null) {
            final String newText = getString(R.string.device_couple_new_timer_text, timeLabel);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTimerLabel.setText(newText);
                }
            });
        }
    }

    class CountdownTimerTask
            extends TimerTask {
        public void run() {
            updateCountdownLabel();
        }
    }

    private class GenerateQrCodeTask
            extends AsyncTask<Void, Void, Bitmap> {
        private final String TAG = GenerateQrCodeTask.class.getSimpleName();
        private final int mWidthPixel;
        private final int mHeightPixel;
        private final ImageView mQrCodeImageView;
        private final Account mAccount;
        private final String mQRCodeData;

        GenerateQrCodeTask(final int widthPixel,
                           final int heightPixel,
                           final ImageView qrCodeImageView,
                           final Account account,
                           final String qrCodeData) {
            mWidthPixel = widthPixel;
            mHeightPixel = heightPixel;
            mQrCodeImageView = qrCodeImageView;
            mAccount = account;
            mQRCodeData = qrCodeData;
        }

        @Override
        protected Bitmap doInBackground(final Void... params) {
            Bitmap qrCodeBitmap = null;
            final int displayWidth = Math.min(mWidthPixel, mHeightPixel);
            final int size = Math.round(displayWidth - (displayWidth * 0.1f));

            if (!StringUtil.isNullOrEmpty(mAccount.getAccountID())) {
                QRCodeModel qrm = new QRCodeModel(mAccount.getAccountID() + "|" + mQRCodeData);
                qrCodeBitmap = qrm.createQRCodeBitmap(size);
            }
            return qrCodeBitmap;
        }

        @Override
        protected void onPostExecute(final Bitmap result) {
            super.onPostExecute(result);

            if (result != null) {
                mQrCodeImageView.setImageBitmap(result);
            }
        }
    }
}
