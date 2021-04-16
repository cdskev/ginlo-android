// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend;

import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.model.backend.MsgExceptionModel;

public class ApplicationError {
    public static String msgExceptionToErrorString(Resources resources,
                                                   MsgExceptionModel msgExceptionModel) {
        String code = msgExceptionModel.getIdent().replaceAll("-", "_");
        String errorString = "";

        try {
            int resourceId = resources.getIdentifier("service_" + code, "string", BuildConfig.APPLICATION_ID);

            errorString = resources.getString(resourceId);
        } catch (NotFoundException e) {
            errorString = msgExceptionModel.getMessage();
        }

        return errorString + " (" + msgExceptionModel.getIdent() + ")";
    }
}
