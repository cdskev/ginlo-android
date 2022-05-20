// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.service;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.log.LogUtil;

import java.util.HashMap;
import java.util.Map;

public class FCMService extends FirebaseMessagingService {
    private final static String TAG = "FCMService";
    private final static String WAKELOCK_TAG = "ginlo:" + TAG;
    private final static int WAKELOCK_TIMEOUT = 15000; // 15 seconds
    private final static int WAKELOCK_FLAGS = PowerManager.PARTIAL_WAKE_LOCK;

    @Override
    public void onNewToken(String s) {
        LogUtil.i(TAG, "onNewToken: New FCM Token: " + s);

        SimsMeApplication app = getSimsmeApplication();
        if (app != null) {
            app.getGcmController().setFcmToken(s);
        }
    }

    /**
     * Firebase messaging service reports new incoming message.
     * If message content is available and app is not in foreground
     * build notification using NotificationIntentService.
     * @param remoteMessage
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        LogUtil.d(TAG, "onMessageReceived: Firebase message received: " + remoteMessage.getData());

        if (remoteMessage.getData().size() < 1) {
            LogUtil.d(TAG, "onMessageReceived: Nothing will be shown to the user. No push data");
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

        if(app.getAppLifecycleController().isAppInForeground()) {
            // Message is (already) going to be retrieved and handled by running GetMessageTask.
            LogUtil.d(TAG, "onMessageReceived: Done here. Message processing is to be done by running GetMessagesTask.");
            wl.release();
            return;
        }

        // Retrieve and set message extras such as "action", "messageGuid", "accountGuid", "loc-key", ...
        Map<String, String> extras = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : remoteMessage.getData().entrySet()) {
            extras.put(entry.getKey(), entry.getValue());
        }

        NotificationIntentService.postProcessFcmNotification(app, extras);
        // Keep WAKELOCK until WAKELOCK_TIMEOUT
        //wl.release();
    }

    private SimsMeApplication getSimsmeApplication() {
        Context context = getApplicationContext();
        return (context instanceof SimsMeApplication) ? ((SimsMeApplication) context) : null;
    }
}
