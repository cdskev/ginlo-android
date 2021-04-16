// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.activity.register.device;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.base.NewBaseActivity;
import eu.ginlo_apps.ginlo.activity.register.device.DeviceRequestTanActivity;
import eu.ginlo_apps.ginlo.contract.RegisterPhone;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.DeviceController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.BaseFragment;
import eu.ginlo_apps.ginlo.fragment.RegisterEmailAddressFragment;
import eu.ginlo_apps.ginlo.fragment.RegisterPhoneFragment;
import eu.ginlo_apps.ginlo.fragment.RegisterSIMSmeIdFragment;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.PhoneNumberUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;

public class DeviceLoginActivity extends NewBaseActivity implements AdapterView.OnItemSelectedListener,
        BaseFragment.OnFragmentInteractionListener {
    private final static String SAVE_INSTANCE_KEY_FRAGMENT = "SAVED_KEY_FRAGMENT";
    private final static String SAVE_INSTANCE_KEY_DEVICE = "SAVED_KEY_DEVICE";
    private final static String SAVE_INSTANCE_KEY_IDENT = "SAVED_KEY_IDENT";

    private RegisterPhoneFragment mPhoneFragment;
    private RegisterEmailAddressFragment mEmailFragment;
    private RegisterSIMSmeIdFragment mSIMSmeIdFragment;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        Spinner identSpinner = findViewById(R.id.device_login_spinner);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.account_identifier, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        identSpinner.setAdapter(adapter);
        identSpinner.setOnItemSelectedListener(this);

        if (savedInstanceState != null && savedInstanceState.containsKey(SAVE_INSTANCE_KEY_IDENT)) {
            identSpinner.setSelection(savedInstanceState.getInt(SAVE_INSTANCE_KEY_IDENT, 0));
        }

        EditText deviceNameET = findViewById(R.id.device_login_device_name);
        deviceNameET.setHint(DeviceController.getDefaultDeviceName());

        if (savedInstanceState != null && savedInstanceState.containsKey(SAVE_INSTANCE_KEY_DEVICE)) {
            deviceNameET.setText(savedInstanceState.getCharSequence(SAVE_INSTANCE_KEY_DEVICE));
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(SAVE_INSTANCE_KEY_FRAGMENT)) {
            Fragment fragment = getSupportFragmentManager().getFragment(savedInstanceState, SAVE_INSTANCE_KEY_FRAGMENT);

            if (fragment != null) {
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.add(R.id.device_login_fragment, fragment);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);

                ft.commit();
            }
        }
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_device_login;
    }

    @Override
    protected void onResumeActivity() {

    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Fragment nextFragment;

        switch (position) {
            //Phone - 1
            case 1: {
                if (mPhoneFragment == null) {
                    mPhoneFragment = new RegisterPhoneFragment();
                }
                nextFragment = mPhoneFragment;
                break;
            }
            //SIMSME ID - 2
            case 2: {
                if (mSIMSmeIdFragment == null) {
                    mSIMSmeIdFragment = new RegisterSIMSmeIdFragment();
                }
                nextFragment = mSIMSmeIdFragment;
                break;
            }
            //Email - 0
            case 0:
            default: {
                if (mEmailFragment == null) {
                    mEmailFragment = new RegisterEmailAddressFragment();
                }
                nextFragment = mEmailFragment;
                break;
            }
        }
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.device_login_fragment);

        if (nextFragment.isAdded()) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.show(nextFragment);
            ft.commit();
            return;
        }

        if (currentFragment != null) {
            if (currentFragment != nextFragment) {
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.device_login_fragment, nextFragment);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                ft.commit();
            }
        } else {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.device_login_fragment, nextFragment);
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            ft.commit();
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.device_login_fragment);
        if (currentFragment != null) {
            getSupportFragmentManager().putFragment(outState, SAVE_INSTANCE_KEY_FRAGMENT, currentFragment);
        }

        EditText deviceNameET = findViewById(R.id.device_login_device_name);
        if (deviceNameET != null) {
            CharSequence devName = deviceNameET.getText();
            if (devName != null && deviceNameET.getText().length() > 0) {
                outState.putCharSequence(SAVE_INSTANCE_KEY_DEVICE, devName);
            }
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

    public void handleNextClick(View view) {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.device_login_fragment);
        if (currentFragment != null) {
            if (currentFragment == mPhoneFragment) {
                handlePhoneLogin(mPhoneFragment.getPhoneText(), mPhoneFragment.getCountryCodeText());
            } else if (currentFragment == mEmailFragment) {
                handleEmailLogin(mEmailFragment.getEmailText());
            } else if (currentFragment == mSIMSmeIdFragment) {
                handleSimsmeIdLogin(mSIMSmeIdFragment.getSimsmeIdText());
            }
        }
    }

    @Override
    public void onFragmentInteraction(int action, Bundle arguments) {

        switch (action) {
            case RegisterPhone.ACTION_PHONE_NUMBER_EDIT_TEXT_DONE: {
                String phone = arguments.getString(AppConstants.ACTION_ARGS_VALUE);
                String countryCode = arguments.getString(AppConstants.ACTION_ARGS_VALUE_2);

                handlePhoneLogin(phone, countryCode);
                break;
            }
            case RegisterEmailAddressFragment.ACTION_EMAIL_EDIT_TEXT_DONE: {
                String mail = arguments.getString(AppConstants.ACTION_ARGS_VALUE);
                handleEmailLogin(mail);
                break;
            }
            case RegisterSIMSmeIdFragment.ACTION_SIMSME_ID_EDIT_TEXT_DONE: {
                String simsmeId = arguments.getString(AppConstants.ACTION_ARGS_VALUE);
                handleSimsmeIdLogin(simsmeId);
                break;
            }
            default: {
                //do nothing
            }
        }
    }

    private void handlePhoneLogin(String phone, String countryCode) {
        if (StringUtil.isNullOrEmpty(phone) || phone.length() < 6) {
            AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(this,
                    getString(R.string.registration_subline_phone_empty));

            alert.show();
        } else {
            final String normalizedPhonenumber = PhoneNumberUtil.normalizePhoneNumberNew(this, countryCode, phone);

            startSearchAccount(normalizedPhonenumber, JsonConstants.SEARCH_TYPE_PHONE);
        }
    }

    private void handleEmailLogin(String mail) {
        if (StringUtil.isNullOrEmpty(mail) || !StringUtil.isEmailValid(mail)) {
            DialogBuilderUtil.buildErrorDialog(this, getResources().getString(R.string.register_email_address_alert_email_empty)).show();
        } else {
            startSearchAccount(mail, JsonConstants.SEARCH_TYPE_EMAIL);
        }
    }

    private void handleSimsmeIdLogin(String simsmeId) {
        if (StringUtil.isNullOrEmpty(simsmeId) || simsmeId.length() != 8) {
            DialogBuilderUtil.buildErrorDialog(this, getResources().getString(R.string.device_login_simsme_id_wrong_input)).show();
        } else {
            startSearchAccount(simsmeId, JsonConstants.SEARCH_TYPE_SIMSME_ID);
        }
    }

    private void startSearchAccount(final String searchText, final String searchType) {
        //check device name
        String deviceName = null;
        EditText deviceNameET = findViewById(R.id.device_login_device_name);
        if (deviceNameET != null) {
            CharSequence cs = deviceNameET.getText();
            if (cs != null && !StringUtil.isNullOrEmpty(cs.toString())) {
                deviceName = cs.toString();
            }
        }

        if (StringUtil.isNullOrEmpty(deviceName)) {
            deviceName = DeviceController.getDefaultDeviceName();
        }

        try {
            showIdleDialog();
            AccountController accountController = getSimsMeApplication().getAccountController();
            accountController.coupleDeviceSetDeviceName(deviceName);
            accountController.coupleDeviceSearchAccount(searchText, searchType, new GenericActionListener<String>() {
                @Override
                public void onSuccess(String object) {
                    dismissIdleDialog();
                    Intent intent = new Intent(DeviceLoginActivity.this, DeviceRequestTanActivity.class);
                    startActivity(intent);
                }

                @Override
                public void onFail(String message, String errorIdent) {
                    dismissIdleDialog();
                    DialogBuilderUtil.buildErrorDialog(DeviceLoginActivity.this, getResources().getString(R.string.error_couple_device)).show();
                }
            });
        } catch (LocalizedException e) {
            DialogBuilderUtil.buildErrorDialog(this, getResources().getString(R.string.error_couple_device) + e.getIdentifier()).show();
        }
    }
}
