// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import eu.ginlo_apps.ginlo.model.backend.MsgExceptionModel;

public class BackendResponse {
    public boolean isError = true;

    public JsonObject jsonObject;

    public JsonArray jsonArray;

    public String errorMessage;

    public MsgExceptionModel msgException;
}
