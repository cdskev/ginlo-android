// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.MimeType;

/**
 * @author KS
 *
 * Class to hold APP_GINLO_CONTROL message.
 * It consists of the message itself and additional data describing where it belongs to.
 * These control messages never come alone but always have a regular message where they
 * correspond to.
 */

public class AppGinloControlMessage {

    private static final String TAG = AppGinloControlMessage.class.getSimpleName();

    // JSON payload of AGC message
    public static final String MESSAGE_KEY = "message";
    public static final String ORIG_MESSAGE_TYPE_KEY = "orig-message-type";
    public static final String ORIG_MESSAGE_IDENTIFIER_KEY = "orig-message-identifier";
    public static final String ADDITIONAL_PAYLOAD_KEY = "additional-payload";

    // AVC control message types
    public static final String MESSAGE_TYPE_XGINLOCALLINVITE = "text/x-ginlo-call-invite";
    public static final String MESSAGE_AVCALL_ACCEPTED = "avCallAccepted";
    public static final String MESSAGE_AVCALL_REJECTED = "avCallRejected";
    public static final String MESSAGE_AVCALL_EXPIRED = "avCallExpired";

    public JSONObject controlMessage = null;

    public String message = "";
    public String origMessageType = "";
    public String origMessageIdentifier = "";
    public String additionalPayload = "";

    // Must know about the corresponding message

    public AppGinloControlMessage(final JSONObject controlMessage) {
        this.controlMessage = controlMessage;

        try {
            message = controlMessage.getString(MESSAGE_KEY);
            origMessageType = controlMessage.getString(ORIG_MESSAGE_TYPE_KEY);
            origMessageIdentifier = controlMessage.getString(ORIG_MESSAGE_IDENTIFIER_KEY);
            additionalPayload = controlMessage.getString(ADDITIONAL_PAYLOAD_KEY);
        } catch (JSONException e) {
            LogUtil.e(TAG, "Could not load controlMessage contents from " + controlMessage.toString());
            this.controlMessage = null;
            message = "";
            origMessageType = "";
            origMessageIdentifier = "";
            additionalPayload = "";
        }
    }

    public AppGinloControlMessage(final String message,
                                  final String origMessageType,
                                  final String origMessageIdentifier,
                                  final String additionalPayload) {

        this.message = message;
        this.origMessageType = origMessageType;
        this.origMessageIdentifier = origMessageIdentifier;
        this.additionalPayload = additionalPayload;

        // Also build a JSON ...
        if(controlMessage == null) {
            controlMessage = new JSONObject();
        }
        try {
            controlMessage.putOpt(MESSAGE_KEY, message);
            controlMessage.putOpt(ORIG_MESSAGE_TYPE_KEY, origMessageType);
            controlMessage.putOpt(ORIG_MESSAGE_IDENTIFIER_KEY, origMessageIdentifier);
            controlMessage.putOpt(ADDITIONAL_PAYLOAD_KEY, additionalPayload);
        } catch (JSONException e) {
            LogUtil.e(TAG, "Could not create JSON of controlMessage");
            this.controlMessage = null;
            this.message = "";
            this.origMessageType = "";
            this.origMessageIdentifier = "";
            this.additionalPayload = "";
        }
    }

    @NotNull
    @Override
    public String toString() {
        if(controlMessage != null) {
            return controlMessage.toString();
        } else {
            return "";
        }
    }
}
