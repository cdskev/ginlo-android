// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.service;

import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.log.LogUtil;

public class GinloOngoingService extends Service {
    private static final String TAG = GinloOngoingService.class.getSimpleName();
    private static final NotificationController notificationController = SimsMeApplication.getInstance().getNotificationController();

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
        notificationController.dismissOngoingNotification();

        Intent intent = new Intent(context, GinloOngoingService.class);
        context.stopService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notification notification = notificationController.buildOngoingServiceNotification(this.getApplicationContext().getString(R.string.notification_gos_running));
        if (notification != null && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
                startForeground(NotificationController.INFO_NOTIFICATION_ID, notification);
        }
        LogUtil.i(TAG, "onCreate: OngoingServiceNotification generated");
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
        if (Actions.START.equals(action)) {
            Notification notification = notificationController.buildOngoingServiceNotification(this.getApplicationContext().getString(R.string.notification_gos_running));
            if (notification != null && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
                startForeground(NotificationController.INFO_NOTIFICATION_ID, notification);
            }
            LogUtil.i(TAG, "GinloOngoingService started.");
        } else if (Actions.STOP.equals(action)) {
            notificationController.dismissOngoingNotification();
            LogUtil.i(TAG, "onStartCommand: Stop requested");
            stopSelf();
        } else {
            LogUtil.w(TAG, "onStartCommand: Unknown action received: " + action);
            stopSelf();
        }

        return START_NOT_STICKY;
    }
}