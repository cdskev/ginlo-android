// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend;

import java.util.Date;

import eu.ginlo_apps.ginlo.model.backend.BaseMessageModel;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class PrivateMessageModel
        extends BaseMessageModel {

    public eu.ginlo_apps.ginlo.model.backend.KeyContainerModel from;

    public KeyContainerModel[] to;

    /**
     * Getimte Nachrichten
     */
    public Date dateSendTimed;

    public PrivateMessageModel() {
        int i;
        i = 0;
    }
}
