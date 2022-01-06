// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.broadcastreceiver.GCMBroadcastReceiver;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycle;
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycleImpl;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.controller.LoginController;
import eu.ginlo_apps.ginlo.controller.MessageDecryptionController;
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
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import java.io.IOException;
import java.util.Date;

import me.leolin.shortcutbadger.ShortcutBadgeException;
import me.leolin.shortcutbadger.ShortcutBadger;

public class GCMIntentService
        extends IntentService {
    private static final String TAG = "GCMIntentService";

    private static final int FOREGROUND_ID = 1981;

    private static final String ACTION_NEW_MESSAGES = "simsme://getNewMessages";
    private static final String LOC_KEY = "loc-key";
    private static final String LOC_KEY_APP_GINLO_CONTROL = "push_newAGC";
    private static final String LOC_KEY_PRIVATE_MSG = "push_newPN";
    // Really? Looks like a typo to me. Must check that!
    // private static final String LOC_KEY_PRIVATE_MSG_EX = "newPNex";
    // First correct it that way and look what happens:
    private static final String LOC_KEY_PRIVATE_MSG_EX = "push_newPNex";
    private static final String LOC_KEY_PRIVATE_MSG_AVC = "push_newAVC";
    private static final String LOC_KEY_GROUP_INV = "push_groupInv";
    private static final String LOC_KEY_GROUP_INV_EX = "push_groupInvEx";
    private static final String LOC_KEY_GROUP_INV_MAN = "push_managedRoomInv";
    private static final String LOC_KEY_GROUP_INV_MAN_EX = "push_managedRoomInvEx";
    private static final String LOC_KEY_GROUP_INV_RES = "push_restrictedRoomInv";
    private static final String LOC_KEY_GROUP_INV_RES_EX = "push_restrictedRoomInvEx";
    private static final String LOC_KEY_PRIVATE_MSG_EX_HIGH = "push_newPNexHigh";
    private static final String LOC_KEY_CHANNEL_MSG = "push_newCN";

    private static final String SENDER_GUID = "senderGuid";

    public GCMIntentService() {
        super("GCMIntentService");
    }

    public static boolean haveToShowNotificationOrSetBadge(@NonNull final SimsMeApplication application, @NonNull final Intent intent) {
        final Bundle extras = checkIntent(intent);
        if (extras == null) {
            return false;
        }

        // If no action is set, no action takes place ;)
        final String action = extras.getString("action");
        if(action == null) {
            return false;
        }

        final String messageGuid = extras.getString("messageGuid");
        final String accountGuid = extras.getString("accountGuid");

        if (!StringUtil.isNullOrEmpty(messageGuid)) {
            final Message messageByGuid = application.getMessageController().getMessageByGuid(messageGuid);
            if (messageByGuid != null) {
                String serverMimeType = messageByGuid.getServerMimeType();
                if(!StringUtil.isNullOrEmpty(serverMimeType) && serverMimeType.equals(MimeType.TEXT_V_CALL)) {
                    // Always process AVC message
                    LogUtil.d(TAG, "haveToShowNotificationOrSetBadge: Message already in database, but is new AVC: " + messageGuid);
                } else {
                    LogUtil.d(TAG, "haveToShowNotificationOrSetBadge: No PN. Message already in database: " + messageGuid);
                    return false;
                }
            }
        }

        if (application.getAccountController().getAccount() == null
                || application.getAccountController().getAccount().getState() != Account.ACCOUNT_STATE_FULL) {
            LogUtil.e(TAG, "haveToShowNotificationOrSetBadge: No valid local account!");
            return false;
        }

        if (accountGuid != null && !accountGuid.equalsIgnoreCase(application.getAccountController().getAccount().getAccountGuid())) {
            LogUtil.e(TAG, "haveToShowNotificationOrSetBadge: Account guid does not match local account: " + accountGuid);
            return false;
        }

        return StringUtil.isEqual(action, ACTION_NEW_MESSAGES);
    }

    public static boolean haveToStartAsForegroundService(@NonNull final SimsMeApplication application, @NonNull final Intent intent) {
        final Bundle extras = checkIntent(intent);

        if (extras == null) {
            return false;
        }

        String locKey = extras.getString(LOC_KEY, LOC_KEY_PRIVATE_MSG).replace(".", "_");
        final String messageGuid = extras.getString("messageGuid");

        final boolean onlyDisplayBadge = StringUtil.isEqual(locKey, "push_only");

        if (onlyDisplayBadge) {
            return false;
        }

        //Keine Message Guid oder es ist eine Channel Msg
        return !StringUtil.isNullOrEmpty(messageGuid) && !messageGuid.startsWith(AppConstants.GUID_MSG_CHANNEL_PREFIX)
                //kein Token zum Laden der Message
                && application.getMessageController().getMessageDeviceToken() != null
                //Nutzer moechte nicht Nachrihcten im Hintergrund laden
                && application.getPreferencesController().getFetchInBackground();
    }

    private static Bundle checkIntent(@NonNull Intent intent) {
        final Bundle extras = intent.getExtras();

        return ((extras != null) && !extras.isEmpty()) ? extras : null;
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

        int numNewMessages = 0;

        if (badge != null) {
            try {
                numNewMessages = Integer.parseInt(badge);
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
                            String placeholder = "%" +
                                    (i + 1) +
                                    "$s";
                            forceTitle = text.replace(placeholder, nickName);
                        }
                    }
                }
            } else {
                forceTitle = text;
            }
        }

        Bundle logExtras = (Bundle) extras.clone();
        // Remove personal data before writing to log.
        if(logExtras.containsKey("loc-args"))
            logExtras.remove("loc-args");

        LogUtil.i(TAG, "showNotificationFromExtras: " + logExtras.toString());

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

        final boolean onlyDisplayBadge = StringUtil.isEqual(locKey, "push_only");

        // Reset Badgenumber
        if (numNewMessages > 0) {
            try {
                ShortcutBadger.applyCountOrThrow(application, numNewMessages);
            } catch (ShortcutBadgeException e) {
                LogUtil.w(TAG, "showNotificationFromExtras: Reset badge number caught " + e.getMessage());
            }
        }

        //keine Anzeige ein Notification
        if (onlyDisplayBadge) {
            LogUtil.i(TAG, "showNotificationFromExtras: onlyDisplayBadge is true. No notification will be shown to the user.");
            return;
        }

        final GinloAppLifecycle alController = application.getAppLifecycleController();
        final LoginController loginController = application.getLoginController();

        /////////////////////// KS: Do we really need this?
        /*
        //App wird angezeigt und ist nicht gesperrt
        if (alController.isAppInForeground() && loginController.isLoggedIn()) {
            LogUtil.i(TAG, "showNotificationFromExtras: isAppInForeground "
                    + alController.isAppInForeground() + " isLoggedIn "
                    + loginController.isLoggedIn() + ". No push shown to the user");
            return;
        }

         */

        //Keine Message Guid oder es ist eine Channel Msg
        if (StringUtil.isNullOrEmpty(messageGuid) || messageGuid.startsWith(AppConstants.GUID_MSG_CHANNEL_PREFIX)
                //kein Token zum Laden der Message
                || messageController.getMessageDeviceToken() == null
                //Nutzer moechte nicht Nachrihcten im Hintergrund laden
                || !preferenceController.getFetchInBackground()) {
            buildExternalNotification(senderGuid, messageGuid, numNewMessages, playSound, locKey, forceTitle, null, application, loginController, alController, highPrio);
            return;
        }

        boolean sendWithPreview = true;

        //Nachrichtenvorschau deaktiviert
        if (preferenceController.isNotificationPreviewDisabledByAdmin()
                || !preferenceController.getNotificationPreviewEnabled()) {
            sendWithPreview = false;
        } else if (loginController.isLoggedIn()) {
            sendWithPreview = true;
        } else if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            try {
                //message mit externen Key entschluesseln
                final KeyController keyController = application.getKeyController();
                // user war schon mal eingeloggt
                if (!keyController.getInternalKeyReady()) {
                    keyController.loadInternalEncryptionKeyForNotificationPreview();
                    GreenDAOSecurityLayer.init(keyController);
                    keyController.reloadUserKeypairFromAccount();
                }
                sendWithPreview = true;
            } catch (final LocalizedException | IOException le) {
                sendWithPreview = false;
            }
        } else {
            sendWithPreview = false;
        }

        final String lSenderGuid = senderGuid;
        final int lNumNewMessages = numNewMessages;
        final boolean lPlaySound = playSound;
        final String lLocKey = locKey;
        final String lForceTitle = forceTitle;
        final boolean lSendWithPreview = sendWithPreview;

        final ConcurrentTaskListener concurrentTaskListener = new ConcurrentTaskListener() {
            @Override
            public void onStateChanged(ConcurrentTask task, int state) {
                try {
                    if (state == ConcurrentTask.STATE_COMPLETE) {
                        LogUtil.d(TAG, "showNotificationFromExtras: Message(s) retrieved from the backend.");
                        // Check for possibly expired AVC message
                        final Message messageByGuid = messageController.getMessageByGuid(messageGuid);
                        if (messageByGuid != null) {
                            final String serverMimeType = messageByGuid.getServerMimeType();
                            if(!StringUtil.isNullOrEmpty(serverMimeType) && serverMimeType.equals(MimeType.TEXT_V_CALL)) {
                                final long now = new Date().getTime();
                                if (messageByGuid.getDateSend() + NotificationController.DISMISS_NOTIFICATION_TIMEOUT < now) {
                                    LogUtil.d(TAG, "showNotificationFromExtras: Expired AVC message - no notification!");
                                    return;
                                }
                            }
                        }
                        if (lSendWithPreview) {
                            //final Message messageByGuid = messageController.getMessageByGuid(messageGuid);
                            if (messageByGuid == null) {
                                buildExternalNotification(lSenderGuid, messageGuid, lNumNewMessages, lPlaySound, lLocKey, lForceTitle, null, application, loginController, alController, highPrio);
                                return;
                            }

                            final DecryptedMessage decryptedMessage = application.getMessageDecryptionController().decryptMessage(messageByGuid, false);
                            if (decryptedMessage == null) {
                                buildExternalNotification(lSenderGuid, messageGuid, lNumNewMessages, lPlaySound, lLocKey, lForceTitle, null, application, loginController, alController, highPrio);
                                return;
                            }

                            buildExternalNotificationWithPreview(decryptedMessage,
                                    lSenderGuid,
                                    lNumNewMessages,
                                    lPlaySound,
                                    lLocKey,
                                    lForceTitle,
                                    application,
                                    loginController,
                                    alController,
                                    highPrio
                            );
                        } else {
                            buildExternalNotification(lSenderGuid, messageGuid, lNumNewMessages, lPlaySound, lLocKey, lForceTitle, null, application, loginController, alController, highPrio);
                        }
                    } else if (state == ConcurrentTask.STATE_ERROR) {
                        LogUtil.w(TAG, "showNotificationFromExtras: Could not retrieve message(s) - ConcurrentTask.STATE_ERROR.");
                        buildExternalNotification(lSenderGuid, messageGuid, lNumNewMessages, lPlaySound, lLocKey, lForceTitle, null, application, loginController, alController, highPrio);
                    }
                } catch (final LocalizedException e) {
                    LogUtil.e(TAG, "showNotificationFromExtras: Got exception " + e.getMessage());
                    buildExternalNotification(lSenderGuid, messageGuid, lNumNewMessages, lPlaySound, lLocKey, lForceTitle, null, application, loginController, alController, highPrio);
                }
            }
        };
        messageController.startMessageTaskSyncFromAppBackground(concurrentTaskListener);
    }

    private static void buildExternalNotificationWithPreview(final DecryptedMessage decryptedMessage,
                                                             final String senderGuid,
                                                             final int numNewMessages,
                                                             final boolean playSound,
                                                             final String locKey,
                                                             final String forceTitle,
                                                             final SimsMeApplication application,
                                                             final LoginController loginController,
                                                             final GinloAppLifecycle alController,
                                                             final boolean highPrio)
            throws LocalizedException {
        final String text;
        final String altTitle;
        final String contentType = decryptedMessage.getContentType();
        if (decryptedMessage.getMessageDestructionParams() != null) {
            text = application.getResources().getString(R.string.chat_selfdestruction_preview);
        } else if (StringUtil.isNullOrEmpty(contentType)) {
            text = null;  // dann standard-text
        } else {
            switch (contentType) {
                case MimeType.TEXT_PLAIN:
                    text = decryptedMessage.getText();
                    break;
                case MimeType.TEXT_RSS:
                    text = decryptedMessage.getRssTitle();
                    break;
                case MimeType.AUDIO_MPEG:
                    text = application.getResources().getString(R.string.export_chat_type_audio);
                    break;
                case MimeType.IMAGE_JPEG:
                    text = application.getResources().getString(R.string.export_chat_type_image);
                    break;
                case MimeType.VIDEO_MPEG:
                    text = application.getResources().getString(R.string.export_chat_type_video);
                    break;
                    // KS: AVC
                case MimeType.TEXT_V_CALL:
                    // Do special notification for AVC
                    // - transmit room info
                    // - give sender name
                    text = decryptedMessage.getAVCRoom();
                    altTitle = decryptedMessage.getNickName();
                    buildExternalNotification(senderGuid,
                            decryptedMessage.getMessage().getGuid(),
                            numNewMessages, playSound, LOC_KEY_PRIVATE_MSG_AVC,
                            altTitle, text, application, loginController, alController, highPrio);
                    return;
                    //break;
                case MimeType.APP_GINLO_CONTROL:
                    // KS: Control message - should never reach this because message pushInfo has been set to "nopush in GetMessagesTask"
                    text = decryptedMessage.getAppGinloControl();
                    LogUtil.w(TAG, "AppGinloControl message (" + text + ") from " + senderGuid + " set for external push? This should not happen!");
                    return;
                    //break;
                case MimeType.TEXT_V_CARD:
                    text = application.getResources().getString(R.string.export_chat_type_contact);
                    break;
                case MimeType.MODEL_LOCATION:
                    text = application.getResources().getString(R.string.export_chat_type_location);
                    break;
                case MimeType.APP_OCTET_STREAM:
                    text = application.getResources().getString(R.string.export_chat_type_file);
                    break;
                default:
                    text = null; // dann standard-text
                    break;
            }
        }
        buildExternalNotification(senderGuid, decryptedMessage.getMessage().getGuid(), numNewMessages, playSound, locKey, forceTitle, text, application, loginController, alController, highPrio);
    }

    private static void buildExternalNotification(final String senderGuid,
                                                  final String messageGuid,
                                                  final int numNewMessages,
                                                  final boolean playSound,
                                                  final String locKey,
                                                  final String forceTitle,
                                                  final String forceText,
                                                  final SimsMeApplication application,
                                                  final LoginController liController,
                                                  final GinloAppLifecycle alController,
                                                  final boolean highPrio) {
        LogUtil.i(TAG, "buildExternalNotification for " + locKey + " -> " + messageGuid);

        // Always let the NotificationController do it's work
        // if ((numNewMessages > 0) && (!alController.isAppInForeground() || !liController.isLoggedIn())) {
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

    @Override
    protected void onHandleIntent(final Intent intent) {
        if (intent == null) {
            return;
        }

        final Bundle extras = checkIntent(intent);

        if (extras == null) {
            return;
        }

        SimsMeApplication application = (SimsMeApplication) getApplication();

        boolean wasStartAsForeground = false;
        if (haveToStartAsForegroundService(application, intent) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
            wasStartAsForeground = true;
            startService();
        }

        showNotificationFromExtras(application, extras);

        finishService(wasStartAsForeground, intent);
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
        } else {
            GCMBroadcastReceiver.completeWakefulIntent(intent);
        }
    }
}
