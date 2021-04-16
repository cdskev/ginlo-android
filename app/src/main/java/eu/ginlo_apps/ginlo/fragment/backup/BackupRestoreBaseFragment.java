// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment.backup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.BaseFragment;
import eu.ginlo_apps.ginlo.fragment.backup.BackupRestoreNoBackupsFoundFragment;
import eu.ginlo_apps.ginlo.fragment.backup.BackupRestorePasswordFragment;
import eu.ginlo_apps.ginlo.fragment.backup.BackupRestoreSelectBackupFragment;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link// BackupRestoreBaseFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link BackupRestoreBaseFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class BackupRestoreBaseFragment extends BaseFragment implements BaseFragment.OnFragmentInteractionListener {
    public static final int TYPE_LIST_BACKUPS = 2;
    public static final int TYPE_NO_BACKUPS_FOUND = 3;
    public static final int TYPE_SET_PASSWORD = 4;

    public static final String TYPE_ARGS_VALUE = "type_args_value";

    private static final String FRAGMENT_TYPE = "fragmentType";
    private static final String FRAGMENT_ARGS = "fragmentArgs";

    private static final String FRAGMENT_TAG = "restore_backup_child_fragment";

    private int mType;

    private Bundle mArguments;

    private Fragment mCurrentChildFragment;

    public BackupRestoreBaseFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param type      bsp. {@link BackupRestoreBaseFragment#TYPE_LIST_BACKUPS}
     * @param arguments arguments
     * @return A new instance of fragment BackupRestoreBaseFragment.
     */
    public static BackupRestoreBaseFragment newInstance(final int type, final Bundle arguments) {
        final BackupRestoreBaseFragment fragment = new BackupRestoreBaseFragment();

        final Bundle args = new Bundle();
        args.putInt(FRAGMENT_TYPE, type);
        args.putBundle(FRAGMENT_ARGS, arguments);

        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mType = getArguments().getInt(FRAGMENT_TYPE);
            mArguments = getArguments().getBundle(FRAGMENT_ARGS);
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {

        final View root = ViewExtensionsKt.themedInflate(inflater, this.requireActivity(), R.layout.fragment_backup_restore_base, container, false);

        setValues(root);

        return root;
    }

    @Override
    public void onFragmentViewClick(final View view) {
        super.onFragmentViewClick(view);

        switch (view.getId()) {
            case R.id.backup_restore_register_wo_button: {
                handleAction(AppConstants.BACKUP_RESTORE_ACTION_REGISTER_WO_BACKUP, null);
                break;
            }
            default: {
                LogUtil.w(this.getClass().getName(), LocalizedException.UNDEFINED_ARGUMENT);
                break;
            }
        }
    }

    @Override
    public void onFragmentInteraction(final int action, final Bundle arguments) {
        handleAction(action, arguments);
    }

    private void setValues(final View root) {
        final Fragment childFragment;

        final String title;
        final String description;
        switch (mType) {
            case TYPE_LIST_BACKUPS: {
                title = getString(R.string.backup_restore_title_select_backup);
                description = getString(R.string.backup_restore_desc_select_backup);

                childFragment = new BackupRestoreSelectBackupFragment();
                if (mArguments != null) {
                    childFragment.setArguments(mArguments);
                }
                break;
            }
            case TYPE_SET_PASSWORD: {
                title = getString(R.string.backup_restore_title_enter_password);
                description = getString(R.string.backup_restore_desc_enter_password);

                childFragment = new BackupRestorePasswordFragment();
                break;
            }
            case TYPE_NO_BACKUPS_FOUND: {
                title = getString(R.string.backup_restore_title_no_backups_found);
                description = getString(R.string.backup_restore_desc_no_backups_found);

                childFragment = new BackupRestoreNoBackupsFoundFragment();
                break;
            }
            default: {
                LogUtil.w(this.getClass().getName(), LocalizedException.UNDEFINED_ARGUMENT);
                return;
            }
        }

        if (root != null) {
            if (!StringUtil.isNullOrEmpty(title)) {
                final TextView titleView = root.findViewById(R.id.backup_restore_title_text_view);
                if (titleView != null) {
                    titleView.setText(title);
                }
            }

            if (!StringUtil.isNullOrEmpty(description)) {
                final TextView descView = root.findViewById(R.id.backup_restore_desc_text_view);
                if (descView != null) {
                    descView.setText(description);
                }
            }

            if (root.findViewById(R.id.backup_restore_child_fragment_container) != null) {
                if (getChildFragmentManager().findFragmentByTag(FRAGMENT_TAG) == null) {
                    final FragmentTransaction transaction = getChildFragmentManager().beginTransaction();

                    transaction.add(R.id.backup_restore_child_fragment_container, childFragment, FRAGMENT_TAG);

                    transaction.commit();
                } else {
                    final FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
                    //transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                    transaction.replace(R.id.backup_restore_child_fragment_container, childFragment, FRAGMENT_TAG);

                    transaction.commit();
                }
                mCurrentChildFragment = childFragment;
            }
        }
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
}
