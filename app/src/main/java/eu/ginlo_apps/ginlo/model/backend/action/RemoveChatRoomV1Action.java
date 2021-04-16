// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.action.Action;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class RemoveChatRoomV1Action
        extends Action {

    private final String mGuid;

    public RemoveChatRoomV1Action(String guid) {
        mGuid = guid;
    }

    public String getGuid() {
        return mGuid;
    }
}
