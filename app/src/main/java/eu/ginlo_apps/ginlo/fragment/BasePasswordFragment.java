// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.fragment;

import android.widget.EditText;
import androidx.fragment.app.Fragment;

public abstract class BasePasswordFragment
        extends Fragment {
    boolean isSettingPw;

    public abstract String getPassword();

    public abstract void clearInput();

    public abstract EditText getEditText();

    void setIsSettingPw() {
        isSettingPw = true;
    }

    public abstract void openKeyboard();
}
