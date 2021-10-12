// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller;

import android.content.Context;
import android.os.Handler;
import android.util.ArrayMap;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.chatsOverview.ChatsAdapter;
import eu.ginlo_apps.ginlo.activity.chatsOverview.ChatsOverviewActivity;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.manager.ChatOverviewTaskManager;
import eu.ginlo_apps.ginlo.concurrent.task.AsyncHttpTask;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.controller.message.MessageDataResolver;
import eu.ginlo_apps.ginlo.controller.message.contracts.MessageControllerListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnChatDataChangedListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.QueryDatabaseListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.chat.overview.BaseChatOverviewItemVO;
import eu.ginlo_apps.ginlo.model.chat.overview.ChannelChatOverviewItemVO;
import eu.ginlo_apps.ginlo.model.chat.overview.ChatOverviewItemVO;
import eu.ginlo_apps.ginlo.model.chat.overview.GroupChatOverviewItemInvitationVO;
import eu.ginlo_apps.ginlo.model.chat.overview.GroupChatOverviewItemVO;
import eu.ginlo_apps.ginlo.model.chat.overview.ServiceChatOverviewItemVO;
import eu.ginlo_apps.ginlo.model.chat.overview.SingleChatOverviewItemInvitationVO;
import eu.ginlo_apps.ginlo.model.chat.overview.SingleChatOverviewItemVO;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.OnImageDataChangedListener;
import eu.ginlo_apps.ginlo.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static eu.ginlo_apps.ginlo.greendao.Contact.CLASS_PRIVATE_ENTRY;

public class ChatOverviewController
        extends MessageDataResolver
        implements MessageControllerListener,
        OnImageDataChangedListener {

    private static final String TAG = ChatOverviewController.class.getSimpleName();

    public static final int MODE_OVERVIEW = 0;
    public static final int MODE_FORWARD_SINGLE = 1;
    public static final int MODE_FORWARD_GROUP = 2;

    public static final int CHAT_CHANGED_IMAGE = 0x1;
    public static final int CHAT_CHANGED_TITLE = 0x1 << 1;
    public static final int CHAT_CHANGED_ACCEPT_CHAT = 0x1 << 2;
    public static final int CHAT_CHANGED_HAS_READ_MSG = 0x1 << 3;
    public static final int CHAT_CHANGED_NEW_SEND_MSG = 0x1 << 4;
    public static final int CHAT_CHANGED_CLEAR_CHAT = 0x1 << 5;
    public static final int CHAT_CHANGED_DELETE_CHAT = 0x1 << 6;
    public static final int CHAT_CHANGED_REFRESH_CHAT = 0x1 << 7;
    public static final int CHAT_CHANGED_NEW_CHAT = 0x1 << 8;
    public static final int CHAT_CHANGED_MSG_DELETED = 0x1 << 9;

    private final ChatOverviewTaskManager chatOverviewTaskManager;
    private final CopyOnWriteArrayList<OnChatDataChangedListener> listeners;
    private final List<BaseChatOverviewItemVO> mOverviewMessages = new ArrayList<>();
    private ChatsAdapter chatsAdapter;
    private HashMap<Long, BaseChatOverviewItemVO> mItemMap;
    private List<List<Message>> mChangedMessages;
    private ConcurrentTask mChatOverviewTask;
    private boolean mStartChatOverviewTaskAgain;
    private ChatsOverviewActivity.ListRefreshedListener mListRefreshedListener;
    private int mMode;
    private Comparator<BaseChatOverviewItemVO> mComparator;
    private ArrayList<String> mChatsToRefresh;
    private AsyncHttpTask<ArrayMap<String, Boolean>> mCheckContactsOnlineTask;

    public ChatOverviewController(final SimsMeApplication application) {
        super(application);

        this.listeners = new CopyOnWriteArrayList<>();

        application.getChatImageController().addListener(this);

        this.chatOverviewTaskManager = new ChatOverviewTaskManager();
    }

    public void setAdapter(final ChatsAdapter adapter) {
        synchronized (this) {
            chatsAdapter = adapter;
        }
    }

    public void addListener(final OnChatDataChangedListener listener) {
        listeners.add(listener);
    }

    public void removeListener(final OnChatDataChangedListener listener) {
        listeners.remove(listener);
    }

    private void notifyListener(final boolean clearImageLoader) {
        Handler handler = new Handler(mApplication.getMainLooper());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                for (final OnChatDataChangedListener listener : listeners) {
                    LogUtil.d(TAG, "notifyListener clear: " + clearImageLoader + ";  Listener: " + listener.toString());
                    listener.onChatDataChanged(clearImageLoader);
                }
            }
        };
        handler.post(runnable);
    }

    private void notifyListenerLoaded() {
        for (final OnChatDataChangedListener listener : listeners) {
            if (listener != null) {
                LogUtil.d(TAG, "notifyListenerLoaded Listener: " + listener.toString());
                listener.onChatDataLoaded(ChatController.NO_MESSAGE_ID_FOUND);
            }
        }
    }

    private void createNotifications() {
        chatOverviewTaskManager.executeCreateNotificationChatTask(mApplication);
    }

    @Override
    public void onNewMessages(final int types) {
        if (((types & AppConstants.Message.NewMessagesStates.TYPE_GROUP) == AppConstants.Message.NewMessagesStates.TYPE_GROUP)
                || ((types & AppConstants.Message.NewMessagesStates.TYPE_GROUP_INVITATION) == AppConstants.Message.NewMessagesStates.TYPE_GROUP_INVITATION)
                || ((types & AppConstants.Message.NewMessagesStates.TYPE_PRIVATE) == AppConstants.Message.NewMessagesStates.TYPE_PRIVATE)
                || ((types & AppConstants.Message.NewMessagesStates.TYPE_CHANNEL) == AppConstants.Message.NewMessagesStates.TYPE_CHANNEL)) {
            startChatOverviewTask(true, null);
        }

        //Nicht bei Gruppeneinladungen starten
        if (((types & AppConstants.Message.NewMessagesStates.TYPE_GROUP) == AppConstants.Message.NewMessagesStates.TYPE_GROUP)
                || ((types & AppConstants.Message.NewMessagesStates.TYPE_PRIVATE) == AppConstants.Message.NewMessagesStates.TYPE_PRIVATE)
                || ((types & AppConstants.Message.NewMessagesStates.TYPE_CHANNEL) == AppConstants.Message.NewMessagesStates.TYPE_CHANNEL)) {
            // BUG 39523 Notifications sollen immer angezeigt werden
            createNotifications();
        }
    }

    @Override
    public void onMessagesChanged(@NotNull final SparseArray<List<Message>> messagesListContainer) {
        final List<Message> groupMessages = messagesListContainer.get(Message.TYPE_GROUP);
        final List<Message> privateMessages = messagesListContainer.get(Message.TYPE_PRIVATE);

        if ((groupMessages == null) && (privateMessages == null)) {
            return;
        }

        synchronized (this) {
            // map wurde noch nicht initialisiert, view lädt wahrscheinlich
            // noch
            if (mItemMap == null) {
                if ((groupMessages != null) && (groupMessages.size() > 0)) {
                    // messages zurückstellen bis itemmap initialsiert wurde
                    if (mChangedMessages == null) {
                        mChangedMessages = new ArrayList<>();
                    }

                    mChangedMessages.add(groupMessages);
                }

                if ((privateMessages != null) && (privateMessages.size() > 0)) {
                    // messages zurückstellen bis itemmap initialsiert wurde
                    if (mChangedMessages == null) {
                        mChangedMessages = new ArrayList<>();
                    }

                    mChangedMessages.add(privateMessages);
                }

                return;
            }
        }

        boolean notify = false;

        if ((groupMessages != null) && (!groupMessages.isEmpty())) {
            notify = true;
            changeOverviewItems(groupMessages);
        }

        if ((privateMessages != null) && (!privateMessages.isEmpty())) {
            notify = true;
            changeOverviewItems(privateMessages);
        }

        if (notify) {
            notifyListener(false);
        }
    }

    private void changeOverviewItems(final List<Message> messages) {
        LogUtil.i(TAG, "onMessagesChanged");

        if ((mItemMap == null) || (chatsAdapter == null)) {
            return;
        }

        for (final Message message : messages) {
            BaseChatOverviewItemVO chatOverviewItem = mItemMap.get(message.getId());

            if (chatOverviewItem == null) {
                continue;
            }

            final int position = chatsAdapter.getPosition(chatOverviewItem);

            if (position < 0) {
                continue;
            }

            chatOverviewItem = chatsAdapter.getItem(position);

            if (chatOverviewItem == null) {
                continue;
            }

            if (chatOverviewItem instanceof ChatOverviewItemVO) {
                final ChatOverviewItemVO activeChatOverviewItem = (ChatOverviewItemVO) chatOverviewItem;

                activeChatOverviewItem.datesend = (message.getDateSend() != null) ? message.getDateSend() : 0;
                activeChatOverviewItem.hasRead = message.hasReceiversRead();
                activeChatOverviewItem.hasDownloaded = message.hasReceiversDownloaded();
                activeChatOverviewItem.isSendConfirm = message.getDateSendConfirm() != null;
                activeChatOverviewItem.hasSendError = (message.getHasSendError() != null) ? message.getHasSendError()
                        : false;

                //message count > 0 --> pruefen ob ein anderes Device die Nachricht gelesen hat
                if (activeChatOverviewItem.messageCount > 0 && !message.isSentMessage()) {
                    //die Vorschaunachricht wurde auf einen anderen Device gelesen
                    if (message.getRead() != null && message.getRead()) {
                        activeChatOverviewItem.messageCount = 0;
                    }
                }
            }
        }
    }

    public DecryptedMessage decryptMessage(final Message message)
            throws LocalizedException {
        return mApplication.getMessageDecryptionController().decryptMessage(message, false);
    }

    public void loadChatOverviewItems() {
        startChatOverviewTask(false, null);
    }

    public boolean hasChatOverviewItems() {
        return mOverviewMessages.size() > 0;
    }

    private void startChatOverviewTask(final boolean isRefresh, List<String> chatRefreshGuids) {
        final ConcurrentTaskListener listener = new ConcurrentTaskListener() {
            @Override
            @SuppressWarnings("unchecked")
            public void onStateChanged(final ConcurrentTask task,
                                       final int state) {
                if (chatsAdapter == null) {
                    LogUtil.w(TAG, "refreshView:adapter is null");
                }

                if (state == ConcurrentTask.STATE_COMPLETE) {
                    LogUtil.i(TAG, "refreshView:complete");

                    boolean changeItems = true;

                    if (mOverviewMessages.size() < 1) {
                        ArrayList<BaseChatOverviewItemVO> list = (ArrayList<BaseChatOverviewItemVO>) task.getResults()[0];
                        if (list != null) {
                            mOverviewMessages.addAll(list);
                            mItemMap = (HashMap<Long, BaseChatOverviewItemVO>) task.getResults()[1];

                            startCheckContactsOnlineTask();
                        }
                    } else {
                        ArrayList<BaseChatOverviewItemVO> items = (ArrayList<BaseChatOverviewItemVO>) task.getResults()[0];
                        if (items != null) {
                            HashMap<Long, BaseChatOverviewItemVO> itemMap = (HashMap<Long, BaseChatOverviewItemVO>) task.getResults()[1];

                            for (BaseChatOverviewItemVO item : items) {
                                int oldIndex = mOverviewMessages.indexOf(item);

                                if (oldIndex > -1) {
                                    BaseChatOverviewItemVO oldItem = mOverviewMessages.get(oldIndex);
                                    if (oldItem != null) {
                                        mOverviewMessages.set(oldIndex, item);
                                    }
                                } else {
                                    mOverviewMessages.add(0, item);
                                }
                            }

                            if (itemMap != null) {
                                if (mItemMap != null) {
                                    mItemMap.putAll(itemMap);
                                } else {
                                    mItemMap = itemMap;
                                }
                            }

                            Collections.sort(mOverviewMessages, getChatOverviewComperator());
                        } else {
                            changeItems = false;
                        }
                    }

                    if (changeItems) {

                        /* Bug 33109 type filter */
                        final List<BaseChatOverviewItemVO> newOverviewMessages = new ArrayList<>();

                        try {
                            switch (mMode) {
                                case MODE_FORWARD_SINGLE:
                                    filterMessagesHelperForwardSingle(newOverviewMessages);
                                    break;
                                case MODE_FORWARD_GROUP:
                                    filterMessagesHelperForwardGroup(newOverviewMessages);
                                    break;
                                case MODE_OVERVIEW:
                                default:
                                    filterMessagesHelper(newOverviewMessages);
                                    break;
                            }
                        } catch (LocalizedException e) {
                            LogUtil.e(TAG, e.getMessage(), e);
                        }

                        if (mListRefreshedListener != null) {
                            mListRefreshedListener.onListRefreshed(newOverviewMessages);
                        }

                        if (chatsAdapter != null) {
                            //notify ausschalten, sonst aktualisiert sich der Adapter zu oft
                            chatsAdapter.setItems(newOverviewMessages, false);
                        }
                    }

                    // wenn noch geaendert Messages existieren
                    synchronized (ChatOverviewController.this) {
                        if ((mChangedMessages != null) && (mChangedMessages.size() > 0)) {
                            while (mChangedMessages.size() > 0) {
                                final List<Message> messages = mChangedMessages.remove(0);

                                changeOverviewItems(messages);

                                changeItems = true;
                            }
                        }
                    }

                    if (changeItems) {
                        notifyListenerLoaded();
                        notifyListener(false);
                    }
                }

                if ((state == ConcurrentTask.STATE_COMPLETE) || (state == ConcurrentTask.STATE_CANCELED)
                        || (state == ConcurrentTask.STATE_ERROR)) {
                    synchronized (this) {
                        mChatOverviewTask = null;
                        if (mStartChatOverviewTaskAgain) {
                            mStartChatOverviewTaskAgain = false;
                            startChatOverviewTask(true, null);
                        }
                    }
                }
            }
        };

        synchronized (this) {
            if (mChatOverviewTask == null) {
                ArrayList<String> chatsToRefresh;

                if (mChatsToRefresh != null) {
                    ArrayList<?> o = (ArrayList<?>) mChatsToRefresh.clone();
                    chatsToRefresh = (ArrayList<String>) o;
                    if (chatRefreshGuids != null && chatRefreshGuids.size() > 0) {
                        chatsToRefresh.addAll(chatRefreshGuids);
                    }
                    mChatsToRefresh = null;
                } else if (chatRefreshGuids != null) {
                    chatsToRefresh = new ArrayList<>(chatRefreshGuids);
                } else {
                    chatsToRefresh = null;
                }

                LogUtil.i(TAG, "refreshView:start with guids" + (chatsToRefresh != null ? StringUtil.getStringFromList(",", chatsToRefresh) : ""));

                boolean refresh = isRefresh && mOverviewMessages.size() > 0;
                mChatOverviewTask = chatOverviewTaskManager.executeRefreshChatOverviewTask(mApplication, listener, chatsToRefresh, refresh);
            } else {
                if (chatRefreshGuids != null && chatRefreshGuids.size() > 0) {
                    if (mChatsToRefresh != null) {
                        mChatsToRefresh.addAll(chatRefreshGuids);
                    } else {
                        mChatsToRefresh = new ArrayList<>();
                        mChatsToRefresh.addAll(chatRefreshGuids);
                    }
                }
                mStartChatOverviewTaskAgain = true;
            }
        }
    }

    public void chatChanged(@Nullable List<String> chatGuids, @Nullable String chatGuid, @Nullable Message message, int changes) {
        if (chatGuids != null && chatGuids.size() > 0 && ((changes & CHAT_CHANGED_NEW_SEND_MSG) == CHAT_CHANGED_NEW_SEND_MSG
                || (changes & CHAT_CHANGED_REFRESH_CHAT) == CHAT_CHANGED_REFRESH_CHAT)) {
            startChatOverviewTask(true, chatGuids);
        } else if ((changes & CHAT_CHANGED_NEW_CHAT) == CHAT_CHANGED_NEW_CHAT && !StringUtil.isNullOrEmpty(chatGuid)) {
            List<String> newChatGuids = new ArrayList<>();
            newChatGuids.add(chatGuid);
            startChatOverviewTask(true, newChatGuids);
        } else if ((changes & CHAT_CHANGED_IMAGE) == CHAT_CHANGED_IMAGE) {
            notifyListener(true);
        } else if (chatChangedInternally(chatGuid, message, changes)) {
            notifyListener(false);
        } else if ((chatGuid != null) && ((changes & CHAT_CHANGED_NEW_SEND_MSG) == CHAT_CHANGED_NEW_SEND_MSG
                || (changes & CHAT_CHANGED_REFRESH_CHAT) == CHAT_CHANGED_REFRESH_CHAT)) {
            startChatOverviewTask(true, Collections.singletonList(chatGuid));
        }
    }

    private boolean chatChangedInternally(@Nullable String chatGuid, @Nullable Message message, int changes) {
        if (StringUtil.isNullOrEmpty(chatGuid) && (changes & CHAT_CHANGED_TITLE) == CHAT_CHANGED_TITLE) {
            return refreshChatTitles();
        }

        BaseChatOverviewItemVO foundItem = null;
        String refreshChatGuid;

        if (!StringUtil.isNullOrEmpty(chatGuid)) {
            refreshChatGuid = chatGuid;
        } else if (message != null) {
            String guid = getGuidForMessage(message);

            if (!StringUtil.isNullOrEmpty(guid)) {
                refreshChatGuid = guid;
            } else {
                return false;
            }
        } else {
            return false;
        }

        if ((changes & CHAT_CHANGED_NEW_SEND_MSG) == CHAT_CHANGED_NEW_SEND_MSG
                || (changes & CHAT_CHANGED_ACCEPT_CHAT) == CHAT_CHANGED_ACCEPT_CHAT
                || (changes & CHAT_CHANGED_MSG_DELETED) == CHAT_CHANGED_MSG_DELETED
                || (changes & CHAT_CHANGED_REFRESH_CHAT) == CHAT_CHANGED_REFRESH_CHAT) {
            List<String> refreshChatGuids = new ArrayList<>();
            refreshChatGuids.add(refreshChatGuid);
            startChatOverviewTask(true, refreshChatGuids);
            return false;
        }

        synchronized (mOverviewMessages) {
            if (mOverviewMessages.size() < 1) {
                return false;
            }

            int hash = refreshChatGuid.hashCode();

            for (BaseChatOverviewItemVO item : mOverviewMessages) {
                if (item.hashCode() == hash) {
                    foundItem = item;
                    break;
                }
            }

            if (foundItem == null) {
                return false;
            }

            if ((changes & CHAT_CHANGED_HAS_READ_MSG) == CHAT_CHANGED_HAS_READ_MSG) {
                if (foundItem instanceof ChatOverviewItemVO) {
                    if (((ChatOverviewItemVO) foundItem).messageCount > 0) {
                        ((ChatOverviewItemVO) foundItem).messageCount = 0;
                        return true;
                    }
                } else if (foundItem instanceof ChannelChatOverviewItemVO) {
                    if (((ChannelChatOverviewItemVO) foundItem).messageCount > 0) {
                        ((ChannelChatOverviewItemVO) foundItem).messageCount = 0;
                        return true;
                    }
                }
            }

            if ((changes & CHAT_CHANGED_CLEAR_CHAT) == CHAT_CHANGED_CLEAR_CHAT) {
                if (foundItem instanceof ChatOverviewItemVO) {
                    ChatOverviewItemVO chatItem = ((ChatOverviewItemVO) foundItem);

                    chatItem.messageCount = 0;
                    chatItem.hasDownloaded = false;
                    chatItem.hasRead = false;
                    chatItem.isPriority = false;
                    chatItem.setMediaType(0);

                    if (chatItem.dateSendTimed == null) {
                        chatItem.isSentMessage = false;
                        chatItem.previewText = null;
                        chatItem.isSendConfirm = false;
                    }
                    return true;
                }
                if (foundItem instanceof ChannelChatOverviewItemVO) {
                    ((ChannelChatOverviewItemVO) foundItem).messageCount = 0;
                    if (foundItem instanceof ServiceChatOverviewItemVO) {
                        ((ServiceChatOverviewItemVO) foundItem).previewText = null;
                    }
                    return true;
                }
            }

            if ((changes & CHAT_CHANGED_DELETE_CHAT) == CHAT_CHANGED_DELETE_CHAT) {
                mOverviewMessages.remove(foundItem);
                if (chatsAdapter != null) {
                    chatsAdapter.removeItem(foundItem);
                }
                return true;
            }

            if ((changes & CHAT_CHANGED_TITLE) == CHAT_CHANGED_TITLE) {
                try {
                    return refreshChatTitle(foundItem);
                } catch (LocalizedException e) {
                    LogUtil.e(TAG, e.getMessage(), e);
                }
            }
        }
        return false;
    }

    private boolean refreshChatTitles() {
        try {
            if (mOverviewMessages.size() < 1) {
                return false;
            }

            synchronized (mOverviewMessages) {
                for (BaseChatOverviewItemVO item : mOverviewMessages) {
                    refreshChatTitle(item);
                }
            }

            return true;
        } catch (LocalizedException e) {
            return false;
        }
    }

    private boolean refreshChatTitle(BaseChatOverviewItemVO item)
            throws LocalizedException {
        boolean returnValue = true;
        if (item instanceof SingleChatOverviewItemVO
                || item instanceof SingleChatOverviewItemInvitationVO) {
            item.setTitle(getTitleForChat(item.getChatGuid()));
        } else if (item instanceof GroupChatOverviewItemInvitationVO) {
            if (StringUtil.isNullOrEmpty(item.getChatGuid())) {
                return false;
            }
            Chat chat = mApplication.getGroupChatController().getChatByGuid(item.getChatGuid());
            if (chat != null) {
                if (chat.getOwner() != null) {
                    Contact contact = mApplication.getContactController().getContactByGuid(chat.getOwner());
                    if (contact != null) {
                        String ownerName = contact.getName();

                        //       item.setTitle(truncateTitle(ownerName + ": " + chat.getTitle()));
                        if (ownerName.length() != 0) {
                            ownerName = ownerName + ": ";
                        }

                        item.setTitle(ownerName + chat.getTitle());
                    } else {
                        item.setTitle(chat.getTitle());
                    }
                } else {
                    item.setTitle(chat.getTitle());
                }
            }
        } else if (item instanceof GroupChatOverviewItemVO) {
            if (StringUtil.isNullOrEmpty(item.getChatGuid())) {
                return false;
            }
            Chat chat = mApplication.getGroupChatController().getChatByGuid(item.getChatGuid());
            if (chat != null) {
                item.setTitle(chat.getTitle());
            }
        } else {
            returnValue = false;
        }

        return returnValue;
    }

    public void startCheckContactsOnlineTask() {
        if (mCheckContactsOnlineTask != null) {
            return;
        }

        if (mOverviewMessages.size() < 1) {
            return;
        }

        final List<String> contactGuids = new ArrayList<>(mOverviewMessages.size());

        for (final BaseChatOverviewItemVO item : mOverviewMessages) {
            if (item instanceof SingleChatOverviewItemVO) {
                contactGuids.add(item.getChatGuid());
            }
        }

        if (contactGuids.size() < 1) {
            return;
        }

        LogUtil.d(TAG, "startCheckContactsOnlineTask: getOnlineStateBatch for SingleChatOverviewItemVOs: " + contactGuids.toString());

        mCheckContactsOnlineTask = new AsyncHttpTask<>(new AsyncHttpTask.AsyncHttpCallback<ArrayMap<String, Boolean>>() {
            @Override
            public void asyncLoaderServerRequest(IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .getOnlineStateBatch(StringUtil.getStringFromList(",", contactGuids), listener);
            }

            @Override
            public ArrayMap<String, Boolean> asyncLoaderServerResponse(BackendResponse response) {
                if (response.jsonArray != null) {
                    ArrayMap<String, Boolean> wasOnline = new ArrayMap<>(response.jsonArray.size());
                    long now = new Date().getTime();

                    long sixHoursAgo = now - (6 * 60 * 60 * 1000);

                    for (JsonElement je : response.jsonArray) {
                        if (!je.isJsonObject()) {
                            continue;
                        }

                        JsonObject entryJO = je.getAsJsonObject();

                        LogUtil.d(TAG, "asyncLoaderServerResponse: Got: " + entryJO.toString());

                        String guid = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_GUID, entryJO);
                        if (StringUtil.isNullOrEmpty(guid)) {
                            continue;
                        }

                        String lastOnlineTimeStamp = JsonUtil.stringFromJO(JsonConstants.LAST_ONLINE, entryJO);

                        boolean isAbsent = false;
                        if (entryJO.has(JsonConstants.OOO_STATUS)) {
                            JsonElement oooStatusJE = entryJO.get(JsonConstants.OOO_STATUS);
                            JsonObject oooStatusJO = oooStatusJE.isJsonObject() ? oooStatusJE.getAsJsonObject() : null;

                            if (oooStatusJO != null) {
                                String statusState = JsonUtil.stringFromJO(JsonConstants.OOO_STATUS_STATE, oooStatusJO);

                                if (StringUtil.isEqual(JsonConstants.OOO_STATUS_STATE_OOO, statusState)) {
                                    isAbsent = true;
                                }
                            }
                        }

                        if (isAbsent) {
                            wasOnline.put(guid, false);
                        } else if (!StringUtil.isNullOrEmpty(lastOnlineTimeStamp)) {
                            Date lastOnline = DateUtil.utcWithoutMillisStringToDate(lastOnlineTimeStamp);
                            if (lastOnline != null && lastOnline.getTime() >= sixHoursAgo) {
                                wasOnline.put(guid, true);
                            }
                        }
                    }

                    return wasOnline;
                }
                return null;
            }

            @Override
            public void asyncLoaderFinished(ArrayMap<String, Boolean> result) {
                if (chatsAdapter != null) {
                    notifyListener(false);
                }

                mCheckContactsOnlineTask = null;
            }

            @Override
            public void asyncLoaderFailed(String errorMessage) {
                mCheckContactsOnlineTask = null;
            }
        });
        mCheckContactsOnlineTask.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }

    private void filterMessagesHelper(final List<BaseChatOverviewItemVO> newOverviewMessages) {
        newOverviewMessages.addAll(mOverviewMessages);
    }

    /**
     * Filters the Overview-Items. Update Ctrs on refresh., not on filtering
     */
    private void filterMessagesHelperForwardSingle(final List<BaseChatOverviewItemVO> newOverviewMessages)
            throws LocalizedException {
        final boolean managementCompanyIsUserRestricted = mApplication.getAccountController().getManagementCompanyIsUserRestricted();

        for (final BaseChatOverviewItemVO item : mOverviewMessages) {
            if (item instanceof SingleChatOverviewItemVO) {
                final SingleChatOverviewItemVO itemCast = (SingleChatOverviewItemVO) item;
                final Contact contact = mApplication.getContactController().getContactByGuid(itemCast.getChatGuid());

                final boolean isNoMemberOfRestrictedCompany = managementCompanyIsUserRestricted && CLASS_PRIVATE_ENTRY.equals(contact.getClassEntryName());

                final boolean isBlocked;
                if (contact != null && contact.getIsBlocked() != null) {
                    isBlocked = contact.getIsBlocked();
                } else {
                    isBlocked = false;
                }
                if (!itemCast.isSystemChat
                        && !isBlocked
                        && itemCast.getState() != Contact.STATE_UNSIMSABLE
                        && !isNoMemberOfRestrictedCompany
                ) {
                    newOverviewMessages.add(item);
                }
            }
        }
    }

    /**
     * Filters the Overview-Items. Update Ctrs on refresh., not on filtering
     */
    private void filterMessagesHelperForwardGroup(final List<BaseChatOverviewItemVO> newOverviewMessages) {
        for (final BaseChatOverviewItemVO item : mOverviewMessages) {
            if (item instanceof GroupChatOverviewItemVO) {
                final GroupChatOverviewItemVO groupChatOverviewItemVO = (GroupChatOverviewItemVO) item;

                if (!groupChatOverviewItemVO.isReadOnly() && !groupChatOverviewItemVO.isRemoved()) {
                    newOverviewMessages.add(item);
                }
            }
        }
    }

    public void exportChat(final Context context, final Chat chat, final OnChatExportedListener listener) {
        final int messageType;

        if (chat.getType() == Chat.TYPE_SINGLE_CHAT) {
            messageType = Message.TYPE_PRIVATE;
        } else if (chat.getType() == Chat.TYPE_GROUP_CHAT) {
            messageType = Message.TYPE_GROUP;
        } else {
            return;
        }

        try {
            final String name = chat.getTitle();
            final File directory = new File(mApplication.getFilesDir(), "share");

            if (!directory.isDirectory()) {
                if (!directory.mkdirs()) {
                    throw new LocalizedException(LocalizedException.CREATE_FILE_FAILED, "create directory");
                }
            } else {
                deleteShareFiles(directory);
            }

            if (!directory.canWrite()) {
                listener.onChatExportFail(context.getResources().getString(R.string.chat_overview_export_chat_directory_does_not_exist));
                return;

            }

            // KS: name may be null!
            final String fileName = ("ginlo_" + (name != null ? name.replace(" ", "_") : "") + ".txt");
            final File file = new File(directory, fileName);
            try {
                boolean wasTheFileCreated = file.createNewFile();

                if (!wasTheFileCreated) {
                    listener.onChatExportFail(context.getResources().getString(R.string.action_failed));
                }
            } catch (IOException e) {
                LogUtil.e(TAG, e.getMessage(), e);
                listener.onChatExportFail(context.getResources().getString(R.string.action_failed));
            }

            final QueryDatabaseListener queryDatabaseListener = new QueryDatabaseListener() {
                @Override
                public void onListResult(@NotNull List<Message> messages) {
                    try (OutputStream fout = new BufferedOutputStream(new FileOutputStream(file.getPath()))) {
                        for (int size = messages.size(); size > 0; ) {
                            --size;
                            final Message message = messages.get(size);
                            if (message != null) {
                                //Umgehen von Systemnachrichten
                                if (message.isSystemInfo()) {
                                    continue;
                                }

                                DecryptedMessage decryptedMessage;
                                try {
                                    decryptedMessage = decryptMessage(message);

                                    if (message.getType() == Message.TYPE_PRIVATE || message.getType() == Message.TYPE_GROUP) {

                                        String nickname;
                                        final long date = message.getDateSend();
                                        AccountController accountController = mApplication.getAccountController();

                                        if (StringUtil.isEqual(message.getFrom(), accountController.getAccount().getAccountGuid())) {
                                            nickname = accountController.getAccount().getName();
                                        } else {
                                            nickname = decryptedMessage.getNickName();
                                        }
                                        final String text;

                                        if (StringUtil.isEqual(decryptedMessage.getContentType(), MimeType.TEXT_PLAIN)) {
                                            text = decryptedMessage.getText();
                                        } else if (StringUtil.isEqual(decryptedMessage.getContentType(), MimeType.TEXT_RSS)) {
                                            text = decryptedMessage.getText();
                                            nickname = null;
                                        } else if (StringUtil.isEqual(decryptedMessage.getContentType(), MimeType.AUDIO_MPEG)) {
                                            text = context.getResources().getString(R.string.export_chat_type_audio);
                                        } else if (StringUtil.isEqual(decryptedMessage.getContentType(), MimeType.IMAGE_JPEG)) {
                                            text = context.getResources().getString(R.string.export_chat_type_image);
                                        } else if (StringUtil.isEqual(decryptedMessage.getContentType(), MimeType.VIDEO_MPEG)) {
                                            text = context.getResources().getString(R.string.export_chat_type_video);
                                            // KS: AVC
                                        } else if (StringUtil.isEqual(decryptedMessage.getContentType(), MimeType.TEXT_V_CALL)) {
                                            text = decryptedMessage.getContentType();
                                        } else if (StringUtil.isEqual(decryptedMessage.getContentType(), MimeType.TEXT_V_CARD)) {
                                            text = context.getResources().getString(R.string.export_chat_type_contact);
                                        } else if (StringUtil.isEqual(decryptedMessage.getContentType(), MimeType.MODEL_LOCATION)) {
                                            text = context.getResources().getString(R.string.export_chat_type_location);
                                        } else if (StringUtil.isEqual(decryptedMessage.getContentType(), MimeType.APP_OCTET_STREAM)) {
                                            text = context.getResources().getString(R.string.export_chat_type_file);
                                        } else {
                                            text = "";
                                        }

                                        final String entry = DateUtil.dateToUtcString(new Date(date)) + ((nickname != null) ? (": " + nickname) : "") + ": " + text + "\n";

                                        final byte[] byteArray = entry.getBytes(StandardCharsets.UTF_8);
                                        fout.write(byteArray, 0, byteArray.length);
                                    }
                                } catch (LocalizedException e) {
                                    LogUtil.w(TAG, e.getMessage(), e);
                                }
                            }
                        }
                        fout.close();
                        listener.onChatExportSuccess(file);
                    } catch (IOException ex) {
                        listener.onChatExportFail(context.getResources().getString(R.string.chat_overview_export_chat_directory_does_not_exist));
                    }
                }

                @Override
                public void onUniqueResult(@NotNull Message message) {

                }

                @Override
                public void onCount(long count) {

                }
            };

            MessageController messageController = mApplication.getMessageController();
            messageController.loadAllMessages(chat.getChatGuid(), messageType, true, queryDatabaseListener);

        } catch (LocalizedException e) {
            listener.onChatExportFail(context.getResources().getString(R.string.action_failed));
        }
    }

    private String getWonContactNickName() {
        String nickName = "";
        ContactController contactController = mApplication.getContactController();
        try {
            Contact ownContact = contactController.getOwnContact();
            if (ownContact != null) {
                nickName = ownContact.getNickname();
            }
        } catch (LocalizedException ex) {
            LogUtil.w(TAG, "Failed to get own contact's nickname");
        }
        return nickName;
    }

    private void deleteShareFiles(File shareDir) {
        if ((shareDir != null) && shareDir.exists()) {
            for (File file : shareDir.listFiles()) {
                if (file.isFile()) {
                    if (!file.delete()) {
                        LogUtil.w(TAG, "deleteShareFiles() -> delete file failed");
                    }
                }
            }
        }
    }

    public void filterMessages() {
        filterMessages(true);
    }

    public void filterMessages(final boolean notifyListener) {
        /* Bug 33109 type filter */
        final List<BaseChatOverviewItemVO> newOverviewMessages = new ArrayList<>();

        try {
            switch (mMode) {
                case MODE_FORWARD_SINGLE:
                    filterMessagesHelperForwardSingle(newOverviewMessages);
                    break;
                case MODE_FORWARD_GROUP:
                    filterMessagesHelperForwardGroup(newOverviewMessages);
                    break;
                case MODE_OVERVIEW:
                default: {
                    filterMessagesHelper(newOverviewMessages);
                    break;
                }
            }
        } catch (LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }

        if (mListRefreshedListener != null) {
            mListRefreshedListener.onListRefreshed(newOverviewMessages);
        }

        if (chatsAdapter != null) {
            //notify ausschalten, sonst aktualisiert sich der Adapter zu oft
            chatsAdapter.setItems(newOverviewMessages, false);
        }

        if (notifyListener) {
            notifyListenerLoaded();
            notifyListener(false);
        }
    }

    @Override
    public void onImageDataChanged(final String guid) {
        // Das Bild hat sich geändert
        synchronized (this) {
            if (chatsAdapter != null) {
                chatsAdapter.notifyDataSetChanged();
            }
        }
    }

    public void setListRefreshedListener(ChatsOverviewActivity.ListRefreshedListener listener) {
        mListRefreshedListener = listener;
    }

    public void setMode(int mode) {
        mMode = mode;
    }

    public Comparator<BaseChatOverviewItemVO> getChatOverviewComperator() {
        if (mComparator == null) {
            mComparator = new Comparator<BaseChatOverviewItemVO>() {
                @Override
                public int compare(BaseChatOverviewItemVO chatOverviewItem1,
                                   BaseChatOverviewItemVO chatOverviewItem2) {
                    return Long.compare(chatOverviewItem2.datesend, chatOverviewItem1.datesend);
                }
            };
        }
        return mComparator;
    }

    public interface OnChatExportedListener {
        void onChatExportSuccess(File file);

        void onChatExportFail(final String message);
    }
}
