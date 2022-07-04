// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.GreenDAOSecurityLayer;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.MimeUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import me.leolin.shortcutbadger.ShortcutBadgeException;
import me.leolin.shortcutbadger.ShortcutBadger;

public class NotificationIntentService
        extends IntentService {
    private static final String TAG = "NotificationIntentService";

    private final static String WAKELOCK_TAG = "ginlo:" + TAG;
    private final static int WAKELOCK_TIMEOUT = 30000;
    private final static int WAKELOCK_FLAGS = PowerManager.PARTIAL_WAKE_LOCK;

    private static final int FOREGROUND_ID = 1981;

    public static final String ACTION_NEW_MESSAGES = "simsme://getNewMessages";
    public static final String LOC_KEY = "loc-key";
    public static final String LOC_KEY_APP_GINLO_CONTROL = "push_newAGC";
    public static final String LOC_KEY_PRIVATE_MSG = "push_newPN";
    public static final String LOC_KEY_PRIVATE_MSG_EX = "push_newPNex";
    public static final String LOC_KEY_PRIVATE_MSG_AVC = "push_newAVC";
    public static final String LOC_KEY_GROUP_INV = "push_groupInv";
    public static final String LOC_KEY_GROUP_INV_EX = "push_groupInvEx";
    public static final String LOC_KEY_GROUP_INV_MAN = "push_managedRoomInv";
    public static final String LOC_KEY_GROUP_INV_MAN_EX = "push_managedRoomInvEx";
    public static final String LOC_KEY_GROUP_INV_RES = "push_restrictedRoomInv";
    public static final String LOC_KEY_GROUP_INV_RES_EX = "push_restrictedRoomInvEx";
    public static final String LOC_KEY_PRIVATE_MSG_EX_HIGH = "push_newPNexHigh";
    public static final String LOC_KEY_CHANNEL_MSG = "push_newCN";

    private static final String SENDER_GUID = "senderGuid";
    private static int numNewMessages;

    public NotificationIntentService() {
        super("NotificationIntentService");
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        LogUtil.d(TAG, "onHandleIntent: Started.");
        if (intent == null) {
            return;
        }

        final Bundle extras = checkIntent(intent);
        if (extras == null) {
            return;
        }

        SimsMeApplication application = (SimsMeApplication) getApplication();

        // Acquire wakelock for WAKELOCK_TIMEOUT millis to stay awake for message processing.
        // Otherwise Android will send ginlo sleeping, thus not allowing for retrieval of the
        // new message from the backend and finally resulting in no notification for the user.
        PowerManager pm = (PowerManager)application.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(WAKELOCK_FLAGS, WAKELOCK_TAG);
        wl.acquire(WAKELOCK_TIMEOUT);

        boolean wasStartAsForeground = false;
        if (haveToStartAsForegroundService(application, intent) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
            wasStartAsForeground = true;
            startService();
        }

        showNotificationFromExtras(application, extras);

        finishService(wasStartAsForeground, intent);
        wl.release();
    }

    private void startService() {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            this.startForeground(FOREGROUND_ID, NotificationController.getLoadMessageNotification(this));
        }
    }

    private void finishService(boolean hasStartForeground, Intent intent) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            if (hasStartForeground) {
                this.stopForeground(true);
            }
        }
    }

    public static void postProcessFcmNotification(@NonNull final SimsMeApplication application, @NonNull final Map<String, String> notificationExtras) {
        final String messageGuid = notificationExtras.get("messageGuid");
        final String accountGuid = notificationExtras.get("accountGuid");
        final MessageController messageController = application.getMessageController();

        LogUtil.i(TAG, "postProcessFcmNotification: FCM received " + messageGuid + " for " + accountGuid);

        if(messageController == null) {
            LogUtil.e(TAG, "postProcessFcmNotification: Fatal! No MessageController!");
            return;
        }

        if (application.getAccountController().getAccount() == null
                || application.getAccountController().getAccount().getState() != Account.ACCOUNT_STATE_FULL) {
            LogUtil.e(TAG, "postProcessFcmNotification: Fatal! No valid local account!");
            return;
        }

        // If no action is set, no action takes place ;)
        final String action = notificationExtras.get("action");
        if(action == null) {
            LogUtil.w(TAG, "postProcessFcmNotification: No Action!");
            return;
        }

        if (!StringUtil.isNullOrEmpty(messageGuid)) {
            final Message messageByGuid = messageController.getMessageByGuid(messageGuid);
            if (messageByGuid == null) {
                // Message not yet in database - initiate downloading.
                LogUtil.i(TAG, "postProcessFcmNotification: Message unknown - calling backend ...");

                // Only prepare key if notification preview is enabled.
                if(application.getPreferencesController() != null && application.getPreferencesController().getNotificationPreviewEnabled()) {
                    if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
                        try {
                            // Prepare key for message decryption
                            final KeyController keyController = application.getKeyController();
                            if(keyController == null) {
                                LogUtil.e(TAG, "postProcessFcmNotification: Fatal! No KeyController!");
                                return;
                            }

                            if (!keyController.getInternalKeyReady()) {
                                keyController.loadInternalEncryptionKeyForNotificationPreview();
                                GreenDAOSecurityLayer.init(keyController);
                                keyController.reloadUserKeypairFromAccount();
                            }
                        } catch (final LocalizedException | IOException e) {
                            LogUtil.e(TAG, "postProcessFcmNotification: Notification preview enabled, but failed to initialize keys: " + e.getMessage());
                            return;
                        }
                    }
                }

                final ConcurrentTaskListener concurrentTaskListener = new ConcurrentTaskListener() {
                    @Override
                    public void onStateChanged(ConcurrentTask task, int state) {
                        if (state == ConcurrentTask.STATE_COMPLETE) {
                            LogUtil.i(TAG, "postProcessFcmNotification: Processing for " + messageGuid + " done.");

                        } else if (state == ConcurrentTask.STATE_ERROR) {
                            LogUtil.w(TAG, "postProcessFcmNotification: Could not retrieve message " + messageGuid + "! ConcurrentTask.STATE_ERROR.");
                        }
                    }
                };
                // Retrieve message and keep notification info.
                messageController.startMessageTaskSyncFromAppBackground(concurrentTaskListener, notificationExtras);
            } else {
                // Do nothing. Since message is in the database, notification should have been triggered by GetMessageTask.
                LogUtil.i(TAG, "postProcessFcmNotification: Message has been/is being processed - nothing to do here.");
            }
        }
    }

    private static Bundle checkIntent(@NonNull Intent intent) {
        final Bundle extras = intent.getExtras();
        return ((extras != null) && !extras.isEmpty()) ? extras : null;
    }

    public static boolean haveToStartAsForegroundService(@NonNull final SimsMeApplication application, @NonNull final Intent intent) {
        final Bundle extras = checkIntent(intent);
        if (extras == null) {
            return false;
        }

        String locKey = extras.getString(LOC_KEY, LOC_KEY_PRIVATE_MSG).replace(".", "_");
        if (StringUtil.isEqual(locKey, "push_only")) {
            return false;
        }

        final String messageGuid = extras.getString("messageGuid");
        //Keine Message Guid oder es ist eine Channel Msg
        return !StringUtil.isNullOrEmpty(messageGuid) && !messageGuid.startsWith(AppConstants.GUID_MSG_CHANNEL_PREFIX)
                //kein Token zum Laden der Message
                && application.getMessageController().getMessageDeviceToken() != null
                //Nutzer moechte nicht Nachrihcten im Hintergrund laden
                && application.getPreferencesController().getFetchInBackground();
    }

    // Main notification entrance for FCM/GCM services/receiver
    public static void showNotification(@NonNull final SimsMeApplication application, @NonNull Intent intent) {
        final Bundle extras = intent.getExtras();
        if (extras != null) {
            showNotificationFromExtras(application, extras);
        }
    }

    private static void showNotificationFromExtras(@NonNull final SimsMeApplication application, @NonNull final Bundle extras) {
        String locKey = extras.getString(LOC_KEY, LOC_KEY_PRIVATE_MSG).replace(".", "_");
        final String badge = extras.getString("badge");
        String forceTitle = extras.getString("body");
        final String sound = extras.getString("sound");
        String senderGuid = extras.getString(SENDER_GUID);
        final String messageGuid = extras.getString("messageGuid");

        final MessageController messageController = application.getMessageController();
        final PreferencesController preferenceController = application.getPreferencesController();
        final NotificationController notificationController = application.getNotificationController();

        if (badge != null) {
            try {
                numNewMessages += Integer.parseInt(badge);
            } catch (Exception ignored) {
            }
        }

        LogUtil.i(TAG, "showNotificationFromExtras: numNewMessages " + numNewMessages);

        // Bug 41339 - fuer Gruppeneinladungen werden keine Benachrichtigungen außerhalb der App angezeigt
        if (numNewMessages == 0 &&
                (StringUtil.isEqual(locKey, LOC_KEY_GROUP_INV)
                        || StringUtil.isEqual(locKey, LOC_KEY_GROUP_INV_EX)
                        || StringUtil.isEqual(locKey, LOC_KEY_GROUP_INV_MAN)
                        || StringUtil.isEqual(locKey, LOC_KEY_GROUP_INV_MAN_EX)
                        || StringUtil.isEqual(locKey, LOC_KEY_GROUP_INV_RES)
                        || StringUtil.isEqual(locKey, LOC_KEY_GROUP_INV_RES_EX)
                )
        ) {
            numNewMessages = 1;
            /*Senderguid wird nur benoetigt, um den richtigen intent zu waehlen
             * damit man nicht in den SInglechat des Gruppenerstellers umgeleitet wird -> senderGuid = null
             */
            senderGuid = null;
        }

        final boolean highPrio = StringUtil.isEqual(locKey, LOC_KEY_PRIVATE_MSG_EX_HIGH);

        int resId = application.getResources().getIdentifier(locKey, "string", application.getPackageName());

        if (resId != 0) {
            //FIXME nicht optimal: lockey wieder zuruecksetzen, damit die anschliessende Mechanik funktioniert
            locKey = LOC_KEY_PRIVATE_MSG;
            final String text = application.getString(resId);

            final JsonParser parser = new JsonParser();
            final String locArgs = extras.getString("loc-args");
            if (!StringUtil.isNullOrEmpty(locArgs)) {
                final JsonElement json = parser.parse(locArgs);
                if (json.isJsonArray()) {
                    final JsonArray array = json.getAsJsonArray();
                    if (array.size() > 0) {
                        for (int i = 0; i < array.size(); ++i) {
                            final String nickName = array.get(i).getAsString();
                            String placeholder = "%" + (i + 1) + "$s";
                            forceTitle = text.replace(placeholder, nickName);
                        }
                    }
                }
            } else {
                forceTitle = text;
            }
        }

        LogUtil.d(TAG, "showNotificationFromExtras: " + extras.toString());

        boolean playSound = true;

        if (sound == null) {
            // alte Config checken
            if (!preferenceController.getSoundForSingleChatEnabled()
                    && !preferenceController.getSoundForGroupChatEnabled()) {
                // es wurden beide settings auf false in der Version < 1.3 gesetzt
                playSound = false;
            }
        } else if (sound.equalsIgnoreCase(PreferencesController.NOTIFICATION_SOUND_NO_SOUND)) {
            // neue Config
            playSound = false;
        }

        // Set badge number
        if (numNewMessages > 0) {
            try {
                ShortcutBadger.applyCountOrThrow(application, numNewMessages);
            } catch (ShortcutBadgeException e) {
                LogUtil.w(TAG, "showNotificationFromExtras: ShortcutBadger.applyCountOrThrow: " + e.getMessage());
            }
        }

        //keine Anzeige ein Notification
        if (StringUtil.isEqual(locKey, "push_only")) {
            LogUtil.i(TAG, "showNotificationFromExtras: loc-key is push_only. No notification will be shown to the user.");
            return;
        }

        //Keine Message Guid oder es ist eine Channel Msg
        if (StringUtil.isNullOrEmpty(messageGuid) || messageGuid.startsWith(AppConstants.GUID_MSG_CHANNEL_PREFIX)
                //kein Token zum Laden der Message
                || messageController.getMessageDeviceToken() == null
                //Nutzer moechte nicht Nachrihcten im Hintergrund laden
                || !preferenceController.getFetchInBackground()) {
            buildExternalNotification(senderGuid, messageGuid, numNewMessages, playSound, locKey, forceTitle, null, application, highPrio);
            return;
        }

        boolean sendWithPreview = true;
        if (preferenceController.isNotificationPreviewDisabledByAdmin()
                || !preferenceController.getNotificationPreviewEnabled()) {
            sendWithPreview = false;
        }

        /* KS: Should not reach so far ...

        // Check for possibly expired AVC message
        final Message messageByGuid = messageController.getMessageByGuid(messageGuid);
        if (messageByGuid != null) {
            final String serverMimeType = messageByGuid.getServerMimeType();
            if(!StringUtil.isNullOrEmpty(serverMimeType) && serverMimeUtil.MIME_TYPE_equals(MimeUtil.MIME_TYPE_TEXT_V_CALL)) {
                final long now = new Date().getTime();
                if (messageByGuid.getDateSend() + NotificationController.AVC_NOTIFICATION_TIMEOUT < now) {
                    LogUtil.d(TAG, "showNotificationFromExtras: Expired AVC message - no notification!");
                    return;
                }
            }
        } else {
            sendWithPreview = false;
        }

         */

        if (sendWithPreview) {
            // Message info has been prepared earlier in GetMessagesTask to avoid multiple decryptions.
            final DecryptedMessage decryptedMessage = notificationController.getAndClearDecryptedMessageInfo(messageGuid);
            if (decryptedMessage != null) {
                buildExternalNotificationWithPreview(decryptedMessage,
                        senderGuid,
                        numNewMessages,
                        playSound,
                        locKey,
                        forceTitle,
                        application,
                        highPrio
                );
                return;
            }
        }

        buildExternalNotification(senderGuid, messageGuid, numNewMessages, playSound, locKey, forceTitle, null, application, highPrio);
    }

    private static void buildExternalNotificationWithPreview(final DecryptedMessage decryptedMessage,
                                                             final String senderGuid,
                                                             final int numNewMessages,
                                                             final boolean playSound,
                                                             final String locKey,
                                                             final String forceTitle,
                                                             final SimsMeApplication application,
                                                             final boolean highPrio) {

        final String text;
        final String altTitle;
        final String contentType = decryptedMessage.getContentType();

        try {
            if (decryptedMessage.getMessageDestructionParams() != null) {
                text = application.getResources().getString(R.string.chat_selfdestruction_preview);
            } else if (StringUtil.isNullOrEmpty(contentType)) {
                text = null;  // dann standard-text
            } else {
                switch (contentType) {
                    case MimeUtil.MIME_TYPE_TEXT_PLAIN:
                        text = decryptedMessage.getText();
                        break;
                    case MimeUtil.MIME_TYPE_TEXT_RSS:
                        text = decryptedMessage.getRssTitle();
                        break;
                    case MimeUtil.MIME_TYPE_AUDIO_MPEG:
                        text = application.getResources().getString(R.string.export_chat_type_audio);
                        break;
                    case MimeUtil.MIME_TYPE_IMAGE_JPEG:
                        text = application.getResources().getString(R.string.export_chat_type_image);
                        break;
                    case MimeUtil.MIME_TYPE_VIDEO_MPEG:
                        text = application.getResources().getString(R.string.export_chat_type_video);
                        break;
                    // KS: AVC
                    case MimeUtil.MIME_TYPE_TEXT_V_CALL:
                        // Do special notification for AVC
                        // - transmit room info
                        // - give sender name
                        text = decryptedMessage.getAVCRoom();
                        altTitle = decryptedMessage.getNickName();
                        buildExternalNotification(senderGuid,
                                decryptedMessage.getMessage().getGuid(),
                                numNewMessages, playSound, LOC_KEY_PRIVATE_MSG_AVC,
                                altTitle, text, application, highPrio);
                        return;
                    //break;
                    case MimeUtil.MIME_TYPE_APP_GINLO_CONTROL:
                        // KS: Control message - should never reach this because message pushInfo has been set to "nopush in GetMessagesTask"
                        text = decryptedMessage.getAppGinloControl();
                        LogUtil.w(TAG, "AppGinloControl message (" + text + ") from " + senderGuid + " set for external push? This should not happen!");
                        return;
                    //break;
                    case MimeUtil.MIME_TYPE_TEXT_V_CARD:
                        text = application.getResources().getString(R.string.export_chat_type_contact);
                        break;
                    case MimeUtil.MIME_TYPE_MODEL_LOCATION:
                        text = application.getResources().getString(R.string.export_chat_type_location);
                        break;
                    case MimeUtil.MIME_TYPE_APP_GINLO_RICH_CONTENT:
                        text = application.getResources().getString(R.string.export_chat_type_file);
                        break;
                    case MimeUtil.MIME_TYPE_APP_OCTETSTREAM:
                    case MimeUtil.MIME_TYPE_APP_OCTET_STREAM:
                        text = application.getResources().getString(R.string.export_chat_type_file);
                        break;
                    default:
                        text = null; // dann standard-text
                        break;
                }
            }
        } catch (LocalizedException e) {
            LogUtil.e(TAG, "buildExternalNotificationWithPreview: Caught " + e);
            return;
        }

        buildExternalNotification(senderGuid, decryptedMessage.getMessage().getGuid(), numNewMessages, playSound, locKey, forceTitle, text, application, highPrio);
    }

    private static void buildExternalNotification(final String senderGuid,
                                                  final String messageGuid,
                                                  final int numNewMessages,
                                                  final boolean playSound,
                                                  final String locKey,
                                                  final String forceTitle,
                                                  final String forceText,
                                                  final SimsMeApplication application,
                                                  final boolean highPrio) {
        LogUtil.i(TAG, "buildExternalNotification " + locKey + ": " + senderGuid + " -> " + messageGuid);

        if (numNewMessages > 0) {
            int msgTyp = 0;

            // KS: Comment: Really? Translating this.messagetype to notificationController-Messagetype!!
            // TODO: Clean that up!

            if (StringUtil.isEqual(locKey, LOC_KEY_PRIVATE_MSG) || StringUtil.isEqual(locKey, LOC_KEY_PRIVATE_MSG_EX)) {
                msgTyp = NotificationController.NEW_PRIVATE_MESSAGE;
            } else if (StringUtil.isEqual(locKey, LOC_KEY_CHANNEL_MSG)) {
                msgTyp = NotificationController.NEW_CHANNEL_MESSAGE;
            } else if (StringUtil.isEqual(locKey, LOC_KEY_PRIVATE_MSG_AVC)) {
                msgTyp = NotificationController.NEW_PRIVATE_MESSAGE_AVC;
            }

            application.getNotificationController().buildExternalNotification(senderGuid, messageGuid, numNewMessages, playSound, msgTyp, forceTitle, forceText, highPrio);
        }
    }

    public static void resetNumNewMessages() {
        numNewMessages = 0;
    }
}
