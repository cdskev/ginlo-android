// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.LoginActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.register.IntroActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.LocalBackupHelper;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.contracts.DownloadBackupListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnConfirmAccountListener;
import eu.ginlo_apps.ginlo.data.network.AppConnectivity;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.BaseFragment;
import eu.ginlo_apps.ginlo.fragment.IOnFragmentViewClickable;
import eu.ginlo_apps.ginlo.fragment.backup.BackupRestoreBaseFragment;
import eu.ginlo_apps.ginlo.fragment.backup.BackupRestoreConfirmWarningFragment;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.service.RestoreBackupService;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.PermissionUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import eu.ginlo_apps.ginlo.view.ProgressDownloadDialog;
import org.jetbrains.annotations.NotNull;
import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static eu.ginlo_apps.ginlo.model.constant.AppConstants.STATE_ACTION_RESTORE_BACKUP_SERVICES_STARTED;

public class RestoreBackupActivity
        extends BaseActivity implements
        DownloadBackupListener,
        OnConfirmAccountListener,
        BaseFragment.OnFragmentInteractionListener,
        IOnFragmentViewClickable {

    private static final String FRAGMENT_TAG = "restore_backup_fragment";

    private static final int STATE_SELECT_BACKUP = 4;
    private static final int STATE_NO_BACKUPS_FOUND = 5;
    private static final int STATE_ENTER_PASSWORD = 6;
    private static final int STATE_WO_CONFIRM = 7;

    private final List<Integer> mLastStates = new ArrayList<>();
    @Inject
    public AppConnectivity appConnectivity;
    private AccountController mAccountController;
    private BackupStateReceiver mBackupStateReceiver;
    private int mState;
    private Bundle mSelectedBackup;
    private ProgressDownloadDialog mProgressDownloadDialog;
    private String mDownloadedBackupPath;
    private ContactController.OnLoadContactsListener mOnLoadContactsListener;
    private boolean mRestoreBackupIsStarted = false;
    private ActivityState mNextState;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        mAccountController = ((SimsMeApplication) getApplication()).getAccountController();
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_backup_restore_not_found;
    }

    @Override
    protected void onResumeActivity() {
        if (this.mAfterCreate) {
            showIdleDialog(-1);
            showLocalBackups();
        } else if (mNextState != null) {
            updateActivity(mNextState.state, mNextState.args);
        }
    }

    @Override
    protected void onActivityPostLoginResult(int requestCode, int resultCode, Intent data) {
        super.onActivityPostLoginResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onBackPressed() {
        switch (mState) {
            case STATE_NO_BACKUPS_FOUND:
            case STATE_SELECT_BACKUP: {
                String title = getString(R.string.abort_registration_warning_title);
                String message = getString(R.string.abort_registration_warning_message);
                String posButton = getString(R.string.abort_registration_warning_positve_button);
                String negButton = getString(R.string.std_cancel);

                DialogInterface.OnClickListener posClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mAccountController != null) {
                            mAccountController.resetRegistrationProcess();
                            Intent intent = new Intent(RestoreBackupActivity.this, IntroActivity.class);
                                                        startActivity(intent);
                                                        finish();
                        }
                    }
                };

                AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialogV7(this, message, title, posButton, negButton, posClickListener, null);

                dialog.show();

                break;
            }
            default: {
                if (mLastStates.size() > 0) {
                    Integer state = mLastStates.remove(mLastStates.size() - 1);
                    if (state != null) {
                        mState = state;
                    }
                }
                super.onBackPressed();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (mBackupStateReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mBackupStateReceiver);
        }

        super.onDestroy();
    }

    private void updateActivity(int state, Bundle args) {
        if (!isActivityInForeground()) {
            mNextState = new ActivityState();
            mNextState.state = state;
            mNextState.args = args;
            return;
        }

        mNextState = null;
        mLastStates.add(mState);
        mState = state;
        Fragment nextFragment;

        switch (mState) {
            case STATE_NO_BACKUPS_FOUND: {
                nextFragment = BackupRestoreBaseFragment.newInstance(BackupRestoreBaseFragment.TYPE_NO_BACKUPS_FOUND, args);
                break;
            }
            case STATE_SELECT_BACKUP: {
                nextFragment = BackupRestoreBaseFragment.newInstance(BackupRestoreBaseFragment.TYPE_LIST_BACKUPS, args);
                break;
            }
            case STATE_ENTER_PASSWORD: {
                nextFragment = BackupRestoreBaseFragment.newInstance(BackupRestoreBaseFragment.TYPE_SET_PASSWORD, args);
                break;
            }
            case STATE_WO_CONFIRM: {
                nextFragment = new BackupRestoreConfirmWarningFragment();
                break;
            }
            default: {
                LogUtil.w(this.getClass().getName(), LocalizedException.UNDEFINED_ARGUMENT);
                return;
            }
        }

        if (findViewById(R.id.fragment_restore_backup_container) != null) {
            Fragment oldFragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

            if (oldFragment == null) {
                transaction.add(R.id.fragment_restore_backup_container, nextFragment, FRAGMENT_TAG);
            } else {
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                transaction.replace(R.id.fragment_restore_backup_container, nextFragment, FRAGMENT_TAG);
            }
            transaction.addToBackStack(null);
            transaction.commit();
        }
    }

    private void showLocalBackups() {
        dismissIdleDialog();
        LocalBackupHelper localBackupHelper = new LocalBackupHelper((SimsMeApplication) getApplication());
        List<Bundle> backupsFound = localBackupHelper.getBundleForLocalBackup();

        //TODO : Need to discuss how to handle to the scenario if no backups found on local external storage

        if (backupsFound.size() > 0) {
            Bundle args = new Bundle();
            args.putParcelableArrayList(BackupRestoreBaseFragment.TYPE_ARGS_VALUE, new ArrayList<Bundle>(backupsFound));

            updateActivity(STATE_SELECT_BACKUP, args);
        } else {
            updateActivity(STATE_NO_BACKUPS_FOUND, null);
        }
    }

    @Override
    public void onConfirmAccountSuccess() {
        final ContactController.OnSystemChatCreatedListener onSystemChatCreatedListener = new ContactController.OnSystemChatCreatedListener() {
            @Override
            public void onSystemChatCreatedSuccess() {
                dismissIdleDialog();

                try {
                    final Intent intent = new Intent(RestoreBackupActivity.this, eu.ginlo_apps.ginlo.LoginActivity.class);

                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                    startActivity(intent);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public void onSystemChatCreatedError(String message) {
                dismissIdleDialog();

                mAccountController.resetCreateAccountValidateConfirmCode();
                DialogBuilderUtil.buildErrorDialog(RestoreBackupActivity.this, message).show();
            }
        };

        SimsMeApplication app = ((SimsMeApplication) getApplication());
        app.getContactController().createSystemChatContact(onSystemChatCreatedListener);
    }

    @Override
    public void onConfirmAccountFail(@NotNull String message) {
        dismissIdleDialog();

        DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mAccountController.deleteAccount();
            }
        };

        if (StringUtil.isEqual(message, getString(R.string.notification_account_was_deleted))) {
            DialogBuilderUtil.buildResponseDialog(RestoreBackupActivity.this,
                    getString(R.string.notification_account_was_deleted_registration_text),
                    getString(R.string.notification_account_was_deleted_registration_title),
                    getString(R.string.notification_account_was_deleted_registration_confirm),
                    null,
                    clickListener,
                    null
            ).show();
        } else {
            DialogBuilderUtil.buildErrorDialog(RestoreBackupActivity.this, message).show();
        }
    }

    @Override
    public void onDownloadBackupStateChanged(int state) {
        if (state == DownloadBackupListener.STATE_STARTED_DOWNLOAD) {
            mProgressDownloadDialog.updateMessage(getString(R.string.backup_restore_download_progress_text));
        } else if (state == DownloadBackupListener.STATE_STARTED_COPY_TO_SIMSME) {
            mProgressDownloadDialog.updateMessage(getString(R.string.backup_restore_copy_progress_text));
        }

        mProgressDownloadDialog.updateProgress(0);
    }

    @Override
    public void onDownloadBackupDownloadUpdate(int percent) {
        LogUtil.d("BACKUP DOWNLOAD", "Download File. Percent -> " + percent);
        mProgressDownloadDialog.updateProgress(percent);
        mProgressDownloadDialog.updateSecondaryTextView(percent + "%");
    }

    @Override
    public void onDownloadBackupCopyUpdate(int percent) {
        LogUtil.d("BACKUP DOWNLOAD", "Copy File. Percent -> " + percent);
        mProgressDownloadDialog.updateProgress(percent);
        mProgressDownloadDialog.updateSecondaryTextView(percent + "%");
    }

    @Override
    public void onDownloadBackupFailed(String message) {
        LogUtil.d("BACKUP DOWNLOAD", "DOWNLOAD FAILED! Msg:" + message);
        mProgressDownloadDialog.dismiss();
        AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialogV7(this, getString(R.string.backup_restore_download_process_failed),
                null, getString(android.R.string.ok), null, null, null);
        dialog.show();
    }

    @Override
    public void onDownloadBackupFinished(@NotNull String localBackupPath) {
        LogUtil.d("BACKUP DOWNLOAD", "DOWNLOAD FINISHED! Path:" + localBackupPath);
        mDownloadedBackupPath = localBackupPath;
        mProgressDownloadDialog.dismiss();
        updateActivity(STATE_ENTER_PASSWORD, null);
    }

    @Override
    public void onFragmentInteraction(int action, Bundle arguments) {
        switch (action) {
            case AppConstants.BACKUP_RESTORE_ACTION_REGISTER_WO_BACKUP: {
                updateActivity(STATE_WO_CONFIRM, null);
                break;
            }
            case AppConstants.BACKUP_RESTORE_ACTION_BACKUP_SELECTED: {
                mSelectedBackup = arguments;
                break;
            }
            case AppConstants.BACKUP_RESTORE_ACTION_BACKUP_DESELECTED: {
                mSelectedBackup = null;
                break;
            }
            case AppConstants.BACKUP_RESTORE_ACTION_IMPORT_BACKUP: {
                if (mSelectedBackup != null) {
                    final String localBackupPath = mSelectedBackup.getString(LocalBackupHelper.LOCAL_BACKUP_PATH);
                    if (localBackupPath != null && !localBackupPath.isEmpty()) {
                        createAndShowProgressDownloadDialog();

                        if (SystemUtil.hasMarshmallow()) {
                            requestPermission(PermissionUtil.PERMISSION_FOR_WRITE_EXTERNAL_STORAGE,
                                    Integer.MIN_VALUE,
                                    new PermissionUtil.PermissionResultCallback() {
                                        @Override
                                        public void permissionResult(int permission,
                                                                     boolean permissionGranted) {
                                            if ((permission == PermissionUtil.PERMISSION_FOR_WRITE_EXTERNAL_STORAGE)
                                                    && permissionGranted) {
                                                onDownloadBackupFinished(localBackupPath);
                                            }
                                            else
                                            {
                                                mProgressDownloadDialog.dismiss();
                                            }
                                        }
                                    });
                        } else {
                            onDownloadBackupFinished(localBackupPath);
                        }
                    }
                }
                break;
            }
            case AppConstants.BACKUP_RESTORE_ACTION_BACKUP_SET_PASSWORD: {
                if (arguments != null) {
                    String password = arguments.getString(AppConstants.ACTION_ARGS_VALUE);
                    if (!StringUtil.isNullOrEmpty(password)) {
                        startRestoreBackup(password);
                    }
                }
                break;
            }
            case AppConstants.BACKUP_RESTORE_ACTION_REGISTER_WO_CONFIRM: {
                try {
                    String confirmCode = getSimsMeApplication().getPreferencesController().getRegConfirmCode();
                    if (confirmCode != null) {
                        showIdleDialog(-1);
                        mAccountController.createAccountConfirmAccount(confirmCode, this);
                    }
                } catch (LocalizedException e) {
                    LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                }
                break;
            }
            case AppConstants.BACKUP_RESTORE_ACTION_REGISTER_WO_CONFIRM_CANCEL: {
                onBackPressed();
                break;
            }
            default: {
                LogUtil.w(this.getClass().getName(), LocalizedException.UNDEFINED_ARGUMENT);
                finish();
                break;
            }
        }
    }

    private void createAndShowProgressDownloadDialog() {
        mProgressDownloadDialog = ProgressDownloadDialog.buildProgressDownloadDialog(this);
        mProgressDownloadDialog.setMax(100);
        mProgressDownloadDialog.updateMessage(getString(R.string.backup_restore_download_progress_text));
        mProgressDownloadDialog.updateSecondaryTextView("0 %");
        mProgressDownloadDialog.show();
    }

    private void startRestoreBackup(final String password) {
        if (!mRestoreBackupIsStarted) {
            mRestoreBackupIsStarted = true;
            // The filter's action is BROADCAST_ACTION
            IntentFilter statusIntentFilter = new IntentFilter(
                    AppConstants.BROADCAST_RESTORE_BACKUP_ACTION);

            // Sets the filter's category to DEFAULT
            statusIntentFilter.addCategory(Intent.CATEGORY_DEFAULT);

            // Instantiates a new BackupStateReceiver
            mBackupStateReceiver = new BackupStateReceiver();

            // Registers the Receiver
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    mBackupStateReceiver,
                    statusIntentFilter);

            Intent restoreBackupIntent = new Intent(this, RestoreBackupService.class);
            restoreBackupIntent.putExtra(AppConstants.INTENT_EXTENDED_DATA_PATH, mDownloadedBackupPath);
            restoreBackupIntent.putExtra(AppConstants.INTENT_EXTENDED_DATA_BACKUP_PWD, password);

            if (mProgressDownloadDialog == null) {
                mProgressDownloadDialog = ProgressDownloadDialog.buildProgressDownloadDialog(this);
            }

            mProgressDownloadDialog.setIndeterminate(true);
            mProgressDownloadDialog.show();

            startService(restoreBackupIntent);
        }
    }

    @Override
    public void onFragmentViewClick(View view) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);

        if (fragment instanceof IOnFragmentViewClickable) {
            ((IOnFragmentViewClickable) fragment).onFragmentViewClick(view);
        }
    }

    /**
     * Methode wird nach erfolgreichem backup aufgerufen
     * kann in Sub-Klassen zusaetzliche Aktionen ausfuehren
     */
    void backupFinished() {
        mProgressDownloadDialog.dismiss();

        Intent nextIntent = new Intent(RestoreBackupActivity.this, RuntimeConfig.getClassUtil().getLoginActivityClass());
        nextIntent.putExtra(LoginActivity.EXTRA_NEXT_ACTIVITY, RuntimeConfig.getClassUtil().getChatOverviewActivityClass().getName());
        nextIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        startActivity(nextIntent);
        finish();
    }

    private class BackupStateReceiver extends BroadcastReceiver {
        private int mChatsSize;
        private int mChannelSize;

        public BackupStateReceiver() {
            //
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getIntExtra(AppConstants.INTENT_EXTENDED_DATA_STATUS, -1)) {
                case AppConstants.STATE_ACTION_RESTORE_BACKUP_STARTED: {
                    LogUtil.d("RESTORE", "STATE_ACTION_RESTORE_BACKUP_STARTED");
                    mProgressDownloadDialog.updateMessage(getString(R.string.backup_restore_progress_started_text));
                    mProgressDownloadDialog.updateSecondaryTextView("");
                    break;
                }
                case AppConstants.STATE_ACTION_RESTORE_BACKUP_ACCOUNT: {
                    LogUtil.d("RESTORE", "STATE_ACTION_RESTORE_BACKUP_ACCOUNT");
                    mProgressDownloadDialog.updateMessage(getString(R.string.backup_restore_progress_account));
                    break;
                }
                case AppConstants.STATE_ACTION_RESTORE_BACKUP_CONTACTS: {
                    LogUtil.d("RESTORE", "STATE_ACTION_RESTORE_BACKUP_CONTACTS");
                    mProgressDownloadDialog.updateMessage(getString(R.string.backup_restore_progress_contacts));
                    break;
                }
                case AppConstants.STATE_ACTION_RESTORE_BACKUP_CHATS_STARTED: {
                    LogUtil.d("RESTORE", "STATE_ACTION_RESTORE_BACKUP_CHATS_STARTED");
                    mChatsSize = intent.getIntExtra(AppConstants.INTENT_EXTENDED_DATA_EXTRA, -1);
                    if (mChatsSize > -1) {
                        mProgressDownloadDialog.setIndeterminate(false);
                        mProgressDownloadDialog.setMax(mChatsSize);
                        mProgressDownloadDialog.updateProgress(0);
                        mProgressDownloadDialog.updateSecondaryTextView("1 " + getString(R.string.backup_restore_progress_secondary) + " " + mChatsSize);
                    }

                    mProgressDownloadDialog.updateMessage(getString(R.string.backup_restore_progress_chats));

                    break;
                }
                case AppConstants.STATE_ACTION_RESTORE_BACKUP_CHATS_UPDATE: {
                    LogUtil.d("RESTORE", "STATE_ACTION_RESTORE_BACKUP_CHATS_UPDATE");
                    int currentChat = intent.getIntExtra(AppConstants.INTENT_EXTENDED_DATA_EXTRA, -1);
                    if (currentChat > -1) {
                        mProgressDownloadDialog.updateProgress(currentChat);
                        mProgressDownloadDialog.updateSecondaryTextView((currentChat + 1) + " " + getString(R.string.backup_restore_progress_secondary) + " " + mChatsSize);
                    }
                    break;
                }
                case AppConstants.STATE_ACTION_RESTORE_BACKUP_TIMED_MESSAGES: {
                    LogUtil.d("RESTORE", "STATE_ACTION_RESTORE_BACKUP_TIMED_MESSAGES");
                    mProgressDownloadDialog.setIndeterminate(true);
                    mProgressDownloadDialog.updateMessage(getString(R.string.backup_restore_progress_timed_messages));
                    mProgressDownloadDialog.updateSecondaryTextView("");
                    break;
                }
                case AppConstants.STATE_ACTION_RESTORE_BACKUP_CHANNELS_STARTED: {
                    LogUtil.d("RESTORE", "STATE_ACTION_RESTORE_BACKUP_CHANNELS_STARTED");
                    mChannelSize = intent.getIntExtra(AppConstants.INTENT_EXTENDED_DATA_EXTRA, -1);
                    if (mChannelSize > -1) {
                        mProgressDownloadDialog.setIndeterminate(false);
                        mProgressDownloadDialog.setMax(mChannelSize);
                        mProgressDownloadDialog.updateProgress(0);
                        mProgressDownloadDialog.updateSecondaryTextView("1 " + getString(R.string.backup_restore_progress_secondary) + " " + mChannelSize);
                    }

                    mProgressDownloadDialog.updateMessage(getString(R.string.backup_restore_progress_channels));

                    break;
                }
                case AppConstants.STATE_ACTION_RESTORE_BACKUP_CHANNELS_UPDATE:
                case AppConstants.STATE_ACTION_RESTORE_BACKUP_SERVICES_UPDATE: {
                    LogUtil.d("RESTORE", "STATE_ACTION_RESTORE_BACKUP_CHANNELS/SERVICES_UPDATE");
                    int currentChannel = intent.getIntExtra(AppConstants.INTENT_EXTENDED_DATA_EXTRA, -1);
                    if (currentChannel > -1) {
                        mProgressDownloadDialog.updateProgress(currentChannel);
                        mProgressDownloadDialog.updateSecondaryTextView((currentChannel + 1) + " " + getString(R.string.backup_restore_progress_secondary) + " " + mChannelSize);
                    }

                    break;
                }
                case STATE_ACTION_RESTORE_BACKUP_SERVICES_STARTED: {
                    LogUtil.d("RESTORE", "STATE_ACTION_RESTORE_BACKUP_SERVICES_STARTED");
                    mChannelSize = intent.getIntExtra(AppConstants.INTENT_EXTENDED_DATA_EXTRA, -1);
                    if (mChannelSize > -1) {
                        mProgressDownloadDialog.setIndeterminate(false);
                        mProgressDownloadDialog.setMax(mChannelSize);
                        mProgressDownloadDialog.updateProgress(0);
                        mProgressDownloadDialog.updateSecondaryTextView("1 " + getString(R.string.backup_restore_progress_secondary) + " " + mChannelSize);
                    }

                    mProgressDownloadDialog.updateMessage(getString(R.string.backup_restore_progress_services));

                    break;
                }
                case AppConstants.STATE_ACTION_RESTORE_BACKUP_FINISHED: {
                    LogUtil.d("RESTORE", "STATE_ACTION_RESTORE_BACKUP_FINISHED");

                    if (mBackupStateReceiver != null) {
                        LocalBroadcastManager.getInstance(RestoreBackupActivity.this).unregisterReceiver(mBackupStateReceiver);
                        mBackupStateReceiver = null;
                    }

                    String localBackupPath = mSelectedBackup.getString(LocalBackupHelper.LOCAL_BACKUP_PATH);
                    if (localBackupPath != null && !localBackupPath.isEmpty()) {
                        File backupFile = new File(localBackupPath);
                        if (backupFile.exists() && backupFile.canRead()) {

                            PreferencesController preferencesController = getSimsMeApplication().getPreferencesController();
                            preferencesController.setLatestBackupFileSize(backupFile.length());
                            preferencesController.setLatestBackupDate(backupFile.lastModified());
                            preferencesController.setLatestBackupPath(backupFile.getAbsolutePath());
                        }
                    }
                    mRestoreBackupIsStarted = false;

                    if (RuntimeConfig.isBAMandant() && !getSimsMeApplication().getContactController().existsFtsDatabase()) {
                        try {
                            getSimsMeApplication().getContactController().createAndFillFtsDB(true);
                        } catch (LocalizedException e) {
                            LogUtil.w("RestoreBackupActivity", "createFTS", e);
                        }
                    }

                    int hasRestoredContacts = intent.getIntExtra(AppConstants.INTENT_EXTENDED_DATA_EXTRA, -1);

                    if (hasRestoredContacts == 1) {
                        mProgressDownloadDialog.setIndeterminate(true);
                        mProgressDownloadDialog.updateMessage(getString(R.string.backup_restore_progress_sync_contacts));
                        mProgressDownloadDialog.updateSecondaryTextView("");

                        mOnLoadContactsListener = new ContactController.OnLoadContactsListener() {
                            @Override
                            public void onLoadContactsComplete() {
                                if (mOnLoadContactsListener != null) {
                                    getSimsMeApplication().getContactController().removeOnLoadContactsListener(mOnLoadContactsListener);
                                }

                                getSimsMeApplication().getPreferencesController().setHasOldContactsMerged();
                                getSimsMeApplication().getContactController().loadPrivateIndexEntries(null);
                                getSimsMeApplication().getPreferencesController().loadServerConfigVersions(true, null);
                                backupFinished();
                            }

                            @Override
                            public void onLoadContactsCanceled() {
                                if (mOnLoadContactsListener != null) {
                                    getSimsMeApplication().getContactController().removeOnLoadContactsListener(mOnLoadContactsListener);
                                }

                                mProgressDownloadDialog.dismiss();

                                String errorMsg = getString(R.string.backup_restore_process_failed_contact_sync_failed);

                                AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialogV7(RestoreBackupActivity.this,
                                        errorMsg, getString(R.string.backup_restore_process_failed_title),
                                        getString(R.string.std_ok), null, null, null);
                                dialog.show();
                            }

                            @Override
                            public void onLoadContactsError(String message) {
                                if (mOnLoadContactsListener != null) {
                                    getSimsMeApplication().getContactController().removeOnLoadContactsListener(mOnLoadContactsListener);
                                }

                                mProgressDownloadDialog.dismiss();

                                String errorMsg = getString(R.string.backup_restore_process_failed_contact_sync_failed);

                                AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialogV7(RestoreBackupActivity.this,
                                        errorMsg, getString(R.string.backup_restore_process_failed_title),
                                        getString(R.string.std_ok), null, null, null);
                                dialog.show();
                            }
                        };

                        requestPermission(PermissionUtil.PERMISSION_FOR_READ_CONTACTS, R.string.permission_rationale_contacts, new PermissionUtil.PermissionResultCallback() {
                            @Override
                            public void permissionResult(int permission, boolean permissionGranted) {

                                boolean hasPerm = permission == PermissionUtil.PERMISSION_FOR_READ_CONTACTS && permissionGranted;
                                getSimsMeApplication().getContactController().syncContacts(mOnLoadContactsListener, true, hasPerm);
                            }
                        });
                    } else {
                        mProgressDownloadDialog.setIndeterminate(true);
                        mProgressDownloadDialog.updateMessage(getString(R.string.backup_restore_progress_contacts));
                        mProgressDownloadDialog.updateSecondaryTextView("");

                        getSimsMeApplication().getContactController().loadPrivateIndexEntries(new GenericActionListener<Void>() {
                            @Override
                            public void onSuccess(Void object) {
                                getSimsMeApplication().getPreferencesController().loadServerConfigVersions(true, null);
                                backupFinished();
                            }

                            @Override
                            public void onFail(String message, String errorIdent) {
                                getSimsMeApplication().getPreferencesController().loadServerConfigVersions(true, null);
                                backupFinished();
                            }
                        });
                    }
                    break;
                }
                case AppConstants.STATE_ACTION_RESTORE_BACKUP_ERROR: {
                    if (mBackupStateReceiver != null) {
                        LocalBroadcastManager.getInstance(RestoreBackupActivity.this).unregisterReceiver(mBackupStateReceiver);
                        mBackupStateReceiver = null;
                    }

                    mRestoreBackupIsStarted = false;
                    boolean createSilentException = true;
                    String errorMsg;
                    Exception e = SystemUtil.dynamicDownCast(intent.getSerializableExtra(AppConstants.INTENT_EXTENDED_DATA_EXCEPTION), Exception.class);
                    if (e instanceof LocalizedException) {
                        LocalizedException le = (LocalizedException) e;

                        switch (le.getIdentifier()) {
                            case LocalizedException.BACKUP_RESTORE_WRONG_PW: {
                                errorMsg = getString(R.string.backup_restore_process_failed_msg_wrong_pwd);
                                createSilentException = false;
                                break;
                            }
                            case LocalizedException.BACKUP_RESTORE_SERVER_CONNECTION_FAILED:
                            case LocalizedException.BACKUP_RESTORE_ACCOUNT_SERVER_CONNECTION_FAILED: {
                                if (appConnectivity.isConnected()) {
                                    if (le.getIdentifier().equals(LocalizedException.BACKUP_RESTORE_ACCOUNT_SERVER_CONNECTION_FAILED)) {
                                        errorMsg = getString(R.string.backup_restore_process_failed_msg_conn_failed);
                                    } else {
                                        errorMsg = getString(R.string.backup_restore_process_failed_msg_conn_failed);
                                    }
                                } else {
                                    Toast.makeText(RestoreBackupActivity.this, R.string.backendservice_internet_connectionFailed,
                                            Toast.LENGTH_LONG).show();
                                    errorMsg = getString(R.string.backup_restore_process_failed_msg_conn_failed_no_inet);
                                }
                                createSilentException = false;
                                break;
                            }
                            case LocalizedException.BACKUP_RESTORE_SALTS_NOT_EQUAL: {
                                errorMsg = getString(R.string.backup_restore_process_failed_msg_salts_not_equal);
                                break;
                            }
                            case LocalizedException.FILE_UNZIPPING_FAILED: {
                                errorMsg = getString(R.string.backup_restore_process_failed_msg_unzipping_failed);
                                break;
                            }
                            default: {
                                errorMsg = getString(R.string.backup_restore_process_failed_msg_default);
                            }
                        }
                    } else {
                        errorMsg = getString(R.string.backup_restore_process_failed_msg_default);
                    }

                    if (e != null && createSilentException) {
                        LogUtil.e(this.getClass().getSimpleName(), "Failed while restoring backup", e);
                    }

                    mProgressDownloadDialog.dismiss();

                    if (!StringUtil.isNullOrEmpty(errorMsg)) {
                        AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialogV7(RestoreBackupActivity.this,
                                errorMsg, getString(R.string.backup_restore_process_failed_title),
                                getString(R.string.std_ok), null, null, null);
                        dialog.show();
                    }
                    break;
                }
                default: {
                    LogUtil.w(this.getClass().getName(), LocalizedException.UNDEFINED_ARGUMENT);
                    finish();
                    break;
                }
            }
        }
    }

    private class ActivityState {
        int state;
        Bundle args;
    }
}
