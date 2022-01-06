// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment.backup;

import android.app.Application;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.FragmentActivity;
import dagger.android.support.AndroidSupportInjection;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.data.network.AppConnectivity;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.BaseFragment;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import javax.inject.Inject;

public class BackupConfigFragment extends BaseFragment {
    public static final String TYPE_ARGS_VALUE_SAVE_MEDIA = "type_args_value_media";
    public static final String TYPE_ARGS_VALUE_INTERVAL = "type_args_value_interval";
    private static final String FRAGMENT_TYPE = "fragmentType";
    private static final String FRAGMENT_ARGS = "fragmentArgs";
    private int mType;

    private Bundle mArguments;

    private String mLastBackupDate;

    private TextView mLatestDateTextView;

    private TextView mIntervalTextView;

    @Inject
    public AppConnectivity appConnectivity;

    public BackupConfigFragment() {
    }

    public static BackupConfigFragment newInstance(Bundle arguments) {
        BackupConfigFragment fragment = new BackupConfigFragment();

        Bundle args = new Bundle();
        args.putBundle(FRAGMENT_ARGS, arguments);

        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        AndroidSupportInjection.inject(this);
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mType = getArguments().getInt(FRAGMENT_TYPE);
            mArguments = getArguments().getBundle(FRAGMENT_ARGS);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View root = ViewExtensionsKt.themedInflate(inflater, this.getContext(), R.layout.fragment_backup_config, container, false);

        final FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.setTitle(getString(R.string.backup_title));
        }

        mLatestDateTextView = root.findViewById(R.id.settings_backup_last_date);

        if (!StringUtil.isNullOrEmpty(mLastBackupDate)) {
            mLatestDateTextView.setText(mLastBackupDate);
        }

        boolean isSwitchChecked = false;

        String interval = getString(R.string.settings_backup_config_interval_value_weekly);

        if (mArguments != null) {
            isSwitchChecked = mArguments.getBoolean(TYPE_ARGS_VALUE_SAVE_MEDIA);
            String intervalValue = mArguments.getString(TYPE_ARGS_VALUE_INTERVAL);

            if (!StringUtil.isNullOrEmpty(intervalValue)) {
                interval = intervalValue;
            }
        }

        SwitchCompat mediaSwitch = root.findViewById(R.id.settings_backup_config_save_media_switch);

        if (mediaSwitch != null) {
            mediaSwitch.setChecked(isSwitchChecked);
            mediaSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    Bundle args = new Bundle();
                    args.putBoolean(AppConstants.ACTION_ARGS_VALUE, isChecked);
                    handleAction(AppConstants.BACKUP_ACTION_CHANGE_MEDIA_SETTING, args);
                }
            });
        }

        mIntervalTextView = root.findViewById(R.id.settings_backup_config_interval_desc);

        if (mIntervalTextView != null) {
            String text = String.format(getString(R.string.settings_backup_config_interval_desc), "<b>" + interval + "</b>");
            CharSequence styledText = Html.fromHtml(text);
            mIntervalTextView.setText(styledText);
        }

        if (RuntimeConfig.isBAMandant() && activity != null) {
            //colorize
            final ColorUtil colorUtil = ColorUtil.getInstance();
            final int appAccentColor = colorUtil.getAppAccentColor((Application) activity.getApplicationContext());

            final Button configButton = root.findViewById(R.id.settings_backup_config_button);
            configButton.getBackground().setColorFilter(appAccentColor, PorterDuff.Mode.SRC_ATOP);

            final TextView settingsLabel = root.findViewById(R.id.settings_backup_settings_label);
            settingsLabel.setTextColor(appAccentColor);
        }
        return root;
    }

    @Override
    public void onFragmentViewClick(View view) {
        if (!appConnectivity.isConnected()) {
            Toast.makeText(getContext(), R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG).show();
            return;
        }

        super.onFragmentViewClick(view);

        switch (view.getId()) {
            case R.id.settings_backup_config_button: {
                handleAction(AppConstants.BACKUP_ACTION_START_BACKUP, null);
                break;
            }
            case R.id.settings_backup_config_password_view: {
                handleAction(AppConstants.BACKUP_ACTION_CHANGE_PASSWORD, null);
                break;
            }
            case R.id.settings_backup_config_interval_view: {
                handleAction(AppConstants.BACKUP_ACTION_CHANGE_INTERVAL, null);
                break;
            }
            default: {
                LogUtil.w(this.getClass().getName(), LocalizedException.UNDEFINED_ARGUMENT);
                break;
            }
        }
    }

    public void setLastBackupDate(final String dateString) {
        mLastBackupDate = dateString;

        if (!StringUtil.isNullOrEmpty(mLastBackupDate) && mLatestDateTextView != null) {
            mLatestDateTextView.setText(mLastBackupDate);
        }
    }

    public void setIntervalText(final String interval) {
        if (!StringUtil.isNullOrEmpty(interval)) {
            String text = String.format(getString(R.string.settings_backup_config_interval_desc), "<b>" + interval + "</b>");
            CharSequence styledText = Html.fromHtml(text);
            mIntervalTextView.setText(styledText);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}

