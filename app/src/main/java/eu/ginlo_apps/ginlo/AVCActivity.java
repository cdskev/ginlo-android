// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.jitsi.meet.sdk.JitsiMeetActivity;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;

import java.util.Map;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AVChatController;
import eu.ginlo_apps.ginlo.log.LogUtil;


/**
 * @author: KS
 * AVC class for audio video calls
 */

public class AVCActivity extends JitsiMeetActivity  {
    private static final String TAG = "AVCActivity";

    private final SimsMeApplication mApplication;
    private final AVChatController avChatController;

    private static final String ACTION_JITSI_MEET_CONFERENCE = "org.jitsi.meet.CONFERENCE";
    private static final String JITSI_MEET_CONFERENCE_OPTIONS = "JitsiMeetConferenceOptions";

    public AVCActivity() {
        mApplication = SimsMeApplication.getInstance();
        avChatController = mApplication.getAVChatController();
    }

    public void startAVC(Context context, JitsiMeetConferenceOptions options) {
        Intent intent = new Intent(context, AVCActivity.class);
        intent.setAction(ACTION_JITSI_MEET_CONFERENCE);
        intent.putExtra(JITSI_MEET_CONFERENCE_OPTIONS, options);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // May be null, which is the case if Android version is below 8.0 (Oreo)
        if (avChatController == null) {
            finish();
            return;
        }
        super.onCreate(savedInstanceState);
        avChatController.setCallStatus(AVChatController.CALL_STATUS_RUNNING);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtil.d(TAG, "==============> AVCActivity onResume() on CC instance " + avChatController + " called with CALL_STATUS: " + avChatController.getCallStatus());
        if (avChatController.getCallStatus() != AVChatController.CALL_STATUS_RUNNING
                && avChatController.getCallStatus() != AVChatController.CALL_STATUS_NONE) {
            finish();
        }
    }

    @Override
    public void onDestroy() {
        LogUtil.d(TAG, "==============> AVCActivity onDestroy() on CC instance " + avChatController + " called with CALL_STATUS: " + avChatController.getCallStatus());
        super.onDestroy();
    }

    @Override
    public void finish() {
        LogUtil.d(TAG, "==============> AVCActivity finish() called on CC instance " + avChatController + " - reset AVC");
        avChatController.resetAVC();
        super.finish();
    }

    @Override
    public void onUserLeaveHint() {
        LogUtil.d(TAG, "==============> AVCActivity onUserLeaveHint() called");
        super.onUserLeaveHint();
    }

    @Override
    public void onBackPressed() {
        LogUtil.d(TAG, "==============> AVCActivity onBackPressed() on CC instance " + avChatController + " called with CALL_STATUS: " + avChatController.getCallStatus());
        avChatController.resetAVC();
        super.onBackPressed();
    }
    @Override
    public void onConferenceJoined(Map<String, Object> data) {
        LogUtil.d(TAG, "==============> AVCActivity onConferenceJoined() called on CC instance " + avChatController + "  with: " + data);
        avChatController.setCallStatus(AVChatController.CALL_STATUS_RUNNING);
        super.onConferenceJoined(data);
    }

    @Override
    public void onConferenceTerminated(Map<String, Object> data) {
        LogUtil.d(TAG, "==============> AVCActivity onConferenceTerminated() called on CC instance " + avChatController + "  with: " + data);
        avChatController.setCallStatus(AVChatController.CALL_STATUS_TERMINATED);
        super.onConferenceTerminated(data);
    }

    @Override
    public void onConferenceWillJoin(Map<String, Object> data) {
        LogUtil.d(TAG, "==============> AVCActivity onConferenceWillJoin() called on CC instance " + avChatController + "  with: " + data);
        avChatController.setCallStatus(AVChatController.CALL_STATUS_RUNNING);
        super.onConferenceWillJoin(data);
    }
}