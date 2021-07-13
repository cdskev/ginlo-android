// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Base64;
import android.util.Base64InputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import org.json.JSONArray;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Locale;
import java.util.Map;

import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.billing.Purchase;
import eu.ginlo_apps.ginlo.broadcastreceiver.ConnectionBroadcastReceiver;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.concurrent.task.HttpBaseTask;
import eu.ginlo_apps.ginlo.concurrent.task.HttpLazyMessageTask;
import eu.ginlo_apps.ginlo.concurrent.task.HttpSingleTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.TaskManagerController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.ApplicationError;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.MsgExceptionModel;
import eu.ginlo_apps.ginlo.model.backend.serialization.MsgExceptionModelDeserializer;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.model.param.HttpPostParams;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.ChecksumUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StreamUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

public class BackendService
        implements IBackendService {

    private static final String TAG = BackendService.class.getSimpleName();

    private static final String CREATE_ACCOUNT_ENDPOINT = "/MsgService/CreateAccount";
    private static final String DEFAULT_ENDPOINT = "/MsgService";
    private static final String LAZY_MSG_ENDPOINT = "/MsgService/LazyService";
    private static final String DIAGNOSE_ENDPOINT = "/MsgService/Diag";
    private static final String BACKGROUND_ENDPOINT = "/MsgService/BackgroundService";
    private static final String RECOVERY_ENDPOINT = "/MsgService/RecoveryService";
    private static final String CERT_PINNING_CA_PW = BuildConfig.CERT_PINNING_CA_PW;
    private static final int CONNECTION_TIMEOUT = 60000;

    private static KeyStore keyStore;

    private final Gson gson;
    private final ConnectivityManager connectivityManager;
    private final Resources resources;
    private final SimsMeApplication application;
    private String username;
    private String password;
    private boolean mUseAsycnConnections;
    // Können neue THreads gestartet werden, oder wird die VM gerade Terminiert
    private boolean mCanStartThreads;

    static {
        loadKeystore();
    }

    private BackendService(final SimsMeApplication application) {
        mCanStartThreads = true;
        mUseAsycnConnections = true;
        this.application = application;

        resources = application.getResources();
        connectivityManager = (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);


        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(MsgExceptionModel.class, new MsgExceptionModelDeserializer());
        gson = gsonBuilder.create();

        final Thread shutdownHandler = new Thread() {
            @Override
            public void run() {
                LogUtil.i(TAG, "Terminating VM");
                mCanStartThreads = false;
            }
        };
        Runtime.getRuntime().addShutdownHook(shutdownHandler);
    }

    /**
     * Get a backend service that communicates synchronously to the backend
     */
    public static IBackendService withSyncConnection(final SimsMeApplication application) {
        return getBackendService(application, false);
    }

    /**
     * Get a backend service that communicates asynchronously to the backend
     */
    public static IBackendService withAsyncConnection(final SimsMeApplication application) {
        return getBackendService(application, true);
    }

    private static IBackendService getBackendService(final SimsMeApplication application, boolean forAsyncConnections) {
        BackendService service = new BackendService(application);
        service.setUseAsyncConnections(forAsyncConnections);
        return service;
    }

    private String setUpDefaultEndpointCall() {
        setUpWithAccountCredential();
        return getEndPointUrl(DEFAULT_ENDPOINT);
    }

    private String setUpLazyEndpointCall() {
        setUpWithAccountCredential();
        return getEndPointUrl(LAZY_MSG_ENDPOINT);
    }

    private String setUpDiagnosticEndpointCall() {
        setUpWithDiagnosticCredential();
        return getEndPointUrl(DIAGNOSE_ENDPOINT);
    }

    private String setUpAccountCreationEndpointCall() {
        setUpWithAccountCreationCredential();
        return getEndPointUrl(CREATE_ACCOUNT_ENDPOINT);
    }

    private String setUpBackgroundServiceEndpointCall() {
        setUpWithBackgroundAccessTokenCredential();
        return getEndPointUrl(BACKGROUND_ENDPOINT);
    }

    private String setUpRecoveryEndpointCall(String accountGuid, String backupPassToken) {
        this.elevateRights(null, accountGuid, backupPassToken);
        return getEndPointUrl(RECOVERY_ENDPOINT);
    }

    private void setUpWithAccountCredential() {
        Account account = application.getAccountController().getAccount();
        if (account != null) {
            final String deviceGuid = account.getDeviceGuid();
            final String accountGuid = account.getAccountGuid();
            String passToken = null;
            try {
                passToken = account.getPasstoken();

                if (StringUtil.isNullOrEmpty(passToken)) {
                    LogUtil.e(TAG, "Backend Error: Account passtoken is unexpectedly empty or null");
                }
            } catch (LocalizedException e) {
                LogUtil.e(TAG, "Backend Error: Account passtoken is unexpectedly empty or null");
            }

            this.elevateRights(deviceGuid, accountGuid, passToken);
        } else {
            LogUtil.e(TAG, "Backend Error: Account is unexpectedly null.");
        }
    }

    private void setUpWithBackgroundAccessTokenCredential() {
        Account account = application.getAccountController().getAccount();
        if (account != null) {
            final String deviceGuid = account.getDeviceGuid();
            final String accountGuid = account.getAccountGuid();
            final String passToken = application.getPreferencesController().getFetchInBackgroundAccessToken();

            if (StringUtil.isNullOrEmpty(passToken)) {
                LogUtil.e(TAG, "Backend Error: Background access token is unexpectedly empty or null");
            }

            this.elevateRights(deviceGuid, accountGuid, passToken);
        } else {
            LogUtil.e(TAG, "Backend Error: Account is unexpectedly null while accessing backend access token.");
        }
    }

    private void setUpWithAccountCreationCredential() {
        username = null;
        password = null;
    }

    private void setUpWithDiagnosticCredential() {
        username = null;
        password = null;
    }

    public static boolean isConnected(@NonNull final SimsMeApplication application) {
        ConnectivityManager connectivityManager = (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            //Im zweifel true
            return true;
        }
        final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        return networkInfo != null && networkInfo.isConnected();
    }

    /**
     * Load the static keystore here. Beware of threading issue should you decide to use this method other than
     * in the static initializer.
     */
    private static void loadKeystore() {
        InputStream inputStream = null;
        try {

            final String keyStoreData2 = BuildConfig.APPLICATION_KEYSTORE_DATA;

            inputStream = new Base64InputStream(new ByteArrayInputStream(keyStoreData2.getBytes(StandardCharsets.UTF_8)), Base64.DEFAULT);

            // getKeyStoreInstance and loading takes a bit of time that's why it is stored in a temp first
            // to avoid the static keyStore from being used between being constructed, and loading on another thread.
            KeyStore keyStoreTemp = SecurityUtil.getKeyStoreInstance("BKS");
            keyStoreTemp.load(inputStream, CERT_PINNING_CA_PW.toCharArray());

            keyStore = keyStoreTemp;

        } catch (NoSuchAlgorithmException | CertificateException | KeyStoreException | IOException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        } finally {
            if (inputStream != null) {
                StreamUtil.closeStream(inputStream);
            }
        }
    }

    public void elevateRights(final String deviceGuid,
                              final String accountGuid,
                              final String passToken) {
        username = constructUserName(deviceGuid, accountGuid);
        password = passToken;
    }

    private String constructUserName(final String deviceGuid,
                                     final String accountGuid) {
        final String deviceGuidWithoutPrefix;
        if (!StringUtil.isNullOrEmpty(deviceGuid)) {
            deviceGuidWithoutPrefix = deviceGuid.substring(2);
        } else {
            deviceGuidWithoutPrefix = null;
        }
        final String accountGuidWithoutPrefix = accountGuid.substring(2);

        return (deviceGuidWithoutPrefix == null) ? accountGuidWithoutPrefix : deviceGuidWithoutPrefix + "@" + accountGuidWithoutPrefix;
    }

    public void createAccountEx(final String dataJson,
                                final String phoneNumber,
                                final String language,
                                final String cockpitToken,
                                final String cockpitData,
                                final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpAccountCreationEndpointCall());

        params.addParam("cmd", "CreateAccountEx");
        if (!StringUtil.isNullOrEmpty(phoneNumber)) {
            params.addParam("phone", phoneNumber);
        }
        params.addParam("language", language);
        params.addParam("data", dataJson);
        params.addParam("data-checksum", ChecksumUtil.getMD5ChecksumForString(dataJson));
        params.addParam("allowFreeMailer", "true");

        if (!StringUtil.isNullOrEmpty(cockpitToken)) {
            params.addParam("cockpitToken", cockpitToken);
        }
        if (!StringUtil.isNullOrEmpty(cockpitData)) {
            params.addParam("cockpitData", cockpitData);
        }

        if (!RuntimeConfig.isB2c()) {
            params.addParam("mandant", RuntimeConfig.getMandant());
        }

        callBackend(listener, params);
    }

    public void requestCoupling(final String accountGuid,
                                final String transactionID,
                                final String publicKey,
                                final String encVrfy,
                                final String reqType,
                                final String appData,
                                final String signature,
                                final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpAccountCreationEndpointCall());

        params.addParam("cmd", "requestCoupling");
        params.addParam("accountGuid", accountGuid);
        params.addParam("transId", transactionID);
        params.addParam("pubKey", publicKey);
        params.addParam("encVrfy", encVrfy);
        params.addParam("reqType", reqType);
        params.addParam("appData", appData);
        params.addParam("sig", signature);

        callBackend(listener, params);
    }

    public void initialiseCoupling(final String transactionID,
                                   final String timestamp,
                                   final String encTan,
                                   final String appData,
                                   final String signature,
                                   final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "initialiseCoupling");
        params.addParam("transId", transactionID);
        params.addParam("ts", timestamp);
        params.addParam("tan", encTan);
        params.addParam("appData", appData);
        params.addParam("sig", signature);

        callBackend(listener, params);
    }

    public void cancelCoupling(final String transactionID,
                               final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "cancelCoupling");
        params.addParam("transId", transactionID);

        callBackend(listener, params);
    }

    public void responseCoupling(
            final String transactionID,
            final String device,
            final String devKeySig,
            final String kek,
            final String kekIv,
            final String encSyncData,
            final String appData,
            final String sig,
            final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "responseCoupling");
        params.addParam("transId", transactionID);
        params.addParam("device", device);
        params.addParam("devKeySig", devKeySig);
        params.addParam("kek", kek);
        params.addParam("kekIV", kekIv);
        params.addParam("encSyncData", encSyncData);
        params.addParam("appData", appData);
        params.addParam("sig", sig);

        callBackend(listener, params);
    }

    public void getCouplingRequest(final String transactionID,
                                   final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getCouplingRequest");
        params.addParam("transId", transactionID);

        callBackend(listener, params, null, true, false);
    }

    public void getCouplingResponse(final String accountGuid,
                                    final String transactionID,
                                    final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpAccountCreationEndpointCall());

        params.addParam("cmd", "getCouplingResponse");
        params.addParam("accountGuid", accountGuid);
        params.addParam("transId", transactionID);

        callBackend(listener, params, null, true, false);
    }

    public void setChatDeleted(final String guid, final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setChatDeleted");
        params.addParam("guid", guid);

        callBackend(listener, params);
    }

    public void setOptInState(final String state, final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setOptInState");
        params.addParam("state", state);

        callBackend(listener, params);
    }

    @Override
    public void getOptInState(OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getOptInState");

        callBackend(listener, params);
    }

    public void isConfirmationValid(final String confirmCode,
                                    final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "isConfirmationValid");
        params.addParam("confirmationCode", confirmCode);

        callBackend(listener, params);
    }

    public void validateMail(final String email,
                             final boolean isAccountConfirmed,
                             final OnBackendResponseListener listener) {
        String url = isAccountConfirmed ? setUpDefaultEndpointCall() : setUpAccountCreationEndpointCall();

        final HttpPostParams params = new HttpPostParams(url);
        params.addParam("cmd", "validateMail");
        params.addParam("email", email);

        callBackend(listener, params);
    }

    /**
     * This method uses the Recovery Endpoint which needs the special account backPasstoken.
     *
     * @param deviceJson
     * @param phone
     * @param listener
     */
    public void createDevice(final String accountGuid,
                             final String backupPassToken,
                             final String deviceJson,
                             final String phone,
                             final OnBackendResponseListener listener) {

        final HttpPostParams params = new HttpPostParams(setUpRecoveryEndpointCall(accountGuid, backupPassToken));
        params.addParam("cmd", "createDevice");
        params.addParam("device", deviceJson);
        params.addParam("mandant", RuntimeConfig.getMandant());
        if (!StringUtil.isNullOrEmpty(phone)) {
            params.addParam("phoneNumber", phone);
        }

        callBackend(listener, params);
    }

    public void createAdditionalDevice(final String accountGuid,
                                       final String transId,
                                       final String deviceJsonString,
                                       final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpAccountCreationEndpointCall());
        params.addParam("cmd", "createAdditionalDevice");
        params.addParam("accountGuid", accountGuid);
        params.addParam("device", deviceJsonString);
        params.addParam("transId", transId);
        callBackend(listener, params);
    }

    public void confirmAccount(final String confirmationCode,
                               final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "confirmAccountEx");
        params.addParam("confirmationCode", confirmationCode);

        callBackend(listener, params);
    }

    public void resetBadge(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "resetBadge");

        callBackend(listener, params);
    }

    public void requestConfirmationMail(final String eMailAddress,
                                        final boolean forceCreation,
                                        final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "requestConfirmationMail");
        params.addParam("language", Locale.getDefault().getLanguage());
        params.addParam("email", eMailAddress);
        params.addParam("allowFreeMailer", "true");
        params.addParam("forceCreation", forceCreation ? "1" : "0");

        callBackend(listener, params);
    }

    public void confirmConfirmationMail(final String confirmCode,
                                        final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "confirmConfirmationMail");
        params.addParam("confirmMode", "newDeviceV1");
        params.addParam("code", confirmCode);

        callBackend(listener, params);
    }

    public void requestConfirmPhone(final String phone,
                                    final boolean forceCreation,
                                    final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "requestConfirmPhone");
        params.addParam("phone", phone);
        params.addParam("language", Locale.getDefault().getLanguage());

        if (forceCreation) {
            params.addParam("forceCreation", "true");
        } else {
            params.addParam("forceCreation", "false");
        }
        callBackend(listener, params);
    }

    public void confirmConfirmPhone(final String confirmCode,
                                    final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "confirmConfirmPhone");
        params.addParam("code", confirmCode);
        params.addParam("confirmMode", "newDeviceV1");
        callBackend(listener, params);
    }

    public void removeConfirmedMail(final String emailAddress,
                                    final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "removeConfirmedMail");
        params.addParam("email", emailAddress);
        callBackend(listener, params);
    }

    public void removeConfirmedPhone(final String phoneNumber,
                                     final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "removeConfirmedPhone");
        params.addParam("phone", phoneNumber);
        callBackend(listener, params);
    }

    public void setAddressInformation(final String dataJson,
                                      final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setAdressInformation");
        params.addParam("data", dataJson);

        callBackend(listener, params);
    }

    public void setAutoGeneratedMessages(final String dataJson,
                                         final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setAutoGeneratedMessages");
        params.addParam("data", dataJson);

        callBackend(listener, params);
    }

    public void getAutoGeneratedMessages(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getAutoGeneratedMessages");

        callBackend(listener, params);
    }

    public void getAddressInformation(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getAdressInformations");

        callBackend(listener, params, null, false, false, null, CONNECTION_TIMEOUT);
    }

    public void getAddressInformationBatch(final String guids,
                                           final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getAdressInformationBatch");
        params.addParam("guids", guids);

        callBackend(listener, params, null, false, false, null, CONNECTION_TIMEOUT);
    }

    public void listCompanyIndex(final String ifModifiedSince, final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "listCompanyIndex");

        if (!StringUtil.isNullOrEmpty(ifModifiedSince)) {
            params.addParam("ifModifiedSince", ifModifiedSince);
        }

        callBackend(listener, params, null, false, false, null, CONNECTION_TIMEOUT);
    }

    public void getCompanyIndexEntries(final String guids, final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getCompanyIndexEntries");

        params.addParam("guids", guids);

        callBackend(listener, params, null, false, false, null, CONNECTION_TIMEOUT);
    }

    public void deleteAccount(final OnBackendResponseListener listener) {
        if ((username == null) || (password == null)) {
            final BackendResponse response = wrapError(
                    application.getResources().getString(R.string.settings_profile_delete_error_backendCredentials));

            listener.onBackendResponse(response);
        }

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "deleteAccount");

        callBackend(listener, params);
    }

    public void sendPrivateMessage(final String messageJson,
                                   final OnBackendResponseListener listener,
                                   final String requestId) {
        LogUtil.i(this.getClass().toString(), "SendPrivateMessage");

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "sendPrivateMessage");
        params.addParam("message", messageJson);
        params.addParam("returnConfirmMessage", "1");
        if(messageJson.startsWith("/")) {
            params.addParam("message-checksum", ChecksumUtil.getMD5HashFromFile(new File(messageJson)));
        } else {
            params.addParam("message-checksum", ChecksumUtil.getMD5ChecksumForString(messageJson));
        }

        callBackend(listener, params, requestId);
    }

    public void sendTimedPrivateMessage(final String messageJson,
                                        final OnBackendResponseListener listener,
                                        final String requestId,
                                        final String date) {
        LogUtil.i(this.getClass().toString(), "sendTimedPrivateMessage");

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "sendTimedPrivateMessage");
        params.addParam("message", messageJson);
        if(messageJson.startsWith("/")) {
            params.addParam("message-checksum", ChecksumUtil.getMD5HashFromFile(new File(messageJson)));
        } else {
            params.addParam("message-checksum", ChecksumUtil.getMD5ChecksumForString(messageJson));
        }
        params.addParam("dateToSend", date);

        callBackend(listener, params, requestId);
    }

    public void sendPrivateInternalMessage(final String messageJson,
                                           final OnBackendResponseListener listener,
                                           final String requestId) {
        LogUtil.i(this.getClass().toString(), "sendPrivateInternalMessage");

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "sendPrivateInternalMessage");
        params.addParam("message", messageJson);
        params.addParam("returnConfirmMessage", "1");
        if(messageJson.startsWith("/")) {
            params.addParam("message-checksum", ChecksumUtil.getMD5HashFromFile(new File(messageJson)));
        } else {
            params.addParam("message-checksum", ChecksumUtil.getMD5ChecksumForString(messageJson));
        }

        callBackend(listener, params, requestId);
    }

    @Override
    public void sendPrivateInternalMessages(String messagesJson,
                                            OnBackendResponseListener listener) {
        LogUtil.i(this.getClass().toString(), "sendPrivateInternalMessages");

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "sendPrivateInternalMessages");
        params.addParam("messages", messagesJson);
        params.addParam("returnConfirmMessage", "1");
        if(messagesJson.startsWith("/")) {
            params.addParam("message-checksum", ChecksumUtil.getMD5HashFromFile(new File(messagesJson)));
        } else {
            params.addParam("message-checksum", ChecksumUtil.getMD5ChecksumForString(messagesJson));
        }

        callBackend(listener, params);
    }

    public void sendGroupMessage(final String messageJson,
                                 final OnBackendResponseListener listener,
                                 final String requestId) {
        LogUtil.i(this.getClass().toString(), "sendGroupMessage");

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "sendGroupMessage");
        params.addParam("message", messageJson);
        params.addParam("returnConfirmMessage", "1");
        if(messageJson.startsWith("/")) {
            params.addParam("message-checksum", ChecksumUtil.getMD5HashFromFile(new File(messageJson)));
        } else {
            params.addParam("message-checksum", ChecksumUtil.getMD5ChecksumForString(messageJson));
        }

        callBackend(listener, params, requestId);
    }

    public void sendTimedGroupMessage(final String messageJson,
                                      final OnBackendResponseListener listener,
                                      final String requestId,
                                      final String date) {
        LogUtil.i(this.getClass().toString(), "sendTimedGroupMessage");

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "sendTimedGroupMessage");
        params.addParam("message", messageJson);
        if(messageJson.startsWith("/")) {
            params.addParam("message-checksum", ChecksumUtil.getMD5HashFromFile(new File(messageJson)));
        } else {
            params.addParam("message-checksum", ChecksumUtil.getMD5ChecksumForString(messageJson));
        }
        params.addParam("dateToSend", date);

        callBackend(listener, params, requestId);
    }

    public void getTenants(final OnBackendResponseListener listener) {
        LogUtil.i(this.getClass().toString(), "getTenants");

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getMandanten");
        callBackend(listener, params);
    }

    public void getNewMessages(final OnBackendResponseListener listener,
                               final boolean useLazyMsgService) {

        String endPoint = useLazyMsgService ? setUpLazyEndpointCall() : setUpDefaultEndpointCall();

        final HttpPostParams params = new HttpPostParams(endPoint);

        params.addParam("cmd", "getNewMessages");

        callBackend(listener, params, null, useLazyMsgService, false);
    }

    public void getTimedMessageGuids(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getTimedMessageGuids");

        callBackend(listener, params);
    }

    @Override
    public void getTimedMessages(String guids, OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getTimedMessages");
        params.addParam("guids", guids);

        callBackend(listener, params);
    }

    @Override
    public void getServices(OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getServices");

        callBackend(listener, params);
    }

    public void getServiceDetailsBatch(final String serviceGuids, final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getServiceDetailsBatch");
        params.addParam("guids", serviceGuids);

        callBackend(listener, params);
    }

    public void getKnownAccounts(final JsonArray data,
                                 final String salt,
                                 final String tenant,
                                 final String searchMode,
                                 final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getKnownAccounts");
        params.addParam("data", data.toString());
        params.addParam("data-checksum", ChecksumUtil.getMD5ChecksumForString(data.toString()));
        params.addParam("salt", salt);
        params.addParam("searchMode", searchMode);
        params.addParam("mandant", tenant);

        callBackend(listener, params);
    }

    /**
     * @param withProfileInfo Über den Parameter profileInfo kann gesteuert werden, ob man die einfachen ProfilInformationen
     *                        (==1), die strukturierten Abwesenheitsnotiz (==2), oder beide Informationen haben möchte (==3)
     */
    public void getAccountInfo(final String accountGuid,
                               final int withProfileInfo,
                               final boolean withTenant,
                               final boolean checkReadonly,
                               final boolean withTempDevice,
                               final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getAccountInfo");
        params.addParam("accountGuid", accountGuid);
        params.addParam("profileInfo", "" + withProfileInfo);
        params.addParam("mandant", withTenant ? "1" : "0");
        params.addParam("tempDevice", withTempDevice ? "1" : "0");
        if (checkReadonly) {
            params.addParam("checkReadonly", "1");
        }

        callBackend(listener, params);
    }

    public void getAccountInfoAnonymous(final String searchText,
                                        final String searchMode,
                                        final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpAccountCreationEndpointCall());

        params.addParam("cmd", "getAccountInfo");
        params.addParam("data", searchText);
        params.addParam("searchMode", searchMode);
        callBackend(listener, params);
    }

    public void getAccountImage(final String accountGuid,
                                final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getAccountImage");
        params.addParam("accountGuid", accountGuid);

        callBackend(listener, params);
    }

    /**
     * Abfragen der Account-Informationen im BatchModus. Es werden nur die
     * öffentlichen Daten aus der Tabelle msg_public_account zurückgegeben. Dies
     * ist insbesondere nur der PublicKey des Accounts. Die Telefonnummer wird
     * explizit NICHT zurückgegeben. Für nicht vorhandene Accounts wird KEINE
     * Exception zurück gegeben, sondern es wird keine Antwort für diesen Eintrag
     * zurückgegeben Server Responce: JSON-Array mit Verkürzte
     * Accountinformationen: Bsp.:
     * [{"Account":{"guid":"0:{1234}","publicKey":"<RSA..."}}]
     */
    public void getAccountInfoBatch(final String accountGuids,
                                    final boolean withProfileInfo,
                                    final boolean withTenant,
                                    final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getAccountInfoBatch");
        params.addParam("guids", accountGuids);
        params.addParam("profileInfo", withProfileInfo ? "1" : "0");
        params.addParam("mandant", withTenant ? "1" : "0");

        callBackend(listener, params);
    }

    public void getAttachment(final String attachmentGuid,
                              final OnBackendResponseListener listener,
                              final HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getAttachment");
        params.addParam("guid", attachmentGuid);

        callBackend(listener, params, onConnectionDataUpdatedListener);
    }

    public void setDeviceData(final String dataJson,
                              final OnBackendResponseListener listener) {
        LogUtil.i(this.getClass().toString(), "setDeviceData");

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setDeviceData");
        params.addParam("data", dataJson);
        params.addParam("data-checksum", ChecksumUtil.getMD5ChecksumForString(dataJson));

        callBackend(listener, params);
    }

    public void createGroup(final String dataJson,
                            final String groupInvMessagesAsJson,
                            final String adminGuids,
                            final String nickname,
                            final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "createGroup");
        params.addParam("groupData", dataJson);
        params.addParam("groupData-checksum", ChecksumUtil.getMD5ChecksumForString(dataJson));
        params.addParam("addRoomMemberData", groupInvMessagesAsJson);
        params.addParam("addRoomMemberData-checksum", ChecksumUtil.getMD5ChecksumForString(groupInvMessagesAsJson));

        if (adminGuids != null) {
            params.addParam("makeAdminGuids", adminGuids);
        }

        if (!StringUtil.isNullOrEmpty(nickname)) {
            params.addParam("nickName", nickname);
        }

        params.addParam("returnComplexResult", "1");

        callBackend(listener, params);
    }

    public void getRoom(final String chatRoomGuid,
                        final OnBackendResponseListener listener,
                        final boolean checkReadonly) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getRoom");
        params.addParam("guid", chatRoomGuid);

        if (checkReadonly) {
            params.addParam("checkReadonly", "1");
        }

        callBackend(listener, params);
    }

    public void getRoomMemberInfo(final String chatRoomGuid,
                                  final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getRoomMemberInfo");
        params.addParam("roomGuid", chatRoomGuid);

        callBackend(listener, params);
    }

    public void updateGroup(final String groupGuid,
                            final String newMembers,
                            final String removedMembers,
                            final String newAdmins,
                            final String removedAdmins,
                            final String data,
                            final String keyIv,
                            final String nickname,
                            final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "updateGroup");
        params.addParam("roomGuid", groupGuid);

        if (!StringUtil.isNullOrEmpty(newMembers)) {
            params.addParam("newMembers", newMembers);
        }

        if (!StringUtil.isNullOrEmpty(removedMembers)) {
            params.addParam("removedMembers", removedMembers);
        }

        if (!StringUtil.isNullOrEmpty(newAdmins)) {
            params.addParam("newAdmins", newAdmins);
        }

        if (!StringUtil.isNullOrEmpty(removedAdmins)) {
            params.addParam("removedAdmins", removedAdmins);
        }

        if (!StringUtil.isNullOrEmpty(nickname)) {
            params.addParam("nickName", nickname);
        }

        if (!StringUtil.isNullOrEmpty(data) && !StringUtil.isNullOrEmpty(keyIv)) {
            params.addParam("data", data);
            params.addParam("key-iv", keyIv);
        }

        callBackend(listener, params);
    }

    @Override
    public void setGroupInfo(String groupGuid, String data, String keyIv, OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setGroupInfo");
        params.addParam("roomGuid", groupGuid);
        params.addParam("data", data);
        params.addParam("key-iv", keyIv);

        callBackend(listener, params);
    }

    @Override
    public void getCurrentRoomInfo(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getCurrentRoomInfo");

        callBackend(listener, params);
    }

    public void removeRoom(final String chatRoomGuid,
                           final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "removeRoom");
        params.addParam("guid", chatRoomGuid);

        callBackend(listener, params);
    }

    public void removeTimedMessage(final String messageGuid,
                                   final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "removeTimedMessage");
        params.addParam("guid", messageGuid);

        callBackend(listener, params);
    }

    public void removeTimedMessageBatch(final String messageGuids,
                                        final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "removeTimedMessageBatch");
        params.addParam("guids", messageGuids);

        callBackend(listener, params);
    }

    public void removeFromRoom(final String chatRoomGuid,
                               final String removeAccountGuid,
                               final String nickname,
                               final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "removeFromRoom");
        params.addParam("roomGuid", chatRoomGuid);
        params.addParam("accountGuid", removeAccountGuid);
        params.addParam("returnComplexResult", "1");

        if (!StringUtil.isNullOrEmpty(nickname)) {
            params.addParam("nickName", nickname);
        }

        callBackend(listener, params);
    }

    public void acceptRoomInvitation(final String chatRoomGuid,
                                     final String nickname,
                                     final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "acceptRoomInvitation");
        params.addParam("guid", chatRoomGuid);

        if (!StringUtil.isNullOrEmpty(nickname)) {
            params.addParam("nickName", nickname);
        }

        params.addParam("returnComplexResult", "1");

        callBackend(listener, params);
    }

    public void declineRoomInvitation(final String chatRoomGuid,
                                      final String nickname,
                                      final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "declineRoomInvitation");
        params.addParam("guid", chatRoomGuid);
        if (!StringUtil.isNullOrEmpty(nickname)) {
            params.addParam("nickName", nickname);
        }
        params.addParam("returnComplexResult", "1");

        callBackend(listener, params);
    }

    @Override
    public void getCompany(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getCompany");

        callBackend(listener, params);
    }

    @Override
    public void getSimsmeRecoveryPublicKey(final OnBackendResponseListener listener) {

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getSimsmeRecoveryPublicKey");
        callBackend(listener, params);
    }

    @Override
    public void requestSimsmeRecoveryKey(final OnBackendResponseListener listener,
                                         final String transId,
                                         final String recoveryToken,
                                         final String recoveryChannel,
                                         final String sig
    ) {
        final HttpPostParams params = new HttpPostParams(setUpBackgroundServiceEndpointCall());
        params.addParam("cmd", "requestSimsmeRecoveryKey");
        params.addParam("transId", transId);
        params.addParam("recoveryToken", recoveryToken);
        params.addParam("recoveryChannel", recoveryChannel);
        params.addParam("sig", sig);
        params.addParam("language", Locale.getDefault().getLanguage());
        callBackend(listener, params);
    }

    @Override
    public void getCompanyLayout(OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getCompanyLayout");
        callBackend(listener, params);
    }

    @Override
    public void getCompanyLogo(OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getCompanyLogo");
        callBackend(listener, params);
    }

    @Override
    public void requestCompanyRecoveryKey(final String data, final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpBackgroundServiceEndpointCall());

        params.addParam("cmd", "requestCompanyRecoveryKey");
        params.addParam("data", data);

        callBackend(listener, params, true);
    }

    public void getConfigVersions(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getConfigVersions");

        callBackend(listener, params);
    }

    public void getConfiguration(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getConfiguration");

        callBackend(listener, params);
    }

    public void setNotificationSound(final String dataJson,
                                     final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setNotificationSound");
        params.addParam("data", dataJson);

        callBackend(listener, params);
    }

    public void setMessageState(final JsonArray guids,
                                final String state,
                                final OnBackendResponseListener listener,
                                final boolean useBackgroundEndpoint) {
        String url = useBackgroundEndpoint ? setUpBackgroundServiceEndpointCall() : setUpDefaultEndpointCall();

        final HttpPostParams params = new HttpPostParams(url);

        params.addParam("cmd", "setMessageState");
        params.addParam("guids", guids.toString());
        params.addParam("state", state);

        callBackend(listener, params, useBackgroundEndpoint);
    }

    public void setNotification(final boolean enabled,
                                final OnBackendResponseListener listener) {
        final String enabledString = enabled ? "1" : "0";

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setNotification");
        params.addParam("enabled", enabledString);

        callBackend(listener, params);
    }

    public void setGroupNotification(final boolean enabled,
                                     final OnBackendResponseListener listener) {
        final String enabledString = enabled ? "1" : "0";

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setGroupNotification");
        params.addParam("enabled", enabledString);

        callBackend(listener, params);
    }

    public void setServiceNotification(final boolean enabled,
                                       final OnBackendResponseListener listener) {
        final String enabledString = enabled ? "1" : "0";

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setServiceNotification");
        params.addParam("enabled", enabledString);

        callBackend(listener, params);
    }

    public void setServiceNotificationForService(final String guid,
                                                 final boolean enabled,
                                                 final OnBackendResponseListener listener) {
        final String enabledString = enabled ? "1" : "0";

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setServiceNotificationForService");
        params.addParam("guid", guid);
        params.addParam("enabled", enabledString);

        callBackend(listener, params);
    }

    public void setChannelNotification(final boolean enabled,
                                       final OnBackendResponseListener listener) {
        final String enabledString = enabled ? "1" : "0";

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setChannelNotification");
        params.addParam("enabled", enabledString);

        callBackend(listener, params);
    }

    public void setBlocked(final String guid,
                           final boolean isBlocked,
                           final OnBackendResponseListener listener) {
        final String blocked = (isBlocked) ? "1" : "0";

        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setBlocked");
        params.addParam("guid", guid);
        params.addParam("blocked", blocked);

        callBackend(listener, params);
    }

    public void getBlocked(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getBlocked");

        callBackend(listener, params);
    }

    public void registerPayment(final Purchase purchase,
                                final OnBackendResponseListener listener) {
        final String productId = purchase.getSku();
        final String transactionId = purchase.getToken();
        String receipt = Base64.encodeToString(purchase.getOriginalJson().getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);

        if (receipt != null) {
            final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

            params.addParam("cmd", "registerPurchase");
            params.addParam("receipt", receipt);
            params.addParam("productId", productId);
            params.addParam("transactionId", transactionId);

            callBackend(listener, params);
        }
    }

    public void registerVoucher(final String voucherCode,
                                final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "registerVoucher");
        params.addParam("ident", voucherCode);

        callBackend(listener, params);
    }

    public void resetPurchaseToken(final String purchaseToken,
                                   final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "resetPurchaseToken");
        params.addParam("token", purchaseToken);

        callBackend(listener, params);
    }

    public void getPurchasedProducts(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getPurchasedProducts");

        callBackend(listener, params);
    }

    public void getProducts(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getProducts");

        callBackend(listener, params);
    }

    public void getChannelList(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getChannels");

        callBackend(listener, params);
    }

    public void getChannelDetails(final String guid,
                                  final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getChannelDetails");
        params.addParam("guid", guid);

        callBackend(listener, params);
    }

    public void getChannelDetailsBatch(final String guids,
                                       final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getChannelDetailsBatch");
        params.addParam("guids", guids);

        callBackend(listener, params);
    }

    @Override
    public void getChannelCategories(OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getCategories");

        callBackend(listener, params);
    }

    public void getNewMessagesFromBackground(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpBackgroundServiceEndpointCall());

        params.addParam("cmd", "getNewPrio1Messages");
        params.addParam("prefetchedMessages", "");

        setUpWithBackgroundAccessTokenCredential();
        callBackend(listener, params, true);
    }

    public void getMessages(final String guids,
                            final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getMessages");
        params.addParam("guids", guids);

        callBackend(listener, params);
    }

    public void getPrioMessages(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getNewPrio1Messages");
        params.addParam("prefetchedMessages", "");

        setUpWithAccountCredential();
        callBackend(listener, params, false);
    }

    @Override
    public void setChannelNotificationForChannel(final String guid,
                                                 final boolean isEnabled,
                                                 final OnBackendResponseListener listener) {
        final String enabled = isEnabled ? "1" : "0";
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setChannelNotificationForChannel");
        params.addParam("guid", guid);
        params.addParam("enabled", enabled);

        callBackend(listener, params);
    }

    @Override
    public void setFollowedChannels(String followedChannelsJsonArray, OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setFollowedChannels");
        params.addParam("data", followedChannelsJsonArray);

        callBackend(listener, params);
    }

    @Override
    public void setFollowedServices(String followedServicesJsonArray, OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setFollowedServices");
        params.addParam("data", followedServicesJsonArray);

        callBackend(listener, params);
    }

    public void subscribeService(final String guid,
                                 final String filter,
                                 final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "subscribeService");
        params.addParam("guid", guid);
        params.addParam("filter", filter);

        callBackend(listener, params);
    }

    public void cancelServiceSubscription(final String guid,
                                          final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "cancelServiceSubscription");
        params.addParam("guid", guid);

        callBackend(listener, params);
    }

    public void subscribeChannel(final String guid,
                                 final String filter,
                                 final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "subscribeChannel");
        params.addParam("guid", guid);
        params.addParam("filter", filter);

        callBackend(listener, params);
    }

    public void cancelChannelSubscription(final String guid,
                                          final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "cancelChannelSubscription");
        params.addParam("guid", guid);

        callBackend(listener, params);
    }

    public void getChannelAsset(final String guid,
                                final String type,
                                final String res,
                                final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getChannelAsset");
        params.addParam("guid", guid);
        params.addParam("os", "aos");
        params.addParam("type", type);
        params.addParam("res", res);

        callBackend(listener, params);
    }

    @Override
    public void getBackgroundAccessToken(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "createBackgroundAccessToken");

        callBackend(listener, params);
    }

    public void trackEvents(final String trackingGuid,
                            final JSONArray trackingData,
                            final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDiagnosticEndpointCall());

        params.addParam("cmd", "trackEvents");
        params.addParam("guid", trackingGuid);
        params.addParam("data", trackingData.toString());

        callBackend(listener, params);
    }

    @Override
    public void setProfileInfo(final String nickname,
                               final String status,
                               final String image,
                               final String informAccountGuids,
                               final String oooStatus,
                               final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setProfileInfo");

        if (!StringUtil.isNullOrEmpty(nickname)) {
            params.addParam("nickname", nickname);
        }
        if (!StringUtil.isNullOrEmpty(status)) {
            params.addParam("status", status);
        }
        if (!StringUtil.isNullOrEmpty(image)) {
            params.addParam("image", image);
        }

        params.addParam("informAccountGuids", informAccountGuids);

        if (!StringUtil.isNullOrEmpty(oooStatus)) {
            params.addParam("oooStatus", oooStatus);
        }

        callBackend(listener, params);
    }

    public void setDeviceName(final String guid,
                              final String deviceNameEncoded,
                              final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setDeviceName");
        params.addParam("guid", guid);
        params.addParam("name", deviceNameEncoded);

        callBackend(listener, params);
    }

    public void createBackupPassToken(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "createBackupPasstoken");

        callBackend(listener, params);
    }

    public void hasCompanyManagement(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "hasCompanyManagement");

        callBackend(listener, params);
    }

    public void declineCompanyManagement(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "declineCompanyManagement");

        callBackend(listener, params);
    }

    public void acceptCompanyManagement(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "acceptCompanyManagement");

        callBackend(listener, params);
    }

    public void getCompanyMdmConfig(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getCompanyMdmConfig");

        callBackend(listener, params);
    }

    @Override
    public void registerTestVoucher(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "registerTestVoucher");

        callBackend(listener, params);
    }

    @Override
    public void getTestVoucherInfo(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getTestVoucherInfo");

        callBackend(listener, params);
    }

    @Override
    public void getDevices(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "listDevice");

        callBackend(listener, params);
    }

    @Override
    public void deleteDevice(final String guid, final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "deleteDevice");
        params.addParam("guid", guid);

        callBackend(listener, params);
    }

    public void listPrivateIndexEntries(@Nullable final String ifModifiedSince,
                                        final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "listPrivateIndexEntries");

        if (!StringUtil.isNullOrEmpty(ifModifiedSince)) {
            params.addParam("ifModifiedSince", ifModifiedSince);
        }

        callBackend(listener, params);
    }

    public void getPrivateIndexEntries(@NonNull final String guids,
                                       final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getPrivateIndexEntries");
        params.addParam("guids", guids);

        callBackend(listener, params);
    }

    public void insUpdPrivateIndexEntry(@NonNull final JsonObject entry, final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "insUpdPrivateIndexEntry");

        if (JsonUtil.hasKey(JsonConstants.GUID, entry)) {
            params.addParam(JsonConstants.GUID, JsonUtil.stringFromJO(JsonConstants.GUID, entry));
        }

        if (JsonUtil.hasKey(JsonConstants.DATA, entry)) {
            params.addParam(JsonConstants.DATA, JsonUtil.stringFromJO(JsonConstants.DATA, entry));
        }

        if (JsonUtil.hasKey(JsonConstants.KEY_DATA, entry)) {
            params.addParam(JsonConstants.KEY_DATA, JsonUtil.stringFromJO(JsonConstants.KEY_DATA, entry));
        }

        if (JsonUtil.hasKey(JsonConstants.KEY_IV, entry)) {
            params.addParam(JsonConstants.KEY_IV, JsonUtil.stringFromJO(JsonConstants.KEY_IV, entry));
        }

        if (JsonUtil.hasKey(JsonConstants.SIGNATURE, entry)) {
            params.addParam(JsonConstants.SIGNATURE, JsonUtil.stringFromJO(JsonConstants.SIGNATURE, entry));
        }

        callBackend(listener, params);
    }

    public void deletePrivateIndexEntries(final String guids,
                                          final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "deletePrivateIndexEntries");
        params.addParam("guids", guids);

        callBackend(listener, params);
    }

    public void requestEncryptionInfo(final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "requestEncryptionInfo");

        callBackend(listener, params);
    }

    @Override
    public void setSilentSingleChat(final String accountGuid,
                                    final String date,
                                    final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setSilentSingleChat");
        params.addParam("guid", accountGuid);
        params.addParam("till", date);

        callBackend(listener, params);
    }

    @Override
    public void setSilentGroupChat(final String roomGuid,
                                   final String date,
                                   final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setSilentGroupChat");
        params.addParam("guid", roomGuid);
        params.addParam("till", date);

        callBackend(listener, params);
    }

    @Override
    public void setIsWriting(final String guid,
                             final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setIsWriting");
        params.addParam("guid", guid);

        callBackend(listener, params);
    }

    @Override
    public void resetIsWriting(final String guid,
                               final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "resetIsWriting");
        params.addParam("guid", guid);

        callBackend(listener, params);
    }

    @Override
    public void getOnlineState(final String guid,
                               final String lastKnownState,
                               final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpLazyEndpointCall());

        params.addParam("cmd", "getOnlineState");
        params.addParam("guid", guid);
        params.addParam("lastKnownState", lastKnownState);

        callBackend(listener, params, null, true, false, null, -1);
    }

    @Override
    public void getOnlineStateBatch(final String guids,
                                    final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getOnlineStateBatch");
        params.addParam("guids", guids);
        params.addParam("oooProfil", "1");

        callBackend(listener, params);
    }

    @Override
    public void setPublicOnlineState(final boolean state,
                                     final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setPublicOnlineState");
        params.addParam("state", state ? "1" : "0");

        callBackend(listener, params);
    }

    private void callBackend(final OnBackendResponseListener listener,
                             final HttpPostParams params,
                             final boolean inBackgound) {
        callBackend(listener, params, null, false, inBackgound);
    }

    private void callBackend(final OnBackendResponseListener listener,
                             final HttpPostParams params) {
        callBackend(listener, params, null, false, false);
    }

    private void callBackend(final OnBackendResponseListener listener,
                             final HttpPostParams params,
                             HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener) {
        callBackend(listener, params, null, false, false, onConnectionDataUpdatedListener, -1);
    }

    private void callBackend(final OnBackendResponseListener listener,
                             final HttpPostParams params,
                             final String requestId) {
        callBackend(listener, params, requestId, false, false);
    }

    private void callBackend(final OnBackendResponseListener listener,
                             final HttpPostParams params,
                             final String requestId,
                             final boolean useLazyHttpTask,
                             final boolean inBackground) {
        callBackend(listener, params, requestId, useLazyHttpTask, inBackground, null, -1);
    }

    private void callBackend(final OnBackendResponseListener listener,
                             final HttpPostParams params,
                             final String aRequestId,
                             final boolean useLazyHttpTask,
                             final boolean inBackground,
                             HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener,
                             final int connectionTimeout) {
        if (!mCanStartThreads) {
            return;
        }
        final String requestId;
        if (aRequestId == null) {
            requestId = GuidUtil.generateRequestGuid();
        } else {
            requestId = aRequestId;
        }
        if (isConnected()) {
            final ConcurrentTaskListener taskListener = new ConcurrentTaskListener() {
                @Override
                public void onStateChanged(final ConcurrentTask task,
                                           final int state) {
                    BackendResponse wrappedResponse;

                    if (state == ConcurrentTask.STATE_COMPLETE) {
                        final String result = (String) task.getResults()[1];

                        wrappedResponse = wrapResponse(result);

                        if (listener != null) {
                            listener.onBackendResponse(wrappedResponse);
                        }
                    } else if (state == ConcurrentTask.STATE_ERROR) {
                        String errormessage;
                        String excIdentifier = null;
                        if (task.getLocalizedException() != null) {
                            excIdentifier = task.getLocalizedException().getIdentifier();

                            try {
                                final int resourceId = resources.getIdentifier("local_" + excIdentifier.replaceAll("-", "_"),
                                        "string", BuildConfig.APPLICATION_ID);

                                errormessage = application.getResources().getString(resourceId);
                            } catch (Exception e) {
                                LogUtil.e(TAG, e.getMessage(), e);
                                errormessage = application.getResources().getString(R.string.service_tryAgainLater);
                            }
                        } else {
                            if (inBackground && task.getResponseCode() == 403) {
                                application.getMessageController().setMessageDeviceToken(null);
                            }
                            if (task.getResponseCode() == 499) {
                                excIdentifier = LocalizedException.NO_ACCOUNT_ON_SERVER;

                                errormessage = application.getResources().getString(R.string.notification_account_was_deleted);
                            } else {
                                errormessage = application.getResources().getString(R.string.service_tryAgainLater);
                            }
                        }
                        wrappedResponse = wrapError(errormessage);

                        if (!StringUtil.isNullOrEmpty(excIdentifier)) {
                            wrappedResponse.msgException = new MsgExceptionModel();
                            wrappedResponse.msgException.setIdent(excIdentifier);
                            if (task.getLocalizedException() != null) {
                                wrappedResponse.msgException.setMessage(task.getLocalizedException().getMessage());
                            }
                        }

                        if (listener != null) {
                            listener.onBackendResponse(wrappedResponse);
                        }
                    }
                }
            };


            LogUtil.i(TAG, "Call backend via " + (isConnectedViaWLAN() ? "WLAN" : "mobile network") + " for command: " + getCmdParam(params));

            if (mUseAsycnConnections) {
                getTaskManagerController().getHttpTaskManager().executeHttpPostTask(keyStore, params, username, password,
                        requestId, taskListener, onConnectionDataUpdatedListener, connectionTimeout);
            } else {
                if (useLazyHttpTask) {
                    final int timeout = application.getPreferencesController()
                            .getLazyMsgServiceTimeout();
                    final ConcurrentTask task = new HttpLazyMessageTask(keyStore, params, username, password,
                            requestId, timeout);

                    task.addListener(taskListener);
                    task.run();
                } else {
                    final HttpSingleTask task = new HttpSingleTask(keyStore, params, username, password, requestId, onConnectionDataUpdatedListener);

                    if (connectionTimeout > -1) {
                        task.setConnectionTimeout(connectionTimeout);
                    }

                    task.addListener(taskListener);
                    task.run();
                }
            }
        } else {
            if (listener != null) {

                LogUtil.i(TAG, "Backend: No Internet Connection. Backend call skipped: " + getCmdParam(params));
                final BackendResponse response = wrapError(
                        application.getString(R.string.backendservice_internet_connectionFailed));

                listener.onBackendResponse(response);
            }
        }
    }

    private String getCmdParam(final HttpPostParams params) {
        final Map<String, String> pairs = params.getNameValuePairs();

        for (final Map.Entry<String, String> pair : pairs.entrySet()) {
            if (pair.getKey().equals("cmd")) {
                return pair.getValue();
            }
        }

        return "";
    }

    private BackendResponse wrapError(final String errorMessage) {
        final BackendResponse response = new BackendResponse();
        response.isError = true;
        response.errorMessage = errorMessage;
        return response;
    }

    private BackendResponse wrapResponse(final String response) {
        BackendResponse backendResponse = new BackendResponse();

        if (response == null) {
            LogUtil.w(TAG, "RESPONSE IS NULL");
            backendResponse.isError = false;
            return backendResponse;
        }

        File responseFile = null;
        final Reader responseReader;
        final JsonParser jsonParser = new JsonParser();
        final JsonElement jsonElement;

        try {
            if(response.startsWith("/")) {
                // KS: Backend response is stored in separate file
                // Check if we may do further processing in RAM because
                // the filesize is < FileUtil.MAX_RAM_PROCESSING_SIZE
                responseFile = new File(response);
                if(responseFile.length() > FileUtil.MAX_RAM_PROCESSING_SIZE) {
                    backendResponse.responseFilename = response;
                    backendResponse.isError = false;
                    backendResponse.jsonObject = null;
                    backendResponse.jsonArray = null;
                    // Keep responseFile!
                    return backendResponse;
                }

                // Small response file, contents can be processed in RAM
                responseReader = new FileReader(responseFile);
                jsonElement = jsonParser.parse(responseReader);
            } else {
                // Backend response is given as String
                jsonElement = jsonParser.parse(response);
            }

            JsonObject jsonObject = null;
            JsonArray jsonArray = null;

            if (jsonElement instanceof JsonObject) {
                jsonObject = (JsonObject) jsonElement;
            } else if (jsonElement instanceof JsonArray) {
                jsonArray = (JsonArray) jsonElement;
            }

            if (jsonObject != null) {
                if (!jsonObject.has("MsgException")) {
                    backendResponse.isError = false;
                    backendResponse.jsonObject = jsonObject;
                } else {
                    backendResponse.isError = true;
                    backendResponse.jsonObject = jsonObject;
                    backendResponse.msgException = gson.fromJson(jsonObject, MsgExceptionModel.class);
                    backendResponse.errorMessage = ApplicationError.msgExceptionToErrorString(resources,
                            backendResponse.msgException);
                }
            } else if (jsonArray != null) {
                backendResponse.isError = false;
                backendResponse.jsonArray = jsonArray;
            }
        } catch (JsonParseException e) {
            LogUtil.e(TAG, "wrapResponse(): Invalid json data: " + e.getMessage(), e);
            backendResponse = wrapError(resources.getString(R.string.service_tryAgainLater));
        } catch (FileNotFoundException e) {
            LogUtil.e(TAG, "wrapResponse(): Response file " + responseFile.getPath() + " not found!");
            backendResponse = wrapError(resources.getString(R.string.service_tryAgainLater));
        }

        FileUtil.deleteFile(responseFile);

        return backendResponse;
    }

    public boolean isConnected() {
        final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        final boolean isConnected = (networkInfo != null) && networkInfo.isConnectedOrConnecting();

        if (!isConnected) {
            final PackageManager packageManager = application.getPackageManager();
            final ComponentName receiverComponentName = new ComponentName(application, ConnectionBroadcastReceiver.class);

            final int enabled = packageManager.getComponentEnabledSetting(receiverComponentName);

            if (enabled != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                packageManager.setComponentEnabledSetting(receiverComponentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
            }
        }

        return isConnected;
    }

    public boolean isConnectedViaWLAN() {
        NetworkInfo wifi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifi != null && wifi.isConnected();
    }

    public void isMessageSend(final String senderId,
                              final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());
        params.addParam("cmd", "isMessageSend");
        params.addParam("senderId", senderId);
        callBackend(listener, params);
    }

    private String getEndPointUrl(final String endPoint) {
        return RuntimeConfig.getBaseUrl() + "/MessageService" + endPoint;
    }

    public void setUseAsyncConnections(final boolean useAsyncConnections) {
        this.mUseAsycnConnections = useAsyncConnections;
    }

    public void getTempDeviceInfo(final String accountGuid,
                                  final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "getTempDeviceInfo");
        params.addParam("accountGuid", accountGuid);

        callBackend(listener, params);
    }

    public void setTempDeviceInfo(final String keys,
                                  final String createdAt,
                                  final String nextUpdate,
                                  final String sig,
                                  final OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());

        params.addParam("cmd", "setTempDeviceInfo");
        params.addParam("keys", keys);
        params.addParam("createdAt", createdAt);
        params.addParam("nextUpdate", nextUpdate);
        params.addParam("sig", sig);

        callBackend(listener, params);
    }

    @Override
    public void getConfirmedIdentities(OnBackendResponseListener listener) {
        final HttpPostParams params = new HttpPostParams(setUpDefaultEndpointCall());
        params.addParam("cmd", "getConfirmedIdentities");
        callBackend(listener, params);
    }

    private TaskManagerController getTaskManagerController() {
        return ((SimsMeApplication) application.getApplicationContext()).getTaskManagerController();
    }
}
