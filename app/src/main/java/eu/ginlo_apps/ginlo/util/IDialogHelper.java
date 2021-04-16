// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;

/**
 * Created by Florian on 13.12.16.
 */

public interface IDialogHelper {
    AlertDialogWrapper getMessageSendErrorDialog(final BaseActivity activity, final String errorMessage, final String errorIdentifier);
}
