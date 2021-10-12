// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.activity.register;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.zxing.integration.android.IntentIntegrator;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.SearchContactActivity;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.activity.register.device.DeviceRequestTanActivity;
import eu.ginlo_apps.ginlo.activity.register.device.WelcomeBackActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.LoginController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.QRCodeModel;
import eu.ginlo_apps.ginlo.model.ResultContainer;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.GinloNowUtil;
import eu.ginlo_apps.ginlo.util.IManagedConfigUtil;
import eu.ginlo_apps.ginlo.util.PermissionUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static eu.ginlo_apps.ginlo.util.RuntimeConfig.getBaseUrl;
import static eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty;

public abstract class IntroBaseActivity
        extends BaseActivity {
    public static final String TAG = IntroBaseActivity.class.getSimpleName();
    private boolean mNextWasClicked;
    SimsMeApplication simsMeApplication = null;
    PreferencesController preferencesController = null;
    private GinloNowUtil mGinloNowUtil;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        simsMeApplication = (SimsMeApplication) getApplication();
        if (simsMeApplication == null) {
            LogUtil.e(TAG, "No main application!");
            return;
        }
        preferencesController =  simsMeApplication.getPreferencesController();
        simsMeApplication.getKeyController().clearKeys();
        simsMeApplication.getKeyController().purgeKeys();

        mGinloNowUtil = new GinloNowUtil();

        // Since preferences are going to be cleared, we must save a pending invitation
        String possibleGinloInvitation = mGinloNowUtil.getGinloNowInvitationString();
        LogUtil.d(TAG, "onCreate: Saving possible GinloInvitation = " + possibleGinloInvitation);

        preferencesController.getSharedPreferences().edit().clear().apply();
        preferencesController.clearAll();

        mGinloNowUtil.setGinloNowInvitationString(possibleGinloInvitation);
    }

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        // Wenn Automatische Registrierungskeys da sind, dann gleich auf díe zweite Seit wechseln
        final IManagedConfigUtil managedConfigUtil = RuntimeConfig.getClassUtil().getManagedConfigUtil((SimsMeApplication) getApplication());
        if (managedConfigUtil != null && managedConfigUtil.hasAutomaticMdmRegistrationKeys()) {
            handleNextClick(null);
        } else {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.intro_fragment, new IntroFirstFragment());
            ft.commit();
        }
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_intro;
    }

    @Override
    protected void onResumeActivity() {
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        if(!RuntimeConfig.isB2c()) {
            // Hide ginlo now
            final View ginloNowLayout = findViewById(R.id.intro_2_ginlo_now);
            if(ginloNowLayout != null) {
                ginloNowLayout.setVisibility(View.GONE);
            }
        }
        final TextView mTermsAcceptTV = findViewById(R.id.intro_registration_label_accept);
        if(mTermsAcceptTV != null) {
            mTermsAcceptTV.setText(Html.fromHtml(getString(R.string.registration_label_accept)));
            mTermsAcceptTV.setClickable(true);
            mTermsAcceptTV.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
    }

    public void handleNextClick(View view) {
        if(mGinloNowUtil.haveGinloNowInvitation()) {
            // Proceed with ginlo now invitation process.
            Class<?> classForNextIntent = RuntimeConfig.getClassUtil().getActivityAfterIntro((SimsMeApplication) getApplication());
            Intent intentProceed = new Intent(this, classForNextIntent);
            startActivity(intentProceed);
            return;
        }

        if (BuildConfig.MULTI_DEVICE_SUPPORT) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.intro_fragment, new IntroSecondFragment());
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            ft.commit();
            mNextWasClicked = true;
        } else {
            handleRegisterClick(null);
        }
    }

    public void handleGinloNowClick(View view) {
        requestPermission(PermissionUtil.PERMISSION_FOR_CAMERA,
                R.string.permission_rationale_camera,
                new PermissionUtil.PermissionResultCallback() {
                @Override
                public void permissionResult(int permission,
                                             boolean permissionGranted) {
                    if ((permission == PermissionUtil.PERMISSION_FOR_CAMERA) && permissionGranted) {
                        IntentIntegrator intentIntegrator = new IntentIntegrator(IntroBaseActivity.this);
                        Intent intent = intentIntegrator.createScanIntent();
                        startActivityForResult(intent, SearchContactActivity.SCAN_CONTACT_RESULT_CODE);
                    }
                }
            });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == SearchContactActivity.SCAN_CONTACT_RESULT_CODE) {
            if (resultCode == RESULT_OK) {
                final String qrCodeString = intent.getStringExtra("SCAN_RESULT");
                QRCodeModel qrm = QRCodeModel.parseQRString(qrCodeString);

                if(qrm.getVersion().equals(QRCodeModel.TYPE_V3)) {
                    // Git invitation QR code - keep it!
                    preferencesController.getSharedPreferences().edit().putString(GinloNowUtil.GINLO_NOW_INVITATION, qrm.getPayload()).apply();

                    Class<?> classForNextIntent = RuntimeConfig.getClassUtil().getActivityAfterIntro((SimsMeApplication) getApplication());
                    Intent intentProceed = new Intent(this, classForNextIntent);
                    startActivity(intentProceed);
                } else {
                    LogUtil.w(TAG, "onActivityResult: No valid QR code: " + qrm.getPayload());
                    Toast.makeText(this,
                            getString(R.string.device_request_tan_error_coupling_qrcode_failed),
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }


    public void handleRegisterClick(View view) {
        Class<?> classForNextIntent = RuntimeConfig.getClassUtil().getActivityAfterIntro((SimsMeApplication) getApplication());
        Intent intent = new Intent(this, classForNextIntent);
        startActivity(intent);
    }

    public void handleLoginClick(View view) {
        Intent intent = new Intent(this, WelcomeBackActivity.class);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (mNextWasClicked) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.intro_fragment, new IntroFirstFragment());
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            ft.commit();
            mNextWasClicked = false;
        } else {
            super.onBackPressed();
        }
    }

    public static class IntroFirstFragment extends Fragment implements View.OnClickListener {
        private int clickCount = 0;
        public String mManualServerBase;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

            View view = ViewExtensionsKt.themedInflate(inflater, getContext(), R.layout.fragment_intro_first, container, false);
            View logo = view.findViewById(R.id.logo);
            logo.setOnClickListener(this);

            return view;
        }

        @Override
        public void onResume() {
            super.onResume();
        }

        @Override
        public void onClick(View v) {
            if (v.getId() != R.id.logo || ++clickCount != 10)
                return;

            RuntimeConfig.enableTestCountryCode();
            toggleEnvironmentPicker();
        }

        private void toggleEnvironmentPicker() {
            View rootView = getView();
            if (rootView == null)
                return;

            List<String> choices = new ArrayList<>();
            choices.add("Production");
            choices.add("Testing");
            choices.add("Manual");

            AppCompatSpinner spinner = rootView.findViewById(R.id.environment_picker);
            AppCompatEditText serverUrl = rootView.findViewById(R.id.server_url_input);
            serverUrl.setText(RuntimeConfig.getBaseUrl());
            spinner.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.support_simple_spinner_dropdown_item, choices));

            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    final String selectedItem =  (String) parent.getItemAtPosition(position);

                    // Allow manual editing of server base
                    if(selectedItem.equals("Manual")) {
                        serverUrl.setVisibility(View.VISIBLE);
                        serverUrl.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {
                            }

                            @Override
                            public void afterTextChanged(Editable serverInput) {
                                if(serverInput != null && serverInput.length() > 8) {
                                        String s = serverInput.toString();
                                        if(s.startsWith("https://")) {
                                            RuntimeConfig.setServerBase(s);
                                    }
                                }
                            }
                        });
                        setEnvironment("Testing");
                        return;
                    }
                    setEnvironment(selectedItem);
                    serverUrl.setVisibility(View.GONE);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    setEnvironment("Testing");
                }

                private void setEnvironment(String environment) {
                    String msg = RuntimeConfig.setEnvironment(environment)
                            ? String.format("Environment set to %s", environment)
                            : "That didn't work as expected. Good luck on your next try.";
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                }
            });

            spinner.setVisibility(View.VISIBLE);
            rootView.findViewById(R.id.marketing_information).setVisibility(View.GONE);
        }
    }

    public static class IntroSecondFragment extends Fragment {

        @Override
        public void onResume() {
            super.onResume();
            Activity introActivity = this.getActivity();
            if(introActivity != null) {
                introActivity.onContentChanged();
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return ViewExtensionsKt.themedInflate(inflater, getContext(), R.layout.fragment_intro_second, container, false);
        }
    }
}
