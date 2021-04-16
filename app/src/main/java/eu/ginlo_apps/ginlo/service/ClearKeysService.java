// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycle;
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycleImpl;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.log.LogUtil;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ClearKeysService
        extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent,
                              int flags,
                              int startId) {
        LogUtil.i("ClearKeysService", "Start ClearKeysService");

        KeyController keyController = ((SimsMeApplication) getApplication()).getKeyController();
        GinloAppLifecycle alController = ((SimsMeApplication) getApplication()).getAppLifecycleController();

        if (!alController.isAppInForeground()) {
            keyController.clearKeys();
        }

        stopSelf();
        LogUtil.i("ClearKeysService", "Stop ClearKeysService");

        return START_NOT_STICKY;
    }
}
