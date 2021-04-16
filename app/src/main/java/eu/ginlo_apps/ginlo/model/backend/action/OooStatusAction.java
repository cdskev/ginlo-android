// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.action;

import com.google.gson.JsonObject;

import eu.ginlo_apps.ginlo.model.backend.action.Action;

/**
 * Created by sga
 */

public class OooStatusAction extends Action {

    public final JsonObject mJsonObject;

    public OooStatusAction(final JsonObject jsonObject) {
        super();
        super.name = ACTION_OOO_STATUS;
        mJsonObject = jsonObject;
    }
}
