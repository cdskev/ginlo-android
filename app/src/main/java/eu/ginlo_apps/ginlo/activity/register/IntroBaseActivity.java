// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.activity.register;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.activity.register.device.WelcomeBackActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.IManagedConfigUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import java.util.ArrayList;
import java.util.List;

import static eu.ginlo_apps.ginlo.util.RuntimeConfig.getBaseUrl;
import static eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty;

public abstract class IntroBaseActivity
        extends BaseActivity {
    private boolean mNextWasClicked;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        PreferencesController preferencesController = ((SimsMeApplication) getApplication()).getPreferencesController();

        ((SimsMeApplication) getApplication()).getKeyController().clearKeys();
        ((SimsMeApplication) getApplication()).getKeyController().purgeKeys();

        preferencesController.getSharedPreferences().edit().clear().apply();
        preferencesController.clearAll();

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
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
    }

    public void handleNextClick(View view) {
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
        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return ViewExtensionsKt.themedInflate(inflater, getContext(), R.layout.fragment_intro_second, container, false);
        }
    }
}
