// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.content.DialogInterface;
import android.content.Intent;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.register.PurchaseLicenseActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.context.SimsMeApplicationBusiness;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactControllerBusiness;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;

/**
 * Created by Florian on 13.12.16.
 */

public class DialogHelperBusiness extends DialogHelper {
    private DialogHelperBusiness(SimsMeApplication application) {
        super(application);
    }

    public static DialogHelper getInstance(SimsMeApplication application) {
        if (instance == null) {
            instance = new DialogHelperBusiness(application);
        }

        return instance;
    }

    @Override
    public AlertDialogWrapper getMessageSendErrorDialog(final BaseActivity activity, final String errorMessage, final String errorIdentifier) {
        if (StringUtil.isEqual(errorIdentifier, LocalizedException.ACCOUNT_DURATION_EXPIRED)) {
            try {
                AccountController accountController = mApplication.getAccountController();

                if (!accountController.getAccount().isAutorenewingLicense()) {
                    LogUtil.i(ContactControllerBusiness.class.getSimpleName(), "expired License (non autorenewing)");

                    DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            //Class<?> classForNextIntent = SystemUtil.getClassForBuildConfigClassname(BuildConfig.ACTIVITY_AFTER_CONFIRM_ACCOUNT);
                            final Intent intent = new Intent(activity, PurchaseLicenseActivity.class);
                            intent.putExtra(PurchaseLicenseActivity.EXTRA_DONT_FORWARD_TO_OVERVIEW, true);
                            activity.startActivity(intent);
                        }
                    };

                    return DialogBuilderUtil.buildResponseDialog(activity,
                            mApplication.getString(R.string.dialog_licence_is_expired),
                            mApplication.getString(R.string.dialog_licence_is_expired_title),
                            mApplication.getString(R.string.dialog_licence_is_about_to_expire_positive),
                            null,
                            positiveOnClickListener,
                            null);
                }
            } catch (LocalizedException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            }
        }

        return super.getMessageSendErrorDialog(activity, errorMessage, errorIdentifier);
    }

    public AlertDialogWrapper getManagementRequestDialog(final BaseActivity activity, DialogInterface.OnClickListener positiveListener, DialogInterface.OnClickListener negativeListener) {
        SimsMeApplicationBusiness application = (SimsMeApplicationBusiness) mApplication;

        AccountController accountController = application.getAccountController();

        String title = application.getString(R.string.administration_request_dialog_title);
        String companyName;
        try {
            companyName = accountController.getManagementCompanyName();
        } catch (LocalizedException e) {
            companyName = BuildConfig.GINLO_APP_NAME;
        }

        String message = application.getString(R.string.administration_request_dialog_message, companyName);
        String positiveButton = application.getString(R.string.administration_request_dialog_button_positive);
        String negativeButton = application.getString(R.string.administration_request_dialog_button_negative);

        return DialogBuilderUtil.buildResponseDialogV7(activity, message, title, positiveButton, negativeButton,
                positiveListener, negativeListener);
    }
}
