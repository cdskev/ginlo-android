// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller;

import android.content.Context;
import android.content.Intent;

import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import org.jitsi.meet.sdk.JitsiMeetUserInfo;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import eu.ginlo_apps.ginlo.AVCActivity;
import eu.ginlo_apps.ginlo.AVCallMenuActivity;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnSendMessageListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.AppGinloControlMessage;
import eu.ginlo_apps.ginlo.util.StringUtil;

public class AVChatController {
    private static final String TAG = AVChatController.class.getSimpleName();

    // TODO: Must go to compile config
    public static final String ROOM_STRING_DELIMITER = "@";

    public static final int CALL_STATUS_NONE = 0;
    public static final int CALL_STATUS_TERMINATED = 1;
    public static final int CALL_STATUS_RUNNING = 2;
    public static final int CALL_STATUS_ERROR = -1;

    public static final int AVC_CALL_ANSWER_TIME = 60; // 60 seconds
    public static final int AVC_CALL_TIMEOUT = 14400; // 4 hours in seconds

    public static final int CALL_TYPE_MUTED = 0;
    public static final int CALL_TYPE_AUDIO_ONLY = 1;
    public static final int CALL_TYPE_AUDIO_VIDEO = 2;

    private static final String ACTION_JITSI_MEET_CONFERENCE = "org.jitsi.meet.CONFERENCE";
    private static final String JITSI_MEET_CONFERENCE_OPTIONS = "JitsiMeetConferenceOptions";

    // Jitsi "FeatureFlags"
    public static final String ADD_PEOPLE_ENABLED = "add-people.enabled";
    public static final String CALENDAR_ENABLED = "calendar.enabled";
    public static final String CALL_INTEGRATION_ENABLED = "call-integration.enabled";
    public static final String CLOSE_CAPTIONS_ENABLED = "close-captions.enabled";
    public static final String CHAT_ENABLED = "chat.enabled";
    public static final String INVITE_ENABLED = "invite.enabled";
    public static final String IOS_RECORDING_ENABLED = "ios.recording.enabled";
    public static final String LIVE_STREAMING_ENABLED = "live-streaming.enabled";
    public static final String MEETING_NAME_ENABLED = "meeting-name.enabled";
    public static final String MEETING_PASSWORD_ENABLED = "meeting-password.enabled";
    public static final String PIP_ENABLED = "pip.enabled";
    public static final String RAISE_HAND_ENABLED = "raise-hand.enabled";
    public static final String RECORDING_ENABLED = "recording.enabled";
    public static final String TILE_VIEW_ENABLED = "tile-view.enabled";
    public static final String WELCOME_PAGE_ENABLED = "welcomepage.enabled";

    private static AVChatController instance;

    private JitsiMeetConferenceOptions mJitopts;

    // For AppGinloControlMessage
    private String mTargetGuid;

    private URL mServerURL;
    private String mRoom;
    private String mRoomPassword;
    private String mParams;
    private int mCallStatus;
    private String mMyName;
    private String mConferenceTopic;
    private boolean mAudioOnly;
    private boolean mMuteAudio;
    private boolean mMuteVideo;

    private AVChatController() {
        resetAVC();
    }

    public static AVChatController getInstance() {
        if (instance == null) {
            instance = new AVChatController();
        }
        return instance;
    }

    // Give server as String containing FQDN or URL
    private void setServerURL(final String Server) {
        String srv = BuildConfig.GINLO_AVC_SERVER_URL;
        try {
            if (!StringUtil.isNullOrEmpty(Server)) {
                if (Server.startsWith("https://")) {
                    srv = Server;
                } else {
                    srv = "https://" + Server;
                }
            }

            if(!StringUtil.isNullOrEmpty(srv)) {
                mServerURL = new URL(srv);
                return;
            }
        } catch (MalformedURLException e) {
            LogUtil.e(TAG, "Malformed URL for AVC received: " + srv);
        }
        mServerURL = null;
    }

    public void resetAVC() {
        mJitopts = null;
        mMyName = "";
        mTargetGuid = "";
        mConferenceTopic = "";
        mAudioOnly = true;
        mMuteAudio = true;
        mMuteVideo = true;
        resetRoomInfo();
    }

    private void resetRoomInfo() {
        setServerURL(null);
        mCallStatus = CALL_STATUS_NONE;
        mParams = "";
        mRoom = "";
        mRoomPassword = "";
    }

    public boolean rollAndSetNewRoomInfo() {
        String[] passwordAndRoom = rollNewPasswordAndRoom();
        return setRoomInfo(passwordAndRoom);
    }

    // Set roomInfo members for the next call.
    // Return false, if anything went wrong - resetAVC() is then called, no matter
    // what values were given.
    public boolean setRoomInfo(String[] roomInfo) {
        boolean allOk = true;
        resetRoomInfo();

        if (roomInfo != null) {
            switch (roomInfo.length) {
                case 2:
                    mRoomPassword = StringUtil.isNullOrEmpty(roomInfo[0]) ? "" : roomInfo[0];
                    mRoom = StringUtil.isNullOrEmpty(roomInfo[1]) ? "" : roomInfo[1];
                    break;
                case 3:
                    mRoomPassword = StringUtil.isNullOrEmpty(roomInfo[0]) ? "" : roomInfo[0];
                    mRoom = StringUtil.isNullOrEmpty(roomInfo[1]) ? "" : roomInfo[1];
                    setServerURL(roomInfo[2]);
                    break;
                case 4:
                    mRoomPassword = StringUtil.isNullOrEmpty(roomInfo[0]) ? "" : roomInfo[0];
                    mRoom = StringUtil.isNullOrEmpty(roomInfo[1]) ? "" : roomInfo[1];
                    setServerURL(roomInfo[2]);
                    mParams = StringUtil.isNullOrEmpty(roomInfo[3]) ? "" : roomInfo[3];
                    break;
                case 1:
                default:
                    LogUtil.e(TAG, "Malformed roomInfo for AVC in initRoom()! Resetting.");
                    allOk = false;
            }
        } else {
            allOk = false;
        }

        if (!allOk) {
            resetAVC();
        }
        return allOk;
    }

    public void setTargetGuid(String senderGuid) {
        mTargetGuid = StringUtil.isNullOrEmpty(senderGuid) ? "" : senderGuid;
    }

    public void setMyName(String myName) {
        mMyName = StringUtil.isNullOrEmpty(myName) ? "" : myName;
    }

    public void setConferenceTopic(String conferenceTopic) {
        mConferenceTopic = StringUtil.isNullOrEmpty(conferenceTopic) ? "" : conferenceTopic;
    }

    public void setCallType(int callType) {
        switch (callType) {
            case CALL_TYPE_AUDIO_VIDEO:
                mAudioOnly = false;
                mMuteAudio = false;
                mMuteVideo = false;
                break;
            case CALL_TYPE_AUDIO_ONLY:
                mAudioOnly = true;
                mMuteAudio = false;
                mMuteVideo = true;
                break;
            case CALL_TYPE_MUTED:
            default:
                mAudioOnly = true;
                mMuteAudio = true;
                mMuteVideo = true;
        }
    }

    public void setCallStatus(int callStatus) {
        mCallStatus = callStatus;
        LogUtil.d(TAG, "Call status is now: " + mCallStatus);
    }

    public static int getCallTimeoutMillis() {
        // Need value in Milliseconds
        return AVC_CALL_TIMEOUT * 1000;
    }

    public String getConferenceTopic() {
        return mConferenceTopic;
    }

    public int getCallStatus() {
        return mCallStatus;
    }

    public boolean isCallActive() {
        return mCallStatus == CALL_STATUS_RUNNING;
    }

    public String getParams() {
        return mParams;
    }

    // Return the password/room tuple.
    // Return null if anything goes wrong.
    public String[] getPasswordAndRoom() {
        String[] room = new String[2];
        room[0] = mRoomPassword;
        room[1] = mRoom;
        return room;
    }

    public String[] getRoomInfo() {
        String[] room = new String[4];
        room[0] = mRoomPassword;
        room[1] = mRoom;
        room[2] = mServerURL == null ? "" : mServerURL.toString();
        room[3] = mParams;
        return room;
    }

    public String getSerializedRoomInfo() {
        String srvString = mServerURL == null ? "" : mServerURL.getHost();
        return serializeRoomInfoMessageString(mRoomPassword, mRoom, srvString, getParams());
    }

    // Generate and return random room name and password with standard server.
    private String[] rollNewPasswordAndRoom() {
        String[] newRoom = new String[2];

        new UUID(-1, -1);
        UUID rn = UUID.randomUUID();
        UUID pw = UUID.randomUUID();

        newRoom[0] = pw.toString();
        newRoom[1] = rn.toString();
        return newRoom;
    }

    /**
     * The room info message string is the serialized Version of all necessary avc message info
     * that can directly be sent to the recipient.
     * Valid contents are:
     * roomPassword@room
     * roomPassword@room@server
     * rootPassword@room@server@params
     * Note: server must not be an url here, just a fqdn is used
     * @param roomPassword
     * @param room
     * @param server
     * @param params
     * @return
     */
    public static String serializeRoomInfoMessageString(String roomPassword, String room, String server, String params) {
        String roomInfo = null;

        // At least roomPassword and room must be given!
        if (!StringUtil.isNullOrEmpty(roomPassword) && !StringUtil.isNullOrEmpty(room)) {
            roomInfo = roomPassword + ROOM_STRING_DELIMITER + room;

            if (!StringUtil.isNullOrEmpty(server)) {
                roomInfo += ROOM_STRING_DELIMITER + server;

                if (!StringUtil.isNullOrEmpty(params)) {
                    roomInfo += ROOM_STRING_DELIMITER + params;
                }
            }
        }

        return roomInfo;
    }

    // Deserialize the given room info message string
    public static String[] deserializeRoomInfoMessageString(String roomInfoString) {
        String[] roomInfo = null;
        if (!StringUtil.isNullOrEmpty(roomInfoString)) {
            // Must have at least roomPassword and room, return null otherwise
            roomInfo = roomInfoString.split(ROOM_STRING_DELIMITER);
            if (roomInfo.length < 2) {
                LogUtil.e(TAG, "Malformed roomInfo for AVC received: " + roomInfoString);
                return null;
            }
        }
        return roomInfo;
    }

    public boolean initAVCOptions() {

        JitsiMeetUserInfo userInfo = new JitsiMeetUserInfo();
        userInfo.setDisplayName(mMyName);

        try {
            mJitopts = new JitsiMeetConferenceOptions.Builder()
                    .setServerURL(mServerURL)
                    .setWelcomePageEnabled(false)
                    .setRoom(mRoom)
                    .setUserInfo(userInfo)
                    .setAudioMuted(mMuteAudio)
                    .setVideoMuted(mMuteVideo)
                    .setAudioOnly(mAudioOnly)
                    .setSubject(mConferenceTopic)
                    .setFeatureFlag(ADD_PEOPLE_ENABLED, false)
                    .setFeatureFlag(CALENDAR_ENABLED, false)
                    .setFeatureFlag(CALL_INTEGRATION_ENABLED, true)
                    .setFeatureFlag(CLOSE_CAPTIONS_ENABLED, true)
                    .setFeatureFlag(CHAT_ENABLED, false)
                    .setFeatureFlag(INVITE_ENABLED, false)
                    .setFeatureFlag(IOS_RECORDING_ENABLED, false)
                    .setFeatureFlag(LIVE_STREAMING_ENABLED, false)
                    .setFeatureFlag(MEETING_NAME_ENABLED, true)
                    .setFeatureFlag(MEETING_PASSWORD_ENABLED, false)
                    .setFeatureFlag(PIP_ENABLED, true)
                    .setFeatureFlag(RAISE_HAND_ENABLED, true)
                    .setFeatureFlag(RECORDING_ENABLED, false)
                    .setFeatureFlag(TILE_VIEW_ENABLED, true)
                    .setFeatureFlag(WELCOME_PAGE_ENABLED, false)
                    .build();
        } catch (RuntimeException e) {
            LogUtil.e(TAG, "Cannot set options for AVC!", e);
            return false;
        }
        return true;
    }

    public boolean startAVCall(Context context) {
        if(mJitopts == null) {
            if(!initAVCOptions()) {
                return false;
            }
        }

        Intent intent = new Intent(context, AVCActivity.class);
        intent.setAction(ACTION_JITSI_MEET_CONFERENCE);
        intent.putExtra(JITSI_MEET_CONFERENCE_OPTIONS, mJitopts);
        context.startActivity(intent);
        return true;
    }

    // Prepare intent for starting audio-video-call
    // roomInfo may be a string like "password@room" or "password@room@server_url"
    // TODO: Accepting foreign server names is a spoofing/security issue!
    // Caller must then startActivity();
    // Set notificationId and action only, if we are called by notification intent
    // Call setMyName and setConferenceTopic first.
    private Intent prepareAVCroomActivity(String senderGuid, int notificationId, int action, Context context) {
        String[] avcRoom = getRoomInfo();
        if (avcRoom == null) {
            LogUtil.e(TAG, "Trying to prepare AVC intent with no room initialized!");
            return null;
        }

        Intent intent = new Intent(context, AVCallMenuActivity.class);
        intent.putExtra(AVCallMenuActivity.EXTRA_FROM_GUID, senderGuid);
        intent.putExtra(AVCallMenuActivity.EXTRA_ACTION, action);

        // If we are initiated by a notification:
        if (notificationId >= 0) {
            intent.putExtra(AVCallMenuActivity.EXTRA_FROM_NOTIFICATION, notificationId);
        }
        return intent;
    }

    // Create Intent to answer an audio call out of notification
    public Intent prepareAudioAVCall(String senderGuid, int notificationId, Context context) {
        setCallType(CALL_TYPE_AUDIO_ONLY);
        return prepareAVCroomActivity(senderGuid, notificationId, AVCallMenuActivity.ACTION_AUDIO_CALL, context);
    }

    // Create Intent to answer a video call out of notification
    public Intent prepareVideoAVCall(String senderGuid, int notificationId, Context context) {
        setCallType(CALL_TYPE_AUDIO_VIDEO);
        return prepareAVCroomActivity(senderGuid, notificationId, AVCallMenuActivity.ACTION_VIDEO_CALL, context);
    }

    // Create Intent to answer a video call out of notification
    public Intent prepareDismissCall(String senderGuid, int notificationId, Context context) {
        setCallType(CALL_TYPE_MUTED);
        return prepareAVCroomActivity(senderGuid, notificationId, AVCallMenuActivity.ACTION_DISMISS, context);
    }

    // Send APP_GINLO_CONTROL message to signal our action on incoming call
    public void sendAppGinloControlMessage(final String targetGuid,
                                           final AppGinloControlMessage controlMessage,
                                           final OnSendMessageListener onSendMessageListener) {

        LogUtil.d(TAG, "sendAppGinloControlMessage(): Sending " + controlMessage.toString() + " to "  + targetGuid);

        Contact contact = null;
        try {
            contact = SimsMeApplication.getInstance().getContactController().getContactByGuid(targetGuid);
        } catch (LocalizedException e) {
            LogUtil.i(TAG, "sendAppGinloControlMessage(): No contact for " + targetGuid);
        }

        if(contact != null) {
            SimsMeApplication.getInstance().getSingleChatController().sendAppGinloControl(targetGuid,
                    contact.getPublicKey(),
                    controlMessage,
                    onSendMessageListener);
        } else {
            LogUtil.w(TAG, "sendAppGinloControlMessage(): Send AGC message to group!");
            SimsMeApplication.getInstance().getGroupChatController().sendAppGinloControl(targetGuid,
                    null,
                    controlMessage,
                    onSendMessageListener);
        }
    }

    public void sendCallAcceptMessage(final String targetGuid, final OnSendMessageListener onSendMessageListener) {
        sendAppGinloControlMessage(targetGuid, new AppGinloControlMessage(
                AppGinloControlMessage.MESSAGE_AVCALL_ACCEPTED,
                AppGinloControlMessage.MESSAGE_TYPE_XGINLOCALLINVITE,
                getSerializedRoomInfo(),
                ""), onSendMessageListener);
    }

    public void sendCallRejectMessage(final String targetGuid, final OnSendMessageListener onSendMessageListener) {
        sendAppGinloControlMessage(targetGuid, new AppGinloControlMessage(
                AppGinloControlMessage.MESSAGE_AVCALL_REJECTED,
                AppGinloControlMessage.MESSAGE_TYPE_XGINLOCALLINVITE,
                getSerializedRoomInfo(),
                ""), onSendMessageListener);
    }

}
