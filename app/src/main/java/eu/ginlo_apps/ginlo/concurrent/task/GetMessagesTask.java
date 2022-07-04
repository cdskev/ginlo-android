// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.concurrent.task;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.greenrobot.greendao.query.QueryBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.controller.message.tasks.MessageConcurrentTaskListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.GreenDAOSecurityLayer;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.greendao.MessageDao;
import eu.ginlo_apps.ginlo.greendao.MessageDao.Properties;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.ConfirmReadReceipt;
import eu.ginlo_apps.ginlo.model.backend.MsgExceptionModel;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.MimeUtil;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.NotificationIntentService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.MessageDaoHelper;
import eu.ginlo_apps.ginlo.util.StringUtil;
import io.sentry.Sentry;

public class GetMessagesTask
    extends ConcurrentTask
{
    private final static String TAG = GetMessagesTask.class.getSimpleName();

    private final MessageController mMsgController;
    private final MessageDao mMessageDao;
    private final AttachmentController mAttachmentController;
    private final ChannelController mChannelController;
    private final NotificationController mNotificationController;
    private final ContactController mContactController;
    private final boolean mUseLazyMsgService;
    private final boolean mUseBgEndpoint;
    private final List<String> mNewSoundMessageGuids = new ArrayList<>();
    private final List<Message> mNewMessages = new ArrayList<>();
    private final SimsMeApplication mApplication;
    private final String mAccountGuid;
    private Map<String, String> mNotificationExtras;
    private int mNewMessageFlags;
    private boolean mProcessChannelMessages;
    private MsgExceptionModel mMsgExceptionModel;
    private List<Message> mLoadedMessagesInBg;
    private List<String> mSendMessages;
    private final Gson gson = new Gson();

    private final boolean mOnlyPrioMsg;

    public GetMessagesTask(
        final SimsMeApplication application,
        final boolean useLazyMsgService,
        final boolean useInBackground, boolean onlyPrioMsg
    ) {
        super();

        mApplication = application;
        mMsgController = application.getMessageController();
        mMessageDao = mMsgController.getDao();
        mAttachmentController = application.getAttachmentController();
        mChannelController = application.getChannelController();
        mNotificationController = application.getNotificationController();
        mContactController = application.getContactController();
        mNewMessageFlags = -1;
        mUseLazyMsgService = useLazyMsgService;
        mOnlyPrioMsg = onlyPrioMsg;

        mUseBgEndpoint = useInBackground;
        mAccountGuid = application.getAccountController().getAccount().getAccountGuid();
    }

    @Override
    public void addListener(ConcurrentTaskListener listener) {
        super.addListener(listener);
        if(listener instanceof MessageConcurrentTaskListener) {
            mNotificationExtras = ((MessageConcurrentTaskListener) listener).getNotificationExtras();
            LogUtil.d(TAG, "addListener: mNotificationExtras = " + mNotificationExtras);
        }
    }

    @Override
    public void run() {
        try {
            super.run();

            if (isCanceled()) {
                return;
            }

            if (!mUseBgEndpoint) {
                List<Message> messages = getMessagesNoDownloadDate();

                if (isCanceled()) {
                    return;
                }

                if ((messages != null) && (messages.size() > 0)) {
                    markMessagesAsDownloaded(messages);
                }
            }

            if (isCanceled()) {
                return;
            }

            getMessages();

            if (isCanceled()) {
                return;
            }

            List<Message> messages;

            if (mUseBgEndpoint) {
                messages = mLoadedMessagesInBg;
            } else {
                messages = getMessagesNoDownloadDate();
            }

            if (isCanceled()) {
                return;
            }

            if (((messages != null) && (messages
                .size() > 0)) || (mSendMessages != null && mSendMessages.size() > 0)) {
                markMessagesAsDownloaded(messages);
                if (mProcessChannelMessages) {
                    checkChannelMessages();
                }
            }

            complete();
        } catch (Exception e) {
            LogUtil.e(TAG, e.getMessage(), e);
            error();
        }
    }

    private void getMessages() {
        final IBackendService.OnBackendResponseListener listener =
            new IBackendService.OnBackendResponseListener() {
                @Override
                public void onBackendResponse(final BackendResponse response) {
                    if (response.isError) {
                        logSentry(response);
                        mMsgExceptionModel = response.msgException;
                        GetMessagesTask.this.error();
                    } else {
                        if (response.jsonArray != null && response.jsonArray.size() > 0) {
                            filterResponse(response.jsonArray);
                        } else if (response.jsonObject != null) {
                            JsonArray ja = new JsonArray();
                            ja.add(response.jsonObject);
                            filterResponse(ja);
                        }
                    }
                }
            };

        if (mUseBgEndpoint) {
            // KS: This is getting only Prio1Messages despite other flagging. Why?
            BackendService.withSyncConnection(mApplication).getNewMessagesFromBackground(listener);
        } else if (mOnlyPrioMsg) {
            BackendService.withSyncConnection(mApplication).getPrioMessages(listener);
        } else {
            BackendService.withSyncConnection(mApplication).getNewMessages(listener, mUseLazyMsgService);
        }
    }

    private void logSentry(BackendResponse response) {
        String msg = String.format("[%s] Error @getNewMessages: %s  ", Thread.currentThread().getName(), response.errorMessage);
        LogUtil.i(TAG, msg);
        Exception ex = new Exception(msg);
        Sentry.captureException(ex);
    }

    private void filterResponse(final JsonArray response) {
        if ((response == null) || (response.size() < 1)) {
            return;
        }

        final JsonArray jsonArray = new JsonArray();
        final List<String> chatGuids = new ArrayList<>();

        for (int i = 0; i < response.size(); i++) {
            JsonObject messageJsonContainer = response.get(i).getAsJsonObject();

            // timed messages confirm
            final JsonElement jElement = messageJsonContainer.get("ConfirmMessageSend");
            if (jElement != null) {
                final JsonElement sendGuid = ((JsonObject) jElement).get("sendGuid");
                final JsonElement guid = ((JsonObject) jElement).get("guid");

                if (sendGuid != null && !sendGuid.toString().equals("null") && guid != null) {
                    final String messageGuid = sendGuid.getAsString();
                    final Message message = mMsgController.getMessageByGuid(messageGuid);
                    if (message != null) {
                        mMsgController.resetMessageSendTimedDate(message);

                        String chatGuid = ChatController.getGuidForMessage(message);
                        if (!StringUtil.isNullOrEmpty(chatGuid)) {
                            chatGuids.add(chatGuid);
                        }

                        Message newMessage = new Message(message);
                        mMsgController.saveMessage(newMessage);
                        mMsgController.deleteMessage(message);
                    }

                    jsonArray.add(new JsonPrimitive(guid.getAsString()));
                }
            } else {
                // Regular message
                ConfirmReadReceipt internalMessage =
                    gson.fromJson(messageJsonContainer, ConfirmReadReceipt.class);

                if (internalMessage != null && internalMessage.isConfirmRead()) {
                    processReadReceipt(internalMessage.getInternalMessage());
                }

                Message message = parseMessage(messageJsonContainer);

                if(message != null) {
                    final String messageMimeType = message.getServerMimeType();

                    if (messageMimeType != null) {
                        LogUtil.i(TAG, "filterResponse: Message with messageMimeType " + messageMimeType + " received.");

                        switch (messageMimeType) {
                            case MimeUtil.MIME_TYPE_APP_GINLO_CONTROL:
                                // KS: if SHOW_AGC_MESSAGES is set we process APP_GINLO_CONTROL (AGC) messages
                                // and allow for saving them to the database for further processing, even if
                                // they are some sort of control messages.
                                LogUtil.d(TAG, "filterResponse: APP_GINLO_CONTROL - dismiss call notification.");
                                mNotificationController.cancelAVCallNotification();

                                if (BuildConfig.SHOW_AGC_MESSAGES) {
                                    // Prevent this message from being pushed
                                    message.setPushInfo("nopush");
                                    message.setRead(true);
                                } else {
                                    // Don't do further message processing
                                    message = null;
                                }
                                break;
                            case MimeUtil.MIME_TYPE_TEXT_V_CALL:
                                // Don't push old AVC messages
                                final long now = new Date().getTime();
                                LogUtil.d(TAG, "AVC message from " + message.getDateSend() + ". Now: " + now);
                                if (message.getDateSend() + NotificationController.AVC_NOTIFICATION_TIMEOUT < now) {
                                    LogUtil.d(TAG, "filterResponse: Expired AVC message - no notification!");
                                    message.setPushInfo("nopush");
                                    //message.setRead(true);
                                }
                                break;
                        }
                    }
                }

                if (message != null && message.getGuid() != null) {

                    processMessage(message);

                    if (message.getIsSentMessage() != null && message.getIsSentMessage()) {
                        if (mSendMessages == null) {
                            mSendMessages = new ArrayList<>(response.size());
                        }
                        mSendMessages.add(message.getGuid());

                        if (message.getDateSendConfirm() == null && message.getDateSend() != null) {
                            message.setDateSendConfirm(message.getDateSend());
                        }
                    }

                    if (mUseBgEndpoint) {
                        if (mLoadedMessagesInBg == null) {
                            mLoadedMessagesInBg = new ArrayList<>();
                        }

                        mLoadedMessagesInBg.add(message);
                    }
                }
            }
        }

        //ConfirmMessageSend Objekte muessen als gedownloaded markiert werden
        //Da sie nicht gespeichert werden, muss das an dieser Stelle getan werden
        if (jsonArray.size() > 0) {
            String state =
                mUseBgEndpoint ? AppConstants.MESSAGE_STATE_PREFETCHED_PERSISTENCE : AppConstants.MESSAGE_STATE_METADATA_DOWNLOADED;

            BackendService.withSyncConnection(mApplication)
                .setMessageState(jsonArray, state, null, mUseBgEndpoint);

            if (chatGuids.size() > 0 && !mUseBgEndpoint) {
                //TODO haesslich. Aus einem Thread einen Listener aufrufen
                mMsgController.notifyOnTimedMessagesDeliveredListeners(chatGuids);
            }
        }
    }

    private void processReadReceipt(ConfirmReadReceipt.InternalMessage readReceiptMessage) {
        if (readReceiptMessage == null) {
            LogUtil.w(TAG, "processReadReceipt: readReceiptMessage = null!");
            return;
        }

        if (!StringUtil.isEqual(readReceiptMessage.getFrom(), mAccountGuid)
            || readReceiptMessage.getMessageData() == null
            || readReceiptMessage.getMessageData().getConfirmRead() == null) {
            return;
        }

        String chatGuid = null;

        for (String messageGuid : readReceiptMessage.getMessageData().getConfirmRead()) {
            Message existingMessage = mMsgController.getMessageByGuid(messageGuid);
            if (existingMessage != null) {
                if (existingMessage.getRead() != null && existingMessage.getRead()) {
                    continue;
                }
                existingMessage.setRead(true);
                mMsgController.saveMessage(existingMessage);
                if (chatGuid == null) {
                    chatGuid = ChatController.getGuidForMessage(existingMessage);
                }
            }
        }
        if (chatGuid == null)
            return;

        mApplication.getChatOverviewController()
            .chatChanged(null, chatGuid, null, ChatOverviewController.CHAT_CHANGED_REFRESH_CHAT);
    }

    private void processMessage(final Message message) {
        if (mMsgController.messageExists(message.getGuid())) {
            LogUtil.i(TAG, "processMessage: Duplicate message:" + message.getGuid() + " -> " + message.getTo());
            //Nachricht wurde schon geladen
            Message oldMessage = mMsgController.getMessageByGuid(message.getGuid());

            if (oldMessage.getDateDownloaded() != null) {
                oldMessage.setDateDownloaded(null);
                mMsgController.saveMessage(oldMessage);
            }
        } else {
            LogUtil.i(TAG, "processMessage: Adding Message to Database:" + message.getGuid() + " -> " + message.getTo());

            mMsgController.saveMessage(message);

            //als letzte Msg an Chat haengen
            mMsgController.setLatestMessageToChat(message);

            if (message.getType() == Message.TYPE_CHANNEL) {
                mProcessChannelMessages = true;
            }

            String guid = null;
            switch (message.getType()) {
                case Message.TYPE_PRIVATE:
                    guid = message.getFrom();
                    break;
                case Message.TYPE_GROUP:
                case Message.TYPE_GROUP_INVITATION:
                case Message.TYPE_CHANNEL:
                    guid = message.getTo();
                    break;
                default:
                    // LogUtil.i(TAG, "Message.getType() returned: " + message.getType());
            }

            if (guid != null) {
                mNewSoundMessageGuids.add(guid);
                mNewMessages.add(message);
                // Trigger notification to user
                triggerMessageNotification(message, guid);
            }
        }
    }

    private void triggerMessageNotification(@NonNull Message message, String targetGuid) {

        final String messageGuid = message.getGuid();

        if(message.getPushInfo() == null || message.getPushInfo().contains("nopush")) {
            LogUtil.i(TAG, "triggerMessageNotification: NoPush flag set for " + messageGuid);
            return;
        }

        final String action = NotificationIntentService.ACTION_NEW_MESSAGES;
        final int numberOfMessages = mNewMessages.size();
        int newBadgeValue = 0;
        String senderGuid;
        String locArgs = "[\"-\"]";
        String sound = "default";
        String body = null;
        String badge = null;
        String accountGuid = targetGuid;
        String locKey = "";

        // Do we have (FCM) pre-fetched information about the current message? Use it!
        if(!mNotificationExtras.isEmpty() && mNotificationExtras.containsValue(messageGuid)) {
            LogUtil.d(TAG, "triggerMessageNotification: Use prefetched FCM infos for " + messageGuid);
            senderGuid = mNotificationExtras.get("senderGuid");
            locArgs = mNotificationExtras.get("loc-args");
            sound = mNotificationExtras.get("sound");
            body = mNotificationExtras.get("body");
            locKey = mNotificationExtras.get("loc-key");
            accountGuid = mNotificationExtras.get("accountGuid");

            final String tmpBadge = mNotificationExtras.get("badge");
            if(tmpBadge != null) {
                newBadgeValue = Integer.parseInt(tmpBadge);
            }
        } else {
            senderGuid = targetGuid;

            // Try to set correct loc-key
            switch (message.getType()) {
                case Message.TYPE_GROUP_INVITATION:
                    locKey = NotificationIntentService.LOC_KEY_GROUP_INV;
                    break;
                case Message.TYPE_CHANNEL:
                    locKey = NotificationIntentService.LOC_KEY_CHANNEL_MSG;
                    break;
                case Message.TYPE_PRIVATE:
                case Message.TYPE_GROUP:
                    locKey = NotificationIntentService.LOC_KEY_PRIVATE_MSG;
                    break;
                case Message.TYPE_PRIVATE_INTERNAL:
                default:
                    // No defined loc-key
                    break;
            }

            LogUtil.d(TAG, "triggerMessageNotification: No FCM infos, use targetGuid " + senderGuid);
            if(!StringUtil.isNullOrEmpty(senderGuid)) {
                try {
                    final Contact sender = mContactController.getContactByGuid(senderGuid);
                    if(sender != null) {
                        final String senderName = sender.getName();
                        locArgs = "[\"" + senderName + "\"]";
                    }
                } catch (LocalizedException e) {
                    LogUtil.w(TAG, "triggerMessageNotification: Could not get sender's name: " + e.getMessage());
                }
            }
        }

        // Get new message count
        badge = String.valueOf(Math.max(newBadgeValue, numberOfMessages));

        // Don't push old messages
        final long now = new Date().getTime();
        if (message.getDateSend() + NotificationController.OLD_MESSAGE_NOTIFICATION_TIMEOUT < now) {
            LogUtil.i(TAG, "triggerMessageNotification: Notification expiration exceeded for " + messageGuid);
            return;
        }

        // Own message from coupled device?
        if(StringUtil.isEqual(senderGuid, mAccountGuid)) {
            LogUtil.i(TAG, "triggerMessageNotification: Own message - no notification for " + messageGuid);
            return;
        }

        // Only prepare key and decrypt message if notification preview is enabled.
        if(mApplication.getPreferencesController() != null && mApplication.getPreferencesController().getNotificationPreviewEnabled()) {
            // Trying to decrypt message - if not possible, it's not for us!
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
                try {
                    // Prepare key for message decryption
                    final KeyController keyController = mApplication.getKeyController();
                    if (!keyController.getInternalKeyReady()) {
                        keyController.loadInternalEncryptionKeyForNotificationPreview();
                        GreenDAOSecurityLayer.init(keyController);
                        keyController.reloadUserKeypairFromAccount();
                    }
                } catch (final LocalizedException | IOException e) {
                    LogUtil.e(TAG, "triggerMessageNotification: Failed to prepare decryption key for message preview: " + e.getMessage());
                    return;
                }
            }

            // Persist decrypted message infos in NotificationController to avoid multiple decryption in further notification processing.
            final DecryptedMessage decryptedMessage = mApplication.getMessageDecryptionController().decryptMessage(message, false);
            if (decryptedMessage == null) {
                LogUtil.w(TAG, "triggerMessageNotification: Message " + messageGuid + " cannot be decrypted! Don't notify user.");
                return;
            }
            mNotificationController.addDecryptedMessageInfo(decryptedMessage);
        } else {
            LogUtil.i(TAG, "triggerMessageNotification: Notification preview is disabled by the user.");
        }

        // Suppress notification when in same chat - except AVC
        if(StringUtil.isEqual(targetGuid, mNotificationController.getCurrentChatGuid())
                && !StringUtil.isEqual(message.getServerMimeType(), MimeUtil.MIME_TYPE_TEXT_V_CALL)) {
            LogUtil.d(TAG, "triggerMessageNotification: Suppress message notification for current chat " + targetGuid);
            return;
        }

        LogUtil.i(TAG, "triggerMessageNotification: Trigger message notification for " + messageGuid + " with locKey = " + locKey);

        Intent intent = new Intent(mApplication, NotificationIntentService.class);
        intent.putExtra("accountGuid", accountGuid);
        intent.putExtra("messageGuid", messageGuid);
        intent.putExtra("action", action);
        intent.putExtra("loc-key", locKey);
        intent.putExtra("senderGuid", senderGuid);
        intent.putExtra("badge", badge);
        intent.putExtra("loc-args", locArgs);
        intent.putExtra("sound", sound);
        if(!StringUtil.isNullOrEmpty(body)) {
            intent.putExtra("body", body);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (NotificationIntentService.haveToStartAsForegroundService(mApplication, intent)) {
                LogUtil.d(TAG, "triggerMessageNotification: haveToStartAsForegroundService true");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    LogUtil.d(TAG, "triggerMessageNotification: Start NotificationIntentService as foreground service.");
                    mApplication.startForegroundService(intent);
                } else {
                    LogUtil.d(TAG, "triggerMessageNotification: Start NotificationIntentService.");
                    mApplication.startService(intent);
                }
            } else {
                LogUtil.d(TAG, "triggerMessageNotification: showNotification");
                NotificationIntentService.showNotification(mApplication, intent);
            }
        } else {
            LogUtil.d(TAG, "triggerMessageNotification: Start NotificationIntentService.");
            mApplication.startService(intent);
        }
    }

    private void checkChannelMessages() {
        List<Channel> channelList = mChannelController.getChannelsFromDB(Channel.TYPE_ALL);

        if (channelList == null) {
            return;
        }

        for (Channel channel : channelList) {
            List<Message> messages;
            synchronized (mMessageDao) {
                final QueryBuilder<Message> queryBuilder = mMessageDao.queryBuilder();

                queryBuilder.where(Properties.To.eq(channel.getGuid()))
                    .orderDesc(Properties.DateSend);

                messages = queryBuilder.list();
            }

            if (messages.size() > 0) {
                final Date now = new Date();
                boolean deleteFromHere = false;

                for (int position = 0; position < messages.size(); ++position) {
                    Message message = messages.get(position);

                    if (!deleteFromHere) {
                        if ((position >= mChannelController.getDeleteMessageMaxCount())
                            || (
                            (now.getTime() - message.getDateSend()) > mChannelController
                                .getDeleteMessageMaxMillisecs()
                        )) {
                            deleteFromHere = true;
                        }
                    }

                    if (deleteFromHere) {
                        if (message.getAttachment() != null) {
                            mAttachmentController.deleteAttachment(message.getAttachment());
                        }
                        mApplication.getMessageController().deleteMessage(message);
                    }
                }
            }
        }
        mProcessChannelMessages = false;
    }

    private Message parseMessage(final JsonObject messageJsonContainer) {

        // Deserialize message
        Message message =
            MessageDaoHelper.getInstance(mApplication).buildMessageFromJson(messageJsonContainer);

        if (message != null) {
            MessageController.checkAndSetSendMessageProps(message, mAccountGuid);

            if (message.getType() != -1) {
                if (mNewMessageFlags == -1) {
                    mNewMessageFlags = AppConstants.getNewMessageFlag(message.getType());
                } else {
                    mNewMessageFlags =
                        mNewMessageFlags | AppConstants.getNewMessageFlag(message.getType());
                }
            }
        }

        return message;
    }

    private List<Message> getMessagesNoDownloadDate() {
        List<Message> messages;

        synchronized (mMessageDao) {
            final QueryBuilder<Message> queryBuilder = mMessageDao.queryBuilder();

            if (mUseBgEndpoint) {
                queryBuilder.where(Properties.DateDownloaded.isNull(),
                    Properties.DatePrefetchedPersistence.isNull(),
                    Properties.IsSentMessage.eq(false)
                );
            } else {
                queryBuilder
                    .where(Properties.DateDownloaded.isNull(), Properties.IsSentMessage.eq(false));
            }
            messages = queryBuilder.build().forCurrentThread().list();
        }

        return messages;
    }

    private void markMessagesAsDownloaded(List<Message> messages) {
        final List<Message> markedMessages = new ArrayList<>();
        final JsonArray jsonArray = new JsonArray();

        if (messages != null) {
            for (final Message message : messages) {
                if (message.getDateDownloaded() != null || message.getGuid() == null) {
                    continue;
                }

                if (!message.getIsSentMessage()
                    && ((message.getIsSystemInfo() == null) || !message.getIsSystemInfo())) {
                    jsonArray.add(new JsonPrimitive(message.getGuid()));
                }

                if (mUseBgEndpoint) {
                    message.setDatePrefetchedPersistence(new Date().getTime());
                } else {
                    long newDate = new Date().getTime();
                    message.setDateDownloaded(newDate);

                    if (message.getDatePrefetchedPersistence() == null) {
                        message.setDatePrefetchedPersistence(newDate);
                    }
                }

                markedMessages.add(message);
            }
        }

        if (mSendMessages != null && mSendMessages.size() > 0) {
            for (String guid : mSendMessages) {
                jsonArray.add(new JsonPrimitive(guid));
            }
        }

        if (jsonArray.size() > 0) {
            final IBackendService.OnBackendResponseListener listener =
                new IBackendService.OnBackendResponseListener() {
                    @Override
                    public void onBackendResponse(final BackendResponse response) {
                        if (response.isError) {
                            mMsgExceptionModel = response.msgException;
                            GetMessagesTask.this.error();
                        } else {
                            savedMarkedMessages(markedMessages);
                        }
                    }
                };

            String state =
                mUseBgEndpoint ? AppConstants.MESSAGE_STATE_PREFETCHED_PERSISTENCE : AppConstants.MESSAGE_STATE_METADATA_DOWNLOADED;

            BackendService.withSyncConnection(mApplication)
                .setMessageState(jsonArray, state, listener, mUseBgEndpoint);
        }
    }

    private void savedMarkedMessages(final List<Message> markedMessages) {
        for (final Message message : markedMessages) {
            mMsgController.saveMessage(message);
        }
    }

    @Override
    public Object[] getResults() {
        if (isError()) {
            if (mMsgExceptionModel != null) {
                return new Object[]
                    {
                        mMsgExceptionModel
                    };
            } else {
                return null;
            }
        } else {
            return new Object[]
                {
                    mNewMessageFlags,
                    mNewSoundMessageGuids,
                    mNewMessages
                };
        }
    }
}
