// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;

public abstract class BaseContactsFragment extends Fragment {
    SimsMeApplication mApplication;
    int mMode;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mMode = savedInstanceState.getInt("mMode");
        }
    }

    public abstract void onResumeFragment();

    public abstract boolean searchQueryTextChanged(@Nullable final String query);

    public abstract String getTitle();

    void setApplication(@NonNull final SimsMeApplication application) {
        mApplication = application;
    }

    void setMode(final int mode) {
        mMode = mode;
    }

    public abstract ContactsFragmentType getContactsFragmentType();

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("mMode", mMode);
    }

    public enum ContactsFragmentType {
        TYPE_PRIVATE,
        TYPE_COMPANY,
        TYPE_DOMAIN
    }
}

