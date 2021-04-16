// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.action.Action;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ChangeStatusAction
        extends Action {

    public String status;

    public ChangeStatusAction() {
        super.name = ACTION_CHANGE_STATUS;
    }
}
