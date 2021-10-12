// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.register;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import java.util.Date;

import javax.inject.Inject;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity;
import eu.ginlo_apps.ginlo.activity.register.IdentConfirmActivity;
import eu.ginlo_apps.ginlo.activity.reregister.ChangePhoneActivity;
import eu.ginlo_apps.ginlo.activity.reregister.ConfirmPhoneActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.contract.RegisterPhone;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.contracts.OnCreateAccountListener;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.data.network.AppConnectivity;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.BaseFragment;
import eu.ginlo_apps.ginlo.fragment.RegisterEmailAddressFragment;
import eu.ginlo_apps.ginlo.fragment.RegisterPhoneFragment;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.GinloNowUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.PhoneNumberUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;

public class IdentRequestActivity
        extends NewBaseActivity implements AdapterView.OnItemSelectedListener,
        BaseFragment.OnFragmentInteractionListener {

    public static final String TAG = IdentRequestActivity.class.getSimpleName();

    private static final int TYPE_SPINNER_EMAIL = 0;
    private static final int TYPE_SPINNER_PHONE = 1;
    private final static String SAVE_INSTANCE_KEY_FRAGMENT = "SAVED_KEY_FRAGMENT";
    private final static String SAVE_INSTANCE_KEY_IDENT = "SAVED_KEY_IDENT";
    @Inject
    public AppConnectivity appConnectivity;
    protected RegisterPhoneFragment mPhoneFragment;
    private boolean nextClicked = false;
    private RegisterEmailAddressFragment mEmailFragment;
    private int mRegistrationType = -1;
    private AccountController mAccountController;
    private GinloNowUtil mGinloNowUtil;
    final private OnCreateAccountListener listener = new OnCreateAccountListener() {
        @Override
        public void onCreateAccountSuccess() {
            if (mRegistrationType == IdentConfirmActivity.REGISTRATION_TYPE_MAIL) {
                //prüfen ob Freemailer oder Business Adresse
                validateMail();
            } else {
                dismissIdleDialog();
                startNextActivity();
            }
        }

        @Override
        public void onCreateAccountFail(final String errorMsg, final boolean haveToResetRegistration) {
            final Handler handler = new Handler(getMainLooper());
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    dismissIdleDialog();
                    String msg = errorMsg;
                    if (StringUtil.isNullOrEmpty(msg)) {
                        msg = getString(R.string.create_account_failed);
                    }
                    nextClicked = false;

                    final DialogBuilderUtil.OnCloseListener listener = new DialogBuilderUtil.OnCloseListener() {
                        @Override
                        public void onClose(final int ref) {
                            if (haveToResetRegistration) {
                                IdentRequestActivity.this.onBackPressed();
                            }
                        }
                    };

                    DialogBuilderUtil.buildErrorDialog(IdentRequestActivity.this, msg, -1, listener).show();
                }
            };
            handler.post(runnable);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mAccountController = getSimsMeApplication().getAccountController();
        if(mAccountController == null) {
            return;
        }

        mGinloNowUtil = new GinloNowUtil();

        super.onCreate(savedInstanceState);

        // A little bit unhandy, but IdentRequestActivity is being re-used by ChangePhoneActivity <sigh!>,
        // so we need to do the instance check.
        if (!(this instanceof ChangePhoneActivity)
                && (!BuildConfig.NEED_PHONENUMBER_VALIDATION) || mGinloNowUtil.haveGinloNowInvitation()) {
            // Skip all confirmation steps - create account and continue with next activity.
            doExpressRegistration();
        }
    }

    private void doExpressRegistration() {
        View myView = findViewById(R.id.ident_request_main_layout);
        myView.setVisibility(View.GONE);
        mRegistrationType = IdentConfirmActivity.REGISTRATION_TYPE_PHONE;
        LogUtil.i(TAG, "doExpressRegistration: Using registration with no phone number validation.");
        mAccountController.createAccountRegisterPhone("", "", listener);
        // listener.onCreateAccountSuccess();
    }

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        Spinner identSpinner = findViewById(R.id.intro_ident_request_spinner);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.account_registration_identifier, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        identSpinner.setAdapter(adapter);
        identSpinner.setOnItemSelectedListener(this);

        if (!RuntimeConfig.isBAMandant()) {
            final View hintView = findViewById(R.id.intro_ident_request_spinner_label);
            hintView.setVisibility(View.GONE);
            identSpinner.setVisibility(View.GONE);
            onItemSelected(null, null, TYPE_SPINNER_PHONE, 0);
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(SAVE_INSTANCE_KEY_IDENT)) {
            identSpinner.setSelection(savedInstanceState.getInt(SAVE_INSTANCE_KEY_IDENT, 0));
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(SAVE_INSTANCE_KEY_FRAGMENT)) {
            Fragment fragment = getSupportFragmentManager().getFragment(savedInstanceState, SAVE_INSTANCE_KEY_FRAGMENT);

            if (fragment != null) {
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.add(R.id.intro_ident_request_fragment, fragment);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);

                ft.commit();
            }
        }
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_ident_request;
    }

    @Override
    protected void onResumeActivity() {
        //
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Fragment nextFragment;

        switch (position) {
            //EMAIL - 1
            case TYPE_SPINNER_PHONE: {
                if (mPhoneFragment == null) {
                    mPhoneFragment = new RegisterPhoneFragment();
                }
                nextFragment = mPhoneFragment;
                break;
            }
            //PHONE - 0
            case TYPE_SPINNER_EMAIL:
            default: {
                if (mEmailFragment == null) {
                    mEmailFragment = new RegisterEmailAddressFragment();
                }
                nextFragment = mEmailFragment;
                break;
            }
        }
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.intro_ident_request_fragment);

        if (nextFragment.isAdded()) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.show(nextFragment);
            ft.commit();
            return;
        }

        if (currentFragment != null) {
            if (currentFragment != nextFragment) {
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.intro_ident_request_fragment, nextFragment);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                ft.commit();
            }
        } else {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.intro_ident_request_fragment, nextFragment);
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            ft.commit();
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.intro_ident_request_fragment);
        if (currentFragment != null) {
            getSupportFragmentManager().putFragment(outState, SAVE_INSTANCE_KEY_FRAGMENT, currentFragment);
        }

        Spinner identSpinner = findViewById(R.id.device_login_spinner);
        if (identSpinner != null) {
            int pos = identSpinner.getSelectedItemPosition();
            outState.putInt(SAVE_INSTANCE_KEY_IDENT, pos);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onFragmentInteraction(int action, Bundle arguments) {

        switch (action) {
            case RegisterPhone.ACTION_PHONE_NUMBER_EDIT_TEXT_DONE: {
                String phone = arguments.getString(AppConstants.ACTION_ARGS_VALUE);
                String countryCode = arguments.getString(AppConstants.ACTION_ARGS_VALUE_2);

                handleRegisterPhone(phone, countryCode);
                break;
            }
            case RegisterEmailAddressFragment.ACTION_EMAIL_EDIT_TEXT_DONE: {
                String mail = arguments.getString(AppConstants.ACTION_ARGS_VALUE);
                handleRegisterEmail(mail);
                break;
            }
            default: {
                //do nothing
            }
        }
    }

    public void handleNextClick(final View view) {

        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.intro_ident_request_fragment);
        if (currentFragment != null) {
            if (currentFragment == mPhoneFragment) {
                handleRegisterPhone(mPhoneFragment.getPhoneText(), mPhoneFragment.getCountryCodeText());
            } else if (currentFragment == mEmailFragment) {
                handleRegisterEmail(mEmailFragment.getEmailText());
            }
        }
    }

    private void handleRegisterEmail(final String emailAdress) {
        if (StringUtil.isNullOrEmpty(emailAdress) || !StringUtil.isEmailValid(emailAdress)) {
            DialogBuilderUtil.buildErrorDialog(this, getResources().getString(R.string.register_email_address_alert_email_empty)).show();
        } else {
            final String noteText = getResources().getString(R.string.create_account_confirm_mail, emailAdress);
            showNoteDialogAndRegister(noteText, eu.ginlo_apps.ginlo.activity.register.IdentConfirmActivity.REGISTRATION_TYPE_MAIL, null, emailAdress);
        }
    }

    protected void handleRegisterPhone(final String phoneNumber, final String countryCode) {
        if (!appConnectivity.isConnected()) {
            Toast.makeText(this, R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG).show();
            return;
        }

        if ((!StringUtil.isNullOrEmpty(phoneNumber) && !StringUtil.isNullOrEmpty(countryCode) && (phoneNumber.length() >= 6)) && !nextClicked) {
            final String normalizedPhonenumber = PhoneNumberUtil.normalizePhoneNumberNew(this, countryCode, phoneNumber);
            final String noteText = getResources().getString(R.string.create_account_confirm, normalizedPhonenumber);
            showNoteDialogAndRegister(noteText, eu.ginlo_apps.ginlo.activity.register.IdentConfirmActivity.REGISTRATION_TYPE_PHONE, normalizedPhonenumber, null);
        } else {
            final AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(this,
                    getString(R.string.registration_subline_phone_empty));
            alert.show();
        }
    }

    private void showNoteDialogAndRegister(final String noteText, final int registrationType, final String phoneNumber, final String mailAdress) {
        final String title = getResources().getString(R.string.create_account_confirm_title);
        final String positiveButton = getResources().getString(R.string.create_account_confirm_continue_btn);
        final String negativeButton = getResources().getString(R.string.create_account_confirm_back_btn);

        final DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog,
                                final int which) {
                dialog.dismiss();
                nextClicked = true;
                showIdleDialog(registrationType == eu.ginlo_apps.ginlo.activity.register.IdentConfirmActivity.REGISTRATION_TYPE_MAIL ? R.string.progress_dialog_ident_request_mail : R.string.progress_dialog_ident_request);
                mRegistrationType = registrationType;
                if (!mGinloNowUtil.haveGinloNowInvitation()) {
                    mAccountController.createAccountRegisterPhone(phoneNumber, mailAdress, listener);
                } else {
                    mAccountController.createAccountRegisterPhone("", "", listener);
                }
            }
        };

        final DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog,
                                final int which) {
                dialog.dismiss();
            }
        };

        final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(this, noteText, true,
                title,
                positiveButton,
                negativeButton,
                positiveOnClickListener,
                negativeOnClickListener);

        dialog.show();
    }

    @Override
    public void onBackPressed() {
        mAccountController.resetCreateAccountSetPassword();
        super.onBackPressed();
    }

    public void handleDeletePhoneNumberClick(final View view) {
        //sollte nicht aufgerufen werden -> falls doch Dummy-Funktion
    }

    private void startNextActivity() {
        final Class activityClass = RuntimeConfig.getClassUtil().getIdentConfirmActivityClass();
        final Intent identConfirmIntent = new Intent(IdentRequestActivity.this, activityClass);
        identConfirmIntent.putExtra(IdentConfirmActivity.REGISTRATION_TYPE, mRegistrationType);
        startActivity(identConfirmIntent);
        nextClicked = false;
    }

    private void validateMail() {
        try {
            mAccountController.validateOwnEmailAsync(new GenericActionListener<Void>() {
                @Override
                public void onSuccess(Void object) {
                    dismissIdleDialog();
                    startNextActivity();
                }

                @Override
                public void onFail(String message, String errorIdent) {
                    //hat nicht geklappt
                    dismissIdleDialog();
                    startNextActivity();
                }
            });
        } catch (LocalizedException e) {
            //hat nicht geklappt, trotzdem naechster Screen
            dismissIdleDialog();
            startNextActivity();
        }
    }
}
