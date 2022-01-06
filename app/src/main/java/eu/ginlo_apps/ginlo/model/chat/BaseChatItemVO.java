// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.chat;

import com.google.gson.JsonElement;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.model.CitationModel;
import java.io.Serializable;

public class BaseChatItemVO
        implements Serializable {

    public static final int DIRECTION_LEFT = 0;
    public static final int DIRECTION_RIGHT = 1;
    public static final int TYPE_SINGLE = 0;
    public static final int TYPE_GROUP = 1;
    public static final int TYPE_CHANNEL = 2;
    private static final long serialVersionUID = 8015592434853634336L;

    public boolean hasSendError;
    public boolean isValid;
    public long messageId = -1;
    public boolean hasRead;
    public boolean hasDownloaded;
    public int direction;
    public int type;
    public String name;
    public boolean isPriority;
    public CitationModel citation;
    String mToGuid;
    private long datesend;
    private long datesendConfirmed;
    private int mState;
    private String mMessageGuid;
    private String mFromGuid;
    private boolean isSelected;

    BaseChatItemVO() {
    }

    public String getMessageGuid() {
        return mMessageGuid;
    }

    public void setMessageGuid(String guid) {
        mMessageGuid = guid;
    }

    public String getFromGuid() {
        return mFromGuid;
    }

    public void setFromGuid(String guid) {
        mFromGuid = guid;
    }

    public String getToGuid() {
        return mToGuid;
    }

    public void setToGuid(String guid) {
        mToGuid = guid;
    }

    private void setDateSendConfirmed(Long date) {
        if (date == null) {
            datesendConfirmed = 0;
        } else {
            datesendConfirmed = date;
        }
    }

    public boolean isSendConfirmed() {
        return datesendConfirmed != 0;
    }

    public long getDateSend() {
        return datesend;
    }

    public void setDateSend(Long date) {
        if (date == null) {
            datesend = 0;
        } else {
            datesend = date;
        }
    }

    public void setHasSendError(Boolean val) {
        if (val == null) {
            hasSendError = false;
        } else {
            hasSendError = val;
        }
    }

    public void setCitation(final JsonElement dataJson) {
        if (dataJson != null) {
            citation = new CitationModel(dataJson);
        }
    }

    public void setCommonValues(Message message) {
        setHasSendError(message.getHasSendError());
        setDateSend(message.getDateSend());
        setDateSendConfirmed(message.getDateSendConfirm());

        hasRead = message.hasReceiversRead();
        hasDownloaded = message.hasReceiversDownloaded();
    }

    public int getState() {
        return mState;
    }

    public void setState(int state) {
        mState = state;
    }

    public String getClassName() {
        return getClass().getName();
    }

    public void setSelected(boolean selected)  {  this.isSelected = selected; }

    public boolean isSelected () {  return this.isSelected; }
}
