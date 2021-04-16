// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.action.Action;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ConfirmV1Action
        extends Action {

    public String[] guids;

    public String fromGuid;

    public long dateSend = 0;

    public ConfirmV1Action() {
    }
}
