// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import static eu.ginlo_apps.ginlo.RegisterEmailActivity.EXTRA_RUN_AFTER_REGISTRATION;
import eu.ginlo_apps.ginlo.context.SimsMeApplicationBusiness;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactControllerBusiness;
import eu.ginlo_apps.ginlo.controller.contracts.HasCompanyManagementCallback;
import eu.ginlo_apps.ginlo.controller.contracts.PhoneOrEmailActionListener;
import eu.ginlo_apps.ginlo.controller.contracts.SetAddressInfoListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.router.Router;
import eu.ginlo_apps.ginlo.util.ConfigureProgressViewHelper;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

public class EnterEmailActivationCodeActivity extends BaseActivity {
    private EditText mCodeEditText;
    private AccountController mAccountControllerBusiness;
    @Inject
    public Router router;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        try {
            setTitle(getResources().getString(R.string.enter_email_activation_code_title));

            final TextView topHint = findViewById(R.id.enter_email_confimation_code_hint1);
            mAccountControllerBusiness = ((SimsMeApplicationBusiness) getApplication()).getAccountController();

            if (mAccountControllerBusiness.needEmailRegistrationForManaging()) {
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                }
            }

            final String topHintText = String.format(getResources().getString(R.string.enter_email_activation_code_hint1), mAccountControllerBusiness.getPendingEmailAddress());
            topHint.setText(topHintText);

            mCodeEditText = findViewById(R.id.enter_activation_code_edittext);

            mCodeEditText.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                        handleActivateClick(null);
                        return true;
                    }
                    return false;
                }
            });

            final Button registerNewButton = findViewById(R.id.profile_button_email_authenticate);
            if (mAccountControllerBusiness.isDeviceManaged()) {
                registerNewButton.setVisibility(View.GONE);
            }
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        }
    }

    public void handleActivateClick(View v) {
        final String code = mCodeEditText.getText().toString();

        final String expression = "^[a-zA-Z0-9\\-_]{4}$";

        final Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
        final Matcher matcher = pattern.matcher(code);

        if (StringUtil.isNullOrEmpty(code) || !matcher.matches()) {
            DialogBuilderUtil.buildErrorDialog(this, getResources().getString(R.string.dialog_email_verfication_code_too_short)).show();
        } else {
            PhoneOrEmailActionListener confirmationEmailListener = new PhoneOrEmailActionListener() {
                @Override
                public void onSuccess(String result) {
                    try {
                        ContactControllerBusiness ccb = (ContactControllerBusiness) getSimsMeApplication().getContactController();

                        if (!StringUtil.isNullOrEmpty(ccb.getOwnContact().getDomain())) {
                            mAccountControllerBusiness.resetDomainKey();
                            ccb.deleteAllDomainContacts();
                            setAddressInformation();
                        }
                        mAccountControllerBusiness.unsetPendingEmailStatus(false);
                        //neuen Token fuer die mail kreieren
                        getSimsMeApplication().getPreferencesController().checkRecoveryCodeToBeSet(true);
                    } catch (final LocalizedException e) {
                        LogUtil.w(EnterEmailActivationCodeActivity.this.getClass().getName(), e.getMessage(), e);
                    }
                    showSuccessDialog(result);

                }

                @Override
                public void onFail(String errorMsg, boolean emailIsInUse) {
                    dismissIdleDialog();
                    DialogBuilderUtil.buildErrorDialog(EnterEmailActivationCodeActivity.this, errorMsg).show();
                }
            };

            try {
                mAccountControllerBusiness.confirmConfirmEmail(code, confirmationEmailListener);
            } catch (final LocalizedException e) {
                LogUtil.w(EnterEmailActivationCodeActivity.this.getClass().getName(), e.getMessage(), e);
            }
            showIdleDialog(-1);
        }
    }

    private void setAddressInformation() {
        SetAddressInfoListener listener = new SetAddressInfoListener() {

            @Override
            public void onSuccess(final String result) {
            }

            @Override
            public void onFail(String errorMsg) {
                DialogBuilderUtil.buildErrorDialog(EnterEmailActivationCodeActivity.this, errorMsg).show();
            }
        };

        mAccountControllerBusiness.setAddressInformation(listener);
    }

    private void showSuccessDialog(String msg) {
        dismissIdleDialog();
        DialogBuilderUtil.OnCloseListener onCloseListener = new DialogBuilderUtil.OnCloseListener() {
            @Override
            public void onClose(int ref) {
                loadCompanyAfterSuccess();
            }
        };

        DialogBuilderUtil.buildErrorDialog(EnterEmailActivationCodeActivity.this, msg, 0, onCloseListener).show();
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

    private void loadCompanyAfterSuccess() {
        final AccountController accountControllerBusiness = getSimsMeApplication().getAccountController();
        accountControllerBusiness.loadCompanyManagement(new HasCompanyManagementCallback() {
            @Override
            public void onSuccess(String managementState) {
                if (accountControllerBusiness.isManagementStateRequired(managementState)) {
                    startNextScreenForRequiredManagementState(managementState);
                } else {
                    ConfigureProgressViewHelper vh = new ConfigureProgressViewHelper(
                            EnterEmailActivationCodeActivity.this, new ConfigureProgressViewHelper.ConfigureProgressListener() {
                        @Override
                        public void onFinish() {
                            startNextActivityAfterRegistration();
                        }

                        @Override
                        public void onError(String errorMsg, String detailErrorMsg) {
                            DialogBuilderUtil.OnCloseListener onCloseListener = new DialogBuilderUtil.OnCloseListener() {
                                @Override
                                public void onClose(int ref) {
                                    startNextActivityAfterRegistration();
                                }
                            };
                            String text = errorMsg + "\n" + detailErrorMsg;
                            DialogBuilderUtil.buildErrorDialog(EnterEmailActivationCodeActivity.this, text, -1, onCloseListener).show();
                        }
                    });
                    accountControllerBusiness.startConfigureCompanyAccount(vh);
                }
            }

            @Override
            public void onFail(String message) {
                startNextActivityAfterRegistration();
            }
        });
    }

    private void startNextActivityAfterRegistration() {
        try {
            final Intent callerIntent = getIntent();
            if (callerIntent.hasExtra(EXTRA_RUN_AFTER_REGISTRATION) && callerIntent.getBooleanExtra(EXTRA_RUN_AFTER_REGISTRATION, false)) {
                final String className = BuildConfig.ACTIVITY_AFTER_REGISTRATION;

                final Class<?> classForNextIntent = SystemUtil.getClassForBuildConfigClassname(className);

                Intent intent = new Intent(this, RuntimeConfig.getClassUtil().getLoginActivityClass());

                intent.putExtra(LoginActivity.EXTRA_NEXT_ACTIVITY, classForNextIntent.getName());
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                startActivity(intent);
                finish();
            } else {
                EnterEmailActivationCodeActivity.this.setResult(RESULT_OK);
                EnterEmailActivationCodeActivity.this.finish();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void onBackPressed() {
        try {
            if (mAccountControllerBusiness.needEmailRegistrationForManaging()) {
                return;
            }
        } catch (final LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        }
        KeyboardUtil.toggleSoftInputKeyboard(this, mCodeEditText, false);
        super.onBackPressed();
    }

    public void onRegisterNewClicked(View v) {
        Intent intent = new Intent(EnterEmailActivationCodeActivity.this, RegisterEmailActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_enter_email_activation_code;
    }

    @Override
    protected void onResumeActivity() {

    }
}
