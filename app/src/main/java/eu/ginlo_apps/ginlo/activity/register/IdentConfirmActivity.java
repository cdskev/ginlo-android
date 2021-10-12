// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.activity.register;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.LoginActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity;
import eu.ginlo_apps.ginlo.activity.reregister.ChangePhoneActivity;
import eu.ginlo_apps.ginlo.activity.reregister.ConfirmPhoneActivity;
import eu.ginlo_apps.ginlo.controller.ContactController.OnSystemChatCreatedListener;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.contracts.OnConfirmAccountListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnValidateConfirmCodeListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.NumberConstants;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.GinloNowUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.ViewUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;

import static eu.ginlo_apps.ginlo.model.constant.NumberConstants.INT_1000;
import static eu.ginlo_apps.ginlo.model.constant.NumberConstants.INT_60;

public class IdentConfirmActivity
        extends NewBaseActivity implements OnValidateConfirmCodeListener, OnConfirmAccountListener {
    public static final String TAG = IdentConfirmActivity.class.getSimpleName();
    public static final String REGISTRATION_TYPE = "IdentConfirmActivity.RegistrationType";
    public static final int REGISTRATION_TYPE_PHONE = 1;
    public static final int REGISTRATION_TYPE_MAIL = 2;
    public static final String FAKE_PHONENUMBER = "IdentConfirmActivity.FakePhonenumber";

    private AccountController mAccountController;
    private GinloNowUtil mGinloNowUtil;
    protected String mConfirmCode;
    protected EditText mConfirmCodeEditText1;
    protected EditText mConfirmCodeEditText2;
    protected boolean mBackAllowed;
    protected TextView mIdentConfirmLabel;
    boolean nextClicked;
    private EditText mConfirmCodeMailEditText;
    private TextView countdownTextView;
    private int mRegistrationType = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {

        mAccountController = getSimsMeApplication().getAccountController();
        if(mAccountController == null) {
            return;
        }

        nextClicked = false;
        mGinloNowUtil = new GinloNowUtil();

        if (getIntent().hasExtra(REGISTRATION_TYPE)) {
            mRegistrationType = getIntent().getIntExtra(REGISTRATION_TYPE, REGISTRATION_TYPE_PHONE);
            getSimsMeApplication().getPreferencesController().getSharedPreferences().edit().putInt(REGISTRATION_TYPE, mRegistrationType).apply();
        } else {
            mRegistrationType = getSimsMeApplication().getPreferencesController().getSharedPreferences().getInt(REGISTRATION_TYPE, REGISTRATION_TYPE_PHONE);
        }

        // A little bit unhandy, but IdentConfirmActivity is being re-used by ConfirmPhoneActivity <sigh!>,
        // so we need to do the instance check.
        if (!(this instanceof ConfirmPhoneActivity) && (!BuildConfig.NEED_PHONENUMBER_VALIDATION
                || mGinloNowUtil.haveGinloNowInvitation())) {

            continueExpressRegistration();
            return;
        }

        mIdentConfirmLabel = findViewById(R.id.ident_confirm_text_view_label);

        EditText.OnEditorActionListener listener = new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v,
                                          int actionId,
                                          KeyEvent event) {
                if ((actionId == EditorInfo.IME_ACTION_DONE)
                        || (event != null &&
                        event.getAction() == KeyEvent.ACTION_DOWN &&
                        event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    final Button nextButton = findViewById(R.id.next_button);

                    if (nextButton != null) {
                        nextButton.performClick();
                    }
                }
                return true;
            }
        };

        mConfirmCodeEditText1 = findViewById(R.id.ident_confirm_edit_text_confirm_code);
        mConfirmCodeEditText2 = findViewById(R.id.ident_confirm_edit_text_confirm_code_2);
        mConfirmCodeMailEditText = findViewById(R.id.ident_confirm_mail_code_edit_text);

        if (mRegistrationType == REGISTRATION_TYPE_PHONE) {
            if (mIdentConfirmLabel != null) {
                final String phoneNumber = mAccountController.getCreateAccountPhoneNumber();
                mIdentConfirmLabel.setText(getResources().getString(R.string.registration_textView_killTextView, phoneNumber));
            }

            mConfirmCodeEditText1.setEnabled(true);
            mConfirmCodeEditText2.setEnabled(true);

            ViewUtil.createTextWatcher(this, null, mConfirmCodeEditText1, mConfirmCodeEditText2, 3);
            ViewUtil.createTextWatcher(this, mConfirmCodeEditText1, mConfirmCodeEditText2, null, 3);

            ViewUtil.createOnKeyListener(mConfirmCodeEditText1, mConfirmCodeEditText2);

            mConfirmCodeEditText2.setOnEditorActionListener(listener);

            if (mConfirmCodeMailEditText != null) {
                mConfirmCodeMailEditText.setVisibility(View.GONE);
                mConfirmCodeMailEditText.setEnabled(false);
            }
        } else {
            if (mIdentConfirmLabel != null) {
                try {
                    String email = getSimsMeApplication().getContactController().getOwnContact().getEmail();
                    mIdentConfirmLabel.setText(getResources().getString(R.string.registration_confirm_mail_text, email));
                } catch (LocalizedException e) {
                    LogUtil.w(TAG, e.getIdentifier(), e);
                }
            }

            TextView identConfirmTitle = findViewById(R.id.ident_confirm_text_view_title);
            if (identConfirmTitle != null) {
                identConfirmTitle.setText(R.string.registration_confirm_mail_headline);
            }

            if (mConfirmCodeMailEditText != null) {
                mConfirmCodeMailEditText.setVisibility(View.VISIBLE);
                mConfirmCodeMailEditText.setOnEditorActionListener(listener);
                mConfirmCodeMailEditText.setEnabled(true);
            }

            View editTextLayout = findViewById(R.id.ident_confirm_edit_text_confirm_code_layout);
            if (editTextLayout != null) {
                editTextLayout.setVisibility(View.GONE);
            }
        }

        countdownTextView = findViewById(R.id.ident_confirm_text_countdown_label);

        final int waitTimeMillis = BuildConfig.DEBUG ? 60000 : BuildConfig.REGISTER_NAVIGATE_BACK_WAIT_TIME_MILLIS;
        new CountDownTimer(waitTimeMillis, INT_1000) {
            public void onTick(final long millisUntilFinished) {
                long seconds = millisUntilFinished / INT_1000;
                final long minutes = seconds / INT_60;

                seconds = seconds - (minutes * INT_60);

                String secondsZero = "";

                if (seconds < NumberConstants.INT_10) {
                    secondsZero = "0";
                }
                countdownTextView.setText(getString(R.string.registration_textView_countdown) + " "
                        + minutes + ":" + secondsZero + seconds);
            }

            public void onFinish() {
                countdownTextView.setVisibility(View.GONE);
                mBackAllowed = true;
            }
        }.start();
    }

    /**
     * This is for invitations and other ways of registrations where phone number validation
     * should be skipped.
     */
    private void continueExpressRegistration() {
        LogUtil.d(TAG, "continueExpressRegistration: Skip all validation.");
        View myView = findViewById(R.id.activity_ident_confirm);
        myView.setVisibility(View.GONE);
        mRegistrationType = IdentConfirmActivity.REGISTRATION_TYPE_PHONE;
        nextClicked = true;
        mConfirmCode = "NOT_USED";

        if(mAccountController.getAccountState() != Account.ACCOUNT_STATE_VALID_CONFIRM_CODE
                && !BuildConfig.NEED_PHONENUMBER_VALIDATION
                && !mGinloNowUtil.haveGinloNowInvitation()) {

            // We must have this for a possible backup restore.
            // LoginActivity then expects a pre-confirmed account.
            mAccountController.setAccountStateToConfirmationCodeValid();
            LogUtil.d(TAG, "continueExpressRegistration: Starting  LoginActivity ...");
            final Intent intent = new Intent(IdentConfirmActivity.this, RuntimeConfig.getClassUtil().getLoginActivityClass());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return;
        }
        mAccountController.setAccountStateToConfirmed();
        mAccountController.configureNewAccount();
        onConfirmAccountSuccess();
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_ident_confirm;
    }

    @Override
    protected void onResumeActivity() {
        setTitle(R.string.registration_title_createAccount);
        if (mRegistrationType == REGISTRATION_TYPE_PHONE) {
            if (mConfirmCodeEditText1 != null) {
                KeyboardUtil.toggleSoftInputKeyboard(this, mConfirmCodeEditText1, true);
            }
        } else {
            if (mConfirmCodeMailEditText != null) {
                KeyboardUtil.toggleSoftInputKeyboard(this, mConfirmCodeMailEditText, true);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mAccountController.getAccount() == null) {
            // Reset gerade im Gang ....
            return;
        }

        if (Account.ACCOUNT_STATE_NOT_CONFIRMED != mAccountController.getAccount().getState()) {
            // falls wir durch abgeleitete Klassen - z.B. Telefonnummer aendern - hier rein rutschen, nicht den Account loeschen
            super.onBackPressed();
            return;
        }

        if (mBackAllowed) {

            if (getSimsMeApplication().getAppLifecycleController().getActivityStackSize() == 1) {
                DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAccountController.resetCreateAccountRegisterPhone();
                        Intent intent = new Intent(IdentConfirmActivity.this, IntroActivity.class);
                        startActivity(intent);
                        finish();
                    }
                };

                DialogBuilderUtil.buildResponseDialog(IdentConfirmActivity.this,
                        getString(R.string.notification_reregister_after_after_app_killed_text),
                        getString(R.string.notification_reregister_after_after_app_killed_title),
                        getString(R.string.notification_reregister_after_after_app_killed_confirm),
                        getString(R.string.std_cancel),
                        clickListener,
                        null
                ).show();
            } else {
                mAccountController.resetCreateAccountRegisterPhone();
                super.onBackPressed();
            }
        } else {
            countdownTextView.setVisibility(View.VISIBLE);
        }
    }

    public void handleNextClick(View view) {
        if (mAccountController.getAccount() == null) {
            // Reset gerade im Gang ....
            return;
        }

        if (mRegistrationType == REGISTRATION_TYPE_PHONE) {
            if (mConfirmCodeEditText1 == null || mConfirmCodeEditText2 == null) {
                return;
            }
            mConfirmCode = mConfirmCodeEditText1.getText().toString() + mConfirmCodeEditText2.getText().toString();
        } else {
            if (mConfirmCodeMailEditText == null) {
                return;
            }

            mConfirmCode = mConfirmCodeMailEditText.getText().toString();
        }

        if (StringUtil.isNullOrEmpty(mConfirmCode)) {
            AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(this,
                    getString(R.string.registration_label_confirmationLabel_empty));

            alert.show();
            return;
        }

        showIdleDialog(R.string.progress_dialog_ident_confirm);

        if (!nextClicked) {
            nextClicked = true;

            switch(mAccountController.getAccountState()) {
                case Account.ACCOUNT_STATE_CONFIRMED:
                    onConfirmAccountSuccess();
                    break;
                case Account.ACCOUNT_STATE_VALID_CONFIRM_CODE:
                    mAccountController.createAccountConfirmAccount(mConfirmCode, this);
                    // Set account state to ACCOUNT_STATE_CONFIRMED on success.
                    break;
                default:
                    mAccountController.createAccountValidateConfirmCode(mConfirmCode, this);
                    // Set account state to ACCOUNT_STATE_VALID_CONFIRM_CODE on success.
            }
        }
    }

    private void validateMail() {
        try {
            mAccountController.validateOwnEmailAsync(new GenericActionListener<Void>() {
                @Override
                public void onSuccess(Void object) {
                    nextStepAfterValidate();
                }

                @Override
                public void onFail(String message, String errorIdent) {
                    //hat nicht geklappt
                    nextStepAfterValidate();
                }
            });
        } catch (LocalizedException e) {
            nextStepAfterValidate();
        }
    }

    private void nextStepAfterValidate() {
        try {
            getSimsMeApplication().getPreferencesController().getSharedPreferences().edit().remove(REGISTRATION_TYPE).apply();

            if (mAccountController.getAccount() != null && mAccountController.getAccount().getAllServerAccountIDs() != null) {
                LogUtil.d(TAG, "nextStepAfterValidate: Starting  RestoreBackupActivity ...");
                dismissIdleDialog();
                getSimsMeApplication().getPreferencesController().setRegConfirmCode(mConfirmCode);

                final Intent intent = new Intent(IdentConfirmActivity.this, RuntimeConfig.getClassUtil().getRestoreBackupActivityClass());

                startActivity(intent);
            } else {
                LogUtil.d(TAG, "nextStepAfterValidate: Starting  createAccountConfirmAccount ...");
                mAccountController.createAccountConfirmAccount(mConfirmCode, this);
            }
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            dismissIdleDialog();
        }
    }

    @Override
    public void onValidateConfirmCodeSuccess() {
        if (mRegistrationType == REGISTRATION_TYPE_MAIL) {
            validateMail();
        } else {
            nextStepAfterValidate();
        }
    }

    @Override
    public void onValidateConfirmCodeFail(String message) {
        dismissIdleDialog();
        nextClicked = false;

        DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mAccountController.deleteAccount();
            }
        };

        if (StringUtil.isEqual(message, getString(R.string.notification_account_was_deleted))) {
            DialogBuilderUtil.buildResponseDialog(IdentConfirmActivity.this,
                    getString(R.string.notification_account_was_deleted_registration_text),
                    getString(R.string.notification_account_was_deleted_registration_title),
                    getString(R.string.notification_account_was_deleted_registration_confirm),
                    null,
                    clickListener,
                    null
            ).show();
        } else {
            DialogBuilderUtil.buildErrorDialog(IdentConfirmActivity.this, message).show();
        }
    }

    @Override
    public void onConfirmAccountSuccess() {
        final OnSystemChatCreatedListener onSystemChatCreatedListener = new OnSystemChatCreatedListener() {
            @Override
            public void onSystemChatCreatedSuccess() {
                dismissIdleDialog();

                try {
                    LogUtil.d(TAG, "onConfirmAccountSuccess: Starting  LoginActivity ...");
                    final Intent intent = new Intent(IdentConfirmActivity.this, RuntimeConfig.getClassUtil().getLoginActivityClass());
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public void onSystemChatCreatedError(String message) {
                dismissIdleDialog();
                nextClicked = false;
                mAccountController.resetCreateAccountValidateConfirmCode();
                DialogBuilderUtil.buildErrorDialog(IdentConfirmActivity.this, message).show();
            }
        };

        getSimsMeApplication().getContactController().createSystemChatContact(onSystemChatCreatedListener);
    }

    @Override
    public void onConfirmAccountFail(String message) {
        dismissIdleDialog();
        nextClicked = false;
        DialogBuilderUtil.buildErrorDialog(IdentConfirmActivity.this, message).show();
    }

    protected int getRegistrationType() {
        return mRegistrationType;
    }
}
