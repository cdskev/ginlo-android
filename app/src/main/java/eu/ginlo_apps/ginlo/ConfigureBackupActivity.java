// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.contracts.AsyncBackupKeysCallback;
import eu.ginlo_apps.ginlo.controller.contracts.CreateBackupListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.BaseFragment;
import eu.ginlo_apps.ginlo.fragment.IOnFragmentViewClickable;
import eu.ginlo_apps.ginlo.fragment.backup.BackupConfigFragment;
import eu.ginlo_apps.ginlo.fragment.backup.BackupConfigSetPasswortFragment;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.StorageUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import eu.ginlo_apps.ginlo.view.ProgressDownloadDialog;
import org.jetbrains.annotations.NotNull;

public class ConfigureBackupActivity
        extends BaseActivity
        implements BaseFragment.OnFragmentInteractionListener, CreateBackupListener,
        IOnFragmentViewClickable {
    
    public static final String TAG = ConfigureBackupActivity.class.getSimpleName();
    public static final String LOCAL_BACKUP_URI_PREF = "ConfigureBackupActivity.LOCAL_BACKUP_URI";
    public static final int LOCAL_BACKUP_URI_ACTIONCODE = 113;

    private static final int STATE_DEFAULT = 0;
    private static final int STATE_SET_PWD = 3;
    private static final int STATE_SET_PWD_CONFIRM = 4;

    private static final String FRAGMENT_TAG = "backup_config_fragment";

    private AccountController mAccountController;
    private PreferencesController mPreferencesController;
    private StorageUtil mStorageUtil;
    private ProgressDownloadDialog mProgressDownloadDialog;
    private int mState;
    private String mSetPassword;
    private ActivityState mNextState;
    private boolean mInitalCall = true;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        mAccountController = mApplication.getAccountController();
        mPreferencesController = mApplication.getPreferencesController();
        mAccountController.registerCreateBackupListener(this);

        if (mPreferencesController.getDisableBackup()) {
            finish();
        }

        mStorageUtil = new StorageUtil(mApplication);
    }

    @Override
    protected void onDestroy() {
        if (mAccountController != null) {
            mAccountController.unregisterCreateBackupListener(this);
        }
        super.onDestroy();
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_configure_backup;
    }

    @Override
    protected void onResumeActivity() {
        if (mAfterCreate) {
            updateActivity(STATE_DEFAULT, null, false);
        } else if (mNextState != null) {
            updateActivity(mNextState.state, mNextState.args, mNextState.clearBackStack);
        }
    }

    private void updateActivity(int state, final Bundle aArgs, boolean clearBackStack) {
        try {
            if (!isActivityInForeground()) {
                mNextState = new ActivityState();
                mNextState.state = state;
                mNextState.args = aArgs;
                return;
            }
            final Bundle args;
            mNextState = null;
            Fragment nextFragment;
            mState = state;

            if (aArgs != null) {
                args = aArgs;
            } else {
                args = new Bundle();
            }

            int interval = mPreferencesController.getBackupInterval();
            args.putString(BackupConfigFragment.TYPE_ARGS_VALUE_INTERVAL, getIntervalString(interval));

            switch (mState) {
                case STATE_SET_PWD: {
                    nextFragment = BackupConfigSetPasswortFragment.newInstance(BackupConfigSetPasswortFragment.TYPE_SET_PWD);
                    mSetPassword = null;
                    break;
                }
                case STATE_SET_PWD_CONFIRM: {
                    nextFragment = BackupConfigSetPasswortFragment.newInstance(BackupConfigSetPasswortFragment.TYPE_SET_PWD_CONFIRM);
                    break;
                }
                default: {
                    args.putBoolean(BackupConfigFragment.TYPE_ARGS_VALUE_SAVE_MEDIA, mPreferencesController.getSaveMediaInBackup());
                    nextFragment = BackupConfigFragment.newInstance(args);
                    updateLatestBUDateInFragment((BackupConfigFragment) nextFragment);
                    break;
                }
            }

            if (findViewById(R.id.fragment_backup_config_container) != null) {
                FragmentManager fm = getSupportFragmentManager();

                if (clearBackStack) {
                    int count = fm.getBackStackEntryCount();
                    while (count > 0) {
                        fm.popBackStackImmediate();
                        count--;
                    }
                }

                Fragment oldFragment = fm.findFragmentByTag(FRAGMENT_TAG);
                FragmentTransaction transaction = fm.beginTransaction();

                if (oldFragment == null) {
                    transaction.add(R.id.fragment_backup_config_container, nextFragment, FRAGMENT_TAG);
                } else {
                    transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                    transaction.replace(R.id.fragment_backup_config_container, nextFragment, FRAGMENT_TAG);
                }

                transaction.commit();
            }
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    private @NonNull
    String getIntervalString(int interval) {
        String intervalString;

        switch (interval) {
            case PreferencesController.BACKUP_INTERVAL_OFF: {
                intervalString = getString(R.string.settings_backup_config_interval_value_off);
                break;
            }
            case PreferencesController.BACKUP_INTERVAL_DAILY: {
                intervalString = getString(R.string.settings_backup_config_interval_value_daily);
                break;
            }
            case PreferencesController.BACKUP_INTERVAL_MONTHLY: {
                intervalString = getString(R.string.settings_backup_config_interval_value_monthly);
                break;
            }
            default: {
                intervalString = getString(R.string.settings_backup_config_interval_value_weekly);
                break;
            }
        }

        return intervalString;
    }

    private void updateLatestBUDateInFragment(BackupConfigFragment fragment) {
        long lastestDate = mPreferencesController.getLatestBackupDate();
        if (lastestDate > -1) {
            String dateString = DateUtil.getDateAndTimeStringFromMillis(lastestDate);
            fragment.setLastBackupDate(dateString);
        }
    }

    @Override
    public void onFragmentViewClick(View view) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);

        if (fragment instanceof IOnFragmentViewClickable) {
            ((IOnFragmentViewClickable) fragment).onFragmentViewClick(view);
        }
    }

    private void startBackupAction()
    {
        if (mPreferencesController.getBackupKey() != null) {
            mAccountController.startBackup();
        } else {
            updateActivity(STATE_SET_PWD, null, false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == LOCAL_BACKUP_URI_ACTIONCODE) {
            if (resultCode == Activity.RESULT_OK) {
                Uri resultUri = data.getData();
                LogUtil.d(TAG, "onActivityResult: LOCAL_BACKUP_URI_ACTIONCODE returned " + resultUri);
                if(resultUri != null) {
                    try {
                        //final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                        getContentResolver().takePersistableUriPermission(resultUri, takeFlags);
                    } catch (Exception e) {
                        LogUtil.e(TAG, "onActivityResult: Caught exception in takePersistableUriPermission: " + e.getMessage(), e);
                        return;
                    }
                    mPreferencesController.getSharedPreferences().edit().putString(LOCAL_BACKUP_URI_PREF, resultUri.toString()).apply();
                    startBackupAction();
                }
            }
        }
    }

    private void showBackupFileChooser() {
        final DialogInterface.OnClickListener showBackupFileHandler = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface arg0, final int arg1) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                String ginloID = mAccountController.getAccount().getAccountID();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/x-zip-compressed");
                intent.putExtra(Intent.EXTRA_TITLE, "ginlo-backup-" + ginloID + ".zip");

                try {
                    startActivityForResult(intent, ConfigureBackupActivity.LOCAL_BACKUP_URI_ACTIONCODE);
                } catch (android.content.ActivityNotFoundException e) {
                    LogUtil.e(TAG, "onResumeActivity: Could not start file chooser for LOCAL_BACKUP_URI_ACTIONCODE: " + e.getMessage());
                }
            }
        };

        final String title = ConfigureBackupActivity.this.getResources().getString(R.string.settings_backup_config_choose_file_title);
        final String body = ConfigureBackupActivity.this.getResources().getString(R.string.settings_backup_config_choose_file_body);
        final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(ConfigureBackupActivity.this,
                body, false, title,
                "Ok", null, showBackupFileHandler, null);
        dialog.show();
    }

    @Override
    public void onFragmentInteraction(int action, Bundle arguments) {
        switch (action) {
            case AppConstants.BACKUP_ACTION_START_BACKUP: {
                // Always ask for destination, if user starts backup manually!
                Uri backupDestination = null;
                if(!mInitalCall) {
                    backupDestination = mStorageUtil.getBackupDestinationUri();
                } else {
                    mInitalCall = false;
                }

                if(backupDestination == null || mStorageUtil.getBackupDestinationSize(backupDestination) == 0L) {
                    showBackupFileChooser();
                } else {
                    startBackupAction();
                }

                break;
            }
            case AppConstants.BACKUP_ACTION_CHANGE_PASSWORD: {
                updateActivity(STATE_SET_PWD, null, false);
                break;
            }
            case AppConstants.BACKUP_ACTION_SET_PASSWORD: {
                if (arguments == null) {
                    break;
                }

                String password = arguments.getString(AppConstants.ACTION_ARGS_VALUE);
                if (!StringUtil.isNullOrEmpty(password)) {
                    mSetPassword = password;
                    updateActivity(STATE_SET_PWD_CONFIRM, null, false);
                }
                break;
            }
            case AppConstants.BACKUP_ACTION_CONFIRM_PASSWORD: {
                try {
                    if (arguments == null) {
                        break;
                    }

                    String password = arguments.getString(AppConstants.ACTION_ARGS_VALUE);
                    if (StringUtil.isNullOrEmpty(password)) {
                        break;
                    }

                    if (StringUtil.isNullOrEmpty(mSetPassword)) {
                        updateActivity(STATE_SET_PWD, null, false);
                    }

                    if (!StringUtil.isEqual(mSetPassword, password)) {
                        DialogBuilderUtil.buildErrorDialog(this, getString(R.string.backup_passwords_not_match)).show();
                        break;
                    }

                    if (mPreferencesController.getBackupKey() != null) {
                        mAccountController.deactivateBackup();
                    }

                    showIdleDialog(-1);
                    mAccountController.activateBackup(password, new AsyncBackupKeysCallback() {
                        @Override
                        public void asyncBackupKeysFinished() {
                            dismissIdleDialog();
                            if (mPreferencesController.getLatestBackupDate() > 0) {
                                showPasswordChangeDialog();
                                updateActivity(STATE_DEFAULT, null, true);
                            } else {
                                mAccountController.startBackup();
                            }
                        }

                        @Override
                        public void asyncBackupKeysFailed(@NotNull LocalizedException exception) {
                            dismissIdleDialog();
                            DialogBuilderUtil.buildErrorDialog(ConfigureBackupActivity.this,
                                    getString(R.string.settings_backup_config_change_password_failed)).show();
                        }
                    });
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                    dismissIdleDialog();
                }
                break;
            }
            case AppConstants.BACKUP_ACTION_CHANGE_MEDIA_SETTING: {
                if (arguments == null) {
                    break;
                }

                try {
                    boolean saveMedia = arguments.getBoolean(AppConstants.ACTION_ARGS_VALUE);

                    mPreferencesController.setSaveMediaInBackup(saveMedia);
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                }
                break;
            }
            case AppConstants.BACKUP_ACTION_CHANGE_NETWORK_SETTING: {
                if (arguments == null) {
                    break;
                }

                try {
                    boolean wifiOnly = arguments.getBoolean(AppConstants.ACTION_ARGS_VALUE);

                    mPreferencesController.setBackupNetworkWifiOnly(wifiOnly);
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                }
                break;
            }
            case AppConstants.BACKUP_ACTION_CHANGE_INTERVAL: {
                showIntervalDialog();
                break;
            }
            default: {
                LogUtil.w(TAG, LocalizedException.UNDEFINED_ARGUMENT);
                break;
            }
        }
    }

    private void showPasswordChangeDialog() {
        String message = getString(R.string.settings_backup_config_change_password_succeeded);
        String title = getString(R.string.settings_backup_config_change_password_succeeded_title);
        String posBtn = getString(R.string.std_ok);

        DialogBuilderUtil.buildResponseDialogV7(this, message, title, posBtn, null, null, null).show();
    }

    private void showIntervalDialog() {
        final CharSequence[] items = new CharSequence[]
                {
                        getString(R.string.settings_backup_config_interval_value_off),
                        getString(R.string.settings_backup_config_interval_value_daily),
                        getString(R.string.settings_backup_config_interval_value_weekly),
                        getString(R.string.settings_backup_config_interval_value_monthly)
                };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(this.getString(R.string.settings_backup_config_interval))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                                        int id) {
                        try {
                            mPreferencesController.setBackupInterval(id);
                            setIntervalText();
                        } catch (LocalizedException e) {
                            LogUtil.e(TAG, e.getMessage(), e);
                            Toast.makeText(ConfigureBackupActivity.this, R.string.settings_save_setting_failed,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }).show();
    }

    private void setIntervalText()
            throws LocalizedException {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);

        if (fragment instanceof BackupConfigFragment) {
            String interval = getIntervalString(mPreferencesController.getBackupInterval());
            ((BackupConfigFragment) fragment).setIntervalText(interval);
        }
    }

    @Override
    public void onCreateBackupStateChanged(int state) {
        switch (state) {
            case CreateBackupListener.STATE_STARTED: {
                if (mProgressDownloadDialog == null) {
                    mProgressDownloadDialog = ProgressDownloadDialog.buildProgressDownloadDialog(this);
                    mProgressDownloadDialog.setIndeterminate(true);
                    mProgressDownloadDialog.updateMessage(getString(R.string.settings_backup_config_create_backup));
                    mProgressDownloadDialog.updateSecondaryTextView("");
                    mProgressDownloadDialog.show();
                }
                break;
            }
            case CreateBackupListener.STATE_SAVE_CHATS: {
                boolean show = false;
                if (mProgressDownloadDialog == null) {
                    mProgressDownloadDialog = ProgressDownloadDialog.buildProgressDownloadDialog(this);
                    show = true;
                }

                mProgressDownloadDialog.setIndeterminate(false);
                mProgressDownloadDialog.updateMessage(getString(R.string.settings_backup_config_saving_chats));
                mProgressDownloadDialog.updateProgress(0);
                mProgressDownloadDialog.updateSecondaryTextView("");

                if (show) {
                    mProgressDownloadDialog.show();
                }

                break;
            }
            case CreateBackupListener.STATE_SAVE_SERVICES: {
                boolean show = false;
                if (mProgressDownloadDialog == null) {
                    mProgressDownloadDialog = ProgressDownloadDialog.buildProgressDownloadDialog(this);
                    show = true;
                }

                mProgressDownloadDialog.setIndeterminate(true);
                mProgressDownloadDialog.updateMessage(getString(R.string.settings_backup_config_saving_services));
                mProgressDownloadDialog.updateSecondaryTextView("");

                if (show) {
                    mProgressDownloadDialog.show();
                }

                break;
            }
            case CreateBackupListener.STATE_WRITE_BACKUP_FILE: {
                boolean show = false;
                if (mProgressDownloadDialog == null) {
                    mProgressDownloadDialog = ProgressDownloadDialog.buildProgressDownloadDialog(this);
                    show = true;
                }

                mProgressDownloadDialog.setIndeterminate(true);
                mProgressDownloadDialog.updateMessage(getString(R.string.settings_backup_config_saving_backup));
                mProgressDownloadDialog.updateSecondaryTextView("");

                if (show) {
                    mProgressDownloadDialog.show();
                }
                break;
            }
            case CreateBackupListener.STATE_FINISHED: {
                if (mProgressDownloadDialog != null) {
                    mProgressDownloadDialog.dismiss();
                    mProgressDownloadDialog = null;
                }

                if (mState != STATE_DEFAULT) {
                    updateActivity(STATE_DEFAULT, null, true);
                }
                if (mPreferencesController.getShowMessageAfterBackupProcess()) {
                    DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);

                            if (fragment instanceof BackupConfigFragment) {
                                updateLatestBUDateInFragment((BackupConfigFragment) fragment);
                            }
                        }
                    };

                    AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialogWithDontShowAgain(ConfigureBackupActivity.this,
                            getString(R.string.settings_backup_config_create_backup_finished, mPreferencesController.getLatestBackupPath()),
                            getString(R.string.settings_backup_config_create_backup_finished_title),
                            null,
                            getString(R.string.std_ok),
                            clickListener,
                            clickListener,
                            new DialogBuilderUtil.DoNotShowAgainChoiceListener() {
                                @Override
                                public void doNotShowAgainClicked(boolean isChecked) {
                                    mPreferencesController.setShowMessageAfterBackupProcess(!isChecked);
                                }
                            });

                    dialog.show();
                } else {
                    Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);

                    if (fragment instanceof BackupConfigFragment) {
                        updateLatestBUDateInFragment((BackupConfigFragment) fragment);
                    }
                }
                break;
            }
            default: {
                LogUtil.w(TAG, LocalizedException.UNDEFINED_ARGUMENT);
                break;
            }
        }
    }

    @Override
    public void onCreateBackupSaveStateUpdate(int percent) {
        boolean show = false;
        if (mProgressDownloadDialog == null) {
            mProgressDownloadDialog = ProgressDownloadDialog.buildProgressDownloadDialog(this);

            mProgressDownloadDialog.updateMessage(getString(R.string.settings_backup_config_copy_backup_to_drive));
            mProgressDownloadDialog.setIndeterminate(false);
            mProgressDownloadDialog.setMax(100);
            show = true;
        }

        mProgressDownloadDialog.updateProgress(percent);
        mProgressDownloadDialog.updateSecondaryTextView(percent + "%");

        if (show) {
            mProgressDownloadDialog.show();
        }
    }

    @Override
    public void onCreateBackupSaveChatsUpdate(int current, int size) {
        boolean show = false;
        if (mProgressDownloadDialog == null) {
            mProgressDownloadDialog = ProgressDownloadDialog.buildProgressDownloadDialog(this);

            mProgressDownloadDialog.updateMessage(getString(R.string.settings_backup_config_saving_chats));
            mProgressDownloadDialog.setIndeterminate(false);
            show = true;
        }

        mProgressDownloadDialog.setMax(size);
        mProgressDownloadDialog.updateProgress(current);
        mProgressDownloadDialog.updateSecondaryTextView(current + " " + getString(R.string.backup_restore_progress_secondary) + " " + size);

        if (show) {
            mProgressDownloadDialog.show();
        }
    }

    @Override
    public void onCreateBackupFailed(@NotNull String message, boolean needToConnectAgain) {
        if (mProgressDownloadDialog != null) {
            mProgressDownloadDialog.dismiss();
            mProgressDownloadDialog = null;
        }

        if (mState != STATE_DEFAULT) {
            updateActivity(STATE_DEFAULT, null, true);
        }

        String errorMessage;
        if (StringUtil.isNullOrEmpty(message)) {
            errorMessage = getString(R.string.settings_backup_config_create_backup_failed);
        } else {
            errorMessage = getString(R.string.settings_backup_config_create_backup_failed) + "\n\n" + message;
        }
        DialogBuilderUtil.buildErrorDialog(this, errorMessage).show();
    }

    private class ActivityState {
        int state;
        Bundle args;
        boolean clearBackStack;
    }
}
