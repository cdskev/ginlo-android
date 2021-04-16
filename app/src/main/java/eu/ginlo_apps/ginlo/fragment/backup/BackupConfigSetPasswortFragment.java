// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment.backup;

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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.fragment.BaseFragment;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;

public class BackupConfigSetPasswortFragment extends BaseFragment {
    public static final int TYPE_SET_PWD = 1;

    public static final int TYPE_SET_PWD_CONFIRM = 2;

    private final static String FRAGMENT_TYPE = "fragmentType";

    private EditText mPasswordView;

    private TextView mPasswordStrength;

    private ArrayList<Integer> mPasswordStrengthBGs;

    private int mType;

    public BackupConfigSetPasswortFragment() {

    }

    public static BackupConfigSetPasswortFragment newInstance(int type) {
        BackupConfigSetPasswortFragment fragment = new BackupConfigSetPasswortFragment();

        Bundle args = new Bundle();
        args.putInt(FRAGMENT_TYPE, type);

        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mType = getArguments().getInt(FRAGMENT_TYPE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if ((mPasswordView != null) && (getActivity() != null)) {
            KeyboardUtil.toggleSoftInputKeyboard(getActivity(), mPasswordView, true);
        }
    }

    @Override
    public void onPause() {
        if ((mPasswordView != null) && (getActivity() != null)) {
            KeyboardUtil.toggleSoftInputKeyboard(getActivity(), mPasswordView, false);
        }
        super.onPause();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        final View view = ViewExtensionsKt.themedInflate(inflater, this.requireActivity(), R.layout.fragment_backup_set_password, container, false);

        final FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.setTitle(getString(R.string.settings_backup_config_set_password_toolbar_title));
        }

        mPasswordView = view.findViewById(R.id.backup_set_password_edit_text);
        TextView titleTV = view.findViewById(R.id.settings_backup_config_set_password_title_text_view);
        TextView descTV = view.findViewById(R.id.settings_backup_config_set_password_desc_text_view);
        mPasswordStrength = view.findViewById(R.id.backup_set_password_strength);

        if (mPasswordView != null) {
            mPasswordView.setOnEditorActionListener(new EditText.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if ((actionId == EditorInfo.IME_ACTION_DONE)
                            || (event != null &&
                            event.getAction() == KeyEvent.ACTION_DOWN &&
                            event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                        onFragmentViewClick(view.findViewById(R.id.backup_set_password_btn));

                        if (activity != null) {
                            KeyboardUtil.toggleSoftInputKeyboard(activity, mPasswordView, false);
                        }

                        return true;
                    }
                    return false;
                }
            });
        }

        if (mType == TYPE_SET_PWD) {
            if (titleTV != null) {
                titleTV.setText(getString(R.string.settings_backup_config_set_password_title));
            }

            if (descTV != null) {
                descTV.setText(getString(R.string.settings_backup_config_set_password_desc));
            }

            if (mPasswordStrength != null && mPasswordView != null) {
                mPasswordStrengthBGs = new ArrayList<>();
                mPasswordStrengthBGs.add(R.drawable.password_strength_grey);
                mPasswordStrengthBGs.add(R.drawable.password_strength_red_dark);
                mPasswordStrengthBGs.add(R.drawable.password_strength_red);
                mPasswordStrengthBGs.add(R.drawable.password_strength_orange_dark);
                mPasswordStrengthBGs.add(R.drawable.password_strength_orange);
                mPasswordStrengthBGs.add(R.drawable.password_strength_yellow);
                mPasswordStrengthBGs.add(R.drawable.password_strength_yellow_green);
                mPasswordStrengthBGs.add(R.drawable.password_strength_dark_green);
                mPasswordStrengthBGs.add(R.drawable.password_strength_green);

                mPasswordView.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (s.length() > 0) {
                            int security_level = StringUtil.getSecurityLevel(s);

                            mPasswordStrength.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                                    mPasswordStrengthBGs
                                            .get(security_level),
                                    0);
                        } else {
                            mPasswordStrength.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                                    mPasswordStrengthBGs
                                            .get(0), 0);
                        }
                        mPasswordStrength.requestLayout();
                    }
                });
            }
        } else if (mType == TYPE_SET_PWD_CONFIRM) {
            if (titleTV != null) {
                titleTV.setText(getString(R.string.settings_backup_config_confirm_password_title));
            }

            if (descTV != null) {
                descTV.setVisibility(View.INVISIBLE);
            }

            if (mPasswordStrength != null) {
                mPasswordStrength.setVisibility(View.INVISIBLE);
            }
        }
        if (RuntimeConfig.isBAMandant() && activity != null) {
            final ColorUtil colorUtil = ColorUtil.getInstance();
            final int appAccentColor = colorUtil.getAppAccentColor(activity.getApplication());
            final Button button = view.findViewById(R.id.backup_set_password_btn);
            button.getBackground().setColorFilter(appAccentColor, PorterDuff.Mode.SRC_ATOP);
            mPasswordView.getBackground().setColorFilter(appAccentColor, PorterDuff.Mode.SRC_ATOP);
        }
        return view;
    }

    @Override
    public void onFragmentViewClick(final View view) {
        super.onFragmentViewClick(view);

        if (view != null) {
            if (view.getId() == R.id.backup_set_password_btn) {
                final String password = mPasswordView.getText().toString();
                if (!StringUtil.isNullOrEmpty(password)) {
                    final Bundle args = new Bundle();
                    args.putString(AppConstants.ACTION_ARGS_VALUE, password);
                    final int actionType = mType == TYPE_SET_PWD ? AppConstants.BACKUP_ACTION_SET_PASSWORD : AppConstants.BACKUP_ACTION_CONFIRM_PASSWORD;
                    handleAction(actionType, args);
                } else {
                    final BaseActivity activity = (BaseActivity) getActivity();
                    if (activity != null) {
                        final AlertDialogWrapper alert = DialogBuilderUtil.buildErrorDialog(activity,
                                getString(R.string.registration_validation_passwordNotSet));
                        alert.show();
                    }
                }
            }
        }
    }
}
