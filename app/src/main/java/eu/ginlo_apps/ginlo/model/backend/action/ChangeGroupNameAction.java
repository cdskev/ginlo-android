// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.action.Action;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ChangeGroupNameAction
        extends Action {

    public String groupGuid;

    public String groupName;

    public ChangeGroupNameAction() {
        super.name = ACTION_CHANGE_GROUP_NAME;
    }
}
