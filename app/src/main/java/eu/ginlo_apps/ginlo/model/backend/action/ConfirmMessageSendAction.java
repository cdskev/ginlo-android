// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.ConfirmMessageSendModel;
import eu.ginlo_apps.ginlo.model.backend.action.Action;

/**
 * Created by yves1 on 28.02.18.
 */

public class ConfirmMessageSendAction
        extends Action {

    private final ConfirmMessageSendModel mConfirmMessageSendModel;

    public ConfirmMessageSendAction(ConfirmMessageSendModel confirmMessageSendModel) {
        super();
        mConfirmMessageSendModel = confirmMessageSendModel;
    }

    public String getMessageGuid() {
        if (mConfirmMessageSendModel == null) {
            return null;
        }
        return mConfirmMessageSendModel.guid;
    }
}
