// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.chat;

import android.graphics.Bitmap;

import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class AttachmentChatItemVO
        extends BaseChatItemVO {

    public transient Bitmap image;

    public String attachmentGuid;

    public String attachmentDesc;

    AttachmentChatItemVO() {
    }
}
