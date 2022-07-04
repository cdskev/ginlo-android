// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.chat.overview;

import eu.ginlo_apps.ginlo.greendao.Chat;

public class BaseChatOverviewItemVO {
    public static final int MSG_MEDIA_TYPE_FAILED = 0;
    public static final int MSG_MEDIA_TYPE_TEXT = 1;
    public static final int MSG_MEDIA_TYPE_IMAGE = 2;
    public static final int MSG_MEDIA_TYPE_MOVIE = 3;
    public static final int MSG_MEDIA_TYPE_AUDIO = 4;
    public static final int MSG_MEDIA_TYPE_LOCATION = 5;
    public static final int MSG_MEDIA_TYPE_VCARD = 6;
    public static final int MSG_MEDIA_TYPE_DESTROY = 7;
    public static final int MSG_MEDIA_TYPE_FILE = 8;
    public static final int MSG_MEDIA_TYPE_AVC = 9;
    public static final int MSG_MEDIA_TYPE_GINLOCONTROL = 10;
    public static final int MSG_MEDIA_TYPE_RICH_CONTENT = 11;

    public Chat chat;
    public long datesend;
    public boolean hasSendError;
    public boolean isSystemInfo;
    public Long dateSendTimed;
    private String mChatGuid;
    private String mTitle;
    private int mState;
    private int mMediaType;

    BaseChatOverviewItemVO() {
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String newTitle) {
        mTitle = newTitle;
    }

    public int getState() {
        return mState;
    }

    public void setState(int state) {
        mState = state;
    }

    public String getChatGuid() {
        return mChatGuid;
    }

    public void setChatGuid(String guid) {
        mChatGuid = guid;
    }

    public int getMediaType() {
        return mMediaType;
    }

    public void setMediaType(int mediaType) {
        this.mMediaType = mediaType;
    }

    @Override
    public int hashCode() {
        return mChatGuid != null ? mChatGuid.hashCode() : -1;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        } else {
            if (obj instanceof BaseChatOverviewItemVO) {
                return obj.hashCode() == this.hashCode();
            }
        }

        return false;
    }

    public long getLatestDate() {
        return datesend;
    }
}
