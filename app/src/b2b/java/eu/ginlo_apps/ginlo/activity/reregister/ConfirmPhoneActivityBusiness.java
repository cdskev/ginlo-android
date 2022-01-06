// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.reregister;

import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.contracts.HasCompanyManagementCallback;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.router.Router;
import eu.ginlo_apps.ginlo.util.ConfigureProgressViewHelper;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import javax.inject.Inject;

public class ConfirmPhoneActivityBusiness extends ConfirmPhoneActivity {
    private boolean hasRequireStateBefore;

    @Inject
    public Router router;

    @Override
    protected void startNextActivity() {
        showIdleDialog();
        final AccountController accountControllerBusiness = getSimsMeApplication().getAccountController();
        try {
            hasRequireStateBefore = accountControllerBusiness.isManagementStateRequired(accountControllerBusiness.getManagementState());
        } catch (LocalizedException e) {
            LogUtil.w("ConfirmPhoneActivityBusiness", e.getMessage(), e);
        }

        accountControllerBusiness.loadCompanyManagement(new HasCompanyManagementCallback() {
            @Override
            public void onSuccess(String managementState) {
                if (accountControllerBusiness.isManagementStateRequired(managementState)) {
                    startNextScreenForRequiredManagementState(managementState);
                } else {
                    if (hasRequireStateBefore && StringUtil.isEqual(AccountController.MC_STATE_ACCOUNT_ACCEPTED, managementState)) {
                        ConfigureProgressViewHelper vh = new ConfigureProgressViewHelper(
                                ConfirmPhoneActivityBusiness.this, new ConfigureProgressViewHelper.ConfigureProgressListener() {
                            @Override
                            public void onFinish() {
                                ConfirmPhoneActivityBusiness.super.startNextActivity();
                            }

                            @Override
                            public void onError(String errorMsg, String detailErrorMsg) {
                                DialogBuilderUtil.OnCloseListener onCloseListener = new DialogBuilderUtil.OnCloseListener() {
                                    @Override
                                    public void onClose(int ref) {
                                        ConfirmPhoneActivityBusiness.super.startNextActivity();
                                    }
                                };
                                String text = errorMsg + "\n" + detailErrorMsg;
                                DialogBuilderUtil.buildErrorDialog(ConfirmPhoneActivityBusiness.this, text, -1, onCloseListener).show();
                            }
                        });
                        accountControllerBusiness.startConfigureCompanyAccount(vh);
                    } else {
                        ConfirmPhoneActivityBusiness.super.startNextActivity();
                    }
                }
            }

            @Override
            public void onFail(String message) {
                ConfirmPhoneActivityBusiness.super.startNextActivity();
            }
        });
    }

    private void startNextScreenForRequiredManagementState(String managementState) {
        String firstName = null, lastName = null;
        try {
            Contact ownContact = getSimsMeApplication().getContactController().getOwnContact();
            firstName = ownContact.getFirstName();
            lastName = ownContact.getLastName();
        } catch (LocalizedException ex) {
            LogUtil.e(getClass().getSimpleName(), ex.getMessage(), ex);
        }
        router.startNextScreenForRequiredManagementState(managementState, firstName, lastName);
    }
}
