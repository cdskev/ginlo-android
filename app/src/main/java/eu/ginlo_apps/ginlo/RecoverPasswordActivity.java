// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import dagger.android.AndroidInjection;
import static eu.ginlo_apps.ginlo.LoginActivity.PREFS_LOGIN_TRIES;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.SetPasswordActivity;
import eu.ginlo_apps.ginlo.controller.AccountController;
import static eu.ginlo_apps.ginlo.controller.AccountController.MC_RECOVERY_CODE_REQUESTED;
import eu.ginlo_apps.ginlo.controller.LoginController.LoginListener;
import eu.ginlo_apps.ginlo.data.network.AppConnectivity;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.ViewUtil;
import java.io.IOException;
import javax.crypto.SecretKey;
import javax.inject.Inject;

public class RecoverPasswordActivity extends BaseActivity {
    private static final int REQUEST_CODE_PASSWORD_ACTIVITY = 500;

    private AccountController mAccountController;

    private EditText mText1;
    private EditText mText2;
    private EditText mText3;
    private EditText mText4;
    private EditText mText5;
    private EditText mText6;

    @Inject
    public AppConnectivity appConnectivity;

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        mAccountController = getSimsMeApplication().getAccountController();

        final Button nextbutton = findViewById(R.id.next_button);
        nextbutton.setText(getResources().getString(R.string.intro_nextButtonTitle));

        mText1 = findViewById(R.id.enter_6x4_code_edittext_1);
        mText2 = findViewById(R.id.enter_6x4_code_edittext_2);
        mText3 = findViewById(R.id.enter_6x4_code_edittext_3);
        mText4 = findViewById(R.id.enter_6x4_code_edittext_4);
        mText5 = findViewById(R.id.enter_6x4_code_edittext_5);
        mText6 = findViewById(R.id.enter_6x4_code_edittext_6);

        ViewUtil.createTextWatcher(this, null, mText1, mText2, 4);
        ViewUtil.createTextWatcher(this, mText1, mText2, mText3, 4);
        ViewUtil.createTextWatcher(this, mText2, mText3, mText4, 4);
        ViewUtil.createTextWatcher(this, mText3, mText4, mText5, 4);
        ViewUtil.createTextWatcher(this, mText4, mText5, mText6, 4);
        ViewUtil.createTextWatcher(this, mText5, mText6, null, 4);

        ViewUtil.createOnKeyListener(mText1, mText2);
        ViewUtil.createOnKeyListener(mText2, mText3);
        ViewUtil.createOnKeyListener(mText3, mText4);
        ViewUtil.createOnKeyListener(mText4, mText5);
        ViewUtil.createOnKeyListener(mText5, mText6);

        final TextView headerTextView = findViewById(R.id.enter_6x4_header);
        headerTextView.setText(getResources().getString(R.string.recover_password_title));

        final TextView hintView = findViewById(R.id.enter_6x4_hint);
        hintView.setText(getResources().getString(R.string.recover_password_hint));
    }

    public void handleNextClick(final View view) {
        hideErrorView();
        if (!appConnectivity.isConnected()) {
            Toast.makeText(this, R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG).show();
            return;
        }
        final String recoveryCode = mText1.getText().toString() + mText2.getText() + mText3.getText() + mText4.getText() + mText5.getText() + mText6.getText();

        if (StringUtil.isNullOrEmpty(recoveryCode)) {
            DialogBuilderUtil.buildErrorDialog(RecoverPasswordActivity.this, getResources().getString(R.string.recover_password_recovery_key_empty)).show();
        } else {
            recover(recoveryCode);
        }
    }

    private void recover(final String recoveryCode) {
        final SecurityUtil.OnDeriveKeyCompleteListener onDeriveKeyCompleteListener = new SecurityUtil.OnDeriveKeyCompleteListener() {
            @Override
            public void onComplete(final SecretKey key, final byte[] usedSalt) {
                // privaten schlüssel mit aeskey verschlüsseln und auf geraet speichern (ioexception)
                try {
                    // key entschlüsseln
                    final String newKey = SecurityUtil.readKeyFromDisc(key, getSimsMeApplication());
                    // app intern entsperren
                    final LoginListener loginListener = new LoginListener() {
                        @Override
                        public boolean onLoginComplete(final String password) {
                            getSimsMeApplication().getPreferencesController().getSharedPreferences().edit().putInt(PREFS_LOGIN_TRIES, 0).apply();
                            //SetPasswordActivity starten -> startactivityforresult
                            final Intent intent = new Intent(RecoverPasswordActivity.this, SetPasswordActivity.class);
                            startActivityForResult(intent, REQUEST_CODE_PASSWORD_ACTIVITY);
                            dismissIdleDialog();
                            return true;
                        }

                        @Override
                        public void onLoginFailed(final String message) {
                            dismissIdleDialog();
                            showErrorView();
                        }
                    };
                    loginController.handleRecoveryLogin(loginListener, newKey);
                } catch (IOException | LocalizedException e) {
                    dismissIdleDialog();
                    // hier kommt man raus, wenn der falsche recovery-code eingegeben wurde
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            showErrorView();
                        }
                    });
                }
            }

            @Override
            public void onError() {
                // key konnt enicht gespreizt werden -> sollte nicht vorkommen
                dismissIdleDialog();
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        showErrorView();
                    }
                });
            }
        };
        showIdleDialog();
        SecurityUtil.deriveKeyFromPassword(recoveryCode, new byte[32], onDeriveKeyCompleteListener, 80000, SecurityUtil.DERIVE_ALGORITHM_SHA_256, true);
    }

    private void showErrorView() {
        final View errorView = findViewById(R.id.enter_6x4_code_top_warning);
        errorView.setVisibility(View.VISIBLE);
        final TextView errorText = errorView.findViewById(R.id.enter_6x4_code_top_warning_text);
        errorText.setText(getResources().getString(R.string.recover_password_recovery_key_wrong));
    }

    private void hideErrorView() {
        final View errorView = findViewById(R.id.enter_6x4_code_top_warning);
        errorView.setVisibility(View.GONE);
    }

    @Override
    protected void onActivityResult(final int requestCode,
                                    final int resultCode,
                                    final Intent data) {
        if (REQUEST_CODE_PASSWORD_ACTIVITY == requestCode) {
            if (resultCode == RESULT_OK) {
                setResult(RESULT_OK);
                final Intent intent = new Intent(this, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                //alten recoverykey löschen und neuen erstellen
                // TODO: Async!
                mAccountController.unsetRecoveryCode();
                getSimsMeApplication().getPreferencesController().getSharedPreferences().edit().putBoolean(MC_RECOVERY_CODE_REQUESTED, false).apply();
                // der recovery-code wird spaeter beim Setzen des Passwortes neu generiert
            }
        }
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_enter_6x4_code;
    }

    @Override
    protected void onResumeActivity() {

    }

    /**
     * diese Methode muss ueberschrieben werden, da sonst die BaseActivity eine Exception wirft
     *
     * @return immer false
     */
    @Override
    protected boolean isLogout() {
        return false;
    }
}
