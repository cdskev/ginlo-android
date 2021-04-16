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

public class RegisterSIMSmeIdFragment extends BaseFragment {
    public final static int ACTION_SIMSME_ID_EDIT_TEXT_DONE = 333;

    private final static String SAVE_INSTANCE_KEY = "SIMSME_ID_KEY";

    private EditText mSimsIdEditText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View root = ViewExtensionsKt.themedInflate(inflater, getContext(), R.layout.fragment_login_id, container, false);

        mSimsIdEditText = root.findViewById(R.id.registration_simsme_id);

        if (savedInstanceState != null) {
            CharSequence idCS = savedInstanceState.getCharSequence(SAVE_INSTANCE_KEY);
            if (idCS != null) {
                mSimsIdEditText.setText(idCS);
            }
        }

        mSimsIdEditText.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v,
                                          int actionId,
                                          KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                    Bundle args = new Bundle();
                    args.putString(AppConstants.ACTION_ARGS_VALUE, v.getText().toString());
                    handleAction(ACTION_SIMSME_ID_EDIT_TEXT_DONE, args);
                }
                return true;
            }
        });

        return root;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSimsIdEditText != null) {
            outState.putCharSequence(SAVE_INSTANCE_KEY, mSimsIdEditText.getText());
        }
    }

    public String getSimsmeIdText() {
        if (mSimsIdEditText == null) {
            return null;
        }

        CharSequence simsmeId = mSimsIdEditText.getText();
        return simsmeId != null ? simsmeId.toString() : null;
    }
}
