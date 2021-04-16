// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.broadcastreceiver;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import androidx.legacy.content.WakefulBroadcastReceiver;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.service.GCMIntentService;
import eu.ginlo_apps.ginlo.util.SystemUtil;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class GCMBroadcastReceiver
        extends WakefulBroadcastReceiver {

    public GCMBroadcastReceiver() {
    }

    @Override
    public void onReceive(Context context,
                          Intent intent) {
        ComponentName comp = new ComponentName(context.getPackageName(),
                GCMIntentService.class.getName());

        Context appContext = context.getApplicationContext();
        SimsMeApplication application = null;

        if (appContext instanceof SimsMeApplication) {
            application = (SimsMeApplication) appContext;
        }

        if (application != null) {
            boolean isAppLoggedInAnVisible = application.getAppLifecycleController().isAppInForeground() && application.getLoginController().isLoggedIn();

            if (!isAppLoggedInAnVisible) {
                if (GCMIntentService.haveToShowNotificationOrSetBadge(application, intent)) {
                    if (SystemUtil.hasMarshmallow()) {
                        if (GCMIntentService.haveToStartAsForegroundService(application, intent)) {
                            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                            boolean isIdleMode = (pm == null) || pm.isDeviceIdleMode();

                            if (SystemUtil.hasOreo() && isIdleMode) {
                                context.startForegroundService((intent.setComponent(comp)));
                            } else {
                                context.startService((intent.setComponent(comp)));
                            }
                        } else {
                            GCMIntentService.showNotification(application, intent);
                        }
                    } else {
                        startWakefulService(context, (intent.setComponent(comp)));
                    }
                }
            }
        }

        if (isOrderedBroadcast()) {
            setResultCode(Activity.RESULT_OK);
        }
    }
}
