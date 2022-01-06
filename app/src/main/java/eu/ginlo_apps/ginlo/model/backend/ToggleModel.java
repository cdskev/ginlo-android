// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend;

import eu.ginlo_apps.ginlo.model.backend.ToggleChildModel;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ToggleModel {

    public String type;

    public String ident;

    public String label;

    public String filterOn;

    public String filterOff;

    public String defaultValue;

    public ToggleChildModel[] children;

    public int depth;
}
