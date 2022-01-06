// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment.backup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.fragment.BaseFragment;
import eu.ginlo_apps.ginlo.fragment.backup.BackupRestoreSelectItemListFragment;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import java.util.ArrayList;
import java.util.List;

public class BackupRestoreSelectBackupFragment extends BaseFragment {

    private Fragment mCurrentChildFragment;

    public BackupRestoreSelectBackupFragment() {
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = ViewExtensionsKt.themedInflate(inflater, this.getContext(), R.layout.fragment_backup_restore_select, container, false);

        if (view.findViewById(R.id.backup_restore_select_child_fragment_container) != null) {
            BackupRestoreSelectItemListFragment childFragment = new BackupRestoreSelectItemListFragment();
            childFragment.setArguments(getArguments());

            FragmentTransaction transaction = getChildFragmentManager().beginTransaction();

            transaction.add(R.id.backup_restore_select_child_fragment_container, childFragment);

            transaction.commit();

            mCurrentChildFragment = childFragment;
        }
        return view;
    }

    @Override
    public List<Fragment> getChildFragments() {
        if (mCurrentChildFragment == null) {
            return null;
        }
        ArrayList<Fragment> al = new ArrayList<>(1);
        al.add(mCurrentChildFragment);
        return al;
    }

    @Override
    public void onFragmentViewClick(View view) {
        super.onFragmentViewClick(view);

        if (view.getId() == R.id.backup_restore_select_btn) {
            handleAction(AppConstants.BACKUP_RESTORE_ACTION_IMPORT_BACKUP, null);
        }
    }
}
