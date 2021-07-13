// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.sqlite.SQLiteException;
import android.hardware.fingerprint.FingerprintManager;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.UseCases.DeleteLocalBackupsUseCase;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.manager.SerialExecutor;
import eu.ginlo_apps.ginlo.concurrent.task.AsyncHttpTask;
import eu.ginlo_apps.ginlo.concurrent.task.MigrationTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.DeviceController;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.controller.KeyController.OnKeyPairsInitiatedListener;
import eu.ginlo_apps.ginlo.controller.LocalBackupHelper;
import eu.ginlo_apps.ginlo.controller.LoginController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.contracts.AccountOnServerWasDeleteListener;
import eu.ginlo_apps.ginlo.controller.contracts.AsyncBackupKeysCallback;
import eu.ginlo_apps.ginlo.controller.contracts.BackupUploadListener;
import eu.ginlo_apps.ginlo.controller.contracts.CreateBackupListener;
import eu.ginlo_apps.ginlo.controller.contracts.CreateRecoveryCodeListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnConfirmAccountListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnCreateAccountListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnDeleteAccountListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnGetPurchasedProductsListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnRequestRecoveryCodeListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnValidateConfirmCodeListener;
import eu.ginlo_apps.ginlo.controller.contracts.PhoneOrEmailActionListener;
import eu.ginlo_apps.ginlo.controller.contracts.UpdateAccountInfoCallback;
import eu.ginlo_apps.ginlo.controller.message.PrivateInternalMessageController;
import eu.ginlo_apps.ginlo.controller.models.CouplingRequestModel;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.AccountDao;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.DaoMaster;
import eu.ginlo_apps.ginlo.greendao.DaoSession;
import eu.ginlo_apps.ginlo.greendao.GreenDAOSecurityLayer;
import eu.ginlo_apps.ginlo.greendao.Preference;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.AccountModel;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.DeviceModel;
import eu.ginlo_apps.ginlo.model.backend.ResponseModel;
import eu.ginlo_apps.ginlo.model.backend.action.Action;
import eu.ginlo_apps.ginlo.model.backend.serialization.AccountModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.DeviceModelSerializer;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.BackupService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.BCrypt;
import eu.ginlo_apps.ginlo.util.ChecksumUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.Listener.GenericUpdateListener;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import eu.ginlo_apps.ginlo.util.XMLUtil;
import org.greenrobot.greendao.database.Database;
import org.json.JSONException;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

public class AccountControllerBase
        implements LoginController.AppLockLifecycleCallbacks {

    private static final String TAG = AccountControllerBase.class.getSimpleName();
    public static final String MC_RECOVERY_CODE_REQUESTED = "mc_recovery_code_requested";
    static final String RECOVERY_CODE_SET = "AccountControllerBusiness.recoveryCodeSet";
    public static final String EMAIL_ATTRIBUTES_EMAIL = "email";
    static final String EMAIL_ATTRIBUTES_FIRSTNAME_PRE_22 = "firstname";
    static final String EMAIL_ATTRIBUTES_LASTNAME_PRE_22 = "name";
    public static final String PENDING_PHONE_STATUS_WAIT_REQUEST = "AccountController.pendingPhoneNumber.wait.request";
    public static final String PENDING_PHONE_STATUS_WAIT_CONFIRM = "AccountController.pendingPhoneNumber.wait.confirm";
    public static final String MC_STATE_ACCOUNT_ACCEPTED_EMAIL_REQUIRED = "ManagedAccountAcceptedEmailRequired";
    public static final String MC_STATE_ACCOUNT_ACCEPTED_PHONE_REQUIRED = "ManagedAccountAcceptedPhoneRequired";
    public static final String MC_STATE_ACCOUNT_ACCEPTED_PHONE_FAILED = "ManagedAccountAcceptedPhoneFailed";
    public static final String MC_STATE_ACCOUNT_ACCEPTED_EMAIL_FAILED = "ManagedAccountAcceptedEmailFailed";
    /**
     * Migrationsversion, wenn fachliche Aktualisierungstätigkeiten beim Update der App durchgeführt werden soll, muss die Version erhöht werden.
     * <p>
     * Bei einer Neuinstallation werden diese nicht durchgeführt.
     */
    public static final int ACCOUNT_MIGRATION_VERSION = 3;

    protected static final String PENDING_PHONE_NUMBER = "AccountController.pendingPhoneNumber";
    protected static final String PENDING_PHONE_STATUS = "AccountController.pendingPhoneStatus";
    private static final String ONLINE_STATE_TYPING = "AccountController.onlineStateTyping";
    private static final String ONLINE_STATE_ONLINE = "AccountController.onlineStateOnline";

    private static final String MSG_EXCEPTION = "MsgException";
    private static final String MESSAGE = "message";
    private static final String EMAIL_ATTRIBUTES_PRE_22 = "AccountControllerBusiness.eMailAttributes";
    private static final String PENDING_PHONE_STATUS_NONE = "AccountController.pendingPhoneNumber.none";
    private static final String PENDING_PHONE_STATUS_CONFIRM_FAILED = "AccountController.pendingPhoneNumber.confirm.failed";
    private static final String PENDING_PHONE_STATUS_CONFIRMED = "AccountController.pendingPhoneNumber.confirmed";

    private static final SerialExecutor ONLINE_STATE_EXECUTOR = new SerialExecutor();
    private static AsyncTask<Void, Void, LocalizedException> mCoupleDeviceRequestCouplingTask;

    protected final AccountDao accountDao;
    final SimsMeApplication mApplication;
    private final Resources resources;
    protected Account mAccount;
    Gson gson;
    protected CreateAccountModel mCreateAccountModel;
    private CountDownTimer mCountDownTimer;
    private String password;
    private boolean accountLoaded;
    private CoupleDeviceModel mCoupleDeviceModel;
    private boolean mIsBackupStarted;
    private AccountOnServerWasDeleteListener mAccountDeleteListener;
    private List<BackupUploadListener> mBackupUploadListeners;

    private AsyncTask<Void, Void, LocalizedException> mCoupleDeviceCreateDeviceTask;
    private AsyncTask<Void, Void, LocalizedException> mCoupleDeviceGetCouplingTask;
    private MigrationTask mMigrationTask;
    private GenericUpdateListener<Integer> mMigrationListener;
    // Eigenes TempDevices
    private String mTempDeviceGuid;
    private String mTempDevicePublicKeyXML;
    // Coupling Request
    private CouplingRequestModel couplingRequestModel;
    private AsyncTask<Void, Void, LocalizedException> mCoupleGetCouplingRequestTask;
    private SendOnlineStateTask mSendOnlineStateTask;

    protected AccountControllerBase(final SimsMeApplication application) {
        //  mBackupListeners = new ArrayList<>();
        mSendOnlineStateTask = new SendOnlineStateTask();

        mApplication = application;
        resources = application.getResources();

        Database db = mApplication.getDataBase();

        DaoMaster daoMaster = new DaoMaster(db);
        DaoSession daoSession = daoMaster.newSession();

        accountDao = daoSession.getAccountDao();

        registerJsonAdapters(); // Overridable method 'registerJsonAdapters' called during object construction == Absicht

        loadAccount();

        // The filter's action is BROADCAST_ACTION
        IntentFilter statusIntentFilter = new IntentFilter(AppConstants.BROADCAST_ACTION);

        // Sets the filter's category to DEFAULT
        statusIntentFilter.addCategory(Intent.CATEGORY_DEFAULT);

        // Instantiates a new BackupStateReceiver
        final BackupStateReceiver backupStateReceiver = new BackupStateReceiver();

        // Registers the Receiver
        LocalBroadcastManager.getInstance(mApplication)
                .registerReceiver(backupStateReceiver, statusIntentFilter);

        mApplication.getLoginController().registerAppLockLifecycleCallbacks(this);
    }

    protected void registerJsonAdapters() {
        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(AccountModel.class, new AccountModelSerializer());
        gsonBuilder.registerTypeAdapter(DeviceModel.class, new DeviceModelSerializer());

        gson = gsonBuilder.create();
    }

    Resources getResources() {
        return resources;
    }

    public void deleteAccount(final OnDeleteAccountListener onDeleteAccountListener) {
        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {

                if (!response.isError) {
                    //TODO : Not sure about how to use dependency injection here so creating object of the use case here
                    DeleteLocalBackupsUseCase deleteLocalBackupsUseCase = new DeleteLocalBackupsUseCase();
                    deleteLocalBackupsUseCase.deleteLocalBackups();

                    if (BuildConfig.DEBUG) {
                        onDeleteAccountListener.onDeleteAccountSuccess();
                    }
                    AccountControllerBase.this.deleteAccount();

                } else {
                    onDeleteAccountListener.onDeleteAccountFail(response.errorMessage);
                }
            }
        };
        BackendService.withAsyncConnection(mApplication)
                .deleteAccount(listener);
    }

    public void deleteAccount() {
        mApplication.safeDeleteAccount();
    }

    public void setAccountOnServerWasDeleteListener(AccountOnServerWasDeleteListener listener) {
        mAccountDeleteListener = listener;
    }

    public void removeAccountOnServerWasDeleteListener(AccountOnServerWasDeleteListener listener) {
        if (mAccountDeleteListener != null && mAccountDeleteListener.equals(listener)) {
            mAccountDeleteListener = null;
        }
    }

    public void ownAccountWasDeleteOnServer() {
        if (mAccountDeleteListener != null) {
            mAccountDeleteListener.onOwnAccountWasDeleteOnServer();
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void clearPassword() {
        password = null;
    }

    @Nullable
    public String getCreateAccountPhoneNumber() {
        if (mCreateAccountModel != null) {
            return mCreateAccountModel.normalizedPhoneNumber;
        } else if (mAccount != null) {
            return mAccount.getPhoneNumber();
        }

        return null;
    }

    public void createAccountSetPassword(@NonNull final String password,
                                         final boolean isSimplePassword,
                                         final boolean isPwdOnStartEnabled) {
        mCreateAccountModel = new CreateAccountModel();
        mCreateAccountModel.password = password;
        mCreateAccountModel.isSimplePassword = isSimplePassword;
        mCreateAccountModel.isPwdOnStartEnabled = isPwdOnStartEnabled;
    }

    public void resetCreateAccountSetPassword() {
        mCreateAccountModel = null;
    }

    public void createAccountRegisterPhone(@Nullable final String normalizedPhoneNumber,
                                           @Nullable final String emailAdress,
                                           @NonNull final OnCreateAccountListener createAccountListener) {
        if (mCreateAccountModel == null) {
            createAccountListener.onCreateAccountFail(null, true);
            return;
        }

        mCreateAccountModel.normalizedPhoneNumber = normalizedPhoneNumber;
        mCreateAccountModel.emailAddress = emailAdress;

        OnKeyPairsInitiatedListener listener = new OnKeyPairsInitiatedListener() {
            @Override
            public void onKeyPairsInitiated() {
                PreferencesController preferencesController = mApplication.getPreferencesController();
                preferencesController.clearAll();
                preferencesController.getSharedPreferences().edit().clear().apply();
                createAccountRegisterPhoneInternal(createAccountListener);
            }

            @Override
            public void onKeyPairsInitiatedFailed() {
                resetCreateAccountRegisterPhone();
                createAccountListener.onCreateAccountFail(null, false);
            }
        };
        mApplication.getKeyController().initKeys(listener);
    }

    private void createAccountRegisterPhoneInternal(
            @NonNull final OnCreateAccountListener createAccountListener) {
        // TODO gyan what is this check for? mBackendService should never be null
//        if (mBackendService == null) {
//            resetCreateAccountRegisterPhone();
//            createAccountListener.onCreateAccountFail(null, false);
//            return;
//        }
        try {
            String accountGuid = GuidUtil.generateAccountGuid();
            String deviceGuid = GuidUtil.generateDeviceGuid();

            mCreateAccountModel.accountDaoModel = new Account(null,
                    mCreateAccountModel.normalizedPhoneNumber,
                    deviceGuid, accountGuid, null, null,
                    null, null,
                    Account.ACCOUNT_STATE_NO_ACCOUNT, null);

            final String refCountryFlag = Locale.getDefault().getLanguage();

            mCreateAccountModel.accountModel = createAccountModel(accountGuid,
                    mCreateAccountModel.normalizedPhoneNumber, mCreateAccountModel.emailAddress);
            mCreateAccountModel.deviceModel = createDeviceModel(deviceGuid, refCountryFlag,
                    mCreateAccountModel.accountModel);

            IBackendService.OnBackendResponseListener listener = createOnBackendResponseListenerForAccountCreation(createAccountListener, Account.ACCOUNT_STATE_NOT_CONFIRMED);

            JsonArray jsonArray = new JsonArray();

            jsonArray.add(gson.toJsonTree(mCreateAccountModel.accountModel));
            jsonArray.add(gson.toJsonTree(mCreateAccountModel.deviceModel));

            String dataJson = jsonArray.toString();

            BackendService.withAsyncConnection(mApplication)
                    .createAccountEx(dataJson, mCreateAccountModel.accountModel.phone,
                            mCreateAccountModel.deviceModel.language, null, null, listener);
        } catch (LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
            resetCreateAccountRegisterPhone();

            createAccountListener.onCreateAccountFail(null, false);
        }
    }

    IBackendService.OnBackendResponseListener createOnBackendResponseListenerForAccountCreation(@NonNull final OnCreateAccountListener createAccountListener, @NonNull final Integer nextAccountState) {
        return new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    LogUtil.i(TAG, "Create account request failed.");

                    String errorMsg = mApplication.getResources()
                            .getString(R.string.service_tryAgainLater);

                    if (response.errorMessage != null) {
                        errorMsg = response.errorMessage;
                    }

                    resetCreateAccountRegisterPhone();
                    createAccountListener.onCreateAccountFail(errorMsg, false);
                } else {
                    try {
                        LogUtil.i(TAG, "Create account request succeeded.");

                        if (response.jsonArray != null) {
                            List<String> serverAccountIds = new ArrayList<>(response.jsonArray.size());
                            for (JsonElement je : response.jsonArray) {
                                JsonObject accountJO = JsonUtil.searchJsonObjectRecursive(je, JsonConstants.ACCOUNT);
                                if (accountJO != null) {
                                    String accountGuid = JsonUtil.stringFromJO(JsonConstants.GUID, accountJO);

                                    if (StringUtil.isNullOrEmpty(accountGuid)) {
                                        continue;
                                    }

                                    String accountId = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, accountJO);
                                    if (StringUtil.isNullOrEmpty(accountId)) {
                                        continue;
                                    }

                                    if (StringUtil.isEqual(mCreateAccountModel.accountDaoModel.getAccountGuid(), accountGuid)) {
                                        mCreateAccountModel.accountDaoModel.setAccountID(accountId);
                                    } else {
                                        serverAccountIds.add(accountId);
                                    }
                                }
                            }
                            //Server gibt mehr als eine Guid zurueck, d.h. auf dem Server gibt es noch min. ein weiteren Account

                            if (serverAccountIds.size() > 0) {
                                mCreateAccountModel.allServerAccountIDs = serverAccountIds;
                            }
                        }

                        // TODO gyan whatis this setting for?
//                        mBackendService.elevateRights(mCreateAccountModel.deviceModel.guid,
//                                mCreateAccountModel.accountModel.guid,
//                                mCreateAccountModel.deviceModel.passtoken);

                        saveNotConfirmedAccount(createAccountListener, nextAccountState);
                        mApplication.getDeviceController().createDeviceFromModel(mCreateAccountModel.deviceModel, true);
                    } catch (LocalizedException e) {
                        resetCreateAccountRegisterPhone();
                        createAccountListener.onCreateAccountFail(mApplication.getString(R.string.service_tryAgainLater), false);
                    }
                }
            }
        };
    }

    void getPurchasedProducts(final OnGetPurchasedProductsListener onGetPurchasedProductsListener) {
        // wird in der AccountControllerBusiness ueberschrieben
    }

    private void saveNotConfirmedAccount(
            @NonNull final OnCreateAccountListener createAccountListener, @NonNull final Integer nextAccountState) {
        saveOrUpdateAccount(mCreateAccountModel.accountDaoModel);

        eu.ginlo_apps.ginlo.controller.KeyController.OnKeysSavedListener keysSavedListener = new eu.ginlo_apps.ginlo.controller.KeyController.OnKeysSavedListener() {
            @Override
            public void onKeysSaveFailed() {
                resetCreateAccountRegisterPhone();

                LogUtil.i(TAG, "Keys save failed.");

                createAccountListener.onCreateAccountFail(null, false);
            }

            @Override
            public void onKeysSaveComplete() {
                try {
                    LogUtil.i(TAG, "Keys saved successful.");

                    mAccount.setPasstoken(mCreateAccountModel.deviceModel.passtoken);
                    if (mCreateAccountModel.allServerAccountIDs != null) {
                        mAccount.setAllServerAccountIDs(StringUtil.getStringFromList(",", mCreateAccountModel.allServerAccountIDs));
                    }
                    mAccount.setState(nextAccountState);

                    saveOrUpdateAccount(mAccount);
                    //eigenen Kontakt anlegen
                    Contact ownContact = new Contact();
                    mApplication.getContactController().fillOwnContactWithAccountInfos(ownContact);
                    //mit E-Mail Adresse angelegt
                    if (!StringUtil.isNullOrEmpty(mCreateAccountModel.emailAddress)) {
                        ownContact.setEmail(mCreateAccountModel.emailAddress);
                        mApplication.getContactController().insertOrUpdateContact(ownContact);
                    }

                    createAccountListener.onCreateAccountSuccess();
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                    resetCreateAccountRegisterPhone();
                    createAccountListener.onCreateAccountFail(null, false);
                }
            }
        };
        try {
            initInternalSecurityAndSavePasswordSettings(mCreateAccountModel.isPwdOnStartEnabled, mCreateAccountModel.isSimplePassword, mCreateAccountModel.password);
            mApplication.getKeyController().saveKeys(mCreateAccountModel.password, keysSavedListener);
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    private void initInternalSecurityAndSavePasswordSettings(final boolean isPwdOnStartEnabled, final boolean isSimplePassword, @NonNull final String password)
            throws LocalizedException {
        GreenDAOSecurityLayer.init(mApplication.getKeyController());

        PreferencesController preferencesController = mApplication.getPreferencesController();
        preferencesController.getPreferences();

        preferencesController.setPasswordEnabled(isPwdOnStartEnabled);
        preferencesController.setPasswordType(isSimplePassword ? Preference.TYPE_PASSWORD_SIMPLE : Preference.TYPE_PASSWORD_COMPLEX);

        preferencesController.onPasswordChanged(password);
    }

    public void resetCreateAccountRegisterPhone() {
        if (mCreateAccountModel == null) {
            resetAppData();
            return;
        }

        if (mAccount == null && mCreateAccountModel.accountDaoModel != null &&
                mCreateAccountModel.accountDaoModel.getId() != null) {
            synchronized (accountDao) {
                accountDao.delete(mCreateAccountModel.accountDaoModel);
            }
        }

        mCreateAccountModel.accountDaoModel = null;
        mCreateAccountModel.accountModel = null;
        mCreateAccountModel.deviceModel = null;
        mCreateAccountModel.normalizedPhoneNumber = null;
        mCreateAccountModel.emailAddress = null;
        mCreateAccountModel.allServerAccountIDs = null;

        resetAppData();
    }

    public void createAccountValidateConfirmCode(@NonNull final String confirmCode,
                                                 @NonNull final OnValidateConfirmCodeListener confirmListener) {
        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    if (StringUtil.isEqual(response.errorMessage, "")) {
                        if (response.jsonObject != null) {
                            if (response.jsonObject.has(MSG_EXCEPTION)) {
                                JsonObject exception = response.jsonObject.get(MSG_EXCEPTION)
                                        .getAsJsonObject();

                                if (exception.has(MESSAGE)) {
                                    confirmListener.onValidateConfirmCodeFail(
                                            exception.get(MESSAGE).getAsString());
                                }
                            }
                        }
                    } else {
                        confirmListener.onValidateConfirmCodeFail(response.errorMessage);
                    }
                } else {
                    LogUtil.i(TAG, "check confirm code successful.");

                    mAccount.setState(Account.ACCOUNT_STATE_VALID_CONFIRM_CODE);

                    saveOrUpdateAccount(mAccount);

                    confirmListener.onValidateConfirmCodeSuccess();
                }
            }
        };
        BackendService.withAsyncConnection(mApplication)
                .isConfirmationValid(confirmCode, listener);
    }

    public void resetCreateAccountValidateConfirmCode() {
        if (mAccount != null) {
            mAccount.setState(Account.ACCOUNT_STATE_NOT_CONFIRMED);
            saveOrUpdateAccount(mAccount);
        }
    }

    public void resetRegistrationProcess() {
        deleteAccount();
    }

    public void createAccountConfirmAccount(String confirmCode,
                                            final OnConfirmAccountListener confirmListener) {
        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    if (StringUtil.isEqual(response.errorMessage, "")) {
                        if (response.jsonObject != null) {
                            if (response.jsonObject.has(MSG_EXCEPTION)) {
                                JsonObject exception = response.jsonObject.get(MSG_EXCEPTION)
                                        .getAsJsonObject();

                                if (exception.has(MESSAGE)) {
                                    confirmListener
                                            .onConfirmAccountFail(exception.get(MESSAGE).getAsString());
                                    return;
                                }
                            }
                        }
                        confirmListener.onConfirmAccountFail("");
                    } else {
                        confirmListener.onConfirmAccountFail(response.errorMessage);
                    }
                } else {
                    LogUtil.i(TAG, "Confirm account successful.");

                    if (response.jsonObject != null) {
                        //Account ID auslesen
                        if (JsonUtil.hasKey(JsonConstants.ACCOUNT, response.jsonObject)) {
                            JsonObject accountJO = response.jsonObject.getAsJsonObject(JsonConstants.ACCOUNT);

                            if (JsonUtil.hasKey(JsonConstants.ACCOUNT_ID, accountJO)) {
                                String accountID = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, accountJO);

                                mAccount.setAccountID(accountID);
                            }
                        }
                    }

                    setAccountStateToConfirmed();
                    configureNewAccount();
                    confirmListener.onConfirmAccountSuccess();
                }
            }
        };
        BackendService.withAsyncConnection(mApplication)
                .confirmAccount(confirmCode, listener);
    }

    public void setAccountStateToConfirmed() {
        mAccount.setState(Account.ACCOUNT_STATE_CONFIRMED);
        saveOrUpdateAccount(mAccount);

    }

    public void configureNewAccount() {

        PreferencesController preferencesController = mApplication.getPreferencesController();
        preferencesController.setFirstBackupAfterCreateAccount();
        //neuer Account -> kein merge
        preferencesController.setHasOldContactsMerged();
        preferencesController.setMigrationVersion(ACCOUNT_MIGRATION_VERSION);

        //set login state to STATE_LOGGED_IN
        mApplication.getLoginController().loginCompleteSuccess(null, null, null);

        // KS: Set optInState on backend but do nothing locally - keep that for now!
        setOptInState("auto", new OptInStateListener() {
            @Override
            public void optInStateSuccess() {
                LogUtil.i(TAG, "Set OPTIN_STATE on backend to 'auto'.");
            }

            @Override
            public void optInStateFailed() {
                LogUtil.e(TAG, "Could not set OPTIN_STATE on backend to 'auto'.");
            }
        });

        try {
            mApplication.getContactController().createAndFillFtsDB(true);
            preferencesController.setNotificationPreviewEnabled(true, true);
            preferencesController.setSendProfileNameSet();
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    public void requestConfirmPhone(final String newPhoneNumber, final boolean force, final PhoneOrEmailActionListener listener) {
        final IBackendService.OnBackendResponseListener responseListener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(final BackendResponse response) {
                try {
                    if (!response.isError) {
                        final String result;
                        if (response.jsonArray != null && response.jsonArray.size() != 0) {
                            result = response.jsonArray.get(0).getAsString();

                            if (mAccount.getAccountGuid().equals(response.jsonArray.get(0).getAsString())) {
                                if (listener != null) {
                                    listener.onSuccess(result);
                                }
                                mAccount.setCustomStringAttribute(PENDING_PHONE_NUMBER, newPhoneNumber);
                                mAccount.setCustomStringAttribute(PENDING_PHONE_STATUS, PENDING_PHONE_STATUS_WAIT_CONFIRM);
                                saveOrUpdateAccount(mAccount);
                            }
                            //
                            else {
                                // rueckgabewert stimmt nicht mit guid ueberein -> sollte nicht auftreten
                                if (listener != null) {
                                    listener.onFail(result, false);
                                }
                                LogUtil.w(TAG, "requestConfirmPhone failed");
                            }
                        }
                    } else {
                        if (response.msgException == null || StringUtil.isNullOrEmpty(response.msgException.getIdent())) {
                            if (listener != null) {
                                listener.onFail(getResources().getString(R.string.service_tryAgainLater), false);
                            }
                            LogUtil.w(TAG, "requestConfirmPhone failed");
                            return;
                        }

                        if (listener != null) {
                            if (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0077")) {
                                // aussen behandeln
                                listener.onFail(response.msgException.getIdent(), false);
                            } else if (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0123")) {
                                listener.onFail(getResources().getString(R.string.service_ERR_0123), false);
                            } else if (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0124")) {
                                listener.onFail(response.msgException.getIdent(), false);
                            } else if (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0128")) {
                                listener.onFail(getResources().getString(R.string.service_ERR_0128), false);
                            } else if (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0130")) {
                                listener.onFail(getResources().getString(R.string.service_ERR_0130), false);
                            } else if (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0150")) {
                                listener.onFail(getResources().getString(R.string.service_ERR_0150), false);
                            } else if (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0153")) {
                                listener.onFail(getResources().getString(R.string.service_ERR_0153), false);
                            } else if (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0154")) {
                                listener.onFail(getResources().getString(R.string.service_ERR_0154), false);
                            } else {
                                listener.onFail(getResources().getString(R.string.change_phone_number_error), false);
                            }
                        }
                    }
                } catch (final LocalizedException e) {
                    if (listener != null) {
                        listener.onFail(getResources().getString(R.string.change_phone_number_error), false);
                    }
                    LogUtil.w(TAG, "requestConfirmPhone failed", e);
                }
            }
        };
        BackendService.withAsyncConnection(mApplication)
                .requestConfirmPhone(newPhoneNumber, force, responseListener);
    }

    public void confirmConfirmPhone(final String confirmationCode, final PhoneOrEmailActionListener listener) {
        final IBackendService.OnBackendResponseListener responseListener = new IBackendService.OnBackendResponseListener() {

            @Override
            public void onBackendResponse(final BackendResponse response) {
                if (!response.isError) {
                    if (mAccount.getAccountGuid().equals(response.jsonArray.get(0).getAsString())) {
                        try {
                            final String pendingPhoneNumber = mAccount.getCustomStringAttribute(PENDING_PHONE_NUMBER);
                            if (StringUtil.isNullOrEmpty(pendingPhoneNumber)) {
                                mAccount.setCustomStringAttribute(PENDING_PHONE_STATUS, PENDING_PHONE_STATUS_CONFIRM_FAILED);
                                if (listener != null) {
                                    listener.onFail(getResources().getString(R.string.change_phone_number_error), false);
                                }
                                LogUtil.w(TAG, "confirmConfirmPhone failed");
                            } else {
                                mAccount.setPhoneNumber(pendingPhoneNumber);
                                mAccount.setCustomStringAttribute(PENDING_PHONE_STATUS, PENDING_PHONE_STATUS_CONFIRMED);

                                Contact ownContact = mApplication.getContactController().getOwnContact();
                                mApplication.getContactController().saveContactInformation(ownContact, null, null, pendingPhoneNumber, null, null, null, null, null, -1, false);

                                if (listener != null) {
                                    listener.onSuccess(getResources().getString(R.string.change_phone_number_success));
                                }
                            }
                            saveOrUpdateAccount(mAccount);
                        } catch (final LocalizedException e) {
                            if (listener != null) {
                                listener.onFail(getResources().getString(R.string.change_phone_number_error), false);
                            }
                            LogUtil.w(TAG, "confirmConfirmPhone failed", e);
                        }
                    } else {
                        if (listener != null) {
                            listener.onFail(getResources().getString(R.string.change_phone_number_error), false);
                        }
                    }
                } else //iserror
                {
                    if (listener != null) {
                        listener.onFail(getResources().getString(R.string.change_phone_number_error), false);
                    }
                    LogUtil.w(TAG, "confirmConfirmPhone failed");
                }
            }
        };
        BackendService.withAsyncConnection(mApplication)
                .confirmConfirmPhone(confirmationCode, responseListener);
    }

    public void unsetPendingPhoneStatus()
            throws LocalizedException {
        mAccount.setCustomStringAttribute(PENDING_PHONE_NUMBER, "");
        mAccount.setCustomStringAttribute(PENDING_PHONE_STATUS, PENDING_PHONE_STATUS_NONE);
        saveOrUpdateAccount(mAccount);
    }

    public String getPendingPhoneStatus()
            throws LocalizedException {
        return mAccount.getCustomStringAttribute(PENDING_PHONE_STATUS);
    }

    public String getPendingPhoneNumber()
            throws LocalizedException {
        return mAccount.getCustomStringAttribute(PENDING_PHONE_NUMBER);
    }

    public void removeConfirmedPhone(final PhoneOrEmailActionListener phoneOrEmailActionListener) {
        try {
            final String phoneNumber = mAccount.getPhoneNumber();
            final String emailAddress = mApplication.getContactController().getOwnContact().getEmail();
            if (StringUtil.isNullOrEmpty(phoneNumber)) {
                phoneOrEmailActionListener.onFail(getResources().getString(R.string.remove_phone_number_error_empty_phone), false);
                return;
            }

            if (StringUtil.isNullOrEmpty(emailAddress)) {
                phoneOrEmailActionListener.onFail(getResources().getString(R.string.remove_phone_number_error_empty_mail), false);
                return;
            }

            final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
                @Override
                public void onBackendResponse(final BackendResponse response) {
                    if (response.isError) {
                        if (response.msgException != null) {
                            phoneOrEmailActionListener.onFail(response.msgException.toString(), false);
                        } else {
                            phoneOrEmailActionListener.onFail(getResources().getString(R.string.remove_phone_number_error), false);
                        }
                    } else {
                        if (response.jsonArray != null
                                && response.jsonArray.size() > 0
                                && (mAccount.getAccountGuid().equals(response.jsonArray.get(0).getAsString()))) {
                            mAccount.setPhoneNumber(null);
                            final Contact ownContact;
                            try {
                                ownContact = mApplication.getContactController().getOwnContact();
                                mApplication.getContactController().saveContactInformation(ownContact, null, null, "", null, null, null, null, null, -1, false);
                            } catch (LocalizedException e) {
                                phoneOrEmailActionListener.onFail(getResources().getString(R.string.remove_phone_number_error), false);
                                LogUtil.w(TAG, "removeConfirmedPhone failed", e);
                            }

                            saveOrUpdateAccount(mAccount);
                            phoneOrEmailActionListener.onSuccess(null);
                        } else {
                            phoneOrEmailActionListener.onFail(getResources().getString(R.string.remove_phone_number_error), false);
                            LogUtil.w(TAG, "removeConfirmedPhone failed");
                        }
                    }
                }
            };
            BackendService.withAsyncConnection(mApplication)
                    .removeConfirmedPhone(phoneNumber, listener);

        } catch (final LocalizedException e) {
            LogUtil.w(TAG, "removeConfirmedPhone failed", e);
        }
    }

    private void setTempDeviceInfo(String guid, String publicKey) {
        mTempDeviceGuid = guid;
        mTempDevicePublicKeyXML = publicKey;
    }

    private void removeTempDeviceInfo() {
        mTempDeviceGuid = null;
        mTempDevicePublicKeyXML = null;
    }

    public String getTempDeviceGuid() {
        return mTempDeviceGuid;
    }

    public String getTempDevicePublicKeyXML() {
        return mTempDevicePublicKeyXML;
    }

    public void fetchOwnTempDevice() {
        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                try {
                    if (response == null || response.jsonObject == null) {
                        return;
                    }
                    if (response.isError) {
                        return;
                    }

                    JsonObject responseJSON = response.jsonObject;
                    JsonObject accountObject = responseJSON.get(JsonConstants.ACCOUNT).getAsJsonObject();

                    if (accountObject.has("tempDeviceGuid") && accountObject.has("publicKeyTempDevice") && accountObject.has("pkSign256TempDevice")) {
                        //  Signature prüfen
                        String tempDeviceGuid = accountObject.get("tempDeviceGuid").getAsString();
                        String publicKeyTempDevice = accountObject.get("publicKeyTempDevice").getAsString();
                        String pkSign256TempDevice = accountObject.get("pkSign256TempDevice").getAsString();

                        PublicKey pubKey = XMLUtil.getPublicKeyFromXML(mAccount.getPublicKey());
                        try {
                            if (SecurityUtil.verifyData(pubKey, Base64.decode(pkSign256TempDevice, Base64.DEFAULT), publicKeyTempDevice.getBytes("utf-8"), true)) {
                                setTempDeviceInfo(tempDeviceGuid, publicKeyTempDevice);
                            }
                        } catch (IOException ex) {
                            LogUtil.e(TAG, ex.getMessage(), ex);
                        }
                    } else {
                        removeTempDeviceInfo();
                    }
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                }
            }
        };

        BackendService.withAsyncConnection(mApplication)
                .getAccountInfo(mAccount.getAccountGuid(), 1, false, false, true, listener);
    }

    /**
     * Couple Device  Step 1
     * Set Password for new device
     *
     * @param password            - festgelegtes Passwort
     * @param isSimplePassword    - komplexes oder einfaches(PIN) Passwort
     * @param isPwdOnStartEnabled - muss Passwort beim Start von SIMSme eingegeben werden
     */
    public void coupleDeviceSetPassword(@NonNull final String password,
                                        final boolean isSimplePassword,
                                        final boolean isPwdOnStartEnabled) {
        mCoupleDeviceModel = new CoupleDeviceModel();
        mCoupleDeviceModel.password = password;
        mCoupleDeviceModel.isSimplePassword = isSimplePassword;
        mCoupleDeviceModel.isPwdOnStartEnabled = isPwdOnStartEnabled;
    }

    public void coupleDeviceSetDeviceName(@NonNull final String deviceName)
            throws LocalizedException {
        if (mCoupleDeviceModel == null) {
            throw new LocalizedException(LocalizedException.DEVICE_MODEL_UNKNOWN, "model == null");
        }
        mCoupleDeviceModel.deviceName = deviceName;
    }

    public void coupleDeviceSearchAccount(@NonNull final String searchText, @NonNull final String searchType, @NonNull final GenericActionListener<String> actionListener)
            throws LocalizedException {
        final AsyncHttpTask<String> coupleDeviceSearchTask = new AsyncHttpTask<>(new AsyncHttpTask.AsyncHttpCallback<String>() {
            @Override
            public void asyncLoaderServerRequest(IBackendService.OnBackendResponseListener listener) {
                String searchTextTmp = searchText;
                if (StringUtil.isEqual(searchType, JsonConstants.SEARCH_TYPE_PHONE) || StringUtil.isEqual(searchType, JsonConstants.SEARCH_TYPE_EMAIL)) {
                    searchTextTmp = BCrypt.hashpw(searchText.toLowerCase(Locale.US), BuildConfig.SERVER_SALT);
                }

                BackendService.withSyncConnection(mApplication)
                        .getAccountInfoAnonymous(searchTextTmp, searchType, listener);
            }

            @Override
            public String asyncLoaderServerResponse(BackendResponse response)
                    throws LocalizedException {
                if (response.jsonArray == null || response.jsonArray.size() < 1) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response is null");
                }

                JsonElement je = response.jsonArray.get(0);

                if (!je.isJsonObject()) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response has wrong format");
                }

                JsonObject jo = je.getAsJsonObject();

                if (!jo.has(JsonConstants.ACCOUNT)) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response has wrong format");
                }

                JsonObject accountObject = jo.get(JsonConstants.ACCOUNT).getAsJsonObject();

                mCoupleDeviceModel.accountModel = new AccountModel();

                String accountID = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, accountObject);
                if (StringUtil.isNullOrEmpty(accountID)) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Account Id is null");
                }
                mCoupleDeviceModel.accountModel.accountID = accountID;

                String guid = JsonUtil.stringFromJO(JsonConstants.GUID, accountObject);
                if (StringUtil.isNullOrEmpty(guid)) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Guid is null");
                }
                mCoupleDeviceModel.accountModel.guid = guid;

                String publicKey = JsonUtil.stringFromJO(JsonConstants.PUBLIC_KEY, accountObject);
                if (StringUtil.isNullOrEmpty(publicKey)) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Public Key is null");
                }
                mCoupleDeviceModel.accountModel.publicKey = publicKey;

                return guid;
            }

            @Override
            public void asyncLoaderFinished(String result) {
                actionListener.onSuccess(result);
            }

            @Override
            public void asyncLoaderFailed(String errorMessage) {
                actionListener.onFail(errorMessage, null);
            }
        });
        coupleDeviceSearchTask.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }

    //die Tasks werden vom controller selbst gestartet.
    //dieser ist ueber die gesamte Laufzeit aktiv, wird also niemals garbagecollected
    public void coupleDeviceRequestCoupling(@NonNull final String tan, @NonNull final GenericActionListener<Void> actionListener) {
        if (mCoupleDeviceRequestCouplingTask != null) {
            return;
        }

        mCoupleDeviceRequestCouplingTask = new AsyncTask<Void, Void, LocalizedException>() {

            @Override
            protected LocalizedException doInBackground(Void... params) {
                try {
                    if (mCoupleDeviceModel == null) {
                        throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "missing data for server call");
                    }

                    mApplication.getKeyController().createDeviceKeysSync();

                    mCoupleDeviceModel.deviceModel = new DeviceModel();
                    mCoupleDeviceModel.deviceModel.guid = GuidUtil.generateDeviceGuid();

                    if (mCoupleDeviceModel.accountModel == null || StringUtil.isNullOrEmpty(mCoupleDeviceModel.accountModel.publicKey) || StringUtil.isNullOrEmpty(mCoupleDeviceModel.accountModel.guid)) {
                        throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Account Data is null");
                    }

                    String transId = generateTransId(tan, mCoupleDeviceModel.accountModel.publicKey, mCoupleDeviceModel.accountModel.guid);

                    if (StringUtil.isNullOrEmpty(mCoupleDeviceModel.deviceName)) {
                        throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "Device name is null");
                    }
                    String encodedDeviceName = Base64.encodeToString(mCoupleDeviceModel.deviceName.getBytes(Encoding.UTF8), Base64.NO_WRAP);
                    JsonObject deviceDataJO = new JsonObject();
                    deviceDataJO.addProperty(JsonConstants.DEVICE_NAME, encodedDeviceName);
                    deviceDataJO.addProperty(JsonConstants.DEVICE_GUID, mCoupleDeviceModel.deviceModel.guid);
                    deviceDataJO.addProperty(JsonConstants.DEVICE_OS, AppConstants.OS);

                    String appData = deviceDataJO.toString();

                    String publicKeyString = XMLUtil.getXMLFromPublicKey(mApplication.getKeyController().getDeviceKeyPair().getPublic());
                    if (StringUtil.isNullOrEmpty(publicKeyString)) {
                        throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "Public Key String is null");
                    }

                    String concatSignatureVerify = tan + transId + publicKeyString;
                    byte[] checksumData = ChecksumUtil.getSHA256ChecksumAsBytesForString(concatSignatureVerify);

                    PublicKey pubKey = XMLUtil.getPublicKeyFromXML(mCoupleDeviceModel.accountModel.publicKey);
                    if (pubKey == null) {
                        throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "Public Key is null");
                    }

                    byte[] encryptedVerify = SecurityUtil.encryptMessageWithRSA(checksumData, pubKey);
                    String encryptedVerifyBase64 = Base64.encodeToString(encryptedVerify, Base64.NO_WRAP);

                    String requestType = "0x0101";
                    String concatSignature = mCoupleDeviceModel.accountModel.guid + transId + publicKeyString + encryptedVerifyBase64 + requestType + appData;

                    byte[] signBytes = SecurityUtil.signData(mApplication.getKeyController().getDeviceKeyPair().getPrivate(), concatSignature.getBytes(Encoding.UTF8), true);
                    if (signBytes == null) {
                        throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "signature bytes are null");
                    }

                    String signBase64 = Base64.encodeToString(signBytes, Base64.DEFAULT);

                    ResponseModel rm = requestCoupling(mCoupleDeviceModel.accountModel.guid, transId, publicKeyString, encryptedVerifyBase64, requestType, appData, signBase64);

                    if (rm.isError) {
                        throw new LocalizedException(rm.errorIdent, rm.errorMsg);
                    }

                    if (rm.responseException != null) {
                        throw rm.responseException;
                    }

                    if (rm.response.isJsonNull() || !rm.response.isJsonPrimitive()) {
                        throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "Server response is not correct");
                    }
                    mCoupleDeviceModel.couplingTransactionID = rm.response.getAsString();
                } catch (UnsupportedEncodingException ue) {
                    return new LocalizedException(LocalizedException.UNSUPPORTED_ENCODING_EXCEPTION, ue.getMessage(), ue);
                } catch (LocalizedException e) {
                    return e;
                }

                return null;
            }

            @Override
            protected void onPostExecute(LocalizedException e) {
                mCoupleDeviceRequestCouplingTask = null;
                if (e == null) {
                    actionListener.onSuccess(null);
                } else {
                    LogUtil.w(TAG, "coupleDeviceRequestCoupling failed", e);
                    resetCoupleDevice();
                    actionListener.onFail("", e.getIdentifier());
                }
            }
        };
        mCoupleDeviceRequestCouplingTask.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }

    //die Tasks werden vom controller selbst gestartet.
    //dieser ist ueber die gesamte Laufzeit aktiv, wird also niemals garbagecollected
    public void coupleDeviceGetCouplingResponse(@NonNull final GenericActionListener<Void> actionListener) {
        LogUtil.d("CHECK", "coupleDeviceGetCouplingResponse()");
        if (mCoupleDeviceGetCouplingTask != null) {
            return;
        }

        mCoupleDeviceGetCouplingTask = new AsyncTask<Void, Void, LocalizedException>() {

            @Override
            protected LocalizedException doInBackground(Void... params) {
                try {
                    long timeStampStart = new Date().getTime();

                    boolean hasResponse = false;
                    while (!hasResponse) {
                        if (mCoupleDeviceModel == null || StringUtil.isNullOrEmpty(mCoupleDeviceModel.couplingTransactionID) || mCoupleDeviceModel.accountModel == null || StringUtil.isNullOrEmpty(mCoupleDeviceModel.accountModel.guid)) {
                            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "missing data for server call");
                        }

                        ResponseModel rm = getCouplingResponse(mCoupleDeviceModel.accountModel.guid, mCoupleDeviceModel.couplingTransactionID);
                        if (rm.isError) {
                            if (rm.responseException != null) {
                                throw rm.responseException;
                            }

                            String ident = LocalizedException.BACKEND_REQUEST_FAILED;
                            if (!StringUtil.isNullOrEmpty(rm.errorIdent)) {
                                ident = rm.errorIdent;
                            }

                            throw new LocalizedException(ident, rm.errorMsg);
                        } else {
                            if (rm.response != null) {
                                hasResponse = true;

                                if (!rm.response.isJsonObject()) {
                                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "NO JSON Object found");
                                }
                                JsonObject responseObject = rm.response.getAsJsonObject();

                                if (!responseObject.has(JsonConstants.CRESP)) {
                                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "NO Object for CRESP found");
                                }

                                CouplingResponseModel crm = new CouplingResponseModel(responseObject.getAsJsonObject(JsonConstants.CRESP));

                                if (!crm.checkSignature(mCoupleDeviceModel.accountModel.publicKey)) {
                                    throw new LocalizedException(LocalizedException.VERIFY_DATA_FAILED, "");
                                }

                                mCoupleDeviceModel.couplingResponse = crm;
                            } else {
                                if (timeStampStart + (5 * 60 * 1000) < new Date().getTime()) {
                                    throw new LocalizedException(LocalizedException.COUPLE_DEVICE_TIME_OUT, "");
                                }
                            }
                        }
                    }
                } catch (LocalizedException e) {
                    return e;
                }

                return null;
            }

            @Override
            protected void onPostExecute(LocalizedException e) {
                mCoupleDeviceGetCouplingTask = null;
                if (e == null) {
                    actionListener.onSuccess(null);
                } else {
                    LogUtil.w(TAG, "coupleDeviceGetCouplingResponse failed", e);
                    //TODO Exception zu Errortext
                    resetCoupleDevice();
                    actionListener.onFail("", e.getIdentifier());
                }
            }
        };
        mCoupleDeviceGetCouplingTask.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }

    public boolean coupleDeviceCreateDevice(@NonNull final GenericActionListener<Void> actionListener) {
        if (mCoupleDeviceModel == null || mCoupleDeviceModel.couplingResponse == null) {
            return false;
        }

        try {
            LogUtil.d("CHECK", "coupleDeviceCreateDevice()");
            mApplication.getAccountController().coupleDeviceSavePasswordAndSettings();
            mApplication.getKeyController().saveKeys(mCoupleDeviceModel.password, new eu.ginlo_apps.ginlo.controller.KeyController.OnKeysSavedListener() {
                @Override
                public void onKeysSaveComplete() {
                    LogUtil.d("CHECK", "onKeysSaveComplete()");
                    coupleDeviceCreateDeviceInternal(actionListener);
                }

                @Override
                public void onKeysSaveFailed() {
                    resetCoupleDevice();
                    actionListener.onFail("", LocalizedException.SAVE_KEYS_FAILED);
                }
            });
        } catch (LocalizedException e) {
            resetCoupleDevice();
            return false;
        }

        return true;
    }

    //die Tasks werden vom controller selbst gestartet.
    //dieser ist ueber die gesamte Laufzeit aktiv, wird also niemals garbagecollected
    private void coupleDeviceCreateDeviceInternal(@NonNull final GenericActionListener<Void> actionListener) {
        if (mCoupleDeviceCreateDeviceTask != null) {
            return;
        }

        mCoupleDeviceCreateDeviceTask = new AsyncTask<Void, Void, LocalizedException>() {

            @Override
            protected LocalizedException doInBackground(Void... params) {
                try {
                    Key privKey = mApplication.getKeyController().getDeviceKeyPair().getPrivate();
                    if (privKey == null) {
                        throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "Private Key null.");
                    }

                    byte[] decKek = Base64.decode(mCoupleDeviceModel.couplingResponse.kek, Base64.NO_WRAP);
                    decKek = SecurityUtil.decryptMessageWithRSA(decKek, privKey);
                    String jsonString = new String(decKek, Encoding.UTF8);
                    JsonObject aesKeyJO = JsonUtil.getJsonObjectFromString(jsonString);

                    if (aesKeyJO == null) {
                        throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "AES JSON Object null");
                    }

                    String decString = SecurityUtil.decryptStringWithGCM(mCoupleDeviceModel.couplingResponse.encSyncData, aesKeyJO);

                    if (StringUtil.isNullOrEmpty(decString)) {
                        throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "Mini Backup null");
                    }

                    JsonArray miniBackUpArray = JsonUtil.getJsonArrayFromString(decString);

                    if (miniBackUpArray == null || miniBackUpArray.size() < 1) {
                        throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "Mini Backup: Wrong JSON Structur");
                    }

                    mApplication.getBackupController().restoreMiniBackup(miniBackUpArray, mCoupleDeviceModel.couplingTransactionID, mCoupleDeviceModel.couplingResponse.publicKeySig, mCoupleDeviceModel.deviceModel.guid);

                    //Kein Merge von nöten
                    mApplication.getPreferencesController().setHasOldContactsMerged();

                    //Keine Migration nötig
                    mApplication.getPreferencesController().setMigrationVersion(ACCOUNT_MIGRATION_VERSION);

                    mApplication.getPreferencesController().setNotificationPreviewEnabled(true, true);

                    // Gerätenamen senden
                    if (!StringUtil.isNullOrEmpty(mCoupleDeviceModel.deviceName)) {
                        setDeviceName(mCoupleDeviceModel.deviceModel.guid, mCoupleDeviceModel.deviceName);
                    }

                    // Default-Einstellungen vornehmen
                    mApplication.getPreferencesController().setSendProfileName(true);
                    mApplication.getPreferencesController().setSendProfileNameSet();

                    getPurchasedProducts(null);

                    //Nachrichten laden starten auf Main Thread
                    Handler mainHandler = new Handler(mApplication.getMainLooper());

                    Runnable myRunnable = new Runnable() {
                        @Override
                        public void run() {
                            mApplication.getMessageController().loadPendingMessages();
                            mApplication.getMessageController().loadPendingTimedMessages();
                        }
                    };
                    mainHandler.post(myRunnable);
                } catch (LocalizedException e) {
                    return e;
                } catch (UnsupportedEncodingException ue) {
                    return new LocalizedException(LocalizedException.UNSUPPORTED_ENCODING_EXCEPTION, ue.getMessage(), ue);
                }

                return null;
            }

            @Override
            protected void onPostExecute(LocalizedException e) {
                mCoupleDeviceCreateDeviceTask = null;
                if (e == null) {
                    actionListener.onSuccess(null);
                } else {
                    LogUtil.w(TAG, "coupleDeviceCreateDevice failed", e);
                    resetCoupleDevice();
                    actionListener.onFail("", e.getIdentifier());
                }
            }
        };

        mCoupleDeviceCreateDeviceTask.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }

    protected void coupleDeviceSavePasswordAndSettings()
            throws LocalizedException {
        if (mCoupleDeviceModel == null || mCoupleDeviceModel.couplingResponse == null) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "couple device model is null");
        }

        initInternalSecurityAndSavePasswordSettings(mCoupleDeviceModel.isPwdOnStartEnabled, mCoupleDeviceModel.isSimplePassword, mCoupleDeviceModel.password);
    }

    public void resetCoupleDevice() {
        if (mCoupleDeviceModel == null) {
            resetAppData();
            return;
        }

        if (mAccount == null && mCoupleDeviceModel.accountDaoModel != null &&
                mCoupleDeviceModel.accountDaoModel.getId() != null) {
            synchronized (accountDao) {
                accountDao.delete(mCoupleDeviceModel.accountDaoModel);
            }
        }

        mCoupleDeviceModel.accountDaoModel = null;
        mCoupleDeviceModel.accountModel = null;
        mCoupleDeviceModel.deviceModel = null;

        resetAppData();
    }

    /* ***** Couple Device ****** */

    //die Tasks werden vom controller selbst gestartet.
    //dieser ist ueber die gesamte Laufzeit aktiv, wird also niemals garbagecollected
    public void coupleInitialiseCoupling(@NonNull final String tan, @NonNull final Account account, @NonNull final GenericActionListener<Void> actionListener) {

        AsyncTask<Void, Void, LocalizedException> initialiseCoupling = new AsyncTask<Void, Void, LocalizedException>() {

            @Override
            protected LocalizedException doInBackground(Void... params) {
                try {
                    String transId = generateTransId(tan, account.getPublicKey(), account.getAccountGuid());

                    JsonObject deviceDataJO = new JsonObject();

                    String appData = deviceDataJO.toString();

                    String timeStamp = DateUtil.dateToUtcString(new Date());

                    String encTan = Base64.encodeToString(SecurityUtil.encryptMessageWithRSA(tan.getBytes("utf-8"), mApplication.getKeyController().getUserKeyPair().getPublic()), Base64.NO_WRAP);

                    String concatSignature = transId + encTan + timeStamp + appData;

                    byte[] signBytes = SecurityUtil.signData(mApplication.getKeyController().getUserKeyPair().getPrivate(), concatSignature.getBytes(Encoding.UTF8), true);
                    if (signBytes == null) {
                        throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "signature bytes are null");
                    }

                    String signBase64 = Base64.encodeToString(signBytes, Base64.NO_WRAP);

                    ResponseModel rm = initialiseCoupling(transId, timeStamp, encTan, appData, signBase64);

                    if (rm.isError) {
                        throw new LocalizedException(rm.errorIdent, rm.errorMsg);
                    }

                    if (rm.responseException != null) {
                        throw rm.responseException;
                    }

                    if (rm.response.isJsonNull() || !rm.response.isJsonPrimitive()) {
                        throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "Server response is not correct");
                    }
                } catch (UnsupportedEncodingException ue) {
                    return new LocalizedException(LocalizedException.UNSUPPORTED_ENCODING_EXCEPTION, ue.getMessage(), ue);
                } catch (LocalizedException e) {
                    return e;
                }

                return null;
            }

            @Override
            protected void onPostExecute(LocalizedException e) {
                mCoupleDeviceRequestCouplingTask = null;
                if (e == null) {
                    actionListener.onSuccess(null);
                } else {
                    LogUtil.w(TAG, "coupleInitialiseCoupling failed", e);
                    actionListener.onFail("", e.getIdentifier());
                }
            }
        };
        initialiseCoupling.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }

    //die Tasks werden vom controller selbst gestartet.
    //dieser ist ueber die gesamte Laufzeit aktiv, wird also niemals garbagecollected
    public void coupleCancelCoupling(@NonNull final String tan, @NonNull final Account account, @NonNull final GenericActionListener<Void> actionListener) {

        AsyncTask<Void, Void, LocalizedException> task = new AsyncTask<Void, Void, LocalizedException>() {

            @Override
            protected LocalizedException doInBackground(Void... params) {
                try {
                    String transId = generateTransId(tan, account.getPublicKey(), account.getAccountGuid());

                    ResponseModel rm = cancelCoupling(transId);

                    if (rm.isError) {
                        throw new LocalizedException(rm.errorIdent, rm.errorMsg);
                    }

                    if (rm.responseException != null) {
                        throw rm.responseException;
                    }

                    if (rm.response.isJsonNull() || !rm.response.isJsonPrimitive()) {
                        throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "Server response is not correct");
                    }
                } catch (LocalizedException e) {
                    return e;
                }

                return null;
            }

            @Override
            protected void onPostExecute(LocalizedException e) {
                mCoupleDeviceRequestCouplingTask = null;
                if (e == null) {
                    actionListener.onSuccess(null);
                } else {
                    LogUtil.w(TAG, "coupleCancelCoupling failed", e);
                    actionListener.onFail("", e.getIdentifier());
                }
            }
        };
        task.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }

    //die Tasks werden vom controller selbst gestartet.
    //dieser ist ueber die gesamte Laufzeit aktiv, wird also niemals garbagecollected
    public void coupleResponseCoupling(@NonNull final String tan, @NonNull final Account account, @NonNull final GenericActionListener<Void> actionListener) {

        final CouplingRequestModel couplingRequest = getCurrentCouplingRequest();
        AsyncTask<Void, Void, LocalizedException> task = new AsyncTask<Void, Void, LocalizedException>() {

            @Override
            protected LocalizedException doInBackground(Void... params) {
                try {
                    String transId = generateTransId(tan, account.getPublicKey(), account.getAccountGuid());

                    String devicePublicKey = couplingRequest.getPublicKey();

                    final byte[] signBytesPk;
                    signBytesPk = SecurityUtil.signData(mApplication.getKeyController().getUserKeyPair().getPrivate(), devicePublicKey.getBytes(StandardCharsets.UTF_8), true);
                    if (signBytesPk == null) {
                        throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "signature bytes are null");
                    }

                    String deviceKeySig = Base64.encodeToString(signBytesPk, Base64.NO_WRAP);

                    // Minibackup erstellen
                    JsonArray ja = mApplication.getBackupController().createMiniBackup(couplingRequest.isTempDevice());
                    String miniBackup = ja.toString();

                    JsonObject key = SecurityUtil.generateGCMKey();
                    // Minibackup verschlüsselm
                    String miniBackupEnc = SecurityUtil.encryptStringWithGCM(miniBackup, key);

                    SecurityUtil.decryptStringWithGCM(miniBackupEnc, key);

                    // GCM Schlüssel verschlüsseln
                    String keyString = key.toString();
                    PublicKey devicePK = XMLUtil.getPublicKeyFromXML(devicePublicKey);
                    final String kek;
                    kek = Base64.encodeToString(SecurityUtil.encryptMessageWithRSA(keyString.getBytes(StandardCharsets.UTF_8), devicePK), Base64.NO_WRAP);

                    String kekIv = key.get(JsonConstants.IV).getAsString();

                    String appData = couplingRequest.getAppData();

                    String concatSignature = transId + devicePublicKey + deviceKeySig + kek + kekIv + miniBackupEnc + appData;

                    byte[] signBytes;
                    try {
                        signBytes = SecurityUtil.signData(mApplication.getKeyController().getUserKeyPair().getPrivate(), concatSignature.getBytes(Encoding.UTF8), true);
                    } catch (IOException e) {
                        throw new LocalizedException(LocalizedException.STREAMING_ERROR, e);
                    }
                    if (signBytes == null) {
                        throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "signature bytes are null");
                    }

                    String signBase64 = Base64.encodeToString(signBytes, Base64.NO_WRAP);

                    ResponseModel rm = responseCoupling(transId, devicePublicKey, deviceKeySig, kek, kekIv, miniBackupEnc, appData, signBase64);

                    if (rm.isError) {
                        throw new LocalizedException(rm.errorIdent, rm.errorMsg);
                    }

                    if (rm.responseException != null) {
                        throw rm.responseException;
                    }

                    if (rm.response.isJsonNull() || !rm.response.isJsonPrimitive()) {
                        throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "Server response is not correct");
                    }
                    if (couplingRequest.isTempDevice()) {
                        ResponseModel tempInfo = getCouplingTempDevice(mAccount.getAccountGuid());
                        if (tempInfo.isError) {
                            throw new LocalizedException(tempInfo.errorIdent, tempInfo.errorMsg);
                        }

                        if (tempInfo.responseException != null) {
                            throw tempInfo.responseException;
                        }

                        JsonArray deviceData = new JsonArray();
                        if (!tempInfo.response.isJsonNull() && tempInfo.response.isJsonArray()) {
                            // TempDeviceInfos laden
                            JsonArray responseArray = tempInfo.response.getAsJsonArray();
                            if (responseArray != null) {
                                for (int i = 0; i < responseArray.size(); i++) {
                                    JsonObject entry = responseArray.get(i).getAsJsonObject();
                                    JsonObject keyList = entry.get("AccountKeysList").getAsJsonObject();
                                    String signature = keyList.get("sig").getAsString();
                                    String deviceDataRaw = keyList.get("keys").getAsString();
                                    String createdAt = keyList.get("createdAt").getAsString();
                                    String nextUpdate = keyList.get("nextUpdate").getAsString();

                                    PublicKey pubKey = XMLUtil.getPublicKeyFromXML(mAccount.getPublicKey());
                                    try {
                                        String dataForSig = deviceDataRaw + createdAt + nextUpdate;
                                        // Signature prüfen
                                        if (SecurityUtil.verifyData(pubKey, Base64.decode(signature, Base64.DEFAULT), dataForSig.getBytes("utf-8"), true)) {
                                            deviceData = JsonUtil.getJsonArrayFromString(deviceDataRaw);
                                        }
                                    } catch (IOException ex) {
                                        LogUtil.e(TAG, ex.getMessage(), ex);
                                    }
                                }
                            }
                        }

                        if (deviceData != null) {
                            // TempDeviceData erweitern
                            JsonObject tempDeviceData = new JsonObject();
                            tempDeviceData.addProperty("deviceGuid", couplingRequest.getDeviceGuid());
                            tempDeviceData.addProperty("pubKey", couplingRequest.getPublicKey());
                            tempDeviceData.addProperty("fingerPrint", couplingRequest.getPublicKeySha());

                            Date now = new Date();
                            String start = DateUtil.dateToUtcStringWithoutMillis(now);
                            Calendar cal = DateUtil.getCalendarFromDate(now);
                            cal.add(Calendar.HOUR, 6);
                            String end = DateUtil.dateToUtcStringWithoutMillis(cal.getTime());
                            tempDeviceData.addProperty("type", "0x0201");
                            JsonObject validity = new JsonObject();
                            validity.addProperty("start", start);
                            validity.addProperty("end", end);

                            tempDeviceData.add("validity", validity);

                            deviceData.add(tempDeviceData);

                            String deviceDataRaw = deviceData.toString();

                            String dataForSig = deviceDataRaw + start + end;

                            try {
                                signBytes = SecurityUtil.signData(mApplication.getKeyController().getUserKeyPair().getPrivate(), dataForSig.getBytes(Encoding.UTF8), true);
                            } catch (IOException e) {
                                throw new LocalizedException(LocalizedException.STREAMING_ERROR, e);
                            }
                            if (signBytes == null) {
                                throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "signature bytes are null");
                            }

                            String sig = Base64.encodeToString(signBytes, Base64.NO_WRAP);

                            ResponseModel rm2 = setCouplingTempDeviceInfo(deviceDataRaw, start, end, sig);
                            if (rm2.isError) {
                                throw new LocalizedException(rm2.errorIdent, rm2.errorMsg);
                            }

                            if (rm2.responseException != null) {
                                throw rm2.responseException;
                            }
                        } else {
                            throw new LocalizedException(LocalizedException.COUPLE_DEViCE_FAILED, "device data is null");
                        }

                        /**/

                    }
                } catch (LocalizedException e) {
                    return e;
                }

                return null;
            }

            @Override
            protected void onPostExecute(LocalizedException e) {
                mCoupleDeviceRequestCouplingTask = null;
                if (e == null) {
                    actionListener.onSuccess(null);
                } else {
                    LogUtil.w(TAG, "coupleCancelCoupling failed", e);
                    actionListener.onFail("", e.getIdentifier());
                }
            }
        };
        task.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }

    //die Tasks werden vom controller selbst gestartet.
    //dieser ist ueber die gesamte Laufzeit aktiv, wird also niemals garbagecollected
    public void coupleGetCouplingRequest(@NonNull final String tan, @NonNull final Account account, @NonNull final GenericActionListener<CouplingRequestModel> actionListener) {
        LogUtil.d("CHECK", "coupleGetCouplingResponse()");
        if (mCoupleGetCouplingRequestTask != null) {
            mCoupleGetCouplingRequestTask.cancel(true);
            mCoupleGetCouplingRequestTask = null;
            return;
        }

        mCoupleGetCouplingRequestTask = new AsyncTask<Void, Void, LocalizedException>() {

            @Override
            protected LocalizedException doInBackground(Void... params) {
                try {
                    long timeStampStart = new Date().getTime();

                    String transId = generateTransId(tan, account.getPublicKey(), account.getAccountGuid());

                    boolean hasResponse = false;
                    while (!hasResponse) {

                        ResponseModel rm = getCouplingRequest(transId);
                        if (rm.isError) {
                            if (rm.responseException != null) {
                                throw rm.responseException;
                            }

                            String ident = LocalizedException.BACKEND_REQUEST_FAILED;
                            if (!StringUtil.isNullOrEmpty(rm.errorIdent)) {
                                ident = rm.errorIdent;
                            }

                            throw new LocalizedException(ident, rm.errorMsg);
                        } else {
                            if (rm.response != null) {
                                hasResponse = true;

                                if (!rm.response.isJsonArray()) {
                                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "NO JSON Object found");
                                }
                                JsonArray responseArray = rm.response.getAsJsonArray();

                                for (int i = 0; i < responseArray.size(); i++) {
                                    JsonObject responseObject = responseArray.get(i).getAsJsonObject();

                                    if (responseObject.has(JsonConstants.CREQ)) {

                                        couplingRequestModel = new CouplingRequestModel(responseObject.getAsJsonObject(JsonConstants.CREQ), mAccount);
                                    }
                                }

                                //mCoupleDeviceModel.couplingResponse = crm;
                            } else {
                                if (timeStampStart + (5 * 60 * 1000) < new Date().getTime()) {
                                    throw new LocalizedException(LocalizedException.COUPLE_DEVICE_TIME_OUT, "");
                                }
                            }
                        }
                    }
                } catch (LocalizedException e) {
                    return e;
                }

                return null;
            }

            @Override
            protected void onPostExecute(LocalizedException e) {
                mCoupleGetCouplingRequestTask = null;
                if (e == null) {
                    actionListener.onSuccess(couplingRequestModel);
                } else {
                    LogUtil.w(TAG, "coupleGetCouplingRequest failed", e);
                    actionListener.onFail("", e.getIdentifier());
                }
            }
        };
        mCoupleGetCouplingRequestTask.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }

    public CouplingRequestModel getCurrentCouplingRequest() {
        return couplingRequestModel;
    }

    private void resetAppData() {
        accountLoaded = false;
        if (mAccount != null && mAccount.getId() != null) {
            synchronized (accountDao) {
                accountDao.delete(mAccount);
            }
        }
        mApplication.getDeviceController().deleteOwnDevice();
        //eigener Kontakt ist angelegt -> Löschen aller Kontakte
        mApplication.getContactController().getDao().deleteAll();
        PreferencesController preferencesController = mApplication.getPreferencesController();
        preferencesController.clearAll();
        preferencesController.getSharedPreferences().edit().clear().apply();
        mApplication.getKeyController().clearKeys();
        mApplication.getKeyController().purgeKeys();
    }

    private ResponseModel setDeviceName(final String deviceGuid,
                                        final String name) {
        final ResponseModel rm = new ResponseModel();
        String encodedDeviceName = Base64.encodeToString(name.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                    return;
                }

                if (response.jsonArray != null && response.jsonArray.size() > 0) {
                    rm.response = response.jsonArray.get(0);
                }
            }
        };

        BackendService.withSyncConnection(mApplication)
                .setDeviceName(deviceGuid, encodedDeviceName, listener);
        return rm;
    }

    private ResponseModel getCouplingResponse(final String accountGuid,
                                              final String transactionID) {
        final ResponseModel rm = new ResponseModel();
        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                    return;
                }

                if (response.jsonArray != null && response.jsonArray.size() > 0) {
                    rm.response = response.jsonArray.get(0);
                }
            }
        };

        BackendService.withSyncConnection(mApplication)
                .getCouplingResponse(accountGuid, transactionID, listener);

        return rm;
    }

    private String generateTransId(@NonNull String tan, @NonNull String publicKey, @NonNull String accountGuid)
            throws LocalizedException {
        try {
            String fingerprint = ChecksumUtil.getSHA256ChecksumForString(publicKey);
            if (StringUtil.isNullOrEmpty(fingerprint)) {
                throw new LocalizedException(LocalizedException.GENERATED_TRANS_ID_FAILED, "PublicKey Fingerprint is null");
            }

            String concat = tan + fingerprint;

            String key1 = ChecksumUtil.getSHA256ChecksumForString(concat);
            if (StringUtil.isNullOrEmpty(key1)) {
                throw new LocalizedException(LocalizedException.GENERATED_TRANS_ID_FAILED, "Key is null");
            }

            byte[] ivData = ChecksumUtil.getSHA256ChecksumAsBytesForString(accountGuid);
            if (ivData == null) {
                throw new LocalizedException(LocalizedException.GENERATED_TRANS_ID_FAILED, "IV is null");
            }

            String encoded = BCrypt.encode_base64(ivData, ivData.length);
            if (StringUtil.isNullOrEmpty(encoded)) {
                throw new LocalizedException(LocalizedException.GENERATED_TRANS_ID_FAILED, "IV BASE64 is null");
            }

            String salt = "$2a$04$" + encoded;

            return BCrypt.hashpw(key1, salt);
        } catch (IllegalArgumentException e) {
            throw new LocalizedException(LocalizedException.GENERATED_TRANS_ID_FAILED, e);
        }
    }

    @NonNull
    private ResponseModel requestCoupling(final String accountGuid,
                                          final String transactionID,
                                          final String publicKey,
                                          final String encryptionVerify,
                                          final String reqType,
                                          final String appData,
                                          final String signature) {
        final ResponseModel rm = new ResponseModel();
        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                    return;
                }

                if (response.jsonArray == null || response.jsonArray.size() < 1) {
                    rm.responseException = new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response is null");
                    return;
                }

                rm.response = response.jsonArray.get(0);
            }
        };

        BackendService.withSyncConnection(mApplication)
                .requestCoupling(accountGuid, transactionID, publicKey, encryptionVerify, reqType, appData, signature, listener);

        return rm;
    }

    @NonNull
    private ResponseModel initialiseCoupling(
            final String transactionID,
            final String timestamp,
            final String encTan,
            final String appData,
            final String signature)
            throws LocalizedException {
        final ResponseModel rm = new ResponseModel();

        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                    return;
                }

                if (response.jsonArray == null || response.jsonArray.size() < 1) {
                    rm.responseException = new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response is null");
                    return;
                }

                rm.response = response.jsonArray.get(0);
            }
        };

        BackendService.withSyncConnection(mApplication)
                .initialiseCoupling(transactionID, timestamp, encTan, appData, signature, listener);

        return rm;
    }

    @NonNull
    private ResponseModel cancelCoupling(
            final String transactionID)
            throws LocalizedException {
        final ResponseModel rm = new ResponseModel();

        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                    return;
                }

                if (response.jsonArray == null || response.jsonArray.size() < 1) {
                    rm.responseException = new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response is null");
                    return;
                }

                rm.response = response.jsonArray.get(0);
            }
        };

        BackendService.withSyncConnection(mApplication)
                .cancelCoupling(transactionID, listener);

        return rm;
    }

    @NonNull
    private ResponseModel responseCoupling(
            final String transactionID,
            final String device,
            final String devKeySig,
            final String kek,
            final String kekIv,
            final String encSyncData,
            final String appData,
            final String sig
    )
            throws LocalizedException {
        final ResponseModel rm = new ResponseModel();
        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                    return;
                }

                if (response.jsonArray == null || response.jsonArray.size() < 1) {
                    rm.responseException = new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response is null");
                    return;
                }

                rm.response = response.jsonArray.get(0);
            }
        };

        BackendService.withSyncConnection(mApplication)
                .responseCoupling(transactionID, device, devKeySig, kek, kekIv, encSyncData, appData, sig, listener);

        return rm;
    }

    @NonNull
    private ResponseModel getCouplingRequest(
            final String transactionID)
            throws LocalizedException {
        final ResponseModel rm = new ResponseModel();
        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                    return;
                }

                if (response.jsonArray == null || response.jsonArray.size() < 1) {
                    rm.responseException = new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response is null");
                    return;
                }

                rm.response = response.jsonArray;
            }
        };

        BackendService.withSyncConnection(mApplication)
                .getCouplingRequest(transactionID, listener);

        return rm;
    }

    // Kopplung / Freigebendes Gerät

    @NonNull
    private ResponseModel getCouplingTempDevice(
            final String accountGuid
    )
            throws LocalizedException {
        final ResponseModel rm = new ResponseModel();

        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                    return;
                }

                if (response.jsonArray == null) {
                    rm.responseException = new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response is null");
                    return;
                }

                rm.response = response.jsonArray;
            }
        };

        BackendService.withSyncConnection(mApplication)
                .getTempDeviceInfo(accountGuid, listener);

        return rm;
    }

    @NonNull
    private ResponseModel setCouplingTempDeviceInfo(
            final String keys,
            final String createdAt,
            final String nextUpdate,
            final String sig
    )
            throws LocalizedException {
        final ResponseModel rm = new ResponseModel();

        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                    return;
                }

                if (response.jsonArray == null || response.jsonArray.size() < 1) {
                    rm.responseException = new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response is null");
                    return;
                }

                rm.response = response.jsonArray.get(0);
            }
        };

        BackendService.withSyncConnection(mApplication)
                .setTempDeviceInfo(keys, createdAt, nextUpdate, sig, listener);

        return rm;
    }

    public JsonObject getCompanyMDMConfig() {
        // Wird in der AccountControllerBusiness implementiert
        return null;
    }

    public SecretKey getCompanyAesKey()
            throws LocalizedException {
        // Wird in der AccountControllerBusiness implementiert
        return null;
    }

    public SecretKey getCompanyUserAesKey()
            throws LocalizedException {
        // Wird in der AccountControllerBusiness implementiert
        return null;
    }

    public boolean hasCompanyUserAesKey() {
        try {
            SecretKey companyUserAesKey = getCompanyUserAesKey();

            return companyUserAesKey != null;
        } catch (LocalizedException e) {
            return false;
        }
    }

    public String getEmailDomainPre22() {
        // Wird in der AccountControllerBusiness implementiert
        return null;
    }

    private void loadAccount() {
        List<Account> accounts;

        synchronized (accountDao) {
            try {
                accounts = accountDao.loadAll();
            } catch (SQLiteException e) {
                //Keine DB vorhanden
                accounts = null;
            }
        }

        if (accounts != null && accounts.size() > 0) {
            mAccount = accounts.get(0);
            accountLoaded = true;
            LogUtil.i(TAG, "Account loaded.");
        } else {
            accountLoaded = false;
            LogUtil.i(TAG, "No account found.");
        }
    }

    public void saveOrUpdateAccount(Account account) {
        mAccount = account;
        accountLoaded = true;

        synchronized (accountDao) {
            if (account.getId() != null) {
                accountDao.update(account);
            } else {
                accountDao.insert(account);
            }
        }
    }

    AccountModel createAccountModel(String guid, String phoneNumber, String email)
            throws LocalizedException {
        AccountModel accountModel = new AccountModel();

        accountModel.guid = guid;
        accountModel.publicKey = XMLUtil
                .getXMLFromPublicKey(mApplication.getKeyController().getUserKeyPair().getPublic());
        accountModel.phone = phoneNumber;
        accountModel.email = email;

        return accountModel;
    }

    DeviceModel createDeviceModel(String guid, String countryFlag, AccountModel accountModel) throws LocalizedException {
        eu.ginlo_apps.ginlo.controller.KeyController keyController = mApplication.getKeyController();
        DeviceModel deviceModel = new DeviceModel();

        deviceModel.guid = guid;
        deviceModel.accountGuid = accountModel.guid;
        deviceModel.publicKey = XMLUtil
                .getXMLFromPublicKey(keyController.getDeviceKeyPair().getPublic());

        String sha256Hash = ChecksumUtil.getSHA256ChecksumForString(deviceModel.publicKey);
        byte[] pkSignBytes = SecurityUtil
                .signData(keyController.getUserKeyPair().getPrivate(), sha256Hash.getBytes(), false);

        deviceModel.pkSign = Base64.encodeToString(pkSignBytes, Base64.NO_WRAP);
        // The new PassToken is a generated random UUID of format: "{" + UUID.randomUUID() + "}"
        deviceModel.passtoken = GuidUtil.generatePassToken();
        deviceModel.language = countryFlag;
        deviceModel.appName = AppConstants.getAppName();
        deviceModel.appVersion = AppConstants.getAppVersionName();
        deviceModel.os = AppConstants.OS;

        deviceModel.featureVersion = AppConstants.getAppFeatureVersions();

        return deviceModel;
    }

    public void updateAccoutDao() {
        if ((accountDao != null) && (mAccount != null)) {
            synchronized (accountDao) {
                accountDao.update(mAccount);
            }
        }
    }

    public boolean getAccountLoaded() {
        return accountLoaded;
    }

    public Account getAccount() {
        return mAccount;
    }

    public int getAccountState() {
        if (mAccount != null) {
            Integer state = mAccount.getState();

            return state != null ? state : Account.ACCOUNT_STATE_NO_ACCOUNT;
        }
        return Account.ACCOUNT_STATE_NO_ACCOUNT;
    }

    public boolean hasAccountFullState() {
        if (mAccount != null) {
            Integer state = mAccount.getState();

            if (state != null) {
                return state == Account.ACCOUNT_STATE_FULL;
            }
        }

        return false;
    }

    boolean saveAccountKeyPair(@NonNull KeyPair keyPair) {
        if (mAccount != null) {
            try {
                mAccount.setPrivateKey(XMLUtil.getXMLFromPrivateKey(keyPair.getPrivate()));
                mAccount.setPublicKey(XMLUtil.getXMLFromPublicKey(keyPair.getPublic()));

                synchronized (accountDao) {
                    accountDao.update(mAccount);
                }
            } catch (LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
                return false;
            }
            return true;
        }
        return false;
    }

    @Nullable
    KeyPair getAccountKeyPair() throws LocalizedException {
        if (mAccount != null) {
            String pubKeyAsXml = mAccount.getPublicKey();
            String priKeyAsXml = mAccount.getPrivateKey();

            if (!StringUtil.isNullOrEmpty(pubKeyAsXml) && !StringUtil.isNullOrEmpty(priKeyAsXml)) {
                return new KeyPair(XMLUtil.getPublicKeyFromXML(pubKeyAsXml),
                        XMLUtil.getPrivateKeyFromXML(priKeyAsXml));
            }
            return null;
        }

        return null;
    }

    public void updateAccountInfo(final String nickname,
                                  final String status,
                                  final byte[] imageBytes,
                                  final String lastName,
                                  final String firstName,
                                  final String department,
                                  final String oooStatus,
                                  final String encryptedOooStatus,
                                  final boolean isInitialSave,
                                  @NonNull final UpdateAccountInfoCallback callback) {
        try {
            Contact ownContact = mApplication.getContactController().getOwnContact();

            if (ownContact == null) {
                mApplication.getContactController().fillOwnContactWithAccountInfos(null);

                ownContact = mApplication.getContactController().getOwnContact();
            }

            boolean bForce = false;

            if (!StringUtil.isNullOrEmpty(nickname)) {
                if (!StringUtil.isEqual(nickname, ownContact.getNickname())) {
                    bForce = true;
                }
                ownContact.setNickname(nickname);
            }

            if (!StringUtil.isNullOrEmpty(status)) {
                if (!StringUtil.isEqual(status, ownContact.getStatusText())) {
                    bForce = true;
                }
                ownContact.setStatusText(status);
            }

            mApplication.getContactController().saveContactInformation(ownContact, lastName, firstName, null, null, null, department, oooStatus, imageBytes, -1, bForce || isInitialSave);
        } catch (LocalizedException e) {
            LogUtil.w(getClass().getSimpleName(), "save own contact infos failed", e);
        }

        final AsyncHttpTask.AsyncHttpCallback<JsonObject> asyncCallback = new AsyncHttpTask.AsyncHttpCallback<JsonObject>() {
            boolean isFailed;

            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                try {
                    String nicknameForServer = null;
                    String statusForServer = null;
                    String imageBytesForServer = null;

                    String aesKeyBase64 = mAccount.getAccountInfosAesKey();

                    SecretKey aesKey;

                    if (StringUtil.isNullOrEmpty(aesKeyBase64)) {

                        aesKey = SecurityUtil.generateAESKey();
                        mAccount.setAccountInfosAesKey(
                                SecurityUtil.getBase64StringFromAESKey(aesKey));
                        updateAccoutDao();
                    } else {
                        aesKey = SecurityUtil.getAESKeyFromBase64String(aesKeyBase64);
                    }

                    if (!StringUtil.isNullOrEmpty(nickname)) {
                        String accountNickname = mAccount.getName();
                        if (!StringUtil.isEqual(nickname, accountNickname)) {
                            nicknameForServer = SecurityUtil
                                    .encryptWithAESToBase64String(nickname, aesKey);
                        }
                    }

                    if (!StringUtil.isNullOrEmpty(status)) {
                        String accountStatus = mAccount.getStatusText();
                        if (!StringUtil.isEqual(status, accountStatus)) {
                            statusForServer = SecurityUtil.encryptWithAESToBase64String(status, aesKey);
                        }
                    }

                    if (imageBytes != null && imageBytes.length > 0) {
                        byte[] decodeImageBytes = Base64.encode(imageBytes, Base64.NO_WRAP);
                        byte[] encodeBytes = SecurityUtil.encryptMessageWithAES(decodeImageBytes,
                                aesKey,
                                new IvParameterSpec(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
                        imageBytesForServer = Base64.encodeToString(encodeBytes, Base64.NO_WRAP);
                    }

                    if (nicknameForServer != null
                            || statusForServer != null
                            || imageBytesForServer != null
                            || !StringUtil.isNullOrEmpty(encryptedOooStatus)) {
                        final String guids = mApplication.getContactController().getGuidsAsStringFromSimsMeContacts();

                        BackendService.withSyncConnection(mApplication)
                                .setProfileInfo(nicknameForServer,
                                        statusForServer,
                                        imageBytesForServer,
                                        StringUtil.isNullOrEmpty(guids) ? "" : guids,
                                        encryptedOooStatus,
                                        listener);
                    }
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                    isFailed = true;
                }
            }

            @Override
            public JsonObject asyncLoaderServerResponse(
                    final BackendResponse response) {
                return response.jsonObject;
            }

            @Override
            public void asyncLoaderFinished(final JsonObject result) {
                if (isFailed) {
                    callback.updateAccountInfoFailed(null);
                    return;
                }

                if (result == null) {
                    callback.updateAccountInfoFinished();
                    return;
                }

                if (!result.has("ProfilInfoResult")) {
                    callback.updateAccountInfoFailed(null);
                    return;
                }

                try {
                    PrivateInternalMessageController privateInternalMessageController = mApplication.getPrivateInternalMessageController();
                    JsonObject jso = result.getAsJsonObject("ProfilInfoResult");
                    List<Contact> oldContacts = null;
                    if (jso.has("not-send")) {
                        JsonArray jsonArray = jso.getAsJsonArray("not-send");
                        //alte methode
                        if (jsonArray.size() > 0) {
                            //noinspection unchecked
                            oldContacts = new ArrayList(jsonArray.size());
                            for (int i = 0; i < jsonArray.size(); i++) {
                                String guid = jsonArray.get(i).getAsString();
                                Contact oldContact = mApplication.getContactController().getContactByGuid(guid);

                                if (oldContact != null &&
                                        !GuidUtil.isSystemChat(oldContact.getAccountGuid())) {
                                    oldContacts.add(oldContact);
                                }
                            }
                        }
                    }

                    //speichern der daten in db
                    if (!StringUtil.isNullOrEmpty(nickname)) {
                        String accountNickname = mAccount.getName();
                        if (!StringUtil.isEqual(nickname, accountNickname)) {
                            mAccount.setName(nickname);
                            updateAccoutDao();

                            if (oldContacts != null && oldContacts.size() > 0) {
                                privateInternalMessageController
                                        .broadcastProfileNameChange(oldContacts, nickname);
                            }
                        }
                    }

                    if (!StringUtil.isNullOrEmpty(status)) {
                        String accountStatus = mAccount.getStatusText();
                        if (!StringUtil.isEqual(status, accountStatus)) {
                            mAccount.setStatusText(status);
                            mApplication.getStatusTextController().saveStatusText(status);
                            updateAccoutDao();

                            if (oldContacts != null && oldContacts.size() > 0) {
                                privateInternalMessageController
                                        .broadcastStatusTextChange(oldContacts, status);
                            }
                        }
                    }

                    ChatImageController chatImageController = mApplication.getChatImageController();

                    if (imageBytes != null && imageBytes.length > 0) {
                        chatImageController.saveImage(mAccount.getAccountGuid(), imageBytes);

                        if (oldContacts != null && oldContacts.size() > 0) {
                            privateInternalMessageController
                                    .broadcastProfileImageChange(oldContacts, imageBytes);
                        }
                    }

                    chatImageController.removeFromCache(mAccount.getAccountGuid());

                    callback.updateAccountInfoFinished();
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                    callback.updateAccountInfoFailed(null);
                }
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                callback.updateAccountInfoFailed(errorMessage);
            }
        };


        new AsyncHttpTask<>(asyncCallback)
                .executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);

    }

    /* ************************ */

    @Override
    public void appIsUnlock() {
        try {
            if (hasAccountFullState()) {
                //Ist eine Account ID vorhanden(Update 2.0 -> 2.1)
                if (StringUtil.isNullOrEmpty(getAccount().getAccountID())) {
                    updateAccountInfoFromServer(false, false);
                }
                //Ist ein eigenes Device vorhanden(Update 2.0 -> 2.1)
                if (mApplication.getDeviceController().getOwnDevice() == null) {
                    mApplication.getDeviceController().createOwnDevice(getAccount());
                }

                if (!mApplication.getPreferencesController().hasSetOwnDeviceName()) {
                    mApplication.getDeviceController().changeDeviceNameAtBackend(getAccount().getDeviceGuid(), DeviceController.getDefaultDeviceName(), new GenericActionListener<Void>() {
                        @Override
                        public void onSuccess(Void object) {
                            mApplication.getPreferencesController().setHasSetOwnDeviceName();
                        }

                        @Override
                        public void onFail(String message, String errorIdent) {
                            //beim nächsten Start neu versuchen
                        }
                    });
                }
            }
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    public void appWillBeLocked() {

    }

    public void updateAccountInfoFromServer(final boolean updateProfileInfo,
                                            final boolean updateOooStatus) {
        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (!response.isError && response.jsonObject != null) {
                    JsonObject responseJSON = response.jsonObject;

                    if (!responseJSON.has(JsonConstants.ACCOUNT)) {
                        return;
                    }

                    JsonObject accountObject = responseJSON.get(JsonConstants.ACCOUNT).getAsJsonObject();

                    if (JsonUtil.hasKey(JsonConstants.ACCOUNT_ID, accountObject)) {
                        String accountId = accountObject.get(JsonConstants.ACCOUNT_ID).getAsString();

                        getAccount().setAccountID(accountId);
                    }

                    if (!updateProfileInfo && !updateOooStatus) {
                        saveOrUpdateAccount(getAccount());
                        return;
                    }

                    try {
                        final String aesKeyAsString = getAccount().getAccountInfosAesKey();

                        if (StringUtil.isNullOrEmpty(aesKeyAsString)) {
                            return;
                        }

                        SecretKey aesKey = SecurityUtil.getAESKeyFromBase64String(aesKeyAsString);

                        if (accountObject.has(JsonConstants.STATUS) && !accountObject.get(JsonConstants.STATUS).isJsonNull()) {
                            String encryptedStatus = accountObject.get(JsonConstants.STATUS).getAsString();
                            String status = SecurityUtil.decryptBase64StringWithAES(encryptedStatus, aesKey);

                            mApplication.getStatusTextController().saveStatusText(status);
                            getAccount().setStatusText(status);
                        } else {
                            String status = mApplication.getString(R.string.settings_statusWorker_firstMessage);
                            mApplication.getStatusTextController().saveStatusText(status);
                            getAccount().setStatusText(status);
                        }

                        if (accountObject.has(JsonConstants.NICKNAME) && !accountObject.get(JsonConstants.NICKNAME).isJsonNull()) {
                            String encryptedNickname = accountObject.get(JsonConstants.NICKNAME).getAsString();

                            String nickname = SecurityUtil.decryptBase64StringWithAES(encryptedNickname, aesKey);

                            getAccount().setName(nickname);
                        }

                        //bild laden
                        loadAccountProfileImageFromServer(getAccount(), aesKey);

                        saveOrUpdateAccount(getAccount());
                    } catch (Exception e) {
                        LogUtil.e(TAG, e.getMessage(), e);
                    }
                }
            }
        };

        final int withProfileInfo = (updateProfileInfo ? 1 : 0) + (updateOooStatus ? 2 : 0);

        BackendService.withAsyncConnection(mApplication)
                .getAccountInfo(getAccount().getAccountGuid(), withProfileInfo, false, false, false, listener);
    }

    private void loadAccountProfileImageFromServer(final Account account, final SecretKey aesKey) {

        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (!response.isError) {
                    try {
                        if (response.jsonArray == null || response.jsonArray.size() < 1) {
                            return;
                        }

                        String imageBase64String = response.jsonArray.get(0).getAsString();

                        if (StringUtil.isNullOrEmpty(imageBase64String) || aesKey == null) {
                            return;
                        }

                        byte[] encryptedBytes = Base64.decode(imageBase64String, Base64.NO_WRAP);
                        byte[] decryptedBytes = SecurityUtil.decryptMessageWithAES(encryptedBytes, aesKey);

                        byte[] imageBytes = Base64.decode(decryptedBytes, Base64.NO_WRAP);

                        mApplication.getChatImageController().saveImage(account.getAccountGuid(), imageBytes);
                    } catch (Exception e) {
                        LogUtil.e(TAG, e.getMessage(), e);
                    }
                }
            }
        };

        BackendService.withAsyncConnection(mApplication)
                .getAccountImage(account.getAccountGuid(), listener);
    }

    /**
     * Kann/soll durch ein Flavour überschrieben werden
     *
     * @param action action model
     */
    public void handleActionModel(final Action action) {
    }

    public void loadConfirmedIdentitiesConfig(@Nullable final GenericActionListener<Void> callback, @Nullable Executor executor) {

    }

    private boolean checkBackupInterval() throws LocalizedException {
        PreferencesController preferencesController = mApplication.getPreferencesController();
        long latestBackupDate = preferencesController.getLatestBackupDate();
        final long millisToAdd;

        switch (preferencesController.getBackupInterval()) {
            case PreferencesController.BACKUP_INTERVAL_DAILY: {
                millisToAdd = BuildConfig.DEBUG ? (5 * 60 * 1000) : (24 * 60 * 60 * 1000); // sonarcube moechte Klammern
                break;
            }
            case PreferencesController.BACKUP_INTERVAL_WEEKLY: {
                millisToAdd = 7 * 24 * 60 * 60 * 1000;
                break;
            }
            case PreferencesController.BACKUP_INTERVAL_MONTHLY: {
                millisToAdd = 30L * 24 * 60 * 60 * 1000;
                break;
            }
            default: {
                millisToAdd = 0;
                LogUtil.w(TAG, LocalizedException.UNDEFINED_ARGUMENT);
                break;
            }
        }

        long current = new Date().getTime();

        if (latestBackupDate + millisToAdd < current) {
            startBackup();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Methode kann von Flavours überschrieben werden.
     * Methode wird aufgerufen nachdem ein Account erfolgreich wieder hergestellt wurde.
     * <p>
     * Methode wird nicht auf den Main Thread aufgerufen.
     *
     * @param accountFromBackupJO Account JsonObject aus dem Backup
     */
    public void doActionsAfterRestoreAccountFromBackup(JsonObject accountFromBackupJO) {
        try {
            final PreferencesController preferencesController = mApplication.getPreferencesController();

            preferencesController.getDisableConfirmRead();
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    /**
     * Methode kann von Flavours überschrieben werden.
     * Methode wird aufgerufen bevor ein Account im Backup gespeichert wird.
     * <p>
     * Methode wird nicht auf den Main Thread aufgerufen.
     *
     * @param accountBackupJO Account JsonObject das im Backup gespeichert wird
     */
    public void doActionsBeforeAccountIsStoreInBackup(final JsonObject accountBackupJO)
            throws LocalizedException {
    }

    public void activateBackup(String password,
                               final AsyncBackupKeysCallback listener) {
        AsyncBackupKeysTask task = new AsyncBackupKeysTask(listener, password, 80000);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
    }

    public void deactivateBackup() throws LocalizedException {
        PreferencesController preferencesController = mApplication.getPreferencesController();
        preferencesController.saveBackupKeyInfo(null, null, -1);
    }

    public void startBackup() {
        if (!mIsBackupStarted) {
            mIsBackupStarted = true;
            Intent buServiceIntent = new Intent(mApplication, BackupService.class);
            mApplication.startService(buServiceIntent);
        }
    }

    public void registerCreateBackupListener(CreateBackupListener listener) {
        backupListeners.add(listener);
    }

    public void unregisterCreateBackupListener(CreateBackupListener listener) {
        backupListeners.remove(listener);
    }

    private void updateListenerForSaveChatsState(int current, int size) {
        for (CreateBackupListener listener : backupListeners) {
            listener.onCreateBackupSaveChatsUpdate(current, size);
        }
    }

    public void registerBackupUploadListener(final BackupUploadListener backupUploadListener) {
        if (backupUploadListener != null) {
            if (mBackupUploadListeners == null) {
                mBackupUploadListeners = new ArrayList<>();
            }
            mBackupUploadListeners.add(backupUploadListener);
        }
    }

    public void unRegisterBackupUploadListener(final BackupUploadListener backupUploadListener) {
        if (mBackupUploadListeners != null && backupUploadListener != null) {
            mBackupUploadListeners.remove(backupUploadListener);
        }
    }

    @SuppressLint("NewApi")
    public boolean isBiometricAuthAvailable() {
        if (SystemUtil.hasMarshmallow()) {
            final KeyguardManager keyGuardManager = mApplication.getSystemService(KeyguardManager.class);
            final FingerprintManager fingerprintManager = mApplication.getSystemService(FingerprintManager.class);
            if (fingerprintManager == null || keyGuardManager == null) {
                return false;
            } else {
                return keyGuardManager.isKeyguardSecure() &&
                        !keyGuardManager.inKeyguardRestrictedInputMode() &&
                        fingerprintManager.isHardwareDetected() &&
                        fingerprintManager.hasEnrolledFingerprints() &&
                        !mApplication.getPreferencesController().isBiometricLoginDisabledByAdmin();
            }
        } else {
            return false;
        }
    }

    public void enableBiometricAuthenticationPre28(@NonNull final Cipher encryptCipher, final eu.ginlo_apps.ginlo.controller.KeyController.OnKeysSavedListener listener) {
        if (SystemUtil.hasMarshmallow()) {
            final KeyguardManager keyGuardManager = mApplication.getSystemService(KeyguardManager.class);
            final FingerprintManager fingerprintManager = mApplication.getSystemService(FingerprintManager.class);
            if (fingerprintManager == null || keyGuardManager == null) {
                if (listener != null) {
                    listener.onKeysSaveFailed();
                }
            } else if (!fingerprintManager.hasEnrolledFingerprints()) {
                if (listener != null) {
                    listener.onKeysSaveFailed();
                }
            } else {
                mApplication.getKeyController().saveBiometricKeys(encryptCipher, listener);
            }
        } else if (listener != null) {
            listener.onKeysSaveFailed();
        }
    }

    /**
     * biometric key aus keytsore loeschen
     *
     * @param deleteListener
     */
    public void disableBiometricAuthentication(ConcurrentTaskListener deleteListener) {
        mApplication.getKeyController().deleteBiometricKeyFromKeystore(deleteListener);
        mApplication.getPreferencesController().setBiometricAuthEnabled(false);
    }

    /**
     * @param attribute
     * @return
     * @throws LocalizedException
     */
    public String getEmailAttributePre22(final String attribute)
            throws LocalizedException {
        final JsonObject jsonObject = getEmailAttributesJsonObjectPre22();
        if (jsonObject != null) {
            final JsonElement subElement = jsonObject.get(attribute);
            if (subElement != null && !subElement.isJsonNull()) {
                return subElement.getAsString();
            } else {
                return null;
            }
        }

        return null;
    }

    /**
     * getEmailAttributesJsonObject
     *
     * @return
     * @throws LocalizedException
     */
    private JsonObject getEmailAttributesJsonObjectPre22()
            throws LocalizedException {
        if (mAccount != null) {
            final String eMailDataJson = mAccount.getCustomStringAttribute(EMAIL_ATTRIBUTES_PRE_22);
            final JsonParser parser = new JsonParser();

            if (StringUtil.isNullOrEmpty(eMailDataJson)) {
                return null;
            } else {
                final JsonElement element = parser.parse(eMailDataJson);
                final JsonObject jsonObject = element.getAsJsonObject();

                if (jsonObject != null) {
                    return jsonObject;
                }
            }
            return null;
        }

        return null;
    }

    public boolean hasEmailOrCompanyContacts()
            throws LocalizedException {
        return false;
    }

    public boolean isDeviceManaged()
            throws LocalizedException {
        return false;
    }

    /**
     * Nicht auf den Main Thread aufrufen!
     *
     * @return Secret Aes Key
     */
    public SecretKey getDomainAesKey()
            throws LocalizedException {
        return null;
    }

    /**
     * Recovery Passwort generien und spreizen,
     * PublicKey vom Server laden,
     * RP AES verschluesseln und lokal Speichern,
     * RP RSA verschluesseln und an Server senden
     * Achtung, Methode arbeitet syncron, muss also von einem anderen Thread7Task aufgerufen werden
     */
    void createRecoveryPassword(final CreateRecoveryCodeListener createRecoveryCodeListener) {
        // recoverycode erzeugen

        final String recoveryCode = StringUtil.generatePassword(4) +
                "-" +
                StringUtil.generatePassword(4) +
                "-" +
                StringUtil.generatePassword(4) +
                "-" +
                StringUtil.generatePassword(4) +
                "-" +
                StringUtil.generatePassword(4) +
                "-" +
                StringUtil.generatePassword(4);

        final SecurityUtil.OnDeriveKeyCompleteListener onDeriveKeyCompleteListener = new SecurityUtil.OnDeriveKeyCompleteListener() {
            @Override
            public void onComplete(final SecretKey key, byte[] usedSalt) {

                IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {

                    @Override
                    public void onBackendResponse(final BackendResponse response) {
                        try {
                            if (!response.isError && response.jsonArray != null) {

                                final JsonElement publicRsaKeyJson = response.jsonArray.get(0);
                                if (publicRsaKeyJson != null) {
                                    // privaten Key des Accounts auf dem Filesystem speichern
                                    final KeyController keyController = mApplication.getKeyController();
                                    IvParameterSpec iv = SecurityUtil.generateIV();
                                    SecurityUtil.AESKeyContainer keyContainer = new SecurityUtil.AESKeyContainer(key, iv);
                                    SecurityUtil.writeKeyToDisc(keyContainer, keyController.getDeviceKeyPair().getPrivate(), mApplication);

                                    final String serverPublicKeyString = publicRsaKeyJson.getAsString();
                                    final PublicKey publicKey = XMLUtil.getPublicKeyFromXML(serverPublicKeyString);

                                    final byte[] recoveryTokenBytes = SecurityUtil.encryptMessageWithRSA(recoveryCode.getBytes("UTF-8"), publicKey);
                                    final String encodedRecoveryToken = Base64.encodeToString(recoveryTokenBytes, Base64.NO_WRAP);

                                    // RecoveryCode mit ServerPublicKey verschluesseln unf ins Filesystem schreiben

                                    final KeyPair deviceKeyPair = mApplication.getKeyController().getDeviceKeyPair();
                                    final KeyPair userKeyPair = mApplication.getKeyController().getUserKeyPair();
                                    final String devicePublicKeyString = XMLUtil.getXMLFromPublicKey(deviceKeyPair.getPublic());
                                    final String accountPublicKeyString = XMLUtil.getXMLFromPublicKey(userKeyPair.getPublic());

                                    final String transId = ChecksumUtil.getSHA256ChecksumForString(devicePublicKeyString + accountPublicKeyString);
                                    final String sigString = transId + encodedRecoveryToken;
                                    final PreferencesController preferencesController = mApplication.getPreferencesController();
                                    final String phoneNumber = mAccount.getPhoneNumber();
                                    if (!StringUtil.isNullOrEmpty(phoneNumber)) {
                                        final byte[] encryptedPhoneNumberBytes = SecurityUtil.encryptMessageWithRSA(phoneNumber.getBytes("UTF-8"), publicKey);
                                        final byte[] signatureBytes = SecurityUtil.signData(userKeyPair.getPrivate(), sigString.getBytes("UTF-8"), true);

                                        final JSONObject json = new JSONObject();
                                        json.put("transId", transId);
                                        json.put("recoveryToken", encodedRecoveryToken);
                                        json.put("recoveryChannel", Base64.encodeToString(encryptedPhoneNumberBytes, Base64.NO_WRAP));
                                        json.put("sig", Base64.encodeToString(signatureBytes, Base64.NO_WRAP));

                                        preferencesController.setRecoveryTokenPhone(json.toString());
                                    } else {
                                        preferencesController.setRecoveryTokenPhone(null);
                                    }

                                    final String emailAddress = mApplication.getContactController().getOwnContact().getEmail();
                                    if (!StringUtil.isNullOrEmpty(emailAddress)) {
                                        final byte[] encryptedEmailAddressBytes = SecurityUtil.encryptMessageWithRSA(emailAddress.getBytes("UTF-8"), publicKey);
                                        final byte[] signatureBytes = SecurityUtil.signData(userKeyPair.getPrivate(), sigString.getBytes("UTF-8"), true);

                                        final JSONObject json = new JSONObject();
                                        json.put("transId", transId);
                                        json.put("recoveryToken", encodedRecoveryToken);
                                        json.put("recoveryChannel", Base64.encodeToString(encryptedEmailAddressBytes, Base64.NO_WRAP));
                                        json.put("sig", Base64.encodeToString(signatureBytes, Base64.NO_WRAP));

                                        preferencesController.setRecoveryTokenEmail(json.toString());
                                    } else {
                                        preferencesController.setRecoveryTokenEmail(null);
                                    }
                                    mAccount.setCustomBooleanAttribute(AccountController.RECOVERY_CODE_SET, true);
                                    updateAccoutDao();
                                } else {
                                    LogUtil.e(TAG, "createRecoveryPassword failed");
                                    if (createRecoveryCodeListener != null) {
                                        createRecoveryCodeListener.onCreateFailed(getResources().getString(R.string.settings_recovery_code_creation_failed));
                                    }
                                }
                            } else {
                                LogUtil.e(TAG, "createRecoveryPassword failed");
                                if (createRecoveryCodeListener != null) {
                                    createRecoveryCodeListener.onCreateFailed(getResources().getString(R.string.settings_recovery_code_creation_failed));
                                }
                            }
                        } catch (final LocalizedException | IOException | JSONException e) {
                            LogUtil.e(TAG, e.getMessage(), e);
                            if (createRecoveryCodeListener != null) {
                                createRecoveryCodeListener.onCreateFailed(getResources().getString(R.string.settings_recovery_code_creation_failed));
                            }
                        }
                    }
                };
                try {
                    // pubkey vom server laden
                    BackendService.withSyncConnection(mApplication)
                            .getSimsmeRecoveryPublicKey(listener);
                } catch (Exception e) {
                    LogUtil.w(TAG, "createRecoveryPassword failed", e);
                    if (createRecoveryCodeListener != null) {
                        createRecoveryCodeListener.onCreateFailed(getResources().getString(R.string.settings_recovery_code_creation_failed));
                    }
                }
            }

            @Override
            public void onError() {
                LogUtil.e(TAG, "createRecoveryPassword failed");
                if (createRecoveryCodeListener != null) {
                    createRecoveryCodeListener.onCreateFailed(getResources().getString(R.string.settings_recovery_code_creation_failed));
                }
            }
        };
        // key ableiten, aber ohne minusse
        SecurityUtil.deriveKeyFromPassword(recoveryCode.replace("-", ""), new byte[32], onDeriveKeyCompleteListener, 80000, SecurityUtil.DERIVE_ALGORITHM_SHA_256, false);
    }

    /**
     * entshcluesselung des Recovery Tokens vom server anfordern
     */
    public void requestRecoveryCode(final OnRequestRecoveryCodeListener onRequestRecoveryCodeListener, final String recoveryTokenJson) {
        try {
            final JsonParser jsonParser = new JsonParser();
            final JsonObject jsonObject = jsonParser.parse(recoveryTokenJson).getAsJsonObject();

            final String transId = jsonObject.get("transId").getAsString();
            final String recoveryToken = jsonObject.get("recoveryToken").getAsString();
            final String recoveryChannel = jsonObject.get("recoveryChannel").getAsString();
            final String sig = jsonObject.get("sig").getAsString();

            final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
                @Override
                public void onBackendResponse(final BackendResponse response) {
                    if (response.isError) {
                        onRequestRecoveryCodeListener.onRequestFailed(getResources().getString(R.string.recover_password_recovery_key_error));
                        LogUtil.e(TAG, "requestRecoveryCode failed 1");
                    } else {
                        if (response.jsonArray == null || response.jsonArray.size() == 0) {
                            onRequestRecoveryCodeListener.onRequestFailed(getResources().getString(R.string.recover_password_recovery_key_error));
                            LogUtil.e(TAG, "requestRecoveryCode failed 2");
                        } else {
                            final String transIdServer = response.jsonArray.get(0).getAsString();
                            if (StringUtil.isEqual(transId, transIdServer)) {
                                onRequestRecoveryCodeListener.onRequestSuccess();
                            } else {
                                onRequestRecoveryCodeListener.onRequestFailed(getResources().getString(R.string.recover_password_recovery_key_error));
                                LogUtil.e(TAG, "requestRecoveryCode failed 3");
                            }
                        }
                    }
                }
            };
            BackendService.withAsyncConnection(mApplication)
                    .requestSimsmeRecoveryKey(listener, transId, recoveryToken, recoveryChannel, sig);
        } catch (final JsonSyntaxException e) {
            LogUtil.e(TAG, "requestRecoveryCode failed", e);
        }
    }

    public void searchAccount(@NonNull final String searchText,
                              @NonNull final String searchType,
                              @NonNull final GenericActionListener<Contact> actionListener,
                              @NonNull final String salt,
                              @NonNull final String tenant)
            throws LocalizedException {
        final AsyncHttpTask<Contact> searchTask = new AsyncHttpTask<>(new AsyncHttpTask.AsyncHttpCallback<Contact>() {
            private String dataString;

            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {

                if (StringUtil.isEqual(searchType, JsonConstants.SEARCH_TYPE_PHONE) || StringUtil.isEqual(searchType, JsonConstants.SEARCH_TYPE_EMAIL)) {
                    dataString = BCrypt.hashpw(searchText.toLowerCase(Locale.US), salt);
                } else {
                    dataString = searchText.toUpperCase();
                }
                final JsonArray data = new JsonArray();
                data.add(new JsonPrimitive(dataString));
                BackendService.withSyncConnection(mApplication)
                        .getKnownAccounts(data, salt, tenant, searchType, listener);
            }

            @Override
            public Contact asyncLoaderServerResponse(final BackendResponse response)
                    throws LocalizedException {

                if (response.jsonArray == null || response.jsonArray.size() < 1) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response is null");
                }

                final JsonElement je = response.jsonArray.get(0);

                if (!je.isJsonObject()) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response has wrong format");
                }

                final JsonObject jo = je.getAsJsonObject();

                final String guid;
                if (jo.has(dataString)) {
                    guid = jo.get(dataString).getAsString();
                    final Contact tmpContact = new Contact();
                    tmpContact.setAccountGuid(guid);
                    return tmpContact;
                } else {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "no guid");
                }
            }

            @Override
            public void asyncLoaderFinished(final Contact tmpContact) {
                try {
                    if (tmpContact != null) {
                        searchAccountGetAccountInfo(tmpContact, dataString, searchType, tenant, actionListener);
                    } else {
                        asyncLoaderFailed("no guid");
                    }
                } catch (final LocalizedException le) {
                    asyncLoaderFailed(le.getMessage());
                }
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                actionListener.onFail(errorMessage, "");
            }
        });
        searchTask.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }

    private void searchAccountGetAccountInfo(@NonNull final Contact tmpContact,
                                             @NonNull final String dataString,
                                             @NonNull final String searchType,
                                             @NonNull final String mandant,
                                             @NonNull final GenericActionListener<Contact> actionListener)
            throws LocalizedException {
        final AsyncHttpTask<Contact> searchTask = new AsyncHttpTask<>(new AsyncHttpTask.AsyncHttpCallback<Contact>() {
            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .getAccountInfoAnonymous(dataString, searchType, listener);
            }

            @Override
            public Contact asyncLoaderServerResponse(final BackendResponse response)
                    throws LocalizedException {
                if (response.jsonArray == null || response.jsonArray.size() < 1) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response is null");
                }

                final JsonElement je = response.jsonArray.get(0);

                if (!je.isJsonObject()) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response has wrong format");
                }

                final JsonObject jo = je.getAsJsonObject();

                if (!jo.has(JsonConstants.ACCOUNT)) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response has wrong format");
                }

                final JsonObject accountObject = jo.get(JsonConstants.ACCOUNT).getAsJsonObject();

                final String accountID = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_ID, accountObject);
                if (StringUtil.isNullOrEmpty(accountID)) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Account Id is null");
                }

                tmpContact.setSimsmeId(accountID);

                final String guid = JsonUtil.stringFromJO(JsonConstants.GUID, accountObject);
                if (StringUtil.isNullOrEmpty(guid)) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Guid is null");
                }
                tmpContact.setAccountGuid(guid);

                final String publicKey = JsonUtil.stringFromJO(JsonConstants.PUBLIC_KEY, accountObject);
                if (StringUtil.isNullOrEmpty(publicKey)) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Public Key is null");
                }

                tmpContact.setMandant(mandant);
                tmpContact.setPublicKey(publicKey);
                return tmpContact;
            }

            @Override
            public void asyncLoaderFinished(final Contact result) {
                actionListener.onSuccess(result);
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                actionListener.onFail(errorMessage, null);
            }
        });
        searchTask.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }

    public void unsetRecoveryCode() {
        try {
            if (mAccount.getCustomBooleanAttribute(RECOVERY_CODE_SET)) {
                mAccount.setCustomBooleanAttribute(RECOVERY_CODE_SET, false);
                updateAccoutDao();
                SecurityUtil.deleteKeyFromDisc(mApplication);
            }
        } catch (final LocalizedException le) {
            LogUtil.e(TAG, le.getIdentifier(), le);
        }
    }

    public boolean getManagementCompanyIsUserRestricted()
            throws LocalizedException {
        return false;
    }

    public boolean haveToStartMigration() {
        if (mAccount == null || mAccount.getState() != Account.ACCOUNT_STATE_FULL) {
            return false;
        }
        int migrationVersion = mApplication.getPreferencesController().getMigrationVersion();

        return !mApplication.getPreferencesController().hasOldContactsMerged() || migrationVersion < ACCOUNT_MIGRATION_VERSION || !mApplication.getContactController().existsFtsDatabase();
    }

    public void startMigration(@NonNull final GenericUpdateListener<Integer> listener) {
        mMigrationListener = listener;
        if (mMigrationTask != null) {
            return;
        }

        mMigrationTask = new MigrationTask(mApplication, new GenericUpdateListener<Integer>() {
            @Override
            public void onSuccess(Integer object) {
                mMigrationTask = null;
                mMigrationListener.onSuccess(object);
            }

            @Override
            public void onUpdate(String updateMessage) {
                mMigrationListener.onUpdate(updateMessage);
            }

            @Override
            public void onFail(String message, String errorIdent) {
                mMigrationTask = null;
                mMigrationListener.onFail(message, errorIdent);
            }
        });
        mMigrationTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mApplication.getPreferencesController().getMigrationVersion());
    }

    /**
     * Online-Status auf "typing" setzen
     */
    public void setOnlineStateToTyping(final String chatPartnerGuid) {

        // rennt noch -> return
        if (mCountDownTimer != null) {
            return;
        }

        if (mSendOnlineStateTask != null) {
            mSendOnlineStateTask.cancel(true);
        }
        mSendOnlineStateTask = new SendOnlineStateTask();
        mSendOnlineStateTask.startTask(ONLINE_STATE_TYPING, chatPartnerGuid);
        mCountDownTimer = new CountDownTimer(10000, 1000) {

            public void onTick(long millisUntilFinished) {

            }

            /**
             * nach Ablauf Timer loeschen und Status auf "online" setzen --> Server setzt den Status selbst
             */
            public void onFinish() {
                mCountDownTimer = null;
            }
        }.start();
    }

    public void setOnlineStateToOnline(final String chatPartnerGuid) {
        if (mSendOnlineStateTask != null) {
            mSendOnlineStateTask.cancel(true);
        }
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
            mCountDownTimer = null;
        }
        mSendOnlineStateTask = new SendOnlineStateTask();
        mSendOnlineStateTask.startTask(ONLINE_STATE_ONLINE, chatPartnerGuid);
    }

    public void validateOwnEmailAsync(@NonNull final GenericActionListener<Void> listener)
            throws LocalizedException {
        final Contact ownContact = mApplication.getContactController().getOwnContact();
        if (ownContact == null) {
            throw new LocalizedException(LocalizedException.NOT_OWN_CONTACT, "Contact is null");
        }

        final String email = ownContact.getEmail();

        if (StringUtil.isNullOrEmpty(email)) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "No mail adress");
        }

        ValidateEmailTask task = new ValidateEmailTask(mApplication, email, new GenericActionListener<String>() {
            @Override
            public void onSuccess(String domain) {
                if (!StringUtil.isNullOrEmpty(domain)) {
                    try {
                        ownContact.setDomain(domain);
                        mApplication.getContactController().insertOrUpdateContact(ownContact);
                    } catch (LocalizedException e) {
                        listener.onFail(e.getMessage(), e.getIdentifier());
                        return;
                    }
                }

                listener.onSuccess(null);
            }

            @Override
            public void onFail(String message, String errorIdent) {
                listener.onFail(message, errorIdent);
            }
        });

        task.executeOnExecutor(ValidateEmailTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * KS: Following is all the rest of optIn code to keep for now.
     * Put it all together and will remove later.
     */

    public static final String OPT_IN_STATE_OBJECT = "OptInState";
    public static final String OPT_IN_STATE = "state";

    public interface OptInStateListener {
        void optInStateSuccess();

        void optInStateFailed();
    }

    public void setOptInState(final String state, @Nullable final OptInStateListener optInStateListener) {
        IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    if (optInStateListener != null) {
                        optInStateListener.optInStateFailed();
                    }
                    return;
                }

                if (response.jsonObject != null) {
                    JsonObject jo = JsonUtil.jsonObjectFromJO(OPT_IN_STATE_OBJECT, response.jsonObject);
                    if (jo == null) {
                        if (optInStateListener != null) {
                            optInStateListener.optInStateFailed();
                        }
                        return;
                    }
                    String responseOptInState = JsonUtil.stringFromJO(OPT_IN_STATE, jo);
                    if (!StringUtil.isNullOrEmpty(responseOptInState) && StringUtil.isEqual(responseOptInState, state)) {
                        if (optInStateListener != null) {
                            optInStateListener.optInStateSuccess();
                        }
                    } else {
                        if (optInStateListener != null) {
                            optInStateListener.optInStateFailed();
                        }
                    }
                } else {
                    if (optInStateListener != null) {
                        optInStateListener.optInStateFailed();
                    }
                }
            }
        };

        BackendService.withAsyncConnection(mApplication)
                .setOptInState(state, listener);
    }

    /**
     * [!CLASS_DESCRIPTION!]
     *
     * @author Florian
     * @version $Revision$, $Date$, $Author$
     */
    @SuppressLint("StaticFieldLeak")
    //die Tasks werden vom controller selbst gestartet.
    //dieser ist ueber die gesamte Laufzeit aktiv, wird also niemals garbagecollected
    private class SendOnlineStateTask
            extends AsyncTask<String, Void, String>
            //extends AsyncTask<String, Void, String>
    {

        SendOnlineStateTask() {
        }

        /**
         * startet den Task
         *
         * @param params Guid des Empfaenger
         */
        void startTask(final String... params) {
            executeOnExecutor(ONLINE_STATE_EXECUTOR, params);
        }

        @Override
        protected String doInBackground(final String... params) {

            final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {

                @Override
                public void onBackendResponse(final BackendResponse response) {
                    //TODO
                }
            };

            if (params == null
                    || params.length < 2
                    || StringUtil.isNullOrEmpty(params[0])
                    || StringUtil.isNullOrEmpty(params[1])
            ) {
                //todo
                return null;
            } else if (ONLINE_STATE_TYPING.equals(params[0])) {
                BackendService.withSyncConnection(mApplication)
                        .setIsWriting(params[1], listener);
            } else if (ONLINE_STATE_ONLINE.equals(params[0])) {
                BackendService.withSyncConnection(mApplication)
                        .resetIsWriting(params[1], listener);
            }
            return null;
        }
    }

    private static class ValidateEmailTask extends AsyncTask<Void, Void, Void> {
        private final SimsMeApplication mApp;
        private final String mEmail;
        private final GenericActionListener<String> mListener;
        private ResponseModel mResponseModel;
        private String mDomain;

        ValidateEmailTask(@NonNull final SimsMeApplication app, @NonNull final String email, @NonNull final GenericActionListener<String> listener) {
            mApp = app;
            mEmail = email;
            mListener = listener;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            IBackendService.OnBackendResponseListener listenerValidateMail = new IBackendService.OnBackendResponseListener() {
                @Override
                public void onBackendResponse(BackendResponse response) {
                    if (response.isError) {
                        mResponseModel = new ResponseModel();
                        mResponseModel.setError(response);
                    } else {
                        if (response.jsonArray != null
                                && response.jsonArray.size() > 0) {
                            JsonElement je = response.jsonArray.get(0);

                            if (je.isJsonNull() || !je.isJsonPrimitive()) {
                                return;
                            }

                            String result = je.getAsString();
                            if (!StringUtil.isNullOrEmpty(result) && mEmail.contains(result)) {
                                mDomain = result;
                            }
                        }
                    }
                }
            };

            BackendService.withSyncConnection(mApp)
                    .validateMail(mEmail, mApp.getAccountController().getAccountState() >= Account.ACCOUNT_STATE_CONFIRMED, listenerValidateMail);

            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (mResponseModel != null && mResponseModel.isError) {
                //Freemailer -> kein Fehler
                if (StringUtil.isEqual(LocalizedException.BLACKLISTED_EMAIL_DOMAIN, mResponseModel.errorIdent)) {
                    mListener.onSuccess(null);
                } else {
                    mListener.onFail(mResponseModel.errorMsg, mResponseModel.errorIdent);
                }
                return;
            }

            if (!StringUtil.isNullOrEmpty(mDomain)) {
                mListener.onSuccess(mDomain);
            } else {
                mListener.onFail(null, LocalizedException.UNKNOWN_ERROR);
            }
        }
    }

    //die Tasks werden vom controller selbst gestartet.
    //dieser ist ueber die gesamte Laufzeit aktiv, wird also niemals garbagecollected
    private class AsyncBackupKeysTask extends AsyncTask<Void, Void, LocalizedException> {

        private final String mPassword;

        private final int mDeriveIterations;

        private final AsyncBackupKeysCallback mCallback;

        AsyncBackupKeysTask(final AsyncBackupKeysCallback callback, String password,
                            int deriveIterations) {
            mPassword = password;
            mDeriveIterations = deriveIterations;
            mCallback = callback;
        }

        @Override
        protected LocalizedException doInBackground(final Void... params) {
            try {
                byte[] salt = SecurityUtil.generateSalt();
                SecretKey key = SecurityUtil
                        .deriveKeyFromPasswordOnSameThread(mPassword, salt, mDeriveIterations);

                byte[] aesKeyBytes = key.getEncoded();

                String base64Key = Base64.encodeToString(aesKeyBytes, Base64.NO_WRAP);
                String base64Salt = Base64.encodeToString(salt, Base64.NO_WRAP);
                PreferencesController preferencesController = mApplication.getPreferencesController();
                preferencesController.saveBackupKeyInfo(base64Key, base64Salt, mDeriveIterations);

                return null;
            } catch (LocalizedException e) {
                LogUtil.w(TAG, e.getMessage(), e);
                return e;
            }
        }

        @Override
        protected void onPostExecute(final LocalizedException exception) {
            if (exception != null) {
                mCallback.asyncBackupKeysFailed(exception);
            } else {
                mCallback.asyncBackupKeysFinished();
            }
        }
    }

    private class BackupStateReceiver extends BroadcastReceiver {
        private int mChatsSize;

        public BackupStateReceiver() {
            //
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getIntExtra(AppConstants.INTENT_EXTENDED_DATA_STATUS, -1)) {
                case AppConstants.STATE_ACTION_BACKUP_STARTED: {
                    setBackupState(CreateBackupListener.STATE_STARTED);
                    break;
                }
                case AppConstants.STATE_ACTION_BACKUP_START_SAVE_CHATS:
                case AppConstants.STATE_ACTION_BACKUP_START_SAVE_SERVICES: {
                    mChatsSize = intent.getIntExtra(AppConstants.INTENT_EXTENDED_DATA_EXTRA, -1);
                    setBackupState(CreateBackupListener.STATE_SAVE_CHATS);
                    break;
                }
                case AppConstants.STATE_ACTION_BACKUP_UPDATE_SAVE_CHATS: {
                    int currentChat = intent.getIntExtra(AppConstants.INTENT_EXTENDED_DATA_EXTRA, -1);
                    if (currentChat > -1) {
                        updateListenerForSaveChatsState(currentChat, mChatsSize);
                    }
                    break;
                }
                case AppConstants.STATE_ACTION_BACKUP_SAVE_BU_FILE: {
                    setBackupState(CreateBackupListener.STATE_WRITE_BACKUP_FILE);
                    break;
                }
                case AppConstants.STATE_ACTION_BACKUP_FINISHED: {
                    String path = intent.getStringExtra(AppConstants.INTENT_EXTENDED_DATA_EXTRA);
                    if (StringUtil.isNullOrEmpty(path) || !(new LocalBackupHelper(mApplication).copyBackupToFinalPath(path))) {
                        setBackupState(CreateBackupListener.STATE_ERROR);
                        return;
                    }
                    setBackupState(CreateBackupListener.STATE_FINISHED);
                    break;
                }

                case AppConstants.STATE_ACTION_BACKUP_ERROR: {
                    setBackupState(CreateBackupListener.STATE_ERROR);
                    break;
                }
                default: {
                    LogUtil.w(TAG, LocalizedException.UNDEFINED_ARGUMENT);
                    break;
                }
            }
        }
    }

    private ArrayList<CreateBackupListener> backupListeners = new ArrayList<>();

    private void setBackupState(int state) {
        if (state == CreateBackupListener.STATE_STARTED) {
            mApplication.getLoginController().setPKTaskIsRunning(this, true);
        }

        if (state == CreateBackupListener.STATE_ERROR) {
            for (CreateBackupListener listener : backupListeners) {
                listener.onCreateBackupFailed("", false);
            }
        } else {
            for (CreateBackupListener listener : backupListeners) {
                listener.onCreateBackupStateChanged(state);
            }
        }

        if (state == CreateBackupListener.STATE_FINISHED
                || state == CreateBackupListener.STATE_ERROR) {
            mApplication.getLoginController().setPKTaskIsRunning(this, false);
            mIsBackupStarted = false;
        }
    }

    protected class CreateAccountModel {
        String password;

        boolean isSimplePassword;

        boolean isPwdOnStartEnabled;

        AccountModel accountModel;

        DeviceModel deviceModel;

        Account accountDaoModel;

        List<String> allServerAccountIDs;

        String normalizedPhoneNumber;

        String emailAddress;
    }

    private class CoupleDeviceModel {
        String password;

        boolean isSimplePassword;

        boolean isPwdOnStartEnabled;

        String deviceName;

        AccountModel accountModel;

        DeviceModel deviceModel;

        Account accountDaoModel;

        String couplingTransactionID;

        CouplingResponseModel couplingResponse;
    }

    private class CouplingResponseModel {
        String transId;
        String publicKey;
        String publicKeySig;
        String kek;
        String kekIV;
        String encSyncData;
        String appData;
        String signature;

        CouplingResponseModel(JsonObject crespJO)
                throws LocalizedException {
            if (crespJO == null) {
                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "JsonObject is null");
            }

            transId = getStringFromJson(JsonConstants.TRANS_ID, crespJO);
            publicKey = getStringFromJson(JsonConstants.DEVICE, crespJO);
            publicKeySig = getStringFromJson(JsonConstants.DEVICE_KEY_SIG, crespJO);
            kek = getStringFromJson(JsonConstants.KEK, crespJO);
            kekIV = getStringFromJson(JsonConstants.KEK_IV, crespJO);
            encSyncData = getStringFromJson(JsonConstants.ENC_SYNC_DATA, crespJO);
            appData = getStringFromJson(JsonConstants.APP_DATA, crespJO);
            signature = getStringFromJson(JsonConstants.SIG, crespJO);
        }

        private String getStringFromJson(String key, JsonObject object)
                throws LocalizedException {
            if (!JsonUtil.hasKey(key, object)) {
                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, key + " is null");
            }

            return JsonUtil.stringFromJO(key, object);
        }

        boolean checkSignature(@NonNull final String accountPublicKey) {
            try {
                PublicKey key = XMLUtil.getPublicKeyFromXML(accountPublicKey);

                if (key == null) {
                    return false;
                }

                String concatSignature = transId + publicKey + publicKeySig + kek + kekIV + encSyncData + appData;

                byte[] sigBytes = Base64.decode(signature, Base64.NO_WRAP);
                byte[] concatBytes = concatSignature.getBytes(Encoding.UTF8);

                return SecurityUtil.verifyData(key, sigBytes, concatBytes, true);
            } catch (UnsupportedEncodingException | LocalizedException e) {
                LogUtil.w(getClass().getSimpleName(), "checkSignature()", e);
                return false;
            }
        }
    }
}
