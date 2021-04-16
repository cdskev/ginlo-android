// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.SpannableString;
import android.util.SparseArray;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import com.google.gson.JsonArray;

import eu.ginlo_apps.ginlo.AVCallMenuActivity;
import eu.ginlo_apps.ginlo.MainActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.chat.ChannelChatActivity;
import eu.ginlo_apps.ginlo.activity.chat.GroupChatActivity;
import eu.ginlo_apps.ginlo.activity.chat.SingleChatActivity;
import eu.ginlo_apps.ginlo.activity.chat.SystemChatActivity;
import eu.ginlo_apps.ginlo.activity.chatsOverview.ChatsOverviewActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.models.NotificationInfoContainer;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.DaoMaster;
import eu.ginlo_apps.ginlo.greendao.DaoSession;
import eu.ginlo_apps.ginlo.greendao.NotificationDao;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.service.FCMService;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.query.QueryBuilder;

import javax.annotation.Nonnull;

public class NotificationController {
    public static final int NEW_PRIVATE_MESSAGE = 1;
    public static final int NEW_PRIVATE_MESSAGE_AVC = 1366;
    public static final int NEW_CHANNEL_MESSAGE = 2;
    public static final int MESSAGE_NOTIFICATION_ID = 219711;
    public static final int AVC_NOTIFICATION_ID = 131966;
    public static final int AVC_NOTIFICATION_ID_AUDIO = 131967;
    public static final int AVC_NOTIFICATION_ID_VIDEO = 131968;
    public static final int AVC_NOTIFICATION_ID_DISMISS = 131969;
    public static final int AVC_NOTIFICATION_ID_OPEN = 131970;
    public static final int GROUP_NOTIFICATION_ID = 199089;
    public static final int INFO_NOTIFICATION_ID = 199090;

    private static int DISMISS_NOTIFICATION_TIMEOUT = 30000; //Millis

    private static final String MESSAGE_NOTIFICATION_CHANNEL_ID = "nc_pm";
    private static final String AVC_NOTIFICATION_CHANNEL_ID = "nc_avc";
    private static final String INFO_NOTIFICATION_CHANNEL_ID = "nc_info";
    private static final String LOAD_MESSAGE_NOTIFICATION_CHANNEL_ID = "nc_load";

    private static final String NOTIFICATION_CHANNEL_GROUP_ID_OTHERS = "ncg_others";
    private static final String NOTIFICATION_CHANNEL_GROUP_ID_CHATS = "ncg_chats";
    private static final String NOTIFICATION_CHANNEL_GROUP_ID_AVC = "ncg_avc";

    private final NotificationManager notificationManager;
    private final SimsMeApplication mApplication;
    private final NotificationDao mNotificationDao;
    private final AVChatController mAVChatController;
    private final ContactController mContactController;
    private final PreferencesController mPreferencesController;

    private SparseArray<NotificationInfoContainer> infoContainerArray;
    private String ignoredGuid;
    private boolean ignoreAll;

    private static final String TAG = NotificationController.class.getSimpleName();

    public NotificationController(final SimsMeApplication application) {
        this.mApplication = application;
        mAVChatController = mApplication.getAVChatController(); // Is null if Android version < 8.0
        mContactController = mApplication.getContactController();
        mPreferencesController = mApplication.getPreferencesController();
        this.infoContainerArray = new SparseArray<>();
        this.ignoreAll = true;
        this.notificationManager = (NotificationManager) application.getSystemService(Context.NOTIFICATION_SERVICE);

        final Database db = application.getDataBase();
        final DaoMaster daoMaster = new DaoMaster(db);
        final DaoSession daoSession = daoMaster.newSession();

        mNotificationDao = daoSession.getNotificationDao();

        createNotificationChannel();
    }

    public static Notification getLoadMessageNotification(final Context context) {
        String title = context.getString(R.string.notification_load_msg_title);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, LOAD_MESSAGE_NOTIFICATION_CHANNEL_ID);
        builder.setOngoing(true)
                .setContentTitle(title)
                .setProgress(0, 0, true)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setTicker(title)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_MIN);

        return builder.build();
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library

        // Take care of channel groups first ...
        if (SystemUtil.hasOreo()) {
            try {
                String groupId;
                CharSequence channelName;

                NotificationChannelGroup channelGroupOthers = null;
                NotificationChannelGroup channelGroupChats = null;
                NotificationChannelGroup channelGroupAVC = null;

                List<NotificationChannelGroup> groups = notificationManager.getNotificationChannelGroups();
                if (groups != null && groups.size() > 0) {
                    for (NotificationChannelGroup group : groups) {
                        groupId = group.getId();
                        switch (groupId) {
                            case NOTIFICATION_CHANNEL_GROUP_ID_OTHERS: {
                                channelGroupOthers = group;
                                break;
                            }
                            case NOTIFICATION_CHANNEL_GROUP_ID_CHATS: {
                                channelGroupChats = group;
                                break;
                            }
                            case NOTIFICATION_CHANNEL_GROUP_ID_AVC: {
                                channelGroupAVC = group;
                                break;
                            }
                            default: {
                                // Delete unknown group!
                                notificationManager.deleteNotificationChannelGroup(groupId);
                                break;
                            }
                        }
                    }
                }

                // Channel Group for Chats
                if (channelGroupChats == null) {
                    CharSequence groupName = mApplication.getString(R.string.settings_chats_title);
                    channelGroupChats = new NotificationChannelGroup(NOTIFICATION_CHANNEL_GROUP_ID_CHATS, groupName);
                    notificationManager.createNotificationChannelGroup(channelGroupChats);
                }

                // Channel Group for audio video calls
                if (channelGroupAVC == null) {
                    CharSequence groupName = mApplication.getString(R.string.settings_avc_title);
                    channelGroupAVC = new NotificationChannelGroup(NOTIFICATION_CHANNEL_GROUP_ID_AVC, groupName);
                    notificationManager.createNotificationChannelGroup(channelGroupAVC);
                }

                //Channel Group for Others
                if (channelGroupOthers == null) {
                    CharSequence groupName = mApplication.getString(R.string.channel_notification_group_others_title);
                    channelGroupOthers = new NotificationChannelGroup(NOTIFICATION_CHANNEL_GROUP_ID_OTHERS, groupName);
                    notificationManager.createNotificationChannelGroup(channelGroupOthers);
                }

                // Now check/set all channels to their appropriate groups

                //Push Vorschau Foreground Service Notification
                NotificationChannel channelLoadMsg = notificationManager.getNotificationChannel(LOAD_MESSAGE_NOTIFICATION_CHANNEL_ID);
                if(channelLoadMsg == null) {
                    channelName = mApplication.getString(R.string.channel_notification_load_msg_title);
                    NotificationChannel channelLoad = new NotificationChannel(LOAD_MESSAGE_NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW);
                    channelLoad.setShowBadge(false);
                    channelLoad.enableLights(false);
                    channelLoad.enableVibration(false);
                    channelLoad.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
                    channelLoad.setGroup(NOTIFICATION_CHANNEL_GROUP_ID_OTHERS);
                    notificationManager.createNotificationChannel(channelLoad);
                }

                //Infos
                NotificationChannel channelInfo = notificationManager.getNotificationChannel(INFO_NOTIFICATION_CHANNEL_ID);
                if(channelInfo == null) {
                    channelName = mApplication.getString(R.string.notification_channel_info_name);
                    channelInfo = new NotificationChannel(INFO_NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW);
                    channelInfo.setShowBadge(false);
                    channelInfo.enableLights(false);
                    channelInfo.enableVibration(false);
                    channelInfo.setGroup(NOTIFICATION_CHANNEL_GROUP_ID_OTHERS);
                    notificationManager.createNotificationChannel(channelInfo);
                }

                //Single Chats
                NotificationChannel channelPN = notificationManager.getNotificationChannel(MESSAGE_NOTIFICATION_CHANNEL_ID);
                if(channelPN == null) {
                    channelName = mApplication.getString(R.string.settings_notification_chat_notifications);
                    channelPN = new NotificationChannel(MESSAGE_NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
                    channelPN.setGroup(NOTIFICATION_CHANNEL_GROUP_ID_CHATS);
                    channelPN.enableVibration(true);
                    channelPN.enableLights(true);
                    channelPN.setLightColor(mApplication.getColor(R.color.color10));
                    // Register the channel with the system; you can't change the importance
                    // or other notification behaviors after this
                    notificationManager.createNotificationChannel(channelPN);
                }

                //AVC
                NotificationChannel channelAVC = notificationManager.getNotificationChannel(AVC_NOTIFICATION_CHANNEL_ID);
                if(channelAVC == null) {
                    channelName = mApplication.getString(R.string.settings_notification_avc_notifications);
                    channelAVC = new NotificationChannel(AVC_NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
                    channelAVC.setGroup(NOTIFICATION_CHANNEL_GROUP_ID_AVC);
                    channelAVC.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                    channelAVC.enableVibration(true);
                    channelAVC.enableLights(true);
                    channelAVC.setLightColor(mApplication.getColor(R.color.color8));

                    //Uri uri = Uri.parse("android.resource://"+this.getPackageName()+"/" + R.raw.aperturaabductores);
                    Uri uri = Settings.System.DEFAULT_RINGTONE_URI;
                    AudioAttributes att = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    channelAVC.setSound(uri,att);
                    notificationManager.createNotificationChannel(channelAVC);
                }
            } catch (Exception e) {
                LogUtil.e(this.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    public void toggleIgnoreAll(boolean ignoreAll) {
        this.ignoreAll = ignoreAll;

        if (ignoreAll) {
            this.ignoredGuid = null;
        } else {
            if (infoContainerArray != null) {
                infoContainerArray.clear();
            }
        }
    }

    public void ignoreGuid(String guid) {
        this.ignoredGuid = guid;
    }

    public static int getInfoNotificationId() {
        return INFO_NOTIFICATION_ID;
    }

    public void showOngoingServiceNotification() {
        final Notification notification = buildOngoingServiceNotification();
        if(notification != null) {
            notificationManager.notify(INFO_NOTIFICATION_ID, notification);
        }
    }

    // Create an ongoing notification to keep ginlo alive
    public Notification buildOngoingServiceNotification() {
        Context context = mApplication;
        if (context == null) {
            LogUtil.w(TAG, " Cannot create notification: no current context");
            return null;
        }

        Intent notificationIntent = new Intent(context, context.getClass());
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        NotificationCompat.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new NotificationCompat.Builder(context, INFO_NOTIFICATION_CHANNEL_ID);
        } else {
            builder = new NotificationCompat.Builder(context);
        }

        builder
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentTitle(mApplication.getString(R.string.notification_gos_running))
                //.setContentText("")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setSmallIcon(R.drawable.ginlo_avatar)
                .setColor(context.getResources().getColor(R.color.business_trial_background))
                .setUsesChronometer(true)
                .setOnlyAlertOnce(true);

        return builder.build();
    }

    public void buildExternalNotification(final String senderGuid,
                                          final String messageGuid,
                                          int numMessages,
                                          boolean playSound,
                                          int messageType,
                                          final String forceTitle,
                                          final String forceText,
                                          final boolean highPriority) {

        if (numMessages == 0) {
            LogUtil.i(TAG, "buildExternalNotification: 0 new messages. Skip push.");
            return;
        }

        // KS: Special handling of AVC messages - got a modified version of forceTitle: "nickname"
        if (messageType == NEW_PRIVATE_MESSAGE_AVC) {
            showAVCInvitationNotification(forceTitle, senderGuid, messageGuid, forceText, null, true);
            return;
        }

        try {
            Intent intent;
            boolean isInvitation = false;
            String title = mApplication.getString(R.string.android_notification_newMessage_title);

            if (StringUtil.isNullOrEmpty(senderGuid)) {
                //intent = new Intent(mApplication, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());
                //return to the chatOverview
                intent = new Intent(mApplication, MainActivity.class);
            } else if (GuidUtil.isChatSingle(senderGuid)) {
                isInvitation = isSingleChatInvitation(senderGuid);

                if (isInvitation) {
                    //intent = new Intent(mApplication, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());
                    intent = new Intent(mApplication, MainActivity.class);
                    title = mApplication.getString(R.string.android_notification_new_single_msg__not_accepted_title);
                } else {
                    //intent = new Intent(mApplication, GuidUtil.isSystemChat(senderGuid) ? SystemChatActivity.class : SingleChatActivity.class);
                    //intent.putExtra(SingleChatActivity.EXTRA_TARGET_GUID, senderGuid);
                    intent = new Intent(mApplication, MainActivity.class);
                    title = mApplication.getString(R.string.android_notification_new_private_msg_title);
                }
            } else if (GuidUtil.isChatRoom(senderGuid)) {
                final Chat chat = mApplication.getGroupChatController().getChatByGuid(senderGuid);

                isInvitation = chat == null || chat.getType() == null || chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION;

                if (isInvitation) {
                    // intent = new Intent(mApplication, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());
                    intent = new Intent(mApplication, MainActivity.class);
                    title = mApplication.getString(R.string.android_notification_new_group_msg__not_accepted_title);
                } else {
                    //intent = new Intent(mApplication, GroupChatActivity.class);
                    //intent.putExtra(SingleChatActivity.EXTRA_TARGET_GUID, senderGuid);
                    intent = new Intent(mApplication, MainActivity.class);
                    try {
                        if (Chat.ROOM_TYPE_RESTRICTED.equals(chat.getRoomType())) {
                            title = mApplication.getString(R.string.android_notification_new_channel_msg_title);
                        } else {
                            title = mApplication.getString(R.string.android_notification_new_group_msg_title);
                        }
                    } catch (final LocalizedException e) {
                        LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                        title = mApplication.getString(R.string.android_notification_new_private_msg_title);
                    }
                }
            } else if (GuidUtil.isChatChannel(senderGuid)) {
                //intent = new Intent(mApplication, ChannelChatActivity.class);
                //intent.putExtra(SingleChatActivity.EXTRA_TARGET_GUID, senderGuid);
                intent = new Intent(mApplication, MainActivity.class);
                title = mApplication.getResources().getString(R.string.android_notification_new_channel_msg_title);
            } else if (GuidUtil.isChatService(senderGuid)) {
                //intent = new Intent(mApplication, ChannelChatActivity.class);
                //intent.putExtra(SingleChatActivity.EXTRA_TARGET_GUID, senderGuid);
                intent = new Intent(mApplication, MainActivity.class);
                title = mApplication.getResources().getString(R.string.android_notification_new_service_msg_title);
            } else {
                //intent = new Intent(mApplication, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());
                intent = new Intent(mApplication, MainActivity.class);
            }

            PendingIntent pendingIntent = PendingIntent.getActivity(mApplication, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mApplication, MESSAGE_NOTIFICATION_CHANNEL_ID);

            builder.setSmallIcon(R.drawable.ic_notification_logo);

            if (!StringUtil.isNullOrEmpty(forceTitle) && !isInvitation) {
                title = forceTitle;
            }

            if (highPriority) {
                title = mApplication.getResources().getString(R.string.chats_overview_important_hint) + " " + title;
            }

            if (!isInvitation) {
                final String contentText;
                if (StringUtil.isNullOrEmpty(forceText)) {
                    contentText = mApplication.getString(R.string.android_notification_newMessage_message, numMessages);
                    builder.setContentText(contentText);
                    builder.setTicker(contentText);
                } else {
                    contentText = forceText;

                    builder.setContentText(contentText);
                    builder.setTicker(contentText);
                    builder.setStyle(new NotificationCompat.BigTextStyle().bigText(contentText));

                    if (numMessages > 1) {
                        title = title + " " + mApplication.getString(R.string.notification_msg_title_more_msg, numMessages - 1);
                    }
                }
            }
            builder.setContentTitle(title);
            builder.setAutoCancel(true);
            builder.setContentIntent(pendingIntent);

            int defaults = Notification.DEFAULT_LIGHTS;

            if (!StringUtil.isNullOrEmpty(senderGuid)) {
                if ((messageType == NEW_PRIVATE_MESSAGE && GuidUtil.isChatSingle(senderGuid) && mPreferencesController.getVibrationForSingleChatsEnabled())
                        || (messageType == NEW_PRIVATE_MESSAGE && GuidUtil.isSystemChat(senderGuid) && mPreferencesController.getVibrationForSingleChatsEnabled())
                        || (messageType == NEW_PRIVATE_MESSAGE && GuidUtil.isChatRoom(senderGuid) && mPreferencesController.getVibrationForGroupsEnabled())
                        || (messageType == NEW_CHANNEL_MESSAGE && GuidUtil.isChatChannel(senderGuid) && mPreferencesController.getVibrationForChannelsEnabled())
                        || (messageType == NEW_PRIVATE_MESSAGE && GuidUtil.isChatService(senderGuid) && mPreferencesController.getVibrationForServicesEnabled())
                ) {

                    defaults |= Notification.DEFAULT_VIBRATE;
                }
            }

            if (playSound) {
                defaults |= Notification.DEFAULT_SOUND;
            }

            builder.setDefaults(defaults);

            Notification notification = builder.build();

            LogUtil.i(TAG, "buildExternalNotification: notify " + MESSAGE_NOTIFICATION_ID);

            notificationManager.notify(MESSAGE_NOTIFICATION_ID, notification);
            mApplication.getNotificationController().setNotificationWasShown(messageGuid);

        } catch (SecurityException e) {
            LogUtil.i(TAG, "buildExternalNotification: error");
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    public void dismissAll() {
        dismissNotification(-1, true);
    }

    public void dismissNotification(String guid) {
        int id = guid.hashCode();

        LogUtil.d(TAG, "Got dismiss notification request for guid " + guid);

        if (infoContainerArray != null) {
            infoContainerArray.remove(id);
            dismissNotification(id, false);
        }
    }

    public void dismissNotification(int notificationId, boolean all) {
          if (all) {
                if (infoContainerArray != null) {
                    infoContainerArray.clear();
                } else {
                    infoContainerArray = new SparseArray<>();
                }
              LogUtil.d(TAG, "Got dismiss all notifications request.");
              notificationManager.cancelAll();
            } else if (notificationId >= 0) {
                  if (infoContainerArray != null) {
                      infoContainerArray.remove(notificationId);
                  }
                  LogUtil.d(TAG, "Got dismiss notification request for id " + notificationId);
                  notificationManager.cancel(notificationId);
            }
    }

    public void dismissOngoingNotification() {
        dismissNotification(getInfoNotificationId(), false);
    }

    public int getDismissNotificationTimeout() {
        return DISMISS_NOTIFICATION_TIMEOUT;
    }

    // Some notifications should be discarded after a while
    // Builder.setTimeoutAfter() doesn't work on old devices (before Oreo, API 26).
    public void createDismissRunner(int notificationId, int timeout, boolean all) {
        final Handler dismissHandler = new Handler(Looper.getMainLooper());
        Runnable dismissRunner = new Runnable() {
            public void run() {
                dismissNotification(notificationId, all);
                LogUtil.d(TAG, "Notification " + notificationId + " cancelled.");
            }
        };
        dismissHandler.postDelayed(dismissRunner, timeout);
        LogUtil.d(TAG, "Runner for notification cancellation initialized!");
    }

    void showInfoNotification(String title, String message) {
        Intent intent = new Intent(mApplication, eu.ginlo_apps.ginlo.ConfigureBackupActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mApplication, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mApplication, INFO_NOTIFICATION_CHANNEL_ID);
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle(builder);

        builder.setContentIntent(pendingIntent);

        if (!StringUtil.isNullOrEmpty(title)) {
            builder.setContentTitle(title);
            bigTextStyle.setBigContentTitle(title);
        }

        if (!StringUtil.isNullOrEmpty(message)) {
            builder.setContentText(message);
            bigTextStyle.bigText(message);
        }

        builder.setSmallIcon(R.drawable.ic_notification_logo);
        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        builder.setAutoCancel(true);

        Notification notification = builder.build();

        notificationManager.notify(INFO_NOTIFICATION_ID, notification);
    }

    /**
     * Returns true if the device is locked or screen turned off (in case password not set)
     */
    public static boolean isDeviceLocked(Context context) {
        boolean isLocked = false;

        // First we check the locked state
        KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        boolean inKeyguardRestrictedInputMode = keyguardManager.isKeyguardLocked();

        if (inKeyguardRestrictedInputMode) {
            isLocked = true;

        } else {
            // If password is not set in the settings, the isKeyguardLocked() returns false,
            // so we need to check if screen on for this case
            PowerManager powerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            isLocked = !powerManager.isInteractive();
        }
        return isLocked;
    }

    // KS: Push avc invitation
    public void showAVCInvitationNotification(final String sender, final String senderGuid, final String messageGuid, final String notificationText, Bitmap notificationImage, boolean fullscreen) {
        try {
            if (mPreferencesController.getShowInAppNotifications()) {

                // No fullscreen if not on lockscreen
                Notification avcNoti = null;
                if(isDeviceLocked(mApplication)) {
                    avcNoti = buildAVCInvitationNotification(sender, senderGuid, notificationText, notificationImage, true);
                } else {
                    avcNoti = buildAVCInvitationNotification(sender, senderGuid, notificationText, notificationImage, false);
                }
                if (avcNoti == null) {
                    // No AVChatController available or avc already busy
                    return;
                }

                notificationManager.notify(AVC_NOTIFICATION_ID, avcNoti);
                // Dont need dismissRunner because AVC is only available since Oreo (which has .setTimeoutAfter())
                // createDismissRunner(AVC_NOTIFICATION_ID, DISMISS_NOTIFICATION_TIMEOUT, false);
                mApplication.getNotificationController().setNotificationWasShown(messageGuid);
            }
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        }
    }

    // KS: Build AVC notification
    private Notification buildAVCInvitationNotification(final String sender, final String senderGuid, final String notificationText, Bitmap notificationImage, boolean fullscreen) {
        if(mAVChatController == null) {
            return null;
        }

        mAVChatController.setRoomInfo(AVChatController.deserializeRoomInfoMessageString(notificationText));
        String myName = "John Doe (unknown)";
        try {
            myName = mContactController.getOwnContact().getNameFromNameAttributes() + " (" + mContactController.getOwnContact().getSimsmeId() + ")";
        } catch (LocalizedException e) {
            e.printStackTrace();
        }
        mAVChatController.setMyName(myName);
        mAVChatController.setTargetGuid(senderGuid);

        String senderName = sender;
        if (StringUtil.isNullOrEmpty(senderName)) {
            senderName = "";
        }
        mAVChatController.setConferenceTopic(senderName);

        Intent openIntent = null;
        Intent intentAudio = null;
        Intent intentVideo = null;
        Intent intentCancel = null;
        RemoteViews view = null;
        if(!fullscreen) {
            LogUtil.d(TAG, "buildAVCInvitationNotification: Build regular notification.");
            intentAudio = mAVChatController.prepareAudioAVCall(senderGuid, AVC_NOTIFICATION_ID, mApplication);
            PendingIntent pendingIntentAudio = PendingIntent.getActivity(mApplication, AVC_NOTIFICATION_ID_AUDIO, intentAudio,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            intentVideo = mAVChatController.prepareVideoAVCall(senderGuid, AVC_NOTIFICATION_ID, mApplication);
            PendingIntent pendingIntentVideo = PendingIntent.getActivity(mApplication, AVC_NOTIFICATION_ID_VIDEO, intentVideo,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            intentCancel = mAVChatController.prepareDismissCall(senderGuid, AVC_NOTIFICATION_ID, mApplication);
            PendingIntent pendingIntentCancel = PendingIntent.getActivity(mApplication, AVC_NOTIFICATION_ID_DISMISS, intentCancel,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            openIntent = new Intent(mApplication, AVCallMenuActivity.class);
            openIntent.putExtra(AVCallMenuActivity.EXTRA_FROM_GUID, senderGuid);
            openIntent.putExtra(AVCallMenuActivity.EXTRA_FROM_NOTIFICATION, AVC_NOTIFICATION_ID);
            openIntent.putExtra(AVCallMenuActivity.EXTRA_ACTION, AVCallMenuActivity.ACTION_NONE);

            view = new RemoteViews(mApplication.getPackageName(), R.layout.notification_avc);
            view.setOnClickPendingIntent(R.id.notification_closebtn_ib, pendingIntentCancel);
            view.setOnClickPendingIntent(R.id.notification_audiobtn_ib, pendingIntentAudio);
            view.setOnClickPendingIntent(R.id.notification_videobtn_ib, pendingIntentVideo);
            view.setTextViewText(R.id.notification_title_iv, mApplication.getResources().getString(R.string.notification_new_avc) + " " + senderName);
            if(notificationImage != null) {
                view.setImageViewBitmap(R.id.notification_icon_iv, notificationImage);
            }

        } else {
            LogUtil.d(TAG, "buildAVCInvitationNotification: Build fullscreen notification.");
            openIntent = new Intent(mApplication, AVCallMenuActivity.class);
            openIntent.putExtra(AVCallMenuActivity.EXTRA_FROM_NOTIFICATION, AVC_NOTIFICATION_ID);
            openIntent.putExtra(AVCallMenuActivity.EXTRA_FROM_GUID, senderGuid);
            openIntent.putExtra(AVCallMenuActivity.EXTRA_ACTION, AVCallMenuActivity.ACTION_ASK);

        }
        //openIntent.putExtra(SingleChatActivity.EXTRA_TARGET_GUID, senderGuid);
        PendingIntent pOpenIntent = PendingIntent.getActivity(mApplication, AVC_NOTIFICATION_ID_OPEN, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // KS: Task stack improvisation
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(mApplication);
        stackBuilder.addParentStack(ChatsOverviewActivity.class);
        stackBuilder.addNextIntent(openIntent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mApplication, AVC_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(mApplication.getResources().getString(R.string.notification_new_avc) + " " + senderName)
                .setContentText("")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setTimeoutAfter(DISMISS_NOTIFICATION_TIMEOUT)
                .setAutoCancel(true)
                .setContentIntent(pOpenIntent);

        if(!fullscreen) {
            builder.setCustomContentView(view);
            builder.setCustomBigContentView(view);
        } else {
            builder.setFullScreenIntent(pOpenIntent, true);
        }
        return builder.build();
    }

    public void showInternalNotification(List<NotificationInfoContainer> infoContainers, final boolean isPriority) {
        int id;

        if (infoContainers.size() == 0) {
            return;
        }

        NotificationInfoContainer infoContainer;

        Pattern pattern = Pattern.compile("https?:\\/\\/\\S*");

        boolean newMessage = false;

        boolean singleVibrateEnabled = mPreferencesController.getVibrationForSingleChatsEnabled();
        boolean groupVibrateEnabled = mPreferencesController.getVibrationForGroupsEnabled();
        boolean channelVibrateEnabled = mPreferencesController.getVibrationForChannelsEnabled();
        boolean serviceVibrateEnabled = mPreferencesController.getVibrationForServicesEnabled();

        boolean vibrate = false;

        // Build/update new global list of notification infos
        for (int i = 0; i < infoContainers.size(); i++) {
            id = infoContainers.get(i).getSenderGuid().hashCode();
            infoContainer = infoContainerArray.get(id);

            if (infoContainer != null) {
                // A notification info for the given sender GUID is already in global list
                // No more entries to prevent multiple notifications
                continue;
            } else {
                // No info for given sender GUID in global list - prepare a new one
                infoContainer = infoContainers.get(i);

                String shortLinkText = infoContainer.getShortLinkText();
                String text = infoContainer.getLastMessage(); // "preview" text in CreateNotificationTask

                SpannableString ss = StringUtil.replaceUrlNew(text, shortLinkText, pattern, true);
                infoContainer.setLastMessage(ss != null ? ss.toString() : "");

                // Put new info to global list
                infoContainerArray.put(id, infoContainer);

                // Find out whether it's time to send notification, e.g. we have at least one new message to signal
                // Then set newMessage true
                try {
                    final Chat chat;

                    if (GuidUtil.isChatSingle(infoContainer.getSenderGuid())) {
                        chat = mApplication.getSingleChatController().getChatByGuid(infoContainer.getSenderGuid());
                    } else if (GuidUtil.isChatRoom(infoContainer.getSenderGuid())) {
                        chat = mApplication.getGroupChatController().getChatByGuid(infoContainer.getSenderGuid());
                    } else {
                        chat = null;
                    }

                    if (chat != null) {
                        final long now = new Date().getTime();
                        final long silentTill = chat.getSilentTill();
                        if (now > silentTill) {
                            newMessage = true;
                        }
                    } else {
                        //kein chat vorhanden -> neu
                        newMessage = true;
                    }
                } catch (final LocalizedException e) {
                    LogUtil.w(this.getClass().getName(), e.getMessage(), e);
                }
            }

            // If only one notification info, activate haptic features
            if (i == 0) {
                if (GuidUtil.isChatSingle(infoContainer.getSenderGuid())) {
                    if (singleVibrateEnabled) {
                        vibrate = true;
                    }
                } else if (GuidUtil.isChatRoom(infoContainer.getSenderGuid())) {
                    if (groupVibrateEnabled) {
                        vibrate = true;
                    }
                } else if (GuidUtil.isChatChannel(infoContainer.getSenderGuid())) {
                    if (channelVibrateEnabled) {
                        vibrate = true;
                    }
                } else if (GuidUtil.isChatService(infoContainer.getSenderGuid())) {
                    if (serviceVibrateEnabled) {
                        vibrate = true;
                    }
                }
            }
        }

        if (newMessage) {
            showInternalNotification(isPriority, vibrate);
        }
    }

    private void showInternalNotification(final boolean isPriority, final boolean vibrate) {
        try {
            if (mPreferencesController.getShowInAppNotifications()) {

                // AVC available?
                if (mAVChatController != null) {
                    if (infoContainerArray.size() == 0) {
                        return;
                    }

                    NotificationInfoContainer infoContainer = infoContainerArray.valueAt(infoContainerArray.size() - 1);
                    if (infoContainer == null || (infoContainer.getSenderGuid().equals(ignoredGuid))) {
                        return;
                    }

                    // Now look what kind of notification we must build
                    if (!"AVC!".equals(infoContainer.getShortLinkText())) {
                        // Regular notification
                        // Only these are ignored if ignoreAll is set.
                        if(ignoreAll) {
                            return;
                        }
                        Notification notification = buildInternalNotification(isPriority, vibrate);
                        if (notification != null) {
                            notificationManager.notify(MESSAGE_NOTIFICATION_ID, notification);
                            infoContainerArray.clear();
                        }
                    } else {
                        // AVC!
                        // Call notifications are not affected by ignoreAll.
                        String name = infoContainer.getName();
                        String roomInfo = infoContainer.getLastMessage();

                        Notification avcNoti = buildAVCInvitationNotification(name, infoContainer.getSenderGuid(), roomInfo, infoContainer.getImage(), false);
                        if (avcNoti == null) {
                            // No AVChatController available or avc already busy
                            return;
                        }
                        notificationManager.notify(AVC_NOTIFICATION_ID, avcNoti);
                        //createDismissRunner(AVC_NOTIFICATION_ID, DISMISS_NOTIFICATION_TIMEOUT, false);
                        infoContainerArray.clear();
                    }

                } else {
                    Notification notification = buildInternalNotification(isPriority, vibrate);
                    if (notification != null) {
                        notificationManager.notify(MESSAGE_NOTIFICATION_ID, notification);
                        infoContainerArray.clear();
                    }

                }

            }
        } catch (LocalizedException e) {
            LogUtil.w(TAG, "showInternalNotification ", e);
        }
    }

    private Notification buildInternalNotification(final boolean isPriority, final boolean vibrate) {

        if (infoContainerArray.size() == 0) {
            return null;
        }

        // Bug 43587 - Tap auf externe und inApp-Push solle gleiches Verhalten auslösen -> nur noch in letzte
        NotificationInfoContainer infoContainer = infoContainerArray.valueAt(infoContainerArray.size() - 1);

        if (infoContainer == null || (infoContainer.getSenderGuid().equals(ignoredGuid))) {
            return null;
        }

        Intent intent;
        String title;

        boolean isInvitation = false;
        if (GuidUtil.isChatChannel(infoContainer.getSenderGuid())) {
            title = mApplication.getString(R.string.android_notification_new_channel_msg_title);

            intent = new Intent(mApplication, ChannelChatActivity.class);
            intent.putExtra(ChannelChatActivity.EXTRA_TARGET_GUID, infoContainer.getSenderGuid());
        } else if (GuidUtil.isChatService(infoContainer.getSenderGuid())) {
            title = mApplication.getString(R.string.android_notification_new_service_msg_title);

            intent = new Intent(mApplication, ChannelChatActivity.class);
            intent.putExtra(ChannelChatActivity.EXTRA_TARGET_GUID, infoContainer.getSenderGuid());
        } else if (GuidUtil.isChatRoom(infoContainer.getSenderGuid())) {
            final Chat chat = mApplication.getGroupChatController().getChatByGuid(infoContainer.getSenderGuid());

            if (chat == null || chat.getType() == null) {
                title = mApplication.getString(R.string.android_notification_new_group_msg__not_accepted_title);
                isInvitation = true;
            } else {
                if (chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION) {
                    title = mApplication.getString(R.string.android_notification_new_group_msg__not_accepted_title);
                    isInvitation = true;
                } else {
                    try {
                        if (Chat.ROOM_TYPE_RESTRICTED.equals(chat.getRoomType())) {
                            title = mApplication.getString(R.string.android_notification_new_channel_msg_title);
                        } else {
                            title = mApplication.getString(R.string.android_notification_new_group_msg_title);
                        }
                    } catch (final LocalizedException e) {
                        LogUtil.w(this.getClass().getName(), e.getMessage(), e);
                        title = mApplication.getString(R.string.android_notification_new_channel_msg_title);
                    }
                }
            }
            JsonArray chatMembers = null;

            if (chat != null) {
                try {
                    chatMembers = chat.getMembers();
                } catch (LocalizedException e) {
                    LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                }
            }
            if ((chatMembers != null && chatMembers.size() == 0) || isInvitation) {
                intent = new Intent(mApplication, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());
                intent.putExtra(ChatsOverviewActivity.EXTRA_SCROLL_UP, true);
            } else {
                intent = new Intent(mApplication, GroupChatActivity.class);
                intent.putExtra(GroupChatActivity.EXTRA_TARGET_GUID, infoContainer.getSenderGuid());
            }
        } else if (GuidUtil.isChatSingle(infoContainer.getSenderGuid())) {
            isInvitation = isSingleChatInvitation(infoContainer.getSenderGuid());

            if (isInvitation) {
                title = mApplication.getString(R.string.android_notification_new_single_msg__not_accepted_title);
                intent = new Intent(mApplication, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());
                intent.putExtra(ChatsOverviewActivity.EXTRA_SCROLL_UP, true);
            } else {
                title = mApplication.getString(R.string.android_notification_new_private_msg_title);
                intent = new Intent(mApplication, GuidUtil.isSystemChat(infoContainer.getSenderGuid()) ? SystemChatActivity.class : SingleChatActivity.class);
                intent.putExtra(SingleChatActivity.EXTRA_TARGET_GUID, infoContainer.getSenderGuid());
            }
        } else {
            return null;
        }

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(mApplication);
        /*
         *    BUG 41627
         *    SGA: beim Klick auf den Intent wird ein neuer Navigations-Strang fuer die Zurueck-Taste angelegt.
         *    Dazu muessen wir einen Parent-Stack angeben. Dieser ist im Manifest fuer die jeweilige Activity definiert.
         *    Wenn man fuer die Chat-Activities einen Parent definiert, wird bei onBackPressed immer zum Parent gesprungen,
         *    egal, was sonst noch in der Methode steht: Bug 41859
         *    Man kann also fuer die Chat-Activities keinen Parent im Manifest definieren, ohne die Navigation kaputt zu machen.
         *    Ohne Parent-Stack wird die App unter Umstaenden beendet.
         *
         *    Workarround: fuer die PreferencesInformationActivity wurde ein Parent definiert und wir nutzen diesen als Parent-Stack
         *
         */
        stackBuilder.addParentStack(ChatsOverviewActivity.class);
        stackBuilder.addNextIntent(intent);
        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        String contentText = (infoContainer.getName() == null) ? infoContainer.getLastMessage() : (infoContainer.getName() + ": " + infoContainer.getLastMessage());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mApplication, MESSAGE_NOTIFICATION_CHANNEL_ID);

        builder.setSmallIcon(R.drawable.ic_notification_logo);

        if (isPriority) {
            title = mApplication.getResources().getString(R.string.chats_overview_important_hint) + " " + title;
        }

        builder.setContentTitle(title);
        if (!isInvitation) {
            builder.setContentText(contentText);
            builder.setTicker(contentText);
        }
        builder.setAutoCancel(true);
        builder.setContentIntent(pendingIntent);

        //damit eine heads-up notification angezeigt wird, muss das noch gesetzt werden
        builder.setPriority(NotificationCompat.PRIORITY_MAX);
        if (vibrate) {
            builder.setDefaults(Notification.DEFAULT_VIBRATE);
        } else {
            long[] pattern = {0};
            builder.setVibrate(pattern);
        }
        //nur ab 5.0 werden Nutzericons angezeigt
        builder.setLargeIcon(infoContainer.getImage());

        return builder.build();
    }

    public void showGroupInvitationNotification(final String notificationText) {
        try {
            if (!ignoreAll && mPreferencesController.getShowInAppNotifications() &&
                    mPreferencesController.getNotificationForGroupChatEnabled()) {
                notificationManager.notify(GROUP_NOTIFICATION_ID, buildGroupInvitationNotification(notificationText));
            }
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        }
    }

    private Notification buildGroupInvitationNotification(final String notificationText) {
        Intent intent = new Intent(mApplication, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());
        PendingIntent pendingIntent = PendingIntent.getActivity(mApplication, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mApplication, MESSAGE_NOTIFICATION_CHANNEL_ID);

        builder.setSmallIcon(R.drawable.ic_notification_logo);
        String content = "";

        builder.setContentTitle(notificationText);
        builder.setContentText(content);
        builder.setTicker(notificationText);
        builder.setAutoCancel(true);
        builder.setContentIntent(pendingIntent);

        boolean groupVibrateEnabled = mPreferencesController.getVibrationForGroupsEnabled();

        //damit eine heads-up notification angezeigt wird, muss das noch gesetzt werden
        builder.setPriority(NotificationCompat.PRIORITY_MAX);
        if (groupVibrateEnabled) {
            builder.setDefaults(Notification.DEFAULT_VIBRATE);
        } else {
            long[] pattern = {0};
            builder.setVibrate(pattern);
        }

        return builder.build();
    }

    private boolean isSingleChatInvitation(String chatGuid) {
        final Chat chat = mApplication.getSingleChatController().getChatByGuid(chatGuid);

        if (chat == null || chat.getType() == null) {
            return mApplication.getContactController().isFirstContact(chatGuid);
        } else return chat.getType() == Chat.TYPE_SINGLE_CHAT_INVITATION;
    }

    public boolean wasNotificationShownForMessage(final String messageGuid) {
        synchronized (mNotificationDao) {
            final QueryBuilder<eu.ginlo_apps.ginlo.greendao.Notification> queryBuilder = mNotificationDao.queryBuilder();
            final List<eu.ginlo_apps.ginlo.greendao.Notification> notifications = queryBuilder.where(NotificationDao.Properties.MessageGuid.eq(messageGuid)).list();

            return notifications != null && notifications.size() != 0;
        }
    }

    public void setNotificationWasShown(final String messageGuid) {

        eu.ginlo_apps.ginlo.greendao.Notification notification = new eu.ginlo_apps.ginlo.greendao.Notification(null, messageGuid);
        synchronized (mNotificationDao) {
            mNotificationDao.insert(notification);
        }
    }

    public void deleteNotification(final String messageGuid) {
        final QueryBuilder<eu.ginlo_apps.ginlo.greendao.Notification> queryBuilder = mNotificationDao.queryBuilder();
        final List<eu.ginlo_apps.ginlo.greendao.Notification> notifications = queryBuilder.where(NotificationDao.Properties.MessageGuid.eq(messageGuid)).list();

        if (notifications != null && notifications.size() != 0) {
            synchronized (mNotificationDao) {
                mNotificationDao.delete(notifications.get(0));
            }
        }
    }
}
