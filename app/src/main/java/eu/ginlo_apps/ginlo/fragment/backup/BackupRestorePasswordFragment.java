// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment.backup;

import android.app.Application;
import android.graphics.PorterDuff;
import android.os.Bundle;
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
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.fragment.BaseFragment;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;

public class BackupRestorePasswordFragment extends BaseFragment {
    private EditText mPasswordView;

    public BackupRestorePasswordFragment() {

    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        final FragmentActivity activity = getActivity();

        final View view = ViewExtensionsKt.themedInflate(inflater, this.getContext(), R.layout.fragment_backup_restore_password, container, false);

        mPasswordView = view.findViewById(R.id.backup_restore_password_edit_text);
        mPasswordView.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v,
                                          int actionId,
                                          KeyEvent event) {
                if ((actionId == EditorInfo.IME_ACTION_DONE)
                        || (event != null &&
                        event.getAction() == KeyEvent.ACTION_DOWN &&
                        event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {

                    Button button = view.findViewById(R.id.backup_restore_password_btn);
                    onFragmentViewClick(button);

                    return false;
                }
                return false;
            }
        });
        if (RuntimeConfig.isBAMandant() && activity != null) {
            final ColorUtil colorUtil = ColorUtil.getInstance();
            final int appAccentColor = colorUtil.getAppAccentColor((Application) activity.getApplicationContext());
            final Button button = view.findViewById(R.id.backup_restore_password_btn);
            button.getBackground().setColorFilter(appAccentColor, PorterDuff.Mode.SRC_ATOP);
            mPasswordView.getBackground().setColorFilter(appAccentColor, PorterDuff.Mode.SRC_ATOP);
        }
        return view;
    }

    @Override
    public void onFragmentViewClick(View view) {
        super.onFragmentViewClick(view);

        if (view.getId() == R.id.backup_restore_password_btn) {
            String password = mPasswordView.getText().toString();
            if (!StringUtil.isNullOrEmpty(password)) {
                Bundle args = new Bundle();
                args.putString(AppConstants.ACTION_ARGS_VALUE, password);
                handleAction(AppConstants.BACKUP_RESTORE_ACTION_BACKUP_SET_PASSWORD, args);
            }
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
}
