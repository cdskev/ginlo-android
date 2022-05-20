// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.service;

import android.app.Activity;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import eu.ginlo_apps.ginlo.LoginActivity;
import eu.ginlo_apps.ginlo.MainActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.chat.BaseChatActivity;
import eu.ginlo_apps.ginlo.activity.register.IdentConfirmActivity;
import eu.ginlo_apps.ginlo.context.GinloLifecycleObserver;
import eu.ginlo_apps.ginlo.context.GinloLifecycleObserverImpl;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycle;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;

public class GinloOngoingService extends Service {
    private static final String TAG = "GinloOngoingService";
    private static final NotificationController notificationController = SimsMeApplication.getInstance().getNotificationController();
    private static final PreferencesController preferencesController = SimsMeApplication.getInstance().getPreferencesController();
    private static final GinloAppLifecycle appLifecycle = SimsMeApplication.getInstance().getAppLifecycleController();

    private final static String WAKELOCK_TAG = "ginlo:" + TAG;
    private final static int WAKELOCK_TIMEOUT = 30000;
    private final static int WAKELOCK_FLAGS = PowerManager.PARTIAL_WAKE_LOCK;

    static final class Actions {
        static final String START = TAG + ":START";
        static final String STOP = TAG + ":STOP";
    }

    public static void launch(Context context) {
        Intent intent = new Intent(context, GinloOngoingService.class);

        if (notificationController == null) {
            LogUtil.w(TAG, "Not started - no NotificationController.");
            return;
        }

        intent.setAction(Actions.START);

        ComponentName componentName;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            componentName = context.startForegroundService(intent);
            LogUtil.i(TAG, "launch: start as foreground service.");
        } else {
            componentName = context.startService(intent);
            LogUtil.i(TAG, "launch: start as service.");
        }
        if (componentName == null) {
            LogUtil.w(TAG, "launch: not started.");
        }

    }

    public static void abort(Context context) {
        notificationController.dismissNotification(NotificationController.GOS_NOTIFICATION_ID);
        Intent intent = new Intent(context, GinloOngoingService.class);
        context.stopService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notification notification = notificationController.buildOngoingServiceNotification(this.getApplicationContext().getString(R.string.notification_gos_running));
        if (notification != null && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
                startForeground(NotificationController.GOS_NOTIFICATION_ID, notification);
        }
        LogUtil.d(TAG, "onCreate: OngoingServiceNotification generated");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent.getAction();

        if (notificationController == null) {
            LogUtil.w(TAG, "onStartCommand: Couldn't start GinloOngoingService, notificationController is null.");
            stopSelf();
            return START_NOT_STICKY;
        }

        if(preferencesController == null) {
            LogUtil.w(TAG, "onStartCommand: Couldn't start GinloOngoingService, preferencesController is null.");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (Actions.START.equals(action)) {
            // Only start if we need background polling
            if(preferencesController.getPollingEnabled()) {
                Notification notification = notificationController.buildOngoingServiceNotification(this.getApplicationContext().getString(R.string.notification_gos_running));
                if (notification != null && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
                    startForeground(NotificationController.GOS_NOTIFICATION_ID, notification);
                }
                LogUtil.i(TAG, "GinloOngoingService started.");
            } else {
                LogUtil.i(TAG, "GinloOngoingService halted. Background polling not enabled.");
                stopSelf();
            }
        } else if (Actions.STOP.equals(action)) {
            notificationController.dismissNotification(NotificationController.GOS_NOTIFICATION_ID);
            LogUtil.i(TAG, "onStartCommand: Stop requested");
            stopSelf();
        } else {
            LogUtil.w(TAG, "onStartCommand: Unknown action received: " + action);
            stopSelf();
        }

        return START_NOT_STICKY;
    }
}