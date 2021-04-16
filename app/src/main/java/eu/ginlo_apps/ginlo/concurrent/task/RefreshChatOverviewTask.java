// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.task;

import eu.ginlo_apps.ginlo.concurrent.manager.ChatOverviewBaseTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.chat.overview.BaseChatOverviewItemVO;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class RefreshChatOverviewTask
        extends ChatOverviewBaseTask {
    private static long mLastMsgId;
    private final boolean mIsRefresh;
    private final List<String> mChatsToRefresh;
    private ArrayList<BaseChatOverviewItemVO> overviewItems;
    private HashMap<Long, BaseChatOverviewItemVO> itemMap;

    public RefreshChatOverviewTask(SimsMeApplication application,
                                   List<String> chatsToRefresh,
                                   boolean refresh) {
        super(application);
        mIsRefresh = refresh;
        mChatsToRefresh = chatsToRefresh;
    }

    public static long getLastLoadedMsgId() {
        return mLastMsgId;
    }

    public static void setLastLoadedMsgId(long newMsgId) {
        mLastMsgId = newMsgId;
    }

    @Override
    public void run() {
        long startTime = System.currentTimeMillis();
        try {
            super.run();

            Iterator<Chat> chats = null;
            if (!mIsRefresh) {
                chats = mChatController.loadAll().iterator();
            } else {
                //Chats anlegen, falls nötig
                Map<String, Chat> chatMap = getChatsForRefresh(getGroupInvitationAndUnreadMessages(mLastMsgId));

                if (chatMap != null && chatMap.size() > 0) {
                    if (mChatsToRefresh != null && mChatsToRefresh.size() > 0) {
                        for (String guid : mChatsToRefresh) {
                            if (!chatMap.containsKey(guid)) {
                                Chat chat = mChatController.getChatByGuid(guid);
                                if (chat != null) {
                                    chatMap.put(chat.getChatGuid(), chat);
                                }
                            }
                        }
                    }
                    chats = chatMap.values().iterator();
                } else if (mChatsToRefresh != null && mChatsToRefresh.size() > 0) {
                    Map<String, Chat> tmpChatMap = new HashMap<>();

                    for (String guid : mChatsToRefresh) {
                        if (!tmpChatMap.containsKey(guid)) {
                            Chat chat = mChatController.getChatByGuid(guid);
                            if (chat != null) {
                                tmpChatMap.put(chat.getChatGuid(), chat);
                            }
                        }
                    }
                    chats = tmpChatMap.values().iterator();
                }
            }

            if (chats != null) {
                processChats(chats);
            }

            complete();
        } finally {
            long endTime = System.currentTimeMillis();
            LogUtil.i(this.getClass().getSimpleName(), "Time Used in run ms:" + (endTime - startTime));
        }
    }

    private void processChats(Iterator<Chat> chats) {
        long startTime = System.currentTimeMillis();
        try {
            overviewItems = new ArrayList<>();
            itemMap = new HashMap<>();

            while (chats.hasNext()) {
                try {
                    Chat chat = chats.next();
                    if (isCanceled()) {
                        return;
                    }

                    if (chat == null) {
                        continue;
                    }

                    Message message = getLastChatMessage(chat);

                    if (message != null && message.getId() > mLastMsgId) {
                        mLastMsgId = message.getId();
                    }

                    DecryptedMessage decryptedMessage = (message != null) ? chatOverviewController.decryptMessage(message)
                            : null;
                    BaseChatOverviewItemVO chatOverviewItem = null;

                    if (chat.getType() == null) {
                        if (decryptedMessage == null) {
                            continue;
                        }

                        // wenn die encryptete Einladung schon decryptet wurde (per in-app-push), dann nicht nochmal
                        final String roomType = chat.getRoomType();
                        if (Chat.ROOM_TYPE_RESTRICTED.equals(roomType) || Chat.ROOM_TYPE_MANAGED.equals(roomType)) {
                            continue;
                        }

                        // wenn nicht, dann jetzt
                        if (decryptedMessage.getContentType() != null && decryptedMessage.getContentType().endsWith("+encrypted")) {
                            mGroupChatController.handleAdditionalEncryptedInvitationData(chat, message, true);
                            continue;
                        }
                        fillChat(chat, decryptedMessage);
                    }

                    if (chat.getType() == null) {
                        continue;
                    }

                    if (chat.getType() == Chat.TYPE_SINGLE_CHAT) {
                        chatOverviewItem = getSingleChatOverviewItem(chat, decryptedMessage);
                    } else if (chat.getType() == Chat.TYPE_SINGLE_CHAT_INVITATION) {
                        chatOverviewItem = getSingleChatOverviewInvitationItem(chat, decryptedMessage);
                    } else if (chat.getType() == Chat.TYPE_GROUP_CHAT) {
                        chatOverviewItem = getGroupChatOverviewItem(chat, decryptedMessage);
                    } else if (chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION) {
                        if (Chat.ROOM_TYPE_RESTRICTED.equals(chat.getRoomType()) || Chat.ROOM_TYPE_MANAGED.equals(chat.getRoomType())) {
                            // Falls das Akzeptieren fehlgeschlagen hatte
                            // dann nochmal probieren
                            mGroupChatController.handleAdditionalEncryptedInvitationData(chat, message, true);
                            chatOverviewItem = getGroupChatOverviewItem(chat, decryptedMessage);
                        } else {
                            chatOverviewItem = getGroupChatOverviewInvitationItem(chat);
                        }
                    } else if (chat.getType() == Chat.TYPE_CHANNEL) {
                        final Channel channel = mChannelController.getChannelFromDB(chat.getChatGuid());
                        if (channel != null && StringUtil.isEqual(Channel.TYPE_SERVICE, channel.getType())) {
                            chatOverviewItem = getServiceChatOverviewItem(chat, decryptedMessage);
                        } else {
                            chatOverviewItem = getChannelChatOverviewItem(chat, decryptedMessage);
                        }
                    }

                    if (chatOverviewItem != null) {
                        chatOverviewItem.chat = chat;
                        overviewItems.add(chatOverviewItem);
                        if (decryptedMessage != null) {
                            if (decryptedMessage.getMessage().getId() != null) {
                                itemMap.put(decryptedMessage.getMessage().getId(), chatOverviewItem);
                            }
                        } else {
                            if ((chat.getLastChatModifiedDate() != null) && (chat.getLastChatModifiedDate() != 0)) {
                                chatOverviewItem.datesend = chat.getLastChatModifiedDate();
                            }
                        }
                    }
                } catch (LocalizedException e) {
                    if (StringUtil.isEqual(e.getIdentifier(), LocalizedException.NO_INIT_CALLED) || StringUtil.isEqual(e.getIdentifier(), LocalizedException.KEY_NOT_AVAILABLE)) {
                        throw e;
                    }
                }
            }

            if (!mIsRefresh) {
                Collections.sort(overviewItems, chatOverviewController.getChatOverviewComperator());
            }
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.toString());
        } finally {
            long endTime = System.currentTimeMillis();
            LogUtil.i(this.getClass().getSimpleName(), "Time Used in processChats ms:" + (endTime - startTime));
        }
    }

    private Message getLastChatMessage(Chat chat) {
        if (chat.getType() == null) {
            if (chat.getLastMsgId() == null) {
                return null;
            }

            return messageController.getMessageById(chat.getLastMsgId());
        }

        if (chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION) {
            return null;
        }

        Long lastMsgId = chat.getLastMsgId();
        Message message = null;

        if (lastMsgId != null) {
            message = messageController.getMessageById(lastMsgId);
        }

        if (message == null) {
            int type = getMessageTypeFromChat(chat);
            message = messageController.loadLastChatMessage(chat.getChatGuid(), type);

            if (message != null) {
                //aktualisieren, kann ja sein das jemand anderes
                mChatController.refresh(chat);

                if (chat.getLastMsgId() == null) {
                    messageController.setLatestMessageToChat(message, chat, false);
                    mChatController.insertOrUpdateChat(chat);
                }
            }
        }

        return message;
    }

    private int getMessageTypeFromChat(Chat chat) {
        int type;
        switch (chat.getType()) {
            case Chat.TYPE_CHANNEL: {
                type = Message.TYPE_CHANNEL;
                break;
            }
            case Chat.TYPE_GROUP_CHAT: {
                type = Message.TYPE_GROUP;
                break;
            }
            case Chat.TYPE_SINGLE_CHAT:
            case Chat.TYPE_SINGLE_CHAT_INVITATION: {
                type = Message.TYPE_PRIVATE;
                break;
            }
            default: {
                type = -1;
            }
        }

        return type;
    }

    @Override
    public Object[] getResults() {
        return new Object[]
                {
                        overviewItems,
                        itemMap
                };
    }
}
