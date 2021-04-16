// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.register;

import android.content.DialogInterface;
import android.content.Intent;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.RegisterEmailActivity;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;

/**
 * Created by SGA on 24.10.2016.
 */

public class InitProfileActivityBusiness
        extends InitProfileActivity {

    protected void startNextActivity() {
        AccountController accountController = getSimsMeApplication().getAccountController();
        try {
            if (!accountController.hasEmailActivated() && !accountController.isDeviceManaged()) {
                String message = getResources().getString(R.string.dialog_link_email_address_text);
                String title = getResources().getString(R.string.dialog_link_email_address_title);
                String positiveButton = getResources().getString(R.string.dialog_link_email_address_positive_button);
                String negativeButton = getResources().getString(R.string.dialog_link_email_address_negative_button);

                DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                                        int which) {
                        Intent intent = new Intent(InitProfileActivityBusiness.this, RegisterEmailActivity.class);
                        intent.putExtra(RegisterEmailActivity.EXTRA_FIRST_RUN, true);
                        intent.putExtra(RegisterEmailActivity.EXTRA_RUN_AFTER_REGISTRATION, true);
                        startActivity(intent);
                        finish();
                        dialog.dismiss();
                    }
                };

                DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                                        int which) {
                        InitProfileActivityBusiness.super.startNextActivity();
                        dialog.dismiss();
                    }
                };

                final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this, message, true,
                        title,
                        positiveButton,
                        negativeButton,
                        positiveOnClickListener,
                        negativeOnClickListener);

                dialog.show();

            } else {
                InitProfileActivityBusiness.super.startNextActivity();
            }
        } catch (LocalizedException e) {
            InitProfileActivityBusiness.super.startNextActivity();
        }
    }
}
