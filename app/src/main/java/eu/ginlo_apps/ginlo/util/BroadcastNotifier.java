// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;

public class BroadcastNotifier {

    private final LocalBroadcastManager mBroadcaster;
    private final String mBroadcastAction;

    /**
     * Creates a BroadcastNotifier containing an instance of LocalBroadcastManager.
     * LocalBroadcastManager is more efficient than BroadcastManager; because it only
     * broadcasts to components within the app, it doesn't have to do parceling and so forth.
     *
     * @param context a Context from which to get the LocalBroadcastManager
     */
    public BroadcastNotifier(Context context, final String broadcastAction) {

        // Gets an instance of the support library local broadcastmanager
        mBroadcaster = LocalBroadcastManager.getInstance(context);
        mBroadcastAction = broadcastAction;
    }

    /**
     * Uses LocalBroadcastManager to send an {@link Intent} containing {@code status}. The
     * {@link Intent} has the action {@code BROADCAST_ACTION} and the category {@code DEFAULT}.
     *
     * @param status      {@link Integer} denoting a work request status
     * @param extraString extra info like path
     * @param extraInt    extra info
     * @param ex          Exception, put as Serializable in Intent
     */
    public void broadcastIntentWithState(int status, String extraString, Integer extraInt, Exception ex) {
        Intent localIntent = new Intent();

        // The Intent contains the custom broadcast action for this app
        localIntent.setAction(mBroadcastAction);

        // Puts the status into the Intent
        localIntent.putExtra(AppConstants.INTENT_EXTENDED_DATA_STATUS, status);
        if (extraString != null) {
            localIntent.putExtra(AppConstants.INTENT_EXTENDED_DATA_EXTRA, extraString);
        } else if (extraInt != null) {
            localIntent.putExtra(AppConstants.INTENT_EXTENDED_DATA_EXTRA, extraInt);
        }

        if (ex != null) {
            localIntent.putExtra(AppConstants.INTENT_EXTENDED_DATA_EXCEPTION, ex);
        }

        localIntent.addCategory(Intent.CATEGORY_DEFAULT);

        // Broadcasts the Intent
        mBroadcaster.sendBroadcast(localIntent);
    }

//   /**
//    * Uses LocalBroadcastManager to send an {@link String} containing a logcat message.
//    * {@link Intent} has the action {@code BROADCAST_ACTION} and the category {@code DEFAULT}.
//    *
//    * @param logData a {@link String} to insert into the log.
//    */
//   public void notifyProgress(String logData) {
//
//      Intent localIntent = new Intent();
//
//      // The Intent contains the custom broadcast action for this app
//      localIntent.setAction(Constants.BROADCAST_ACTION);
//
//      localIntent.putExtra(Constants.INTENT_EXTENDED_DATA_STATUS, -1);
//
//      // Puts log data into the Intent
//      localIntent.putExtra(Constants.EXTENDED_STATUS_LOG, logData);
//      localIntent.addCategory(Intent.CATEGORY_DEFAULT);
//
//      // Broadcasts the Intent
//      mBroadcaster.sendBroadcast(localIntent);
//
//   }
}
