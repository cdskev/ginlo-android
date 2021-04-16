// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.chat;

import eu.ginlo_apps.ginlo.model.chat.ChannelChatItemVO;
import eu.ginlo_apps.ginlo.model.param.MessageDestructionParams;

public class ChannelSelfDestructionChatItemVO
        extends ChannelChatItemVO {

    public MessageDestructionParams destructionParams;

    public ChannelSelfDestructionChatItemVO() {
        super();
    }
}
