// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.register;

import android.content.DialogInterface;
import android.content.Intent;
import eu.ginlo_apps.ginlo.BusinessTrialActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.context.SimsMeApplicationBusiness;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactController.OnSystemChatCreatedListener;
import eu.ginlo_apps.ginlo.controller.contracts.AcceptOrDeclineCompanyManagementCallback;
import eu.ginlo_apps.ginlo.controller.contracts.HasCompanyManagementCallback;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.router.Router;
import eu.ginlo_apps.ginlo.util.ConfigureProgressViewHelper;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.DialogHelperBusiness;
import eu.ginlo_apps.ginlo.util.StringUtil;
import javax.inject.Inject;

public class IdentConfirmActivityBusiness extends IdentConfirmActivity {
    @Inject
    public Router router;

    @Override
    public void onConfirmAccountSuccess() {
        SimsMeApplication application = getSimsMeApplication();

        final AccountController accountControllerBusiness = application.getAccountController();

        accountControllerBusiness.loadCompanyManagement(new HasCompanyManagementCallback() {
            @Override
            public void onSuccess(String managementState) {
                dismissIdleDialog();
                if (StringUtil.isEqual(managementState, AccountController.MC_STATE_ACCOUNT_NEW)) {
                    showAdministrationRequest();
                } else if (accountControllerBusiness.isManagementStateRequired(managementState)) {
                    startNextScreenForRequiredManagementState(managementState);
                } else {
                    if (getRegistrationType() == REGISTRATION_TYPE_MAIL) {
                        ConfigureProgressViewHelper vh = new ConfigureProgressViewHelper(
                                IdentConfirmActivityBusiness.this, new ConfigureProgressViewHelper.ConfigureProgressListener() {
                            @Override
                            public void onFinish() {
                                nextStepWithOutManagement();
                            }

                            @Override
                            public void onError(String errorMsg, String detailErrorMsg) {
                                DialogBuilderUtil.OnCloseListener onCloseListener = new DialogBuilderUtil.OnCloseListener() {
                                    @Override
                                    public void onClose(int ref) {
                                        nextStepWithOutManagement();
                                    }
                                };
                                String text = errorMsg + "\n" + detailErrorMsg;
                                DialogBuilderUtil.buildErrorDialog(IdentConfirmActivityBusiness.this, text, -1, onCloseListener).show();
                            }
                        });
                        accountControllerBusiness.startConfigureCompanyAccount(vh);
                    } else {
                        nextStepWithOutManagement();
                    }
                }
            }

            @Override
            public void onFail(String message) {
                dismissIdleDialog();
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

    private void nextStepWithOutManagement() {
        final AccountController accountControllerBusiness = getSimsMeApplication().getAccountController();
        try {
            if (accountControllerBusiness.testVoucherAvailable()) {
                final OnSystemChatCreatedListener onSystemChatCreatedListener = new OnSystemChatCreatedListener() {
                    @Override
                    public void onSystemChatCreatedSuccess() {
                        Intent intent = new Intent(IdentConfirmActivityBusiness.this, BusinessTrialActivity.class);
                        startActivity(intent);
                    }

                    @Override
                    public void onSystemChatCreatedError(String message) {
                        dismissIdleDialog();
                        nextClicked = false;
                        accountControllerBusiness.resetCreateAccountValidateConfirmCode();
                        DialogBuilderUtil.buildErrorDialog(IdentConfirmActivityBusiness.this, message).show();
                    }
                };

                getSimsMeApplication().getContactController().createSystemChatContact(onSystemChatCreatedListener);
            } else {
                IdentConfirmActivityBusiness.super.onConfirmAccountSuccess();
            }
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            dismissIdleDialog();
        }
    }

    private void showAdministrationRequest() {
        DialogInterface.OnClickListener positiveListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showIdleDialog();
                acceptOrDeclineCompanyManagement(true);
            }
        };

        DialogInterface.OnClickListener negativeListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showIdleDialog();
                acceptOrDeclineCompanyManagement(false);

            }
        };

        ((DialogHelperBusiness) DialogHelperBusiness.getInstance(getSimsMeApplication())).getManagementRequestDialog(this,
                positiveListener,
                negativeListener).show();
    }

    private void acceptOrDeclineCompanyManagement(final boolean accept) {
        final SimsMeApplicationBusiness application = (SimsMeApplicationBusiness) getApplicationContext();

        final AccountController accountController = application.getAccountController();

        showIdleDialog();
        accountController.acceptOrDeclineCompanyManagement(accept, new AcceptOrDeclineCompanyManagementCallback() {
            @Override
            public void onSuccess(final String mcState) {
                dismissIdleDialog();
                if (accountController.isManagementStateRequired(mcState)) {
                    startNextScreenForRequiredManagementState(mcState);
                } else {
                    ConfigureProgressViewHelper vh = new ConfigureProgressViewHelper(
                            IdentConfirmActivityBusiness.this, new ConfigureProgressViewHelper.ConfigureProgressListener() {
                        @Override
                        public void onFinish() {
                            IdentConfirmActivityBusiness.super.onConfirmAccountSuccess();
                        }

                        @Override
                        public void onError(String errorMsg, String detailErrorMsg) {
                            DialogBuilderUtil.OnCloseListener onCloseListener = new DialogBuilderUtil.OnCloseListener() {
                                @Override
                                public void onClose(int ref) {
                                    IdentConfirmActivityBusiness.super.onConfirmAccountSuccess();
                                }
                            };
                            String text = errorMsg + "\n" + detailErrorMsg;
                            DialogBuilderUtil.buildErrorDialog(IdentConfirmActivityBusiness.this, text, -1, onCloseListener).show();
                        }
                    });
                    accountController.startConfigureCompanyAccount(vh);
                }
            }

            @Override
            public void onFail(final String message) {
                dismissIdleDialog();
            }
        });
    }
}
