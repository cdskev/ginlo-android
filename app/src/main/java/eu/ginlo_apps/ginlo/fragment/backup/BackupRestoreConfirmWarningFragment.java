// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment.backup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.BaseFragment;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;

public class BackupRestoreConfirmWarningFragment extends BaseFragment {

    public BackupRestoreConfirmWarningFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return ViewExtensionsKt.themedInflate(inflater, this.getActivity(), R.layout.fragment_backup_restore_confirm_account_warning, container, false);
    }

    @Override
    public void onFragmentViewClick(View view) {
        super.onFragmentViewClick(view);

        switch (view.getId()) {
            case R.id.backup_restore_confirm_wo_button: {
                handleAction(AppConstants.BACKUP_RESTORE_ACTION_REGISTER_WO_CONFIRM, null);
                break;
            }
            case R.id.backup_restore_confirm_cancel_button: {
                handleAction(AppConstants.BACKUP_RESTORE_ACTION_REGISTER_WO_CONFIRM_CANCEL, null);
                break;
            }
            default: {
                LogUtil.w(this.getClass().getName(), LocalizedException.UNDEFINED_ARGUMENT);
                break;
            }
        }
    }
}
