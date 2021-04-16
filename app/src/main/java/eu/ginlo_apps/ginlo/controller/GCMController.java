// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import androidx.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.Locale;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.DeviceModel;
import eu.ginlo_apps.ginlo.model.backend.ResponseModel;
import eu.ginlo_apps.ginlo.model.backend.serialization.DeviceModelSerializer;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import io.sentry.Sentry;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class GCMController {
    public final static String TAG = GCMController.class.getSimpleName();
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private static final String PROPERTY_APP_VERSION = "appVersion";

    //private static final String SENDER_ID                        = "26355693843";

    private static final String PROPERTY_FCM_TOKEN = "fcm_token";

    private static final String PROPERTY_FCM_TOKEN_REG_AT_SERVER = "fcm_token_server_flag";

    private final SimsMeApplication context;

    private Dialog dialog;

    /**
     * GCMController
     *
     * @param application SIMSme Application
     */
    public GCMController(final SimsMeApplication application) {
        this.context = application;
    }

    public void registerForGCM(@NonNull final GenericActionListener<Void> listener) {
        if (!BackendService.isConnected(context)) {
            listener
                .onFail(context.getString(R.string.backendservice_internet_connectionFailed), "");
            return;
        }

        if (!haveToStartRegistrationTask()) {
            listener.onSuccess(null);
            return;
        }

        // KS: AppConstants muss be initialized before executing registration (Issue #64)!
        AppConstants.gatherData(context);
        LogUtil.i(TAG, "registerForGCM: Gathered app data found version: " + AppConstants.getAppVersionCode());

        final SharedPreferences prefs = getGCMPreferences(context);
        String token = prefs.getString(PROPERTY_FCM_TOKEN, null);

        if (!StringUtil.isNullOrEmpty(token)) {
            startRegistrationTask(token, listener);
            return;
        }

        FirebaseInstanceId.getInstance().getInstanceId()
            .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                @Override
                public void onComplete(@NonNull Task<InstanceIdResult> task) {
                    try {
                        if (!task.isSuccessful()) {
                            if (task.getException() != null) {
                                Exception e = task.getException();
                                LogUtil.e(TAG, e.getMessage(), e);
                            }
                            listener.onFail(
                                context.getString(R.string.gcm_notifcation_registration_failed),
                                LocalizedException.NO_FCM_TOKEN
                            );
                            return;
                        }

                        String token = task.getResult().getToken();
                        if (StringUtil.isNullOrEmpty(token)) {
                            if (task.getException() != null) {
                                Exception e = task.getException();
                                LogUtil.e(TAG, e.getMessage(), e);
                            }
                            listener.onFail(
                                context.getString(R.string.gcm_notifcation_registration_failed),
                                LocalizedException.NO_FCM_TOKEN
                            );
                            return;
                        }

                        prefs.edit().putString(PROPERTY_FCM_TOKEN, token).apply();

                        startRegistrationTask(token, listener);
                    } catch (Exception e) {
                        LogUtil.e(TAG, e.getMessage(), e);
                        listener.onFail(
                            context.getString(R.string.gcm_notifcation_registration_failed),
                            LocalizedException.NO_FCM_TOKEN
                        );
                    }
                }
            });
    }

    private void startRegistrationTask(
        @NonNull final String fcmToken,
        @NonNull final GenericActionListener<Void> listener
    ) {
        new RegistrationTask(context, fcmToken, new GenericActionListener<Void>() {
            @Override
            public void onSuccess(Void object) {
                storeRegistrationId();
                listener.onSuccess(null);
            }

            @Override
            public void onFail(String message, String errorIdent) {
                LogUtil.w(TAG, "RegistrationTask: Could not register token for app " + AppConstants.getAppVersionCode());
                listener.onFail(message, errorIdent);
            }
        }).executeOnExecutor(RegistrationTask.THREAD_POOL_EXECUTOR);
    }

    private boolean haveToStartRegistrationTask() {
        SharedPreferences prefs = getGCMPreferences(context);

        if (!prefs.getBoolean(PROPERTY_FCM_TOKEN_REG_AT_SERVER, false)) {
            return true;
        }

        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = AppConstants.getAppVersionCode();

        if (registeredVersion != currentVersion) {
            LogUtil.i(TAG, "App version changed: " + registeredVersion + " -> " + currentVersion);
            return true;
        }

        return false;
    }

    private void storeRegistrationId() {
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = AppConstants.getAppVersionCode();

        if (appVersion == 0) {
            LogUtil.e(TAG, "Trying to save token for unset app version (0) - stop this!");
            return;
        }

        LogUtil.i(TAG, "Saving token and app version " + appVersion);

        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean(PROPERTY_FCM_TOKEN_REG_AT_SERVER, true);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.apply();
    }

    public boolean checkPlayServices(Activity activity) {
        int resultCode =
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity);

        if (resultCode != ConnectionResult.SUCCESS) {
            if (GoogleApiAvailability.getInstance().isUserResolvableError(resultCode)) {
                dialog = GoogleApiAvailability.getInstance()
                    .getErrorDialog(activity, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST);
                dialog.show();
            } else {
                LogUtil.i(TAG, "This device is not supported.");
                activity.finish();
            }
            return false;
        }
        return true;
    }

    private SharedPreferences getGCMPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences,
        // but
        // how you store the regID in your app is up to you.
        return context.getSharedPreferences(TAG, Context.MODE_PRIVATE);
    }

    public Dialog getDialog() {
        return dialog;
    }

    public void clearGCMValues() {
        final SharedPreferences prefs = getGCMPreferences(context);

        SharedPreferences.Editor editor = prefs.edit();

        editor.clear();
        editor.apply();

        new UnregisterFcm().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, context);
    }

    public void setFcmToken(@NonNull final String token) {
        SharedPreferences prefs = getGCMPreferences(context);

        String oldToken = prefs.getString(PROPERTY_FCM_TOKEN, "");

        if (StringUtil.isNullOrEmpty(oldToken) || !StringUtil.isEqual(token, oldToken)) {
            prefs.edit().putString(PROPERTY_FCM_TOKEN, token)
                .putBoolean(PROPERTY_FCM_TOKEN_REG_AT_SERVER, false).apply();
        }
    }

    private static class UnregisterFcm
        extends AsyncTask<SimsMeApplication, Void, Void>
    {

        /**
         * Override this method to perform a computation on a background thread. The
         * specified parameters are the parameters passed to {@link #execute}
         * by the caller of this task.
         * <p>
         * This method can call {@link #publishProgress} to publish updates
         * on the UI thread.
         *
         * @param contexts The parameters of the task.
         * @return A result, defined by the subclass of this task.
         * @see #onPreExecute()
         * @see #onPostExecute
         * @see #publishProgress
         */
        @Override
        protected Void doInBackground(SimsMeApplication... contexts) {
            try {
                FirebaseInstanceId.getInstance().deleteInstanceId();
            } catch (IOException e) {
                LogUtil.e(TAG, e.getMessage(), e);
            }

            return null;
        }
    }

    /**
     * @author Florian
     * @version $Revision$, $Date$, $Author$
     */
    private static class RegistrationTask
        extends AsyncTask<Void, Void, ResponseModel>
    {

        private final SimsMeApplication application;
        private final GenericActionListener<Void> listener;
        private String fcmToken;

        RegistrationTask(
            @NonNull final SimsMeApplication context,
            @NonNull final String fcmToken,
            @NonNull final GenericActionListener<Void> listener
        ) {
            application = context;
            this.fcmToken = fcmToken;
            this.listener = listener;
        }

        @Override
        protected ResponseModel doInBackground(Void... params) {
            final ResponseModel rm = new ResponseModel();

            final GsonBuilder gsonBuilder = new GsonBuilder();

            gsonBuilder.registerTypeAdapter(DeviceModel.class, new DeviceModelSerializer());
            Gson gson = gsonBuilder.create();

            DeviceModel device = new DeviceModel();

            device.language = Locale.getDefault().getLanguage();
            device.apnIdentifier = RuntimeConfig.getGcmPrefix() + ":" + fcmToken;
            device.appName = AppConstants.getAppName();
            device.appVersion = AppConstants.getAppVersionName();
            device.os = AppConstants.OS;
            device.featureVersion = AppConstants.getAppFeatureVersions();

            IBackendService.OnBackendResponseListener listener =
                new IBackendService.OnBackendResponseListener() {
                    @Override
                    public void onBackendResponse(BackendResponse response) {
                        if (response.isError) {
                            rm.setError(response);
                            logSentry(response);
                        }
                    }
                };

            String dataJson = gson.toJson(device);

            BackendService.withSyncConnection(application).setDeviceData(dataJson, listener);
            return rm;
        }

        private void logSentry(BackendResponse response) {
            String msg = String.format("[%s] Error @sendRegistrationIdToBackend: %s  ", Thread.currentThread().getName(), response.errorMessage);
            LogUtil.i(
                TAG, msg);
            Exception ex = new Exception(msg);
            Sentry.capture(ex);
        }

        @Override
        protected void onPostExecute(ResponseModel rm) {
            if (rm != null) //Error :SERVICE_NOT_AVAILABLE
            {
                if (rm.isError) {
                    listener.onFail(rm.errorMsg, rm.errorIdent);
                } else {
                    listener.onSuccess(null);
                }
            }
        }
    }
}
