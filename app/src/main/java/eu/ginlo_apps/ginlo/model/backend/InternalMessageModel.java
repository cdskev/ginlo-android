// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend;

import eu.ginlo_apps.ginlo.model.backend.BaseMessageModel;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class InternalMessageModel
        extends BaseMessageModel {

    public String from;

    public String to;

    public byte[] data;

    public InternalMessageModel() {
    }
}
