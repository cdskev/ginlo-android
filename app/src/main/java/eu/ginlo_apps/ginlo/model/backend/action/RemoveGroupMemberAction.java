// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.action.GroupAction;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class RemoveGroupMemberAction
        extends GroupAction {

    public RemoveGroupMemberAction() {
        super();
        super.name = ACTION_REMOVE_GROUP_MEMBER;
    }
}
