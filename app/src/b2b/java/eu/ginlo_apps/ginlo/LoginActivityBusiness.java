// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Calendar;

import eu.ginlo_apps.ginlo.activity.register.InitProfileActivityBusiness;
import eu.ginlo_apps.ginlo.activity.register.PurchaseLicenseActivity;
import eu.ginlo_apps.ginlo.billing.GinloBillingImpl;
import eu.ginlo_apps.ginlo.billing.GinloBillingResult;
import eu.ginlo_apps.ginlo.billing.GinloPurchaseImpl;
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

        if (nextActivityClass == null) {
            if (getSimsMeApplication().getAccountController().getAccount().getHasLicence()) {
                LogUtil.i(TAG, "checkDeviceManagementAndLicense: Account has license.");
            }
            else {
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
        }

        return nextActivityClass;
    }

    private void checkAndExtendPurchase() {
        if(ginloBillingImpl == null ) {
            LogUtil.i(TAG, "checkAndExtendPurchase: No ginloBillingImpl instance!");
        } else {
            // Initialize billing and connect.
            ginloBillingImpl.initialize(new GinloBillingImpl.OnBillingInitializeFinishedListener() {
                @Override
                public void onBillingInitializeFinished(@NotNull GinloBillingResult result) {
                    if (result.isSuccess()) {
                        LogUtil.i(TAG, "checkAndExtendPurchase: Billing initialization successful.");
                        // Retrieve current in-app purchases.
                        ginloBillingImpl.queryPurchases(GinloBillingImpl.INAPP, new GinloBillingImpl.OnQueryPurchasesFinishedListener() {
                            @Override
                            public void onQueryPurchasesFinished(@NotNull GinloBillingResult result, ArrayList<GinloPurchaseImpl> purchases) {

                                if (result.isSuccess()) {
                                    try {
                                        if (purchases != null && purchases.size() > 0) {
                                            LogUtil.i(TAG, "checkAndExtendPurchase: Query purchases successful: " + purchases);
                                            Calendar cal = Calendar.getInstance();
                                            cal.add(Calendar.DATE, 1);
                                            getSimsMeApplication().getAccountController().getAccount().setLicenceDate(cal.getTimeInMillis());
                                            ((ContactControllerBusiness) getSimsMeApplication().getContactController()).resetLicenseDaysLeft();
                                            LogUtil.i(TAG, "checkAndExtendPurchase: Silent setting of license date done.");

                                            ginloBillingImpl.consumePurchases(purchases, new GinloBillingImpl.OnConsumePurchaseFinishedListener() {
                                                @Override
                                                public void onConsumePurchaseFinished(@NotNull GinloBillingResult billingResult, String purchaseToken) {
                                                    if(billingResult.isSuccess()) {
                                                        LogUtil.i(TAG, "checkAndExtendPurchase: onConsumePurchaseFinished successful for token = " + purchaseToken);
                                                    } else {
                                                        LogUtil.i(TAG, "checkAndExtendPurchase: onConsumePurchaseFinished returned " +
                                                                billingResult.getResponseCode() + " (" + billingResult.getResponseMessage() + ")");
                                                    }
                                                }
                                            });

                                        } else {
                                            LogUtil.i(TAG, "checkAndExtendPurchase: Query purchases successful: No pending purchases.");
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

                                                LogUtil.i(TAG, "checkAndExtendPurchase: Reset old purchase tokens on backend: " + oldPurchaseTokens);
                                                BackendService.withAsyncConnection(getSimsMeApplication())
                                                        .resetPurchaseToken(oldPurchaseTokens, listener);
                                            }
                                        }
                                    } catch (LocalizedException e) {
                                        LogUtil.e(TAG, "Query purchase inventory failed with: " + e.getMessage());
                                    }
                                } else {
                                    LogUtil.w(TAG, "checkAndExtendPurchase: onQueryPurchasesFinished returned with: " + result.getResponseMessage());
                                }
                            }
                        });
                    } else {
                            LogUtil.w(TAG, "checkAndExtendPurchase: onBillingInitializeFinished returned with: " + result.getResponseMessage());
                    }
                }
            });
        }
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
