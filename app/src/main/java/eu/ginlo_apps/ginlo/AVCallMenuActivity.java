// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo;

import androidx.appcompat.app.AppCompatActivity;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import eu.ginlo_apps.ginlo.activity.chat.GroupChatActivity;
import eu.ginlo_apps.ginlo.activity.chat.SingleChatActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AVChatController;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.PermissionUtil;

import static eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty;


public class AVCallMenuActivity extends AppCompatActivity {
    private static final String TAG = AVCallMenuActivity.class.getSimpleName();

    public static final String EXTRA_SERVER_URL = TAG + ".extraServer";
    public static final String EXTRA_ROOM = TAG + ".extraRoom";
    public static final String EXTRA_PASSWORD = TAG + ".extraPassword";
    public static final String EXTRA_TOPIC = TAG + ".extraTopic";
    public static final String EXTRA_MYNAME = TAG + ".extraMyName";
    public static final String EXTRA_AUDIO_ONLY = TAG + ".extraAudioOnly";
    public static final String EXTRA_FROM_NOTIFICATION = TAG + ".extraFromNotification";
    public static final String EXTRA_ACTION = TAG + ".extraAction";
    public static final String EXTRA_FROM_GUID = TAG + ".extraFromGuid";

    public static final int ACTION_NONE = 0;
    public static final int ACTION_DISMISS = 1;
    public static final int ACTION_AUDIO_CALL = 2;
    public static final int ACTION_VIDEO_CALL = 3;
    public static final int ACTION_ASK = 4;

    private String mSenderGuid;
    private int mFromNotification;
    private int mAction;
    private PermissionUtil mPermissionUtil;

    private final SimsMeApplication mApplication;
    private final AVChatController avChatController;
    private final NotificationController notificationController;

    public AVCallMenuActivity() {
        mApplication = SimsMeApplication.getInstance();
        avChatController = mApplication.getAVChatController();
        notificationController = mApplication.getNotificationController();

        mFromNotification = -1;
        mAction = ACTION_NONE;
    }

    /**
     * Turn screen on, if device is sleeping/locked
     */
    private void activateAndUnlockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            keyguardManager.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED  |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // May be null, which is the case if Android version is below 8.0 (Oreo)
        if (avChatController == null) {
            finish();
            return;
        }

        super.onCreate(savedInstanceState);

        // Init notification listener
        NotificationController.AVCNotificationListener notificationListener = new NotificationController.AVCNotificationListener() {
            @Override
            public void onCancelAVCNotification() {
                LogUtil.d(TAG, "onCancelAVCNotification: Called - finish this.");
                finish();
            }
        };
        notificationController.setAVCallMenuListener(notificationListener);

        // Make sure to turn screen on if device is sleeping/locked
        activateAndUnlockScreen();

        // First, let's have a look where we come from and what to do ...
        mFromNotification = getIntent().getIntExtra(EXTRA_FROM_NOTIFICATION, -1);
        mAction = getIntent().getIntExtra(EXTRA_ACTION, ACTION_NONE);
        mSenderGuid = getIntent().getStringExtra(EXTRA_FROM_GUID);

        LogUtil.d(TAG, "onCreate: Start for EXTRA_ACTION = " + mAction);

        // Now see whether we have the user choose the action. If we have ACTION_ASK set, show call menu.
        switch (mAction) {
            case ACTION_ASK:
                setContentView(R.layout.activity_avcall_fullscreen);

                String ct = avChatController.getConferenceTopic();
                if(!isNullOrEmpty(ct)) {
                    TextView tv = findViewById(R.id.avcall_fsnotification_title);
                    if(tv != null) {
                        tv.setText(mApplication.getResources().getString(R.string.notification_new_avc) + " " + ct);
                    }
                }

                // Only play own ringtone if there was not initialization by notification
                // which plays a ringtone on their own.
                if(mFromNotification == -1) {
                    notificationController.playRingtone();
                }

                // Cancel after DISMISS_NOTIFICATION_TIMEOUT
                final Handler dismissHandler = new Handler(Looper.getMainLooper());
                Runnable dismissRunner = new Runnable() {
                    public void run() {
                        LogUtil.i(TAG, "AvcallMenuActivity Timeout - dismissed!");
                        // Cancel all AVC notifications and finish this upon callback.
                        notificationController.cancelAVCallNotification();
                    }
                };
                dismissHandler.postDelayed(dismissRunner, NotificationController.DISMISS_NOTIFICATION_TIMEOUT);
                LogUtil.d(TAG, "Runner for fullscreen activity cancellation initialized!");
                return;
            case ACTION_AUDIO_CALL:
                avChatController.setCallType(AVChatController.CALL_TYPE_AUDIO_ONLY);
                avChatController.sendCallAcceptMessage(mSenderGuid, null);
                break;
            case ACTION_VIDEO_CALL:
                avChatController.setCallType(AVChatController.CALL_TYPE_AUDIO_VIDEO);
                avChatController.sendCallAcceptMessage(mSenderGuid, null);
                break;
            case ACTION_DISMISS:
                // We don't want a call. Finish this and exit
                avChatController.sendCallRejectMessage(mSenderGuid, null);
                // Cancel all AVC notifications and finish this upon callback.
                notificationController.cancelAVCallNotification();
                return;
            case ACTION_NONE:
                // ACTION_NONE means, that we may have been called directly - not through notification.
                // All should be set up already.
                break;
            default:
                // Should not happen
                LogUtil.e(TAG, "Undefined action in AVCActivity. Terminate.");
                avChatController.setCallStatus(AVChatController.CALL_STATUS_ERROR);
                finish();
                return;

        }

        if (!initAndStartAVCall()) {
            LogUtil.w(TAG, "Could not start AVC, initAndStartAVCall() failed for some reason.");
            avChatController.setCallStatus(AVChatController.CALL_STATUS_ERROR);
        }

        // Cancel all AVC notifications and finish this upon callback.
        notificationController.cancelAVCallNotification();
    }

    public void onLayoutClick(View view) {
        notificationController.stopRingtone();
    }

        public void onButtonClick(View view) {
        boolean doCall = false;

        // Make sure to turn screen on if device is sleeping/locked
        activateAndUnlockScreen();

        LogUtil.d(TAG, "onButtonClick: view.getId = " + view.getId());

        switch (view.getId()) {
            case R.id.button_audio_pickup:
                LogUtil.i(TAG, "Audio Pickup");
                avChatController.sendCallAcceptMessage(mSenderGuid, null);
                avChatController.setCallType(AVChatController.CALL_TYPE_AUDIO_ONLY);
                doCall = true;
                break;
            case R.id.button_video_pickup:
                LogUtil.i(TAG, "Video Pickup");
                avChatController.sendCallAcceptMessage(mSenderGuid, null);
                avChatController.setCallType(AVChatController.CALL_TYPE_AUDIO_VIDEO);
                doCall = true;
                break;
            case R.id.button_dismiss:
                LogUtil.i(TAG, "Dismiss Call");
                if(!isNullOrEmpty(mSenderGuid)) {
                    avChatController.sendCallRejectMessage(mSenderGuid, null);
                    Intent intent = new Intent(mApplication, GuidUtil.isChatRoom(mSenderGuid) ? GroupChatActivity.class : SingleChatActivity.class);
                    startActivity(intent);
                }
                break;
            default:
                LogUtil.i(TAG, "Close AVCActivity and reset controller");
        }

        if (doCall) {
            if (!initAndStartAVCall()) {
                LogUtil.w(TAG, "Could not start AVC, initAndStartAVCall() failed for some reason.");
                avChatController.setCallStatus(AVChatController.CALL_STATUS_ERROR);
            }
        }
        // Cancel all AVC notifications and finish this upon callback.
        notificationController.cancelAVCallNotification();
    }

    private boolean initAndStartAVCall() {
        if (!avChatController.initAVCOptions()) {
            return false;
        }

        return avChatController.startAVCall(this);
    }
}
