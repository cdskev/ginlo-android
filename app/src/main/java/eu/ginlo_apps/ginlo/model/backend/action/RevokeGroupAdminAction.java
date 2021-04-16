// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.action.GroupAction;

/**
 * Created by Florian on 08.12.16.
 */

public class RevokeGroupAdminAction extends GroupAction {
    public RevokeGroupAdminAction() {
        super();
        super.name = ACTION_REVOKE_GROUP_ADMINS;
    }
}
