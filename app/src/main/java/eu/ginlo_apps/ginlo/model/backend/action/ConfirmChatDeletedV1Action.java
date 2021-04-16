// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.action.Action;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ConfirmChatDeletedV1Action
        extends Action {

    private final String mGuid;

    public ConfirmChatDeletedV1Action(String guid) {
        mGuid = guid;
    }

    public String getGuid() {
        return mGuid;
    }
}
