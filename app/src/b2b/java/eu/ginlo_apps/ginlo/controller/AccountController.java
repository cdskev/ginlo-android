// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.task.AsyncHttpTask;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.contracts.AcceptOrDeclineCompanyManagementCallback;
import eu.ginlo_apps.ginlo.controller.contracts.ConfigureCompanyAccountListener;
import eu.ginlo_apps.ginlo.controller.contracts.CreateRecoveryCodeListener;
import eu.ginlo_apps.ginlo.controller.contracts.HasCompanyManagementCallback;
import eu.ginlo_apps.ginlo.controller.contracts.OnCompanyLayoutChangeListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnCompanyLogoChangeListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnCreateAccountListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnGetPurchasedProductsListener;
import eu.ginlo_apps.ginlo.controller.contracts.OnRegisterVoucherListener;
import eu.ginlo_apps.ginlo.controller.contracts.PhoneOrEmailActionListener;
import eu.ginlo_apps.ginlo.controller.contracts.SetAddressInfoListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.CompanyContact;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.AccountModel;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.CompanyLayoutModel;
import eu.ginlo_apps.ginlo.model.backend.DeviceModel;
import eu.ginlo_apps.ginlo.model.backend.action.Action;
import eu.ginlo_apps.ginlo.model.backend.action.CompanyEncryptInfoAction;
import eu.ginlo_apps.ginlo.model.backend.action.RequestConfirmEmailAction;
import eu.ginlo_apps.ginlo.model.backend.action.RequestConfirmPhoneAction;
import eu.ginlo_apps.ginlo.model.backend.serialization.AccountModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.DeviceModelSerializer;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.ConfigureCompanyService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.BaManagedConfigUtil;
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil;
import eu.ginlo_apps.ginlo.util.ConfigUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import eu.ginlo_apps.ginlo.util.XMLUtil;

import static eu.ginlo_apps.ginlo.model.constant.JsonConstants.CHECKSUM;
import static eu.ginlo_apps.ginlo.model.constant.JsonConstants.COMPANY_COMPANY;
import static eu.ginlo_apps.ginlo.model.constant.JsonConstants.COMPANY_COMPANY_LAYOUT;
import static eu.ginlo_apps.ginlo.model.constant.JsonConstants.COMPANY_COMPANY_LOGO;
import static eu.ginlo_apps.ginlo.model.constant.JsonConstants.COMPANY_STATE;
import static eu.ginlo_apps.ginlo.model.constant.JsonConstants.COMPANY_TEST_VOUCHER_AVAILABLE;
import static eu.ginlo_apps.ginlo.model.constant.JsonConstants.DATA;
import static eu.ginlo_apps.ginlo.model.constant.JsonConstants.DAYSLEFT;
import static eu.ginlo_apps.ginlo.model.constant.JsonConstants.IDENT;
import static eu.ginlo_apps.ginlo.model.constant.JsonConstants.IV;
import static eu.ginlo_apps.ginlo.model.constant.JsonConstants.USAGE;
import static eu.ginlo_apps.ginlo.model.constant.JsonConstants.VALID;

public class AccountController extends AccountControllerBase implements PreferencesController.OnServerVersionChangedListener {
    private static final String TAG = AccountController.class.getSimpleName();

    private static final String EMAIL_ATTRIBUTES_ADDRESS_INFO_V1 = "AdressInformation-v1";

    private static final String EMAIL_DOMAIN_PRE_22 = "AccountControllerBusiness.eMailDomain";

    private static final String PENDING_EMAIL_DOMAIN = "AccountControllerBusiness.pending.eMailDomain";

    private static final String TRIAL_USAGE = "AccountControllerBusiness.trialUsage";

    private static final String EMAIL_VERIFICATION_TRIES_LEFT = "verificationTriesLeft";

    private static final int EMAIL_VERIFICATION_TRIES_DEFAULT = 10;

    private static final String MC_NAME = "name";
    private static final String MC_GUID = "guid";

    private static final String MC_ENCRYPTION_SALT = "mc_encryptionSalt";

    private static final String MC_ENCRYPTION_SEED = "mc_encryptionSeed";

    private static final String MC_USER_RESTRICTED_INDEX = "userRestrictedIndex";

    private static final String MC_ENCRYPTION_SEED_SUFFIX = "mc_encryptionSeedPrefix";
    private static final String MC_ENCRYPTION_EMAIL = "mc_encryptionMail";
    private static final String MC_ENCRYPTION_PHONE = "mc_encryptionPhonenumber";
    private static final String MC_ENCRYPTION_DIFF = "mc_encryptionDiff";

    private static final String TOOLBAR_LOGO_CHECKSUM = "AccountController.toolbarLogoBusinessChecksum";
    private static final String PENDING_EMAIL_ADDRESS = "AccountController.pendingEmailNumber";
    private static final String PENDING_EMAIL_STATUS = "AccountController.pendingEmailStatus";
    private static final String PENDING_EMAIL_STATUS_NONE = "AccountController.pendingEmailNumber.none";
    private static final String PENDING_EMAIL_STATUS_CONFIRMED = "AccountController.pendingEmailNumber.confirmed";
    private static final String MC_STATE_ACCOUNT_PENDING_VALIDATION = "ManagedAccountPendingValidation";
    public static final String PENDING_EMAIL_STATUS_WAIT_REQUEST = "AccountController.pendingEmailNumber.wait.request";
    public static final String PENDING_EMAIL_STATUS_WAIT_CONFIRM = "AccountController.pendingEmailNumber.wait.confirm";
    public static final String MC_STATE_ACCOUNT_NEW = "ManagedAccountNew";
    public static final String MC_STATE_ACCOUNT_ACCEPTED = "ManagedAccountAccepted";
    /**
     * wert fuer backup
     */
    private static final String MC_COMPANY_DOMAIN = "companyDomain";

    public static final String COMPANY_LOGO_FILENAME = "companyLogo";

    private static final String MC_COMPANY_MDM_CONFIG = "CompanyMdmConfig";

    private static final String MC_APP_CONFIG = "AppConfig";

    public static final String MC_ACCOUNT_IS_MANAGED = "mc_account_is_managed";

    private static final String MC_REGISTERED_EMAIL = "mc_registered_email";

    private List<OnCompanyLayoutChangeListener> mLayoutChangeListeners;

    private OnCompanyLogoChangeListener mLogoChangeListener;

    private SecretKey mCompanyAesKey;
    private SecretKey mCompanyUserDataAesKey;

    private SecretKey mDomainAesKey;

    private int mTrialsDaysLeft = -1;

    private AsyncHttpTask<Long> mRegisterTestVoucherTask;

    /**
     * wurde beim aktuellen App-start bereits mindestens einmal die Passwort-Recovery-Activity gelauncht
     * die Loginactivity erzeugt immer eine neue Instanz, daher kann der Wert dort nicht gehalten werden
     * Das muss irgendwo festgehalten werden. Der PreferencesController ist bisher noch nicht ueberladen, daher erstmal hier rein
     * <p>
     * FIXME schnelle Loesung - darf gerne verbessert werden.
     */
    private boolean mHasLaunchedRecoveryActivity = false;

    public AccountController(final SimsMeApplication application) {
        super(application);

        application.getPreferencesController().registerServerChangedListener(ConfigUtil.SERVER_VERSION_GET_COMPANY, this);
        application.getPreferencesController().registerServerChangedListener(ConfigUtil.SERVER_VERSION_GET_COMPANY_LAYOUT, this);
        application.getPreferencesController().registerServerChangedListener(ConfigUtil.SERVER_VERSION_HAS_COMPANY_MANAGEMENT, this);
        application.getPreferencesController().registerServerChangedListener(ConfigUtil.SERVER_VERSION_GET_COMPANY_APP_SETTINGS, this);
        application.getPreferencesController().registerServerChangedListener(ConfigUtil.SERVER_VERSION_GET_CONFIRMED_IDENTITIES, this);
        application.getPreferencesController().registerServerChangedListener(ConfigUtil.SERVER_VERSION_GET_PUBLIC_ONLINE_STATE, this);
    }

    @Override
    protected void registerJsonAdapters() {
        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(AccountModel.class, new AccountModelSerializer());
        gsonBuilder.registerTypeAdapter(DeviceModel.class, new DeviceModelSerializer());
        gson = gsonBuilder.create();
    }

    public boolean isManagementStateRequired(final String mcState) {
        return StringUtil.isEqual(MC_STATE_ACCOUNT_ACCEPTED_EMAIL_REQUIRED, mcState)
                || StringUtil.isEqual(MC_STATE_ACCOUNT_ACCEPTED_PHONE_REQUIRED, mcState)
                || StringUtil.isEqual(MC_STATE_ACCOUNT_ACCEPTED_EMAIL_FAILED, mcState)
                || StringUtil.isEqual(MC_STATE_ACCOUNT_ACCEPTED_PHONE_FAILED, mcState);
    }

    /**
     * Achtung Methode erzeugt Task und dann gegen asynchron auf den Listener zu
     */
    public void getAddressInformationForOwnAccountPre22(final ContactControllerBusiness.GetAddressInformationsListener getAddressInformationListener) {

        // Für die Migration eines Backups Pre 2.2 -> 2.2
        final String domain = getEmailDomainPre22();
        if (StringUtil.isNullOrEmpty(domain)) {
            if (getAddressInformationListener != null) {
                getAddressInformationListener.onFail();
            }
            return;
        }

        try {
            final String ownAccountGuid = mAccount.getAccountGuid();
            final String simsmeID = mAccount.getAccountID();
            final SecretKey domainKey = getDomainAesKey();

            final AsyncHttpTask<Void> task = new AsyncHttpTask<>(new AsyncHttpTask.AsyncHttpCallback<Void>() {
                @Override
                public void asyncLoaderServerRequest(IBackendService.OnBackendResponseListener listener) {
                    BackendService.withSyncConnection(mApplication)
                            .getAddressInformationBatch(ownAccountGuid, listener);
                }

                @Override
                public Void asyncLoaderServerResponse(BackendResponse response)
                        throws LocalizedException {
                    if (response.jsonArray == null || response.jsonArray.size() < 1) {
                        throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "response is null");
                    }

                    final JsonObject jObj = response.jsonArray.get(0).getAsJsonObject();
                    if (jObj != null) {
                        final JsonObject addInfoObj = jObj.has("AdressInformation") ? jObj.get("AdressInformation").getAsJsonObject() : null;
                        if (addInfoObj != null) {
                            final String keyIv = addInfoObj.has("key-iv") ? addInfoObj.get("key-iv").getAsString() : null;
                            final String publicKey = addInfoObj.has("publicKey") ? addInfoObj.get("publicKey").getAsString() : null;
                            final String data = addInfoObj.has("data") ? addInfoObj.get("data").getAsString() : null;
                            if (!StringUtil.isNullOrEmpty(data) && !StringUtil.isNullOrEmpty(keyIv) && !StringUtil.isNullOrEmpty(publicKey)) {
                                final CompanyContact companyContact = new CompanyContact(null, ownAccountGuid, null, keyIv, data.getBytes(), publicKey, null, simsmeID);

                                final String firstName = companyContact.getEncryptedAttribute(domainKey, CompanyContact.COMPANY_CONTACT_FIRSTNAME);
                                final String lastName = companyContact.getEncryptedAttribute(domainKey, CompanyContact.COMPANY_CONTACT_LASTNAME);
                                final String email = companyContact.getEncryptedAttribute(domainKey, CompanyContact.COMPANY_CONTACT_EMAIL);

                                Contact ownContact = mApplication.getContactController().getOwnContact();
                                mApplication.getContactController().saveContactInformation(ownContact, lastName, firstName, null, email, null, null, null, null, -1, false);
                            }
                        }
                    }
                    return null;
                }

                @Override
                public void asyncLoaderFinished(Void result) {
                    if (getAddressInformationListener != null) {
                        getAddressInformationListener.onSuccess();
                    }
                }

                @Override
                public void asyncLoaderFailed(String errorMessage) {
                    if (getAddressInformationListener != null) {
                        getAddressInformationListener.onFail();
                    }
                }
            });

            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } catch (LocalizedException e) {
            if (getAddressInformationListener != null) {
                getAddressInformationListener.onFail();
            }
        }
    }

    @Nullable
    public String getEmailDomainPre22() {
        try {
            return mAccount.getCustomStringAttribute(EMAIL_DOMAIN_PRE_22);
        } catch (final LocalizedException e) {
            throw new RuntimeException(TAG, e);
        }
    }

    @Nullable
    @Override
    public SecretKey getDomainAesKey()
            throws LocalizedException {
        if (mDomainAesKey != null) {
            return mDomainAesKey;
        }

        String domain = mApplication.getContactController().getOwnContact().getDomain();

        if (StringUtil.isNullOrEmpty(domain)) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Domain is null");
        }

        String domainKeyBase64 = getAccount().getCustomStringAttribute(JsonConstants.DOMAIN_KEY);

        if (!StringUtil.isNullOrEmpty(domainKeyBase64)) {
            mDomainAesKey = SecurityUtil.getAESKeyFromBase64String(domainKeyBase64);

            return mDomainAesKey;
        }

        // TODO: What is this BS?
        mDomainAesKey = SecurityUtil.deriveKeyFromPassword(domain, new byte[32], 80000, SecurityUtil.DERIVE_ALGORITHM_SHA_256);

        if (mDomainAesKey != null) {
            getAccount().setCustomStringAttribute(JsonConstants.DOMAIN_KEY, SecurityUtil.getBase64StringFromAESKey(mDomainAesKey));
            saveOrUpdateAccount(getAccount());
        }

        return mDomainAesKey;
    }

    public void resetDomainKey() {
        mDomainAesKey = null;
        if (getAccount() != null) {
            try {
                getAccount().setCustomStringAttribute(JsonConstants.DOMAIN_KEY, "");
            } catch (LocalizedException e) {
                LogUtil.w(getClass().getSimpleName(), "", e);
            }
        }
    }

    /**
     * Receive all purchased products for the current account to look for a valid license.
     * At least one available product must be a valid license to keep the account active.
     *
     * @param onGetPurchasedProductsListener
     */
    public void getPurchasedProducts(@Nullable final OnGetPurchasedProductsListener onGetPurchasedProductsListener) {
        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(final BackendResponse response) {
                    if (response.isError) {
                    LogUtil.w(TAG, "getPurchasedProducts: Got error from backend: " + response.errorMessage);
                    if (onGetPurchasedProductsListener != null) {
                        if (response.msgException != null && StringUtil.isEqual(response.msgException.getIdent(), "AND-0055")) {
                            // No such account found on server
                            onGetPurchasedProductsListener.onGetPurchasedProductsFail(response.msgException.getIdent());
                        } else {
                            onGetPurchasedProductsListener.onGetPurchasedProductsFail(response.errorMessage);
                        }
                    }
                } else {
                    if (response.jsonArray != null) {
                        LogUtil.d(TAG, "getPurchasedProducts: Got product info from backend - validating ... " + response.jsonArray.toString());
                        if (response.jsonArray.size() > 0) {
                            boolean bHasUsage = false;
                            for (int i = 0; i < response.jsonArray.size(); i++) {
                                final JsonObject jsonObject = response.jsonArray.get(i).getAsJsonObject();
                                if (jsonObject != null) {
                                    LogUtil.d(TAG, "getPurchasedProducts: Checking " + jsonObject);
                                    if (jsonObject.has("ident")) {
                                        final String ident = jsonObject.get("ident").getAsString();

                                        if (StringUtil.isEqual(ident, "usage") && jsonObject.has("valid")) {
                                            LogUtil.d(TAG, "getPurchasedProducts: Got purchased product: " + jsonObject.toString());
                                            final String date = jsonObject.get("valid").getAsString();
                                            final long dateLong = DateUtil.utcWithoutMillisStringToMillis(date);
                                            bHasUsage = true;

                                            try {
                                                final Long oldDate = mAccount.getLicenceDate();
                                                // KS: If we have more than one product, always keep the expiration date of the product
                                                // with the longest term.
                                                if (oldDate == null || oldDate < dateLong) {
                                                    mAccount.setLicenceDate(dateLong);
                                                    ((ContactControllerBusiness) mApplication.getContactController()).resetLicenseDaysLeft();
                                                    mAccount.setAutorenewingLicense(jsonObject.has("autorenewing"));
                                                    mAccount.resetCustomLongAttribute(TRIAL_USAGE);

                                                    saveOrUpdateAccount(mAccount);
                                                }
                                                LogUtil.i(TAG, "getPurchasedProducts: License ends " + new Date(mAccount.getLicenceDate())
                                                        + ", AutoRenewing is " + mAccount.isAutorenewingLicense()
                                                        + ", HasLicense is " + mAccount.getHasLicence());
                                            } catch (final LocalizedException e) {
                                                LogUtil.e(TAG, "getPurchasedProducts: Updating license account information failed with " + e.getMessage());
                                            }
                                        }
                                    }
                                }
                            }

                            if (!bHasUsage) {
                                LogUtil.w(TAG, "getPurchasedProducts: No useable license found.");
                                try {
                                    mAccount.setLicenceDate(null);
                                    ((ContactControllerBusiness) mApplication.getContactController()).resetLicenseDaysLeft();
                                    saveOrUpdateAccount(mAccount);
                                } catch (final LocalizedException e) {
                                    LogUtil.e(TAG, "getPurchasedProducts: bHasUsage = false - saveOrUpdateAccount() failed with " + e.getMessage());
                                }

                            }
                        } else {
                            // null-response - no license on backend or license has been reset.
                            LogUtil.w(TAG, "getPurchasedProducts: No license information available.");

                            try {
                                mAccount.setLicenceDate(null);
                                ((ContactControllerBusiness) mApplication.getContactController()).resetLicenseDaysLeft();
                                saveOrUpdateAccount(mAccount);
                            } catch (final LocalizedException e) {
                                LogUtil.e(TAG, "getPurchasedProducts: Got null response - saveOrUpdateAccount() failed with " + e.getMessage());
                            }

                        }
                    }
                    if (onGetPurchasedProductsListener != null) {
                        onGetPurchasedProductsListener.onGetPurchasedProductsSuccess();
                    }
                }
            }
        };

        BackendService.withAsyncConnection(mApplication)
                .getPurchasedProducts(listener);
    }

    public void setAddressInformation(final SetAddressInfoListener listener) {
        AsyncTask<Void, Void, String> task = new AsyncTask<Void, Void, String>() {
            String mErrorText = null;
            String mMailAdd = null;

            @Override
            protected String doInBackground(Void... params) {
                try {
                    final JsonObject jsonObject = new JsonObject();

                    final JsonObject jo = new JsonObject();
                    Contact ownContact = mApplication.getContactController().getOwnContact();
                    jo.addProperty("firstname", ownContact.getFirstName());
                    jo.addProperty("name", ownContact.getLastName());
                    jo.addProperty("email", ownContact.getEmail());

                    jsonObject.add(EMAIL_ATTRIBUTES_ADDRESS_INFO_V1, jo);

                    final String domain = mApplication.getContactController().getOwnContact().getDomain();

                    if (!StringUtil.isNullOrEmpty(domain)) {
                        //SecurityUtil.deriveKeyFromPassword(domain, new byte[32], onDeriveKeyCompleteListener, 80000, SecurityUtil.DERIVE_ALGORITHM_SHA_256, false);

                        try {

                            ContactControllerBusiness contactController = (ContactControllerBusiness) mApplication.getContactController();

                            if (needEmailRegistrationForManaging()) {
                                mMailAdd = contactController.getOwnContact().getEmail();
                            } else {
                                getDomainAesKey();
                                final IBackendService.OnBackendResponseListener responseListener = new IBackendService.OnBackendResponseListener() {
                                    @Override
                                    public void onBackendResponse(final BackendResponse response) {
                                        if (!response.isError) {
                                            mMailAdd = jo.has(EMAIL_ATTRIBUTES_EMAIL) ? jo.get(EMAIL_ATTRIBUTES_EMAIL).getAsString() : null;
                                        } else {
                                            if (response.msgException != null) {
                                                mErrorText = response.msgException.getIdent();
                                            } else {
                                                mErrorText = getResources().getString(R.string.unexpected_error_title_next);
                                            }
                                        }
                                    }
                                };

                                final IvParameterSpec iv = SecurityUtil.generateIV();
                                final byte[] ivBytes = iv.getIV();
                                final String ivString = Base64.encodeToString(ivBytes, Base64.NO_WRAP);

                                final byte[] data = SecurityUtil.encryptMessageWithAES(jsonObject.toString().getBytes(), getDomainAesKey(), iv);
                                final String encodedData = Base64.encodeToString(data, Base64.NO_WRAP);

                                final JsonObject root = new JsonObject();

                                root.addProperty("guid", mAccount.getAccountGuid());
                                root.addProperty("key-iv", ivString);
                                root.addProperty("data", encodedData);

                                final JsonObject jObj = new JsonObject();
                                jObj.add("AdressInformation", root);

                                BackendService.withSyncConnection(mApplication)
                                        .setAddressInformation(jObj.toString(), responseListener);
                            }
                        } catch (LocalizedException e) {
                            LogUtil.e(TAG, e.getMessage(), e);
                            mErrorText = getResources().getString(R.string.unexpected_error_title_next);
                        }
                    }
                } catch (final LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                    mErrorText = getResources().getString(R.string.unexpected_error_title_next);
                }

                return mMailAdd;
            }

            @Override
            protected void onPostExecute(String s) {
                if (listener != null) {
                    if (!StringUtil.isNullOrEmpty(mErrorText)) {
                        listener.onFail(mErrorText);
                    } else {
                        if (!StringUtil.isNullOrEmpty(s)) {
                            listener.onSuccess(getResources().getString(R.string.dialog_email_verfication_succeeded, s));
                        } else {
                            listener.onSuccess(null);
                        }
                    }
                }
            }
        };

        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    }

    public boolean hasEmailActivated()
            throws LocalizedException {
        return (!StringUtil.isNullOrEmpty(mApplication.getContactController().getOwnContact().getEmail()));
    }

    public void requestConfirmationMail(final String email, final PhoneOrEmailActionListener listener, boolean forceCreation) {
        final IBackendService.OnBackendResponseListener responseListener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(final BackendResponse response) {
                if (!response.isError) {
                    if (response.jsonArray == null || response.jsonArray.size() < 1) {
                        if (listener != null) {
                            listener.onFail(getResources().getString(R.string.service_tryAgainLater), false);
                        }
                        LogUtil.w(AccountController.class.getSimpleName(), "requestConfirmationMail: Response is null!");
                        return;
                    }

                    String result = response.jsonArray.get(0).getAsString();
                    LogUtil.d(AccountController.class.getSimpleName(), "requestConfirmationMail: Response is " + response.jsonArray.get(0).toString());

                    try {
                        mAccount.setCustomStringAttribute(PENDING_EMAIL_ADDRESS, email);
                        mAccount.setCustomStringAttribute(PENDING_EMAIL_DOMAIN, result);

                        // wenn die email-adresse gerade geaendert wird
                        if (AccountController.PENDING_EMAIL_STATUS_WAIT_REQUEST.equals(getPendingEmailStatus())) {
                            mAccount.setCustomStringAttribute(PENDING_EMAIL_STATUS, PENDING_EMAIL_STATUS_WAIT_CONFIRM);
                        }
                        mAccount.setCustomIntegerAttribute(EMAIL_VERIFICATION_TRIES_LEFT, EMAIL_VERIFICATION_TRIES_DEFAULT);
                        saveOrUpdateAccount(mAccount);
                        if (listener != null) {
                            listener.onSuccess(result);
                        }
                    } catch (final LocalizedException e) {
                        LogUtil.e(TAG, e.toString());
                    }
                } else {
                    if (listener == null) {
                        return;
                    }

                    if (response.msgException == null || StringUtil.isNullOrEmpty(response.msgException.getIdent())) {
                        listener.onFail(getResources().getString(R.string.service_tryAgainLater), false);
                        return;
                    }

                    if (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0123")) {
                        listener.onFail(getResources().getString(R.string.service_ERR_0123), false);
                    } else if (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0124")) {
                        listener.onFail(response.msgException.getIdent(), false);
                    } else if (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0128")) {
                        listener.onFail(getResources().getString(R.string.service_ERR_0128), true);
                    } else if (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0130")) {
                        listener.onFail(getResources().getString(R.string.service_ERR_0130), false);
                    } else if (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0150")) {
                        listener.onFail(getResources().getString(R.string.service_ERR_0150), false);
                    } else {
                        listener.onFail(getResources().getString(R.string.service_tryAgainLater), false);
                    }
                }
            }
        };
        BackendService.withAsyncConnection(mApplication)
                .requestConfirmationMail(email, forceCreation, responseListener);
    }

    public boolean getWaitingForEmailConfirmation() {
        try {
            return !StringUtil.isNullOrEmpty(getPendingEmailAddress());
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            return true;
        }
    }

    @Override
    protected void createRecoveryPassword(final CreateRecoveryCodeListener createRecoveryCodeListener) {
        final PreferencesController preferencesController = mApplication.getPreferencesController();
        if (preferencesController.isRecoveryDisabled()) {
            return;
        }

        if (preferencesController.getRecoveryByAdmin()) {
            createCompanyRecoveryPassword(createRecoveryCodeListener);
        } else {
            super.createRecoveryPassword(createRecoveryCodeListener);
        }
    }

    private void createCompanyRecoveryPassword(final CreateRecoveryCodeListener createRecoveryCodeListener) {
        try {
            final JsonObject managementCompany = mAccount.getManagementCompany();
            if (managementCompany != null && JsonUtil.hasKey("publicKey", managementCompany)) {
                final String companyPublicKeyString = managementCompany.get("publicKey").getAsString();
                if (!StringUtil.isNullOrEmpty(companyPublicKeyString)) {
                    final PublicKey companyPublicKey = XMLUtil.getPublicKeyFromXML(companyPublicKeyString);
                    createCompanyRecoveryPassword(companyPublicKey, createRecoveryCodeListener);
                }
            }
        } catch (final LocalizedException le) {
            LogUtil.e(TAG, le.getMessage());
        }
    }

    /**
     * Recovery Passwort generien und spreizen,
     * Company Key lokal laden,
     * RP AES verschluesseln und lokal Speichern,
     * RP RSA verschluesseln und an Server senden
     * Achtung, Methode arbeitet syncron, muss also von einem anderen Thread7Task aufgerufen werden
     *
     * @param companyPublicKey Public Key
     */
    private void createCompanyRecoveryPassword(final PublicKey companyPublicKey, final CreateRecoveryCodeListener createRecoveryCodeListener) {
        // 8 stelligen recovery-code generieren
        final String recoveryCode = StringUtil.generatePassword(16);

        // code mit pbkdf2 spreizen -> aes schluessel = aeskey
        final SecurityUtil.OnDeriveKeyCompleteListener onDeriveKeyCompleteListener = new SecurityUtil.OnDeriveKeyCompleteListener() {
            @Override
            public void onComplete(final SecretKey key, final byte[] usedSalt) {
                // privaten schlüssel mit aeskey verschlüsseln und auf geraet speichern (ioexception)
                try {
                    final KeyController keyController = mApplication.getKeyController();
                    IvParameterSpec iv = SecurityUtil.generateIV();
                    SecurityUtil.AESKeyContainer keyContainer = new SecurityUtil.AESKeyContainer(key, iv);
                    SecurityUtil.writeKeyToDisc(keyContainer, keyController.getDeviceKeyPair().getPrivate(), mApplication);
                    // revocery-code mit public key verschlüsseln und an server senden
                    final byte[] encryptedRecoveryCodeBytes = SecurityUtil.encryptMessageWithRSA(recoveryCode.getBytes(), companyPublicKey);
                    final String encodedEncryptedRecoveryCode = Base64.encodeToString(encryptedRecoveryCodeBytes, Base64.NO_WRAP);

                    // recoverycode als phonetoken schreiben
                    mApplication.getPreferencesController().setRecoveryTokenPhone(encodedEncryptedRecoveryCode);
                    createRecoveryCodeListener.onCreateSuccess(null);
                    // wenn alles sauber durchgelaufen ist wird am ende das json-objekt gesetzt
                } catch (final LocalizedException e) {
                    createRecoveryCodeListener.onCreateFailed(getResources().getString(R.string.settings_recovery_code_creation_failed));
                    LogUtil.e(TAG, e.getMessage(), e);
                }
            }

            @Override
            public void onError() {
                LogUtil.e(TAG, "createCompanyRecoveryPassword::deriveKeyFromPassword failed");
            }
        };
        SecurityUtil.deriveKeyFromPassword(recoveryCode, new byte[32], onDeriveKeyCompleteListener, 80000, SecurityUtil.DERIVE_ALGORITHM_SHA_256, false);
    }

    public void requestCompanyRecoveryKey(final GenericActionListener genericCompanyActionListener) {
        final IBackendService.OnBackendResponseListener onBackendResponseListener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(final BackendResponse response) {
                if (response.isError) {
                    genericCompanyActionListener.onFail(response.errorMessage, response.msgException != null ? response.msgException.getIdent() : "");
                } else {
                    //TODO
                    genericCompanyActionListener.onSuccess(null);
                }
            }
        };

        final String recoveryTokenPhone = mApplication.getPreferencesController().getRecoveryTokenPhone();
        BackendService.withAsyncConnection(mApplication)
                .requestCompanyRecoveryKey(recoveryTokenPhone, onBackendResponseListener);
    }

    public void getCompanyLayout(final GenericActionListener companyLayoutListener, final Executor executor) {
        final AsyncHttpTask.AsyncHttpCallback<String> callback = new AsyncHttpTask.AsyncHttpCallback<String>() {
            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .getCompanyLayout(listener);
            }

            @Override
            public String asyncLoaderServerResponse(final BackendResponse response)
                    throws LocalizedException {
                String logoChecksumServer = null;

                if (JsonUtil.hasKey(COMPANY_COMPANY_LAYOUT, response.jsonObject)) {
                    final JsonElement jEl = response.jsonObject.get(COMPANY_COMPANY_LAYOUT);
                    final JsonObject jObj = jEl.getAsJsonObject();
                    if (jObj != null) {
                        try {
                            final String companyJson = jObj.toString();
                            if (!StringUtil.isNullOrEmpty(companyJson)) {
                                // einmal parsen, um das JsonObjekt zu validieren
                                // KS: not possible - object is not exactly what we receive from server
                                //gson.fromJson(companyJson, CompanyLayoutModel.class);
                                mApplication.getPreferencesController().getSharedPreferences().edit().putString(ScreenDesignUtil.COMPANY_LAYOUT_JSON, companyJson).apply();
                            }
                        } catch (final JsonParseException je) {
                            LogUtil.e(TAG, je.getMessage(), je);
                            throw new LocalizedException(LocalizedException.VERIFY_DATA_FAILED, je);
                        }
                    }
                }

                if (JsonUtil.hasKey(COMPANY_COMPANY_LOGO, response.jsonObject)) {
                    final JsonObject logoObj = response.jsonObject.getAsJsonObject(COMPANY_COMPANY_LOGO);
                    logoChecksumServer = JsonUtil.stringFromJO(CHECKSUM, logoObj);
                }
                return logoChecksumServer;
            }

            @Override
            public void asyncLoaderFinished(final String result) {
                if (companyLayoutListener != null) {
                    companyLayoutListener.onSuccess(result);
                }
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                if (companyLayoutListener != null) {
                    companyLayoutListener.onFail("CompanyLayout error: " + errorMessage, "");
                }
            }
        };


        final AsyncHttpTask<String> task = new AsyncHttpTask<>(callback);
        if (executor != null) {
            task.executeOnExecutor(executor);
        } else {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void getCompanyLogo(final GenericActionListener companyLayoutListener, final Executor executor) {
        final AsyncHttpTask.AsyncHttpCallback<Bitmap> callback = new AsyncHttpTask.AsyncHttpCallback<Bitmap>() {
            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .getCompanyLogo(listener);
            }

            @Override
            public Bitmap asyncLoaderServerResponse(final BackendResponse response) {
                Bitmap logo = null;
                if (JsonUtil.hasKey(COMPANY_COMPANY_LOGO, response.jsonObject)) {
                    final JsonElement jsonElement = response.jsonObject.get(COMPANY_COMPANY_LOGO);
                    final JsonObject jsonObject = jsonElement.getAsJsonObject();
                    if (jsonObject != null && JsonUtil.hasKey(DATA, jsonObject)) {
                        final String imageString = JsonUtil.stringFromJO(DATA, jsonObject);
                        if (!StringUtil.isNullOrEmpty(imageString)) {
                            final byte[] decodedBytes = Base64.decode(imageString, Base64.DEFAULT);
                            logo = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                            if (logo != null) {
                                final FileUtil fileUtil = new FileUtil(mApplication);
                                final File file = fileUtil.getInternalMediaFile(COMPANY_LOGO_FILENAME);
                                if (file != null) {
                                    FileUtil.saveToFile(file, decodedBytes);
                                }
                            }
                        }
                    }
                }
                return logo;
            }

            @Override
            public void asyncLoaderFinished(final Bitmap result) {
                if (companyLayoutListener != null) {
                    companyLayoutListener.onSuccess(result);
                }
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                if (companyLayoutListener != null) {
                    companyLayoutListener.onFail(errorMessage, "");
                }
            }
        };

        final AsyncHttpTask<Bitmap> task = new AsyncHttpTask<>(callback);
        if (executor != null) {
            task.executeOnExecutor(executor);
        } else {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void registerOnCompanyLayoutChangeListener(@NonNull final OnCompanyLayoutChangeListener listener) {
        if (mLayoutChangeListeners == null) {
            mLayoutChangeListeners = new ArrayList<>(1);
        }

        if (!mLayoutChangeListeners.contains(listener)) {
            mLayoutChangeListeners.add(listener);
        }
    }

    public void deregisterOnCompanyLayoutChangeListener(@NonNull final OnCompanyLayoutChangeListener listener) {
        if (mLayoutChangeListeners != null) {
            mLayoutChangeListeners.remove(listener);
        }
    }

    public void setOnCompanyLogoChangeListener(@NonNull final OnCompanyLogoChangeListener listener) {
        mLogoChangeListener = listener;
    }

    @Override
    public void doActionsAfterRestoreAccountFromBackup(final JsonObject accountFromBackupJO) {
        super.doActionsAfterRestoreAccountFromBackup(accountFromBackupJO);
        try {
            final JsonObject companyInfoJO = JsonUtil.searchJsonObjectRecursive(accountFromBackupJO, JsonConstants.COMPANY_INFO);
            if (companyInfoJO != null) {
                final String pubKey = JsonUtil.stringFromJO(JsonConstants.COMPANY_PUBLIC_KEY, companyInfoJO);
                if (!StringUtil.isNullOrEmpty(pubKey)) {
                    companyInfoJO.addProperty(JsonConstants.PUBLIC_KEY, pubKey);
                    companyInfoJO.remove(JsonConstants.COMPANY_PUBLIC_KEY);
                }

                getAccount().setManagementCompany(companyInfoJO);
            }

            final String companyPhone = JsonUtil.stringFromJO(JsonConstants.COMPANY_PHONE_NUMER, accountFromBackupJO);
            if (!StringUtil.isNullOrEmpty(companyPhone)) {
                getAccount().setCustomStringAttribute(JsonConstants.COMPANY_PHONE_NUMER, companyPhone);
            }

            final String companyDomain = JsonUtil.stringFromJO(JsonConstants.COMPANY_DOMAIN, accountFromBackupJO);
            if (!StringUtil.isNullOrEmpty(companyDomain)) {
                getAccount().setCustomStringAttribute(EMAIL_DOMAIN_PRE_22, companyDomain.toLowerCase(Locale.US));
            }

            if (JsonUtil.hasKey(MC_ENCRYPTION_SALT, accountFromBackupJO)) {
                final String salt = JsonUtil.stringFromJO(MC_ENCRYPTION_SALT, accountFromBackupJO);
                if (!StringUtil.isNullOrEmpty(salt)) {
                    getAccount().setCustomStringAttribute(MC_ENCRYPTION_SALT, salt);
                }
            }

            if (JsonUtil.hasKey(MC_ENCRYPTION_SEED, accountFromBackupJO)) {
                final String seed = JsonUtil.stringFromJO(MC_ENCRYPTION_SEED, accountFromBackupJO);
                if (!StringUtil.isNullOrEmpty(seed)) {
                    getAccount().setCustomStringAttribute(MC_ENCRYPTION_SEED, seed);
                }
            }

            if (JsonUtil.hasKey(MC_REGISTERED_EMAIL, accountFromBackupJO)) {
                final String email = JsonUtil.stringFromJO(MC_REGISTERED_EMAIL, accountFromBackupJO);

                if (!StringUtil.isNullOrEmpty(email)) {
                    getAccount().setCustomStringAttribute(MC_REGISTERED_EMAIL, email.toLowerCase(Locale.US));
                }
            } else if (JsonUtil.hasKey(JsonConstants.EMAIL, accountFromBackupJO)) {
                final String email = JsonUtil.stringFromJO(JsonConstants.EMAIL, accountFromBackupJO);

                if (!StringUtil.isNullOrEmpty(email)) {
                    getAccount().setCustomStringAttribute(MC_REGISTERED_EMAIL, email.toLowerCase(Locale.US));
                }
            }

            saveOrUpdateAccount(getAccount());
        } catch (final LocalizedException e) {
            //wenn hier eine Exception fliegt kann dass vernachlässigt werden
            //dann wird im Nachgang das Restore fehlschlagen
            LogUtil.e(TAG, e.getMessage(), e);
        }

        BackendService.withSyncConnection(mApplication)
                .hasCompanyManagement(new IBackendService.OnBackendResponseListener() {
                    @Override
                    public void onBackendResponse(final BackendResponse response) {
                        try {
                            if (!response.isError) {
                                if (response.jsonObject != null) {
                                    if (JsonUtil.hasKey(COMPANY_COMPANY, response.jsonObject)) {
                                        final JsonObject company = response.jsonObject.getAsJsonObject(COMPANY_COMPANY);

                                        setManagementCompany(company);
                                    } else if (JsonUtil.hasKey(COMPANY_STATE, response.jsonObject)) {
                                        setManagementCompany(response.jsonObject);
                                    }
                                }
                            }
                        } catch (final LocalizedException e) {
                            // kein Handling, muss später erneut versucht werden
                            LogUtil.e(TAG, e.getMessage(), e);
                        }
                    }
                });
    }

    @Override
    public void doActionsBeforeAccountIsStoreInBackup(final JsonObject accountBackupJoParent)
            throws LocalizedException {
        if (JsonUtil.hasKey("AccountBackup", accountBackupJoParent)) {
            final JsonObject accountBackup = accountBackupJoParent.get("AccountBackup").getAsJsonObject();

            final String salt = getAccount().getCustomStringAttribute(MC_ENCRYPTION_SALT);
            if (!StringUtil.isNullOrEmpty(salt)) {
                accountBackup.addProperty(MC_ENCRYPTION_SALT, salt);
            }

            final String seed = getAccount().getCustomStringAttribute(MC_ENCRYPTION_SEED);
            if (!StringUtil.isNullOrEmpty(seed)) {
                accountBackup.addProperty(MC_ENCRYPTION_SEED, seed);
            }

            final String companyDomain = getAccount().getCustomStringAttribute(EMAIL_DOMAIN_PRE_22);
            if (!StringUtil.isNullOrEmpty(companyDomain)) {
                accountBackup.addProperty(MC_COMPANY_DOMAIN, companyDomain);
            }

            final String email = getAccount().getCustomStringAttribute(MC_REGISTERED_EMAIL);
            if (!StringUtil.isNullOrEmpty(email)) {
                accountBackup.addProperty(MC_REGISTERED_EMAIL, email);
            }
        }
    }

    public boolean testVoucherAvailable()
            throws LocalizedException {
        final JsonObject company = mAccount.getManagementCompany();

        if (company != null && JsonUtil.hasKey(COMPANY_TEST_VOUCHER_AVAILABLE, company)) {
            final String testVoucherAvailable = company.get(COMPANY_TEST_VOUCHER_AVAILABLE).getAsString();
            return "true".equals(testVoucherAvailable);
        } else {
            return false;
        }
    }

    public void registerTestVoucher(@Nullable final GenericActionListener<Long> genericCompanyActionListener) {

        final AsyncHttpTask.AsyncHttpCallback<Long> callback = new AsyncHttpTask.AsyncHttpCallback<Long>() {
            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .registerTestVoucher(listener);
            }

            @Override
            public Long asyncLoaderServerResponse(final BackendResponse response) {
                if (response.jsonArray == null) {
                    return null;
                }

                LogUtil.i(TAG, "registerTestVoucher: " + response.jsonArray);

                for (JsonElement jsonElement : response.jsonArray) {
                    if (!jsonElement.isJsonObject()) {
                        continue;
                    }
                    final JsonObject jsonObject = jsonElement.getAsJsonObject();

                    final String identString = JsonUtil.stringFromJO(IDENT, jsonObject);

                    if (StringUtil.isNullOrEmpty(identString)) {
                        continue;
                    }

                    if (!USAGE.equals(identString)) {
                        continue;
                    }

                    final String usage = JsonUtil.stringFromJO(VALID, jsonObject);

                    if (StringUtil.isNullOrEmpty(usage)) {
                        continue;
                    }

                    return DateUtil.utcWithoutMillisStringToMillis(usage);
                }
                return null;
            }

            @Override
            public void asyncLoaderFinished(final Long usageInMillis) {
                mRegisterTestVoucherTask = null;

                if (genericCompanyActionListener != null) {
                    genericCompanyActionListener.onSuccess(usageInMillis);
                }
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                mRegisterTestVoucherTask = null;
                if (genericCompanyActionListener != null) {
                    genericCompanyActionListener.onFail(errorMessage, "");
                }
            }
        };


        if (mRegisterTestVoucherTask == null) {
            mRegisterTestVoucherTask = new AsyncHttpTask<>(callback);
            mRegisterTestVoucherTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    void getTrialVoucherInfo(@Nullable final GenericActionListener<Void> genericCompanyActionListener) {
        final AsyncHttpTask.AsyncHttpCallback<String> callback = new AsyncHttpTask.AsyncHttpCallback<String>() {

            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .getTestVoucherInfo(listener);
            }

            @Override
            public String asyncLoaderServerResponse(final BackendResponse response) throws LocalizedException {
                if (response.jsonObject != null) {

                    LogUtil.i(TAG, "getTrialVoucherInfo: " + response.jsonObject);

                    if (JsonUtil.hasKey(USAGE, response.jsonObject)) {

                        final String usage = response.jsonObject.get(USAGE).getAsString();

                        if (!StringUtil.isNullOrEmpty(usage)) {
                            final long usageMillis = DateUtil.utcWithoutMillisStringToMillis(usage);
                            setTrialUsage(usageMillis);
                        } else {
                            LogUtil.e(TAG, "registerTestVoucher::usage is null or empty");
                            return getResources().getString(R.string.service_tryAgainLater);
                        }
                    }
                    if (JsonUtil.hasKey(DAYSLEFT, response.jsonObject)) {
                        final String daysLeft = response.jsonObject.get(DAYSLEFT).getAsString();
                        if (!StringUtil.isNullOrEmpty(daysLeft)) {
                            try {
                                mTrialsDaysLeft = Integer.parseInt(daysLeft);
                                if (mTrialsDaysLeft < 0) {
                                    mTrialsDaysLeft = 0;
                                }
                            } catch (final NumberFormatException e) {
                                LogUtil.e(TAG, "registerTestVoucher::daysLeft is NAN", e);
                            }
                        }
                    }
                }
                return null;
            }

            @Override
            public void asyncLoaderFinished(final String error) {
                if (StringUtil.isNullOrEmpty(error)) {
                    if (genericCompanyActionListener != null) {
                        genericCompanyActionListener.onSuccess(null);
                    }
                } else {
                    if (genericCompanyActionListener != null) {
                        genericCompanyActionListener.onFail(error, "");
                    }
                }
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                if (genericCompanyActionListener != null) {
                    genericCompanyActionListener.onFail(errorMessage, "");
                }
            }
        };

        final AsyncHttpTask<String> task = new AsyncHttpTask<>(callback);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    }

    public void registerVoucher(final String voucherCode, final OnRegisterVoucherListener onRegisterVoucherListener) {
        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    LogUtil.w(TAG, "Register voucher request failed!");
                    onRegisterVoucherListener.onRegisterVoucherFail(response.errorMessage);

                } else {
                    LogUtil.i(TAG, "Register voucher request succeeded.");

                    if (response.jsonArray != null && response.jsonArray.size() > 0) {

                        JsonObject jsonObject = response.jsonArray.get(0).getAsJsonObject();
                        if (jsonObject != null) {

                            if (jsonObject.has("ident")) {
                                final String ident = jsonObject.get("ident").getAsString();

                                if (StringUtil.isEqual(ident, "usage") && jsonObject.has("valid")) {
                                    final String date = jsonObject.get("valid").getAsString();
                                    final long dateLong = DateUtil.utcWithoutMillisStringToMillis(date);

                                    try {
                                        mAccount.setLicenceDate(dateLong);
                                        ((ContactControllerBusiness) mApplication.getContactController()).resetLicenseDaysLeft();
                                        saveOrUpdateAccount(mAccount);
                                    } catch (LocalizedException e) {
                                        LogUtil.e(TAG, "Setting of licence data failed!", e);
                                    }
                                }
                            }
                        }
                    }
                    onRegisterVoucherListener.onRegisterVoucherSuccess();
                }
            }
        };

        BackendService.withAsyncConnection(mApplication)
                .registerVoucher(voucherCode, listener);

    }

    private void setManagementCompany(final JsonObject company)
            throws LocalizedException {
        if (company == null) {
            return;
        }

        final JsonObject oldCompany = mAccount.getManagementCompany();

        if (oldCompany != null) {
            if (JsonUtil.hasKey(COMPANY_STATE, company)) {
                final String oldState = JsonUtil.stringFromJO(COMPANY_STATE, oldCompany);
                final String newState = JsonUtil.stringFromJO(COMPANY_STATE, company);

                if (MC_STATE_ACCOUNT_ACCEPTED.equals(newState) && !MC_STATE_ACCOUNT_ACCEPTED.equals(oldState)) {
                    // gemerktes Datum für companyIndex zurücksetzen
                    mApplication.getPreferencesController().onDeleteAllCompanyContacts();
                }
            }

            //nur values ersetzen nicht das ganze Objekt, da noch andere Infos abgelegt sind
            Set<Map.Entry<String, JsonElement>> entries = company.entrySet();
            for (Map.Entry<String, JsonElement> entry : entries) {
                oldCompany.add(entry.getKey(), entry.getValue());
            }
            mAccount.setManagementCompany(oldCompany);
        } else {
            mAccount.setManagementCompany(company);
        }

        saveOrUpdateAccount(mAccount);

        if (StringUtil.isEqual(JsonUtil.stringFromJO(COMPANY_STATE, company), MC_STATE_ACCOUNT_ACCEPTED)) {
            boolean accountIsManaged = mApplication.getPreferencesController().getSharedPreferences()
                    .getBoolean(MC_ACCOUNT_IS_MANAGED, false);

            if (!accountIsManaged) {
                mApplication.getPreferencesController().getSharedPreferences().edit().putBoolean(MC_ACCOUNT_IS_MANAGED, true).apply();
            }

            ((ContactControllerBusiness) mApplication.getContactController()).resetTrialVoucherDaysLeft();

            final CreateRecoveryCodeListener createRecoveryCodeListener = new CreateRecoveryCodeListener() {
                @Override
                public void onCreateSuccess(final String message) {
                    if (!StringUtil.isNullOrEmpty(message)) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mApplication, message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }

                @Override
                public void onCreateFailed(@NotNull final String message) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mApplication, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            };

            //
            boolean recoverCodeSet = getAccount().getCustomBooleanAttribute(RECOVERY_CODE_SET);
            if (!recoverCodeSet) {
                AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(final Void... params) {
                        if (JsonUtil.hasKey(JsonConstants.PUBLIC_KEY, company)) {
                            final String companyPublicKeyString = company.get(JsonConstants.PUBLIC_KEY).getAsString();
                            if (!StringUtil.isNullOrEmpty(companyPublicKeyString)) {
                                try {
                                    final PublicKey companyPublicKey = XMLUtil.getPublicKeyFromXML(companyPublicKeyString);
                                    createCompanyRecoveryPassword(companyPublicKey, createRecoveryCodeListener);
                                } catch (final LocalizedException le) {
                                    LogUtil.e(TAG, le.toString());
                                }
                            }
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(final Void aVoid) {
                    }

                };
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        }
    }

    public String getManagementCompanyName()
            throws LocalizedException {
        JsonObject appConfigJO = getCompanyMDMConfig();
        if (appConfigJO != null) {
            final String companyIndexName = JsonUtil.stringFromJO("companyIndexName", appConfigJO);
            if (!StringUtil.isNullOrEmpty(companyIndexName)) {
                return companyIndexName;
            }
        }

        final JsonObject company = mAccount.getManagementCompany();

        if (company != null && JsonUtil.hasKey(MC_NAME, company)) {
            return JsonUtil.stringFromJO(MC_NAME, company);
        }

        return null;
    }

    @Override
    public boolean getManagementCompanyIsUserRestricted()
            throws LocalizedException {
        final JsonObject company = mAccount.getManagementCompany();

        if (company != null && JsonUtil.hasKey(MC_USER_RESTRICTED_INDEX, company)) {
            return "1".equals(JsonUtil.stringFromJO(MC_USER_RESTRICTED_INDEX, company));
        }

        return false;
    }

    public boolean needEmailRegistrationForManaging()
            throws LocalizedException {
        final String state = getManagementState();
        return StringUtil.isEqual(state, MC_STATE_ACCOUNT_ACCEPTED_EMAIL_REQUIRED)
                || StringUtil.isEqual(AccountController.MC_STATE_ACCOUNT_ACCEPTED_PHONE_FAILED, state);
    }

    @Override
    public boolean isDeviceManaged()
            throws LocalizedException {
        final JsonObject company = mAccount.getManagementCompany();

        if (company == null) {
            //bei Null-> Daten im Hintergrund abrufen
            loadCompanyManagement(null);
        }

        if (company == null) {
            return false;
        }

        String state = JsonUtil.stringFromJO(COMPANY_STATE, company);
        if (StringUtil.isNullOrEmpty(state)) {
            return false;
        }

        switch (state) {
            case MC_STATE_ACCOUNT_ACCEPTED:
            case MC_STATE_ACCOUNT_ACCEPTED_EMAIL_REQUIRED:
            case MC_STATE_ACCOUNT_ACCEPTED_PHONE_REQUIRED:
            case MC_STATE_ACCOUNT_ACCEPTED_EMAIL_FAILED:
            case MC_STATE_ACCOUNT_ACCEPTED_PHONE_FAILED:
            case MC_STATE_ACCOUNT_PENDING_VALIDATION: {
                return true;

            }
            default: {
                return false;
            }
        }
    }

    public boolean haveToShowManagementRequest()
            throws LocalizedException {
        final JsonObject company = mAccount.getManagementCompany();

        return company != null && StringUtil.isEqual(JsonUtil.stringFromJO(COMPANY_STATE, company), MC_STATE_ACCOUNT_NEW);
    }

    public String getManagementState()
            throws LocalizedException {
        final JsonObject company = mAccount.getManagementCompany();

        return company != null ? JsonUtil.stringFromJO(COMPANY_STATE, company) : null;
    }

    public void loadCompanyManagement(@Nullable final HasCompanyManagementCallback companyManagementCallback) {
        loadCompanyManagementInternally(companyManagementCallback, null);
    }

    private void loadCompanyManagementInternally(@Nullable final HasCompanyManagementCallback companyManagementCallback, @Nullable final Executor executor) {
        final AsyncHttpTask.AsyncHttpCallback<JsonObject> callback = new AsyncHttpTask.AsyncHttpCallback<JsonObject>() {
            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .hasCompanyManagement(listener);
            }

            @Override
            public JsonObject asyncLoaderServerResponse(final BackendResponse response) {
                return response.jsonObject;
            }

            @Override
            public void asyncLoaderFinished(final JsonObject result) {
                try {
                    if (result != null) {
                        if (JsonUtil.hasKey(COMPANY_COMPANY, result)) {
                            final JsonObject company = result.getAsJsonObject(COMPANY_COMPANY);

                            setManagementCompany(company);

                            if (companyManagementCallback != null) {
                                companyManagementCallback.onSuccess(getManagementState());
                            }

                            return;
                        } else if (JsonUtil.hasKey(COMPANY_STATE, result)) {
                            setManagementCompany(result);
                            if (companyManagementCallback != null) {
                                companyManagementCallback.onSuccess(JsonUtil.stringFromJO(COMPANY_STATE, result));
                            }

                            return;
                        }
                    }
                    if (companyManagementCallback != null) {
                        companyManagementCallback.onFail(null);
                    }
                } catch (final LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                    if (companyManagementCallback != null) {
                        companyManagementCallback.onFail(null);
                    }
                }
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                if (companyManagementCallback != null) {
                    companyManagementCallback.onFail(errorMessage);
                }
            }
        };


        final AsyncHttpTask<JsonObject> task = new AsyncHttpTask<>(callback);

        if (executor != null) {
            task.executeOnExecutor(executor);
        } else {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void loadCompanyInfo(@Nullable final HasCompanyManagementCallback companyManagementCallback, @Nullable final Executor executor) {
        loadCompanyInfoInternally(companyManagementCallback, executor);
    }

    private void loadCompanyInfoInternally(@Nullable final HasCompanyManagementCallback companyManagementCallback, @Nullable final Executor executor) {
        final AsyncHttpTask.AsyncHttpCallback<JsonObject> callback = new AsyncHttpTask.AsyncHttpCallback<JsonObject>() {
            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .getCompany(listener);
            }

            @Override
            public JsonObject asyncLoaderServerResponse(final BackendResponse response) {
                return response.jsonObject;
            }

            @Override
            public void asyncLoaderFinished(final JsonObject result) {
                try {
                    if (result != null) {
                        if (JsonUtil.hasKey(COMPANY_COMPANY, result)) {
                            final JsonObject company = result.getAsJsonObject(COMPANY_COMPANY);

                            setManagementCompany(company);

                            if (companyManagementCallback != null) {
                                companyManagementCallback.onSuccess(getManagementState());
                            }

                            return;
                        }
                    }
                    if (companyManagementCallback != null) {
                        companyManagementCallback.onFail(null);
                    }
                } catch (final LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                    if (companyManagementCallback != null) {
                        companyManagementCallback.onFail(null);
                    }
                }
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                if (companyManagementCallback != null) {
                    companyManagementCallback.onFail(errorMessage);
                }
            }
        };

        final AsyncHttpTask<JsonObject> task = new AsyncHttpTask<>(callback);

        if (executor != null) {
            task.executeOnExecutor(executor);
        } else {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void acceptOrDeclineCompanyManagement(final boolean accept, final AcceptOrDeclineCompanyManagementCallback acceptOrDeclineCompanyManagementCallback) {
        final AsyncHttpTask.AsyncHttpCallback<JsonArray> callback = new AsyncHttpTask.AsyncHttpCallback<JsonArray>() {
            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                if (accept) {
                    BackendService.withSyncConnection(mApplication)
                            .acceptCompanyManagement(listener);
                } else {
                    BackendService.withSyncConnection(mApplication)
                            .declineCompanyManagement(listener);
                }
            }

            @Override
            public JsonArray asyncLoaderServerResponse(final BackendResponse response) {
                return response.jsonArray;
            }

            @Override
            public void asyncLoaderFinished(final JsonArray result) {
                loadCompanyManagement(new HasCompanyManagementCallback() {
                    @Override
                    public void onSuccess(final String managementState) {
                        if (StringUtil.isEqual(managementState, MC_STATE_ACCOUNT_ACCEPTED)) {
                            //Token laden wird nun auch für Recovery Code anfordern benötigt
                            mApplication.getMessageController().getBackgroundAccessToken(null);
                        }
                        acceptOrDeclineCompanyManagementCallback.onSuccess(managementState);
                    }

                    @Override
                    public void onFail(final String message) {
                        acceptOrDeclineCompanyManagementCallback.onFail(message);
                    }
                });
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                acceptOrDeclineCompanyManagementCallback.onFail(errorMessage);
            }
        };

        final AsyncHttpTask<JsonArray> task = new AsyncHttpTask<>(callback);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void handleActionModel(final Action action) {
        if (action instanceof CompanyEncryptInfoAction) {
            final CompanyEncryptInfoAction companyEncryptInfoAction = (CompanyEncryptInfoAction) action;

            try {
                if (!StringUtil.isNullOrEmpty(getAccount().getCustomStringAttribute(MC_ENCRYPTION_SALT))
                        && !StringUtil.isNullOrEmpty(getAccount().getCustomStringAttribute(MC_ENCRYPTION_SEED))
                        && mApplication.getPreferencesController().getSharedPreferences().getBoolean("renewCompanyKey", false)) {
                    return;
                }

                LogUtil.d("KEY_SALT", companyEncryptInfoAction.companyEncryptionSalt);
                LogUtil.d("KEY_SEED", companyEncryptInfoAction.companyEncryptionSeed);

                getAccount().setCustomStringAttribute(MC_ENCRYPTION_SALT, companyEncryptInfoAction.companyEncryptionSalt);
                getAccount().setCustomStringAttribute(MC_ENCRYPTION_SEED, companyEncryptInfoAction.companyEncryptionSeed);

                if (!StringUtil.isNullOrEmpty(companyEncryptInfoAction.companyEncryptionPart)) {
                    Contact ownContact = mApplication.getContactController().getOwnContact();
                    String phone = null;

                    if (companyEncryptInfoAction.companyEncryptionPart.contains(JsonConstants.COMPANY_ENCRYPTION_PART_EMAIL)) {
                        if (ownContact != null && !StringUtil.isNullOrEmpty(ownContact.getEmail())) {
                            getAccount().setCustomStringAttribute(MC_ENCRYPTION_EMAIL, ownContact.getEmail().toLowerCase(Locale.US));
                        }
                    }

                    if (companyEncryptInfoAction.companyEncryptionPart.contains(JsonConstants.COMPANY_ENCRYPTION_PART_PHONE)) {
                        if (ownContact != null && !StringUtil.isNullOrEmpty(ownContact.getPhoneNumber())) {
                            getAccount().setCustomStringAttribute(MC_ENCRYPTION_PHONE, ownContact.getPhoneNumber());
                        } else {
                            phone = getAccount().getPhoneNumber();

                            if (!StringUtil.isNullOrEmpty(phone)) {
                                getAccount().setCustomStringAttribute(MC_ENCRYPTION_PHONE, phone);
                            }
                        }
                    }

                    if (ownContact != null && !StringUtil.isNullOrEmpty(phone)) {
                        mApplication.getContactController().saveContactInformation(ownContact, null, null, phone, null, null, null, null, null, -1, false);
                    }
                }

                if (!StringUtil.isNullOrEmpty(companyEncryptInfoAction.companyEncryptionDiff)) {
                    LogUtil.d("KEY_DIFF", companyEncryptInfoAction.companyEncryptionDiff);
                    getAccount().setCustomStringAttribute(MC_ENCRYPTION_DIFF, companyEncryptInfoAction.companyEncryptionDiff);
                }

                JsonObject mcJO = getAccount().getManagementCompany();
                if (mcJO != null) {
                    mcJO.addProperty(JsonConstants.COMPANY_KEY, "");
                    mcJO.addProperty(JsonConstants.COMPANY_USER_DATA_KEY, "");

                    getAccount().setManagementCompany(mcJO);
                }

                mCompanyAesKey = null;
                mApplication.getPreferencesController().getSharedPreferences().edit().putBoolean("renewCompanyKey", true).apply();

                saveOrUpdateAccount(getAccount());

                getCompanyAesKey();

            } catch (final LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
                //mmh schlecht was nun
            }
        } else if (action instanceof RequestConfirmPhoneAction) {
            final RequestConfirmPhoneAction requestConfirmPhoneAction = (RequestConfirmPhoneAction) action;

            if (!StringUtil.isNullOrEmpty(requestConfirmPhoneAction.requestPhone)) {
                try {
                    mAccount.setCustomStringAttribute(PENDING_PHONE_NUMBER, requestConfirmPhoneAction.requestPhone);
                    mAccount.setCustomStringAttribute(PENDING_PHONE_STATUS, PENDING_PHONE_STATUS_WAIT_REQUEST);
                    saveOrUpdateAccount(mAccount);
                } catch (final LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                }
            }
        } else if (action instanceof RequestConfirmEmailAction) {
            final RequestConfirmEmailAction requestConfirmEmailAction = (RequestConfirmEmailAction) action;

            if (!StringUtil.isNullOrEmpty(requestConfirmEmailAction.requestEmail)) {
                try {
                    mAccount.setCustomStringAttribute(PENDING_EMAIL_ADDRESS, requestConfirmEmailAction.requestEmail);
                    mAccount.setCustomStringAttribute(PENDING_EMAIL_STATUS, PENDING_EMAIL_STATUS_WAIT_REQUEST);
                    saveOrUpdateAccount(mAccount);
                } catch (final LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                }
            }
        } else {
            super.handleActionModel(action);
        }
    }

    public void loadCompanyMDMConfig(@Nullable final GenericActionListener<Void> callback, @Nullable Executor executor) {
        final AsyncHttpTask.AsyncHttpCallback<String> asyncCallback = new AsyncHttpTask.AsyncHttpCallback<String>() {
            @Override
            public void asyncLoaderServerRequest(IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .getCompanyMdmConfig(listener);
            }

            @Override
            public String asyncLoaderServerResponse(BackendResponse response)
                    throws LocalizedException {
                if (response.jsonObject != null && JsonUtil.hasKey(MC_COMPANY_MDM_CONFIG, response.jsonObject)) {
                    JsonObject mdmCompanyJO = response.jsonObject.getAsJsonObject(MC_COMPANY_MDM_CONFIG);
                    if (JsonUtil.hasKey(IV, mdmCompanyJO) && JsonUtil.hasKey(DATA, mdmCompanyJO)) {
                        SecretKey aesKey = getCompanyAesKey();

                        if (aesKey == null) {
                            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "company key null");
                        }

                        String dataAsString = JsonUtil.stringFromJO(DATA, mdmCompanyJO);
                        if (StringUtil.isNullOrEmpty(dataAsString)) {
                            return null;
                        }

                        String ivAsString = JsonUtil.stringFromJO(IV, mdmCompanyJO);
                        if (StringUtil.isNullOrEmpty(dataAsString)) {
                            return null;
                        }

                        byte[] ivBytes = Base64.decode(ivAsString, Base64.NO_WRAP);
                        byte[] dataBytes = Base64.decode(dataAsString, Base64.NO_WRAP);

                        byte[] data = SecurityUtil.decryptMessageWithAES(dataBytes, aesKey, new IvParameterSpec(ivBytes));
                        String appConfigString = new String(data, StandardCharsets.UTF_8);

                        JsonObject jsonObject = JsonUtil.getJsonObjectFromString(appConfigString);
                        if (jsonObject == null) {
                            return null;
                        }

                        JsonObject appConfigJO = JsonUtil.searchJsonObjectRecursive(jsonObject, MC_APP_CONFIG);
                        if (appConfigJO == null) {
                            return null;
                        }

                        final BaManagedConfigUtil managedConfigUtil = (BaManagedConfigUtil) RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication);

                        final PreferencesController preferencesController = mApplication.getPreferencesController();
                        preferencesController.getSharedPreferences().edit()
                                .putString(MC_APP_CONFIG, appConfigJO.toString()).apply();

                        // alte recovery werte laden
                        final boolean recoveryByAdminOld = preferencesController.getRecoveryByAdmin();
                        final boolean isRecoveryDisableOld = preferencesController.isRecoveryDisabled();

                        // recovery aenderungen auswerten
                        final String disableRecoveryCode = JsonUtil.stringFromJO("disableRecoveryCode", appConfigJO);
                        final String disableSimsmeRecovery = JsonUtil.stringFromJO("disableSimsmeRecovery", appConfigJO);
                        final boolean recoveryByAdminNew = JsonConstants.VALUE_TRUE.equals(disableSimsmeRecovery);
                        final boolean isRecoveryDisableNew = JsonConstants.VALUE_TRUE.equals(disableRecoveryCode);

                        // biometricKey auswerten
                        final boolean isBiometricAuthDisabledOld = managedConfigUtil.getDisableBiometricLogin();
                        final String disableBiometricLogin = JsonUtil.stringFromJO("disableFaceAndTouchID", appConfigJO);
                        final boolean isBiometricAuthDisableNew = JsonConstants.VALUE_TRUE.equals(disableBiometricLogin);

                        // config uebernehmen
                        managedConfigUtil.loadConfig();

                        // nach uebernommener config recovery aenderungen durhcfuehren, wenn noetig
                        // erstellung des recovery codes greift intern auf die config zu, deswegen muss die config vorher uebernommen werden!

                        // wenn der recoverycode vorher aktiviert war und jetzt nicht -> zuruecksetzen
                        if (isRecoveryDisableNew && !isRecoveryDisableOld) {
                            unsetRecoveryCode();
                        }
                        // recover war vorher nicht aktiviert, jetzt aber schon -> alten code loeschen und neuen code erstellen
                        else if (!isRecoveryDisableNew && isRecoveryDisableOld) {
                            unsetRecoveryCode();
                            preferencesController.checkRecoveryCodeToBeSet(false); //force = false, da vorher geloescht
                        }
                        // wenn nicht von on auf off oder umgekehrt gewechselt wurde -> recovery type pruefen
                        else {
                            // wenn alter modus != neuer modus -> zuruecksetzen und neu
                            if (recoveryByAdminOld != recoveryByAdminNew) {
                                unsetRecoveryCode();
                                preferencesController.checkRecoveryCodeToBeSet(false); //force = false, da vorher geloescht
                            }
                        }

                        final String disableBackup = JsonUtil.stringFromJO("disableBackup", appConfigJO);
                        if (!StringUtil.isNullOrEmpty(disableBackup)) {
                            preferencesController.setDisableBackup(JsonConstants.VALUE_TRUE.equals(disableBackup));
                        }

                        if (!isBiometricAuthDisabledOld && isBiometricAuthDisableNew) {
                            final ConcurrentTaskListener deleteListener = new ConcurrentTaskListener() {
                                @Override
                                public void onStateChanged(ConcurrentTask task, int state) {
                                    if (state == ConcurrentTask.STATE_ERROR) {
                                        LogUtil.w(KeyController.class.getSimpleName(), "Delete biometric key failed");
                                    }
                                }
                            };
                            disableBiometricAuthentication(deleteListener);
                        }
                    }
                }

                return null;
            }

            @Override
            public void asyncLoaderFinished(String result) {
                if (callback != null) {
                    callback.onSuccess(null);
                }
            }

            @Override
            public void asyncLoaderFailed(String errorMessage) {
                if (callback != null) {
                    callback.onFail(errorMessage, "");
                }
            }
        };


        final AsyncHttpTask<String> task = new AsyncHttpTask<>(asyncCallback);
        if (executor != null) {
            task.executeOnExecutor(executor);
        } else {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    private String getManagementCompanyGuid()
            throws LocalizedException {
        final JsonObject company = mAccount.getManagementCompany();

        if (company != null && JsonUtil.hasKey(MC_GUID, company)) {
            return JsonUtil.stringFromJO(MC_GUID, company);
        }

        return null;
    }

    @Override
    public void loadConfirmedIdentitiesConfig(@Nullable final GenericActionListener<Void> callback, @Nullable Executor executor) {
        final AsyncHttpTask.AsyncHttpCallback<String> asyncCallback = new AsyncHttpTask.AsyncHttpCallback<String>() {
            @Override
            public void asyncLoaderServerRequest(IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .getConfirmedIdentities(listener);
            }

            @Override
            public String asyncLoaderServerResponse(BackendResponse response)
                    throws LocalizedException {
                if (response.jsonObject != null && JsonUtil.hasKey("accountID", response.jsonObject)) {
                    JsonObject identities = response.jsonObject;

                    if (!identities.get("accountID").isJsonNull()) {
                        String accountID = identities.get("accountID").getAsString();
                        Contact ownContact = mApplication.getContactController().getOwnContact();
                        if (ownContact == null) {
                            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "own contact null");
                        }
                        if (!StringUtil.isEqual(accountID, ownContact.getSimsmeId()) || !StringUtil.isEqual(accountID, getAccount().getAccountID())) {
                            getAccount().setAccountID(accountID);
                            saveOrUpdateAccount(getAccount());

                            ownContact.setSimsmeId(accountID);
                            mApplication.getContactController().saveContactInformation(ownContact, null, null, null, null, null, null, null, null, -1, true);

                        }
                    }
                    if (identities.has("confirmedPhone")) {
                        JsonArray a = identities.get("confirmedPhone").getAsJsonArray();
                        if (a != null && a.size() >= 1) {
                            //  Telefonnummer entschlüsseln und speisetchern
                            String encryptedConfirmedPhone = a.get(0).getAsString();
                            KeyPair keyPair = getAccountKeyPair();

                            if (keyPair == null) {
                                throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "keyPair is null");
                            }

                            byte[] data = SecurityUtil.decryptMessageWithRSA(Base64.decode(encryptedConfirmedPhone, Base64.NO_WRAP), keyPair.getPrivate());
                            String phone = new String(data, StandardCharsets.UTF_8);
                            Contact ownContact = mApplication.getContactController().getOwnContact();
                            if (ownContact == null) {
                                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "own contact null");
                            }

                            if (!StringUtil.isEqual(ownContact.getPhoneNumber(), phone)) {
                                mApplication.getContactController().saveContactInformation(ownContact, null, null, phone, null, null, null, null, null, -1, false);
                                if (StringUtil.isEqual(getPendingPhoneNumber(), phone)) {
                                    unsetPendingPhoneStatus();
                                }
                                //Phone geändert --> Passtoken neu generieren
                                mApplication.getPreferencesController().checkRecoveryCodeToBeSet(true);
                            }

                        } else {
                            ///pruefen ob auch kein mail vorhanden ist, dann raussprung, da min. 1 ident da sein muss
                            if (identities.has("confirmedMail")) {
                                JsonArray b = identities.get("confirmedMail").getAsJsonArray();
                                if (b.size() < 1) {
                                    return null;
                                }
                            } else {
                                return null;
                            }

                            Contact ownContact = mApplication.getContactController().getOwnContact();

                            if (ownContact == null) {
                                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "own contact null");
                            }

                            if (!StringUtil.isNullOrEmpty(ownContact.getPhoneNumber())) {
                                // Telefonnummer entfernen
                                mApplication.getContactController().saveContactInformation(ownContact, null, null, "", null, null, null, null, null, -1, false);
                                //Phone entfernt --> Passtoken neu generieren
                                mApplication.getPreferencesController().checkRecoveryCodeToBeSet(true);

                            }
                        }
                    }
                    if (identities.has("pendingPhone")) {
                        JsonArray a = identities.get("pendingPhone").getAsJsonArray();
                        if (a != null && a.size() >= 1) {

                            // Telefonnummer entschlüsseln und speichern
                            String encryptedConfirmedPhone = a.get(0).getAsString();
                            KeyPair keyPair = getAccountKeyPair();

                            if (keyPair == null) {
                                throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "keyPair is null");
                            }

                            byte[] data = SecurityUtil.decryptMessageWithRSA(Base64.decode(encryptedConfirmedPhone, Base64.NO_WRAP), keyPair.getPrivate());
                            String phone = new String(data, StandardCharsets.UTF_8);

                            if (!StringUtil.isEqual(getPendingPhoneNumber(), phone)) {
                                mAccount.setCustomStringAttribute(PENDING_PHONE_NUMBER, phone);
                                mAccount.setCustomStringAttribute(PENDING_PHONE_STATUS, PENDING_PHONE_STATUS_WAIT_CONFIRM);
                                saveOrUpdateAccount(mAccount);
                            }
                        } else {
                            if (!AccountController.PENDING_PHONE_STATUS_WAIT_REQUEST.equals(getPendingPhoneStatus())) {
                                if (!StringUtil.isNullOrEmpty(getPendingPhoneNumber())) {
                                    unsetPendingPhoneStatus();
                                }
                            }
                        }
                    }
                    if (identities.has("confirmedMail")) {
                        JsonArray a = identities.get("confirmedMail").getAsJsonArray();
                        if (a != null && a.size() == 1) {
                            // EMail entschlüsseln und speichern
                            String encryptedMail = a.get(0).getAsString();
                            KeyPair keyPair = getAccountKeyPair();

                            if (keyPair == null) {
                                throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "keyPair is null");
                            }

                            byte[] data = SecurityUtil.decryptMessageWithRSA(Base64.decode(encryptedMail, Base64.NO_WRAP), keyPair.getPrivate());
                            String email = new String(data, StandardCharsets.UTF_8);
                            Contact ownContact = mApplication.getContactController().getOwnContact();

                            if (ownContact == null) {
                                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "own contact null");
                            }

                            if (!StringUtil.isEqual(ownContact.getEmail(), email)) {
                                mApplication.getContactController().saveContactInformation(ownContact, null, null, null, email, null, null, null, null, -1, false);
                                if (StringUtil.isEqual(getPendingEmailAddress(), email)) {
                                    unsetPendingEmailStatus(false);
                                }
                                //Mail hinzugefügt/geändert --> Passtoken neu generieren
                                mApplication.getPreferencesController().checkRecoveryCodeToBeSet(true);
                            }

                        } else {
                            ///pruefen ob auch kein phone vorhanden ist, dann raussprung, da min. 1 ident da sein muss
                            if (identities.has("confirmedPhone")) {
                                JsonArray b = identities.get("confirmedPhone").getAsJsonArray();
                                if (b.size() < 1) {
                                    return null;
                                }
                            } else {
                                return null;
                            }
                            Contact ownContact = mApplication.getContactController().getOwnContact();
                            if (ownContact == null) {
                                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "own contact null");
                            }
                            if (!StringUtil.isNullOrEmpty(ownContact.getEmail())) {
                                // Telefonnummer entfernen
                                mApplication.getContactController().saveContactInformation(ownContact, null, null, null, "", "", null, null, null, -1, false);
                                //Mail entfernt --> Passtoken neu generieren
                                mApplication.getPreferencesController().checkRecoveryCodeToBeSet(true);
                            }
                        }

                    }
                    if (identities.has("pendingMail")) {
                        JsonArray a = identities.get("pendingMail").getAsJsonArray();
                        if (a != null && a.size() == 1) {
                            // EMail entschlüsseln und speichern
                            String encryptedMail = a.get(0).getAsString();
                            KeyPair keyPair = getAccountKeyPair();

                            if (keyPair == null) {
                                throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "keyPair is null");
                            }

                            byte[] data = SecurityUtil.decryptMessageWithRSA(Base64.decode(encryptedMail, Base64.NO_WRAP), keyPair.getPrivate());
                            String email = new String(data, StandardCharsets.UTF_8);

                            if (!StringUtil.isEqual(getPendingEmailAddress(), email)) {
                                mAccount.setCustomStringAttribute(PENDING_EMAIL_ADDRESS, email);
                                mAccount.setCustomStringAttribute(PENDING_EMAIL_STATUS, PENDING_EMAIL_STATUS_WAIT_CONFIRM);
                                saveOrUpdateAccount(mAccount);
                                // TODO : EMail Domain aktualisiwren
                            }
                        } else {
                            // Pending E-Mail entfernen
                            if (!AccountController.PENDING_EMAIL_STATUS_WAIT_REQUEST.equals(getPendingEmailStatus())) {
                                if (!StringUtil.isNullOrEmpty(getPendingEmailAddress())) {
                                    unsetPendingEmailStatus(true);
                                }
                            }
                        }
                    }
                }

                return null;
            }

            @Override
            public void asyncLoaderFinished(String result) {
                if (callback != null) {
                    callback.onSuccess(null);
                }
            }

            @Override
            public void asyncLoaderFailed(String errorMessage) {
                if (callback != null) {
                    callback.onFail(errorMessage, "");
                }
            }
        };

        final AsyncHttpTask<String> task = new AsyncHttpTask<>(asyncCallback);

        if (executor != null) {
            task.executeOnExecutor(executor);
        } else {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    @Override
    public JsonObject getCompanyMDMConfig() {
        String appConfigAsString = mApplication.getPreferencesController().getSharedPreferences().getString(MC_APP_CONFIG, null);

        if (!StringUtil.isNullOrEmpty(appConfigAsString)) {
            return JsonUtil.getJsonObjectFromString(appConfigAsString);
        }

        return null;
    }

    @Override
    public void onServerVersionChanged(final String serverVersionKey, final String newServerVersion, final Executor executor) {
        if (StringUtil.isEqual(serverVersionKey, ConfigUtil.SERVER_VERSION_GET_COMPANY)) {
            loadCompanyInfoInternally(new HasCompanyManagementCallback() {
                @Override
                public void onSuccess(final String managementState) {
                    mApplication.getPreferencesController().serverVersionIsUpToDate(ConfigUtil.SERVER_VERSION_GET_COMPANY, newServerVersion);
                }

                @Override
                public void onFail(final String message) {
                    //in den Fall muss es später erneut versucht werden
                }
            }, executor);
        } else if (StringUtil.isEqual(serverVersionKey, ConfigUtil.SERVER_VERSION_GET_COMPANY_LAYOUT)) {
            getCompanyLayout(new GenericActionListener<String>() {
                @Override
                public void onSuccess(final String object) {
                    try {
                        final String checksumLogoServer = object;
                        final String checksumLocal = mAccount.getCustomStringAttribute(TOOLBAR_LOGO_CHECKSUM);

                        if (!StringUtil.isNullOrEmpty(checksumLogoServer) && !StringUtil.isEqual(checksumLocal, checksumLogoServer) && mAccount.getState() == Account.ACCOUNT_STATE_FULL) {
                            getCompanyLogo(new GenericActionListener<Bitmap>() {
                                @Override
                                public void onSuccess(final Bitmap object) {
                                    try {
                                        if (mLogoChangeListener != null) {
                                            mLogoChangeListener.onCompanyLogoChanged(object);
                                        }
                                        mAccount.setCustomStringAttribute(TOOLBAR_LOGO_CHECKSUM, checksumLogoServer);
                                        mApplication.getPreferencesController().serverVersionIsUpToDate(ConfigUtil.SERVER_VERSION_GET_COMPANY_LAYOUT, newServerVersion);
                                    } catch (final LocalizedException e) {
                                        LogUtil.e(TAG, e.getMessage(), e);
                                        //in den Fall muss es später erneut versucht werden
                                    }
                                }

                                @Override
                                public void onFail(final String message, final String errorIdent) {
                                    //in den Fall muss es später erneut versucht werden
                                }
                            }, executor);

                        } else {
                            mApplication.getPreferencesController().serverVersionIsUpToDate(ConfigUtil.SERVER_VERSION_GET_COMPANY_LAYOUT, newServerVersion);
                            resetColorLayoutAndCallListener();
                            if (StringUtil.isNullOrEmpty(checksumLogoServer)) {
                                final FileUtil fileUtil = new FileUtil(mApplication);
                                final File mediaDir = fileUtil.getInternalMediaDir();
                                if (mediaDir != null) {
                                    final String path = mediaDir.getPath() + "/" + AccountController.COMPANY_LOGO_FILENAME;
                                    if (!StringUtil.isNullOrEmpty(path)) {
                                        final File logoFile = new File(path);
                                        if (logoFile.exists()) {
                                            try {
                                                if (!logoFile.delete()) {
                                                    LogUtil.e(TAG, "companylogo could not be deleted");
                                                }
                                            } catch (final SecurityException se) {
                                                LogUtil.e(TAG, se.getMessage(), se);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (final LocalizedException e) {
                        LogUtil.e(TAG, e.getMessage(), e);
                        //in den Fall muss es später erneut versucht werden
                    }

                    mApplication.getPreferencesController().serverVersionIsUpToDate(ConfigUtil.SERVER_VERSION_GET_COMPANY_LAYOUT, newServerVersion);
                    resetColorLayoutAndCallListener();
                }

                @Override
                public void onFail(final String message, final String errorIdent) {
                    //in den Fall muss es später erneut versucht werden
                }
            }, executor);
        } else if (StringUtil.isEqual(serverVersionKey, ConfigUtil.SERVER_VERSION_HAS_COMPANY_MANAGEMENT)) {
            loadCompanyManagementInternally(new HasCompanyManagementCallback() {
                @Override
                public void onSuccess(final String managementState) {
                    mApplication.getPreferencesController().serverVersionIsUpToDate(ConfigUtil.SERVER_VERSION_HAS_COMPANY_MANAGEMENT, newServerVersion);
                }

                @Override
                public void onFail(final String message) {
                    //in den Fall muss es später erneut versucht werden
                }
            }, executor);
        } else if (StringUtil.isEqual(serverVersionKey, ConfigUtil.SERVER_VERSION_GET_COMPANY_APP_SETTINGS)) {
            if (getAccount().getState() == Account.ACCOUNT_STATE_FULL) {
                loadCompanyMDMConfig(new GenericActionListener<Void>() {
                    @Override
                    public void onSuccess(Void object) {
                        mApplication.getPreferencesController().serverVersionIsUpToDate(ConfigUtil.SERVER_VERSION_GET_COMPANY_APP_SETTINGS, newServerVersion);
                    }

                    @Override
                    public void onFail(final String message, final String errorIdent) {

                    }
                }, executor);
            }
        } else if (StringUtil.isEqual(serverVersionKey, ConfigUtil.SERVER_VERSION_GET_CONFIRMED_IDENTITIES)) {
            loadConfirmedIdentitiesConfig(new GenericActionListener<Void>() {
                @Override
                public void onSuccess(Void object) {
                    mApplication.getPreferencesController().serverVersionIsUpToDate(ConfigUtil.SERVER_VERSION_GET_CONFIRMED_IDENTITIES, newServerVersion);
                }

                @Override
                public void onFail(final String message, final String errorIdent) {

                }
            }, executor);
        } else if (StringUtil.isEqual(serverVersionKey, ConfigUtil.SERVER_VERSION_GET_PUBLIC_ONLINE_STATE)) {
            try {
                final boolean visible = "1".equals(newServerVersion);
                mApplication.getPreferencesController().setPublicOnlineStateInternally(visible);
                mApplication.getPreferencesController().serverVersionIsUpToDate(ConfigUtil.SERVER_VERSION_GET_PUBLIC_ONLINE_STATE, newServerVersion);
            } catch (final LocalizedException le) {
                LogUtil.w(TAG, le.getMessage(), le);
            }
        }
    }

    public void handleMdmLogin(final String firstName,
                               final String lastName,
                               final String loginCode,
                               final String emailAddress,
                               @NonNull final OnCreateAccountListener createAccountListener) {

        KeyController.OnKeyPairsInitiatedListener listener = new KeyController.OnKeyPairsInitiatedListener() {
            @Override
            public void onKeyPairsInitiated() {
                try {
                    createAccountWithMdmData(firstName,
                            lastName,
                            loginCode,
                            emailAddress,
                            createAccountListener
                    );
                } catch (final LocalizedException e) {
                    LogUtil.w(TAG, e.getMessage(), e);
                }
            }

            @Override
            public void onKeyPairsInitiatedFailed() {
                resetCreateAccountRegisterPhone();
                createAccountListener.onCreateAccountFail(null, false);
            }
        };
        mApplication.getKeyController().initKeys(listener);
    }

    private void createAccountWithMdmData(final String firstName,
                                          final String lastName,
                                          final String loginCode,
                                          final String emailAddress,
                                          @NonNull final OnCreateAccountListener createAccountListener)
            throws LocalizedException {
        String accountGuid = GuidUtil.generateAccountGuid();
        String deviceGuid = GuidUtil.generateDeviceGuid();

        mCreateAccountModel.accountDaoModel = new Account(null,
                mCreateAccountModel.normalizedPhoneNumber,
                deviceGuid, accountGuid, null, null,
                null, null,
                Account.ACCOUNT_STATE_NO_ACCOUNT, null);

        final String refCountryFlag = Locale.getDefault().getLanguage();

        mCreateAccountModel.accountModel = createAccountModel(accountGuid,
                null, emailAddress);
        mCreateAccountModel.deviceModel = createDeviceModel(deviceGuid, refCountryFlag,
                mCreateAccountModel.accountModel);

        JsonArray jsonArray = new JsonArray();

        jsonArray.add(gson.toJsonTree(mCreateAccountModel.accountModel));
        jsonArray.add(gson.toJsonTree(mCreateAccountModel.deviceModel));

        String dataJson = jsonArray.toString();

        final JsonObject jso = new JsonObject();
        final JsonObject innerData = new JsonObject();

        innerData.addProperty("name", lastName);
        innerData.addProperty("firstname", firstName);
        innerData.addProperty("email", emailAddress);

        jso.add("AdressInformation-v1", innerData);

        final String addressInfo = jso.toString();

        final byte[] salt = SecurityUtil.generateSalt();
        final IvParameterSpec ivParameterSpec = SecurityUtil.generateIV();
        final SecretKey secretKey = SecurityUtil.deriveKeyFromPassword(loginCode, salt, 8000, SecurityUtil.DERIVE_ALGORITHM_SHA_256);

        final byte[] bytes = SecurityUtil.encryptStringWithAES(addressInfo, secretKey, ivParameterSpec);
        final String encodedEncryptedAddressInfo = Base64.encodeToString(bytes, Base64.NO_WRAP);

        Map<String, String> key2 = new HashMap<>();

        key2.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP));
        key2.put("iv", Base64.encodeToString(ivParameterSpec.getIV(), Base64.NO_WRAP));
        key2.put("data", encodedEncryptedAddressInfo);
        final String json = new GsonBuilder().serializeNulls().create().toJson(key2);

        IBackendService.OnBackendResponseListener listener = createOnBackendResponseListenerForAccountCreation(createAccountListener, Account.ACCOUNT_STATE_AUTOMATIC_MDM_PROGRESS);

        BackendService.withAsyncConnection(mApplication)
                .createAccountEx(dataJson,
                        null,
                        Locale.getDefault().getLanguage(),
                        loginCode,
                        json,
                        listener);
    }

    @Override
    public void appWillBeLocked() {
        super.appWillBeLocked();

        mCompanyAesKey = null;
        mDomainAesKey = null;
    }

    @Override
    public void appIsUnlock() {
        super.appIsUnlock();

        try {
            if (hasAccountFullState() && isDeviceManaged()) {
                JsonObject mcJO = getAccount().getManagementCompany();

                if (mcJO == null) {
                    loadCompanyInfo(null, null);
                    return;
                }

                boolean hasCompanyKey = false;

                try {
                    hasCompanyKey = mApplication.getPreferencesController().getSharedPreferences().getBoolean("renewCompanyKey", false) && getCompanyAesKey() != null;
                } catch (LocalizedException e) {
                    LogUtil.w(getClass().getSimpleName(), "no company key", e);
                }

                if (hasCompanyKey) {
                    if (!JsonUtil.hasKey(JsonConstants.USER_DATA_KEY_ENC, mcJO)) {
                        //verwaltet aber noch nicht den user dat schluessel....
                        loadCompanyInfo(new HasCompanyManagementCallback() {
                            @Override
                            public void onSuccess(String managementState) {
                                ((ContactControllerBusiness) mApplication.getContactController()).loadCompanyIndexAsync();
                            }

                            @Override
                            public void onFail(String message) {

                            }
                        }, null);
                    } else if (StringUtil.isNullOrEmpty(mApplication.getPreferencesController().getLastCompanyIndexSyncTimeStamp())) {
                        ((ContactControllerBusiness) mApplication.getContactController()).loadCompanyIndexAsync();
                    }
                } else {
                    //Kein Key aber State ist accepted, dann Schluesselinfos neu anfordern
                    if (StringUtil.isEqual(MC_STATE_ACCOUNT_ACCEPTED, getManagementState())) {
                        requestCompanyEncryptionInfo();
                    }
                }
            }
        } catch (LocalizedException e) {
            LogUtil.w(getClass().getSimpleName(), "appIsUnlock()", e);
        }
    }

    private void requestCompanyEncryptionInfo()
            throws LocalizedException {
        mApplication.getPreferencesController().getSharedPreferences().edit().putBoolean("renewCompanyKey", false).apply();

        new AsyncHttpTask<>(new AsyncHttpTask.AsyncHttpCallback<String>() {
            @Override
            public void asyncLoaderServerRequest(IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication).requestEncryptionInfo(listener);
            }

            @Override
            public String asyncLoaderServerResponse(BackendResponse response) {
                return null;
            }

            @Override
            public void asyncLoaderFinished(String result) {
                //
            }

            @Override
            public void asyncLoaderFailed(String errorMessage) {
                //
            }
        }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public SecretKey getCompanyAesKey()
            throws LocalizedException {

        if (mCompanyAesKey == null) {
            JsonObject mcJO = getAccount().getManagementCompany();
            if (mcJO != null) {
                String companyKey = JsonUtil.stringFromJO(JsonConstants.COMPANY_KEY, mcJO);
                if (!StringUtil.isNullOrEmpty(companyKey)) {
                    mCompanyAesKey = SecurityUtil.getAESKeyFromBase64String(companyKey);

                    if (mCompanyAesKey != null) {
                        return mCompanyAesKey;
                    }
                }
            }

            String phone = getAccount().getCustomStringAttribute(MC_ENCRYPTION_PHONE);
            String email = getAccount().getCustomStringAttribute(MC_ENCRYPTION_EMAIL);
            //---> Alte Clients > 2.2
            String suffix = getAccount().getCustomStringAttribute(MC_ENCRYPTION_SEED_SUFFIX);
            //<----

            if (StringUtil.isNullOrEmpty(phone) && StringUtil.isNullOrEmpty(email) && StringUtil.isNullOrEmpty(suffix)) {
                throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "Encryption seed suffix not found.");
            }

            String newSuffix = null;
            if (!StringUtil.isNullOrEmpty(phone)) {
                newSuffix = phone;
            }

            if (!StringUtil.isNullOrEmpty(email)) {
                newSuffix = StringUtil.isNullOrEmpty(newSuffix) ? email : newSuffix + email;
            }

            if (StringUtil.isNullOrEmpty(newSuffix) && !StringUtil.isNullOrEmpty(suffix)) {
                newSuffix = suffix;
            }

            String seed = getAccount().getCustomStringAttribute(MC_ENCRYPTION_SEED);
            if (StringUtil.isNullOrEmpty(seed)) {
                LocalizedException exception = new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "Encryption seed not found.");
                LogUtil.e(TAG, exception.getMessage(), exception);
                throw exception;
            }

            String salt = getAccount().getCustomStringAttribute(MC_ENCRYPTION_SALT);

            if (StringUtil.isNullOrEmpty(salt)) {
                LocalizedException exception = new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "Encryption salt not found.");
                LogUtil.e(TAG, exception.getMessage(), exception);
                throw exception;
            }

            if (newSuffix == null) {
                LocalizedException exception = new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "Encryption suffix not found.");
                LogUtil.e(TAG, exception.getMessage(), exception);
                throw exception;
            }

            String password = seed + newSuffix.toLowerCase(Locale.US);

            String diffAesKey = getAccount().getCustomStringAttribute(MC_ENCRYPTION_DIFF);

            mCompanyAesKey = SecurityUtil.deriveCompanyAesKey(password, salt, diffAesKey);

            if (mCompanyAesKey != null) {
                mcJO = getAccount().getManagementCompany();
                if (mcJO != null) {
                    String keyString = SecurityUtil.getBase64StringFromAESKey(mCompanyAesKey);
                    LogUtil.d("KEY_COMP", keyString);
                    mcJO.addProperty(JsonConstants.COMPANY_KEY, keyString);
                    getAccount().setManagementCompany(mcJO);
                    saveOrUpdateAccount(getAccount());
                }
            }
        }

        return mCompanyAesKey;
    }

    public String getMcEncryptionSalt()
            throws LocalizedException {
        if (mAccount == null) {
            return null;
        } else {
            return mAccount.getCustomStringAttribute(MC_ENCRYPTION_SALT);
        }
    }

    public String getMcEncryptionSeed()
            throws LocalizedException {
        if (mAccount == null) {
            return null;
        } else {
            return mAccount.getCustomStringAttribute(MC_ENCRYPTION_SEED);
        }
    }

    @Override
    public SecretKey getCompanyUserAesKey()
            throws LocalizedException {
        if (mCompanyUserDataAesKey != null) {
            return mCompanyUserDataAesKey;
        }

        SecretKey companyKey = getCompanyAesKey();

        if (companyKey == null) {
            throw new LocalizedException(LocalizedException.KEY_NOT_AVAILABLE, "company key not available");
        }

        JsonObject mcJO = getAccount().getManagementCompany();
        if (mcJO != null) {
            String companyUserDataKey = JsonUtil.stringFromJO(JsonConstants.COMPANY_USER_DATA_KEY, mcJO);
            if (!StringUtil.isNullOrEmpty(companyUserDataKey)) {
                try {
                    mCompanyUserDataAesKey = SecurityUtil.getAESKeyFromBase64String(companyUserDataKey);

                    if (mCompanyUserDataAesKey != null) {
                        return mCompanyUserDataAesKey;
                    }
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                    mcJO.addProperty(JsonConstants.COMPANY_USER_DATA_KEY, "");
                    getAccount().setManagementCompany(mcJO);
                    saveOrUpdateAccount(getAccount());
                }
            }

            String userDataKeyData = JsonUtil.stringFromJO(JsonConstants.USER_DATA_KEY_ENC, mcJO);

            if (StringUtil.isNullOrEmpty(userDataKeyData)) {
                LocalizedException exception = new LocalizedException(LocalizedException.NO_DATA_FOUND, "user data key string not available");
                LogUtil.e(TAG, exception.getMessage(), exception);
                throw exception;
            }

            JsonObject jo = JsonUtil.getJsonObjectFromString(userDataKeyData);

            if (jo == null) {
                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "user data key -> JSON Object is null");
            }

            String iv = JsonUtil.stringFromJO(JsonConstants.IV, jo);
            String data = JsonUtil.stringFromJO(JsonConstants.DATA, jo);

            if (StringUtil.isNullOrEmpty(iv) || StringUtil.isNullOrEmpty(data)) {
                LocalizedException exception = new LocalizedException(LocalizedException.NO_DATA_FOUND, "iv or data not found in JSON Object");
                LogUtil.e(TAG, exception.getMessage(), exception);
                throw exception;
            }

            String decryptedUserDataKeyString;

            try {
                decryptedUserDataKeyString = SecurityUtil.decryptBase64StringWithAES(data, companyKey, iv);
            } catch (LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
                mcJO.addProperty(JsonConstants.COMPANY_KEY, "");
                mcJO.addProperty(JsonConstants.COMPANY_USER_DATA_KEY, "");
                getAccount().setManagementCompany(mcJO);

                mCompanyAesKey = null;
                mCompanyUserDataAesKey = null;

                requestCompanyEncryptionInfo();

                throw e;
            }

            if (StringUtil.isNullOrEmpty(decryptedUserDataKeyString)) {
                LocalizedException exception = new LocalizedException(LocalizedException.NO_DATA_FOUND, "decrypted user data key string is null");
                LogUtil.e(TAG, exception.getMessage(), exception);
                throw exception;
            }

            try {
                mCompanyUserDataAesKey = SecurityUtil.getAESKeyFromBase64String(decryptedUserDataKeyString);
            } catch (LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
                mcJO.addProperty(JsonConstants.COMPANY_KEY, "");
                mcJO.addProperty(JsonConstants.COMPANY_USER_DATA_KEY, "");
                getAccount().setManagementCompany(mcJO);

                mCompanyAesKey = null;
                mCompanyUserDataAesKey = null;

                requestCompanyEncryptionInfo();

                throw e;
            }

            if (mCompanyUserDataAesKey != null) {
                mcJO.addProperty(JsonConstants.COMPANY_USER_DATA_KEY, decryptedUserDataKeyString);
                getAccount().setManagementCompany(mcJO);
                saveOrUpdateAccount(getAccount());
            }

            return mCompanyUserDataAesKey;
        }
        return null;
    }

    public void confirmConfirmEmail(final String confirmationCode, final PhoneOrEmailActionListener listener)
            throws LocalizedException {
        // wenn die email-adresse gerade geaendert wird
        final boolean isChange = AccountController.PENDING_EMAIL_STATUS_WAIT_CONFIRM.equals(getPendingEmailStatus());

        final IBackendService.OnBackendResponseListener responseListener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(final BackendResponse response) {
                if (!response.isError) {
                    if (response.jsonArray != null && response.jsonArray.size() != 0) {
                        final String guid = response.jsonArray.get(0).getAsString();
                        if (StringUtil.isEqual(mAccount.getAccountGuid(), guid)) {
                            try {
                                final String pendingEmailAddress = mAccount.getCustomStringAttribute(PENDING_EMAIL_ADDRESS);
                                final String pendingEmailDomain = mAccount.getCustomStringAttribute(PENDING_EMAIL_DOMAIN);

                                String message = String.format(getResources().getString(R.string.dialog_email_verfication_succeeded), pendingEmailAddress);
                                if (isChange) {
                                    message = getResources().getString(R.string.change_email_address_success);
                                    mAccount.setCustomStringAttribute(PENDING_EMAIL_STATUS, PENDING_EMAIL_STATUS_CONFIRMED);
                                    saveOrUpdateAccount(mAccount);
                                }

                                Contact ownContact = mApplication.getContactController().getOwnContact();
                                mApplication.getContactController().saveContactInformation(ownContact, null, null, null, pendingEmailAddress, pendingEmailDomain, null, null, null, -1, false);
                                //saveEmailAttributes();
                                if (listener != null) {
                                    listener.onSuccess(message);
                                }
                            } catch (LocalizedException e) {
                                LogUtil.e(TAG, e.getMessage(), e);
                                if (listener != null) {
                                    listener.onFail(getResources().getString(isChange ? R.string.change_email_address_error : R.string.unexpected_error_title_next), false);
                                }
                            }

                        } else {
                            if (listener != null) {
                                listener.onFail(getResources().getString(isChange ? R.string.change_email_address_error : R.string.unexpected_error_title_next), false);
                            }
                        }
                    } else {
                        if (listener != null) {
                            listener.onFail(getResources().getString(isChange ? R.string.change_email_address_error : R.string.unexpected_error_title_next), false);
                        }
                    }
                } else {
                    if (StringUtil.isNullOrEmpty(response.errorMessage)) {
                        //response
                        if (response.msgException != null && !StringUtil.isNullOrEmpty(response.msgException.getIdent())) {
                            if (response.msgException.getIdent().equals("ERR-0126")) {
                                try {
                                    final Integer numberofTriesLeft = mAccount.getCustomIntegerAttribute(EMAIL_VERIFICATION_TRIES_LEFT, 10) - 1;

                                    mAccount.setCustomIntegerAttribute(EMAIL_VERIFICATION_TRIES_LEFT, numberofTriesLeft);
                                    accountDao.update(mAccount);

                                    if (listener != null) {
                                        listener.onFail(String.format(getResources().getString(R.string.service_ERR_0126_with_placeholder), numberofTriesLeft.toString()), false);
                                    }

                                    return;
                                } catch (final LocalizedException e) {
                                    LogUtil.e(TAG, e.toString());
                                }
                            } else if (response.msgException.getIdent().equals("ERR-0125")) {
                                if (listener != null) {
                                    listener.onFail(getResources().getString(R.string.service_ERR_0125), false);
                                }
                                return;
                            }
                        }

                        if (listener != null) {
                            listener.onFail(getResources().getString(R.string.unexpected_error_title_next), false);
                        }
                    } else {
                        if (listener != null) {
                            if (response.errorMessage.equals(" (ERR-0126)")) {
                                try {
                                    final Integer numberofTriesLeft = mAccount.getCustomIntegerAttribute(EMAIL_VERIFICATION_TRIES_LEFT, 10) - 1;
                                    mAccount.setCustomIntegerAttribute(EMAIL_VERIFICATION_TRIES_LEFT, numberofTriesLeft);
                                    accountDao.update(mAccount);
                                    listener.onFail(String.format(getResources().getString(R.string.service_ERR_0126_with_placeholder), numberofTriesLeft.toString()), false);
                                } catch (final LocalizedException e) {
                                    LogUtil.e(TAG, e.toString());
                                }
                            } else {
                                listener.onFail(response.errorMessage, false);
                            }
                        }
                    }
                }
            }
        };
        BackendService.withAsyncConnection(mApplication)
                .confirmConfirmationMail(confirmationCode, responseListener);
    }

    public void removeConfirmedMail(final PhoneOrEmailActionListener phoneOrEmailActionListener) {
        try {
            final String phoneNumber = mAccount.getPhoneNumber();
            final String emailAddress = mApplication.getContactController().getOwnContact().getEmail();
            if (StringUtil.isNullOrEmpty(phoneNumber)) {
                phoneOrEmailActionListener.onFail(getResources().getString(R.string.remove_email_address_error_empty_phone), false);
                return;
            }

            final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
                @Override
                public void onBackendResponse(final BackendResponse response) {
                    if (response.isError) {
                        if (response.msgException != null) {
                            phoneOrEmailActionListener.onFail(response.msgException.toString(), false);
                        } else {
                            phoneOrEmailActionListener.onFail(getResources().getString(R.string.remove_email_address_error), false);
                        }
                    } else {
                        try {
                            if (response.jsonArray != null
                                    && response.jsonArray.size() > 0
                                    && (mAccount.getAccountGuid().equals(response.jsonArray.get(0).getAsString()))) {
                                Contact ownContact = mApplication.getContactController().getOwnContact();
                                mApplication.getContactController().saveContactInformation(ownContact, null, null, null, "", "", null, null, null, -1, false);
                                phoneOrEmailActionListener.onSuccess(null);
                            } else {
                                phoneOrEmailActionListener.onFail(getResources().getString(R.string.remove_email_address_error), false);
                                LogUtil.w(TAG, "removeConfirmedMail failed");
                            }
                        } catch (final LocalizedException e) {
                            LogUtil.w(TAG, "removeConfirmedMail failed");
                        }
                    }
                }
            };
            BackendService.withAsyncConnection(mApplication)
                    .removeConfirmedMail(emailAddress, listener);
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, "removeConfirmedPhone failed", e);
        }
    }

    public void unsetPendingEmailStatus(final boolean abort)
            throws LocalizedException {
        mAccount.setCustomStringAttribute(PENDING_EMAIL_ADDRESS, "");
        mAccount.setCustomStringAttribute(PENDING_EMAIL_DOMAIN, "");
        if (abort) {
            mAccount.setCustomStringAttribute(PENDING_EMAIL_STATUS, PENDING_EMAIL_STATUS_NONE);
        } else {
            mAccount.setCustomStringAttribute(PENDING_EMAIL_STATUS, PENDING_EMAIL_STATUS_CONFIRMED);
        }
        saveOrUpdateAccount(mAccount);
    }

    public String getPendingEmailStatus()
            throws LocalizedException {
        return mAccount.getCustomStringAttribute(PENDING_EMAIL_STATUS);
    }

    public String getPendingEmailAddress()
            throws LocalizedException {
        return mAccount.getCustomStringAttribute(PENDING_EMAIL_ADDRESS);
    }

    public boolean hasEmailOrCompanyContacts()
            throws LocalizedException {
        return !StringUtil.isNullOrEmpty(mApplication.getContactController().getOwnContact().getDomain()) || isDeviceManaged();
    }

    private void resetColorLayoutAndCallListener() {
        ScreenDesignUtil.getInstance().reset(mApplication);
        if (mLayoutChangeListeners != null) {
            for (final OnCompanyLayoutChangeListener listener : mLayoutChangeListeners) {
                listener.onCompanyLayoutChanged();
            }
        }
    }

    public void setHasLaunchedRecoveryActivity(final boolean value) {
        mHasLaunchedRecoveryActivity = value;
    }

    public boolean getHasLaunchedRecoveryActivity() {
        return mHasLaunchedRecoveryActivity;
    }

    public void setTrialUsage(final Long usage)
            throws LocalizedException {
        mAccount.setCustomLongAttribute(TRIAL_USAGE, usage);
        saveOrUpdateAccount(mAccount);
    }

    public void resetTrialUsage()
            throws LocalizedException {
        mAccount.resetCustomLongAttribute(TRIAL_USAGE);
        saveOrUpdateAccount(mAccount);
    }

    public boolean isValidTrial()
            throws LocalizedException {
        final Long usage = mAccount.getCustomLongAttribute(TRIAL_USAGE, 0L);

        if (usage == 0L) {
            return false;
        }

        final Long now = new Date().getTime();
        // 1000 * 60 * 60 * 24 * 31 = 2678400000 == 31 Tage
        // 1000 * 60 * 60 * 24 * 30 = 2592000000 == 30 Tage

        //usage date muss groeßer als aktuelles date sein und restlaufzeit sollte kleiner als 31 tage sein
        return usage > now && (usage - now) < 2678400000L;
    }

    public Date getTrialUsage()
            throws LocalizedException {
        final Long usage = mAccount.getCustomLongAttribute(TRIAL_USAGE, 0L);
        if (usage == 0L) {
            return null;
        } else {
            return new Date(usage);
        }
    }

    int getTrialDaysLeft() {
        return mTrialsDaysLeft;
    }

    private void deleteCompanyLogo() {

        final FileUtil fileUtil = new FileUtil(mApplication);
        final File file = fileUtil.getInternalMediaFile(COMPANY_LOGO_FILENAME);
        if (file != null && file.exists()) {
            try {
                if (!file.delete()) {
                    LogUtil.e(TAG, "companylogo could not be deleted");
                }
            } catch (final SecurityException se) {
                LogUtil.e(TAG, se.getMessage(), se);
            }
        }
    }

    public void deleteAccount() {
        mApplication.getPreferencesController().getSharedPreferences().edit().remove(ScreenDesignUtil.COMPANY_LAYOUT_JSON).apply();
        deleteCompanyLogo();
        resetColorLayoutAndCallListener();
        if (mLogoChangeListener != null) {
            mLogoChangeListener.onCompanyLogoChanged(null);
        }
        super.deleteAccount();
        deleteCompanyLogo();
    }

    public void resetLayout() {
        if (Looper.myLooper() != mApplication.getMainLooper()) {
            Handler handler = new Handler(mApplication.getMainLooper());
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    resetLayoutInternally();
                }
            };
            handler.post(runnable);
        } else {
            resetLayoutInternally();
        }
    }

    private void resetLayoutInternally() {
        mApplication.getPreferencesController().getSharedPreferences().edit().remove(ScreenDesignUtil.COMPANY_LAYOUT_JSON).apply();
        deleteCompanyLogo();
        resetColorLayoutAndCallListener();
        if (mLogoChangeListener != null) {
            mLogoChangeListener.onCompanyLogoChanged(null);
        }
        deleteCompanyLogo();
        mApplication.getImageController().clearImageCaches(true, true);
    }

    public void resetCreateAccountRegisterPhone() {
        resetLayout();
        super.resetCreateAccountRegisterPhone();
    }

    public void startConfigureCompanyAccount(@NonNull final ConfigureCompanyAccountListener listener) {
        // The filter's action is BROADCAST_ACTION
        IntentFilter statusIntentFilter = new IntentFilter(AppConstants.BROADCAST_COMPANY_ACTION);

        // Sets the filter's category to DEFAULT
        statusIntentFilter.addCategory(Intent.CATEGORY_DEFAULT);

        // Instantiates a new BackupStateReceiver
        final ConfigureReceiver configureStateReceiver = new ConfigureReceiver(listener);

        // Registers the Receiver
        LocalBroadcastManager.getInstance(mApplication)
                .registerReceiver(configureStateReceiver, statusIntentFilter);

        Intent configureServiceIntent = new Intent(mApplication, ConfigureCompanyService.class);
        mApplication.startService(configureServiceIntent);
    }

    private class ConfigureReceiver extends BroadcastReceiver {
        final ConfigureCompanyAccountListener listener;
        int contactSize;
        int lastState;

        public ConfigureReceiver(@NonNull final ConfigureCompanyAccountListener listener) {
            //
            this.listener = listener;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(AppConstants.INTENT_EXTENDED_DATA_STATUS, -1);
            LogUtil.d(TAG, "ConfigureCompanyAccountReceiver: onReceive state = " + state);
            switch (state) {
                case AppConstants.CONFIGURE_COMPANY_STATE_STARTED:
                case AppConstants.CONFIGURE_COMPANY_STATE_CONNECTING:
                case AppConstants.CONFIGURE_COMPANY_STATE_GET_CONFIG:
                case AppConstants.CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_START:
                case AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_START: {
                    listener.onConfigureStateChanged(state);
                    contactSize = 0;
                    break;
                }
                case AppConstants.CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_SIZE:
                case AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_SIZE: {
                    contactSize = intent.getIntExtra(AppConstants.INTENT_EXTENDED_DATA_EXTRA, -1);
                    listener.onConfigureStateUpdate(state, 0, contactSize);
                    break;
                }
                case AppConstants.CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_UPDATE:
                case AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_UPDATE: {
                    int current = intent.getIntExtra(AppConstants.INTENT_EXTENDED_DATA_EXTRA, -1);
                    listener.onConfigureStateUpdate(state, current, contactSize);
                    break;
                }
                case AppConstants.CONFIGURE_COMPANY_STATE_ERROR: {
                    Exception e = SystemUtil.dynamicDownCast(intent.getSerializableExtra(AppConstants.INTENT_EXTENDED_DATA_EXCEPTION), Exception.class);

                    if (e != null) {
                        LogUtil.e(TAG, e.getMessage(), e);
                    }

                    String identifier = null;
                    if (e instanceof LocalizedException) {
                        identifier = ((LocalizedException) e).getIdentifier();
                    }

                    String message = intent.getStringExtra(AppConstants.INTENT_EXTENDED_DATA_EXTRA);

                    listener.onConfigureFailed(message, identifier, lastState);

                    LocalBroadcastManager.getInstance(mApplication).unregisterReceiver(this);
                    break;
                }
                case AppConstants.CONFIGURE_COMPANY_STATE_FINISHED: {
                    LocalBroadcastManager.getInstance(mApplication).unregisterReceiver(this);
                    listener.onConfigureStateChanged(state);
                    break;
                }
            }
            lastState = state;
        }
    }
}
