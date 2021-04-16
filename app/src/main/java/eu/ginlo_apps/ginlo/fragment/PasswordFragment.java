// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.fragment.BasePasswordFragment;
import eu.ginlo_apps.ginlo.fragment.ComplexPasswordFragment;
import eu.ginlo_apps.ginlo.fragment.SimplePasswordFragment;

public class PasswordFragment
        extends Fragment
        implements OnClickListener {
    public static final String SET_MODE = "SetPasswordFragment.setMode";

    public static final String CONFIRM_MODE = "SetPasswordFragment.confirmMode";

    private static final String BUNDLE_KEY_DEFAULT_POSITION = "SetPasswordFragment.BundleKeyDefaultPosition";

    private static final String BUNDLE_KEY_MODE = "SetPasswordFragment.BundleKeyMode";

    private static final int DEFAULT_POSITION = 1;  // 1 = complex, 0 = simple

    private String mode = null;

    private int currentFragmentPosition;

    private boolean mSimplePasswordAllowed = true;

    private androidx.appcompat.widget.SwitchCompat simplePasswordSwitch;

    private OnClickListener mOnClickListener;

    private boolean reactToListener = false;

    private BasePasswordFragment[] fragments;

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        final Activity activity = getActivity();
        if (activity instanceof BaseActivity) {
            inflater = ViewExtensionsKt.themedInflater(LayoutInflater.from(activity), activity);
        }

        fragments = new BasePasswordFragment[]
                {
                        new SimplePasswordFragment(),
                        new ComplexPasswordFragment()
                };

        LinearLayout linearLayout = null;

        reactToListener = true;
        int defaultPosition = DEFAULT_POSITION;

        if (savedInstanceState != null) {
            mode = savedInstanceState.getString(BUNDLE_KEY_MODE);
            defaultPosition = savedInstanceState.getInt(BUNDLE_KEY_DEFAULT_POSITION, DEFAULT_POSITION);
        }

        if (mode == null) {
            mode = SET_MODE;
        }

        if (mode.equals(SET_MODE)) {
            linearLayout = (LinearLayout) inflater.inflate(R.layout.set_password_layout, container, false);

            /* tell complex child, to show password strength */
            fragments[1].setIsSettingPw();

            simplePasswordSwitch = linearLayout.findViewById(R.id.set_password_switch_simple_password);

            final boolean isComplexPassword = (defaultPosition == 1);

            simplePasswordSwitch.setChecked(!isComplexPassword);
            simplePasswordSwitch.setOnClickListener(this);

            if (!mSimplePasswordAllowed) {
                defaultPosition = 1;
                simplePasswordSwitch.setChecked(false);
                simplePasswordSwitch.setVisibility(View.GONE);
            }
            if (((SimsMeApplication) getActivity().getApplication()).getPreferencesController().getHasSystemGeneratedPasword()) {
                TextView tv = linearLayout.findViewById(R.id.set_password_request_label);
                tv.setText(R.string.settings_password_setInitialPassword_hint);

                Button btnSkip = linearLayout.findViewById(R.id.skip_set_password);
                btnSkip.setVisibility(View.VISIBLE);

                Button btnNext = linearLayout.findViewById(R.id.next_button);
                btnNext.setText(R.string.settings_password_setInitialPassword_next);
            }
        } else if (mode.equals(CONFIRM_MODE)) {
            linearLayout = (LinearLayout) inflater.inflate(R.layout.confirm_password_layout, container, false);
        }

        setFragmentPosition(defaultPosition);
        updateFragment();
        return linearLayout;
    }

    @Override
    public void onPause() {
        super.onPause();
        reactToListener = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        reactToListener = true;
    }

    @Override
    public void onClick(final View v) {
        if (mOnClickListener != null) {
            clearInput();
            mOnClickListener.onClick(v);
        }
    }

    public void clearInput() {
        fragments[currentFragmentPosition].clearInput();
    }

    public BasePasswordFragment getPasswordFragment() {
        if (fragments[currentFragmentPosition] != null) {
            return fragments[currentFragmentPosition];
        } else {
            return null;
        }
    }

    public void setMode(final String mode) {
        this.mode = mode;
    }

    public void setFragmentPosition(final int position) {
        currentFragmentPosition = position;
    }

    public void updateFragment() {
        if (reactToListener) {
            int id = R.id.set_password_relative_layout_fragment_container;

            id = mode.equals(CONFIRM_MODE) ? R.id.confirm_password_type_fragment_container : id;

            getChildFragmentManager().beginTransaction().replace(id, fragments[currentFragmentPosition]).commit();
        }
    }

    @Override
    public void onAttach(final android.content.Context activity) {
        super.onAttach(activity);
    }

    @Override
    public void onSaveInstanceState(final Bundle bundle) {
        bundle.putString(BUNDLE_KEY_MODE, mode);
        bundle.putInt(BUNDLE_KEY_DEFAULT_POSITION, currentFragmentPosition);
        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    public void setOnClickListener(final OnClickListener onClickListener) {
        mOnClickListener = onClickListener;
    }

    public void setSimplePasswordAllowed(boolean bSimplePasswordAllowed) {
        mSimplePasswordAllowed = bSimplePasswordAllowed;
        if (!bSimplePasswordAllowed && simplePasswordSwitch != null) {
            simplePasswordSwitch.setChecked(false);
            simplePasswordSwitch.setVisibility(View.GONE);
        }
    }

    public void openKeyboard() {
        fragments[currentFragmentPosition].openKeyboard();
    }
}
