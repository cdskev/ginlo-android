// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import dagger.android.AndroidInjection;
import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.contracts.OnDeleteAccountListener;
import eu.ginlo_apps.ginlo.data.network.AppConnectivity;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import javax.inject.Inject;

public class DeleteAccountActivity
        extends BaseActivity {

    public static final String CHECK_ID = "DeleteAccountActivity.checkId";

    private AccountController accountController;

    private EditText mIdInput;

    private boolean mCheckId;

    @Inject
    public AppConnectivity appConnectivity;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        accountController = ((SimsMeApplication) getApplication()).getAccountController();

        final Intent intent = getIntent();
        if (intent.hasExtra(CHECK_ID)) {
            mCheckId = intent.getBooleanExtra(CHECK_ID, false);
        }

        mIdInput = findViewById(R.id.delete_account_id_input);

        if (mCheckId) {
            final View mIdInputContainer = findViewById(R.id.delete_account_id_input_container);
            final View idInputHint = findViewById(R.id.delete_account_id_input_hint);
            idInputHint.setVisibility(View.VISIBLE);
            mIdInputContainer.setVisibility(View.VISIBLE);
            if (BuildConfig.DEBUG) {
                mIdInput.setText(accountController.getAccount().getAccountID());
            }
        }
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_delete_account;
    }

    @Override
    protected void onResumeActivity() {
        //
    }

    public void handleDeleteAccountClick(final View view) {
        if (!appConnectivity.isConnected()) {
            Toast.makeText(this, R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG).show();
            return;
        }

        final OnDeleteAccountListener onDeleteAccountListener = new OnDeleteAccountListener() {
            @Override
            public void onDeleteAccountFail(final String message) {
                dismissIdleDialog();
                DialogBuilderUtil.buildErrorDialog(DeleteAccountActivity.this, message).show();
            }

            @Override
            public void onDeleteAccountSuccess() {
                //Methode wird nur im DEBUG MODE aufgerufen
                dismissIdleDialog();
                getSimsMeApplication().getAppLifecycleController().restartApp();
            }
        };

        if (mCheckId) {
            final String input = mIdInput.getText().toString().toUpperCase();
            if (input.equals(accountController.getAccount().getAccountID().toUpperCase())) {
                accountController.deleteAccount(onDeleteAccountListener);
                showIdleDialog(R.string.progress_dialog_delete_account);
            } else {
                DialogBuilderUtil.buildErrorDialog(this,
                        getResources().getString(R.string.settings_profile_delete_phoneDoesNotMatch))
                        .show();
            }
        } else {
            accountController.deleteAccount(onDeleteAccountListener);
            showIdleDialog(R.string.progress_dialog_delete_account);
        }
    }
}
