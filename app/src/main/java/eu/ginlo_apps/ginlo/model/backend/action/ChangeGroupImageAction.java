// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.action.Action;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ChangeGroupImageAction
        extends Action {

    public String groupGuid;

    public byte[] groupImage;

    public ChangeGroupImageAction() {
        super.name = ACTION_CHANGE_GROUP_IMAGE;
    }
}
