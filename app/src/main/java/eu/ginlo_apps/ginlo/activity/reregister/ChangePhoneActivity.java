// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.reregister;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import dagger.android.AndroidInjection;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.register.IdentConfirmActivity;
import eu.ginlo_apps.ginlo.activity.register.IdentRequestActivity;
import eu.ginlo_apps.ginlo.activity.reregister.ConfirmPhoneActivity;
import eu.ginlo_apps.ginlo.controller.contracts.PhoneOrEmailActionListener;
import eu.ginlo_apps.ginlo.data.network.AppConnectivity;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.RegisterPhoneFragment;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.PhoneNumberUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import javax.inject.Inject;

/**
 * Created by SGA on 29.01.2018.
 */

public class ChangePhoneActivity extends IdentRequestActivity {
    public static final String PREFILLED_PHONENUMBER = "ChangePhoneActivity.prefilledPhoneNumber";
    private boolean mPrefilled = false;
    private boolean mOverWrite = false;

    @Inject
    public AppConnectivity appConnectivity;

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        try {
            super.onCreateActivity(savedInstanceState);

            View identSpinner = findViewById(R.id.intro_ident_request_spinner);
            if (identSpinner != null) {
                identSpinner.setVisibility(View.GONE);
            }

            View identSpinnerLabel = findViewById(R.id.intro_ident_request_spinner_label);
            if (identSpinnerLabel != null) {
                identSpinnerLabel.setVisibility(View.GONE);
            }

            final Button nextButton = findViewById(R.id.registration_continue_button);
            if (nextButton != null) {
                nextButton.setText(R.string.register_email_address_button_get_code);
            }

            final TextView headline = findViewById(R.id.intro_ident_request_headline);
            if (headline != null) {
                headline.setText(R.string.change_phone_number_header);
            }

            if (savedInstanceState != null) {
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.intro_ident_request_fragment);

                if (currentFragment instanceof RegisterPhoneFragment) {
                    mPhoneFragment = (RegisterPhoneFragment) currentFragment;
                }
            }

            if (mPhoneFragment == null) {
                mPhoneFragment = new RegisterPhoneFragment();

                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.add(R.id.intro_ident_request_fragment, mPhoneFragment);
                ft.commit();
            }

            final Intent intent = getIntent();
            if (intent.hasExtra(PREFILLED_PHONENUMBER)) {
                final String prefilledPhoneNumber = intent.getStringExtra(PREFILLED_PHONENUMBER);
                if (!StringUtil.isNullOrEmpty(prefilledPhoneNumber)) {
                    mPhoneFragment.setPrefilledPhonenumber(prefilledPhoneNumber);
                    mPrefilled = true;
                }
            } else if (!StringUtil.isNullOrEmpty(getSimsMeApplication().getAccountController().getAccount().getPhoneNumber())
                    && !getSimsMeApplication().getAccountController().isDeviceManaged()
                    && RuntimeConfig.isBAMandant()) {
                final Button deletePhoneNumberButton = findViewById(R.id.remove_phone_number_button);
                deletePhoneNumberButton.setVisibility(View.VISIBLE);
            }
        } catch (final LocalizedException e) {
            LogUtil.w(this.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * @param view
     */
    @Override
    public void handleDeletePhoneNumberClick(final View view) {
        final PhoneOrEmailActionListener phoneOrEmailActionListener = new PhoneOrEmailActionListener() {
            @Override
            public void onSuccess(final String result) {
                dismissIdleDialog();

                //Telefonnummer entfernt --> Passtoken neu generieren
                getSimsMeApplication().getPreferencesController().checkRecoveryCodeToBeSet(true);

                Toast.makeText(getSimsMeApplication(), getResources().getString(R.string.remove_phone_number_success), Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onFail(final String errorMsg, boolean emailIsInUse) {
                dismissIdleDialog();
                DialogBuilderUtil.buildErrorDialog(ChangePhoneActivity.this, errorMsg).show();
            }
        };

        final String title = getResources().getString(R.string.remove_phone_number_title);
        final String text = getResources().getString(R.string.remove_phone_number_text);
        final String yes = getResources().getString(R.string.remove_phone_number_yes);
        final String cancel = getResources().getString(R.string.std_cancel);

        final DialogInterface.OnClickListener positiveClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                showIdleDialog();

                getSimsMeApplication().getAccountController().removeConfirmedPhone(phoneOrEmailActionListener);
            }
        };

        final DialogInterface.OnClickListener negativeClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                // nix zu tun
            }
        };

        DialogBuilderUtil.buildResponseDialog(ChangePhoneActivity.this, text, title, yes, cancel, positiveClickListener, negativeClickListener).show();
    }

    @Override
    protected void handleRegisterPhone(final String phoneNumber, final String countryCode) {
        if (!appConnectivity.isConnected()) {
            Toast.makeText(this, R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG).show();
            return;
        }

        if ((!StringUtil.isNullOrEmpty(phoneNumber) || (!StringUtil.isNullOrEmpty(countryCode) || mPrefilled)
                || (phoneNumber.length() >= 6))) {
            final String normalizedPhoneNumber = mPrefilled ? phoneNumber : PhoneNumberUtil.normalizePhoneNumberNew(this, countryCode, phoneNumber);

            showIdleDialog();
            final PhoneOrEmailActionListener phoneOrEmailActionListener = new PhoneOrEmailActionListener() {

                @Override
                public void onSuccess(final String result) {
                    dismissIdleDialog();
                    final Intent intent = new Intent(ChangePhoneActivity.this, ConfirmPhoneActivity.class);
                    intent.putExtra(IdentConfirmActivity.REGISTRATION_TYPE, IdentConfirmActivity.REGISTRATION_TYPE_PHONE);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }

                @Override
                public void onFail(final String errorMsg, boolean emailIsInUse) {
                    dismissIdleDialog();

                    if (errorMsg.equals("ERR-0077")) {
                        checkRetry();
                    } else {
                        DialogBuilderUtil.buildErrorDialog(ChangePhoneActivity.this, errorMsg).show();
                    }
                }
            };
            // erster Versuch ohne force
            getSimsMeApplication().getAccountController().requestConfirmPhone(normalizedPhoneNumber, mOverWrite, phoneOrEmailActionListener);
        } else {
            final AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(this,
                    getString(R.string.registration_subline_phone_empty));
            alert.show();
        }
    }

    @Override
    public void handleNextClick(final View view) {
        handleNext();
    }

    private void handleNext() {
        if (!appConnectivity.isConnected()) {
            Toast.makeText(this, R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (mPhoneFragment == null) {
            return;
        }

        final String countryCode = mPhoneFragment.getCountryCodeText();
        final String phoneNumber = mPhoneFragment.getPhoneText();

        handleRegisterPhone(phoneNumber, countryCode);
    }

    /**
     * nachfragen und wnen ja mit force
     */
    private void checkRetry() {

        final String title = getResources().getString(R.string.backup_restore_confirm_wo_title);
        final String text = getResources().getString(R.string.change_phone_number_error_number_exists);
        final String yes = getResources().getString(R.string.general_yes);
        final String no = getResources().getString(R.string.general_no);

        final DialogInterface.OnClickListener positiveClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                mOverWrite = true;
                handleNext();
            }
        };

        final DialogInterface.OnClickListener negativeClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                // TODO und nu?
            }
        };

        DialogBuilderUtil.buildResponseDialog(ChangePhoneActivity.this, text, title, yes, no, positiveClickListener, negativeClickListener).show();
    }
}
