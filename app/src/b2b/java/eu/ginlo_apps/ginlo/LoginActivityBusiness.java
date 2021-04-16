// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Calendar;

import eu.ginlo_apps.ginlo.activity.register.InitProfileActivityBusiness;
import eu.ginlo_apps.ginlo.activity.register.PurchaseLicenseActivity;
import eu.ginlo_apps.ginlo.billing.IabException;
import eu.ginlo_apps.ginlo.billing.IabHelper;
import eu.ginlo_apps.ginlo.billing.IabResult;
import eu.ginlo_apps.ginlo.billing.Inventory;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactControllerBusiness;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.PreferencesControllerBusiness;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;

public class LoginActivityBusiness extends LoginActivity {

    private final static String TAG = LoginActivityBusiness.class.getSimpleName();

    @Override
    protected Class<?> getNextActivityClassForConfirmedAccountState(String password, Bundle extras)
            throws ClassNotFoundException, LocalizedException {
        Class<?> nextActivityClass = checkDeviceManagementAndLicense(extras);

        if (nextActivityClass == null) {
            nextActivityClass = super.getNextActivityClassForConfirmedAccountState(password, extras);
        }

        return nextActivityClass;
    }

    @Override
    protected Class<?> getNextActivityClassForFullAccountState(String password, Bundle extras)
            throws ClassNotFoundException, LocalizedException {
        Class<?> nextActivityClass = checkDeviceManagementAndLicense(extras);

        if (nextActivityClass == null) {
            if (getSimsMeApplication().getPreferencesController().needToChangePassword(password, false)) {
                nextActivityClass = SetPasswordActivity.class;
                extras.putString(SetPasswordActivity.EXTRA_MODE, SetPasswordActivity.MODE_FORCE_CHANGE_PW);
                extras.putBoolean(SetPasswordActivity.FORCE_PW_VALITATION_DIALOG, true);
            } else {
                nextActivityClass = super.getNextActivityClassForFullAccountState(password, extras);
            }
        }

        return nextActivityClass;
    }

    @Override
    protected void onResumeActivity() {
        final AccountController accountControllerBusiness = getSimsMeApplication().getAccountController();

        final PreferencesController preferencesController = getSimsMeApplication().getPreferencesController();
        if (preferencesController.getSharedPreferences().getBoolean(AccountController.MC_RECOVERY_CODE_REQUESTED, false)
                && !accountControllerBusiness.getHasLaunchedRecoveryActivity()) {
            accountControllerBusiness.setHasLaunchedRecoveryActivity(true);

            final Intent intent;

            if (preferencesController.getRecoveryByAdmin()) {
                intent = new Intent(LoginActivityBusiness.this, RecoverPasswordActivityBusiness.class);
            } else {
                intent = new Intent(LoginActivityBusiness.this, RecoverPasswordActivity.class);
            }
            startActivity(intent);
        } else {
            super.onResumeActivity();
        }
    }

    private Class<?> checkDeviceManagementAndLicense(Bundle extras)
            throws LocalizedException {
        Class<?> nextActivityClass = null;

        AccountController accountController = getSimsMeApplication().getAccountController();

        boolean isDeviceManaged = accountController.isDeviceManaged();

        if (isDeviceManaged) {
            if (accountController.needEmailRegistrationForManaging()) {
                boolean isWaitingForConformation = accountController.getWaitingForEmailConfirmation();
                if (isWaitingForConformation) {
                    nextActivityClass = EnterEmailActivationCodeActivity.class;
                } else {
                    nextActivityClass = RegisterEmailActivity.class;
                    extras.putBoolean(RegisterEmailActivity.EXTRA_FIRST_RUN, true);
                    extras.putBoolean(RegisterEmailActivity.EXTRA_RUN_AFTER_REGISTRATION, true);
                    Contact ownContact = getSimsMeApplication().getContactController().getOwnContact();
                    extras.putString(RegisterEmailActivity.EXTRA_PREFILLED_FIRST_NAME, ownContact.getFirstName());
                    extras.putString(RegisterEmailActivity.EXTRA_PREFILLED_LAST_NAME, ownContact.getLastName());

                }
            }

        }

        if (nextActivityClass == null && accountController.haveToShowManagementRequest()) {
            nextActivityClass = RuntimeConfig.getClassUtil().getChatOverviewActivityClass();
        }

        // Prüfen, ob es noch eine aktive AndroidSubscription gibt
        checkAndExtendPurchase();

        if (nextActivityClass == null && !getSimsMeApplication().getAccountController().getAccount().getHasLicence()) {
            if (accountController.isValidTrial()) {
                if (Account.ACCOUNT_STATE_FULL != accountController.getAccount().getState()) {
                    nextActivityClass = InitProfileActivityBusiness.class;
                }

            } else {
                if (accountController.testVoucherAvailable() && !accountController.isDeviceManaged()) {
                    nextActivityClass = BusinessTrialActivity.class;
                } else {
                    nextActivityClass = PurchaseLicenseActivity.class;
                }
            }
        }

        return nextActivityClass;
    }

    private void checkAndExtendPurchase() {
        final IabHelper helper = new IabHelper(getSimsMeApplication(), RuntimeConfig.getApplicationPublicKey());

        helper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            @Override
            public void onIabSetupFinished(IabResult result) {
                if (result.isSuccess()) {
                    try {
                        // KS: TODO: Use queryInventoryAsync!
                        Inventory inv = helper.queryInventory(true, null);
                        LogUtil.i(TAG, "Query purchase inventory returns: " + inv.getAllPurchases());

                        // KS: Test
                        LogUtil.i(TAG, "Fire and throw away consuming ... ");
                        helper.consumeAsync(inv.getAllPurchases(), null);

                        // AutoRenewable Purchases unter Android werden für die App nicht verifizierbar verlängert :-(
                        if (inv.getAllPurchases().size() > 0) {
                            Calendar cal = Calendar.getInstance();
                            cal.add(Calendar.DATE, 1);
                            getSimsMeApplication().getAccountController().getAccount().setLicenceDate(cal.getTimeInMillis());
                            ((ContactControllerBusiness) getSimsMeApplication().getContactController()).resetLicenseDaysLeft();
                            LogUtil.i(TAG, "Silent setting of license date done.");
                        } else {
                            String oldPurchaseTokens = getSimsMeApplication().getPreferencesController().getSavedPurchases();
                            if (!StringUtil.isNullOrEmpty(oldPurchaseTokens)) {
                                final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
                                    @Override
                                    public void onBackendResponse(BackendResponse response) {
                                        if (!response.isError) {
                                            getSimsMeApplication().getPreferencesController().resetSavedPurchases();
                                        }
                                    }
                                };

                                LogUtil.i(TAG, "Reset purchaseToken on backend.");
                                BackendService.withAsyncConnection(getSimsMeApplication())
                                        .resetPurchaseToken(oldPurchaseTokens, listener);
                            }
                        }
                    } catch (IabException | LocalizedException e) {
                        LogUtil.e(TAG, "Query purchase inventory failed with: " + e.getMessage(), e);
                    }
                } else {
                    LogUtil.w(TAG, "Query purchase inventory failed with: " + result);
                }
            }
        });
    }

    @Override
    public void handleForgotPwClick(View view) {
        final PreferencesControllerBusiness preferencesController = (PreferencesControllerBusiness) getSimsMeApplication().getPreferencesController();

        boolean accountIsManaged = preferencesController.getSharedPreferences()
                .getBoolean(AccountController.MC_ACCOUNT_IS_MANAGED, false);

        if (accountIsManaged && preferencesController.getRecoveryByAdmin()) {
            KeyboardUtil.toggleSoftInputKeyboard(LoginActivityBusiness.this, getCurrentFocus(), false);

            final View forgotPwView = findViewById(R.id.login_forgot_pw_view);
            final View mainView = findViewById(R.id.login_main_view);
            if (forgotPwView != null && mainView != null) {
                final TextView titleView = findViewById(R.id.dialog_forgot_pw_title);
                final TextView descView = findViewById(R.id.dialog_forgot_pw_description);
                final TextView descView2 = findViewById(R.id.dialog_forgot_pw_description2);
                final ImageView imageViewTop = findViewById(R.id.dialog_forgot_pw_top_image);

                final View warningView = findViewById(R.id.dialog_forgot_pw_warning);
                warningView.setVisibility(View.GONE);

                final Button resetBtn = findViewById(R.id.forgot_pw_reset_button);

                titleView.setText(R.string.forgot_pwd_title);
                descView.setText(R.string.forgot_pwd_desc);
                descView2.setText(R.string.forgot_pwd_desc2);
                descView2.setVisibility(View.VISIBLE);
                resetBtn.setText(R.string.forgot_pwd_btn_request_code);

                mLoginButton.setVisibility(View.GONE);

                imageViewTop.setVisibility(View.VISIBLE);

                mainView.setVisibility(View.GONE);
                forgotPwView.setVisibility(View.VISIBLE);
            }
        } else {
            super.handleForgotPwClick(view);
        }
    }

    @Override
    public void handleConfirmResetClick(View view) {
        boolean accountIsManaged = getSimsMeApplication().getPreferencesController().getSharedPreferences()
                .getBoolean(AccountController.MC_ACCOUNT_IS_MANAGED, false);

        if (accountIsManaged) {
            final GenericActionListener requestCompanyRecoveryKeyListener = new GenericActionListener<String>() {

                @Override
                public void onSuccess(final String notUsed) {
                    dismissIdleDialog();

                    getSimsMeApplication().getPreferencesController().getSharedPreferences().edit().putBoolean(AccountController.MC_RECOVERY_CODE_REQUESTED, true).apply();
                    // Meldung anzeigen, dass der Admin den User kontaktieren wird und alles gut wird
                    DialogBuilderUtil.buildErrorDialog(LoginActivityBusiness.this, getResources().getString(R.string.recover_password_admin_will_contact_you), -1, new DialogBuilderUtil.OnCloseListener() {
                        private boolean mHasClosed = false;

                        @Override
                        public void onClose(int ref) {
                            if (!mHasClosed) {
                                final Intent intent = new Intent(LoginActivityBusiness.this, RecoverPasswordActivityBusiness.class);
                                startActivity(intent);
                                mHasClosed = true;
                            }
                        }
                    }).show();
                }

                @Override
                public void onFail(String message, final String errorIdent) {
                    dismissIdleDialog();
                    DialogBuilderUtil.buildErrorDialog(LoginActivityBusiness.this, message).show();
                }
            };
            showIdleDialog();
            (getSimsMeApplication().getAccountController()).requestCompanyRecoveryKey(requestCompanyRecoveryKeyListener);
        } else {
            super.handleConfirmResetClick(view);
        }
    }

    protected void logInSuccessful() {
        getSimsMeApplication().getPreferencesController().getSharedPreferences().edit().putBoolean(AccountController.MC_RECOVERY_CODE_REQUESTED, false).apply();
    }
}
