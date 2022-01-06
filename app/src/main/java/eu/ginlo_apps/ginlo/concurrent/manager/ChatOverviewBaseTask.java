// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.concurrent.manager;

import androidx.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.MessageDecryptionController;
import eu.ginlo_apps.ginlo.controller.NotificationController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.GroupChatController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.controller.message.MessageDataResolver;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.ChatDao;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.greendao.MessageDao;
import eu.ginlo_apps.ginlo.greendao.MessageDao.Properties;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.backend.ChannelModel;
import eu.ginlo_apps.ginlo.model.chat.overview.BaseChatOverviewItemVO;
import eu.ginlo_apps.ginlo.model.chat.overview.ChannelChatOverviewItemVO;
import eu.ginlo_apps.ginlo.model.chat.overview.GroupChatOverviewItemInvitationVO;
import eu.ginlo_apps.ginlo.model.chat.overview.GroupChatOverviewItemVO;
import eu.ginlo_apps.ginlo.model.chat.overview.ServiceChatOverviewItemVO;
import eu.ginlo_apps.ginlo.model.chat.overview.SingleChatOverviewItemInvitationVO;
import eu.ginlo_apps.ginlo.model.chat.overview.SingleChatOverviewItemVO;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.greenrobot.greendao.query.LazyList;
import org.greenrobot.greendao.query.QueryBuilder;

public class ChatOverviewBaseTask extends ConcurrentTask {
    protected final ChatOverviewController chatOverviewController;
    protected final MessageController messageController;
    protected final ChannelController mChannelController;
    protected final ChatController mChatController;
    protected final GroupChatController mGroupChatController;
    protected final MessageDecryptionController msgDecryptionController;
    private final ChatImageController chatImageController;
    private final ContactController contactController;
    private final NotificationController mNotificationController;
    private final SimsMeApplication mApplication;
    private static final Object lockGetUnreadMessages = new Object();
    private static final Object lockGetChat = new Object();
    private static final String TAG = ChatOverviewBaseTask.class.getSimpleName();

    protected ChatOverviewBaseTask(SimsMeApplication application) {
        super();
        this.mApplication = application;
        this.chatOverviewController = application.getChatOverviewController();
        this.messageController = application.getMessageController();
        this.chatImageController = application.getChatImageController();
        this.contactController = application.getContactController();
        this.mChatController = application.getSingleChatController();
        this.msgDecryptionController = application.getMessageDecryptionController();
        this.mChannelController = application.getChannelController();
        this.mGroupChatController = application.getGroupChatController();
        this.mNotificationController = application.getNotificationController();
    }

    @Override
    public Object[] getResults() {
        return null;
    }

    private void setSendDate(BaseChatOverviewItemVO item,
                             Message message,
                             Chat chat) {
        long dateFromChat = 0;

        if (chat != null) {
            Long lmd = chat.getLastChatModifiedDate();

            dateFromChat = lmd != null ? lmd : 0;
        }
        if ((message != null) && (message.getDateSend() != null)) {
            item.datesend = Math.max(message.getDateSend(), dateFromChat);
        } else {
            item.datesend = dateFromChat;
        }
    }

    protected SingleChatOverviewItemVO getSingleChatOverviewItem(Chat chat, DecryptedMessage decryptedMsg)
            throws LocalizedException {

        String guid = chat.getChatGuid();
        SingleChatOverviewItemVO item = new SingleChatOverviewItemVO();

        try {
            item.setChatGuid(guid);
            item.isSystemChat = guid.equals(AppConstants.GUID_SYSTEM_CHAT);
            item.messageCount = messageController.getNumNotReadMessagesByGuid(guid, Message.TYPE_PRIVATE);
            item.setMediaType(getMediaTypeForMessage(decryptedMsg));
            item.setState(getStateForSingleChat(guid));
            item.previewText = chatOverviewController.getPreviewTextForMessage(decryptedMsg, false);

            if(decryptedMsg != null) {
                final Message msg = decryptedMsg.getMessage();

                setSendDate(item, msg, chat);
                item.hasRead = msg.hasReceiversRead();
                item.hasDownloaded = msg.hasReceiversDownloaded();
                item.hasSendError = msg.getHasSendError();
                item.isSentMessage = msg.isSentMessage();
                item.isSendConfirm = msg.getDateSendConfirm() != null;
                item.dateSendTimed = msg.getDateSendTimed();
                item.setTitle(chatOverviewController.getTitleForMessage(decryptedMsg));

                if (!item.isSentMessage) {
                    String contactGuid = MessageDataResolver.getGuidForMessage(msg);

                    if (!StringUtil.isNullOrEmpty(contactGuid)) {
                        Contact contact = contactController.getContactByGuid(contactGuid);

                        if (contact != null) {
                            //Pruefen ob der Profile AES Key vorhanden ist
                            if (StringUtil.isNullOrEmpty(contact.getProfileInfoAesKey())) {
                                if (!StringUtil.isNullOrEmpty(decryptedMsg.getProfilKey())) {
                                    try {
                                        contactController.setProfilInfoAesKey(contact.getAccountGuid(), decryptedMsg.getProfilKey(), decryptedMsg);
                                    } catch (LocalizedException e) {
                                        //Nicht dadurch den ganzen Task abbrechen
                                        LogUtil.w(TAG, "Error setProfileInfoAesKey", e);
                                    }
                                }
                            }
                        }
                    }
                }
                item.isSystemInfo = msg.getIsSystemInfo();
                checkIfMessageIsStillSending(msg, item);

                if (msg.getIsPriority()) {
                    item.isPriority = true;
                }

            } else {
                // All item booleans already set to false by instantiation of SingleChatOverviewItemVO.class
                setSendDate(item, null, chat);
                item.dateSendTimed = null;
                item.setTitle(chatOverviewController.getTitleForChat(chat.getChatGuid()));
            }

        } catch (LocalizedException e) {
            LogUtil.w(TAG, "getSingleChatOverviewItem: Got ", e);
            if (e.getIdentifier().equals(LocalizedException.CHECK_SIGNATURE_FAILED)) {
                item.previewText = chatOverviewController.getSignatureFailedText();
            } else {
                throw e;
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "getSingleChatOverviewItem: Got " + e.getMessage());
        }

        return item;
    }

    protected GroupChatOverviewItemVO getGroupChatOverviewItem(Chat chat, DecryptedMessage decryptedMsg)
            throws LocalizedException {

        String guid = chat.getChatGuid();
        GroupChatOverviewItemVO item = new GroupChatOverviewItemVO();

        try {
            item.setChatGuid(guid);
            item.messageCount = messageController.getNumNotReadMessagesByGuid(guid, Message.TYPE_PRIVATE);
            item.setMediaType(getMediaTypeForMessage(decryptedMsg));
            item.previewText = chatOverviewController.getPreviewTextForMessage(decryptedMsg, true);

            if(decryptedMsg != null) {
                final Message msg = decryptedMsg.getMessage();
                if(msg.getType() != Message.TYPE_GROUP_INVITATION) {

                    setSendDate(item, msg, chat);
                    item.hasRead = msg.hasReceiversRead();
                    item.hasDownloaded = msg.hasReceiversDownloaded();
                    item.hasSendError = msg.getHasSendError();
                    item.messageCount = messageController.getNumNotReadMessagesByGuid(guid, Message.TYPE_GROUP);
                    item.setHasLocalUserRead(msg.hasReceiversRead(true));
                    item.hasDownloaded = msg.hasReceiversDownloaded();
                    item.isSentMessage = msg.isSentMessage();
                    item.isSendConfirm = msg.getDateSendConfirm() != null;
                    item.dateSendTimed = msg.getDateSendTimed();

                    item.isSystemInfo = msg.getIsSystemInfo();
                    checkIfMessageIsStillSending(msg, item);

                    if (msg.getIsPriority()) {
                        item.isPriority = true;
                    }
                }

            } else {
                // All item booleans already set to false by instantiation of SingleChatOverviewItemVO.class
                setSendDate(item, null, chat);
                item.dateSendTimed = null;
            }

        } catch (LocalizedException e) {
            LogUtil.w(TAG, "getGroupChatOverviewItem: Got ", e);
            if (e.getIdentifier().equals(LocalizedException.CHECK_SIGNATURE_FAILED)) {
                item.previewText = chatOverviewController.getSignatureFailedText();
            } else {
                throw e;
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "getGroupChatOverviewItem: Got " + e.getMessage());
        }


        if (Chat.ROOM_TYPE_MANAGED.equals(chat.getRoomType()) || Chat.ROOM_TYPE_RESTRICTED.equals(chat.getRoomType())) {
            item.setState(Contact.STATE_HIGH_TRUST);
        } else {
            item.setState(getStateForGroupChat(chat.getMembers()));
        }

        item.setTitle(chat.getTitle());
        item.setRemoved(chat.getIsRemoved());
        item.setReadOnly(chat.getIsReadOnly());
        item.setRoomType(chat.getRoomType());

        return item;
    }

    protected SingleChatOverviewItemInvitationVO getSingleChatOverviewInvitationItem(Chat chat, DecryptedMessage decryptedMsg)
            throws LocalizedException {

        SingleChatOverviewItemInvitationVO item = new SingleChatOverviewItemInvitationVO();
        if (decryptedMsg != null) {
            item.setChatGuid(MessageDataResolver.getGuidForMessage(decryptedMsg.getMessage()));
            setSendDate(item, decryptedMsg.getMessage(), chat);
            item.setTitle(chatOverviewController.getTitleForMessage(decryptedMsg));

        } else {
            // decrypted message may be null, if chat has been emptied.
            item.setChatGuid(chat.getChatGuid());
            item.setTitle(chat.getTitle());

            Long lmd = chat.getLastChatModifiedDate();
            if (lmd != null && lmd != 0) {
                item.datesend = lmd;
            } else {
                //FIXME - Hotfix für Bug 36703 - hier sollte die richtige Nachricht rausgesucht werden, damit auch das Datum korrekt gesetzt wird
                Long now = new Date().getTime();
                item.datesend = now;
                chat.setLastChatModifiedDate(now);
                mChatController.getChatDao().update(chat);
            }
        }

        item.setState(Contact.STATE_LOW_TRUST);

        return item;
    }

    protected GroupChatOverviewItemInvitationVO getGroupChatOverviewInvitationItem(Chat chat)
            throws LocalizedException {
        String guid = chat.getChatGuid();
        GroupChatOverviewItemInvitationVO item = new GroupChatOverviewItemInvitationVO();

        item.setChatGuid(guid);
        item.setState(Contact.STATE_LOW_TRUST);

        Long lmd = chat.getLastChatModifiedDate();
        item.datesend = lmd != null ? lmd : 0;

        if (item.datesend == 0) {
            //Fallback fuer alte Versionen, die eine Einladung in die neue Version(1.7) mit genommen haben
            item.datesend = chat.getDate();
        }

        if (chat.getOwner() != null) {
            Contact contact = contactController.getContactByGuid(chat.getOwner());
            if (contact != null) {
                String ownerName = contact.getName();

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

        return item;
    }

    protected ServiceChatOverviewItemVO getServiceChatOverviewItem(Chat chat, DecryptedMessage decryptedMsg)
            throws LocalizedException {
        String guid = chat.getChatGuid();
        ServiceChatOverviewItemVO item = new ServiceChatOverviewItemVO();

        item.setChatGuid(guid);

        setSendDate(item, (decryptedMsg != null) ? decryptedMsg.getMessage() : null, chat);

        item.dateread = ((decryptedMsg != null) && (decryptedMsg.getMessage().getDateRead() != null))
                ? decryptedMsg.getMessage().getDateRead() : 0;
        item.datedownloaded = ((decryptedMsg != null) && (decryptedMsg.getMessage().getDateDownloaded() != null))
                ? decryptedMsg.getMessage().getDateDownloaded() : 0;
        item.messageCount = messageController.getNumNotReadMessagesByGuid(guid, Message.TYPE_CHANNEL);

        ChannelModel channelModel = mChannelController.getChannelModel(chat.getChatGuid());

        if (channelModel != null) {
            item.channelLayoutModel = channelModel.layout;
            item.setTitle(channelModel.shortDesc);

            item.previewText = chatOverviewController.getPreviewTextForMessage(decryptedMsg, false, false);
            if (StringUtil.isNullOrEmpty(item.previewText)) {
                item.previewText = channelModel.welcomeText;
            }
            item.shortLinkText = channelModel.shortLinkText;
        }

        return item;
    }

    protected ChannelChatOverviewItemVO getChannelChatOverviewItem(Chat chat, DecryptedMessage decryptedMsg)
            throws LocalizedException {
        String guid = chat.getChatGuid();
        ChannelChatOverviewItemVO item = new ChannelChatOverviewItemVO();

        item.setChatGuid(guid);

        setSendDate(item, (decryptedMsg != null) ? decryptedMsg.getMessage() : null, chat);

        item.dateread = ((decryptedMsg != null) && (decryptedMsg.getMessage().getDateRead() != null))
                ? decryptedMsg.getMessage().getDateRead() : 0;
        item.datedownloaded = ((decryptedMsg != null) && (decryptedMsg.getMessage().getDateDownloaded() != null))
                ? decryptedMsg.getMessage().getDateDownloaded() : 0;
        item.messageCount = messageController.getNumNotReadMessagesByGuid(guid, Message.TYPE_CHANNEL);
        item.previewText = chatOverviewController.getPreviewTextForMessage(decryptedMsg, false, false);

        ChannelModel channelModel = mChannelController.getChannelModel(chat.getChatGuid());

        if (channelModel != null) {
            item.channelLayoutModel = channelModel.layout;
            item.setTitle(channelModel.shortDesc);
        }
        return item;
    }

    @Deprecated
    protected int getStateForSingleChat(Message message)
            throws LocalizedException {
        if (message == null) {
            return Contact.STATE_LOW_TRUST;
        }

        String guid = !message.getIsSentMessage() ? message.getFrom() : message.getTo();
        Contact contact = contactController.getContactByGuid(guid);

        Integer state = (contact != null) ? contact.getState() : null;

        if (state != null) {
            return state;
        } else {
            return Contact.STATE_LOW_TRUST;
        }
    }

    private int getStateForSingleChat(final String guid)
            throws LocalizedException {
        if (guid == null) {
            return Contact.STATE_LOW_TRUST;
        }

        final Contact contact = contactController.getContactByGuid(guid);

        final Integer state = (contact != null) ? contact.getState() : null;

        if (state != null) {
            return state;
        } else {
            return Contact.STATE_LOW_TRUST;
        }
    }

    private int getStateForGroupChat(JsonArray members)
            throws LocalizedException {
        Gson gson = new Gson();
        int state = Contact.STATE_HIGH_TRUST;
        List<Contact> contacts = contactController.getContactsByGuid(gson.fromJson(members, String[].class));

        if ((contacts == null) || (contacts.size() == 0)) {
            return Contact.STATE_HIGH_TRUST;
        }

        for (Contact contact : contacts) {
            Integer contactState = contact.getState();

            if (contactState == Contact.STATE_LOW_TRUST) {
                return Contact.STATE_LOW_TRUST;
            } else if (contactState < state) {
                state = contactState;
            }
        }
        return state;
    }

    protected LazyList<Message> getGroupInvitationAndUnreadMessages(long lastMsgId) {
        synchronized (lockGetUnreadMessages) {
            MessageDao msgDao = messageController.getDao();
            long startTime = System.currentTimeMillis();
            try {

                QueryBuilder<Message> queryBuilder = msgDao.queryBuilder();

                queryBuilder.whereOr(Properties.Type.eq(Message.TYPE_GROUP_INVITATION),
                        Properties.Type.eq(Message.TYPE_PRIVATE),
                        Properties.Type.eq(Message.TYPE_GROUP),
                        Properties.Type.eq(Message.TYPE_CHANNEL));
                if (lastMsgId > 0) {
                    queryBuilder.where(Properties.Id.gt(lastMsgId));
                }
                queryBuilder.orderDesc(Properties.Id);

                return queryBuilder.build().forCurrentThread().listLazyUncached();
            } finally {
                long endTime = System.currentTimeMillis();
                LogUtil.i(TAG, "Time Used in getGroupInvitationAndUnreadMessages ms:" + (endTime - startTime));
            }
        }
    }

    private Chat getChatForMsgId(long lastMsgId) {
        synchronized (lockGetChat) {
            ChatDao chatDao = mChatController.getChatDao();

            QueryBuilder<Chat> queryBuilder = chatDao.queryBuilder();

            queryBuilder.where(ChatDao.Properties.LastMsgId.eq(lastMsgId));

            List<Chat> chats = queryBuilder.build().forCurrentThread().list();

            return chats != null && chats.size() > 0 ? chats.get(0) : null;
        }
    }

    protected Map<String, Chat> getChatsForRefresh(LazyList<Message> messages) {
        try {
            final Map<String, Chat> chatMap = new HashMap<>();
            final List<Message> readMessages = new ArrayList<>();

            LogUtil.d(TAG, "getChatsForRefresh: Processing LazyList with size " + messages.size());

            for (int i = 0; i < messages.size(); i++) {
                //LogUtil.d(TAG, "getChatsForRefresh: Processing message #" + i);
                Message message = messages.get(i);
                int messageType = message.getType();
                //LogUtil.d(TAG, "getChatsForRefresh: -> " + message.getGuid() + " of type = " + messageType);

                if (messageType == Message.TYPE_PRIVATE
                        || messageType == Message.TYPE_GROUP
                        || messageType == Message.TYPE_CHANNEL) {
                    String guid = MessageDataResolver.getGuidForMessage(message);

                    //Wenn der Chat schon in der Map existiert, naechste Nachricht bearbeiten
                    //weil die Nachrihcten sind absteigen nach Id sortiert
                    if (chatMap.containsKey(guid)) {
                        continue;
                    }

                    DecryptedMessage decryptedMessage = msgDecryptionController.decryptMessage(message, true);
                    if (decryptedMessage == null) {
                        continue;
                    }

                    Chat chat = mChatController.getChatByGuid(guid);

                    if (chat != null) {
                        Long lastMsgID = chat.getLastMsgId();
                        if (lastMsgID != null && message.getId() > lastMsgID) {
                            messageController.setLatestMessageToChat(message, chat, false);
                            mChatController.insertOrUpdateChat(chat);
                        }
                        chatMap.put(guid, chat);
                    }
                } else if (messageType == Message.TYPE_GROUP_INVITATION) {
                    if (message.getRead() != null && message.getRead()) {
                        continue;
                    }
                    readMessages.add(message);
                    Long msgID = message.getId();

                    if (msgID != null) {
                        Chat chat = getChatForMsgId(msgID);
                        final DecryptedMessage decryptedMessage = msgDecryptionController.decryptMessage(message, true);
                        if (decryptedMessage == null) {
                            continue;
                        }

                        if (chat != null) {
                            if (decryptedMessage.getContentType() != null && decryptedMessage.getContentType().endsWith("+encrypted")) {
                                mGroupChatController.handleAdditionalEncryptedInvitationData(chat, message, false);
                            }

                            chatMap.put(StringUtil.isNullOrEmpty(chat.getChatGuid()) ? GuidUtil.generateRoomGuid() : chat.getChatGuid(), chat);
                        } else {
                            messageController.setLatestMessageToChat(message);
                            chat = getChatForMsgId(msgID);

                            if (chat == null) continue;

                            chatMap.put(GuidUtil.generateRoomGuid(), chat);
                        }

                        if (message.isSentMessage() || StringUtil.isEqual("nopush", message.getPushInfo())) {
                            continue;
                        }

                        final String roomType = chat.getRoomType();
                        final String notificationText;
                        if (Chat.ROOM_TYPE_RESTRICTED.equals(roomType)) {
                            notificationText = mApplication.getString(R.string.push_restrictedRoomInv);
                        } else if (Chat.ROOM_TYPE_MANAGED.equals(roomType)) {
                            notificationText = mApplication.getString(R.string.push_managedRoomInv);
                        } else {
                            notificationText = mApplication.getString(R.string.push_groupInv);
                        }

                        mNotificationController.showGroupInvitationNotification(notificationText);
                    }
                }
            }

            if (readMessages.size() > 0) {
                for (Message message : readMessages) {
                    message.setRead(true);
                }

                messageController.updateMessages(readMessages);
            }

            return chatMap;
        } catch (LocalizedException e) {
            LogUtil.e(TAG, "getChatsForRefresh: " + e.getMessage());
            return null;
        }
    }

    protected void fillChat(final Chat aChat, final DecryptedMessage decryptedMessage)
            throws LocalizedException {
        int messageType = decryptedMessage.getMessage().getType();

        if (messageType == Message.TYPE_GROUP_INVITATION) {
            String guid = decryptedMessage.getGroupGuid();

            if (guid == null) {
                return;
            }

            //Pruefen ob wir den Chat schon kennen. Falls wir erneut eingeladen wurden
            final Chat oldChat = mChatController.getChatByGuid(guid);
            final Chat chat;
            if (oldChat != null) {
                mChatController.getChatDao().delete(aChat);
                mChatController.getChatDao().detach(aChat);
                chat = oldChat;
            } else {
                chat = aChat;
            }

            boolean autoAccept = StringUtil.isEqual(decryptedMessage.getMessage().getFrom(), mApplication.getAccountController().getAccount().getAccountGuid());

            chat.setChatGuid(guid);
            chat.setLastChatModifiedDate(decryptedMessage.getMessage().getDateSend());
            if (autoAccept) {
                chat.setType(Chat.TYPE_GROUP_CHAT);
            } else {
                chat.setType(Chat.TYPE_GROUP_CHAT_INVITATION);
            }
            chat.setTitle(decryptedMessage.getGroupName());
            chat.setChatAESKey(decryptedMessage.getMessage().getAesKeyDataContainer().getAesKey());
            chat.setChatInfoIV(decryptedMessage.getMessage().getAesKeyDataContainer().getIv());
            chat.setOwner(decryptedMessage.getMessage().getFrom());
            chat.setIsRemoved(false);

            if (decryptedMessage.getGroupImage() != null) {
                chatImageController.saveImage(guid, decryptedMessage.getGroupImage());
            }

            //Falls kein Kontakt fuer den Owner vorhanden ist, wird ein Neuer angelegt
            contactController.createContactIfNotExists(decryptedMessage.getMessage().getFrom(), decryptedMessage);

            //Aktuelle letzte Nachricht laden.
            Message message = messageController.loadLastChatMessage(chat.getChatGuid(), Message.TYPE_GROUP);

            if (message != null) {
                messageController.setLatestMessageToChat(message, chat, false);
            } else {
                chat.setLastMsgId(null);
            }

            mChatController.insertOrUpdateChat(chat);

            //Group Infos holen, damit wir das Bild bekommen
            mGroupChatController.getAndUpdateRoomInfo(guid);
        } else if (messageType == Message.TYPE_PRIVATE) {
            String guid = MessageDataResolver.getGuidForMessage(decryptedMessage.getMessage());
            String title = chatOverviewController.getTitleForMessage(decryptedMessage);

            Contact contact = contactController.createContactIfNotExists(guid, decryptedMessage);

            int type;
            if (StringUtil.isEqual(contact.getClassEntryName(), Contact.CLASS_DOMAIN_ENTRY) || StringUtil.isEqual(contact.getClassEntryName(), Contact.CLASS_COMPANY_ENTRY)) {
                contact.setState(Contact.STATE_HIGH_TRUST);
                type = Chat.TYPE_SINGLE_CHAT;
            } else {
                if (contact.getIsFirstContact()) {
                    type = Chat.TYPE_SINGLE_CHAT_INVITATION;
                } else {
                    type = Chat.TYPE_SINGLE_CHAT;
                }
            }

            if (!decryptedMessage.getMessage().isSentMessage() && StringUtil.isNullOrEmpty(contact.getProfileInfoAesKey())) {
                String profileKey = decryptedMessage.getProfilKey();
                if (!StringUtil.isNullOrEmpty(profileKey)) {
                    contactController.setProfilInfoAesKey(guid, profileKey, decryptedMessage);
                }
            }

            aChat.setChatGuid(guid);
            aChat.setType(type);
            aChat.setTitle(title);
            mChatController.insertOrUpdateChat(aChat);
        }
    }

    private int getMediaTypeForMessage(DecryptedMessage message) {
        if (message == null) {
            return BaseChatOverviewItemVO.MSG_MEDIA_TYPE_FAILED;
        }

        String contentType = message.getContentType();

        if (contentType == null) {
            return BaseChatOverviewItemVO.MSG_MEDIA_TYPE_FAILED;
        }

        if (message.getMessageDestructionParams() != null) {
            return BaseChatOverviewItemVO.MSG_MEDIA_TYPE_DESTROY;
        } else if (contentType.equals(MimeType.TEXT_PLAIN)) {
            return BaseChatOverviewItemVO.MSG_MEDIA_TYPE_TEXT;
        } else if (contentType.equals(MimeType.IMAGE_JPEG)) {
            return BaseChatOverviewItemVO.MSG_MEDIA_TYPE_IMAGE;
        } else if (contentType.equals(MimeType.VIDEO_MPEG)) {
            return BaseChatOverviewItemVO.MSG_MEDIA_TYPE_MOVIE;
        } else if (contentType.equals(MimeType.AUDIO_MPEG)) {
            return BaseChatOverviewItemVO.MSG_MEDIA_TYPE_AUDIO;
        } else if (contentType.equals(MimeType.MODEL_LOCATION)) {
            return BaseChatOverviewItemVO.MSG_MEDIA_TYPE_LOCATION;
        } else if (contentType.equals(MimeType.TEXT_V_CARD)) {
            return BaseChatOverviewItemVO.MSG_MEDIA_TYPE_VCARD;
        } else if (contentType.equals(MimeType.APP_OCTET_STREAM)) {
            return BaseChatOverviewItemVO.MSG_MEDIA_TYPE_FILE;
        } else if (contentType.equals(MimeType.TEXT_V_CALL)) {
            return BaseChatOverviewItemVO.MSG_MEDIA_TYPE_AVC;
        } else if (contentType.equals(MimeType.APP_GINLO_CONTROL)) {
        return BaseChatOverviewItemVO.MSG_MEDIA_TYPE_GINLOCONTROL;
    }
        return BaseChatOverviewItemVO.MSG_MEDIA_TYPE_FAILED;
    }

    private void checkIfMessageIsStillSending(@NonNull Message message, @NonNull BaseChatOverviewItemVO item) {
        if (message.getIsSentMessage() == null || !message.getIsSentMessage()) {
            return;
        }

        if (message.getId() != null
                && (message.getDateSendConfirm() == null || message.getDateSendConfirm() < 1)
                && (message.getHasSendError() == null || !message.getHasSendError())) {
            //wurde noch nicht versand
            //pruefen ob sie gerade versand wird
            if (!messageController.isMessageStillSending(message.getId())) {
                //Nachricht wird gerade nicht versand, daher als Senden fehlgeschlagen markieren
                messageController.markAsError(message, true);
                item.hasSendError = true;
            }
        }
    }
}
