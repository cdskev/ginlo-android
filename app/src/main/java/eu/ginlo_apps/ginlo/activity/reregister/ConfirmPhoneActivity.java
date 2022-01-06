// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.reregister;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.register.IdentConfirmActivity;
import eu.ginlo_apps.ginlo.controller.contracts.PhoneOrEmailActionListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;

/**
 * Created by SGA on 30.01.2018.
 */

public class ConfirmPhoneActivity extends IdentConfirmActivity {

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        try {
            getSimsMeApplication().getPreferencesController().getSharedPreferences().edit().putInt(REGISTRATION_TYPE, REGISTRATION_TYPE_PHONE).commit();
            super.onCreateActivity(savedInstanceState);
            mBackAllowed = true;
            final String phoneNumber = getSimsMeApplication().getAccountController().getPendingPhoneNumber();

            mIdentConfirmLabel.setText(getResources().getString(R.string.registration_textView_killTextView, phoneNumber));
        } catch (final LocalizedException e) {
            final DialogBuilderUtil.OnCloseListener onCloseListener = new DialogBuilderUtil.OnCloseListener() {

                @Override
                public void onClose(final int ref) {
                    ConfirmPhoneActivity.this.finish();
                }
            };
            DialogBuilderUtil.buildErrorDialog(ConfirmPhoneActivity.this, getResources().getString(R.string.change_phone_number_error), 0, onCloseListener).show();
        }
    }

    public void handleNextClick(final View view) {
        mConfirmCode = mConfirmCodeEditText1.getText().toString() + mConfirmCodeEditText2.getText().toString();

        if (StringUtil.isNullOrEmpty(mConfirmCode)) {
            AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(this,
                    getString(R.string.registration_label_confirmationLabel_empty));

            alert.show();
            return;
        }

        showIdleDialog(R.string.progress_dialog_ident_confirm);

        final PhoneOrEmailActionListener phoneOrEmailActionListener = new PhoneOrEmailActionListener() {

            @Override
            public void onSuccess(final String result) {
                dismissIdleDialog();

                getSimsMeApplication().getPreferencesController().getSharedPreferences().edit().remove(REGISTRATION_TYPE).apply();

                //Nummer geändert --> Passtoken neu generieren
                getSimsMeApplication().getPreferencesController().checkRecoveryCodeToBeSet(true);

                startNextActivity();
            }

            @Override
            public void onFail(final String errorMsg, boolean emailIsInUse) {
                dismissIdleDialog();
                DialogBuilderUtil.buildErrorDialog(ConfirmPhoneActivity.this, errorMsg).show();
            }
        };
        getSimsMeApplication().getAccountController().confirmConfirmPhone(mConfirmCode, phoneOrEmailActionListener);
    }

    @Override
    public void onBackPressed() {
        if (getSimsMeApplication().getAppLifecycleController().getActivityStackSize() == 1) {
            Intent intent = new Intent(this, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());
            startActivity(intent);
        }
        finish();
    }

    void startNextActivity() {
        final Intent intent = new Intent(ConfirmPhoneActivity.this, RuntimeConfig.getClassUtil().getLoginActivityClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
