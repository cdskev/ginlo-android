// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.service;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.ContactControllerBusiness;
import eu.ginlo_apps.ginlo.controller.contracts.HasCompanyManagementCallback;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.model.backend.ResponseModel;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.BroadcastNotifier;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.concurrent.Executor;

public class ConfigureCompanyService extends IntentService {
    private final static String TAG = "ConfigureCompanyService";

    // Defines and instantiates an object for handling status updates.
    private final BroadcastNotifier mBroadcaster = new BroadcastNotifier(this, AppConstants.BROADCAST_COMPANY_ACTION);

    private ServiceExecutor mExecutor = new ServiceExecutor();

    public ConfigureCompanyService() {
        super(TAG);
    }

    /**
     * This method is invoked on the worker thread with a request to process.
     * Only one Intent is processed at a time, but the processing happens on a
     * worker thread that runs independently from other application logic.
     * So, if this code takes a long time, it will hold up other requests to
     * the same IntentService, but it will not hold up anything else.
     * When all requests have been handled, the IntentService stops itself,
     * so you should not call {@link #stopSelf}.
     *
     * @param intent The value passed to {@link
     *               Context#startService(Intent)}.
     *               This may be null if the service is being restarted after
     *               its process has gone away; see
     *               {@link Service#onStartCommand}
     *               for details.
     */
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        try {
            mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_STARTED, null, null, null);
            SimsMeApplication app = SimsMeApplication.getInstance();

            if (app.getAccountController().isDeviceManaged()) {
                ResponseModel rm = startConfigureCompany();

                if (rm.isError) {
                    LocalizedException e = null;
                    if (rm.responseException != null) {
                        e = rm.responseException;
                    } else if (!StringUtil.isNullOrEmpty(rm.errorIdent)) {
                        e = new LocalizedException(rm.errorIdent, !StringUtil.isNullOrEmpty(rm.errorExceptionMessage) ? rm.errorExceptionMessage : rm.errorMsg);
                    }

                    mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_ERROR, rm.errorMsg, null, e);

                    return;
                }
            } else {
                String mailDomain = app.getContactController().getOwnContact().getDomain();

                if (!StringUtil.isNullOrEmpty(mailDomain)) {
                    mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_START, null, null, null);
                    final ResponseModel rm = new ResponseModel();
                    ContactController.LoadCompanyContactsListener getAddressInformationsListener = new ContactController.LoadCompanyContactsListener() {
                        @Override
                        public void onLoadSuccess() {
                            //
                        }

                        @Override
                        public void onLoadFail(String message, String errorIdent) {
                            rm.isError = true;
                            mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_ERROR, errorIdent, null, null);
                        }

                        @Override
                        public void onLoadCompanyContactsSize(int size) {
                            mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_SIZE, null, size, null);
                        }

                        @Override
                        public void onLoadCompanyContactsUpdate(int count) {
                            mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_UPDATE, null, count, null);
                        }
                    };
                    getContactControllerBusiness().getAddressInformation(getAddressInformationsListener, mExecutor);

                    if (rm.isError) {
                        return;
                    }
                }
            }
        } catch (LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
            mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_ERROR, null, null, e);
            return;
        }

        mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_FINISHED, null, null, null);
    }

    private ResponseModel startConfigureCompany()
            throws LocalizedException {
        final ResponseModel rm = new ResponseModel();
        SimsMeApplication app = SimsMeApplication.getInstance();
        boolean restartMsgTask = false;

        try {
            mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_CONNECTING, null, null, null);

            if (!hasEncryptionInfo()) {
                restartMsgTask = true;
                app.getMessageController().resetTasks();

                if (app.getAccountController().getAccountState() != Account.ACCOUNT_STATE_FULL) {
                    app.getLoginController().loginCompleteSuccess(null, null, null);
                }

                app.getMessageController().startMessageTaskSync(null, null, false, false, true);

                long wait = 0;
                while (wait < 60000) {
                    wait += 2000;
                    SystemClock.sleep(2000);
                    if (hasEncryptionInfo()) {
                        break;
                    }
                    app.getMessageController().startMessageTaskSync(null, null, false, false, true);
                }

                if (!hasEncryptionInfo()) {
                    rm.isError = true;
                    rm.errorMsg = getString(R.string.mdm_login_company_encryption_not_loaded);
                    return rm;
                }
            }

            mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_GET_CONFIG, null, null, null);

            getAccountControllerBusiness().loadCompanyInfo(new HasCompanyManagementCallback() {
                @Override
                public void onSuccess(String managementState) {
                    //
                }

                @Override
                public void onFail(String message) {
                    rm.isError = true;
                }
            }, mExecutor);

            if (rm.isError) {
                return rm;
            }

            getAccountControllerBusiness().loadCompanyMDMConfig(new GenericActionListener<Void>() {
                @Override
                public void onSuccess(Void object) {
                    //
                }

                @Override
                public void onFail(final String message, final String errorIdent) {
                    rm.isError = true;
                    rm.errorMsg = message;
                    rm.errorIdent = errorIdent;
                }
            }, mExecutor);

            if (rm.isError) {
                return rm;
            }

            getAccountControllerBusiness().resetLayout();

            //getCompanyLayout

            GenericActionListener genericActionListener = new GenericActionListener<String>() {

                @Override
                public void onSuccess(String s) {
                    //
                }

                @Override
                public void onFail(String message, String errorIdent) {
                    rm.isError = true;
                    rm.errorMsg = message;
                    rm.errorIdent = errorIdent;
                }
            };

            getAccountControllerBusiness().getCompanyLayout(genericActionListener, mExecutor);

            if (rm.isError) {
                return rm;
            }

            GenericActionListener logoListener = new GenericActionListener<Bitmap>() {

                @Override
                public void onSuccess(Bitmap object) {
                    //
                }

                @Override
                public void onFail(String message, String errorIdent) {
                    rm.isError = true;
                    rm.errorMsg = message;
                    rm.errorIdent = errorIdent;
                }
            };

            getAccountControllerBusiness().getCompanyLogo(logoListener, mExecutor);

            if (rm.isError) {
                return rm;
            }

            mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_START, null, null, null);

            ContactController.LoadCompanyContactsListener companyIndexListener = new ContactController.LoadCompanyContactsListener() {
                @Override
                public void onLoadSuccess() {
                    //
                }

                @Override
                public void onLoadFail(String message, String errorIdent) {
                    rm.isError = true;
                    rm.errorMsg = message;
                    rm.errorIdent = errorIdent;
                }

                @Override
                public void onLoadCompanyContactsSize(int size) {
                    mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_SIZE, null, size, null);
                }

                @Override
                public void onLoadCompanyContactsUpdate(int count) {
                    mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_UPDATE, null, count, null);
                }

            };

            getContactControllerBusiness().loadCompanyIndexAsync(companyIndexListener, mExecutor);

            if (rm.isError) {
                return rm;
            }

            String mailDomain = getContactControllerBusiness().getOwnContact().getDomain();

            if (!StringUtil.isNullOrEmpty(mailDomain)) {
                mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_START, null, null, null);

                ContactController.LoadCompanyContactsListener getAddressInformationsListener = new ContactController.LoadCompanyContactsListener() {
                    @Override
                    public void onLoadSuccess() {
                        //
                    }

                    @Override
                    public void onLoadFail(String message, String errorIdent) {
                        rm.isError = true;
                        rm.errorMsg = message;
                        rm.errorIdent = errorIdent;
                    }

                    @Override
                    public void onLoadCompanyContactsSize(int size) {
                        mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_SIZE, null, size, null);
                    }

                    @Override
                    public void onLoadCompanyContactsUpdate(int count) {
                        mBroadcaster.broadcastIntentWithState(AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_UPDATE, null, count, null);
                    }
                };

                getContactControllerBusiness().getAddressInformation(getAddressInformationsListener, mExecutor);
            }
        } finally {
            if (restartMsgTask && app.getAccountController().hasAccountFullState()) {
                app.getMessageController().startGetNewMessages(false);
            }
        }

        return rm;
    }

    private AccountController getAccountControllerBusiness() {
        return SimsMeApplication.getInstance().getAccountController();
    }

    private ContactControllerBusiness getContactControllerBusiness() {
        return (ContactControllerBusiness) SimsMeApplication.getInstance().getContactController();
    }

    private boolean hasEncryptionInfo()
            throws LocalizedException {
        final AccountController accountControllerBusiness = getAccountControllerBusiness();

        final String salt = accountControllerBusiness.getMcEncryptionSalt();
        final String seed = accountControllerBusiness.getMcEncryptionSeed();

        return !StringUtil.isNullOrEmpty(salt) && !StringUtil.isNullOrEmpty(seed);
    }

    private class ServiceExecutor implements Executor {

        /**
         * Executes the given command at some time in the future.  The command
         * may execute in a new thread, in a pooled thread, or in the calling
         * thread, at the discretion of the {@code Executor} implementation.
         *
         * @param command the runnable task
         * @throws java.util.concurrent.RejectedExecutionException if this task cannot be
         *                                                         accepted for execution
         * @throws NullPointerException                            if command is null
         */
        @Override
        public void execute(@NonNull Runnable command) {
            command.run();
        }
    }
}
