// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;

import eu.ginlo_apps.ginlo.LoginActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.KeyController.OnKeysLoadedListener;
import eu.ginlo_apps.ginlo.controller.KeyController.OnKeysSavedListener;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

public class LoginController {
    public static final String STATE_LOGGED_IN = "LoginController.loggedIn";

    public static final String STATE_LOGGED_OUT = "LoginController.loggedOut";

    public static final String STATE_NO_ACCOUNT = "LoginController.noAccount";

    private final ArrayList<AppLockLifecycleCallbacks> mAllcList = new ArrayList<>();
    private final SimsMeApplication mApplication;
    private String state;
    private List<Object> mPKBackgroundTasks;

    public LoginController(final SimsMeApplication application) {
        this.mApplication = application;
    }

    public void changePassword(final String newPassword,
                               final PasswordChangeListener onPasswordChangedListener) {
        OnKeysSavedListener keysSavedListener = new OnKeysSavedListener() {
            @Override
            public void onKeysSaveFailed() {
                onPasswordChangedListener.onPasswordChangeFail();
            }

            @Override
            public void onKeysSaveComplete() {
                onPasswordChangedListener.onPasswordChangeSuccess(newPassword);
            }
        };

        mApplication.getKeyController().saveKeys(newPassword, keysSavedListener);
    }

    public void login(final Activity activity,
                      boolean checkPassword,
                      final String password,
                      final LoginListener onLoginCompleteListener) {
        OnKeysLoadedListener keysLoadedListener = new OnKeysLoadedListener() {
            @Override
            public void onKeysLoadedFailed(boolean incorrectCredentials) {
                final String message;

                if (incorrectCredentials) {
                    message = activity.getResources().getString(R.string.login_failed_credentials);
                } else {
                    message = activity.getResources().getString(R.string.login_failed);
                }

                onLoginCompleteListener.onLoginFailed(message);
            }

            @Override
            public void onKeysLoadedComplete() {
                LogUtil.i(this.getClass().getName(), "login:keysLoadedComplete");

                loginCompleteSuccess(activity, onLoginCompleteListener, password);
            }
        };

        mApplication.getKeyController().loadKeysWithPassword(password, checkPassword, keysLoadedListener);
    }

    public void loginWithoutPassword(final Activity activity,
                                     final LoginListener onLoginCompleteListener) {
        OnKeysLoadedListener keysLoadedListener = new OnKeysLoadedListener() {
            @Override
            public void onKeysLoadedFailed(boolean incorrectCredentials) {
                final String message = activity.getResources().getString(R.string.login_failed);

                onLoginCompleteListener.onLoginFailed(message);
            }

            @Override
            public void onKeysLoadedComplete() {
                LogUtil.i(this.getClass().getName(), "login:keysLoadedComplete");

                loginCompleteSuccess(activity, onLoginCompleteListener, null);
            }
        };

        mApplication.getKeyController().loadKeysWithoutPassword(keysLoadedListener);
    }

    public void loginWithBiometric(final Activity activity,
                                   final LoginListener onLoginCompleteListener,
                                   final Cipher decryptCipher) {
        OnKeysLoadedListener keysLoadedListener = new OnKeysLoadedListener() {
            @Override
            public void onKeysLoadedFailed(boolean incorrectCredentials) {
                final String message = activity.getResources().getString(R.string.login_failed);

                onLoginCompleteListener.onLoginFailed(message);
            }

            @Override
            public void onKeysLoadedComplete() {
                LogUtil.i(this.getClass().getName(), "login:keysLoadedComplete");

                loginCompleteSuccess(activity, onLoginCompleteListener, null);
            }
        };

        mApplication.getKeyController().loadKeysWithBiometricCipher(decryptCipher, keysLoadedListener);
    }

    /**
     * @param recoveryDevicePrivateKeyXMLs recovery key
     */
    public void handleRecoveryLogin(final LoginListener onLoginCompleteListener, final String recoveryDevicePrivateKeyXMLs) {

        OnKeysLoadedListener keysLoadedListener = new OnKeysLoadedListener() {
            @Override
            public void onKeysLoadedFailed(boolean incorrectCredentials) {
                onLoginCompleteListener.onLoginFailed(mApplication.getResources().getString(R.string.action_failed));
            }

            @Override
            public void onKeysLoadedComplete() {
                LogUtil.i(this.getClass().getName(), "handleRecoveryLogin:keysLoadedComplete");

                final AccountController accountController = mApplication.getAccountController();
                final PreferencesController preferencesController = mApplication.getPreferencesController();

                preferencesController.getPreferences();
                state = STATE_LOGGED_IN;
                for (AppLockLifecycleCallbacks cb : mAllcList) {
                    cb.appIsUnlock();
                }
                onLoginCompleteListener.onLoginComplete("");
            }
        };
        mApplication.getKeyController().loadKeysWithRecoveryKey(keysLoadedListener, recoveryDevicePrivateKeyXMLs);
    }

    public void loginCompleteSuccess(final Activity activity, final LoginListener onLoginCompleteListener, final String password) {
        AccountController accountController = mApplication.getAccountController();
        PreferencesController preferencesController = mApplication.getPreferencesController();
        preferencesController.getPreferences();
        state = STATE_LOGGED_IN;

        if (onLoginCompleteListener != null) {
            if (onLoginCompleteListener.onLoginComplete(password)) {
                informListenerAfterLogin(activity);
            }
        }
    }

    public void informListenerAfterLogin(final Activity activity) {
        if (activity instanceof LoginActivity) {
            Intent intent = activity.getIntent();
            Bundle intentExtras = (intent != null) ? intent.getExtras() : null;
            String loginMode = (intentExtras != null) ? intentExtras.getString(LoginActivity.EXTRA_MODE)
                    : null;

            if ((!LoginActivity.EXTRA_MODE_CHECK_PW.equals(loginMode))) {
                for (AppLockLifecycleCallbacks cb : mAllcList) {
                    cb.appIsUnlock();
                }
            }
        }
    }

    public void logout() {
        if (getState().equals(STATE_LOGGED_IN)) {
            for (AppLockLifecycleCallbacks cb : mAllcList) {
                cb.appWillBeLocked();
            }

            mApplication.getKeyController().clearKeys();
            mApplication.getTaskManagerController().cancelAllTasks();
            mApplication.getSingleChatController().clearCache();
            mApplication.getGroupChatController().clearCache();
            mApplication.getAttachmentController().clearCache();
            mApplication.getMessageDecryptionController().clearAesCache();
            mApplication.getAccountController().clearPassword();

            state = STATE_LOGGED_OUT;
        }
    }

    public String getState() {
        if (state == null) {

            if (mApplication.getAccountController().getAccount() != null) {
                state = STATE_LOGGED_OUT;
            } else {
                state = STATE_NO_ACCOUNT;
            }
        }

        return state;
    }

    public boolean isLoggedIn() {
        return StringUtil.isEqual(getState(), STATE_LOGGED_IN);
    }

    public void registerAppLockLifecycleCallbacks(AppLockLifecycleCallbacks callbacks) {
        synchronized (mAllcList) {
            mAllcList.add(callbacks);
        }
    }

    synchronized void setPKTaskIsRunning(@NonNull final Object task, final boolean isRunning) {
        if (!isRunning && mPKBackgroundTasks == null && !getState().equals(STATE_LOGGED_IN)) {
            mApplication.getKeyController().clearKeys();
            return;
        }

        if (mPKBackgroundTasks == null) {
            mPKBackgroundTasks = new ArrayList<>(10);
        }

        if (!isRunning) {
            mPKBackgroundTasks.remove(task);

            if (mPKBackgroundTasks.size() < 1 && !getState().equals(STATE_LOGGED_IN)) {
                mApplication.getKeyController().clearKeys();
            }
        } else {
            if (!mPKBackgroundTasks.contains(task)) {
                mPKBackgroundTasks.add(task);
            }
        }
    }

    public interface LoginListener {

        boolean onLoginComplete(String password);

        void onLoginFailed(String message);
    }

    public interface PasswordChangeListener {

        void onPasswordChangeSuccess(String newPassword);

        void onPasswordChangeFail();
    }

    public interface AppLockLifecycleCallbacks {

        void appIsUnlock();

        void appWillBeLocked();
    }
}
