// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.adapter.ChatAdapter;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.manager.ChatTaskManager;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.concurrent.task.ConvertToChatItemVOTask;
import eu.ginlo_apps.ginlo.concurrent.task.HttpBaseTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.controller.AttachmentController.OnAttachmentLoadedListener;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.controller.MessageDecryptionController;
import eu.ginlo_apps.ginlo.controller.TaskManagerController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.controller.message.MessageDataResolver;
import eu.ginlo_apps.ginlo.controller.message.PrivateInternalMessageController;
import eu.ginlo_apps.ginlo.controller.message.contracts.BuildMessageCallback;
import eu.ginlo_apps.ginlo.controller.message.contracts.MessageControllerListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnAcceptInvitationListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnChatDataChangedListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnDeclineInvitationListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnDeleteTimedMessageListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnSendMessageListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.QueryDatabaseListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.SilenceChatListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.ChatDao;
import eu.ginlo_apps.ginlo.greendao.ChatDao.Properties;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.AESKeyDataContainer;
import eu.ginlo_apps.ginlo.model.AppGinloControlMessage;
import eu.ginlo_apps.ginlo.model.CitationModel;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.BaseMessageModel;
import eu.ginlo_apps.ginlo.model.chat.AttachmentChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.model.param.MessageDestructionParams;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.MessageModelBuilder;
import eu.ginlo_apps.ginlo.util.MimeUtil;
import eu.ginlo_apps.ginlo.util.OnImageDataChangedListener;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.greenrobot.greendao.query.QueryBuilder;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public abstract class ChatController
        extends MessageDataResolver
        implements MessageControllerListener,
        OnImageDataChangedListener {

    private static final String TAG = ChatController.class.getSimpleName();
    public static final long NO_MESSAGE_ID_FOUND = -1;
    private static final int LOAD_NEWEST_MESSAGE_COUNT = 20;
    protected final ChatDao chatDao;

    final MessageController messageController;
    final MessageDecryptionController messageDecryptionController;
    final ChatOverviewController chatOverviewController;
    final ChatTaskManager chatTaskManager;
    final ChannelController mChannelController;
    final AttachmentController attachmentController;
    final PrivateInternalMessageController privateInternalMessageController;
    private final ChatImageController mChatImageController;
    private final TaskManagerController taskManagerController;
    private final ArrayList<OnChatDataChangedListener> listeners;
    ChatAdapter mCurrentChatAdapter;
    private HashMap<String, LongSparseArray<BaseChatItemVO>> chatItemRegistry;
    private final Context mContext;

    ChatController(final SimsMeApplication application) {
        super(application);

        mContext = application.getApplicationContext();
        this.chatItemRegistry = new HashMap<>();
        this.listeners = new ArrayList<>();
        this.chatTaskManager = new ChatTaskManager();

        chatDao = application.getChatDao();

        this.messageController = application.getMessageController();
        this.messageDecryptionController = application.getMessageDecryptionController();
        this.chatOverviewController = application.getChatOverviewController();
        this.mChannelController = application.getChannelController();
        this.attachmentController = application.getAttachmentController();
        this.taskManagerController = application.getTaskManagerController();
        this.privateInternalMessageController = application.getPrivateInternalMessageController();
        mChatImageController = application.getChatImageController();
        mChatImageController.addListener(this);
    }

    private static int rank(final long value,
                            final ChatAdapter adapter) {
        for (int i = adapter.getCount() - 1; i >= 0; i--) {
            final BaseChatItemVO item = adapter.getItem(i);

            if (value == item.messageId) {
                return -2;
            } else if (value > item.messageId) {
                return ((i + 1) == adapter.getCount()) ? -1 : (i + 1);
            }
        }

        return -1;
    }

    public void addListener(final OnChatDataChangedListener listener) {
        listeners.add(listener);
    }

    public void removeListener(final OnChatDataChangedListener listener) {
        listeners.remove(listener);
    }

    private void notifyListenerChanged() {
        new Handler(Looper.getMainLooper()).post(
                new Runnable() {
                    @Override
                    public void run() {
                        for (final OnChatDataChangedListener listener : listeners) {
                            listener.onChatDataChanged(false);
                        }
                    }
                }
        );
    }

    void notifyListenerLoaded(final long lastLoadedMessageId) {
        new Handler(Looper.getMainLooper()).post(
                new Runnable() {
                    @Override
                    public void run() {
                        for (final OnChatDataChangedListener listener : listeners) {
                            listener.onChatDataLoaded(lastLoadedMessageId);
                        }
                    }
                }
        );
    }

    public ChatDao getChatDao() {
        return chatDao;
    }

    public Chat getChatOrCreateIfNotExist(final String chatGuid, final String title)
            throws LocalizedException {
        Chat chat = getChatByGuid(chatGuid);

        if (chat == null) {
            chat = new Chat();
            chat.setChatGuid(chatGuid);
            int type;

            switch (getMainTypeResponsibility()) {
                case Message.TYPE_PRIVATE: {
                    type = Chat.TYPE_SINGLE_CHAT;
                    break;
                }
                case Message.TYPE_GROUP: {
                    type = Chat.TYPE_GROUP_CHAT;
                    break;
                }
                case Message.TYPE_CHANNEL: {
                    type = Chat.TYPE_CHANNEL;
                    break;
                }
                default: {
                    type = Chat.TYPE_SINGLE_CHAT;
                }
            }
            chat.setType(type);
            chat.setTitle(title);

            insertOrUpdateChat(chat);
        }

        return chat;
    }

    public void clearChat(final Chat chat, @NonNull final GenericActionListener<Void> listener) {
        final String chatGuid = chat.getChatGuid();
        final List<Message> messages = messageController.getNonTimedMessagesByGuid(chatGuid);
        JsonArray messageGuids = new JsonArray();
        for (Message m : messages) {
            if (m != null && !StringUtil.isNullOrEmpty(m.getGuid())) {
                messageGuids.add(new JsonPrimitive(m.getGuid()));
            }
        }

        final IBackendService.OnBackendResponseListener obesrl = new IBackendService.OnBackendResponseListener() {

            @Override
            public void onBackendResponse(BackendResponse response) {
                if (response.isError) {

                    final String errorMsg = response.errorMessage != null ? response.errorMessage
                            : mApplication.getString(R.string.delete_message_error);

                    listener.onFail(errorMsg, null);

                    LogUtil.w(TAG, "clearChat: " + errorMsg);
                } else {
                    Long msgId = chat.getLastMsgId();
                    if (msgId != null) {
                        Message message = messageController.getMessageById(msgId);
                        if (message != null) {
                            Long lastModifiedDate = message.getDateSend();
                            Long lastModifiedDateChat = chat.getLastChatModifiedDate();

                            if (lastModifiedDate != null && lastModifiedDateChat != null && lastModifiedDate > lastModifiedDateChat) {
                                chat.setLastChatModifiedDate(lastModifiedDate);
                            }
                        }
                        chat.setLastMsgId(null);
                        insertOrUpdateChat(chat);
                    }

                    for (Message m : messages) {
                        deleteMessageHelper(m, null);
                    }
                    listener.onSuccess(null);
                }
            }
        };
        BackendService.withAsyncConnection(mApplication)
                .setMessageState(messageGuids, AppConstants.MESSAGE_STATE_DELETED, obesrl, false);
    }

    /**
     * loescht den gruppenchat und ignoriert getimte nachrichten, da diese am Server automatisch entfernt werden.
     */
    public void deleteGroupChat(final String chatGuid) {
        messageController.deleteAllMessagesByGuid(chatGuid);
        clearAdapter(chatGuid);

        synchronized (chatDao) {
            final QueryBuilder<Chat> queryBuilder = chatDao.queryBuilder();
            final List<Chat> chats = queryBuilder.where(Properties.ChatGuid.eq(chatGuid)).list();

            for (final Chat chat : chats) {
                chatDao.delete(chat);
                chatDao.detach(chat);
            }
        }
    }

    public void deleteChat(final String chatGuid, final boolean bSyncExternal, final OnDeleteTimedMessageListener outerListener) {
        if (bSyncExternal) {
            // getimte loeschen
            OnDeleteTimedMessageListener innerListener = new OnDeleteTimedMessageListener() {
                @Override
                public void onDeleteMessageError(final String errorMessage) {
                    if (outerListener != null) {
                        outerListener.onDeleteMessageError(errorMessage);
                    }
                }

                @Override
                public void onDeleteAllMessagesSuccess(final String guid) {
                    synchronized (chatDao) {
                        final QueryBuilder<Chat> queryBuilder = chatDao.queryBuilder();
                        final List<Chat> chats = queryBuilder.where(Properties.ChatGuid.eq(chatGuid)).list();

                        for (final Chat chat : chats) {
                            chatDao.delete(chat);
                            chatDao.detach(chat);
                        }
                    }

                    messageController.deleteNonTimedMessagesByGuid(chatGuid);
                    clearAdapter(chatGuid);
                    BackendService.withAsyncConnection(mApplication)
                            .setChatDeleted(chatGuid, null);

                    if (outerListener != null) {
                        outerListener.onDeleteAllMessagesSuccess(guid);
                    }
                }

                @Override
                public void onDeleteSingleMessageSuccess(final String chatGuid) {
                    outerListener.onDeleteSingleMessageSuccess(chatGuid);
                }
            };

            messageController.deleteTimedMessagesByGuid(chatGuid, innerListener);
        } else {
            messageController.deleteNonTimedMessagesByGuid(chatGuid);
            clearAdapter(chatGuid);
            synchronized (chatDao) {
                final QueryBuilder<Chat> queryBuilder = chatDao.queryBuilder();
                final List<Chat> chats = queryBuilder.where(Properties.ChatGuid.eq(chatGuid)).list();

                for (final Chat chat : chats) {
                    chatDao.delete(chat);
                    chatDao.detach(chat);
                }
            }
        }
    }

    public void deleteMessage(final String guid,
                              final boolean bSyncExternal,
                              final OnDeleteTimedMessageListener listener) {
        final Message message = messageController.getMessageByGuid(guid);

        if (message != null) {
            deleteMessage(message, bSyncExternal, listener);
        }
    }

    public void deleteMessage(final BaseChatItemVO chatItemVO,
                              final OnDeleteTimedMessageListener listener) {
        Message message = null;

        if (chatItemVO != null) {
            if (chatItemVO.getMessageGuid() != null) {
                message = messageController.getMessageByGuid(chatItemVO.getMessageGuid());
            } else {
                message = messageController.getMessageById(chatItemVO.messageId);
            }
        }

        if (message != null) {
            deleteMessage(message, true, listener);
        }
    }

    public Message findMessageById(final long id) {
        return messageController.getMessageById(id);
    }

    @Override
    public void clearCache() {
        super.clearCache();
        synchronized (this) {
            chatItemRegistry = new HashMap<>();
        }
    }

    public void checkForResend(final long messageId,
                               final OnSendMessageListener onSendMessageListener) {
        final Message oldMessage = messageController.getMessageById(messageId);

        if (oldMessage == null) {
            onSendMessageListener.onSendMessageError(null,
                    mApplication.getResources().getString(R.string.service_tryAgainLater), null);
            return;
        }

        IBackendService.OnBackendResponseListener obesrl = new IBackendService.OnBackendResponseListener() {

            @Override
            public void onBackendResponse(BackendResponse response) {
                if(response == null) {
                    LogUtil.w(TAG, "checkForResend: onBackendResponse returns null!");
                    return;
                }

                if (response.isError) {
                    final String errorMsg = response.errorMessage != null ? response.errorMessage
                            : mApplication.getString(R.string.resend_message_error);

                    onSendMessageListener.onSendMessageError(oldMessage, errorMsg, null);
                } else {
                    if (response.jsonArray != null) {
                        try {
                            if (response.jsonArray.size() != 0) {
                                final JsonElement jElement = response.jsonArray.get(0);
                                if (jElement.isJsonObject()) {
                                    final JsonElement confirmMessageSend = jElement.getAsJsonObject().get("ConfirmMessageSend");
                                    if (confirmMessageSend != null) {
                                        final JsonObject cMSObject = confirmMessageSend.getAsJsonObject();
                                        if (cMSObject != null) {
                                            final String guid = cMSObject.get("guid").getAsString();

                                            if (!StringUtil.isNullOrEmpty(guid)) {
                                                messageController.markAsError(oldMessage, false);
                                                messageController.setMessageGuid(oldMessage, guid);
                                                messageController.markAsSentConfirmed(oldMessage, new Date().getTime());
                                                onSendMessageListener.onSendMessageSuccess(oldMessage, 0);
                                            }
                                        }
                                    }
                                }
                            } else //size == 0
                            {
                                resend(oldMessage, messageId, onSendMessageListener);
                            }
                        } catch (IllegalStateException e) {
                            // fehler beim konvertieren der JsonObjekte
                            LogUtil.e(TAG, "checkForResend: onBackendResponse caught " + e.getMessage());
                            resend(oldMessage, messageId, onSendMessageListener);
                        }
                    }
                }
            }
        };
        messageController.checkForResend(oldMessage.getRequestGuid(), obesrl);
    }

    private void resend(final Message oldMessage,
                        final long messageId,
                        final OnSendMessageListener onSendMessageListener
    ) {
        BuildMessageCallback buildMessageCallback = new BuildMessageCallback() {
            @Override
            public BaseMessageModel buildMessage() {
                BaseMessageModel baseMessage = null;

                if (oldMessage != null) {
                    baseMessage = MessageModelBuilder.getInstance(mApplication.getContactController())
                            .rebuildMessage(oldMessage, messageDecryptionController);
                }

                return baseMessage;
            }
        };

        OnSendMessageListener sendMessageListener = new OnSendMessageListener() {
            @Override
            public void onSaveMessageSuccess(Message message) {
                messageController.deleteMessage(oldMessage);
                deleteChatItemFromAdapter(messageId);

                if (onSendMessageListener != null) {
                    onSendMessageListener.onSaveMessageSuccess(message);
                }
            }

            @Override
            public void onSendMessageSuccess(Message message,
                                             int countNotSendMessages) {
                if (onSendMessageListener != null) {
                    onSendMessageListener.onSendMessageSuccess(message, countNotSendMessages);
                }
            }

            @Override
            public void onSendMessageError(Message message,
                                           String errorMessage, String localizedErrorIdentifier) {
                if (onSendMessageListener != null) {
                    onSendMessageListener.onSendMessageError(message, errorMessage, null);
                }
            }
        };

        messageController.sendMessage(sendMessageListener, buildMessageCallback);
    }

    public void sendSystemInfo(final String toGuid,
                               final String toPublicKeyXML,
                               final String message,
                               final long replaceMessageId) {
        sendSystemInfo(toGuid, toPublicKeyXML, message, replaceMessageId, null, false);
    }

    public void sendSystemInfo(final String toGuid,
                               final String toPublicKeyXML,
                               final String message,
                               final long replaceMessageId,
                               final OnSendMessageListener externalOnSendMessageListener,
                               final boolean isAbsentMessage) {
        OnSendMessageListener sendMessageListener = new OnSendMessageListener() {
            @Override
            public void onSaveMessageSuccess(Message message) {
                addSendMessage(toGuid, message, externalOnSendMessageListener);
            }

            @Override
            public void onSendMessageSuccess(Message message, int countNotSendMessages) {
                //
                if (externalOnSendMessageListener != null) {
                    externalOnSendMessageListener.onSendMessageSuccess(message, countNotSendMessages);
                }
            }

            @Override
            public void onSendMessageError(Message message, String errorMessage, String localizedErrorIdentifier) {
                //
                if (externalOnSendMessageListener != null) {
                    externalOnSendMessageListener.onSendMessageError(message, errorMessage, null);
                }
            }
        };

        BuildMessageCallback buildMsgCallback = new BuildMessageCallback() {
            @Override
            public BaseMessageModel buildMessage() {
                BaseMessageModel baseMessage = null;
                try {
                    final AESKeyDataContainer encryptionData = getEncryptionData(toGuid);

                    ContactController contactController = mApplication.getContactController();
                    AccountController accountController = mApplication.getAccountController();
                    KeyController keyController = mApplication.getKeyController();

                    baseMessage = MessageModelBuilder.getInstance(contactController).buildTextMessage(getMainTypeResponsibility(),
                            message,
                            accountController.getAccount(),
                            toGuid,
                            toPublicKeyXML,
                            keyController.getUserKeyPair(),
                            encryptionData.getAesKey(), encryptionData.getIv(), null, null, false, null);

                    baseMessage.isSystemMessage = true;
                    baseMessage.isAbesntMessage = isAbsentMessage;

                    if (replaceMessageId > -1) {
                        baseMessage.replaceMessageId = replaceMessageId;
                    }
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                }

                return baseMessage;
            }
        };

        if (!mApplication.getMainLooper().getThread().equals(Thread.currentThread()) && replaceMessageId > -1) {
            messageController.sendMessageInDBSync(sendMessageListener, buildMsgCallback);
        } else {
            messageController.sendMessage(sendMessageListener, buildMsgCallback);
        }
    }

    public void sendText(final String toGuid,
                         final String toPublicKeyXML,
                         final String message,
                         final MessageDestructionParams messageDestructionParams,
                         final OnSendMessageListener onSendMessageListener,
                         final Date sendDateTimed,
                         final boolean isPriority,
                         final CitationModel citation
    ) {
        BuildMessageCallback buildMessageCallback = new BuildMessageCallback() {
            @Override
            public BaseMessageModel buildMessage() {
                BaseMessageModel baseMessage = null;

                try {
                    final AESKeyDataContainer encryptionData = getEncryptionData(toGuid);

                    ContactController contactController = mApplication.getContactController();
                    AccountController accountController = mApplication.getAccountController();
                    KeyController keyController = mApplication.getKeyController();

                    baseMessage = MessageModelBuilder.getInstance(contactController).buildTextMessage(getTypeResponsibility()[0], message,
                            accountController.getAccount(),
                            toGuid,
                            toPublicKeyXML,
                            keyController.getUserKeyPair(),
                            encryptionData.getAesKey(),
                            encryptionData.getIv(),
                            messageDestructionParams,
                            sendDateTimed, isPriority, citation);

                    contactController.upgradeTrustLevel(toGuid, Contact.STATE_MIDDLE_TRUST);
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, "buildTextMessage", e);
                }

                return baseMessage;
            }
        };

        messageController.sendMessage(onSendMessageListener, buildMessageCallback);
    }

    // KS: APP_GINLO_CONTROL
    // TODO: Is the wrong place here for that.
    public void sendAppGinloControl(final String toGuid,
                        final String toPublicKeyXML,
                        final AppGinloControlMessage controlMessage,
                        final OnSendMessageListener onSendMessageListener
    ) {

        BuildMessageCallback buildMessageCallback = new BuildMessageCallback() {
            @Override
            public BaseMessageModel buildMessage() {
                BaseMessageModel baseMessage = null;

                try {
                    final AESKeyDataContainer encryptionData = getEncryptionData(toGuid);

                    ContactController contactController = mApplication.getContactController();
                    AccountController accountController = mApplication.getAccountController();
                    KeyController keyController = mApplication.getKeyController();

                    baseMessage = MessageModelBuilder.getInstance(contactController).buildAppGinloControlMessage(getMainTypeResponsibility(),
                            controlMessage,
                            accountController.getAccount(),
                            toGuid,
                            toPublicKeyXML,
                            keyController.getUserKeyPair(),
                            encryptionData.getAesKey(),
                            encryptionData.getIv());

                } catch (LocalizedException e) {
                    LogUtil.e(TAG, "buildAppGinloControlMessage", e);
                }

                return baseMessage;
            }
        };

        messageController.sendMessage(onSendMessageListener, buildMessageCallback);
    }

    // KS: AVC
    public void sendAVC(final String toGuid,
                         final String toPublicKeyXML,
                         final String roomInfo,
                         final OnSendMessageListener onSendMessageListener,
                         final Date sendDateTimed,
                         final boolean isPriority,
                         final CitationModel citation
    ) {

            BuildMessageCallback buildMessageCallback = new BuildMessageCallback() {
            @Override
            public BaseMessageModel buildMessage() {
                BaseMessageModel baseMessage = null;

                try {
                    final AESKeyDataContainer encryptionData = getEncryptionData(toGuid);

                    ContactController contactController = mApplication.getContactController();
                    AccountController accountController = mApplication.getAccountController();
                    KeyController keyController = mApplication.getKeyController();

                    baseMessage = MessageModelBuilder.getInstance(contactController).buildAVCMessage(getMainTypeResponsibility(),
                            roomInfo,
                            accountController.getAccount(),
                            toGuid,
                            toPublicKeyXML,
                            keyController.getUserKeyPair(),
                            encryptionData.getAesKey(),
                            encryptionData.getIv(),
                            sendDateTimed, isPriority, citation);

                    contactController.upgradeTrustLevel(toGuid, Contact.STATE_MIDDLE_TRUST);
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, "buildAVCMessage", e);
                }

                return baseMessage;
            }
        };

        messageController.sendMessage(onSendMessageListener, buildMessageCallback);
    }

    public void sendLocation(final String toGuid,
                             final String toPublicKeyXML,
                             final double longitude,
                             final double latitude,
                             final byte[] screenshot,
                             final OnSendMessageListener onSendMessageListener) {
        BuildMessageCallback buildMessageCallback = new BuildMessageCallback() {
            @Override
            public BaseMessageModel buildMessage() {
                BaseMessageModel baseMessage = null;

                try {
                    final AESKeyDataContainer encryptionData = getEncryptionData(toGuid);

                    ContactController contactController = mApplication.getContactController();
                    AccountController accountController = mApplication.getAccountController();
                    KeyController keyController = mApplication.getKeyController();

                    baseMessage = MessageModelBuilder.getInstance(contactController).buildLocationMessage(getMainTypeResponsibility(),
                            screenshot, longitude, latitude,
                            accountController.getAccount(),
                            toGuid, toPublicKeyXML,
                            keyController.getUserKeyPair(),
                            encryptionData.getAesKey(), encryptionData.getIv());

                    contactController.upgradeTrustLevel(toGuid, Contact.STATE_MIDDLE_TRUST);
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, "buildLocationMessage", e);
                }

                return baseMessage;
            }
        };

        messageController.sendMessage(onSendMessageListener, buildMessageCallback);
    }

    public void sendVCard(final String toGuid,
                          final String toPublicKeyXML,
                          final String vCard,
                          final String accountID,
                          final String accountGuid,
                          final OnSendMessageListener onSendMessageListener) {
        BuildMessageCallback buildMessageCallback = new BuildMessageCallback() {
            @Override
            public BaseMessageModel buildMessage() {
                BaseMessageModel baseMessage = null;

                try {
                    final AESKeyDataContainer encryptionData = getEncryptionData(toGuid);

                    ContactController contactController = mApplication.getContactController();
                    AccountController accountController = mApplication.getAccountController();
                    KeyController keyController = mApplication.getKeyController();

                    baseMessage = MessageModelBuilder.getInstance(contactController).buildVCardMessage(getTypeResponsibility()[0], vCard,
                            accountController.getAccount(),
                            toGuid,
                            toPublicKeyXML,
                            keyController.getUserKeyPair(),
                            encryptionData.getAesKey(),
                            encryptionData.getIv(),
                            accountID,
                            accountGuid);

                    contactController.upgradeTrustLevel(toGuid, Contact.STATE_MIDDLE_TRUST);
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, "buildVCardMessage", e);
                }

                return baseMessage;
            }
        };

        messageController.sendMessage(onSendMessageListener, buildMessageCallback);
    }

    public void sendFile(final Activity activity,
                         final String toGuid,
                         final String toPublicKeyXML,
                         final Uri fileUri,
                         final boolean copyFileToInternal,
                         final String fileName,
                         final String mimeType,
                         final OnSendMessageListener onSendMessageListener,
                         final CitationModel citation
    ) {
        final FileUtil fileUtil = new FileUtil(this.mApplication);
        final MimeUtil mu = new MimeUtil(this.mApplication);

        try {
            final String fileNameNext;
            final String mimeTypeNext;

            FileUtil fu = new FileUtil(activity);

            if (StringUtil.isNullOrEmpty(fileName)) {
                fileNameNext = fu.getFileName(fileUri);
            } else {
                fileNameNext = fileName;
            }

            if (StringUtil.isNullOrEmpty(mimeType)) {
                mimeTypeNext = mu.getMimeType(fileUri);
            } else {
                mimeTypeNext = mimeType;
            }

            final Uri tempUri;
            if (copyFileToInternal) {
                tempUri = fileUtil.copyFileToInternalDir(fileUri);
            } else {
                tempUri = fileUri;
            }

            if (tempUri == null) {
                return;
            }

            BuildMessageCallback buildMessageCallback = new BuildMessageCallback() {
                @Override
                public BaseMessageModel buildMessage() {
                    BaseMessageModel baseMessage = null;

                    try {
                        final AESKeyDataContainer encryptionData = getEncryptionData(toGuid);

                        ContactController contactController = mApplication.getContactController();
                        AccountController accountController = mApplication.getAccountController();
                        KeyController keyController = mApplication.getKeyController();

                        baseMessage = MessageModelBuilder.getInstance(contactController).buildFileMessage(getMainTypeResponsibility(), activity,
                                tempUri,
                                fileNameNext,
                                null,
                                mimeTypeNext,
                                accountController.getAccount(),
                                toGuid,
                                toPublicKeyXML,
                                keyController.getUserKeyPair(), encryptionData.getAesKey(), encryptionData.getIv(), citation);

                        contactController.upgradeTrustLevel(toGuid, Contact.STATE_MIDDLE_TRUST);
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, "buildFileMessage", e);
                    } finally {
                        if (copyFileToInternal) {
                            fileUtil.deleteFileByUriAndRevokePermission(tempUri);
                        }
                    }

                    return baseMessage;
                }
            };

            messageController.sendMessage(onSendMessageListener, buildMessageCallback);
        } catch (LocalizedException e) {
            LogUtil.e(TAG, "buildFileMessage", e);
        }
    }

    public void sendImage(final Activity activity,
                          final String toGuid,
                          final String toPublicKeyXML,
                          final Uri imageUri,
                          final String imageText,
                          final MessageDestructionParams messageDestructionParams,
                          final OnSendMessageListener onSendMessageListener,
                          final Date sendDateTimed,
                          final boolean isPriority,
                          final CitationModel citation,
                          final boolean deleteImg) {
        final FileUtil fileUtil = new FileUtil(this.mApplication);

        try {

            final Uri tempUri = fileUtil.copyFileToInternalDir(imageUri);
            if (tempUri == null) {
                return;
            }

            BuildMessageCallback buildMessageCallback = new BuildMessageCallback() {
                @Override
                public BaseMessageModel buildMessage() {
                    BaseMessageModel baseMessage = null;

                    try {
                        final AESKeyDataContainer encryptionData = getEncryptionData(toGuid);

                        ContactController contactController = mApplication.getContactController();
                        AccountController accountController = mApplication.getAccountController();
                        KeyController keyController = mApplication.getKeyController();

                        baseMessage = MessageModelBuilder.getInstance(contactController).buildImageMessage(getMainTypeResponsibility(), activity,
                                tempUri, imageText,
                                accountController.getAccount(),
                                toGuid,
                                toPublicKeyXML,
                                keyController.getUserKeyPair(), encryptionData.getAesKey(),
                                encryptionData.getIv(), messageDestructionParams, sendDateTimed, isPriority, citation);

                        contactController.upgradeTrustLevel(toGuid, Contact.STATE_MIDDLE_TRUST);
                    } catch (final LocalizedException e) {
                        LogUtil.e(TAG, "buildImageMessage", e);
                    } finally {
                        if (deleteImg || !tempUri.equals(imageUri)) {
                            fileUtil.deleteFileByUriAndRevokePermission(tempUri);
                        }
                    }

                    return baseMessage;
                }
            };

            messageController.sendMessage(onSendMessageListener, buildMessageCallback);
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
    }

    public void sendVideo(final Activity activity,
                          final String toGuid,
                          final String toPublicKeyXML,
                          final Uri videoUri,
                          final String description,
                          final MessageDestructionParams messageDestructionParams,
                          final OnSendMessageListener onSendMessageListener,
                          final Date sendDateTimed,
                          final boolean isPriority,
                          final boolean deleteVideo) {
        final FileUtil fileUtil = new FileUtil(this.mApplication);

        try {

            final Uri tempUri = fileUtil.copyFileToInternalDir(videoUri);
            if (tempUri == null) {
                return;
            }
            BuildMessageCallback buildMessageCallback = new BuildMessageCallback() {
                @Override
                public BaseMessageModel buildMessage() {
                    BaseMessageModel baseMessage = null;

                    try {
                        final AESKeyDataContainer encryptionData = getEncryptionData(toGuid);

                        ContactController contactController = mApplication.getContactController();
                        AccountController accountController = mApplication.getAccountController();
                        KeyController keyController = mApplication.getKeyController();

                        baseMessage = MessageModelBuilder.getInstance(contactController).buildVideoMessage(getMainTypeResponsibility(), activity,
                                tempUri,
                                description,
                                accountController.getAccount(),
                                toGuid,
                                toPublicKeyXML,
                                keyController.getUserKeyPair(), encryptionData.getAesKey(),
                                encryptionData.getIv(), messageDestructionParams, sendDateTimed, isPriority);

                        contactController.upgradeTrustLevel(toGuid, Contact.STATE_MIDDLE_TRUST);
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, "buildVideoMessage", e);
                    } finally {
                        if (deleteVideo || !tempUri.equals(videoUri)) {
                            fileUtil.deleteFileByUriAndRevokePermission(tempUri);
                        }
                    }

                    return baseMessage;
                }
            };

            messageController.sendMessage(onSendMessageListener, buildMessageCallback);
        } catch (LocalizedException e) {
            LogUtil.e(TAG, "buildVideoMessage", e);
        }
    }

    public void sendVoice(final Activity activity,
                          final String toGuid,
                          final String toPublicKeyXML,
                          final Uri voiceUri,
                          final MessageDestructionParams messageDestructionParams,
                          final OnSendMessageListener onSendMessageListener,
                          final Date sendDateTimed,
                          final boolean isPriority
    ) {
        BuildMessageCallback buildMessageCallback = new BuildMessageCallback() {
            @Override
            public BaseMessageModel buildMessage() {
                BaseMessageModel baseMessage = null;

                try {
                    final AESKeyDataContainer encryptionData = getEncryptionData(toGuid);

                    ContactController contactController = mApplication.getContactController();
                    AccountController accountController = mApplication.getAccountController();
                    KeyController keyController = mApplication.getKeyController();

                    baseMessage = MessageModelBuilder.getInstance(contactController).buildVoiceMessage(getMainTypeResponsibility(), activity,
                            voiceUri,
                            accountController.getAccount(),
                            toGuid,
                            toPublicKeyXML,
                            keyController.getUserKeyPair(), encryptionData.getAesKey(),
                            encryptionData.getIv(), messageDestructionParams, sendDateTimed, isPriority);

                    contactController.upgradeTrustLevel(toGuid, Contact.STATE_MIDDLE_TRUST);
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, "buildVoiceMessage", e);
                }

                return baseMessage;
            }
        };

        messageController.sendMessage(onSendMessageListener, buildMessageCallback);
    }

    @Override
    public void onNewMessages(final int types) {
        final int[] chatTypes = getTypeResponsibility();
        boolean hasNewMessages = false;

        for (final int type : chatTypes) {
            int newMessageType = AppConstants.getNewMessageFlag(type);
            if ((types & newMessageType) == newMessageType) {
                hasNewMessages = true;
            }
        }

        if (!hasNewMessages) {
            return;
        }

        loadNewMessages();
    }

    @Override
    public void onMessagesChanged(@NotNull final SparseArray<List<Message>> messagesListContainer) {
        synchronized (this) {
            final int[] types = getTypeResponsibility();

            for (int i = 0; i < types.length; i++) {
                final List<Message> messages = messagesListContainer.get(types[i]);

                if (messages == null) {
                    continue;
                }

                boolean notifyAdapter = false;
                for (final Message message : messages) {
                    if (message.getId() != null) {
                        final String guid = (message.getType() == Message.TYPE_GROUP)
                                ? getGroupGuidForMessage(message) : getGuidForMessage(message);

                        if (mCurrentChatAdapter != null && StringUtil.isEqual(mCurrentChatAdapter.getChatGuid(), guid)) {
                            for (int k = 0; k < mCurrentChatAdapter.getCount(); k++) {
                                BaseChatItemVO item = mCurrentChatAdapter.getItem(k);
                                if (item != null && item.messageId == message.getId()) {
                                    item.setCommonValues(message);
                                    break;
                                }
                            }

                            notifyAdapter = true;
                        } else if (chatItemRegistry != null) {
                            synchronized (chatItemRegistry) {
                                LongSparseArray<BaseChatItemVO> chatItemArray = chatItemRegistry.get(guid);
                                if (chatItemArray != null) {
                                    for (int k = 0; k < chatItemArray.size(); k++) {
                                        BaseChatItemVO item = chatItemArray.valueAt(k);
                                        if (item != null && message.getId() != null && item.messageId == message.getId()) {
                                            item.setCommonValues(message);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (notifyAdapter) {
                    LogUtil.i(TAG, "Notify Adapter");

                    final Activity activity = mApplication.getAppLifecycleController().getTopActivity();

                    if (activity != null && mCurrentChatAdapter != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (mCurrentChatAdapter != null) {
                                    mCurrentChatAdapter.notifyDataSetChanged();
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    public void addSendMessage(final String targetGuid,
                               final Message oneMessage) {
        addSendMessage(targetGuid, oneMessage, null);
    }

    private void addSendMessage(final String targetGuid,
                                final Message oneMessage,
                                final OnSendMessageListener onSendMessageListener) {
        if (mCurrentChatAdapter == null || !StringUtil.isEqual(mCurrentChatAdapter.getChatGuid(), targetGuid)) {
            if (onSendMessageListener != null) {
                onSendMessageListener.onSaveMessageSuccess(oneMessage);
            }
            return;
        }

        final List<Message> messages = new ArrayList<>(1);

        messages.add(oneMessage);

        final ConcurrentTaskListener listener = new ConcurrentTaskListener() {
            @Override
            public void onStateChanged(final ConcurrentTask task,
                                       final int state) {
                if (state == ConcurrentTask.STATE_COMPLETE) {
                    if (task != null) {
                        if (task.getResults() != null) {
                            final Object listObject = task.getResults()[0];

                            if ((listObject instanceof List<?>)) {
                                final List<?> itemList = (List<?>) listObject;

                                long lastMsgId = -1;

                                for (int i = 0; i < itemList.size(); i++) {
                                    final BaseChatItemVO itemObject = (BaseChatItemVO) itemList.get(i);

                                    if (itemObject != null) {
                                        if (itemObject.messageId > lastMsgId) {
                                            lastMsgId = itemObject.messageId;
                                        }

                                        addToRegistry(targetGuid, itemObject, i == (itemList.size() - 1), true);

                                        if (onSendMessageListener != null) {
                                            onSendMessageListener.onSaveMessageSuccess(oneMessage);
                                        }
                                    }
                                }
                                notifyListenerLoaded(lastMsgId);
                            }
                        }
                    }
                }
            }
        };

        final ConvertToChatItemVOTask task = new ConvertToChatItemVOTask(targetGuid, this, mApplication,
                messages, false);

        task.addListener(listener);
        task.run();
    }

    public void countTimedMessages(final QueryDatabaseListener listener) {
        if (mCurrentChatAdapter == null || listener == null) {
            return;
        }
        final QueryDatabaseListener queryDatabaseListener = new QueryDatabaseListener() {
            @Override
            public void onListResult(@NotNull List<Message> messages) {

            }

            @Override
            public void onUniqueResult(@NotNull Message message) {

            }

            @Override
            public void onCount(final long count) {
                new Handler(Looper.getMainLooper()).post(
                        new Runnable() {
                            @Override
                            public void run() {
                                listener.onCount(count);
                            }
                        }
                );
            }
        };

        messageController.countTimedMessages(mCurrentChatAdapter.getChatGuid(), queryDatabaseListener);
    }

    public boolean loadNewestMessagesByGuid() {
        if (mCurrentChatAdapter == null) {
            return false;
        }

        final long maximumLoadedMessageId = getMinMaxLoadedMessageId(false);

        final QueryDatabaseListener queryDatabaseListener = new QueryDatabaseListener() {
            @Override
            public void onListResult(final List<Message> messages) {
                if (messages.size() >= 1) {
                    if (messages.size() == LOAD_NEWEST_MESSAGE_COUNT) {
                        Message lastMsg = messages.get(messages.size() - 1);

                        if (lastMsg != null) {
                            long minId = lastMsg.getId();
                            Message firstMsg = messages.get(0);

                            //Die Message mit der kleinsten Id entfernen, da nur 20 angezeigt werden sollen
                            if ((firstMsg != null) && (minId > firstMsg.getId())) {
                                minId = (minId < firstMsg.getId()) ? minId : firstMsg.getId();
                                messages.remove(0);
                            } else {
                                messages.remove(messages.size() - 1);
                            }

                            //wenn die Ids nicht uebereinstimmen, gibts es noch weitere Nachrichten die noch nicht geladen wurden
                            if ((maximumLoadedMessageId != -1) && (minId != maximumLoadedMessageId)) {
                                //daher muss der Adapter geleert werden, ansonsten ist die Liste im Adapter nicht korrekt
                                new Handler(Looper.getMainLooper()).post(
                                        new Runnable() {
                                            @Override
                                            public void run() {

                                                if (mCurrentChatAdapter != null) {
                                                    mCurrentChatAdapter.clear();
                                                }
                                            }
                                        });
                            }
                        }
                    }

                    filterMessages(messages);
                } else {
                    //keine Nachrichten
                    notifyListenerLoaded(NO_MESSAGE_ID_FOUND);
                }
            }

            @Override
            public void onUniqueResult(@NotNull Message message) {

            }

            @Override
            public void onCount(long count) {

            }
        };

        int count = (maximumLoadedMessageId != -1) ? LOAD_NEWEST_MESSAGE_COUNT : (LOAD_NEWEST_MESSAGE_COUNT - 1);

        messageController.loadNextMessages(mCurrentChatAdapter.getChatGuid(), getMainTypeResponsibility(), count, maximumLoadedMessageId, false, false,
                queryDatabaseListener);

        return true;
    }

    public long getChatMessagesCount(final String guid, final boolean includeTimed) {
        if (guid == null) {
            return 0;
        }
        return messageController.getNumMessagesByChatGuid(guid, getMainTypeResponsibility(), includeTimed);
    }

    public void loadMessagesFromIdToId(Long start, Long end, final OnChatDataChangedListener listener) {

        final QueryDatabaseListener queryDatabaseListener = new QueryDatabaseListener() {
            @Override
            public void onListResult(final List<Message> messages) {
                if (messages.size() >= 1) {
                    messageController.markReceivedMessagesAsRead(messages);

                    filterMessages(messages, listener);
                } else {
                    listener.onChatDataLoaded(ChatController.NO_MESSAGE_ID_FOUND);
                }
            }

            @Override
            public void onUniqueResult(@NotNull Message message) {

            }

            @Override
            public void onCount(long count) {

            }
        };

        messageController.loadMessageRange(mCurrentChatAdapter.getChatGuid(), getMainTypeResponsibility(), start, end, queryDatabaseListener);
    }

    public void loadMoreMessages(final int count) {
        if (mCurrentChatAdapter == null) {
            return;
        }

        final QueryDatabaseListener queryDatabaseListener = new QueryDatabaseListener() {
            @Override
            public void onListResult(final List<Message> messages) {
                if (messages.size() >= 1) {
                    messageController.markReceivedMessagesAsRead(messages);

                    filterMessages(messages);
                } else {
                    notifyListenerLoaded(NO_MESSAGE_ID_FOUND);
                }
            }

            @Override
            public void onUniqueResult(@NotNull Message message) {

            }

            @Override
            public void onCount(long count) {

            }
        };

        final long minimumLoadedMessagId = getMinMaxLoadedMessageId(true);

        messageController.loadNextMessages(mCurrentChatAdapter.getChatGuid(), getMainTypeResponsibility(), count, minimumLoadedMessagId, true, false,
                queryDatabaseListener);
    }

    public void getTimedMessages() {

        final QueryDatabaseListener queryDatabaseListener = new QueryDatabaseListener() {
            @Override
            public void onListResult(@NotNull final List<Message> messages) {
                filterMessages(messages, true);
            }

            @Override
            public void onUniqueResult(@NotNull Message message) {

            }

            @Override
            public void onCount(long count) {

            }
        };

        if (mCurrentChatAdapter != null) {
            messageController.loadNextMessages(mCurrentChatAdapter.getChatGuid(), getMainTypeResponsibility(), 1000, -1, false, true, queryDatabaseListener);
        }
    }

    private void loadNewMessages() {
        final QueryDatabaseListener queryDatabaseListener = new QueryDatabaseListener() {
            @Override
            public void onListResult(@NotNull final List<Message> messages) {
                filterMessages(messages);
                messageController.markReceivedMessagesAsRead(messages);
            }

            @Override
            public void onUniqueResult(@NotNull Message message) {

            }

            @Override
            public void onCount(long count) {

            }
        };

        if (mCurrentChatAdapter != null) {
            long lastMsgId = -1;
            if (mCurrentChatAdapter.getCount() > 0) {
                BaseChatItemVO lastItem = mCurrentChatAdapter.getItem(mCurrentChatAdapter.getCount() - 1);
                if (lastItem != null) {
                    lastMsgId = lastItem.messageId;
                }
            }
            messageController.getLatestMessagesByContactGuid(mCurrentChatAdapter.getChatGuid(), getMainTypeResponsibility(), lastMsgId, queryDatabaseListener);
        }
    }

    public void markAllUnreadChatMessagesAsRead(final String guid) {
        final QueryDatabaseListener queryDatabaseListener = new QueryDatabaseListener() {
            @Override
            public void onListResult(@NotNull final List<Message> messages) {
                messageController.markReceivedMessagesAsRead(messages);

                if (!StringUtil.isNullOrEmpty(guid)) {
                    mApplication.getChatOverviewController().chatChanged(null, guid, null, ChatOverviewController.CHAT_CHANGED_HAS_READ_MSG);
                }
            }

            @Override
            public void onUniqueResult(@NotNull Message message) {

            }

            @Override
            public void onCount(long count) {

            }
        };
        messageController.getUnreadMessagesByContactGuid(guid, getMainTypeResponsibility(), queryDatabaseListener);
    }

    public ChatAdapter getChatAdapter(final Activity activity,
                                      final String guid) {
        synchronized (chatItemRegistry) {
            ArrayList<BaseChatItemVO> itemList = new ArrayList<>();

            if (chatItemRegistry.containsKey(guid)) {
                LongSparseArray<BaseChatItemVO> chatItemArray = chatItemRegistry.get(guid);
                if (chatItemArray != null) {
                    for (int i = 0; i < chatItemArray.size(); i++) {
                        BaseChatItemVO item = chatItemArray.valueAt(i);
                        itemList.add(item);
                    }
                }
            }

            return new ChatAdapter(activity, mApplication, taskManagerController,
                    R.layout.chat_item_image_right_layout, itemList, guid, mContext);
        }
    }

    public void insertOrUpdateChat(final Chat chat) {
        if (chat.getId() != null) {
            synchronized (chatDao) {
                chatDao.update(chat);
            }
        } else {
            synchronized (chatDao) {
                chatDao.insert(chat);
            }
        }
    }

    public List<Chat> loadAll() {
        synchronized (chatDao) {
            return chatDao.loadAll();
        }
    }

    public void refresh(Chat chat) {
        if (chat == null) {
            return;
        }

        synchronized (chatDao) {
            chatDao.refresh(chat);
        }
    }

    public Chat getChatByGuid(final String guid) {
        if (guid == null) {
            return null;
        }

        synchronized (chatDao) {
            final QueryBuilder<Chat> queryBuilder = chatDao.queryBuilder();
            final List<Chat> chats = queryBuilder.where(Properties.ChatGuid.eq(guid)).build().forCurrentThread().list();

            if (chats != null) {
                if (chats.size() == 1) {
                    return chats.get(0);
                } else if (chats.size() > 1) {
                    Chat returnValue = chats.get(0);

                    for (int i = 1; i < chats.size(); i++) {
                        Chat chat = chats.get(i);
                        chatDao.delete(chat);
                        chatDao.detach(chat);
                    }
                    return returnValue;
                }
            }

            return null;
        }
    }

    public void getAttachment(final AttachmentChatItemVO attachmentChatItemVO,
                              final OnAttachmentLoadedListener listener, boolean safeToShareFolder,
                              final HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener) {
        getAttachment(attachmentChatItemVO.messageId, listener, safeToShareFolder, onConnectionDataUpdatedListener);
    }

    public void getAttachment(final long messageId,
                              final OnAttachmentLoadedListener listener, boolean safeToShareFolder,
                              final HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener) {
        try {
            final Message message = messageController.getMessageById(messageId);

            if (message == null) {
                listener.onHasNoAttachment(mApplication.getString(R.string.chats_load_attachment_failed));
                return;
            }

            final DecryptedMessage decryptedMessage = messageDecryptionController.decryptMessage(message, false);
            if (decryptedMessage == null) {
                listener.onLoadedFailed("Error decrypting message.");
                return;
            }

            final String attachmentGuid = decryptedMessage.getMessage().getAttachment();
            if (attachmentGuid == null) {
                listener.onHasNoAttachment(null);
                return;
            }

            listener.onHasAttachment(attachmentController.isAttachmentLocallyAvailable(attachmentGuid));
            attachmentController.loadAttachment(decryptedMessage, listener, safeToShareFolder, onConnectionDataUpdatedListener);
        } catch (LocalizedException e) {
            LogUtil.e(TAG, "getAttachment: Caught " + e.getMessage());
            listener.onLoadedFailed("Error decrypting message.");
        }
    }

    public abstract void acceptInvitation(Chat chat,
                                          OnAcceptInvitationListener onAcceptInvitationListener);

    public abstract void declineInvitation(Chat chat,
                                           OnDeclineInvitationListener onDeclineInvitationListener);

    protected abstract int[] getTypeResponsibility();

    protected abstract int getMainTypeResponsibility();

    synchronized void addToRegistry(final String accountGuid,
                                    final BaseChatItemVO chatItemVO,
                                    final boolean notifyObserver,
                                    final boolean sort) {

        LogUtil.d(TAG, "addToRegistry: " + chatItemVO.getFromGuid() + " --> " + chatItemVO.messageId);

        if (mCurrentChatAdapter != null && StringUtil.isEqual(mCurrentChatAdapter.getChatGuid(), accountGuid)) {
            mCurrentChatAdapter.setNotifyOnChange(notifyObserver);

            if (sort) {
                if (mCurrentChatAdapter.getCount() == 0) {
                    mCurrentChatAdapter.add(chatItemVO);
                } else {
                    if (mCurrentChatAdapter.getCount() == 1) {
                        if (chatItemVO.messageId > mCurrentChatAdapter.getItem(0).messageId) {
                            mCurrentChatAdapter.add(chatItemVO);
                        } else if (chatItemVO.messageId < mCurrentChatAdapter.getItem(0).messageId) {
                            mCurrentChatAdapter.insert(chatItemVO, 0);
                        }
                    } else {
                        final long firstValue = mCurrentChatAdapter.getItem(0).messageId;
                        final long lastValue = mCurrentChatAdapter.getItem(mCurrentChatAdapter.getCount() - 1).messageId;

                        if (chatItemVO.messageId < firstValue) {
                            mCurrentChatAdapter.insert(chatItemVO, 0);
                        } else {
                            if (chatItemVO.messageId > lastValue) {
                                mCurrentChatAdapter.add(chatItemVO);
                            } else {
                                final int insertIndex = rank(chatItemVO.messageId, mCurrentChatAdapter);

                                if (insertIndex > -1) {
                                    mCurrentChatAdapter.insert(chatItemVO, insertIndex);
                                } else if (insertIndex != -2) {
                                    mCurrentChatAdapter.add(chatItemVO);
                                }
                            }
                        }
                    }
                }
            } else {
                mCurrentChatAdapter.add(chatItemVO);
            }
            mCurrentChatAdapter.setNotifyOnChange(true);
        }
    }

    private void deleteChatItemFromAdapter(Long msgId) {
        if ((msgId == null) || (mCurrentChatAdapter == null) || (mCurrentChatAdapter.getCount() == 0)) {
            return;
        }

        for (int i = 0; i < mCurrentChatAdapter.getCount(); i++) {
            BaseChatItemVO item = mCurrentChatAdapter.getItem(i);
            if (item != null && item.messageId == msgId) {
                mCurrentChatAdapter.remove(item);
            }
        }
    }

    private long getMinMaxLoadedMessageId(boolean getMinId) {
        if ((mCurrentChatAdapter == null) || (mCurrentChatAdapter.getCount() == 0)) {
            return -1;
        }

        long rc = -1;

        final BaseChatItemVO firstChatItem = mCurrentChatAdapter.getItem(0);

        final BaseChatItemVO lastChatItemVO = mCurrentChatAdapter.getItem(mCurrentChatAdapter.getCount() - 1);

        if (firstChatItem.messageId > -1) {
            rc = firstChatItem.messageId;
        }

        if (getMinId) {
            rc = (lastChatItemVO.messageId < rc) ? lastChatItemVO.messageId : rc;
        } else {
            rc = (lastChatItemVO.messageId > rc) ? lastChatItemVO.messageId : rc;
        }

        return rc;
    }

    private void deleteMessage(final Message message, final boolean bSyncExternal, final OnDeleteTimedMessageListener listener) {
        if (bSyncExternal) {

            if (message.getDateSendTimed() != null && message.getGuid() != null) {
                final IBackendService.OnBackendResponseListener obesrl = new IBackendService.OnBackendResponseListener() {

                    @Override
                    public void onBackendResponse(BackendResponse response) {

                        if (response.isError) {

                            if (listener != null) {
                                final String errorMsg = response.errorMessage != null ? response.errorMessage
                                        : mApplication.getString(R.string.delete_message_error);

                                listener.onDeleteMessageError(errorMsg);

                                LogUtil.w(TAG, "deleteMessage: " + errorMsg);
                            }
                        } else {
                            final JsonArray jArray = response.jsonArray;
                            if (jArray != null && jArray.size() != 0) {
                                final JsonElement jElemenet = jArray.get(0);
                                if (StringUtil.isEqual(jElemenet.getAsString(), message.getGuid())) {
                                    deleteMessageHelper(message, listener);
                                }
                            } else {
                                listener.onDeleteMessageError(mApplication.getString(R.string.delete_message_error));
                                LogUtil.w(TAG, "deleteMessage: JsonArray null or empty");
                            }
                        }
                    }
                };
                messageController.deleteTimedMessage(message.getGuid(), obesrl);
            } else {
                if (!StringUtil.isNullOrEmpty(message.getGuid())) {
                    final IBackendService.OnBackendResponseListener obesrl = new IBackendService.OnBackendResponseListener() {

                        @Override
                        public void onBackendResponse(BackendResponse response) {

                            if (response.isError) {

                                if (listener != null) {
                                    final String errorMsg = response.errorMessage != null ? response.errorMessage
                                            : mApplication.getString(R.string.delete_message_error);

                                    LogUtil.w(TAG, "deleteMessage: " + errorMsg);
                                }
                            } else {
                                deleteMessageHelper(message, listener);
                            }
                        }
                    };

                    JsonArray guids = new JsonArray();
                    guids.add(new JsonPrimitive(message.getGuid()));
                    BackendService.withAsyncConnection(mApplication)
                            .setMessageState(guids, AppConstants.MESSAGE_STATE_DELETED, obesrl, false);
                } else {
                    deleteMessageHelper(message, listener);
                }
            }
        } else {
            deleteMessageHelper(message, listener);
        }
    }

    private void deleteMessageHelper(final Message message, final OnDeleteTimedMessageListener listener) {
        if (message != null) {
            LogUtil.i(TAG, "DeleteMessage " + message.getGuid());

            if (message.getAttachment() != null) {
                attachmentController.deleteAttachment(message.getAttachment());
            }

            if (messageDecryptionController.decryptMessage(message, false) != null &&
                    messageDecryptionController.decryptMessage(message, false).getMessageDestructionParams() != null) {
                messageController.deleteNewDestructionDate(message.getGuid());
            }

            final Chat chat = getChatByGuid(getGuidForMessage(message));

            if (chat != null) {
                chat.setLastChatModifiedDate(message.getDateSend());
                Long msgId = message.getId();
                Long latestMsgId = chat.getLastMsgId();

                if (latestMsgId != null && msgId != null && msgId.longValue() == latestMsgId.longValue()) {
                    chat.setLastMsgId(null);
                }

                synchronized (chatDao) {
                    chatDao.update(chat);
                }

                chatOverviewController.chatChanged(null, chat.getChatGuid(), message, ChatOverviewController.CHAT_CHANGED_REFRESH_CHAT);
            }

            List<String> chatGuids = new ArrayList<>();
            if (message.getDateSendTimed() != null) {
                String chatGuid = MessageDataResolver.getGuidForMessage(message);
                chatGuids.add(chatGuid);
            }

            messageController.deleteMessage(message);

            if (chatGuids.size() > 0) {
                messageController.notifyOnTimedMessagesDeliveredListeners(chatGuids);
            }

            if (chat != null) {
                synchronized (chatItemRegistry) {
                    LongSparseArray<BaseChatItemVO> chatItemArray = chatItemRegistry.get(chat.getChatGuid());

                    if (chatItemArray != null && chatItemArray.size() > 0) {
                        chatItemArray.remove(message.getId());
                    }
                }

                if (mCurrentChatAdapter != null && StringUtil.isEqual(mCurrentChatAdapter.getChatGuid(), chat.getChatGuid())) {
                    mCurrentChatAdapter.removerItemByGuid(message.getId(), true);
                }
                if (listener != null) {
                    listener.onDeleteSingleMessageSuccess(chat.getChatGuid());
                }
            }

            notifyListenerChanged();
        }
    }

    public abstract AESKeyDataContainer getEncryptionData(final String recipientGuid)
            throws LocalizedException;

    public void clearAdapter(final String guid) {
        synchronized (chatItemRegistry) {
            chatItemRegistry.remove(guid);
        }

        if (mCurrentChatAdapter != null && StringUtil.isEqual(mCurrentChatAdapter.getChatGuid(), guid)) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    mCurrentChatAdapter.clear();
                }
            });
        }
    }

    void filterMessages(final List<Message> messages) {
        if (mCurrentChatAdapter != null) {
            filterMessages(messages, mCurrentChatAdapter.getShowTimedMessages(), null);
        } else {
            filterMessages(messages, false, null);
        }
    }

    private void filterMessages(final List<Message> messages, final boolean onlyShowTimedMessages) {
        filterMessages(messages, onlyShowTimedMessages, null);
    }

    private void filterMessages(final List<Message> messages, final OnChatDataChangedListener onChatDataChangedListener) {
        filterMessages(messages, false, onChatDataChangedListener);
    }

    private void filterMessages(final List<Message> messages, final boolean onlyShowTimedMessages, final OnChatDataChangedListener onChatDataChangedListener) {
        final ConcurrentTaskListener listener = new ConcurrentTaskListener() {
            @Override
            public void onStateChanged(final ConcurrentTask task,
                                       final int state) {
                if (state == ConcurrentTask.STATE_COMPLETE) {
                    long lastMsgId = -1;

                    if (task != null && mCurrentChatAdapter != null) {
                        if (task.getResults() != null) {
                            final Object listObject = task.getResults()[0];

                            if ((listObject instanceof List<?>)) {
                                final List<?> itemList = (List<?>) listObject;

                                for (int i = 0; i < itemList.size(); i++) {
                                    final BaseChatItemVO itemObject = (BaseChatItemVO) itemList.get(i);

                                    if (itemObject != null) {
                                        if (itemObject.messageId > lastMsgId) {
                                            lastMsgId = itemObject.messageId;
                                        }

                                        addToRegistry(mCurrentChatAdapter.getChatGuid(), itemObject, i == (itemList.size() - 1), !onlyShowTimedMessages);
                                    }
                                }
                            }
                        }
                    }

                    if (onChatDataChangedListener != null) {
                        onChatDataChangedListener.onChatDataLoaded(lastMsgId);
                    } else {
                        notifyListenerLoaded(lastMsgId);
                    }
                } else if (state == ConcurrentTask.STATE_CANCELED || state == ConcurrentTask.STATE_ERROR) {
                    if (onChatDataChangedListener != null) {
                        onChatDataChangedListener.onChatDataLoaded(-1);
                    } else {
                        notifyListenerLoaded(-1);
                    }
                }
            }
        };

        if (mCurrentChatAdapter != null) {
            chatTaskManager.executeConvertToChatItemVOTask(mCurrentChatAdapter.getChatGuid(), this, mApplication, messages, listener, onlyShowTimedMessages);
        } else {
            notifyListenerLoaded(-1);
        }
    }

    void refreshAdapter() {
        synchronized (this) {
            if (mCurrentChatAdapter == null) {
                return;
            }

            final Activity activity = mApplication.getAppLifecycleController().getTopActivity();

            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mCurrentChatAdapter != null) {
                            mCurrentChatAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onImageDataChanged(final String guid) {
        refreshAdapter();
    }

    public void setCurrentChatAdapter(ChatAdapter adapter) {
        mCurrentChatAdapter = adapter;
    }

    public void removeCurrentChatAdapter(ChatAdapter adapter) {
        if (mCurrentChatAdapter != adapter || adapter == null)
            return;
        mCurrentChatAdapter = null;

        LongSparseArray<BaseChatItemVO> chatItemArray = new LongSparseArray<>(adapter.getCount());
        for (int i = 0; i < adapter.getCount(); i++) {
            BaseChatItemVO item = adapter.getItem(i);

            if (item != null)
                chatItemArray.put(item.messageId, item);
        }
        chatItemRegistry.put(adapter.getChatGuid(), chatItemArray);
    }

    public void silenceChat(final String chatGuid, final String dateString, final SilenceChatListener silenceChatListener) {
    }
}
