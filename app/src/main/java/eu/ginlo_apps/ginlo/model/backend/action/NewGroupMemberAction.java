// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.action.GroupAction;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class NewGroupMemberAction
        extends GroupAction {

    public String[] memberNames;

    public NewGroupMemberAction() {
        super();
        super.name = ACTION_NEW_GROUP_MEMBERS;
    }
}
