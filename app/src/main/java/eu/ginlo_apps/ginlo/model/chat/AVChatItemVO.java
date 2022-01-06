// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.chat;

import android.graphics.Bitmap;

import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;

// @author KS
public class AVChatItemVO extends BaseChatItemVO {

    public String room;
    public transient Bitmap image;

    public AVChatItemVO() {
    }
}
