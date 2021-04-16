// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.fragment.BaseFragment;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;

public class RegisterEmailAddressFragment extends BaseFragment {

    public final static int ACTION_EMAIL_EDIT_TEXT_DONE = 444;

    private final static String SAVE_INSTANCE_KEY = "SIMSME_EMAIL_KEY";

    private EditText mEmailEditText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View root = ViewExtensionsKt.themedInflate(inflater, getContext(), R.layout.fragment_login_email, container, false);

        mEmailEditText = root.findViewById(R.id.registration_email);

        if (savedInstanceState != null) {
            CharSequence idCS = savedInstanceState.getCharSequence(SAVE_INSTANCE_KEY);
            if (idCS != null) {
                mEmailEditText.setText(idCS);
            }
        }

        mEmailEditText.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v,
                                          int actionId,
                                          KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                    Bundle args = new Bundle();
                    args.putString(AppConstants.ACTION_ARGS_VALUE, v.getText().toString());
                    handleAction(ACTION_EMAIL_EDIT_TEXT_DONE, args);
                }
                return true;
            }
        });

        return root;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mEmailEditText != null) {
            outState.putCharSequence(SAVE_INSTANCE_KEY, mEmailEditText.getText());
        }
    }

    public String getEmailText() {
        if (mEmailEditText == null) {
            return null;
        }

        CharSequence email = mEmailEditText.getText();
        return email != null ? email.toString() : null;
    }
}
