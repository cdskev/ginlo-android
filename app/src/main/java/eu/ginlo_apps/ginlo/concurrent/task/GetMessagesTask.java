// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.concurrent.task;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.greenrobot.greendao.query.QueryBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import eu.ginlo_apps.ginlo.AVCallMenuActivity;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.greendao.MessageDao;
import eu.ginlo_apps.ginlo.greendao.MessageDao.Properties;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.AppGinloControlMessage;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.ConfirmReadReceipt;
import eu.ginlo_apps.ginlo.model.backend.MsgExceptionModel;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.service.BackendService;
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
    private final boolean mUseLazyMsgService;
    private final boolean mUseBgEndpoint;
    private final List<String> mNewSoundMessageGuids = new ArrayList<>();
    private final List<Message> mNewMessages = new ArrayList<>();
    private final SimsMeApplication mApplication;
    private final String mAccountGuid;
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
        mNewMessageFlags = -1;
        mUseLazyMsgService = useLazyMsgService;
        mOnlyPrioMsg = onlyPrioMsg;

        mUseBgEndpoint = useInBackground;
        mAccountGuid = application.getAccountController().getAccount().getAccountGuid();
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
            BackendService.withSyncConnection(mApplication).getNewMessagesFromBackground(listener);
        } else if (mOnlyPrioMsg) {
            BackendService.withSyncConnection(mApplication).getPrioMessages(listener);
        } else {
            BackendService.withSyncConnection(mApplication)
                .getNewMessages(listener, mUseLazyMsgService);
        }
    }

    private void logSentry(BackendResponse response) {
        String msg = String.format("[%s] Error @getNewMessages: %s  ", Thread.currentThread().getName(), response.errorMessage);
        LogUtil.i(TAG, msg);
        Exception ex = new Exception(msg);
        Sentry.capture(ex);
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
                            case MimeType.APP_GINLO_CONTROL:
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
                            case MimeType.TEXT_V_CALL:
                                // Don't push old AVC messages
                                final long now = new Date().getTime();
                                LogUtil.d(TAG, "AVC message from " + message.getDateSend() + ". Now: " + now);
                                if (message.getDateSend() + NotificationController.DISMISS_NOTIFICATION_TIMEOUT < now) {
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
            }
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
