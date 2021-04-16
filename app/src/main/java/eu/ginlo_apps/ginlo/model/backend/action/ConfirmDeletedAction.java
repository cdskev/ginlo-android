// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.action.Action;

/**
 * Created by yves1 on 27.02.18.
 */

public class ConfirmDeletedAction
        extends Action {
    private final String[] mMessageGuids;

    public ConfirmDeletedAction(String[] messageGuids) {
        mMessageGuids = messageGuids.clone();
    }

    public String[] getMessagesToDelete() {
        return mMessageGuids.clone();
    }
}
