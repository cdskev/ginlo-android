// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.chat;

import eu.ginlo_apps.ginlo.model.chat.AttachmentChatItemVO;
import eu.ginlo_apps.ginlo.model.param.MessageDestructionParams;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class SelfDestructionChatItemVO
        extends AttachmentChatItemVO {

    public static final int TYPE_TEXT = 1;

    public static final int TYPE_VIDEO = 2;

    public static final int TYPE_IMAGE = 3;

    public static final int TYPE_VOICE = 4;

    public String text;

    public int destructionType;

    public MessageDestructionParams destructionParams;

    public SelfDestructionChatItemVO() {
    }
}
