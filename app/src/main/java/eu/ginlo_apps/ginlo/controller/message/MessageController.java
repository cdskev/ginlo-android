// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message;

import android.content.ComponentCallbacks2;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.query.QueryBuilder;
import org.greenrobot.greendao.query.WhereCondition;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.manager.MessageTaskManager;
import eu.ginlo_apps.ginlo.concurrent.manager.SerialExecutor;
import eu.ginlo_apps.ginlo.concurrent.task.AsyncHttpTask;
import eu.ginlo_apps.ginlo.concurrent.task.AsyncHttpTask.AsyncHttpCallback;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.concurrent.task.QueryDatabaseTask;
import eu.ginlo_apps.ginlo.concurrent.task.RefreshChatOverviewTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.LoginController.AppLockLifecycleCallbacks;
import eu.ginlo_apps.ginlo.controller.contracts.AppLifecycleCallbacks;
import eu.ginlo_apps.ginlo.controller.contracts.LowMemoryCallback;
import eu.ginlo_apps.ginlo.controller.message.MessageDataResolver;
import eu.ginlo_apps.ginlo.controller.message.contracts.BuildMessageCallback;
import eu.ginlo_apps.ginlo.controller.message.contracts.MessageControllerListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnDeleteTimedMessageListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnMessageReceivedListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnMessageReceiverChangedListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnSendMessageListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnTimedMessagesDeliveredListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.QueryDatabaseListener;
import eu.ginlo_apps.ginlo.controller.message.models.AsyncTaskResult;
import eu.ginlo_apps.ginlo.controller.message.tasks.LoadPendingMessagesTask;
import eu.ginlo_apps.ginlo.controller.message.tasks.MessageConcurrentTaskListener;
import eu.ginlo_apps.ginlo.controller.message.tasks.SendMessageTask;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.DaoMaster;
import eu.ginlo_apps.ginlo.greendao.DaoSession;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.greendao.MessageDao;
import eu.ginlo_apps.ginlo.greendao.MessageDao.Properties;
import eu.ginlo_apps.ginlo.greendao.NewDestructionDate;
import eu.ginlo_apps.ginlo.greendao.NewDestructionDateDao;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.BaseMessageModel;
import eu.ginlo_apps.ginlo.model.backend.ChannelMessageModel;
import eu.ginlo_apps.ginlo.model.backend.ConfirmMessageSendModel;
import eu.ginlo_apps.ginlo.model.backend.GroupInvMessageModel;
import eu.ginlo_apps.ginlo.model.backend.GroupMessageModel;
import eu.ginlo_apps.ginlo.model.backend.InternalMessageModel;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;
import eu.ginlo_apps.ginlo.model.backend.MessageReceiverModel;
import eu.ginlo_apps.ginlo.model.backend.MsgExceptionModel;
import eu.ginlo_apps.ginlo.model.backend.PrivateInternalMessageModel;
import eu.ginlo_apps.ginlo.model.backend.PrivateMessageModel;
import eu.ginlo_apps.ginlo.model.backend.ResponseModel;
import eu.ginlo_apps.ginlo.model.backend.SignatureModel;
import eu.ginlo_apps.ginlo.model.backend.action.ConfirmV1Action;
import eu.ginlo_apps.ginlo.model.backend.serialization.ChannelMessageModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ChannelMessageModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ConfirmMessageSendModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.GroupInvMessageModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.GroupInvMessageModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.GroupMessageModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.GroupMessageModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.InternalMessageModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.KeyContainerModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.KeyContainerModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.MessageDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.PrivateInternalMessageModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.PrivateInternalMessageModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.PrivateMessageModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.PrivateMessageModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.SignatureModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.SignatureModelSerializer;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.services.LoadPendingTimedMessagesTask;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.MessageDaoHelper;
import eu.ginlo_apps.ginlo.util.StreamUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import me.leolin.shortcutbadger.ShortcutBadgeException;
import me.leolin.shortcutbadger.ShortcutBadger;

public class MessageController
        implements AppLockLifecycleCallbacks,
        AppLifecycleCallbacks, LowMemoryCallback {

    private static final String TAG = MessageController.class.getSimpleName();
    private static final SerialExecutor SEND_MSG_SERIAL_EXECUTOR = new SerialExecutor();
    private static final SerialExecutor GET_SINGLE_MSG_SERIAL_EXECUTOR = new SerialExecutor();

    public static final String TYPE_PRIVATE_INTERNAL_MESSAGE = "PrivateInternalMessage";
    public static final String TYPE_PRIVATE_MESSAGE = "PrivateMessage";
    public static final String TYPE_INTERNAL_MESSAGE = "InternalMessage";
    public static final String TYPE_GROUP_MESSAGE = "GroupMessage";
    public static final String TYPE_GROUP_INVITATION_MESSAGE = "GroupInvMessage";
    public static final String TYPE_CHANNEL_MESSAGE = "ChannelMessage";
    public static final String TYPE_SERVICE_MESSAGE = "ServiceMessage";
    public static final String TYPE_PRIVATE_TIMED_MESSAGE = "TimedPrivateMessage";
    public static final String TYPE_GROUP_TIMED_MESSAGE = "TimedGroupMessage";

    private final MessageDao messageDao;
    private final NewDestructionDateDao newDestructionDateDao;
    private final MessageTaskManager messageTaskManager;
    private final ArrayList<MessageControllerListener> listenerMap;
    private final SimsMeApplication mApplication;
    public final Gson gson;
    private final ArrayList<OnMessageReceivedListener> mOnMessageReceivedListeners;
    private final List<Long> mActiveSendingMessageIds = new ArrayList<>();
    private IBackendService mSendMsgBackendService;
    private ConcurrentTask getMessageTask;
    private Timer refreshTimer;
    private boolean mStartGetMessageTaskAgain;
    private boolean mStopGetMessageTask;
    private String mMessageDeviceToken;
    private OnSendMessageListener mChatOverviewMessageListener;
    private ArrayList<OnTimedMessagesDeliveredListener> mOnTimedMessagesDeliveredListenersList;
    private List<OnMessageReceiverChangedListener> mOnMsgReceiverChangedListeners;

    private LoadPendingMessagesTask mLoadPendingMessagesTask;

    public MessageController(final SimsMeApplication application) {
        this.listenerMap = new ArrayList<>();
        this.messageTaskManager = new MessageTaskManager();
        mOnMessageReceivedListeners = new ArrayList<>();

        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(PrivateMessageModel.class, new PrivateMessageModelSerializer());
        gsonBuilder.registerTypeAdapter(PrivateMessageModel.class, new PrivateMessageModelDeserializer());
        gsonBuilder.registerTypeAdapter(PrivateInternalMessageModel.class, new PrivateInternalMessageModelSerializer());
        gsonBuilder.registerTypeAdapter(PrivateInternalMessageModel.class, new PrivateInternalMessageModelDeserializer());
        gsonBuilder.registerTypeAdapter(GroupMessageModel.class, new GroupMessageModelSerializer());
        gsonBuilder.registerTypeAdapter(GroupMessageModel.class, new GroupMessageModelDeserializer());
        gsonBuilder.registerTypeAdapter(GroupInvMessageModel.class, new GroupInvMessageModelSerializer());
        gsonBuilder.registerTypeAdapter(GroupInvMessageModel.class, new GroupInvMessageModelDeserializer());
        gsonBuilder.registerTypeAdapter(KeyContainerModel.class, new KeyContainerModelSerializer());
        gsonBuilder.registerTypeAdapter(KeyContainerModel.class, new KeyContainerModelDeserializer());
        gsonBuilder.registerTypeAdapter(SignatureModel.class, new SignatureModelSerializer());
        gsonBuilder.registerTypeAdapter(SignatureModel.class, new SignatureModelDeserializer());
        gsonBuilder.registerTypeAdapter(InternalMessageModel.class, new InternalMessageModelDeserializer());
        gsonBuilder.registerTypeAdapter(ConfirmMessageSendModel.class, new ConfirmMessageSendModelDeserializer());
        gsonBuilder.registerTypeAdapter(ChannelMessageModel.class, new ChannelMessageModelDeserializer());
        gsonBuilder.registerTypeAdapter(ChannelMessageModel.class, new ChannelMessageModelSerializer());
        gsonBuilder.registerTypeAdapter(Message.class, new MessageDeserializer(application.getAccountController()));
        gson = gsonBuilder.create();

        final Database db = application.getDataBase();
        final DaoMaster daoMaster = new DaoMaster(db);
        final DaoSession daoSession = daoMaster.newSession();

        messageDao = daoSession.getMessageDao();
        newDestructionDateDao = daoSession.getNewDestructionDateDao();
        NewDestructionDateDao.createTable(db, true);

        mApplication = application;

        // Als Callback registrieren
        mApplication.getLoginController().registerAppLockLifecycleCallbacks(this);
        mApplication.getAppLifecycleController().registerAppLifecycleCallbacks(this);
        mApplication.getAppLifecycleController().registerLowMemoryCallback(this);
    }

    public static void checkAndSetSendMessageProps(Message msg, String accountGuid) {
        if (StringUtil.isEqual(msg.getFrom(), accountGuid)) {
            msg.setIsSentMessage(true);
            //Internal messages must be processed
            if (msg.getType() == -1 || (msg.getType() != Message.TYPE_INTERNAL && msg.getType() != Message.TYPE_GROUP_INVITATION)) {
                msg.setRead(true);
            }
        }

        if (msg.isSystemInfo()) {
            msg.setRead(true);
        }
    }

    public void addOnMessageReceivedListener(final OnMessageReceivedListener listener) {
        mOnMessageReceivedListeners.add(listener);
    }

    public void removeOnOnMessageReceivedListener(final OnMessageReceivedListener listener) {
        mOnMessageReceivedListeners.remove(listener);
    }

    public String getMessageDeviceToken() {
        if (mMessageDeviceToken == null) {
            mMessageDeviceToken = mApplication.getPreferencesController().getFetchInBackgroundAccessToken();
        }
        return mMessageDeviceToken;
    }

    public void setMessageDeviceToken(String newToken) {
        mMessageDeviceToken = newToken;
        mApplication.getPreferencesController().setFetchInBackgroundAccessToken(mMessageDeviceToken);
    }

    void setMessageGuid(final Message message, final String newGuid) {
        message.setGuid(newGuid);
        synchronized (messageDao) {
            messageDao.update(message);
        }
    }

    public void resetMessageSendTimedDate(final Message message) {
        synchronized (messageDao) {
            message.setDateSend(message.getDateSendTimed());
            message.setDateSendTimed(null);
            messageDao.update(message);
        }
    }

    public void addListener(final MessageControllerListener listener) {
        listenerMap.add(listener);
    }

    public void addOnMessageReceiverChangedListener(final OnMessageReceiverChangedListener listener) {
        if (mOnMsgReceiverChangedListeners == null) {
            mOnMsgReceiverChangedListeners = new ArrayList<>();
        }

        mOnMsgReceiverChangedListeners.add(listener);
    }

    public void removeOnMessageReceiverChangedListener(final OnMessageReceiverChangedListener listener) {
        if (mOnMsgReceiverChangedListeners != null) {
            mOnMsgReceiverChangedListeners.remove(listener);
        }
    }

    void checkForResend(final String senderId,
                        final IBackendService.OnBackendResponseListener listener) {
        BackendService.withAsyncConnection(mApplication).isMessageSend(senderId, listener);
    }

    void deleteTimedMessagesByGuid(final String chatGuid, final OnDeleteTimedMessageListener onDeleteTimedMessageListener) {
        List<Message> messages;

        int type;
        if (GuidUtil.isChatSingle(chatGuid)) {
            type = 1;
        } else if (GuidUtil.isChatRoom(chatGuid)) {
            type = 3;
        } else {
            type = 0;
        }

        synchronized (messageDao) {
            final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

            queryBuilder.whereOr(Properties.From.eq(chatGuid), Properties.To.eq(chatGuid)).where(Properties.DateSendTimed.isNotNull());
            if (type != 0) {
                queryBuilder.where(Properties.Type.eq(type));
            }
            messages = queryBuilder.list();
        }

        if (messages != null && messages.size() != 0) {
            IBackendService.OnBackendResponseListener OnBackendResponseListener = new IBackendService.OnBackendResponseListener() {

                @Override
                public void onBackendResponse(BackendResponse response) {
                    if (!response.isError) {

                        if (response.jsonArray != null && response.jsonArray.size() != 0) {
                            for (final JsonElement element : response.jsonArray) {
                                final Message message = getMessageByGuid(element.getAsString());
                                if (message != null) {
                                    deleteMessage(message);
                                }
                            }
                            onDeleteTimedMessageListener.onDeleteAllMessagesSuccess(chatGuid);
                        } else {
                            final String errorMsg = response.errorMessage != null ? response.errorMessage
                                    : mApplication.getString(R.string.delete_timed_messages_error);
                            onDeleteTimedMessageListener.onDeleteMessageError(errorMsg);
                        }
                    } else {
                        final String errorMsg = response.errorMessage != null ? response.errorMessage
                                : mApplication.getString(R.string.delete_timed_messages_error);
                        onDeleteTimedMessageListener.onDeleteMessageError(errorMsg);
                    }
                }
            };

            final int messagesSize = messages.size();

            final StringBuilder messageGuids = new StringBuilder();
            for (int i = 0; i < messages.size(); ++i) {
                final Message message = messages.get(i);
                if (!StringUtil.isNullOrEmpty(message.getGuid())) {
                    messageGuids.append(message.getGuid());
                    if (i < messagesSize - 1) {
                        messageGuids.append(",");
                    }
                }
            }
            BackendService.withAsyncConnection(mApplication)
                    .removeTimedMessageBatch(messageGuids.toString(), OnBackendResponseListener);
        } else {
            onDeleteTimedMessageListener.onDeleteAllMessagesSuccess(chatGuid);
        }
    }

    void deleteAllMessagesByGuid(final String chatGuid) {
        List<Message> messages;

        int type;
        if (GuidUtil.isChatSingle(chatGuid)) {
            type = 1;
        } else if (GuidUtil.isChatRoom(chatGuid)) {
            type = 3;
        } else {
            type = 0;
        }

        synchronized (messageDao) {
            final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

            queryBuilder.whereOr(Properties.From.eq(chatGuid), Properties.To.eq(chatGuid));
            if (type != 0) {
                queryBuilder.where(Properties.Type.eq(type));
            }
            messages = queryBuilder.list();
        }

        if (messages != null) {
            for (final Message message : messages) {
                deleteMessage(message);
            }
        }
    }

    void deleteNonTimedMessagesByGuid(final String chatGuid) {
        List<Message> messages = getNonTimedMessagesByGuid(chatGuid);
        for (Message m : messages) {
            deleteMessage(m);
        }
    }

    List<Message> getNonTimedMessagesByGuid(final String chatGuid) {
        List<Message> messages;

        int type;
        if (GuidUtil.isChatSingle(chatGuid)) {
            type = 1;
        } else if (GuidUtil.isChatRoom(chatGuid)) {
            type = 3;
        } else {
            type = 0;
        }

        synchronized (messageDao) {
            final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

            queryBuilder.whereOr(Properties.From.eq(chatGuid), Properties.To.eq(chatGuid)).where(Properties.DateSendTimed.isNull());
            if (type != 0) {
                queryBuilder.where(Properties.Type.eq(type));
            }
            messages = queryBuilder.list();
        }

        return messages;
    }

    public void deleteMessage(final Message message) {
        if(message == null) {
            LogUtil.w(TAG, "deleteMessage called with message = null!");
            return;
        }

        synchronized (messageDao) {
            messageDao.delete(message);
        }

        switch (message.getType()) {
            case Message.TYPE_GROUP:
            case Message.TYPE_CHANNEL:
            case Message.TYPE_PRIVATE:
            case Message.TYPE_GROUP_INVITATION:
                final String guid = message.getGuid();
                if (!StringUtil.isNullOrEmpty(guid)) {
                    mApplication.getNotificationController().deleteNotification(guid);
                }
                break;
        }
    }

    List<Message> getMessagesWithSendErrorByType() {
        synchronized (messageDao) {
            final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

            queryBuilder.where(Properties.Type.eq(Message.TYPE_PRIVATE_INTERNAL), Properties.HasSendError.eq(true));
            return queryBuilder.list();
        }
    }

    public MessageDao getDao() {
        return messageDao;
    }

    public Message getMessageByGuid(final String guid) {
        final List<Message> messages = getMessagesByGuids(new String[]{guid});

        if (messages.size() > 0) {
            return messages.get(0);
        } else {
            return null;
        }
    }

    public Message getMessageById(final long id) {
        synchronized (messageDao) {
            return messageDao.load(id);
        }
    }

    private List<Message> getMessagesByGuids(final String[] guids) {

        if (guids == null || guids.length <= 0) {
            return null;
        }

        final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

        if (guids.length == 1) {
            synchronized (messageDao) {
                queryBuilder.where(Properties.Guid.eq(guids[0]));
                return queryBuilder.list();
            }
        } else if (guids.length <= 20) {
            synchronized (messageDao) {
                final String[] moreGuids = new String[guids.length - 2];

                System.arraycopy(guids, 2, moreGuids, 0, moreGuids.length);

                final WhereCondition[] moreConditions = new WhereCondition[moreGuids.length];

                for (int i = 0; i < moreGuids.length; i++) {
                    moreConditions[i] = Properties.Guid.eq(moreGuids[i]);
                }

                queryBuilder.whereOr(Properties.Guid.eq(guids[0]), Properties.Guid.eq(guids[1]), moreConditions);
                return queryBuilder.list();
            }
        } else {
            int i = 0;
            List<Message> messages = new ArrayList<>(guids.length);
            while (i < guids.length) {
                int arraySize = (i + 20 < guids.length) ? 20 : (guids.length - i);
                final String[] nextGuids = new String[arraySize];
                System.arraycopy(guids, i, nextGuids, 0, nextGuids.length);

                List<Message> nextMessages = getMessagesByGuids(nextGuids);
                if (nextMessages != null && nextMessages.size() > 0) {
                    messages.addAll(nextMessages);
                }

                i = i + nextGuids.length;
            }

            return messages;
        }
    }

    public Message getMessageByRequestGuid(final String guid) {
        synchronized (messageDao) {
            final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

            queryBuilder.where(Properties.RequestGuid.eq(guid));
            //Message absteigend sortiert nach Id
            queryBuilder.orderDesc(Properties.Id);

            List<Message> items = queryBuilder.build().forCurrentThread().list();

            return items.size() > 0 ? items.get(0) : null;
        }
    }

    void getUnreadMessagesByContactGuid(final String guid,
                                        final int type,
                                        final QueryDatabaseListener queryDatabaseListener) {
        final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

        queryBuilder.whereOr(Properties.Read.eq(false), Properties.Read.isNull())
                .whereOr(Properties.To.eq(guid), Properties.From.eq(guid)).where(Properties.Type.eq(type)).orderDesc(Properties.Id);

        messageTaskManager.executeQueryDatabaseTask(queryBuilder, QueryDatabaseTask.MODE_LIST, queryDatabaseListener);
    }

    void getLatestMessagesByContactGuid(final String guid,
                                        final int type,
                                        final long lastMsgIdFromAdapter,
                                        final QueryDatabaseListener queryDatabaseListener) {
        final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

        if (lastMsgIdFromAdapter > -1) {
            queryBuilder.where(Properties.Id.gt(lastMsgIdFromAdapter));
        }

        queryBuilder.whereOr(Properties.To.eq(guid), Properties.From.eq(guid))
                .where(Properties.Type.eq(type)).orderDesc(Properties.Id);

        messageTaskManager.executeQueryDatabaseTask(queryBuilder, QueryDatabaseTask.MODE_LIST, queryDatabaseListener);
    }

    public List<Message> getUnreadMessagesForNotification() {
        synchronized (messageDao) {
            final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

            queryBuilder.whereOr(Properties.Read.eq(false), Properties.Read.isNull())
                    .where(Properties.IsSentMessage.eq(false))
                    .whereOr(Properties.Type.eq(Message.TYPE_PRIVATE), Properties.Type.eq(Message.TYPE_GROUP), Properties.Type.eq(Message.TYPE_CHANNEL))
                    .orderDesc(Properties.Id);

            return queryBuilder.build().forCurrentThread().list();
        }
    }

    public List<Message> getUnreadInternalMessages() {
        synchronized (messageDao) {
            final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

            return queryBuilder.whereOr(Properties.Read.eq(false), Properties.Read.isNull())
                    .where(Properties.Type.eq(Message.TYPE_INTERNAL)).orderDesc(Properties.Id).list();
        }
    }

    public void deleteAllReadInternalMessages() {
        synchronized (messageDao) {
            final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();
            queryBuilder.where(Properties.Read.eq(true), Properties.Type.eq(Message.TYPE_INTERNAL))
                    .buildDelete().executeDeleteWithoutDetachingEntities();
        }
    }

    void countTimedMessages(final String chatGuid,
                            final QueryDatabaseListener queryDatabaseListener) {

        final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

        queryBuilder.whereOr(Properties.From.eq(chatGuid), Properties.To.eq(chatGuid)).where(Properties.DateSendTimed.isNotNull()).count();

        messageTaskManager.executeQueryDatabaseTask(queryBuilder, QueryDatabaseTask.MODE_COUNT, queryDatabaseListener);
    }

    public void loadAllMessages(final String chatGuid,
                                final int type,
                                final boolean sortDesc,
                                final QueryDatabaseListener queryDatabaseListener) {
        final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

        queryBuilder.where(Properties.Type.eq(type)).whereOr(Properties.From.eq(chatGuid), Properties.To.eq(chatGuid));
        if (sortDesc) {
            queryBuilder.orderDesc(Properties.Id).list();
        } else {
            queryBuilder.orderAsc(Properties.Id).list();
        }

        messageTaskManager.executeQueryDatabaseTask(queryBuilder, QueryDatabaseTask.MODE_LIST, queryDatabaseListener);
    }

    void loadMessageRange(final String chatGuid,
                          final int type,
                          final Long start,
                          final Long end,
                          final QueryDatabaseListener queryDatabaseListener
    ) {
        final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

        queryBuilder.where(Properties.Type.eq(type))
                .whereOr(Properties.From.eq(chatGuid), Properties.To.eq(chatGuid))
                .where(Properties.DateSendTimed.isNull())
                .where(Properties.Id.ge(start))
                .where(Properties.Id.lt(end))
                .orderDesc(Properties.Id);

        messageTaskManager.executeQueryDatabaseTask(queryBuilder, QueryDatabaseTask.MODE_LIST, queryDatabaseListener);
    }

    void loadNextMessages(final String chatGuid,
                          final int type,
                          final int count,
                          final long minMaxId,
                          final boolean loadLessThanMinMaxId,
                          final boolean loadTimed,
                          final QueryDatabaseListener queryDatabaseListener) {
        final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

        queryBuilder.where(Properties.Type.eq(type)).whereOr(Properties.From.eq(chatGuid), Properties.To.eq(chatGuid));

        if (minMaxId != -1) {
            if (loadLessThanMinMaxId) {
                queryBuilder.where(Properties.Id.lt(minMaxId));
            } else {
                queryBuilder.where(Properties.Id.gt(minMaxId));
            }
        }
        if (loadTimed) {
            queryBuilder.where(Properties.DateSendTimed.isNotNull()).orderAsc(Properties.DateSendTimed).limit(count);
        } else {
            queryBuilder.where(Properties.DateSendTimed.isNull()).orderDesc(Properties.Id).limit(count);
        }

        messageTaskManager.executeQueryDatabaseTask(queryBuilder, QueryDatabaseTask.MODE_LIST, queryDatabaseListener);
    }

    public Message loadLastChatMessage(final String chatGuid,
                                       final int type) {
        synchronized (messageDao) {
            final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

            queryBuilder.where(Properties.Type.eq(type)).whereOr(Properties.IsSystemInfo.isNull(), Properties.IsSystemInfo.eq(false))
                    .whereOr(Properties.From.eq(chatGuid), Properties.To.eq(chatGuid))
                    .orderDesc(Properties.Id).limit(1);

            List<Message> msgList = queryBuilder.build().forCurrentThread().list();

            if (msgList != null && msgList.size() > 0) {
                return msgList.get(0);
            }
        }
        return null;
    }

    public long getNumNotReadMessagesByGuid(final String guid,
                                            final int type) {
        synchronized (messageDao) {
            final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

            return queryBuilder.whereOr(Properties.Read.isNull(), Properties.Read.eq(false))
                    .whereOr(Properties.From.eq(guid), Properties.To.eq(guid))
                    .where(Properties.IsSentMessage.eq(false)).where(Properties.Type.eq(type)).count();
        }
    }

    long getNumMessagesByChatGuid(final String guid,
                                  final int type,
                                  final boolean includeTimed) {
        synchronized (messageDao) {
            final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

            queryBuilder.whereOr(Properties.From.eq(guid), Properties.To.eq(guid)).where(Properties.Type.eq(type));

            if (!includeTimed) {
                queryBuilder.where(Properties.DateSendTimed.isNull());
            }

            return queryBuilder.count();
        }
    }

    public boolean messageExists(final String guid) {
        synchronized (messageDao) {
            final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

            return queryBuilder.where(Properties.Guid.eq(guid)).count() > 0;
        }
    }

    public void setLatestMessageToChat(final Message message) {
        if (message == null || message.getId() == null || message.isSystemInfo()) {
            return;
        }

        String guid = null;

        if (message.getType() == Message.TYPE_GROUP || message.getType() == Message.TYPE_CHANNEL) {
            guid = MessageDataResolver.getGroupGuidForMessage(message);
        } else if (message.getType() == Message.TYPE_PRIVATE) {
            guid = MessageDataResolver.getGuidForMessage(message);
        }

        final Chat chat;

        if (!StringUtil.isNullOrEmpty(guid)) {
            chat = mApplication.getChannelChatController().getChatByGuid(guid);

            if (chat != null) {
                setLatestMessageToChat(message, chat, true);
            }
        } else {
            chat = null;
        }

        if (chat == null) {
            if (message.getType() == Message.TYPE_PRIVATE && !StringUtil.isNullOrEmpty(guid)) {
                Chat newChat = new Chat();
                newChat.setChatGuid(guid);
                setLatestMessageToChat(message, newChat, true);
            } else if (message.getType() == Message.TYPE_GROUP_INVITATION) {
                Chat newChat = new Chat();
                setLatestMessageToChat(message, newChat, true);
            }
        }
    }

    public void setLatestMessageToChat(final Message message, final Chat chat, final boolean saveChat) {
        if (message == null || message.getId() == null
                || chat == null || message.isSystemInfo()) {
            return;
        }

        if (message.getType() == Message.TYPE_GROUP || message.getType() == Message.TYPE_CHANNEL
                || message.getType() == Message.TYPE_PRIVATE || message.getType() == Message.TYPE_GROUP_INVITATION) {
            chat.setLastMsgId(message.getId());

            Long lastModifiedDate = getLastModifiedDateFromMessage(message);
            if (lastModifiedDate != null) {
                chat.setLastChatModifiedDate(lastModifiedDate);
            }

            if (saveChat) {
                mApplication.getChannelChatController().insertOrUpdateChat(chat);
            }
        }
    }

    private Long getLastModifiedDateFromMessage(@NonNull final Message message) {
        if (message.getIsSentMessage() != null && message.getIsSentMessage()) {
            return message.getDateSend();
        } else {
            if (message.getDateDownloaded() != null) {
                return message.getDateDownloaded();
            } else {
                // msg wurd enoch nicht als gedownloaded markiert
                // Bug 44190 - Zeitpunkt der letzten Nachricht in Chat und Chatübersicht unterschiedlich wenn Nachricht im Hintergrund geladen wurde
                // dann vesuchen wir es mit dme Datum, an dem die Nachricht gesendet wurde
                // besser waere nach dem gemessagestask die chats upzudaten
                if (message.getIsSentMessage() != null) {
                    return message.getDateSend();
                } else {
                    return new Date().getTime();
                }
            }
        }
    }

    public long saveMessage(@NotNull Message message) {
        synchronized (messageDao) {
            if (message.getId() != null) {
                messageDao.update(message);
            } else {
                messageDao.insert(message);
            }

            Long id = message.getId();
            if (id != null) {
                if (id <= RefreshChatOverviewTask.getLastLoadedMsgId()) {
                    RefreshChatOverviewTask.setLastLoadedMsgId(id - 1);
                }

                if (!StringUtil.isNullOrEmpty(message.getAttachment())) {
                    loadPendingAttachment();
                }
                return id;
            } else return -1;
        }
    }

    public void updateMessages(List<Message> messages) {
        if (messages == null) {
            return;
        }

        synchronized (messageDao) {
            messageDao.updateInTx(messages);
        }
    }

    void sendMessage(final OnSendMessageListener onSendMessageListener,
                     final BuildMessageCallback buildMessageCallback) {
        new SendMessageTask(mApplication.getResources(),
                mApplication.getPreferencesController(),
                mApplication.getAccountController(),
                this,
                onSendMessageListener,
                mChatOverviewMessageListener).startTask(buildMessageCallback.buildMessage());
    }

    void sendMessageInDBSync(final OnSendMessageListener onSendMessageListener,
                             final BuildMessageCallback buildMessageCallback) {
        final SendMessageTask task = new SendMessageTask(mApplication.getResources(),
                mApplication.getPreferencesController(),
                mApplication.getAccountController(),
                this,
                onSendMessageListener,
                mChatOverviewMessageListener);
        final AsyncTaskResult result = task.doInBackground(buildMessageCallback.buildMessage());

        Handler handler = new Handler(mApplication.getMainLooper());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                task.onPostExecute(result);
            }
        };
        handler.post(runnable);
    }

    public boolean isMessageStillSending(long messageId) {
        boolean returnValue;

        synchronized (mActiveSendingMessageIds) {
            returnValue = mActiveSendingMessageIds.contains(messageId);
        }

        return returnValue;
    }

    public boolean isSentByMe(String messageSender) {
        return StringUtil.isEqual(messageSender, mApplication.getAccountController().getAccount().getAccountGuid());
    }

    public boolean isSelfConversation(final BaseMessageModel message) {
        String toGuid = null;

        if (message instanceof GroupMessageModel) {
            toGuid = ((GroupMessageModel) message).to;
        } else if (message instanceof PrivateMessageModel) {
            toGuid = ((PrivateMessageModel) message).to[0].guid;
        }

        return (toGuid != null) && toGuid.equals(mApplication.getAccountController().getAccount().getAccountGuid());
    }

    public ConfirmMessageSendModel[] parseMessageSendModel(JsonArray responseJsonArray) {
        return gson.fromJson(responseJsonArray, ConfirmMessageSendModel[].class);
    }

    void persistSentMessages(final List<BaseMessageModel> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        for (int i = 0; i < messages.size(); i++) {
            long id = persistSentMessage(messages.get(i), false);

            if (id == -1) {
                LogUtil.e(TAG, "Unable to persist message");
                return;
            }
        }
    }

    public long persistSentMessage(final BaseMessageModel baseMessageModel, final boolean markAsSent) {
        try {
            String accountGuid = mApplication.getAccountController().getAccount().getAccountGuid();

            if (markAsSent && (baseMessageModel.requestGuid == null)) {
                baseMessageModel.requestGuid = GuidUtil.generateRequestGuid();
            }

            Message message = MessageDaoHelper.buildMessageFromMessageModel(baseMessageModel, accountGuid);

            if (baseMessageModel.replaceMessageId > -1) {
                message.setId(baseMessageModel.replaceMessageId);
            }

            mApplication.getAttachmentController().saveSendMessageAttachment(message);

            LogUtil.i(TAG, "Adding Message to Database:" + message.getGuid() + " to " + message.getTo());

            saveMessage(message);

            if (markAsSent) {
                markAsSentConfirmed(message, new Date().getTime());
            }

            setLatestMessageToChat(message);

            return message.getId();

        } catch (LocalizedException e) {
            LogUtil.w(TAG, "persistSentMessage: " + e.getMessage());
            return -1L;
        }
    }

    void markReceivedMessagesAsRead(final List<Message> messages) {
        final JsonArray jsonArray = new JsonArray();
        final long now = new Date().getTime();
        for (final Message message : messages) {
            if (((message.getRead() != null) && (message.getRead())) || message.isSentMessage()) {
                continue;
            }

            if ((message.getIsSentMessage() != null && !message.getIsSentMessage())
                    && (message.getIsSystemInfo() == null || !message.getIsSystemInfo()))
            //Bug 38006 - bild soll erts nach download als gelesen markiert werden (message.getattachment)) - Bug 38345 und wieder zurueck...
            {
                jsonArray.add(new JsonPrimitive(message.getGuid()));
            }

            message.setRead(true);
            message.setDateRead(now);

            synchronized (messageDao) {
                messageDao.update(message);
            }
        }

        if (jsonArray.size() > 0) {
            BackendService.withAsyncConnection(mApplication)
                    .setMessageState(jsonArray, AppConstants.MESSAGE_STATE_READ, null, false);
        }
    }

    private boolean markSendMessageAsRead(final Message message, ConfirmV1Action action, List<Message> receiverChangedMessages) {
        boolean markMessage = false;

        if (message.getType() == -1) {
            return false;
        }

        if (!isSentByMe(message.getFrom()))
            return false;

        if (message.getType() == Message.TYPE_GROUP && !StringUtil.isNullOrEmpty(action.fromGuid)) {
            if (message.getChatMemberCount() == -1) {
                String chatGuid = MessageDataResolver.getGuidForMessage(message);

                if (!StringUtil.isNullOrEmpty(chatGuid)) {
                    try {
                        Chat chat = mApplication.getGroupChatController().getChatByGuid(chatGuid);
                        if (chat != null) {
                            JsonArray members = chat.getMembers();
                            int chatMemberCount = members != null ? members.size() : -1;

                            if (chatMemberCount > -1) {
                                message.setChatMemberCount(chatMemberCount);
                            }
                        }
                    } catch (LocalizedException e) {
                        LogUtil.w(TAG, "markSendMessageAsRead: " + e.getMessage());
                    }
                }
            }

            MessageReceiverModel receiver = message.getReceiverForGuid(action.fromGuid, mApplication);

            if (receiver == null) {
                receiver = new MessageReceiverModel();
                receiver.guid = action.fromGuid;
                receiver.sendsReadConfirmation = 1;
            }

            if (action.dateSend > 0) {
                if (receiver.dateRead == action.dateSend) {
                    return false;
                }

                receiver.dateRead = action.dateSend;
            } else {
                receiver.dateRead = new Date().getTime();
            }

            message.setReceiverForGuid(receiver, mApplication);

            if (message.checkAllReceiver(0, mApplication)) {
                markMessage = true;
            }

            if (action.isOwnMessage() && (message.getRead() == null || !message.getRead())) {
                message.setRead(true);

                if (action.dateSend > 0) {
                    message.setDateRead(action.dateSend);
                } else {
                    message.setDateRead(new Date().getTime());
                }

                markMessage = true;
            }

            if (!receiverChangedMessages.contains(message)) {
                receiverChangedMessages.add(message);
            }
        } else {
            if (message.getDateRead() != null) {
                return false;
            }

            if (!action.isOwnMessage() || (message.getRead() == null || !message.getRead())) {
                if (action.dateSend > 0) {
                    message.setDateRead(action.dateSend);
                } else {
                    message.setDateRead(new Date().getTime());
                }

                markMessage = true;

                if (action.isOwnMessage()) {
                    message.setRead(true);
                }
            }

            if (!receiverChangedMessages.contains(message)) {
                receiverChangedMessages.add(message);
            }
        }

        synchronized (messageDao) {
            messageDao.update(message);
        }

        return markMessage;
    }

    public void notifyMessageReceivedListeners(final List<Message> messages) {
        if (messages == null) {
            return;
        }

        for (Message message : messages) {
            for (final OnMessageReceivedListener listener : mOnMessageReceivedListeners) {
                if (listener != null && message != null) {
                    listener.onMessageReceived(message);
                }
            }
        }
    }

    public void markAsError(@NotNull final Message message,
                            final boolean hasError) {
        final List<Message> markedMessages = new ArrayList<>();

        message.setHasSendError(hasError);
        markedMessages.add(message);
        synchronized (messageDao) {
            messageDao.update(message);
        }
        sendMessageChangedNotification(markedMessages);
    }

    public void markAsSentConfirmed(@NotNull final Message message,
                                    final long dateSendConfirm) {
        final List<Message> messages = new ArrayList<>();

        messages.add(message);
        markAsSentConfirmed(messages, dateSendConfirm);
    }

    private void markAsSentConfirmed(final List<Message> messages,
                                     final long datesendConfirm) {
        final List<Message> markedMessages = new ArrayList<>();

        for (final Message message : messages) {
            if (message == null) {
                continue;
            }
            if ((message.getIsSentMessage() != null) && message.getIsSentMessage()) {
                markedMessages.add(message);
                message.setDateSendConfirm(datesendConfirm);
                message.setHasSendError(false);
                synchronized (messageDao) {
                    messageDao.update(message);
                }
            }
        }
        sendMessageChangedNotification(markedMessages);
    }

    public void handleConfirmActions(List<ConfirmV1Action> actions) {
        if (actions == null || actions.size() < 1) {
            return;
        }

        List<Message> allMarkedMessages = new ArrayList<>();
        List<Message> receiverChangedMessages = new ArrayList<>();

        for (ConfirmV1Action action : actions) {
            if (action.guids == null || action.guids.length < 1) {
                continue;
            }

            List<Message> messages = getMessagesByGuids(action.guids);

            if (StringUtil.isEqual(action.name, ConfirmV1Action.ACTION_CONFIRM_DOWNLOAD_V1)) {
                for (final Message message : messages) {
                    if (markSendMessageAsDownloaded(message, action, receiverChangedMessages)) {
                        if (!allMarkedMessages.contains(message)) {
                            allMarkedMessages.add(message);
                        }
                    }
                }
            } else if (StringUtil.isEqual(action.name, ConfirmV1Action.ACTION_CONFIRM_READ_V1)) {
                for (final Message message : messages) {
                    if (markSendMessageAsRead(message, action, receiverChangedMessages)) {
                        if (!allMarkedMessages.contains(message)) {
                            allMarkedMessages.add(message);
                        }
                    }
                }
            }
        }

        if (allMarkedMessages.size() > 0) {
            sendMessageChangedNotification(allMarkedMessages);
        }

        if (receiverChangedMessages.size() > 0) {
            callOnMessageReceiverChangedListener(receiverChangedMessages);
        }
    }

    private boolean markSendMessageAsDownloaded(@NonNull Message message, @NonNull ConfirmV1Action action, List<Message> receiverChangedMessages) {
        if (message.getType() == -1) {
            return false;
        }

        if (!isSentByMe(message.getFrom()))
            return false;

        boolean markMessage = false;

        if (message.getType() == Message.TYPE_GROUP && !StringUtil.isNullOrEmpty(action.fromGuid)) {
            if (message.getChatMemberCount() == -1) {
                String chatGuid = MessageDataResolver.getGuidForMessage(message);

                if (!StringUtil.isNullOrEmpty(chatGuid)) {
                    try {
                        Chat chat = mApplication.getGroupChatController().getChatByGuid(chatGuid);
                        if (chat != null) {
                            JsonArray members = chat.getMembers();
                            int chatMemberCount = members != null ? members.size() : -1;

                            if (chatMemberCount > -1) {
                                message.setChatMemberCount(chatMemberCount);
                            }
                        }
                    } catch (LocalizedException e) {
                        LogUtil.w(TAG, "markSendMessageAsDownloaded: " + e.getMessage());
                    }
                }
            }

            MessageReceiverModel receiver = message.getReceiverForGuid(action.fromGuid, mApplication);

            if (receiver == null) {
                receiver = new MessageReceiverModel();
                receiver.guid = action.fromGuid;
            }

            if (action.dateSend > 0) {
                if (receiver.dateDownloaded == action.dateSend) {
                    return false;
                }
                receiver.dateDownloaded = action.dateSend;
            } else {
                receiver.dateDownloaded = new Date().getTime();
            }

            message.setReceiverForGuid(receiver, mApplication);

            if (message.checkAllReceiver(1, mApplication)) {
                markMessage = true;
            }

            if (!receiverChangedMessages.contains(message)) {
                receiverChangedMessages.add(message);
            }
        } else {
            if (message.getDateDownloaded() != null) {
                return false;
            }

            if (action.dateSend > 0) {
                message.setDateDownloaded(action.dateSend);
            } else {
                message.setDateDownloaded(new Date().getTime());
            }

            markMessage = true;

            if (!receiverChangedMessages.contains(message)) {
                receiverChangedMessages.add(message);
            }
        }

        synchronized (messageDao) {
            messageDao.update(message);
        }
        return markMessage;
    }

    private void callOnMessageReceiverChangedListener(final List<Message> messages) {
        Handler handler = new Handler(mApplication.getMainLooper());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (mOnMsgReceiverChangedListeners != null) {
                    for (OnMessageReceiverChangedListener listener : mOnMsgReceiverChangedListeners) {
                        listener.onMessageReceiverChanged(messages);
                    }
                }
            }
        };
        handler.post(runnable);
    }

    List<Message> loadUnreadMessages() {
        synchronized (messageDao) {
            final QueryBuilder<Message> queryBuilder = messageDao.queryBuilder();

            queryBuilder.whereOr(Properties.Read.isNull(), Properties.Read.eq(false)).where(Properties.Type.eq(Message.TYPE_PRIVATE_INTERNAL));

            return queryBuilder.list();
        }
    }

    public void sendMessageChangedNotification(final List<Message> markedMessages) {
        final SparseArray<List<Message>> listContainer = new SparseArray<>();

        for (final Message message : markedMessages) {
            if (listContainer.get(message.getType()) == null) {
                listContainer.put(message.getType(), new ArrayList<Message>());
            }

            listContainer.get(message.getType()).add(message);
        }

        for (final MessageControllerListener listener : listenerMap) {
            listener.onMessagesChanged(listContainer);
        }
    }

    public void setNewDestructionDate(final String guid,
                                      final Date newDate) {
        final NewDestructionDate newDestructionDate = new NewDestructionDate();

        newDestructionDate.setGuid(guid);
        newDestructionDate.setNewDestructionDate(newDate.getTime());
        newDestructionDateDao.insert(newDestructionDate);
    }

    public void addTimedMessagedDeliveredListener(final OnTimedMessagesDeliveredListener onTimedMessagesDeliveredListener) {
        if (mOnTimedMessagesDeliveredListenersList == null) {
            mOnTimedMessagesDeliveredListenersList = new ArrayList<>();
        }

        mOnTimedMessagesDeliveredListenersList.add(onTimedMessagesDeliveredListener);
    }

    public void removeTimedMessagedDeliveredListener(final OnTimedMessagesDeliveredListener onTimedMessagesDeliveredListener) {
        if (mOnTimedMessagesDeliveredListenersList == null) {
            return;
        }

        mOnTimedMessagesDeliveredListenersList.remove(onTimedMessagesDeliveredListener);
    }

    public void notifyOnTimedMessagesDeliveredListeners(List<String> chatGuids) {
        if (mOnTimedMessagesDeliveredListenersList == null) {
            return;
        }
        for (OnTimedMessagesDeliveredListener listener : mOnTimedMessagesDeliveredListenersList) {
            listener.timedMessageDelivered(chatGuids);
        }
    }

    public Date getNewDestructionDate(final String guid) {
        if (StringUtil.isNullOrEmpty(guid)) {
            return null;
        }

        final QueryBuilder<NewDestructionDate> queryBuilder = newDestructionDateDao.queryBuilder();

        queryBuilder.where(NewDestructionDateDao.Properties.Guid.eq(guid));
        queryBuilder.orderDesc(NewDestructionDateDao.Properties.Id);

        List<NewDestructionDate> list = queryBuilder.list();

        if (list == null || list.size() < 1) {
            return null;
        }

        NewDestructionDate newDestructionDate = list.get(0);

        if (newDestructionDate == null) {
            return null;
        }

        return new Date(newDestructionDate.getNewDestructionDate());
    }

    void deleteNewDestructionDate(final String guid) {
        if (guid == null) {
            return;
        }

        final QueryBuilder<NewDestructionDate> queryBuilder = newDestructionDateDao.queryBuilder();

        queryBuilder.where(NewDestructionDateDao.Properties.Guid.eq(guid));

        final List<NewDestructionDate> newDestructionDates = queryBuilder.list();

        for (final NewDestructionDate newDestructionDate : newDestructionDates) {
            if (newDestructionDate != null) {
                newDestructionDateDao.delete(newDestructionDate);
            }
        }
    }

    /**
     * Lädt alle Nachrichten vom Server, wo lokal noch keine Daten vorhanden sind.
     */
    public void loadPendingMessages() {
        if (mLoadPendingMessagesTask != null) {
            return;
        }

        mLoadPendingMessagesTask = new LoadPendingMessagesTask(mApplication.getAccountController().getAccount().getAccountGuid(), this, new GenericActionListener<List<String>>() {
            @Override
            public void onSuccess(List<String> chatGuidList) {
                mLoadPendingMessagesTask = null;
                mApplication.getChatOverviewController().chatChanged(chatGuidList, null, null, ChatOverviewController.CHAT_CHANGED_REFRESH_CHAT);
            }

            @Override
            public void onFail(String message, String errorIdent) {
                //wird nicht aufgerufen
            }
        });

        mLoadPendingMessagesTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, getDao(), null, null);
    }

    public void loadPendingTimedMessages() {
        LoadPendingTimedMessagesTask.Companion.start();
    }

    private void loadPendingAttachment() {
        eu.ginlo_apps.ginlo.services.LoadPendingAttachmentTask.Companion.start();
    }

    public void startGetNewMessages(final boolean informOnMessageReceivedListener) {
        mStopGetMessageTask = false;

        loadPendingAttachment();

        if (mApplication.getPreferencesController().useLazyMsgService()) {
            if (refreshTimer != null) {
                stopGetMessagesTimer();

                if (getMessageTask != null) {
                    getMessageTask.cancel();
                }
            }
            startGetMessageTask(true, informOnMessageReceivedListener);
        } else {
            if (getMessageTask != null) {
                mStartGetMessageTaskAgain = true;
            } else {
                startGetMessagesTimer(informOnMessageReceivedListener);
            }
        }
    }

    private void startGetMessagesTimer(final boolean informOnMessageReceivedListener) {
        if (refreshTimer != null) {
            return;
        }

        final Runnable refreshRunnable = new Runnable() {
            @Override
            public void run() {
                //Nur starten wenn er gerade nicht laeuft
                if (getMessageTask != null) {
                    return;
                }

                MessageController.this.startGetMessageTask(false, informOnMessageReceivedListener);
            }
        };

        refreshTimer = new Timer();

        final Handler handler = new Handler();

        final TimerTask refreshTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(refreshRunnable);
            }
        };

        final long executionTime = mApplication.getPreferencesController().getListRefreshRate() * 1000;

        refreshTimer.scheduleAtFixedRate(refreshTask, 0, executionTime);
    }

    private void stopGetMessagesTimer() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer.purge();
            refreshTimer = null;
        }
    }

    private void stopGetMessageTask() {
        mStopGetMessageTask = true;

        stopGetMessagesTimer();

        if (getMessageTask != null) {
            getMessageTask.cancel();
        }
    }

    public void resetTasks() {
        stopGetMessagesTimer();
        stopGetMessageTask();
    }

    public boolean sendMessageToBackend(final BaseMessageModel messageModel,
                                        final IBackendService.OnBackendResponseListener onBackendResponseListener) throws LocalizedException {

        String messageJson = gson.toJson(messageModel);

        if(!StringUtil.isNullOrEmpty(messageModel.attachment)) {
            // KS: Message with file attachment. These can be *very* big so the serializer only returned
            // a filename marker instead of the full attachment contents as "attachment".
            // This must be processed now (before MD5 checksum building).
            messageJson = loadFileWithFullMessageIncludingAttachment(messageJson, true);
        }

        if (messageJson == null) {
            return false;
        }

        if (messageModel instanceof PrivateInternalMessageModel) {
            BackendService.withSyncConnection(mApplication)
                    .sendPrivateInternalMessage(messageJson, onBackendResponseListener, messageModel.requestGuid);
        } else if (messageModel instanceof PrivateMessageModel) {
            final Date sendDate = ((PrivateMessageModel) messageModel).dateSendTimed;

            if (sendDate == null) {
                BackendService.withSyncConnection(mApplication)
                        .sendPrivateMessage(messageJson, onBackendResponseListener, messageModel.requestGuid/*, onConnectionDataUpdatedListener*/);
            } else {
                BackendService.withSyncConnection(mApplication)
                        .sendTimedPrivateMessage(messageJson, onBackendResponseListener,
                                messageModel.requestGuid, DateUtil.dateToUtcStringWithoutMillis(sendDate));
            }
        } else if (messageModel instanceof GroupMessageModel) {
            final Date sendDate = ((GroupMessageModel) messageModel).dateSendTimed;
            if (sendDate == null) {
                BackendService.withSyncConnection(mApplication)
                        .sendGroupMessage(messageJson, onBackendResponseListener, messageModel.requestGuid);
            } else {
                BackendService.withSyncConnection(mApplication)
                        .sendTimedGroupMessage(messageJson, onBackendResponseListener,
                                messageModel.requestGuid, DateUtil.dateToUtcStringWithoutMillis(sendDate));
            }
        }

        return true;
    }

    /**
     * Get a message string and look for a pattern that points to an attachment file.
     * If no pattern is found, create the new message file with the contents of message.
     * @param message String with message
     * @param deleteAttachmentFile Delete file which the attachment pattern points to?
     * @return Pathname of the newly created message file
     */
    private String loadFileWithFullMessageIncludingAttachment(final String message,
                                                             final Boolean deleteAttachmentFile) {
        FileUtil fu = new FileUtil(mApplication);
        File messageFile = null;
        try {
            messageFile = fu.getTempFile();
            FileWriter fw = new FileWriter(messageFile);
            int read = 0;
            byte[] data = new byte[StreamUtil.STREAM_BUFFER_SIZE];
            String dataString = null;

            Pattern p = Pattern.compile("(@\\/.*-json)");
            Matcher m = p.matcher(message);
            if (m.find()) {
                String firstPart = message.substring(0, m.start() - 2);
                String jsonFilename = m.group().substring(1);
                String secondPart = message.substring(m.end() + 2);
                LogUtil.d(TAG, "Attachment file location (" + m.group() + ") found at " + m.start());

                fw.append(firstPart);

                // Replace filename with attachment json file contents
                FileInputStream afi = new FileInputStream(jsonFilename);
                while ((read = afi.read(data, 0, StreamUtil.STREAM_BUFFER_SIZE)) > 0) {
                    dataString = new String(data, StandardCharsets.UTF_8).substring(0, read);
                    fw.append(dataString);
                }
                fw.append(secondPart);
                afi.close();
                if(deleteAttachmentFile) {
                    FileUtil.deleteFile(new File(jsonFilename));
                }
            } else {
                // No attachment pattern found - build "normal" message
                fw.append(message);
            }
            fw.close();

        } catch (IOException | JsonIOException e) {
            LogUtil.e(TAG, "Could not create/build message file: " + messageFile.getPath(), e);
            return null;
        }

        return messageFile.getPath();
    }

    public void registerChatOverviewActivityAsListener(OnSendMessageListener listener) {
        mChatOverviewMessageListener = listener;
    }

    public void unregisterChatOverviewActivityAsListener() {
        mChatOverviewMessageListener = null;
    }

    private void startGetMessageTask(final boolean useLazyMsgService, final boolean informOnMessageReceivedListener) {
        if ((getMessageTask == null) && (mApplication.getLoginController().isLoggedIn())) {
            final ConcurrentTaskListener listener = new ConcurrentTaskListener() {
                @Override
                public void onStateChanged(final ConcurrentTask task,
                                           final int state) {
                    if (state == ConcurrentTask.STATE_RUNNING) {
                        return;
                    }

                    getMessageTask = null;

                    // Soll MessageTask gestoppt werden
                    if (mStopGetMessageTask) {
                        // dann hier wieder raus
                        mStopGetMessageTask = false;
                        return;
                    }

                    if ((state == ConcurrentTask.STATE_COMPLETE) || (state == ConcurrentTask.STATE_CANCELED)) {
                        boolean nextInformOnMessageReceivedListener = true;

                        Object[] results = task.getResults();
                        if (!informOnMessageReceivedListener && results != null && results.length > 1) {
                            List<String> newSoundMessageGuids = (List<String>) results[1];
                            if (newSoundMessageGuids != null && newSoundMessageGuids.size() > 0) {
                                nextInformOnMessageReceivedListener = false;
                            }
                        }

                        if (!useLazyMsgService) {
                            if (mStartGetMessageTaskAgain) {
                                mStartGetMessageTaskAgain = false;
                                MessageController.this.startGetMessageTask(false, nextInformOnMessageReceivedListener);
                            }
                        } else {
                            MessageController.this.startGetMessageTask(true, nextInformOnMessageReceivedListener);
                        }
                    } else if (state == ConcurrentTask.STATE_ERROR) {
                        Object[] result = task.getResults();
                        if (result != null && result.length == 1) {
                            if (result[0] instanceof MsgExceptionModel) {
                                String ident = ((MsgExceptionModel) result[0]).getIdent();
                                if (StringUtil.isEqual(ident, LocalizedException.NO_ACCOUNT_ON_SERVER)
                                        || StringUtil.isEqual(ident, LocalizedException.ACCOUNT_UNKNOWN)) {
                                    mApplication.getAccountController().ownAccountWasDeleteOnServer();
                                }
                            }
                        }
                        // wenn ein fehler beim LazyMsgService aufruf aufgetreten ist, verzögert neu starten
                        if (useLazyMsgService) {
                            Timer refreshTimer = new Timer();

                            final TimerTask refreshTask = new TimerTask() {
                                @Override
                                public void run() {
                                    MessageController.this.startGetMessageTask(true, true);
                                }
                            };

                            final long executionTime = mApplication.getPreferencesController().getListRefreshRate() * 1000;

                            refreshTimer.schedule(refreshTask, executionTime);
                        }
                    }
                }
            };

            final MessageConcurrentTaskListener messageConcurrentTaskListener = new MessageConcurrentTaskListener(
                    this,
                    listener,
                    informOnMessageReceivedListener,
                    mApplication.getAppLifecycleController(),
                    mApplication.getLoginController(),
                    mApplication.getPreferencesController());

            boolean useLazy = informOnMessageReceivedListener && useLazyMsgService;

            getMessageTask = messageTaskManager.executeGetMessageTask(mApplication, messageConcurrentTaskListener, useLazy, false);
        }
    }

    public void startMessageTaskSyncFromAppBackground(final ConcurrentTaskListener nextListner) {
        final MessageConcurrentTaskListener messageConcurrentTaskListener = new MessageConcurrentTaskListener(
                this,
                nextListner,
                false,
                mApplication.getAppLifecycleController(),
                mApplication.getLoginController(),
                mApplication.getPreferencesController());

        ConcurrentTask concurrentTask = messageTaskManager.getMessageTask(mApplication, messageConcurrentTaskListener, false, true, false);
        concurrentTask.runSync();
    }

    public void startMessageTaskSync(final ConcurrentTaskListener nextListner, boolean useLazyMsgService, boolean useInBackground, boolean onlyPrio1Msg) {
        final MessageConcurrentTaskListener messageConcurrentTaskListener = new MessageConcurrentTaskListener(
                this,
                nextListner,
                false,
                mApplication.getAppLifecycleController(),
                mApplication.getLoginController(),
                mApplication.getPreferencesController());

        ConcurrentTask concurrentTask = messageTaskManager.getMessageTask(mApplication, messageConcurrentTaskListener, useLazyMsgService, useInBackground, onlyPrio1Msg);
        concurrentTask.runSync();
    }

    @Override
    public void appDidEnterForeground() {
        if (mApplication.getAccountController().hasAccountFullState() && (mApplication.getLoginController().isLoggedIn())) {
            resetBadgeInBackground();

            GET_SINGLE_MSG_SERIAL_EXECUTOR.cancelAll();

            int oldNewMessageFlags = mApplication.getPreferencesController().getNewMessagesFlags();
            if (oldNewMessageFlags > -1) {
                handleNewMessageFlag(oldNewMessageFlags);
                mApplication.getPreferencesController().setNewMessagesFlags(-1);
            }
            startGetNewMessages(false);
        }
    }

    @Override
    public void appGoesToBackGround() {
        if (mApplication.getAccountController().hasAccountFullState()) {
            stopGetMessageTask();

            // Reset BadgeIdent
            resetBadgeInBackground();

            // Reset Badgenumber
            try {
                ShortcutBadger.removeCountOrThrow(mApplication);
            } catch (ShortcutBadgeException e) {
                LogUtil.w(TAG, "appGoesToBackGround: " + e.getMessage());
            }
        }
    }

    void deleteTimedMessage(final String messageGuid,
                            final IBackendService.OnBackendResponseListener listener) {
        BackendService.withAsyncConnection(mApplication)
                .removeTimedMessage(messageGuid, listener);
    }

    private void resetBadgeInBackground() {
        try {
            if (mApplication.getAccountController().getAccountLoaded() && (mApplication.getLoginController().isLoggedIn())) {
                final AsyncHttpCallback<String> callback = new AsyncHttpCallback<String>() {
                    @Override
                    public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                        BackendService.withSyncConnection(mApplication)
                                .resetBadge(listener);
                    }

                    @Override
                    public String asyncLoaderServerResponse(final BackendResponse response) {
                        return "";
                    }

                    @Override
                    public void asyncLoaderFinished(final String result) {
                    }

                    @Override
                    public void asyncLoaderFailed(final String errorMessage) {
                    }
                };
                new AsyncHttpTask<>(callback).executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
            }
        } catch (RuntimeException e) {
            LogUtil.e(TAG, "resetBadgeInBackground: " + e.getMessage(), e);
        }
    }

    @Override
    public void appIsUnlock() {
        if (mApplication.getAccountController().hasAccountFullState()) {
            GET_SINGLE_MSG_SERIAL_EXECUTOR.cancelAll();

            final int oldNewMessageFlags = mApplication.getPreferencesController().getNewMessagesFlags();
            if (oldNewMessageFlags > -1) {
                handleNewMessageFlag(oldNewMessageFlags);
                mApplication.getPreferencesController().setNewMessagesFlags(-1);
            }

            //
            startGetNewMessages(false);
        }
    }

    @Override
    public void onLowMemory(int state) {
        if (state == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            SEND_MSG_SERIAL_EXECUTOR.cancelAll();
        }
    }

    @Override
    public void appWillBeLocked() {
        // wird nicht benoetigt
    }

    public void handleNewMessageFlag(final int newMessageFlag) {
        if (newMessageFlag > -1) {
            for (final MessageControllerListener listener : listenerMap) {
                listener.onNewMessages(newMessageFlag);
            }
        }
    }

    public void addMessageToActiveSendingMessages(Long id) {
        synchronized (mActiveSendingMessageIds) {
            mActiveSendingMessageIds.add(id);
        }
    }

    public void removeMessageFromActiveSendingMessages(Long id) {
        synchronized (mActiveSendingMessageIds) {
            mActiveSendingMessageIds.remove(id);
        }
    }

    @NonNull
    public List<String> getTimedMessagesGuids() throws LocalizedException {
        final List<String[]> guids = new ArrayList<>(1);
        final ResponseModel rm = new ResponseModel();

        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                } else if (response.jsonArray != null) {
                    final GsonBuilder gsonBuilder = new GsonBuilder();
                    Gson gson = gsonBuilder.create();
                    String[] guidArray = gson.fromJson(response.jsonArray, String[].class);
                    if (guidArray != null && guidArray.length > 0) {
                        guids.add(guidArray);
                    }
                }
            }
        };

        BackendService.withSyncConnection(mApplication)
                .getTimedMessageGuids(listener);

        if (rm.isError) {
            throw new LocalizedException(StringUtil.isNullOrEmpty(rm.errorIdent) ? LocalizedException.BACKEND_REQUEST_FAILED : rm.errorIdent, rm.errorMsg);
        }

        if (guids.size() > 0) {
            return new ArrayList<>(Arrays.asList(guids.get(0)));
        }

        return Collections.emptyList();
    }

    public List<String> loadTimedMessages(@NonNull final List<String> guids)
            throws LocalizedException {
        final MessageDao msgDao = getDao();
        final String accountGuid = mApplication.getAccountController().getAccount().getAccountGuid();
        final ResponseModel rm = new ResponseModel();
        final List<String> attachmentGuids = new ArrayList<>(guids.size());

        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {
                    rm.setError(response);
                } else if (response.jsonArray != null) {
                    for (JsonElement jsonElement : response.jsonArray) {
                        if (!jsonElement.isJsonObject()) {
                            continue;
                        }

                        if (JsonUtil.hasKey("TimedMessage", jsonElement.getAsJsonObject())) {
                            JsonObject timedMsgJO = jsonElement.getAsJsonObject().getAsJsonObject("TimedMessage");

                            if (timedMsgJO == null) {
                                continue;
                            }

                            String sendDate = JsonUtil.stringFromJO("sendDate", timedMsgJO);
                            String msgJo = JsonUtil.stringFromJO("data", timedMsgJO);
                            String guid = JsonUtil.stringFromJO("guid", timedMsgJO);

                            if (!StringUtil.isNullOrEmpty(sendDate) && !StringUtil.isNullOrEmpty(msgJo) && !StringUtil.isNullOrEmpty(guid)) {
                                Message msg = gson.fromJson(msgJo, Message.class);
                                msg.setDateSendTimed(DateUtil.utcWithoutMillisStringToMillis(sendDate));
                                // Da vom Server geladen --> DateSendConfirmed ist das gesendet Datum
                                msg.setDateSendConfirm(DateUtil.utcWithoutMillisStringToMillis(sendDate));
                                msg.setGuid(guid);

                                checkAndSetSendMessageProps(msg, accountGuid);

                                if (!StringUtil.isNullOrEmpty(msg.getAttachment())) {
                                    attachmentGuids.add(msg.getAttachment());
                                }
                                msgDao.insert(msg);

                                String chatGuid = MessageDataResolver.getGuidForMessage(msg);
                                List<String> chatGuids = new ArrayList<>();
                                chatGuids.add(chatGuid);

                                notifyOnTimedMessagesDeliveredListeners(chatGuids);
                            }
                        }
                    }
                }
            }
        };

        String guidsAsString = StringUtil.getStringFromList(",", guids);
        BackendService.withSyncConnection(mApplication)
                .getTimedMessages(guidsAsString, listener);

        if (rm.isError) {
            throw new LocalizedException(StringUtil.isNullOrEmpty(rm.errorIdent) ? LocalizedException.BACKEND_REQUEST_FAILED : rm.errorIdent, rm.errorMsg);
        }

        return attachmentGuids;
    }

    public void getBackgroundAccessToken(final GenericActionListener<String> listener) {
        final AsyncHttpCallback<String> callback = new AsyncHttpCallback<String>() {
            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .getBackgroundAccessToken(listener);
            }

            @Override
            public String asyncLoaderServerResponse(final BackendResponse response)
                    throws LocalizedException {
                if (response.jsonArray == null || response.jsonArray.size() == 0 || response.jsonArray.get(0) == null) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Response is null");
                }

                return response.jsonArray.get(0).getAsString();
            }

            @Override
            public void asyncLoaderFinished(final String result) {
                if (!StringUtil.isNullOrEmpty(result)) {
                    setMessageDeviceToken(result);
                    if (listener != null) {
                        listener.onSuccess(null);
                    }
                } else {
                    if (listener != null) {
                        listener.onFail(null, null);
                    }
                }
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                if (listener != null) {
                    listener.onFail(errorMessage, null);
                }
            }
        };

        new AsyncHttpTask<>(callback).executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }

}
