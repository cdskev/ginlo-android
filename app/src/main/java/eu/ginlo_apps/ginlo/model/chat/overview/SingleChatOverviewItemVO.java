// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.chat.overview;

import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.model.chat.overview.ChatOverviewItemVO;

public class SingleChatOverviewItemVO
        extends ChatOverviewItemVO {

    public boolean isSystemChat;

    @Override
    public int getState() {
        if (isSystemChat) {
            return Contact.STATE_SIMSME_SYSTEM;
        }
        return super.getState();
    }
}
