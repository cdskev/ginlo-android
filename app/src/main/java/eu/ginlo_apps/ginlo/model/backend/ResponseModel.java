// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend;

import com.google.gson.JsonElement;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;

/**
 * Created by Florian on 12.01.18.
 */

public class ResponseModel {
    public boolean isError;
    public String errorMsg;
    public String errorIdent;
    public String errorExceptionMessage;
    public LocalizedException responseException;
    public JsonElement response;

    public void setError(BackendResponse response) {
        if (response.isError) {
            isError = true;
            errorMsg = response.errorMessage;
            if (response.msgException != null) {
                errorIdent = response.msgException.getIdent();
                if (response.msgException.getMessage() != null) {
                    errorExceptionMessage = response.msgException.getMessage();
                }

                if (errorIdent != null) {
                    responseException = new LocalizedException(errorIdent, errorExceptionMessage);
                }
            }
        }
    }
}
