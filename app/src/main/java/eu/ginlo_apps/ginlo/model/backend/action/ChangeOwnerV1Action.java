// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.action.Action;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ChangeOwnerV1Action
        extends Action {

    public String roomGuid;

    public String accountGuid;

    public ChangeOwnerV1Action() {
    }
}
