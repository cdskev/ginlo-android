// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.task;

import android.graphics.Bitmap;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AVChatController;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.message.GroupChatController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.controller.message.MessageDataResolver;
import eu.ginlo_apps.ginlo.controller.models.NotificationInfoContainer;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CreateNotificationTask
        extends ConcurrentTask {

    private final static String TAG = CreateNotificationTask.class.getSimpleName();
    private final NotificationController notificationController;
    private final ChatOverviewController chatOverviewController;
    private final ChannelController channelController;
    private final PreferencesController prefController;
    private final ContactController contactController;
    private final MessageController messageController;
    private final GroupChatController groupController;
    private final SimsMeApplication mContext;
    private final AVChatController mAVChatController;

    public CreateNotificationTask(final SimsMeApplication context) {
        super();

        this.chatOverviewController = context.getChatOverviewController();
        this.notificationController = context.getNotificationController();
        this.prefController = context.getPreferencesController();
        this.messageController = context.getMessageController();
        this.mContext = context;
        this.channelController = context.getChannelController();
        this.contactController = context.getContactController();
        this.groupController = context.getGroupChatController();
        this.mAVChatController = context.getAVChatController();
    }

    @Override
    public void run() {
        super.run();

        final String mAccountGuid = this.mContext.getAccountController().getAccount().getAccountGuid();

        final List<Message> messages = messageController.getUnreadMessagesForNotification();

        if (messages == null || messages.isEmpty()) {
            complete();
            return;
        }
        final Message lastMessage = messages.get(0);
        final boolean isPriority;
        if (lastMessage != null) {
            isPriority = lastMessage.getIsPriority();
        } else {
            isPriority = false;
        }

        final List<NotificationInfoContainer> notificationInfos = new ArrayList<>();

        try {
            final HashMap<String, Integer> chatMap = new HashMap<>();
            final HashMap<String, Bitmap> bitmapCache = new HashMap<>();

            final boolean showChannelNotifications = prefController.getNotificationForChannelChatEnabled();
            final boolean showServiceNotifications = prefController.getNotificationForServiceChatEnabled();
            final boolean showGroupNotifications = prefController.getNotificationForGroupChatEnabled();
            final boolean showSingleNotifications = prefController.getNotificationForSingleChatEnabled();

            for (final Message message : messages) {
                try {
                    if (message == null || notificationController.wasNotificationShownForMessage(message.getGuid()) || StringUtil.isEqual("nopush", message.getPushInfo())) {
                        continue;
                    }

                    //// Schutz bei vielen ungelesenen Nachrichten
                    String chatGuid = MessageDataResolver.getGuidForMessage(message);

                    if (StringUtil.isNullOrEmpty(chatGuid)) {
                        continue;
                    }

                    if (chatMap.containsKey(chatGuid)) {
                        int msgCount = chatMap.get(chatGuid);
                        if (msgCount < 5) {
                            msgCount++;
                            chatMap.put(chatGuid, msgCount);
                        } else {
                            //mehr als 5 Msg pro Chat gibts nicht
                            continue;
                        }
                    } else {
                        chatMap.put(chatGuid, 1);
                    }
                    boolean isOwnMessage = StringUtil.isEqual(message.getFrom(), mAccountGuid);
                    if (isOwnMessage) {
                        continue;
                    }

                    final DecryptedMessage decryptedMessage = mContext.getMessageDecryptionController().decryptMessage(message, false);
                    if (decryptedMessage == null) {
                        continue;
                    }

                    final boolean isSystemInfo = (message.getIsSystemInfo() != null)
                            && (message.getIsSystemInfo());
                    final boolean isSentMessage = (message.getIsSentMessage() != null)
                            && (message.getIsSentMessage());
                    boolean isChannelMessage = false;

                    boolean isFirstContact = false;

                    if ((isSystemInfo) || (isSentMessage)) {
                        continue;
                    }

                    String preview = "";
                    String senderGuid = null;

                    switch (message.getType()) {
                        case Message.TYPE_CHANNEL: {
                            isChannelMessage = true;

                            if (GuidUtil.isChatChannel(message.getTo())) {
                                if (!showChannelNotifications || channelController.getDisableChannelNotification(message.getTo())) {
                                    continue;
                                }
                            } else {
                                if (!showServiceNotifications || channelController.getDisableChannelNotification(message.getTo())) {
                                    continue;
                                }
                            }

                            senderGuid = message.getTo();

                            break;
                        }
                        case Message.TYPE_GROUP: {
                            senderGuid = message.getTo();

                            Chat ch = groupController.getChatByGuid(senderGuid);
                            if (ch == null) {
                                continue;
                            }
                            // Bei Kanalnachrichten im BA
                            if (ch.getRoomType() == null) {
                                continue;
                            }
                            if (Chat.ROOM_TYPE_RESTRICTED.equals(ch.getRoomType())) {
                                if (!showChannelNotifications) {
                                    continue;
                                }
                            } else {
                                if (!showGroupNotifications) {
                                    continue;
                                }
                            }

                            break;
                        }
                        case Message.TYPE_PRIVATE: {
                            if (!showSingleNotifications) {
                                continue;
                            }

                            senderGuid = message.getFrom();
                            if (!StringUtil.isNullOrEmpty(senderGuid)) {
                                Contact contact = contactController.getContactByGuid(senderGuid);

                                if (contact != null) {
                                    if (contact.getIsFirstContact()) {
                                        isFirstContact = true;
                                    }
                                }
                            }

                            break;
                        }

                        default:
                            break;
                    }

                    String name = chatOverviewController.getNameForMessage(decryptedMessage);

                    String shortLinkText = null;

                    if (isChannelMessage) {
                        final String contentType = decryptedMessage.getContentType();

                        if (StringUtil.isEqual(contentType, MimeType.TEXT_PLAIN)) {
                            preview = decryptedMessage.getText();
                        } else if (StringUtil.isEqual(contentType, MimeType.IMAGE_JPEG)) {
                            preview = mContext.getResources().getString(R.string.chat_overview_preview_imageReceived);
                        }

                        Channel channel = channelController.getChannelFromDB(senderGuid);
                        if (channel != null) {
                            shortLinkText = channel.getShortLinkText();
                        }
                    } else {
                        if (isFirstContact) {
                            preview = mContext.getResources().getString(R.string.android_notification_new_private_msg_title);
                        } else {
                            preview = chatOverviewController.getPreviewTextForMessage(decryptedMessage, false);
                        }
                    }

                    final String contentType = decryptedMessage.getContentType();

                    if (StringUtil.isEqual(contentType, MimeType.TEXT_RSS)) {
                        name = null;
                    }

                    // Must keep special data if avc message - only if avc is present!
                    if (mAVChatController != null) {
                        if (StringUtil.isEqual(contentType, MimeType.TEXT_V_CALL)) {
                            shortLinkText = "AVC!";
                            preview = decryptedMessage.getAVCRoom();

                        }
                    }

                    Bitmap bitmap = bitmapCache.get(senderGuid);

                    if (bitmap == null) {
                        bitmap = chatOverviewController.getProfileImageForMessage(senderGuid);

                        if (bitmap != null) {
                            bitmapCache.put(senderGuid, bitmap);
                        }
                    }

                    final NotificationInfoContainer notificationInfo = new NotificationInfoContainer(
                            senderGuid,
                            bitmap,
                            name,
                            preview,
                            shortLinkText
                    );

                    notificationInfos.add(notificationInfo);

                    notificationController.setNotificationWasShown(message.getGuid());
                } catch (LocalizedException e) {
                    if (!e.getIdentifier().equals(LocalizedException.CHECK_SIGNATURE_FAILED)) {
                        throw e;
                    }
                }
            }
        } catch (LocalizedException e) {
            // Die Exception dürfte im normal Fall nur auftreten wenn die Schlüssel verworfen wurden.
            // Daher sollten alle Tasks für Ihre Laufzeit Schlüssel bekommen
            LogUtil.e(TAG, "run: Caught exception " + e.getMessage());
        }

        if (notificationInfos.size() > 0 /*&& mLastNotificationCount < notificationInfos.size()*/) {
            notificationController.showInternalNotification(notificationInfos, isPriority);
        }
        complete();
    }

    @Override
    public Object[] getResults() {
        return null;
    }
}
