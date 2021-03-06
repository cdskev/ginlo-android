// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.broadcastreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.service.GinloOngoingService;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = BootCompletedReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent arg1) {

        if(BuildConfig.USE_BOOT_COMPLETED_RECEIVER) {
            LogUtil.i(TAG, "onReceive: Got a boot completed broadcast. Try to start ginlo services ...");
            LogUtil.i(TAG, "onReceive: Intent action received: " + arg1.getAction());

            if (BuildConfig.HAVE_GINLO_ONGOING_SERVICE) {
                GinloOngoingService.launch(context);
            }
        } else {
            LogUtil.i(TAG, "onReceive: Got a boot completed broadcast but BuildConfig says not to do anything.");
        }
    }
}
