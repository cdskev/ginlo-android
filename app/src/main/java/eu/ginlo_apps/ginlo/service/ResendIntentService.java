// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.service;

import android.app.IntentService;
import android.content.Intent;
import eu.ginlo_apps.ginlo.broadcastreceiver.ConnectionBroadcastReceiver;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.message.PrivateInternalMessageController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ResendIntentService
        extends IntentService {

    public ResendIntentService() {
        super("ResendIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            PrivateInternalMessageController privateInternalMessageController = ((SimsMeApplication) getApplication())
                    .getPrivateInternalMessageController();

            privateInternalMessageController.retrySend();
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        }

        ConnectionBroadcastReceiver.completeWakefulIntent(intent);
    }
}
