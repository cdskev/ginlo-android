// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;

import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity;
import eu.ginlo_apps.ginlo.activity.register.IntroActivity;
import eu.ginlo_apps.ginlo.activity.register.ShowSimsmeIdActivity;
import eu.ginlo_apps.ginlo.activity.register.device.WelcomeBackActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.LoginController;
import eu.ginlo_apps.ginlo.controller.LoginController.LoginListener;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.contracts.OnRequestRecoveryCodeListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.BasePasswordFragment;
import eu.ginlo_apps.ginlo.fragment.ComplexPasswordFragment;
import eu.ginlo_apps.ginlo.fragment.FingerprintFragment;
import eu.ginlo_apps.ginlo.fragment.SimplePasswordFragment;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.Preference;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil.OnCloseListener;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.OnSingleClickListener;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import javax.crypto.Cipher;

public class LoginActivity
        extends NewBaseActivity implements FingerprintFragment.AuthenticationListener {
    private static final String TAG = LoginActivity.class.getSimpleName();
    public static final String EXTRA_NEXT_ACTIVITY = "LoginActivity.nextActivity";
    public static final String EXTRA_MODE = "LoginActivity.mMode";
    public static final String EXTRA_MODE_CHECK_PW = "LoginActivity.modeCheckPassword";
    public static final String PREFS_LOGIN_TRIES = "LoginActivity.prefsLoginTries";

    private static final String FINGERPRINT_FRAGMENT = "fingerprintFragment";

    protected FingerprintFragment mFingerprintFragment;
    Button mLoginButton;
    private AccountController mAccountController;
    private LoginController mLoginController;
    private PreferencesController mPreferencesController;
    private String mNextActivity;
    private Bundle mSavedExtras;
    private BasePasswordFragment mPasswordFragment;
    private Button mForgotPwButton;
    private int mLoginTryCounter;
    private int mMaxLoginTries;
    private boolean mIsCheckMode;
    private boolean mCountTries;
    private SharedPreferences mLoginActivitySettings;
    private final LoginListener mOnLoginCompleteListener = new LoginListener() {
        @Override
        public void onLoginFailed(String aMessage) {
            LogUtil.d(TAG, "onLoginFailed: " + aMessage);
            dismissIdleDialog();
            mPasswordFragment.clearInput();

            if (mIsCheckMode) {
                if (mSavedExtras != null) {
                    Intent intent = new Intent();

                    intent.putExtras(mSavedExtras);
                    setResult(RESULT_CANCELED, intent);
                } else {
                    setResult(RESULT_CANCELED);
                }
            }
            /* Bug 32477 show forgot pw dialog */
            /* Bug 38118  PW-vergessen-dialog auch im checkmode anzeigen*/
            mForgotPwButton.setVisibility(View.VISIBLE);

            final String message;
            if (mCountTries) {
                if (mMaxLoginTries > 0) {
                    mLoginTryCounter++;
                    message = aMessage + "\n" + getString(R.string.login_password_incorrect, (mMaxLoginTries - mLoginTryCounter));

                    if (mLoginTryCounter >= mMaxLoginTries) {
                        mLoginButton.setEnabled(false);
                        mAccountController.deleteAccount();
                    }
                } else {
                    message = getString(R.string.login_password_incorrect_noTries);
                }
            } else {
                message = getString(R.string.login_password_incorrect_noTries);
            }

            OnCloseListener onCloseListener = new OnCloseListener() {
                @Override
                public void onClose(int ref) {
                    showLoginMethod();
                    scrollDown();
                }
            };

            AlertDialogWrapper dialog = DialogBuilderUtil.buildErrorDialog(LoginActivity.this, message, 0,
                    onCloseListener);

            dialog.show();
        }

        @Override
        public boolean onLoginComplete(String password) {
            LogUtil.d(TAG, "onLoginComplete");
            dismissIdleDialog();
            showKeyboard(false);

            mLoginTryCounter = 0;

            try {
                logInSuccessful();
                if (mIsCheckMode) {
                    // TODO Zwischenspeichern des Passwortes ?! --> muss geprüft werden.
                    mAccountController.setPassword(password);
                    if (mSavedExtras != null) {
                        Intent intent = new Intent();

                        intent.putExtras(mSavedExtras);
                        setResult(RESULT_OK, intent);
                    } else {
                        setResult(RESULT_OK);
                    }
                    finish();
                } else {
                    mLoginActivitySettings.edit().putInt(PREFS_LOGIN_TRIES, mLoginTryCounter).apply();
                    startNextActivity(password);
                }
            } catch (ClassNotFoundException | LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
            }

            return true;
        }
    };
    private boolean mRecoveryCodeViaSMS;
    private ImageView mEmailSelectedView;
    private ImageView mSmsSelectedView;
    private LinearLayout mEmailContainerView;
    private LinearLayout mSmsContainerView;
    private View mainView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setAllowEnterTransitionOverlap(false);
        getWindow().setAllowReturnTransitionOverlap(false);
        getWindow().setExitTransition(null);

        // Start GinloOngoingService if applicable
        startGinloOngoingService();
    }

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        this.mLoginController = ((SimsMeApplication) getApplication()).getLoginController();
        this.mAccountController = ((SimsMeApplication) getApplication()).getAccountController();
        this.mPreferencesController = ((SimsMeApplication) getApplication()).getPreferencesController();

        mainView = findViewById(R.id.login_main_view);
        mLoginButton = findViewById(R.id.login_button);

        hideLoginViews();

        mLoginActivitySettings = mPreferencesController.getSharedPreferences();
        mLoginButton.setEnabled(true);

        mForgotPwButton = findViewById(R.id.login_forgot_pw_textview);

        if (StringUtil.isEqual(mLoginController.getState(), "LoginController.loggedIn")) {
            mLoginButton.setText(R.string.intro_nextButtonTitle);
        }

        mLoginTryCounter = mLoginActivitySettings.getInt(PREFS_LOGIN_TRIES, 0);
        mMaxLoginTries = mPreferencesController.getNumberOfPasswordTries();

        mCountTries = mPreferencesController.getDeleteDataAfterTries();

        if (mPreferencesController.getPasswordType() == Preference.TYPE_PASSWORD_SIMPLE) {
            this.mPasswordFragment = new SimplePasswordFragment();
        } else if (mPreferencesController.getPasswordType() == Preference.TYPE_PASSWORD_COMPLEX) {
            this.mPasswordFragment = new ComplexPasswordFragment();
        } else {
            throw new RuntimeException("Wrong Passwordtype");
        }

        getSupportFragmentManager().beginTransaction()
                .add(R.id.login_frame_layout_password_fragment_container, mPasswordFragment).commit();

        mEmailSelectedView = findViewById(R.id.mask_selected_send_recovery_code_mail);
        mSmsSelectedView = findViewById(R.id.mask_selected_send_recovery_code_sms);

        mEmailContainerView = findViewById(R.id.send_recovery_code_mail_container);
        mSmsContainerView = findViewById(R.id.send_recovery_code_sms_container);
    }

    private void showKeyboard(final boolean show) {
        // nur die Tastatur ausfahren, wenn man nicht gerade im PW-vergessen-Modus ist
        final View forgotPwView = findViewById(R.id.login_forgot_pw_view);
        if (forgotPwView != null && forgotPwView.getVisibility() == View.GONE) {
            final EditText editText = mPasswordFragment.getEditText();

            if (editText != null) {
                KeyboardUtil.toggleSoftInputKeyboard(LoginActivity.this, editText, show);
            } else {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //try again later
                        final EditText editText = mPasswordFragment.getEditText();
                        if (editText != null) {
                            KeyboardUtil.toggleSoftInputKeyboard(LoginActivity.this, editText, show);
                        }
                    }
                }, 100);
            }
        }
    }

    private void showLoginViews() {
        mainView.setVisibility(View.VISIBLE);
        mLoginButton.setVisibility(View.VISIBLE);
    }

    private void hideLoginViews() {
        mainView.setVisibility(View.GONE);
        mLoginButton.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        final View forgotPwView;
        if (!mPreferencesController.isRecoveryDisabled()
                && mPreferencesController.getRecoveryCodeEnabled()
                && (mPreferencesController.getRecoveryTokenPhone() != null || mPreferencesController.getRecoveryTokenEmail() != null)) {
            forgotPwView = findViewById(R.id.login_forgot_pw_tel_email_view);
        } else {
            forgotPwView = findViewById(R.id.login_forgot_pw_view);
        }
        final View loginButton = findViewById(R.id.login_button);

        if (mainView != null && forgotPwView != null && forgotPwView.getVisibility() == View.VISIBLE) {
            forgotPwView.setVisibility(View.GONE);
            mainView.setVisibility(View.VISIBLE);
            loginButton.setVisibility(View.VISIBLE);
        } else if (!mIsCheckMode) {
            startHome();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_login;
    }

    @Override
    protected void onResumeActivity() {
        handleLoginState();
    }

    private void startHome() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
    }

    private void handleLoginState() {
        Intent intent = getIntent();

        if (intent.hasExtra(EXTRA_MODE) && intent.getStringExtra(EXTRA_MODE).equals(EXTRA_MODE_CHECK_PW)) {
            mIsCheckMode = true;
            if (intent.getExtras() != null) {
                mSavedExtras = intent.getExtras();
            }

            if (mPreferencesController.getHasSystemGeneratedPasword()) {
                mOnLoginCompleteListener.onLoginComplete(null);
            }

            mLoginButton.setText(getString(R.string.intro_nextButtonTitle));

            showLoginViews();
            showLoginMethod();

        } else {
            ActionBar actionBar = getSupportActionBar();

            if (actionBar != null) {
                actionBar.hide();
            }

            mIsCheckMode = false;
            mLoginButton.setText(getString(R.string.login_loginButton));
            if (intent.hasExtra(EXTRA_NEXT_ACTIVITY)) {
                mNextActivity = intent.getExtras().getString(EXTRA_NEXT_ACTIVITY);
                intent.getExtras().remove(EXTRA_NEXT_ACTIVITY);
            }

            if (intent.getExtras() != null) {
                mSavedExtras = intent.getExtras();
            }

            if (mPreferencesController.getPasswordEnabled()) {
                showLoginViews();
                if (mLoginController.getState().equals(LoginController.STATE_LOGGED_OUT)) {
                    if ((mNextActivity != null) && (mAccountController.getPassword() != null)) {
                        showIdleDialog(-1);
                        mLoginController.login(this, false, mAccountController.getPassword(), mOnLoginCompleteListener);
                    } else {
                        showLoginMethod();
                    }
                } else {
                    if (mNextActivity == null) {
                        mNextActivity = RuntimeConfig.getClassUtil().getStartActivityClass(getSimsMeApplication()).getName();
                    }

                    mLoginController.loginCompleteSuccess(this, mOnLoginCompleteListener, null);
                }
            } else {
                View fragmentContainer = findViewById(R.id.login_frame_layout_password_fragment_container);

                if (fragmentContainer != null) {
                    fragmentContainer.setVisibility(View.GONE);
                }
                if (mLoginButton != null) {
                    mLoginButton.setVisibility(View.GONE);
                }

                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

                mNextActivity = (mNextActivity == null) ? RuntimeConfig.getClassUtil().getStartActivityClass(getSimsMeApplication()).getName() : mNextActivity;
                mLoginController.loginWithoutPassword(this, mOnLoginCompleteListener);
            }
        }
        LogUtil.d(TAG, "handleLoginState: mNextActivity: " + mNextActivity + ", mSavedExtras: " + mSavedExtras);

        final EditText editText = mPasswordFragment.getEditText();

        if (editText != null) {
            editText.setOnClickListener(new OnSingleClickListener() {
                @Override
                public void onSingleClick() {
                    scrollDown();
                }
            });
        }

        scrollDown();
    }

    private void scrollDown() {
        final ScrollView scrollView = findViewById(R.id.activity_login_scrollview);
        if (scrollView != null) {
            final Handler handler = new Handler();
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    scrollView.fullScroll(View.FOCUS_DOWN);
                }
            };
            // die Tastatur braucht eine Weile zum ausfahren. Man erhaelt abe rkeine Info, wann sie fertig ist
            // daher: mehrfach versuchen, damit es auf schnellen Geraeten schnell geht und grundsaetzlich auch auf langsamen
            handler.postDelayed(runnable, 250);
            handler.postDelayed(runnable, 500);
            handler.postDelayed(runnable, 1000);
        }
    }

    @Override
    protected void onPauseActivity() {
        //dismissidledialog, damit es keinen WindowLeak gibt
        dismissIdleDialog();
        super.onPauseActivity();
        mLoginActivitySettings.edit().putInt(PREFS_LOGIN_TRIES, mLoginTryCounter).apply();
    }

    private void startNextActivity(final String password)
            throws LocalizedException, ClassNotFoundException {
        Class<?> nextActivityClass;

        Bundle extras = new Bundle();

        int state = mAccountController.getAccountState();
        //Der Account muss erst Confirmed sein, bevor die Licence ueberprueft wird
        switch (state) {
            case Account.ACCOUNT_STATE_FULL: {
                nextActivityClass = getNextActivityClassForFullAccountState(password, extras);
                LogUtil.d(TAG, "startNextActivity: ACCOUNT_STATE_FULL -> " + nextActivityClass.getName());
                break;
            }
            case Account.ACCOUNT_STATE_CONFIRMED: {
                nextActivityClass = getNextActivityClassForConfirmedAccountState(password, extras);
                LogUtil.d(TAG, "startNextActivity: ACCOUNT_STATE_CONFIRMED -> " + nextActivityClass.getName());
                break;
            }
            case Account.ACCOUNT_STATE_VALID_CONFIRM_CODE: {
                try {
                    if (mAccountController.getAccount() != null
                            && (mAccountController.getAccount().getAllServerAccountIDs() != null
                            || mPreferencesController.getSharedPreferences().getInt(WelcomeBackActivity.WELCOME_BACK_MODE, WelcomeBackActivity.UNKNOWN) == WelcomeBackActivity.MODE_BACKUP)) {
                        // One shot only
                        mPreferencesController.getSharedPreferences().edit().remove(WelcomeBackActivity.WELCOME_BACK_MODE).apply();
                        nextActivityClass = RuntimeConfig.getClassUtil().getRestoreBackupActivityClass();
                    } else {
                        nextActivityClass = RuntimeConfig.getClassUtil().getIdentConfirmActivityClass();
                    }
                } catch (LocalizedException e) {
                    LogUtil.w(TAG, e.getMessage(), e);
                    nextActivityClass = RuntimeConfig.getClassUtil().getIdentConfirmActivityClass();
                }
                LogUtil.d(TAG, "startNextActivity: ACCOUNT_STATE_VALID_CONFIRM_CODE -> " + nextActivityClass.getName());
                break;
            }
            case Account.ACCOUNT_STATE_NOT_CONFIRMED: {
                nextActivityClass = RuntimeConfig.getClassUtil().getIdentConfirmActivityClass();
                LogUtil.d(TAG, "startNextActivity: ACCOUNT_STATE_NOT_CONFIRMED -> " + nextActivityClass.getName());
                break;
            }
            case Account.ACCOUNT_STATE_AUTOMATIC_MDM_PROGRESS: {
                mAccountController.resetCreateAccountRegisterPhone();
                getSimsMeApplication().safeDeleteAccount();
                nextActivityClass = IntroActivity.class;
                LogUtil.d(TAG, "startNextActivity: ACCOUNT_STATE_AUTOMATIC_MDM_PROGRESS -> " + nextActivityClass.getName());
                break;
            }
            default: {
                nextActivityClass = MainActivity.class;
                LogUtil.d(TAG, "startNextActivity: No defined ACCOUNT_STATE -> " + nextActivityClass.getName());
            }
        }

        Intent intent = getIntentFromCallerIntent(nextActivityClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (extras.size() > 0) {
            intent.putExtras(extras);
        }

        if(preferencesController.getPollingEnabled() && !mApplication.havePlayServices(this)) {
            requestBatteryWhitelisting();
        }

        mAccountController.clearPassword();
        LoginActivity.this.startActivity(intent);
    }

    public void handleConfirmResetClick(View unused_but_needed_for_xml) {
        DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog,
                                int which) {
                mAccountController.deleteAccount();
                LoginActivity.this.finish();
            }
        };

        DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog,
                                int which) {
            }
        };

        String title = getResources().getString(R.string.settings_password_forgot_dialog_title);
        String message = getResources().getString(R.string.settings_password_forgot_dialog_text);
        String positiveButton = getResources().getString(R.string.settings_password_forgot_pw_confirm_reset);
        String negativeButton = getResources().getString(R.string.settings_password_forgot_pw_cancel_reset);

        final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this, message,
                title,
                positiveButton,
                negativeButton,
                positiveOnClickListener,
                negativeOnClickListener);

        dialog.show();
    }

    public void handleCancelResetClick(View view) {
        final View forgotPwView = findViewById(R.id.login_forgot_pw_view);
        final View loginButton = findViewById(R.id.login_button);
        if (forgotPwView != null && mainView != null) {
            mainView.setVisibility(View.VISIBLE);
            loginButton.setVisibility(View.VISIBLE);
            forgotPwView.setVisibility(View.GONE);
            KeyboardUtil.toggleSoftInputKeyboard(LoginActivity.this, getCurrentFocus(), true);
        }
    }

    public void handleRequestTelSmsClick(View view) {
        showIdleDialog();
        final String recoveryToken;
        if (mRecoveryCodeViaSMS) {
            recoveryToken = mPreferencesController.getRecoveryTokenPhone();
        } else {
            recoveryToken = mPreferencesController.getRecoveryTokenEmail();
        }
        // der Token kann eigentlich nicht null sein, da man sonst auf einen anderen Button geklickt haette
        if (recoveryToken != null) {

            OnRequestRecoveryCodeListener onRequestRecoveryCodeListener = new OnRequestRecoveryCodeListener() {
                @Override
                public void onRequestFailed(@NonNull String errorMessage) {
                    DialogBuilderUtil.buildErrorDialog(LoginActivity.this, errorMessage).show();
                    dismissIdleDialog();
                }

                @Override
                public void onRequestSuccess() {
                    final Intent intent = new Intent(LoginActivity.this, RecoverPasswordActivity.class);
                    startActivity(intent);
                    finish();
                    dismissIdleDialog();
                }
            };
            mAccountController.requestRecoveryCode(onRequestRecoveryCodeListener, recoveryToken);
        } else {
            LogUtil.e(TAG, "handleRequestTelSmsClick failed, recovery token is null");
            DialogBuilderUtil.buildErrorDialog(LoginActivity.this, getResources().getString(R.string.action_failed));
        }
    }

    public void handleForgotPwClick(View view) {
        KeyboardUtil.toggleSoftInputKeyboard(LoginActivity.this, getCurrentFocus(), false);

        final View forgotPwView;

        final String recoveryTokenPhone = mPreferencesController.getRecoveryTokenPhone();
        final String recoveryTokenEmail = mPreferencesController.getRecoveryTokenEmail();

        final String backgroundAccessToken = getSimsMeApplication().getMessageController().getMessageDeviceToken();

        // es kann vorkommen, dass man in diese Funktion kommt, und die beiden Recovery Tokens noch nicht gesetzt wurden, da das SPreizen des Keys eine Zeit dauert
        // ausserdem muss der backgroundaccesstoken gesetzt sein
        if (!StringUtil.isNullOrEmpty(backgroundAccessToken)
                && !mPreferencesController.isRecoveryDisabled()
                && mPreferencesController.getRecoveryCodeEnabled()
                && (recoveryTokenPhone != null || recoveryTokenEmail != null)) {
            forgotPwView = findViewById(R.id.login_forgot_pw_tel_email_view);

            if (recoveryTokenEmail != null && recoveryTokenPhone == null) {
                mSmsContainerView.setVisibility(View.GONE);
                mSmsSelectedView.setVisibility(View.GONE);
                mEmailSelectedView.setVisibility(View.VISIBLE);
                mRecoveryCodeViaSMS = false;
            } else if (recoveryTokenEmail == null) {
                mEmailContainerView.setVisibility(View.GONE);
                mSmsSelectedView.setVisibility(View.VISIBLE);
                mEmailSelectedView.setVisibility(View.GONE);
                mRecoveryCodeViaSMS = true;
            } else // beide != null
            {
                mSmsSelectedView.setVisibility(View.VISIBLE);
                mEmailSelectedView.setVisibility(View.GONE);
                mRecoveryCodeViaSMS = true;
            }
        } else {
            forgotPwView = findViewById(R.id.login_forgot_pw_view);
        }

        final View loginButton = findViewById(R.id.login_button);
        if (forgotPwView != null && mainView != null) {
            mainView.setVisibility(View.GONE);
            loginButton.setVisibility(View.GONE);
            forgotPwView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Nutzer moechte dne Recovery Coder gerne per E-Mail bekommen
     */
    public void handleSendViaEmailClick(final View view) {

        mEmailSelectedView.setVisibility(View.VISIBLE);
        mSmsSelectedView.setVisibility(View.GONE);

        mRecoveryCodeViaSMS = false;
    }

    /**
     * Nutzer moechte dne Recovery Coder gerne per SMS bekommen
     */
    public void handleSendViaSmsClick(final View view) {
        mEmailSelectedView.setVisibility(View.GONE);
        mSmsSelectedView.setVisibility(View.VISIBLE);
        mRecoveryCodeViaSMS = true;
    }

    public void handleLoginClick(View view) {
        handleLogin();
    }

    public void handleLogin() {
        String password = mPasswordFragment.getPassword();

        if (password != null) {
            showIdleDialog(R.string.progress_dialog_login);

            if (mNextActivity == null) {
                mNextActivity = RuntimeConfig.getClassUtil().getStartActivityClass(getSimsMeApplication()).getName();
            }
            mLoginController.login(this, mIsCheckMode, password, mOnLoginCompleteListener);
        }
    }

    private void handleLoginBiometric(@NonNull final Cipher decryptCipher) {
        showIdleDialog();

        if (mNextActivity == null) {
            mNextActivity = RuntimeConfig.getClassUtil().getStartActivityClass(getSimsMeApplication()).getName();
        }

        mLoginController.loginWithBiometric(this, mOnLoginCompleteListener, decryptCipher);
    }

    void logInSuccessful() {
        // Overwritten in subclass
    }

    Class<?> getNextActivityClassForFullAccountState(final String password, final Bundle extras)
            throws ClassNotFoundException, LocalizedException {
        Class<?> nextActivityClass;

        if (mNextActivity != null) {
            nextActivityClass = Class.forName(mNextActivity);
        } else {
            // Fallback
            nextActivityClass = RuntimeConfig.getClassUtil().getChatOverviewActivityClass();
        }
        return nextActivityClass;
    }

    Class<?> getNextActivityClassForConfirmedAccountState(final String password, final Bundle extras)
            throws ClassNotFoundException, LocalizedException {
        final boolean simsmeIdShownAtReg = mPreferencesController.getSimsmeIdShownAtReg();
        if (simsmeIdShownAtReg) {
            return RuntimeConfig.getClassUtil().getInitProfileActivityClass();
        } else {
            mPreferencesController.setSimsmeIdShownAtReg();
            return ShowSimsmeIdActivity.class;
        }
    }

    private void onCloseFingerprintClicked() {
        if (mFingerprintFragment != null) {
            mFingerprintFragment.dismiss();
            mFingerprintFragment.cancelListening();
            showKeyboard(true);
        }
    }

    private void showFingerprintFragment() {
        if (mFingerprintFragment == null) {
            mFingerprintFragment = FingerprintFragment.newInstance(FingerprintFragment.MODE_LOGIN, this);
        }
        if (!mFingerprintFragment.isVisible() && !mFingerprintFragment.isAdded()) {
            mFingerprintFragment.show(getSupportFragmentManager(), FINGERPRINT_FRAGMENT);
        }
    }

    private void showLoginMethod() {
        if (!mIsCheckMode && getSimsMeApplication().getPreferencesController().getBiometricAuthEnabled() && getSimsMeApplication().getAccountController().isBiometricAuthAvailable()) {
            showFingerprintFragment();
        } else {
            showKeyboard(true);
        }
    }

    @Override
    public void onAuthenticationSucceeded(Cipher cipher) {
        if (cipher != null) {
            handleLoginBiometric(cipher);
        }
    }

    @Override
    public void onAuthenticationCancelled() {
        onCloseFingerprintClicked();
    }
}
