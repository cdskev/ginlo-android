// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.chat;

import android.graphics.Bitmap;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.model.chat.TextChatItemVO;
import eu.ginlo_apps.ginlo.util.StringUtil;

public class ChannelChatItemVO
        extends TextChatItemVO {

    public String messageHeader;

    public String messageContent;

    public String shortLinkText;

    public transient Bitmap image;

    public String attachmentGuid;

    public String section;

    public String channelType;

    @Override
    public String getClassName() {
        if (StringUtil.isEqual(Channel.TYPE_SERVICE, channelType)) {
            return super.getClassName() + "s";
        } else {
            return super.getClassName();
        }
    }
}
