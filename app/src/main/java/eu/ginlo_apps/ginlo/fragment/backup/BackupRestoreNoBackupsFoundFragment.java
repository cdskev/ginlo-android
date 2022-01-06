// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment.backup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.fragment.BaseFragment;

public class BackupRestoreNoBackupsFoundFragment extends BaseFragment {
    public BackupRestoreNoBackupsFoundFragment() {

    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return ViewExtensionsKt.themedInflate(inflater, this.getActivity(), R.layout.fragment_backup_restore_no_backups_found, container, false);
    }
}
