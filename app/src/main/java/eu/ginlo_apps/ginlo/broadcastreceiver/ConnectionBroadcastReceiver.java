// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.broadcastreceiver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import androidx.legacy.content.WakefulBroadcastReceiver;
import eu.ginlo_apps.ginlo.service.ResendIntentService;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ConnectionBroadcastReceiver
        extends WakefulBroadcastReceiver {

    public ConnectionBroadcastReceiver() {
    }

    @Override
    public void onReceive(Context context,
                          Intent intent) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return;
        }
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        if ((networkInfo != null) && networkInfo.isConnected()) {
            ComponentName comp = new ComponentName(context.getPackageName(), ResendIntentService.class.getName());

            startWakefulService(context, (intent.setComponent(comp)));

            PackageManager packageManager = context.getPackageManager();
            ComponentName receiverComponentName = new ComponentName(context, ConnectionBroadcastReceiver.class);

            packageManager.setComponentEnabledSetting(receiverComponentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }
}
