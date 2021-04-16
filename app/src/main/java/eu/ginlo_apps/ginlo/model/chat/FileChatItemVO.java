// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.chat;

import eu.ginlo_apps.ginlo.model.chat.AttachmentChatItemVO;

/**
 * Created by Florian on 03.02.16.
 */
public class FileChatItemVO extends AttachmentChatItemVO {
    public String fileName;
    public String fileSize;
    public String fileMimeType;

    public FileChatItemVO() {
    }
}

