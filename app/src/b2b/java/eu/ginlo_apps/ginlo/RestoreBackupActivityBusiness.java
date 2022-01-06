// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.DialogInterface;
import eu.ginlo_apps.ginlo.context.SimsMeApplicationBusiness;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.ContactControllerBusiness;
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

public class RestoreBackupActivityBusiness extends RestoreBackupActivity {
    @Inject
    public Router router;
    
    @Override
    public void onConfirmAccountSuccess() {
        SimsMeApplicationBusiness application = (SimsMeApplicationBusiness) getApplicationContext();

        final AccountController accountController = application.getAccountController();

        accountController.loadCompanyManagement(new HasCompanyManagementCallback() {
            @Override
            public void onSuccess(final String managementState) {

                if (StringUtil.isEqual(managementState, AccountController.MC_STATE_ACCOUNT_NEW) || accountController.isManagementStateRequired(managementState)) {
                    final ContactController.OnSystemChatCreatedListener onSystemChatCreatedListener = new ContactController.OnSystemChatCreatedListener() {
                        @Override
                        public void onSystemChatCreatedSuccess() {
                            dismissIdleDialog();
                            if (accountController.isManagementStateRequired(managementState)) {
                                startNextScreenForRequiredManagementState(managementState);
                            } else {
                                showAdministrationRequest();
                            }
                        }

                        @Override
                        public void onSystemChatCreatedError(String message) {
                            dismissIdleDialog();
                            DialogBuilderUtil.buildErrorDialog(RestoreBackupActivityBusiness.this, message).show();
                        }
                    };
                    getSimsMeApplication().getContactController().createSystemChatContact(onSystemChatCreatedListener);
                } else {
                    RestoreBackupActivityBusiness.super.onConfirmAccountSuccess();
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

    private void showAdministrationRequest() {
        DialogInterface.OnClickListener positiveListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //
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

        ((DialogHelperBusiness) DialogHelperBusiness.getInstance(getSimsMeApplication())).getManagementRequestDialog(this, positiveListener, negativeListener).show();
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
                            RestoreBackupActivityBusiness.this, new ConfigureProgressViewHelper.ConfigureProgressListener() {
                        @Override
                        public void onFinish() {
                            RestoreBackupActivityBusiness.super.onConfirmAccountSuccess();
                        }

                        @Override
                        public void onError(String errorMsg, String detailErrorMsg) {
                            DialogBuilderUtil.OnCloseListener onCloseListener = new DialogBuilderUtil.OnCloseListener() {
                                @Override
                                public void onClose(int ref) {
                                    RestoreBackupActivityBusiness.super.onConfirmAccountSuccess();
                                }
                            };
                            String text = errorMsg + "\n" + detailErrorMsg;
                            DialogBuilderUtil.buildErrorDialog(RestoreBackupActivityBusiness.this, text, -1, onCloseListener).show();
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

    @Override
    protected void backupFinished() {
        final AccountController accountControllerBusiness = getSimsMeApplication().getAccountController();

        // TODO
        final String domain = "";
        if (StringUtil.isNullOrEmpty(domain)) {
            try {
                final String managementState = accountControllerBusiness.getManagementState();
                if (AccountController.MC_STATE_ACCOUNT_NEW.equals(managementState)) {
                    showAdministrationRequest();
                } else {
                    super.backupFinished();
                }
            } catch (final LocalizedException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            }

        } else {
            getEmailDataFromServer(accountControllerBusiness);
        }
    }

    private void getEmailDataFromServer(final AccountController accountControllerBusiness) {
        //TODO: Kann
        final ContactControllerBusiness.GetAddressInformationsListener getAddressInformationsListener = new ContactControllerBusiness.GetAddressInformationsListener() {
            @Override
            public void onSuccess() {
                callSuperBackupFinishedOnMainThread();
            }

            @Override
            public void onFail() {
                backupFailed(getResources().getString(R.string.backup_restore_process_failed_msg_default));
            }
        };

        accountControllerBusiness.getAddressInformationForOwnAccountPre22(getAddressInformationsListener);
        //fixme wird 2x aufgerufen?!
    }

    private void callSuperBackupFinishedOnMainThread() {
        final android.os.Handler handler = new android.os.Handler(RestoreBackupActivityBusiness.this.getMainLooper());
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                RestoreBackupActivityBusiness.super.backupFinished();
            }
        };
        handler.post(runnable);
    }

    private void backupFailed(final String errorMessage) {
        final android.os.Handler handler = new android.os.Handler(RestoreBackupActivityBusiness.this.getMainLooper());
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                dismissIdleDialog();
                DialogBuilderUtil.buildErrorDialog(RestoreBackupActivityBusiness.this, errorMessage).show();
            }
        };
        handler.post(runnable);
    }
}
