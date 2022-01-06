// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend;

import java.io.Serializable;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ToggleSettingsModel
        implements Serializable {

    public String filter;

    public String value;

    public ToggleSettingsModel() {
    }

    public ToggleSettingsModel(String filter,
                               String value) {
        this.filter = filter;
        this.value = value;
    }
}
