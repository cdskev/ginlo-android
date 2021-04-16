// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.chat;

import org.json.JSONException;
import org.json.JSONObject;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.AppGinloControlMessage;

/**
 * @author KS
 *
 * This class is normally not necessary since APP_GINLO_CONTROL messages don't show up in chats.
 * However I implemented it for testing purposes. You may change getLayoutForChatItem() in
 * ChatAdapter.class appropriately.
 */

public class AppGinloControlChatItemVO extends BaseChatItemVO {

    private AppGinloControlMessage appGinloControlMessage;
    public String message;
    public String origMessageType;
    public String origMessageIdentifier;
    public String additionalPayload;

    public String displayMessage;

    public AppGinloControlChatItemVO() {
        resetValues();
    }

    private void resetValues() {
        appGinloControlMessage = null;
        message = "";
        origMessageType = "";
        origMessageIdentifier = "";
        additionalPayload = "";
        displayMessage = "";
    }

    public void loadControlMessageFromString (String jsonString, SimsMeApplication application) {
        JSONObject controlMessage = null;
        try {
            controlMessage = new JSONObject(jsonString);
        } catch (JSONException e) {
            LogUtil.e("AppGinloControlChatItemVO", "Could not load controlMessage contents from " + jsonString);
        }

        if(controlMessage != null) {
            appGinloControlMessage = new AppGinloControlMessage(controlMessage);
            this.message = appGinloControlMessage.message;
            this.origMessageType = appGinloControlMessage.origMessageType;
            this.origMessageIdentifier = appGinloControlMessage.origMessageIdentifier;
            this.additionalPayload = appGinloControlMessage.additionalPayload;

            final String x = AppGinloControlMessage.MESSAGE_AVCALL_ACCEPTED;

            switch (this.message) {
                case AppGinloControlMessage.MESSAGE_AVCALL_ACCEPTED:
                    this.displayMessage = application.getString(R.string.chats_AVC_call_answered);
                    break;
                case AppGinloControlMessage.MESSAGE_AVCALL_REJECTED:
                    this.displayMessage = application.getString(R.string.chats_AVC_call_rejected);
                    break;
                case AppGinloControlMessage.MESSAGE_AVCALL_EXPIRED:
                    this.displayMessage = application.getString(R.string.chats_AVC_call_expired);
                    break;
                default:
                    this.displayMessage = "unknown";
            }
        } else {
            resetValues();
        }
    }
}
