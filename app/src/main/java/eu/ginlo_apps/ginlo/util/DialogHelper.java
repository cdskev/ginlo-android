// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.IDialogHelper;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;

/**
 * Created by Florian on 13.12.16.
 */

public class DialogHelper implements IDialogHelper {
    static DialogHelper instance;
    final SimsMeApplication mApplication;

    DialogHelper(SimsMeApplication application) {
        mApplication = application;
    }

    public static IDialogHelper getInstance(SimsMeApplication application) {
        if (instance == null) {
            instance = new DialogHelper(application);
        }

        return instance;
    }

    @Override
    public AlertDialogWrapper getMessageSendErrorDialog(BaseActivity activity, String errorMessage, String errorIdentifier) {
        return DialogBuilderUtil.buildErrorDialog(activity, errorMessage);
    }
}
