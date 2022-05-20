// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.LoginActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.ArrayList;

public class ComplexPasswordFragment
        extends BasePasswordFragment {

    private EditText mComplexEditText;

    private boolean mFirstShown = true;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        final Activity activity = getActivity();
        if (activity instanceof BaseActivity) {
            inflater = ViewExtensionsKt.themedInflater(inflater, activity);
        }

        LinearLayout linearLayout = (LinearLayout) inflater.inflate(R.layout.complex_password_layout, container, false);

        mComplexEditText = linearLayout.findViewById(R.id.complex_password_edit_text_field);

        if (activity != null) {
            final Button nextButton = activity.findViewById(R.id.next_button);

            if (RuntimeConfig.isBAMandant() && nextButton != null) {
                nextButton.getBackground().setColorFilter(ScreenDesignUtil.getInstance().getAppAccentColor(activity.getApplication()), PorterDuff.Mode.SRC_ATOP);
                nextButton.setTextColor(ScreenDesignUtil.getInstance().getAppAccentContrastColor(activity.getApplication()));
            }

            mComplexEditText.setOnEditorActionListener(new EditText.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v,
                                              int actionId,
                                              KeyEvent event) {
                    if ((actionId == EditorInfo.IME_ACTION_DONE)
                            || (event != null &&
                            event.getAction() == KeyEvent.ACTION_DOWN &&
                            event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                        if (activity instanceof LoginActivity) {
                            LoginActivity loginActivity = (LoginActivity) activity;

                            loginActivity.handleLogin();
                            return true;
                        }
                        if (nextButton != null) {
                            nextButton.performClick();
                        }

                        return false;
                    }
                    return false;
                }
            });
        }

        final TextView passwordStrength = linearLayout.findViewById(R.id.password_strength);

        if (isSettingPw) {
            final ArrayList<Integer> passwordStrengthBGs = new ArrayList<>();
            passwordStrengthBGs.add(R.drawable.password_strength_grey);
            passwordStrengthBGs.add(R.drawable.password_strength_red_dark);
            passwordStrengthBGs.add(R.drawable.password_strength_red);
            passwordStrengthBGs.add(R.drawable.password_strength_orange_dark);
            passwordStrengthBGs.add(R.drawable.password_strength_orange);
            passwordStrengthBGs.add(R.drawable.password_strength_yellow);
            passwordStrengthBGs.add(R.drawable.password_strength_yellow_green);
            passwordStrengthBGs.add(R.drawable.password_strength_dark_green);
            passwordStrengthBGs.add(R.drawable.password_strength_green);

            mComplexEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void onTextChanged(CharSequence s,
                                          int start,
                                          int before,
                                          int count) {
                }

                @Override
                public void beforeTextChanged(CharSequence s,
                                              int start,
                                              int count,
                                              int after) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() > 0) {
                        int security_level = StringUtil.getSecurityLevel(s);

                        passwordStrength.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                                passwordStrengthBGs.get(security_level),
                                0);
                    } else {
                        passwordStrength.setCompoundDrawablesWithIntrinsicBounds(0, 0, passwordStrengthBGs.get(0), 0);
                    }
                    passwordStrength.requestLayout();
                }
            });
        } else {
            passwordStrength.setVisibility(View.GONE);
        }
        //
        openKeyboard();
        return linearLayout;
    }

    @Override
    public String getPassword() {
        return mComplexEditText.getText().toString();
    }

    @Override
    public void clearInput() {
        mComplexEditText.setText("");
    }

    @Override
    public EditText getEditText() {
        return mComplexEditText;
    }

    @Override
    public void openKeyboard() {
        if (mFirstShown) {
            mFirstShown = false;
            return;
        }

        if ((mComplexEditText != null) && (getActivity() != null)) {
            mComplexEditText.requestFocus();
            KeyboardUtil.toggleSoftInputKeyboard(getActivity(), mComplexEditText, true);
        }
    }
}
