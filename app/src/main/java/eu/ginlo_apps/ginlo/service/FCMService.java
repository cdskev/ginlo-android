// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.service;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import java.util.Map;

public class FCMService extends FirebaseMessagingService {
    private final static String TAG = "FCMService";
    public final static String WAKELOCK_TAG = "ginlo:" + TAG;
    public final static int WAKELOCK_TIMEOUT = 15000;
    public final static int WAKELOCK_FLAGS = PowerManager.PARTIAL_WAKE_LOCK;

    @Override
    public void onNewToken(String s) {
        LogUtil.i(TAG, "New FCM Token: " + s);

        SimsMeApplication app = getSimsmeApplication();
        if (app != null) {
            app.getGcmController().setFcmToken(s);
        }
    }

    /*
    Firebase messaging service reports new incoming message.
    If message content is available and app is not in foreground build notification using GCMIntentService.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        LogUtil.d(TAG, "onMessageReceived: Firebase message received: " + remoteMessage.getData());

        if (remoteMessage.getData().size() < 1) {
            LogUtil.i(TAG, "onMessageReceived: nothing will be shown to the user. no push data");
            return;
        }

        SimsMeApplication app = getSimsmeApplication();
        if (app == null) {
            return;
        }

        // Acquire wakelock for WAKELOCK_TIMEOUT millis to stay awake for message processing.
        // Otherwise Android will send ginlo sleeping, thus not allowing for retrieval of the
        // new message from the backend and finally resulting in no notification for the user.
        PowerManager pm = (PowerManager)app.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(WAKELOCK_FLAGS, WAKELOCK_TAG);
        wl.acquire(WAKELOCK_TIMEOUT);


        boolean isAppLoggedInAnVisible = app.getAppLifecycleController().isAppInForeground() &&
                app.getLoginController().isLoggedIn() &&
                !NotificationController.isDeviceLocked(app);

        LogUtil.i(TAG, "onMessageReceived: isAppLoggedInAnVisible = " + isAppLoggedInAnVisible);

        if (isAppLoggedInAnVisible) {
            LogUtil.i(TAG, "onMessageReceived: No notification handling.");
            return;
        }

        Intent intent = new Intent(this, GCMIntentService.class);

        // Retrieve and set message extras such as "action", "messageGuid", "accountGuid", "loc-key", ...
        for (Map.Entry<String, String> entry : remoteMessage.getData().entrySet()) {
            intent.putExtra(entry.getKey(), entry.getValue());
        }

        // Check extra params if we have a real message action or only some administrative stuff
        // Only a new message action generates a notification to the user
        if (!GCMIntentService.haveToShowNotificationOrSetBadge(app, intent)) {
            LogUtil.i(TAG, "onMessageReceived: haveToShowNotificationOrSetBadge false");
            return;
        }

        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            LogUtil.i(TAG, "onMessageReceived: (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) true");

            if (GCMIntentService.haveToStartAsForegroundService(app, intent)) {
                LogUtil.i(TAG, "onMessageReceived: haveToStartAsForegroundService true");

                if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
                    LogUtil.i(TAG, "onMessageReceived: hasOreo true");
                    startForegroundService(intent);
                } else {
                    LogUtil.i(TAG, "onMessageReceived: hasOreo false");
                    startService(intent);
                }
            } else {
                LogUtil.i(TAG, "onMessageReceived: showNotification");
                GCMIntentService.showNotification(app, intent);
            }
        } else {
            LogUtil.i(TAG, "onMessageReceived: (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) false");
            startService(intent);
        }
    }

    private SimsMeApplication getSimsmeApplication() {
        Context context = getApplicationContext();
        return (context instanceof SimsMeApplication) ? ((SimsMeApplication) context) : null;
    }
}
