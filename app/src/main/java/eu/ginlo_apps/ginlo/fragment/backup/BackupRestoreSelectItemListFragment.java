// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment.backup;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.fragment.BaseFragment;
import eu.ginlo_apps.ginlo.fragment.backup.BackupItemRecyclerViewAdapter;
import eu.ginlo_apps.ginlo.fragment.backup.BackupRestoreBaseFragment;

import java.util.ArrayList;

public class BackupRestoreSelectItemListFragment extends BaseFragment {
    public BackupRestoreSelectItemListFragment() {

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = ViewExtensionsKt.themedInflate(inflater, this.getActivity(), R.layout.fragment_backup_item_list, container, false);

        if (view instanceof RecyclerView && getArguments() != null) {
            Context context = view.getContext();
            RecyclerView recyclerView = (RecyclerView) view;

            recyclerView.setLayoutManager(new LinearLayoutManager(context));

            ArrayList<Bundle> backups = getArguments().getParcelableArrayList(BackupRestoreBaseFragment.TYPE_ARGS_VALUE);
            if (backups != null) {
                recyclerView.setAdapter(new BackupItemRecyclerViewAdapter(backups, mListener, (SimsMeApplication) context.getApplicationContext(), getContext()));
            }
        }

        return view;
    }
}
