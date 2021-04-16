// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.action.Action;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ChangeProfileImageAction
        extends Action {

    public byte[] profileImage;

    public ChangeProfileImageAction() {
        super.name = ACTION_CHANGE_PROFILE_IMAGE;
    }
}
