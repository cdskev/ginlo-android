// Copyright (c) 2020-2021 ginlo.net GmbH

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
    private View mView;

    private SimsMeApplication mApplication;
    private AVChatController avChatController;
    private NotificationController notificationController;

    public AVCallMenuActivity() {
        mApplication = SimsMeApplication.getInstance();
        avChatController = mApplication.getAVChatController();
        notificationController = mApplication.getNotificationController();

        mFromNotification = -1;
        mAction = ACTION_NONE;
        mView = null;
    }

    // Turn screen on, if device is sleeping/locked
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
    protected void onCreate(Bundle savedInstanceState) {
        // May be null, which is the case if Android version is below 8.0 (Oreo)
        if (avChatController == null) {
            finish();
            return;
        }

        super.onCreate(savedInstanceState);

        // Make sure to turn screen on if device is sleeping/locked
        activateAndUnlockScreen();

        // First, let's have a look where we come from and what to do ...
        mFromNotification = getIntent().hasExtra(EXTRA_FROM_NOTIFICATION) ? getIntent().getIntExtra(EXTRA_FROM_NOTIFICATION, -1) : -1;
        mAction = getIntent().hasExtra(EXTRA_ACTION) ? getIntent().getIntExtra(EXTRA_ACTION, ACTION_NONE) : ACTION_NONE;
        mSenderGuid = getIntent().getStringExtra(EXTRA_FROM_GUID);

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

                // If we are called by notification, set a timeout.
                if(mFromNotification != -1) {
                    final Handler dismissHandler = new Handler(Looper.getMainLooper());
                    Runnable dismissRunner = new Runnable() {
                        public void run() {
                            LogUtil.i(TAG, "AvcallActivity Timeout - dismissed!");
                            notificationController.dismissNotification(mFromNotification);
                            finish();
                        }
                    };
                    dismissHandler.postDelayed(dismissRunner, NotificationController.DISMISS_NOTIFICATION_TIMEOUT);
                    LogUtil.d(TAG, "Runner for fullscreen notification cancellation initialized!");
                }
                return;
            case ACTION_AUDIO_CALL:
                avChatController.setCallType(AVChatController.CALL_TYPE_AUDIO_ONLY);
                notificationController.dismissNotification(mFromNotification);
                avChatController.sendCallAcceptMessage(mSenderGuid, null);
                break;
            case ACTION_VIDEO_CALL:
                avChatController.setCallType(AVChatController.CALL_TYPE_AUDIO_VIDEO);
                notificationController.dismissNotification(mFromNotification);
                avChatController.sendCallAcceptMessage(mSenderGuid, null);
                break;
            case ACTION_DISMISS:
                // We don't want a call. Finish this and exit
                notificationController.dismissNotification(mFromNotification);
                avChatController.sendCallRejectMessage(mSenderGuid, null);
                finish();
                return;
            case ACTION_NONE:
                // ACTION_NONE means, that we may have been called directly - not through notification.
                // All should be set up already.
                break;
            default:
                // Should not happen
                LogUtil.w(TAG, "Undefined action in AVCActivity. Terminate.");
                avChatController.setCallStatus(AVChatController.CALL_STATUS_ERROR);
                finish();
                return;

        }
        if (!initAndStartAVCall()) {
            LogUtil.w(TAG, "Could not start AVC, initAndStartAVCall() failed for some reason.");
            avChatController.setCallStatus(AVChatController.CALL_STATUS_ERROR);
        }
        finish();
    }

    public void onButtonClick(View view) {
        boolean doCall = false;
        notificationController.dismissNotification(mFromNotification);

        // Make sure to turn screen on if device is sleeping/locked
        activateAndUnlockScreen();

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
        finish();
    }

    private boolean initAndStartAVCall() {
        if (!avChatController.initAVCOptions()) {
            return false;
        }

        return avChatController.startAVCall(this);
    }
}