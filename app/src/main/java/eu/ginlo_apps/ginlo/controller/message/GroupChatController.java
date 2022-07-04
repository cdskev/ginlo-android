// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.concurrent.manager.SerialExecutor;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.controller.message.contracts.GroupInfoChangedListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnAcceptInvitationListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnBuildChatRoomListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnDeclineInvitationListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnRemoveRoomListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnSetGroupInfoListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnUpdateGroupMembersListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.SilenceChatListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.AESKeyDataContainer;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.ChatRoomModel;
import eu.ginlo_apps.ginlo.model.backend.GroupInvMessageModel;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;
import eu.ginlo_apps.ginlo.model.backend.SignatureModel;
import eu.ginlo_apps.ginlo.model.backend.action.GroupAction;
import eu.ginlo_apps.ginlo.model.backend.action.InviteGroupMemberAction;
import eu.ginlo_apps.ginlo.model.backend.action.NewGroupAdminAction;
import eu.ginlo_apps.ginlo.model.backend.action.NewGroupMemberAction;
import eu.ginlo_apps.ginlo.model.backend.action.RemoveChatRoomV1Action;
import eu.ginlo_apps.ginlo.model.backend.action.RemoveGroupMemberAction;
import eu.ginlo_apps.ginlo.model.backend.action.RevokeGroupAdminAction;
import eu.ginlo_apps.ginlo.model.backend.serialization.ChatRoomModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ChatRoomModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.GroupInvMessageModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.GroupInvMessageModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.KeyContainerModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.SignatureModelSerializer;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.BackendError;
import eu.ginlo_apps.ginlo.model.constant.DataContainer;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.ImageUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.MessageModelBuilder;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

public class GroupChatController extends ChatController {

    private static final String TAG = GroupChatController.class.getSimpleName();
    private static final SerialExecutor GROUP_INFO_SERIAL_EXECUTOR = new SerialExecutor();
    private final Gson mGson;
    private final List<GroupInfoChangedListener> mGroupInfoChangedListeners;

    public GroupChatController(final SimsMeApplication application) {
        super(application);

        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(ChatRoomModel.class, new ChatRoomModelDeserializer());
        gsonBuilder.registerTypeAdapter(GroupInvMessageModel.class, new GroupInvMessageModelSerializer());
        gsonBuilder.registerTypeAdapter(GroupInvMessageModel.class, new GroupInvMessageModelDeserializer());
        gsonBuilder.registerTypeAdapter(ChatRoomModel.class, new ChatRoomModelSerializer());
        gsonBuilder.registerTypeAdapter(KeyContainerModel.class, new KeyContainerModelSerializer());
        gsonBuilder.registerTypeAdapter(SignatureModel.class, new SignatureModelSerializer());

        mGson = gsonBuilder.create();

        mGroupInfoChangedListeners = new ArrayList<>();
    }

    private static int getStringIdForAction(final GroupAction action, final boolean hasSenderName) {
        if (action instanceof InviteGroupMemberAction) {
            if (hasSenderName) {
                return R.string.chat_group_invite_member_with_sender;
            } else {
                return R.string.chat_group_invite_member;
            }
        } else if (action instanceof NewGroupAdminAction) {
            if (hasSenderName) {
                return R.string.chat_group_new_admin_with_sender;
            } else {
                return R.string.chat_group_new_admin;
            }
        } else if (action instanceof RevokeGroupAdminAction) {
            if (hasSenderName) {
                return R.string.chat_group_revoke_admin_with_sender;
            } else {
                return R.string.chat_group_revoke_admin;
            }
        }

        return -1;
    }

    private static boolean checkActionValues(final GroupAction action) {
        if (action == null) {
            return false;
        }

        if (StringUtil.isNullOrEmpty(action.getGroupGuid())) {
            return false;
        }

        return action.getGuids() != null && action.getGuids().length >= 1;
    }

    private static JsonArray getNotSendArray(final JsonObject response, final String joKey) {
        if (!response.has(joKey)) {
            return null;
        }

        final JsonObject resultInfo = response.getAsJsonObject(joKey);

        if (!resultInfo.has("not-send")) {
            return null;
        }

        return resultInfo.getAsJsonArray("not-send");
    }

    public void handleAdditionalEncryptedInvitationData(final Chat chat, final Message message, final boolean async) {
        if (async) {
            final AsyncDecryptionTask asyncDecryptionTask = new AsyncDecryptionTask(chat, message);
            asyncDecryptionTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null);
        } else {
            final LocalizedException le = decryptInvitationAndUpdateChat(chat, message);
            acceptInvitation(le, chat);
        }

    }

    public void addGroupInfoChangedListener(final GroupInfoChangedListener groupInfoChangedListener) {
        mGroupInfoChangedListeners.add(groupInfoChangedListener);
    }

    public void removeGroupInfoChangedListener(final GroupInfoChangedListener groupInfoChangedListener) {
        mGroupInfoChangedListeners.remove(groupInfoChangedListener);
    }

    @Override
    public int[] getTypeResponsibility() {
        return new int[]
                {
                        Message.TYPE_GROUP,
                        Message.TYPE_GROUP_INVITATION
                };
    }

    @Override
    public int getMainTypeResponsibility() {
        return Message.TYPE_GROUP;
    }

    @Override
    public AESKeyDataContainer getEncryptionData(String recipientGuid) throws LocalizedException {
        final Chat chat = getChatByGuid(recipientGuid);

        if (chat != null && chat.getChatAESKey() != null)
            return new AESKeyDataContainer(chat.getChatAESKey(), null);
        else return new AESKeyDataContainer(SecurityUtil.generateAESKey(), null);
    }

    void setGroupImage(final String chatGuid,
                       final byte[] imageBytes)
            throws LocalizedException {
        mApplication.getImageController().saveProfileImageRaw(chatGuid, imageBytes);
    }

    void setGroupName(final String chatGuid,
                      final String name)
            throws LocalizedException {
        final Chat chat = getChatByGuid(chatGuid);

        if (chat != null) {
            if (!StringUtil.isEqual(chat.getTitle(), name)) {
                chat.setTitle(name);
                synchronized (chatDao) {
                    chatDao.update(chat);
                }

                mApplication.getChatOverviewController().chatChanged(null, chatGuid, null, ChatOverviewController.CHAT_CHANGED_TITLE);
            }
        }
    }

    public void acceptInvitation(final Chat chat,
                                 final OnAcceptInvitationListener onAcceptInvitationListener) {
        if (chat.getType() != Chat.TYPE_GROUP_CHAT_INVITATION) {
            if (onAcceptInvitationListener != null) {
                onAcceptInvitationListener.onAcceptSuccess(chat);
            }
            return;
        }

        final AcceptInvitationTask task = new AcceptInvitationTask(mApplication, chat, onAcceptInvitationListener);
        task.executeOnExecutor(AcceptInvitationTask.THREAD_POOL_EXECUTOR, null, null, null);
    }

    public void declineInvitation(final Chat chat,
                                  final OnDeclineInvitationListener onDeclineInvitationListener) {
        if (chat.getType() != Chat.TYPE_GROUP_CHAT_INVITATION) {
            onDeclineInvitationListener.onDeclineSuccess(chat);
            return;
        }

        final DeclineInvitationTask task = new DeclineInvitationTask(mApplication, chat, onDeclineInvitationListener);
        task.executeOnExecutor(DeclineInvitationTask.THREAD_POOL_EXECUTOR, null, null, null);
    }

    public void createGroup(final String chatRoomName,
                            final byte[] chatRoomImage,
                            final String chatRoomType,
                            final List<Contact> members,
                            final List<String> admins,
                            final List<String> writers,
                            final OnBuildChatRoomListener buildChatRoomListener) {
        final CreateGroupTask task = new CreateGroupTask(mApplication, chatRoomName, chatRoomImage, chatRoomType, members, admins, writers, buildChatRoomListener);
        task.executeOnExecutor(CreateGroupTask.THREAD_POOL_EXECUTOR, null, null, null);
    }

    public void getAndUpdateRoomInfo(final String chatGuid) {
        final GetAndUpdateRoomInfoTask task = new GetAndUpdateRoomInfoTask(mApplication, chatGuid);
        task.executeOnExecutor(GROUP_INFO_SERIAL_EXECUTOR, null, null, null);
    }

    public void setGroupInfo(final Chat chat, final String groupName, final byte[] image, final OnSetGroupInfoListener onSetGroupInfoListener) {
        final SetGroupInfoTask task = new SetGroupInfoTask(mApplication, chat, image, groupName, onSetGroupInfoListener);
        task.executeOnExecutor(SetGroupInfoTask.THREAD_POOL_EXECUTOR, null, null, null);
    }

    public void updateGroupMember(final Chat chat,
                                  final List<Contact> addedMembers,
                                  final List<Contact> removedMembers,
                                  final List<String> addedAdmins,
                                  final List<String> removedAdmins,
                                  final byte[] newImg,
                                  final String newGroupName,
                                  final OnUpdateGroupMembersListener onUpdateGroupMembersListener) {
        final UpdateGroupMembersTask task = new UpdateGroupMembersTask(mApplication, chat, addedMembers, removedMembers,
                addedAdmins, removedAdmins, newImg, newGroupName, onUpdateGroupMembersListener);
        task.executeOnExecutor(UpdateGroupMembersTask.THREAD_POOL_EXECUTOR, null, null, null);
    }

    public void removeRoom(final String chatRoomGuid,
                           final OnRemoveRoomListener onRemoveRoomListener)
            throws LocalizedException {
        final Chat chat = getChatByGuid(chatRoomGuid);

        if (chat != null) {
            final AccountController accountController = mApplication.getAccountController();
            if ((chat.getOwner() != null) && chat.getOwner().equals(accountController.getAccount().getAccountGuid())) {
                removeRoomAsAdmin(chatRoomGuid, onRemoveRoomListener);
            } else {
                removeRoomAsMember(chatRoomGuid, onRemoveRoomListener);
            }
        }
    }

    public void markAsRemoved(final RemoveChatRoomV1Action action) {
        final String chatRoomGuid = action.getGuid();
        final Chat chat = getChatByGuid(chatRoomGuid);

        if (chat != null) {
            if (action.isOwnMessage()) {
                deleteGroupChat(chatRoomGuid);
                chatOverviewController.chatChanged(null, chatRoomGuid, null, ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
            } else {
                try {
                    chat.setIsRemoved(true);
                    chatDao.update(chat);

                    //Bug 32926 - Gruppe loeschen fuehrt zum Loeschen des Inhalts der Gruppe bei allen Mitgliedern
                    sendSystemInfo(chatRoomGuid, null, mApplication.getResources().getString(R.string.chat_group_wasDeleted), -1);
                } catch (LocalizedException e) {
                    LogUtil.w(TAG, "markAsRemoved", e);
                }
            }
        }
    }

    void newRoomMember(final String chatRoomGuid,
                       final String[] members,
                       final String[] names)
            throws LocalizedException {
        final Chat chat = getChatByGuid(chatRoomGuid);

        if (chat != null) {
            final ContactController contactController = mApplication.getContactController();
            final String ownGuid = mApplication.getAccountController().getAccount().getAccountGuid();
            final JsonArray chatMembers = chat.getMembers();

            for (int i = 0; i < members.length; i++) {
                final String guid = members[i];

                if (StringUtil.isEqual(guid, ownGuid)) {
                    continue;
                }

                String nickName = null;
                if (names != null) {
                    nickName = names[i];
                    contactController.createContactIfNotExists(members[i], nickName, null, null, null, true);
                } else {
                    contactController.createContactIfNotExists(members[i], null, null, true);
                }

                final Contact contact = contactController.getContactByGuid(members[i]);
                String name = contact.getName();

                if (StringUtil.isNullOrEmpty(name)) {
                    if (!StringUtil.isNullOrEmpty(nickName)) {
                        name = nickName;
                        contact.setNickname(nickName);
                        contactController.insertOrUpdateContact(contact);
                    } else {
                        //name = mApplication.getString(R.string.chats_contact_unknown);
                        name = contact.getSimsmeId();
                    }
                }
                try {
                    if (!Chat.ROOM_TYPE_RESTRICTED.equals(chat.getRoomType())) {
                        if (!Chat.ROOM_TYPE_ANNOUNCEMENT.equals(chat.getRoomType()) || amIGroupAdmin(chat)) {
                            final String message = mApplication.getString(R.string.chat_group_newMember, name);
                            sendSystemInfo(chatRoomGuid, null, message, -1);
                        }
                    }
                } catch (final LocalizedException le) {
                    LogUtil.w(TAG, le.getMessage(), le);
                }
                boolean found = false;
                for (int k = 0; k < chatMembers.size(); k++) {
                    final String chatMemberGuid = chatMembers.get(k).getAsString();
                    if (StringUtil.isEqual(chatMemberGuid, guid)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    chatMembers.add(new JsonPrimitive(guid));
                }
            }

            chat.setMembers(chatMembers);

            insertOrUpdateChat(chat);

            final GetGroupMemberInfoTask task = new GetGroupMemberInfoTask(mApplication, chat.getChatGuid());
            task.executeOnExecutor(GetGroupMemberInfoTask.THREAD_POOL_EXECUTOR, null, null, null);
        }
    }

    public void newRoomMember(final NewGroupMemberAction action) {
        try {
            if (!checkActionValues(action)) {
                return;
            }

            final Chat chat = getChatByGuid(action.getGroupGuid());

            if (chat != null) {
                final String ownGuid = mApplication.getAccountController().getAccount().getAccountGuid();
                List<String> noContactInfosList = null;

                for (final String contactGuid : action.getGuids()) {
                    final String name = getContactNameForGuid(contactGuid);

                    if (StringUtil.isNullOrEmpty(name)) {
                        //Kein benutzbaren Info gefunden --> daher spaeter laden und dann system info erstellen
                        if (noContactInfosList == null) {
                            noContactInfosList = new ArrayList<>(action.getGuids().length);
                        }

                        if (!noContactInfosList.contains(contactGuid)) {
                            noContactInfosList.add(contactGuid);
                        }
                    }
                }

                if (noContactInfosList != null && noContactInfosList.size() > 0) {
                    try {
                        mApplication.getContactController().loadContactsAccountInfo(noContactInfosList);
                    } catch (LocalizedException e) {
                        LogUtil.w(TAG, "newRoomMember", e);
                    }
                    if (!Chat.ROOM_TYPE_ANNOUNCEMENT.equals(chat.getRoomType()) || amIGroupAdmin(chat)) {
                        for (String contactGuid : noContactInfosList) {
                            final String message = mApplication.getString(R.string.chat_group_newMember, contactGuid);
                            sendSystemInfo(action.getGroupGuid(), null, message, action.internalMessageId);
                        }
                    }
                }

                Handler handler = new Handler(mApplication.getMainLooper());
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            for (final String contactGuid : action.getGuids()) {
                                if (StringUtil.isEqual(contactGuid, ownGuid)) {
                                    if (action.isOwnMessage() && (chat.getType() != null) && (chat.getType() == Chat.TYPE_GROUP_CHAT_INVITATION)) {
                                        chat.setType(Chat.TYPE_GROUP_CHAT);
                                        mApplication.getChatOverviewController().chatChanged(null, chat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                                    }
                                    continue;
                                }

                                if (!Chat.ROOM_TYPE_RESTRICTED.equals(chat.getRoomType())) {
                                    if (!Chat.ROOM_TYPE_ANNOUNCEMENT.equals(chat.getRoomType()) || amIGroupAdmin(chat)) {
                                        final String message = mApplication.getString(R.string.chat_group_newMember, contactGuid);
                                        sendSystemInfo(action.getGroupGuid(), null, message, action.internalMessageId);
                                    }
                                }
                            }

                            updateRoomMemberInfo(action.getGroupGuid());
                        } catch (LocalizedException e) {
                            LogUtil.w(TAG, e.getMessage(), e);
                        }
                    }
                };
                handler.post(runnable);
            }
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }
    }

    public void doRoomAction(final GroupAction action) {
        try {
            if (!checkActionValues(action)) {
                return;
            }

            final Chat chat = getChatByGuid(action.getGroupGuid());

            if (chat != null) {
                final String senderName = getSenderNameFromAction(action);
                List<String> noContactInfosList = null;

                if (StringUtil.isNullOrEmpty(senderName) && !StringUtil.isNullOrEmpty(action.getSenderGuid())) {
                    //Kein benutzbaren Info gefunden --> daher spaeter laden und dann system info erstellen
                    noContactInfosList = new ArrayList<>(action.getGuids().length);
                    noContactInfosList.add(action.getSenderGuid());
                }

                for (final String contactGuid : action.getGuids()) {
                    final String name = getContactNameForGuid(contactGuid);

                    if (StringUtil.isNullOrEmpty(name)) {
                        //Kein benutzbaren Info gefunden --> daher spaeter laden und dann system info erstellen
                        if (noContactInfosList == null) {
                            noContactInfosList = new ArrayList<>(action.getGuids().length);
                        }

                        if (!noContactInfosList.contains(contactGuid)) {
                            noContactInfosList.add(contactGuid);
                        }
                    }
                }

                if (noContactInfosList != null && noContactInfosList.size() > 0) {
                    try {
                        mApplication.getContactController().loadContactsAccountInfo(noContactInfosList);
                    } catch (LocalizedException e) {
                        LogUtil.w(TAG, "newRoomMember", e);
                    }
                }

                Handler handler = new Handler(mApplication.getMainLooper());
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        String roomType = Chat.ROOM_TYPE_STD;
                        try {
                            roomType = chat.getRoomType();
                        } catch (LocalizedException e) {
                            LogUtil.w(TAG, e.getMessage(), e);
                        }

                        if(!Chat.ROOM_TYPE_ANNOUNCEMENT.equals(roomType) || amIGroupAdmin(chat)) {
                            for (final String contactGuid : action.getGuids()) {
                                final String message;
                                if (StringUtil.isNullOrEmpty(action.getSenderGuid())) {
                                    message = mApplication.getString(getStringIdForAction(action, false), contactGuid);
                                } else {
                                    message = mApplication.getString(getStringIdForAction(action, true), action.getSenderGuid(), contactGuid);
                                }
                                sendSystemInfo(action.getGroupGuid(), null, message, action.internalMessageId);
                            }
                        }
                        updateRoomMemberInfo(action.getGroupGuid());
                    }
                };
                handler.post(runnable);
            }
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }
    }

    private String getSenderNameFromAction(final GroupAction action)
            throws LocalizedException {
        final String senderGuid = action.getSenderGuid();
        if (senderGuid == null) {
            return null;
        }

        if (StringUtil.isEqual(senderGuid, AppConstants.GUID_SYSTEM_CHAT)) {
            return null;
        }

        String nickname = null;
        if (!StringUtil.isNullOrEmpty(action.getNickName())) {
            final byte[] nicknameBytes = Base64.decode(action.getNickName(), Base64.NO_WRAP);
            nickname = new String(nicknameBytes, StandardCharsets.UTF_8);
        }

        final ContactController contactController = mApplication.getContactController();
        final Contact contact = contactController.createContactIfNotExists(senderGuid, nickname, null, null, null, true);

        if (contact != null) {
            return contact.getName();
        }

        return null;
    }

    private String getContactNameForGuid(final String guid)
            throws LocalizedException {
        final ContactController contactController = mApplication.getContactController();
        final Contact contact = contactController.createContactIfNotExists(guid, null);

        String name = null;

        if (contact == null) {
            final Account account = mApplication.getAccountController().getAccount();

            if (StringUtil.isEqual(guid, account.getAccountGuid())) {
                name = account.getName();
            }
        } else {
            name = contact.getName();

            if (StringUtil.isNullOrEmpty(name)) {
                //name = mApplication.getString(R.string.chats_contact_unknown);
                name = contact.getSimsmeId();
            }
        }

        return name;
    }

    private void updateRoomMemberInfo(@NonNull final String chatGuid) {
        final GetGroupMemberInfoTask task = new GetGroupMemberInfoTask(mApplication, chatGuid);
        task.executeOnExecutor(GROUP_INFO_SERIAL_EXECUTOR, null, null, null);
    }

    public boolean removeRoomMember(final RemoveGroupMemberAction action) {
        try {
            if (action == null) {
                return true;
            }

            if (StringUtil.isNullOrEmpty(action.getGroupGuid())) {
                return true;
            }

            if (action.getGuids() == null || action.getGuids().length < 1) {
                return true;
            }

            final Chat chat = getChatByGuid(action.getGroupGuid());

            if (chat != null) {
                final String ownGuid = mApplication.getAccountController().getAccount().getAccountGuid();
                final String roomType = chat.getRoomType();

                for (final String contactGuid : action.getGuids()) {
                    if (StringUtil.isEqual(contactGuid, ownGuid)) {
                        // I leave the group
                        if (action.isOwnMessage()) {
                            String chatRoomGuid = chat.getChatGuid();
                            deleteGroupChat(chatRoomGuid);
                            chatOverviewController.chatChanged(null, chatRoomGuid, null, ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                            continue;
                        }
                        chat.setIsRemoved(true);
                        synchronized (chatDao) {
                            chatDao.update(chat);
                        }
                    }

                    final String message;
                    if (StringUtil.isNullOrEmpty(action.getSenderGuid())) {
                        message = mApplication.getString(R.string.chat_group_removedMember, contactGuid);
                    } else {
                        if (StringUtil.isNullOrEmpty(contactGuid)) {
                            //fallback, falls guid leer ist
                            message = mApplication.getString(R.string.chat_group_removed_member_with_sender,
                                    action.getSenderGuid(), mApplication.getString(R.string.chats_contact_unknown));
                        } else {
                            message = mApplication.getString(R.string.chat_group_removed_member_with_sender,
                                    action.getSenderGuid(), contactGuid);
                        }
                    }

                    if (Chat.ROOM_TYPE_ANNOUNCEMENT.equals(roomType)) {
                        if (StringUtil.isEqual(contactGuid, ownGuid) || amIGroupAdmin(chat)){
                            final String altmessage = mApplication.getString(R.string.chat_group_removed_member_with_sender,
                                    action.getSenderGuid(), contactGuid);
                            sendSystemInfo(action.getGroupGuid(), null, altmessage, action.internalMessageId);
                        }
                    } else {
                        sendSystemInfo(action.getGroupGuid(), null, message, action.internalMessageId);
                    }
                }

                updateRoomMemberInfo(action.getGroupGuid());
            }
        } catch (final LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }

        return action.internalMessageId == -1;
    }

    public List<Contact> getChatMembers(final JsonArray members)
            throws LocalizedException {
        final AccountController accountController = mApplication.getAccountController();
        final ContactController contactController = mApplication.getContactController();

        final ArrayList<String> guids = new ArrayList<>();

        boolean addAccountContact = false;

        for (int i = 0; i < members.size(); i++) {
            final String guid = members.get(i).getAsString();

            if (accountController.getAccount().getAccountGuid().equals(guid)) {
                addAccountContact = true;
            } else {
                guids.add(members.get(i).getAsString());
            }
        }

        final List<Contact> contacts = contactController.getContactsByGuid(guids.toArray(new String[]{}));

        if (addAccountContact) {
            contacts.add(contactController.getOwnContact());
        }

        final List<Contact> rc = new ArrayList<>();

        for (final Contact contact : contacts) {
            if (contact == null || contact.isDeletedHidden()) {
                continue;
            }
            rc.add(contact);
        }

        return rc;
    }

    public boolean amIGroupAdmin(final Chat chat) {
        final String ownGuid = mApplication.getAccountController().getAccount().getAccountGuid();

        JsonArray adminArray = null;
        try {
            adminArray = chat.getAdmins();
        } catch (LocalizedException e) {
            LogUtil.w(TAG, e.getMessage(), e);
        }

        if (adminArray == null || adminArray.size() < 1) {
            return false;
        }

        boolean found = false;
        for (int i = 0; i < adminArray.size(); i++) {
            final String adminGuid = adminArray.get(i).getAsString();
            if (StringUtil.isEqual(ownGuid, adminGuid)) {
                found = true;
                break;
            }
        }

        return found;
    }

    public int getStateForGroupChat(final String chatGuid)
            throws LocalizedException {
        final Chat chat = getChatByGuid(chatGuid);

        if (chat == null) {
            return Contact.STATE_UNSIMSABLE;
        }

        if (Chat.ROOM_TYPE_MANAGED.equals(chat.getRoomType()) || Chat.ROOM_TYPE_RESTRICTED.equals(chat.getRoomType())) {
            return Contact.STATE_HIGH_TRUST;
        }

        final JsonArray jsonMembers = chat.getMembers();

        if (jsonMembers == null) {
            return Contact.STATE_UNSIMSABLE;
        }

        final List<Contact> contacts = getChatMembers(jsonMembers);

        int resultState = Contact.STATE_HIGH_TRUST;

        for (final Contact contact : contacts) {
            final Integer state = contact.getState();

            if (state == null) {
                return Contact.STATE_LOW_TRUST;
            }

            if (state < resultState) {
                resultState = state;
            }
        }

        return resultState;
    }

    private void removeRoomAsMember(final String chatRoomGuid,
                                    final OnRemoveRoomListener onRemoveRoomListener) {
        final AccountController accountController = mApplication.getAccountController();
        final Chat chat = getChatByGuid(chatRoomGuid);
        final IBackendService.OnBackendResponseListener onBackendResponseListener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(final BackendResponse response) {
                boolean allowRemove = (!response.isError);
                if (response.msgException != null && response.msgException.getIdent() != null) {
                    if (response.msgException.getIdent().equals(BackendError.ERR_0079_NOT_GROUP_MEMBER)
                            || response.msgException.getIdent().equals(BackendError.ERR_0026_CANT_OPEN_MESSAGE)
                            || response.msgException.getIdent().equals(BackendError.ERR_0101_NOT_GROUP_MEMBER)) {
                        allowRemove = true;
                    }
                }

                if (allowRemove) {
                    try {
                        if (response.jsonObject != null) {
                            final JsonArray notSendArray = getNotSendArray(response.jsonObject, "GroupInfoResult");

                            if (notSendArray != null && notSendArray.size() > 0) {
                                privateInternalMessageController.broadcastRemoveGroupMember(chat, notSendArray,
                                        new String[]{accountController.getAccount().getAccountGuid()});
                            }
                        }

                        deleteGroupChat(chatRoomGuid);
                    } catch (final LocalizedException e) {
                        LogUtil.w(TAG, e.getMessage(), e);
                    }
                    if (onRemoveRoomListener != null) {
                        onRemoveRoomListener.onRemoveRoomSuccess();
                    }
                } else {
                    if (onRemoveRoomListener != null) {
                        onRemoveRoomListener.onRemoveRoomFail(response.errorMessage);
                    }
                }
            }
        };
        BackendService.withAsyncConnection(mApplication).removeFromRoom(chatRoomGuid, accountController.getAccount().getAccountGuid(),
                getEncodedAccountNameForSend(), onBackendResponseListener);
    }

    private void removeRoomAsAdmin(final String chatRoomGuid,
                                   final OnRemoveRoomListener onRemoveRoomListener) {
        final IBackendService.OnBackendResponseListener onBackendResponseListener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(final BackendResponse response) {
                if (response.isError) {
                    if (onRemoveRoomListener != null) {
                        if (response.errorMessage.contains(LocalizedException.ROOM_UNKNOWN)) {
                            deleteGroupChat(chatRoomGuid);
                        }
                        onRemoveRoomListener.onRemoveRoomFail(response.errorMessage);
                    }
                } else {
                    deleteGroupChat(chatRoomGuid);
                    if (onRemoveRoomListener != null) {
                        onRemoveRoomListener.onRemoveRoomSuccess();
                    }
                }
            }
        };
        BackendService.withAsyncConnection(mApplication)
                .removeRoom(chatRoomGuid, onBackendResponseListener);
    }

    private String getEncodedAccountNameForSend() {
        if (mApplication.getPreferencesController().getSendProfileName()) {
            final AccountController accountController = mApplication.getAccountController();
            final Account account = accountController.getAccount();

            if (account != null && !StringUtil.isNullOrEmpty(account.getName())) {
                return Base64.encodeToString(account.getName().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            }
        }
        return null;
    }

    @Override
    public void silenceChat(final String chatGuid,
                            final String dateString,
                            final SilenceChatListener silenceChatListener) {

        IBackendService.OnBackendResponseListener onBackendResponseListener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(final BackendResponse response) {
                try {

                    if (response.isError) {
                        if (silenceChatListener != null) {
                            silenceChatListener.onFail(response.errorMessage);
                        }
                    } else {
                        if (silenceChatListener != null) {
                            silenceChatListener.onSuccess();
                        }
                        final Chat chat = getChatByGuid(chatGuid);
                        chat.setSilentTill(DateUtil.utcWithoutMillisStringToMillis(dateString));
                        synchronized (chatDao) {
                            chatDao.update(chat);
                        }
                    }
                } catch (final LocalizedException le) {
                    LogUtil.w(TAG, le.getMessage(), le);
                    if (silenceChatListener != null) {
                        silenceChatListener.onFail(mApplication.getResources().getString(R.string.chat_mute_error));
                    }
                }
            }
        };

        BackendService.withAsyncConnection(mApplication)
                .setSilentGroupChat(chatGuid,
                        dateString,
                        onBackendResponseListener);
    }

    ///////////////////// KS: Methods/classes from former GroupChatControllerBusiness

    private LocalizedException decryptInvitationAndUpdateChat(final Chat chat, final Message message) {

        try {
            if (chat == null || message == null) {
                throw new LocalizedException("No chat or message given.");
            }

            final AccountController AccountController = mApplication.getAccountController();
            final SecretKey companyAesKey = AccountController.getCompanyAesKey();

            if(companyAesKey == null) {
                throw new LocalizedException("No company aes key found.");
            }

            final JsonObject decryptedDataContainer = message.getDecryptedDataContainer();

            if (decryptedDataContainer != null &&
                    JsonUtil.hasKey("data", decryptedDataContainer) &&
                    JsonUtil.hasKey("iv", decryptedDataContainer)) {
                final JsonElement iv = decryptedDataContainer.get("iv");
                final String ivString = iv.getAsString();
                if (!StringUtil.isNullOrEmpty(ivString)) {
                    final byte[] decodedIvBytes = Base64.decode(ivString, Base64.NO_WRAP);
                    final JsonElement data = decryptedDataContainer.get("data");
                    final String dataString = data.getAsString();
                    if (!StringUtil.isNullOrEmpty(dataString)) {
                        final byte[] decodedData = Base64.decode(dataString.getBytes(), Base64.NO_WRAP);

                        final byte[] bytes = SecurityUtil.decryptMessageWithAES(decodedData, companyAesKey, new IvParameterSpec(decodedIvBytes));
                        final String decryptedData = new String(bytes);
                        final JsonParser jsonParser = new JsonParser();
                        final JsonElement parsedDecyptedData = jsonParser.parse(decryptedData);
                        if (parsedDecyptedData != null) {
                            final JsonObject ddJsonObject = parsedDecyptedData.getAsJsonObject();
                            if (JsonUtil.hasKey("GroupType", ddJsonObject)) {
                                final JsonElement groupType = ddJsonObject.get("GroupType");
                                chat.setRoomType(groupType.getAsString());
                                chat.setType(Chat.TYPE_GROUP_CHAT_INVITATION);
                            }
                            if (JsonUtil.hasKey("GroupName", ddJsonObject)) {
                                final JsonElement groupName = ddJsonObject.get("GroupName");
                                chat.setTitle(groupName.getAsString());
                            }

                            if (JsonUtil.hasKey("GroupGuid", ddJsonObject)) {
                                final JsonElement groupGuid = ddJsonObject.get("GroupGuid");
                                chat.setChatGuid(groupGuid.getAsString());
                            }

                            if (JsonUtil.hasKey("GroupAesKey", ddJsonObject)) {
                                final JsonElement groupAesKey = ddJsonObject.get("GroupAesKey");
                                chat.setChatAESKeyAsBase64(groupAesKey.getAsString());
                            }
                            chatDao.update(chat);
                        }
                    }
                }
            }
        } catch (final LocalizedException le) {
            return le;
        } catch (final JsonParseException jpe) {
            return new LocalizedException("Error parsing decrypted invitation data.", jpe);
        }
        return null;
    }

    private void acceptInvitation(final LocalizedException decryptInvitationE, final Chat chat) {
        try {

            if (decryptInvitationE != null || StringUtil.isNullOrEmpty(chat.getChatAESKeyAsBase64())) {
                LogUtil.i(TAG, "No managed or restricted Group: " + decryptInvitationE.getMessage());
            } else {
                final OnAcceptInvitationListener onAcceptInvitationListener = new OnAcceptInvitationListener() {
                    @Override
                    public void onAcceptSuccess(final Chat chat) {
                        final String chatGuid = chat.getChatGuid();
                        chat.setType(Chat.TYPE_GROUP_CHAT);
                        try {
                            chat.setIsRemoved(false);
                        } catch (LocalizedException e) {
                            LogUtil.w(TAG, "Error setting chat attribute: ", e);
                        }

                        chatDao.update(chat);
                        mApplication.getChatOverviewController().chatChanged(null, chatGuid, null, ChatOverviewController.CHAT_CHANGED_NEW_CHAT);
                        getAndUpdateRoomInfo(chatGuid);
                    }

                    @Override
                    public void onAcceptError(final String message, final boolean chatWasRemoved) {

                    }
                };
                acceptInvitation(chat, onAcceptInvitationListener);

            }
        } catch (LocalizedException le) {
            LogUtil.w(TAG, "Error accepting new managed/restricted Group: " + le.getMessage());
        }
    }

    private class AsyncDecryptionTask extends AsyncTask<Void, Void, LocalizedException> {
        private final Chat mChat;
        private final Message mMessage;

        AsyncDecryptionTask(final Chat chat, final Message message) {
            mChat = chat;
            mMessage = message;
        }

        @Override
        protected LocalizedException doInBackground(final Void... params) {

            return decryptInvitationAndUpdateChat(mChat, mMessage);
        }

        @Override
        protected void onPostExecute(final LocalizedException exception) {
            acceptInvitation(exception, mChat);
        }
    }

    ///////////////////// KS: End of former GroupChatControllerBusiness methods/classes

    private class DeclineInvitationTask extends GroupTask {
        private final Chat mChat;
        private final OnDeclineInvitationListener mDeclineInvitationListener;
        private boolean mChatWasRemoved;

        DeclineInvitationTask(final SimsMeApplication application,
                              final Chat chat,
                              final OnDeclineInvitationListener onDeclineInvitationListener) {
            super(application);

            mChat = chat;
            mDeclineInvitationListener = onDeclineInvitationListener;
        }

        @Override
        protected Void doInBackground(final Void... params) {
            try {
                BackendService.withSyncConnection(mApp)
                        .declineRoomInvitation(mChat.getChatGuid(), getEncodedAccountNameForSend(),
                                new IBackendService.OnBackendResponseListener() {
                                    @Override
                                    public void onBackendResponse(final BackendResponse response) {
                                        if (response.isError) {
                                            mHasError = true;

                                            if (response.msgException != null &&
                                                    (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0079")
                                                            || StringUtil.isEqual(response.msgException.getIdent(), "ERR-0101"))) {
                                                deleteGroupChat(mChat.getChatGuid());
                                                mChatWasRemoved = true;
                                                mErrorDetail = response.errorMessage;
                                            } else {
                                                mErrorDetail = mApp.getString(R.string.create_group_error_server_communication);

                                                if (!StringUtil.isNullOrEmpty(response.errorMessage)) {
                                                    mErrorDetail = mErrorDetail + "\n" + response.errorMessage;
                                                }
                                            }

                                            return;
                                        }

                                        if (response.jsonObject == null || !response.jsonObject.has("RemoveMembersResult")) {
                                            mHasError = true;
                                            mErrorDetail = mApp.getString(R.string.create_group_error_server_communication);
                                            return;
                                        }

                                        final JsonObject groupResultInfo = response.jsonObject.getAsJsonObject("RemoveMembersResult");

                                        if (groupResultInfo.has("not-send")) {
                                            try {
                                                final Account account = mApp.getAccountController().getAccount();
                                                final JsonArray notSendArray = groupResultInfo.getAsJsonArray("not-send");

                                                privateInternalMessageController.broadcastRemoveGroupMember(mChat, notSendArray,
                                                        new String[]{account.getAccountGuid()});
                                                deleteGroupChat(mChat.getChatGuid());
                                            } catch (final LocalizedException e) {
                                                LogUtil.w(TAG, e.getMessage(), e);
                                            }
                                        }
                                    }
                                });
            } catch (final Exception e) {
                LogUtil.e(TAG, e.getMessage(), e);
                mHasError = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(final Void aVoid) {
            if (mHasError) {
                if (mDeclineInvitationListener != null) {
                    mDeclineInvitationListener.onDeclineError(mChat, mErrorDetail, mChatWasRemoved);
                }
            } else {
                if (mDeclineInvitationListener != null) {
                    mDeclineInvitationListener.onDeclineSuccess(mChat);
                }
            }
        }
    }

    private class AcceptInvitationTask extends GroupTask {
        private final long mChatId;
        private final String mChatGuid;
        private final OnAcceptInvitationListener mAcceptListner;
        private boolean mChatWasRemoved;

        AcceptInvitationTask(final SimsMeApplication application,
                             final Chat chat,
                             final OnAcceptInvitationListener onAcceptInvitationListener) {
            super(application);
            mChatId = chat.getId();
            mChatGuid = chat.getChatGuid();
            mAcceptListner = onAcceptInvitationListener;
        }

        @Override
        protected Void doInBackground(final Void... params) {
            try {
                BackendService.withSyncConnection(mApplication)
                        .acceptRoomInvitation(mChatGuid, getEncodedAccountNameForSend(),
                                new IBackendService.OnBackendResponseListener() {
                                    @Override
                                    public void onBackendResponse(final BackendResponse response) {
                                        try {
                                            if (response.isError) {
                                                mHasError = true;
                                                LogUtil.w(TAG, "AcceptInvitationTask returned with error: " + response.errorMessage);

                                                // Critical errors - rolling back
                                                // 0079 - NO_MEMBER_OF_CHAT_ROOM
                                                // 0101 - ROOM_UNKNOWN
                                                if (response.msgException != null
                                                        && (StringUtil.isEqual(response.msgException.getIdent(), "ERR-0079")
                                                            || StringUtil.isEqual(response.msgException.getIdent(), "ERR-0101"))) {
                                                    deleteGroupChat(mChatGuid);
                                                    mChatWasRemoved = true;
                                                    mErrorDetail = response.errorMessage;
                                                } else {
                                                    mErrorDetail = mApp.getString(R.string.create_group_error_server_communication);
                                                    if (!StringUtil.isNullOrEmpty(response.errorMessage)) {
                                                        mErrorDetail = mErrorDetail + "\n" + response.errorMessage;
                                                    }
                                                }

                                                return;
                                            }

                                            if (response.jsonObject == null) {
                                                mHasError = true;
                                                LogUtil.w(TAG, "AcceptInvitationTask returned with no data.");
                                                mErrorDetail = mApp.getString(R.string.create_group_error_server_communication);
                                                return;
                                            }

                                            if (!response.jsonObject.has(Chat.ROOM_TYPE_STD)
                                                    && !response.jsonObject.has(Chat.ROOM_TYPE_ANNOUNCEMENT)
                                                    && !response.jsonObject.has(Chat.ROOM_TYPE_RESTRICTED)
                                                    && !response.jsonObject.has(Chat.ROOM_TYPE_MANAGED)) {
                                                mHasError = true;
                                                LogUtil.w(TAG, "AcceptInvitationTask returned with unknown room type.");
                                                mErrorDetail = mApp.getString(R.string.create_group_error_server_communication);
                                                return;
                                            }

                                            final JsonObject groupResultInfo = JsonUtil.searchJsonObjectRecursive(response.jsonObject, "AddMembersResult");

                                            if (groupResultInfo != null && groupResultInfo.has("not-send")) {
                                                final Account account = mApp.getAccountController().getAccount();
                                                final JsonArray notSendArray = groupResultInfo.getAsJsonArray("not-send");

                                                privateInternalMessageController.broadcastAddGroupMember(mChatGuid, notSendArray,
                                                        new String[]{account.getAccountGuid()}, new String[]{account.getName()});
                                            }

                                            updateChatWithResponse(mChatGuid, null, response, true, true, true);

                                            createGroupContactsIfNotExist();
                                        } catch (final LocalizedException e) {
                                            LogUtil.w(TAG, e.getMessage(), e);
                                            mHasError = true;
                                        }
                                    }
                                });
            } catch (final Exception e) {
                LogUtil.e(TAG, e.getMessage(), e);
                mHasError = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(final Void aVoid) {
            if (mHasError) {
                if (mAcceptListner != null) {
                    mAcceptListner.onAcceptError(mErrorDetail, mChatWasRemoved);
                }
            } else {
                final Chat chat = getChatByGuid(mChatGuid);
                if (chat != null) {
                    try {
                        if (!Chat.ROOM_TYPE_RESTRICTED.equals(chat.getRoomType())) {
                            if (!Chat.ROOM_TYPE_ANNOUNCEMENT.equals(chat.getRoomType()) || amIGroupAdmin(chat)) {
                                final Account account = mApp.getAccountController().getAccount();
                                final String message = mApp.getString(R.string.chat_group_newMember, account.getAccountGuid());
                                sendSystemInfo(mChatGuid, null, message, -1);
                            }
                        }
                    } catch (final LocalizedException le) {
                        LogUtil.w(TAG, le.getMessage(), le);
                    }

                    if (mAcceptListner != null) {
                        mAcceptListner.onAcceptSuccess(chat);
                    }
                }
            }
        }

        private void createGroupContactsIfNotExist()
                throws LocalizedException {
            final Account account = mApp.getAccountController().getAccount();
            final String ownGuid = account.getAccountGuid();
            final ContactController contactController = mApp.getContactController();
            final Chat chat = getChatDao().load(mChatId);

            if (chat == null) {
                return;
            }

            final List<String> contactsWithoutPubKey = new ArrayList<>();

            final JsonArray members = chat.getMembers();
            for (int i = 0; i < members.size(); i++) {
                final String guid = members.get(i).getAsString();

                if (StringUtil.isEqual(guid, ownGuid)) {
                    continue;
                }

                final Contact contact = contactController.getContactByGuid(guid);

                if (contact == null) {
                    mApp.getContactController().createContactIfNotExists(guid, null, null, null, null, null, false, null, true);

                    contactsWithoutPubKey.add(guid);
                } else if (StringUtil.isNullOrEmpty(contact.getPublicKey())) {
                    contactsWithoutPubKey.add(contact.getAccountGuid());
                }
            }

            if ((StringUtil.isEqual(Chat.ROOM_TYPE_MANAGED, chat.getRoomType()) || StringUtil.isEqual(Chat.ROOM_TYPE_RESTRICTED, chat.getRoomType()))) {
                final Contact contact = contactController.getContactByGuid(chat.getOwner());
                if (contact != null) {
                    contactController.upgradeTrustLevel(contact, Contact.STATE_HIGH_TRUST);
                }
            }

            if (contactsWithoutPubKey.size() > 0) {
                loadPublicKeys(contactsWithoutPubKey, null, contactController);
            }
        }
    }

    private class SetGroupInfoTask extends GroupTask {
        private final Chat mChat;
        private final byte[] mImage;
        private final String mGroupName;
        private final OnSetGroupInfoListener mSetGroupInfoListener;

        SetGroupInfoTask(final SimsMeApplication application,
                         final Chat chat,
                         final byte[] image,
                         final String groupName,
                         final OnSetGroupInfoListener onSetGroupInfoListener) {
            super(application);
            mChat = chat;
            if (image != null) {
                mImage = image.clone();
            } else {
                mImage = null;
            }
            mGroupName = groupName;
            mSetGroupInfoListener = onSetGroupInfoListener;
        }

        @Override
        protected Void doInBackground(final Void... params) {
            try {
                final SecretKey key = mChat.getChatAESKey();
                IvParameterSpec iv = mChat.getChatInfoIV();

                //bei alten Gruppen ist der null sein
                if (iv == null) {
                    iv = SecurityUtil.generateIV();
                    //TODO muss an Sever uebertragen werden
                }

                final JsonObject dataObject = new JsonObject();
                final String groupName;
                if (!StringUtil.isNullOrEmpty(mGroupName)) {
                    groupName = mGroupName;
                } else {
                    groupName = mChat.getTitle();
                }

                dataObject.addProperty("GroupName", groupName);

                final byte[] img;
                if (mImage != null) {
                    img = mImage;
                } else {
                    img = mApp.getImageController().loadProfileImageRaw(mChat.getChatGuid());
                }

                final String data = Base64.encodeToString(img, Base64.NO_WRAP);
                dataObject.addProperty("GroupImage", data);

                final String dataObjectAsJson = dataObject.toString();
                final byte[] encryptedMessage = SecurityUtil.encryptMessageWithAES(dataObjectAsJson.getBytes(StandardCharsets.UTF_8),
                        key, iv);

                final String encryptedData = Base64.encodeToString(encryptedMessage, Base64.NO_WRAP);

                final byte[] ivBytes = iv.getIV();
                final String ivAsBase64 = Base64.encodeToString(ivBytes, Base64.NO_WRAP);

                BackendService.withSyncConnection(mApp)
                        .setGroupInfo(mChat.getChatGuid(), encryptedData, ivAsBase64, new IBackendService.OnBackendResponseListener() {
                            @Override
                            public void onBackendResponse(final BackendResponse response) {
                                if (response.isError) {
                                    mHasError = true;
                                    mErrorDetail = mApp.getString(R.string.create_group_error_server_communication);
                                    if (!StringUtil.isNullOrEmpty(response.errorMessage)) {
                                        mErrorDetail = mErrorDetail + "\n" + response.errorMessage;
                                    }
                                    return;
                                }

                                if (response.jsonObject == null || !response.jsonObject.has("GroupInfoResult")) {
                                    mHasError = true;
                                    mErrorDetail = mApp.getString(R.string.create_group_error_server_communication);
                                    return;
                                }

                                try {
                                    if (!StringUtil.isNullOrEmpty(mGroupName)) {
                                        mChat.setTitle(mGroupName);
                                    }

                                    mChat.setChatInfoIVAsBase64(ivAsBase64);
                                    insertOrUpdateChat(mChat);

                                    if (mImage != null) {
                                        mApp.getImageController().saveProfileImageRaw(mChat.getChatGuid(), mImage);
                                    }
                                } catch (final LocalizedException e) {
                                    LogUtil.w(TAG, e.getMessage(), e);
                                    mHasError = true;
                                }

                                final JsonObject groupResultInfo = response.jsonObject.getAsJsonObject("GroupInfoResult");

                                if (groupResultInfo.has("not-send")) {
                                    final JsonArray notSendArray = groupResultInfo.getAsJsonArray("not-send");

                                    try {
                                        if (!StringUtil.isNullOrEmpty(mGroupName)) {
                                            privateInternalMessageController.broadcastGroupNameChange(mChat, notSendArray, mGroupName);
                                        }

                                        if (mImage != null) {
                                            privateInternalMessageController.broadcastGroupImageChange(mChat, notSendArray, mImage);
                                        }
                                    } catch (final LocalizedException e) {
                                        LogUtil.w(TAG, e.getMessage(), e);
                                    }
                                }
                            }
                        });
            } catch (LocalizedException e) {
                LogUtil.w(TAG, e.getMessage(), e);
                mHasError = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(final Void aVoid) {
            if (mHasError) {
                if (mSetGroupInfoListener != null) {
                    mSetGroupInfoListener.onSetGroupInfoFailed(mErrorDetail);
                }
            } else {
                mApp.getChatOverviewController().chatChanged(null, mChat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_REFRESH_CHAT);
                if (mSetGroupInfoListener != null) {
                    mSetGroupInfoListener.onSetGroupInfoSuccess(mChat);
                }
            }
        }
    }

    private class UpdateGroupMembersTask extends GroupTask {
        private final Chat mChat;
        private final List<Contact> mAddedMembers;
        private final List<Contact> mRemovedMembers;
        private final List<String> mAddedAdmins;
        private final List<String> mRemovedAdmins;
        private final byte[] mImage;
        private final String mGroupName;
        private final OnUpdateGroupMembersListener mUpdateGroupMembersListener;

        UpdateGroupMembersTask(final SimsMeApplication application,
                               final Chat chat,
                               final List<Contact> addedMembers,
                               final List<Contact> removedMembers,
                               final List<String> addedAdmins,
                               final List<String> removedAdmins,
                               final byte[] image,
                               final String groupName,
                               final OnUpdateGroupMembersListener onUpdateGroupMembersListener) {
            super(application);

            mChat = chat;
            mAddedMembers = addedMembers;
            mRemovedMembers = removedMembers;
            mAddedAdmins = addedAdmins;
            mRemovedAdmins = removedAdmins;
            mImage = image;
            mGroupName = groupName;
            mUpdateGroupMembersListener = onUpdateGroupMembersListener;
        }

        @Override
        protected Void doInBackground(final Void... params) {
            final String newMembers = getStringFromList(mAddedMembers);
            final String removedMembers = getStringFromList(mRemovedMembers);
            final String newAdmins;
            final String removedAdmins;

            if (mAddedAdmins != null && mAddedAdmins.size() > 0) {
                newAdmins = StringUtil.getStringFromList(",", mAddedAdmins);
            } else {
                newAdmins = null;
            }

            if (mRemovedAdmins != null && mRemovedAdmins.size() > 0) {
                removedAdmins = StringUtil.getStringFromList(",", mRemovedAdmins);
            } else {
                removedAdmins = null;
            }

            if (newMembers == null && removedAdmins == null && newAdmins == null && removedMembers == null) {
                return null;
            }

            try {
                if (StringUtil.isNullOrEmpty(mChat.getChatInfoIVAsBase64())) {
                    //alte Gruppe vor 1.8 die keinen iv hat
                    //iv anlegen und GruppenInfos aktualisieren
                    final SetGroupInfoTask setGroupInfoTask = new SetGroupInfoTask(mApp, mChat, null, null, null);
                    setGroupInfoTask.doInBackground();
                    if (setGroupInfoTask.mHasError) {
                        mHasError = true;
                        mErrorDetail = setGroupInfoTask.mErrorDetail;
                        return null;
                    }
                }

                String jsonInviteMsgs = null;
                if (mAddedMembers != null && mAddedMembers.size() > 0) {
                    final ContactController contactController = mApp.getContactController();
                    final AccountController accountController = mApp.getAccountController();
                    final KeyController keyController = mApp.getKeyController();
                    final boolean sendProfileName = mApp.getPreferencesController().getSendProfileName();
                    final ArrayList<GroupInvMessageModel> groupInvites = new ArrayList<>();
                    final Account account = accountController.getAccount();
                    final KeyPair keyPair = keyController.getUserKeyPair();
                    final SecretKey aesKey = mChat.getChatAESKey();
                    final IvParameterSpec iv = mChat.getChatInfoIV();

                    List<Contact> loadedContacts = loadPublicKeys(null, mAddedMembers, contactController);

                    if (loadedContacts != null) {
                        for (final Contact contact : loadedContacts) {
                            try {
                                final String title = mChat.getTitle();

                                final GroupInvMessageModel groupInviteMessageModel = createGroupInvMessage(contactController,
                                        mChat.getChatGuid(), title, mChat.getRoomType(), account, contact, keyPair,
                                        aesKey, iv, sendProfileName);

                                if (groupInviteMessageModel != null) {
                                    groupInvites.add(groupInviteMessageModel);
                                }
                            } catch (final LocalizedException e) {
                                LogUtil.w(TAG, e.getMessage(), e);
                                mHasError = true;
                            }
                        }

                        jsonInviteMsgs = mGson.toJson(groupInvites.toArray(new GroupInvMessageModel[0]), GroupInvMessageModel[].class);
                    }
                }

                String encData = null;
                if (!StringUtil.isNullOrEmpty(mGroupName) || mImage != null) {
                    encData = getEncryptedGroupData();
                }

                BackendService.withSyncConnection(mApp)
                        .updateGroup(mChat.getChatGuid(), jsonInviteMsgs, removedMembers,
                                newAdmins, removedAdmins, encData, mChat.getChatInfoIVAsBase64(),
                                getEncodedAccountNameForSend(), new IBackendService.OnBackendResponseListener() {
                                    @Override
                                    public void onBackendResponse(final BackendResponse response) {
                                        if (response.isError) {
                                            mHasError = true;
                                            mErrorDetail = mApp.getString(R.string.create_group_error_server_communication);
                                            if (!StringUtil.isNullOrEmpty(response.errorMessage)) {
                                                mErrorDetail = mErrorDetail + "\n" + response.errorMessage;
                                            }
                                            return;
                                        }

                                        if (response.jsonObject == null) {
                                            mHasError = true;
                                            mErrorDetail = mApp.getString(R.string.create_group_error_server_communication);
                                            return;
                                        }

                                        if (response.jsonObject.has("ChatRoom")) {
                                            try {
                                                updateChatWithResponse(null, mChat, response, true, false, false);
                                            } catch (final LocalizedException e) {
                                                LogUtil.w(TAG, e.getMessage(), e);
                                                mHasError = true;
                                            }
                                        }

                                        final JsonArray addMembersNotSend = getNotSendArray(response.jsonObject, "AddMembersResult");

                                        if (addMembersNotSend != null && addMembersNotSend.size() > 0) {
                                            try {
                                                byte[] img = mImage;
                                                if (img == null) {
                                                    img = mApp.getImageController().loadProfileImageRaw(mChat.getChatGuid());
                                                }
                                                privateInternalMessageController.broadcastGroupImageChange(mChat, addMembersNotSend, img);
                                            } catch (final LocalizedException e) {
                                                LogUtil.w(TAG, e.getMessage(), e);
                                            }
                                        }

                                        final JsonArray removeMembersNotSend = getNotSendArray(response.jsonObject, "RemoveMembersResult");
                                        if (removeMembersNotSend != null && removeMembersNotSend.size() > 0 && !StringUtil.isNullOrEmpty(removedMembers)) {
                                            try {
                                                final String[] removedGuids = removedMembers.split(",");
                                                privateInternalMessageController.broadcastRemoveGroupMember(mChat, removeMembersNotSend, removedGuids);
                                            } catch (final LocalizedException e) {
                                                LogUtil.w(TAG, e.getMessage(), e);
                                            }
                                        }

                                        final JsonArray groupInfoNotSend = getNotSendArray(response.jsonObject, "GroupInfoResult");
                                        if (groupInfoNotSend != null && groupInfoNotSend.size() > 0) {
                                            try {
                                                final JsonArray notSendArray;
                                                if (!StringUtil.isNullOrEmpty(newMembers)) {
                                                    notSendArray = new JsonArray();
                                                    for (int i = 0; i < groupInfoNotSend.size(); i++) {
                                                        final String notSendGuid = groupInfoNotSend.get(i).getAsString();

                                                        if (!newMembers.contains(notSendGuid)) {
                                                            notSendArray.add(new JsonPrimitive(notSendGuid));
                                                        }
                                                    }
                                                } else {
                                                    notSendArray = groupInfoNotSend;
                                                }

                                                if (!StringUtil.isNullOrEmpty(mGroupName)) {
                                                    privateInternalMessageController.broadcastGroupNameChange(mChat, notSendArray, mGroupName);
                                                }

                                                if (mImage != null) {
                                                    privateInternalMessageController.broadcastGroupImageChange(mChat, notSendArray, mImage);
                                                }
                                            } catch (final LocalizedException e) {
                                                LogUtil.w(TAG, e.getMessage(), e);
                                            }
                                        }
                                    }
                                });
            } catch (final LocalizedException e) {
                LogUtil.w(TAG, e.getMessage(), e);
                mHasError = true;
            }

            return null;
        }

        @Override
        protected void onPostExecute(final Void aVoid) {
            if (mHasError) {
                if (mUpdateGroupMembersListener != null) {
                    mUpdateGroupMembersListener.onUpdateGroupMembersFailed(mErrorDetail);
                }
            } else {
                mApp.getChatOverviewController().chatChanged(null, mChat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_REFRESH_CHAT);

                if (mUpdateGroupMembersListener != null) {
                    mUpdateGroupMembersListener.onUpdateGroupMembersSuccess(mChat);
                }
            }
        }

        private String getEncryptedGroupData()
                throws LocalizedException {
            final SecretKey key = mChat.getChatAESKey();
            IvParameterSpec iv = mChat.getChatInfoIV();

            //bei alten Gruppen ist der null sein
            if (iv == null) {
                iv = SecurityUtil.generateIV();
                mChat.setChatInfoIV(iv);

                insertOrUpdateChat(mChat);
            }

            final JsonObject dataObject = new JsonObject();
            final String groupName;
            if (!StringUtil.isNullOrEmpty(mGroupName)) {
                groupName = mGroupName;
            } else {
                groupName = mChat.getTitle();
            }

            dataObject.addProperty("GroupName", groupName);

            final byte[] img;
            if (mImage != null) {
                img = mImage;
            } else {
                img = mApp.getImageController().loadProfileImageRaw(mChat.getChatGuid());
            }

            final String data = Base64.encodeToString(img, Base64.NO_WRAP);
            dataObject.addProperty("GroupImage", data);

            final String dataObjectAsJson = dataObject.toString();

            final byte[] encryptedMessage = SecurityUtil.encryptMessageWithAES(dataObjectAsJson.getBytes(StandardCharsets.UTF_8),
                    key, iv);

            return Base64.encodeToString(encryptedMessage, Base64.NO_WRAP);
        }
    }

    private class GetGroupMemberInfoTask extends GroupTask {
        private final String mChatGuid;

        GetGroupMemberInfoTask(final SimsMeApplication application,
                               final String chatGuid) {
            super(application);
            mChatGuid = chatGuid;
        }

        @Override
        protected Void doInBackground(final Void... params) {
            try {
                BackendService.withSyncConnection(mApp)
                        .getRoomMemberInfo(mChatGuid, new IBackendService.OnBackendResponseListener() {
                            @Override
                            public void onBackendResponse(final BackendResponse response) {
                                if (response.isError) {
                                    mHasError = true;
                                } else {
                                    try {
                                        updateChatWithResponse(mChatGuid, null, response, false, false, true);
                                    } catch (final LocalizedException e) {
                                        LogUtil.w(TAG, e.getMessage(), e);
                                        mHasError = true;
                                    }
                                }
                            }
                        });
            } catch (final Exception e) {
                LogUtil.e(TAG, e.getMessage(), e);
                mHasError = true;
            }

            return null;
        }

        @Override
        protected void onPostExecute(final Void aVoid) {
            if (!mHasError) {
                mApp.getChatOverviewController().chatChanged(null, mChatGuid, null, ChatOverviewController.CHAT_CHANGED_REFRESH_CHAT);
            }
        }
    }

    private class GetAndUpdateRoomInfoTask extends GroupTask {
        private final String mChatGuid;

        GetAndUpdateRoomInfoTask(final SimsMeApplication application,
                                 final String chatGuid) {
            super(application);

            mChatGuid = chatGuid;
        }

        @Override
        protected Void doInBackground(final Void... params) {
            try {
                BackendService.withSyncConnection(mApp)
                        .getRoom(mChatGuid, new IBackendService.OnBackendResponseListener() {
                            @Override
                            public void onBackendResponse(BackendResponse response) {
                                if (response.isError) {
                                    mHasError = true;
                                } else {
                                    try {
                                        updateChatWithResponse(mChatGuid, null, response, false, false, true);
                                    } catch (final LocalizedException e) {
                                        LogUtil.w(TAG, e.getMessage(), e);
                                        mHasError = true;
                                    }
                                }
                            }
                        }, true);
            } catch (final Exception e) {
                LogUtil.e(TAG, e.getMessage(), e);
                mHasError = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(final Void aVoid) {
            if (!mHasError) {
                if (mHasSaveImage) {
                    mApp.getImageController().updateProfileImageInCache(mChatGuid);
                }

                mApp.getChatOverviewController().chatChanged(null, mChatGuid, null, ChatOverviewController.CHAT_CHANGED_REFRESH_CHAT);

                for (final GroupInfoChangedListener listener : mGroupInfoChangedListeners) {
                    listener.onGroupInfoChanged();
                }
            }
        }
    }

    private class CreateGroupTask extends GroupTask {
        private final String mChatRoomName;
        private final byte[] mChatRoomImage;
        private final String mChatRoomType;
        private final List<Contact> mMembers;
        private final List<String> mAdmins;
        private final List<String> mWriters;
        private final OnBuildChatRoomListener mBuildChatRoomListener;
        private String mChatGuid;

        CreateGroupTask(final SimsMeApplication application,
                        final String chatRoomName,
                        final byte[] chatRoomImage,
                        final String chatRoomType,
                        final List<Contact> members,
                        final List<String> admins,
                        final List<String> writers,
                        final OnBuildChatRoomListener buildChatRoomListener) {
            super(application);
            mChatRoomName = chatRoomName;
            mChatRoomImage = chatRoomImage;
            mChatRoomType = chatRoomType;
            mMembers = members;
            mAdmins = admins;
            mWriters = writers;
            mBuildChatRoomListener = buildChatRoomListener;
        }

        @Override
        protected Void doInBackground(final Void... params) {
            try {
                final SecretKey aesKey = SecurityUtil.generateAESKey();
                final IvParameterSpec iv = SecurityUtil.generateIV();
                final byte[] groupImageBytes;

                if (mChatRoomImage != null) {
                    final Bitmap bitmap = ImageUtil.decodeByteArray(mChatRoomImage);
                    final Bitmap scaledBitmap = ImageUtil.getScaledImage(mApp.getResources(), bitmap, ImageUtil.SIZE_PROFILE_BIG);
                    groupImageBytes = ImageUtil.compress(scaledBitmap, 50);
                } else {
                    groupImageBytes = null;
                }

                final ChatRoomModel chatRoomModel = buildChatRoom(mChatRoomName, mChatRoomType, groupImageBytes, aesKey, iv);
                final String groupInvMessagesAsJson = createGroupInvMessagesAsJson(chatRoomModel, aesKey, iv);

                final List<String> adminGuidList = new ArrayList<>();

                final Chat chat = buildChat(chatRoomModel.guid, mChatRoomName, aesKey, iv);

                if (mAdmins != null && mAdmins.size() > 0) {
                    boolean foundOwnGuid = false;
                    for (final String guid : mAdmins) {
                        if (StringUtil.isEqual(chatRoomModel.owner, guid)) {
                            foundOwnGuid = true;
                        }
                        adminGuidList.add(guid);
                    }

                    if (!foundOwnGuid) {
                        adminGuidList.add(chatRoomModel.owner);
                    }
                }

                if(!StringUtil.isNullOrEmpty(mChatRoomType)) {
                    chat.setRoomType(mChatRoomType);
                }

                final String chatRoomAsJson = mGson.toJson(chatRoomModel);
                LogUtil.d(TAG, "Creating new chatRoom: " + chatRoomAsJson);
                final String adminGuids = adminGuidList.size() > 0 ? StringUtil.getStringFromList(",", adminGuidList) : null;

                BackendService.withSyncConnection(mApp)
                        .createGroup(chatRoomAsJson, groupInvMessagesAsJson, adminGuids,
                                getEncodedAccountNameForSend(), new IBackendService.OnBackendResponseListener() {
                                    @Override
                                    public void onBackendResponse(final BackendResponse response) {
                                        try {
                                            if (response.isError) {
                                                mHasError = true;
                                                mErrorDetail = mApp.getString(R.string.create_group_error_server_communication);
                                                if (!StringUtil.isNullOrEmpty(response.errorMessage)) {
                                                    mErrorDetail = mErrorDetail + "\n" + response.errorMessage;
                                                }
                                                LogUtil.w(TAG, "Creating of new chatRoom failed with: " + mErrorDetail);
                                                return;
                                            }

                                            if (response.jsonObject == null || !response.jsonObject.has("GroupInfoResult")) {
                                                mHasError = true;
                                                mErrorDetail = mApp.getString(R.string.create_group_error_server_communication);
                                                LogUtil.w(TAG, "Creating of new chatRoom failed with: " + mErrorDetail);
                                                return;
                                            }

                                            final JsonObject groupResultInfo = response.jsonObject.getAsJsonObject("GroupInfoResult");
                                            final JsonArray members = chat.getMembers();

                                            if (mMembers != null && mMembers.size() > 0) {
                                                for (final Contact member : mMembers) {
                                                    members.add(new JsonPrimitive(member.getAccountGuid()));
                                                }

                                                chat.setMembers(members);
                                            }

                                            if (adminGuidList.size() > 0) {
                                                final JsonArray adminsArray = chat.getAdmins();
                                                for (final String guid : adminGuidList) {
                                                    adminsArray.add(new JsonPrimitive(guid));
                                                }
                                                chat.setAdmins(adminsArray);
                                            }

                                            if (groupImageBytes != null) {
                                                mApp.getImageController().saveProfileImageRaw(chat.getChatGuid(), groupImageBytes);
                                            }

                                            final Date now = new Date();
                                            chat.setLastChatModifiedDate(now.getTime());

                                            insertOrUpdateChat(chat);

                                            if (groupResultInfo.has("not-send") && groupImageBytes != null) {
                                                final JsonArray notSendArray = groupResultInfo.getAsJsonArray("not-send");
                                                privateInternalMessageController.broadcastGroupImageChange(chat, notSendArray, groupImageBytes);
                                            }

                                            mChatGuid = chat.getChatGuid();

                                            updateGroupMember();

                                            LogUtil.d(TAG, "Creating of new chatRoom successfully finished. New chatGuid: " + mChatGuid);

                                        } catch (final LocalizedException e) {
                                            LogUtil.w(TAG, e.getMessage(), e);
                                            mHasError = true;
                                        }
                                    }
                                });
            } catch (final LocalizedException | UnsupportedEncodingException e) {
                LogUtil.e(TAG, e.getMessage(), e);
                mHasError = true;
            }

            return null;
        }

        private void updateGroupMember() throws LocalizedException {
            BackendService.withSyncConnection(mApp)
                    .getRoomMemberInfo(mChatGuid, new IBackendService.OnBackendResponseListener() {
                        @Override
                        public void onBackendResponse(BackendResponse response) {
                            if (!response.isError) {
                                try {
                                    updateChatWithResponse(mChatGuid, null, response, false, false, false);
                                } catch (final LocalizedException e) {
                                    LogUtil.w(TAG, e.getMessage(), e);
                                    //kein Crash, anlegen der Gruppe hat ja vorher geklappt
                                }
                            }
                        }
                    });
        }

        @Override
        protected void onPostExecute(final Void aVoid) {
            if (mHasError && mBuildChatRoomListener != null) {
                mBuildChatRoomListener.onBuildChatRoomFail(mErrorDetail);
            } else {
                mApp.getChatOverviewController().chatChanged(null, mChatGuid, null, ChatOverviewController.CHAT_CHANGED_NEW_CHAT);

                if (mBuildChatRoomListener != null) {
                    mBuildChatRoomListener.onBuildChatRoomSuccess(mChatGuid, mErrorDetail);
                }
            }
        }

        private String createGroupInvMessagesAsJson(final ChatRoomModel chatRoomModel,
                                                    final SecretKey aesKey,
                                                    final IvParameterSpec iv)
                throws LocalizedException, UnsupportedEncodingException {
            if (chatRoomModel == null || mMembers == null || mMembers.size() < 1) {
                return null;
            }

            final AccountController accountController = mApp.getAccountController();
            final ContactController contactController = mApp.getContactController();
            final KeyController keyController = mApp.getKeyController();
            final boolean sendProfileName = mApp.getPreferencesController().getSendProfileName();
            final ArrayList<GroupInvMessageModel> groupInvites = new ArrayList<>();

            for (final Contact contact : mMembers) {
                if (contact == null) {
                    continue;
                }

                final GroupInvMessageModel groupInviteMessageModel = createGroupInvMessage(contactController,
                        chatRoomModel.guid, mChatRoomName, chatRoomModel.roomType, accountController.getAccount(),
                        contact, keyController.getUserKeyPair(), aesKey, iv, sendProfileName);

                if (groupInviteMessageModel != null) {
                    groupInvites.add(groupInviteMessageModel);
                }
            }
            if (RuntimeConfig.supportMultiDevice()) {
                final GroupInvMessageModel groupInviteMessageModel = createOwnGroupInvMessage(contactController,
                        chatRoomModel.guid, mChatRoomName, chatRoomModel.roomType, accountController.getAccount(),
                        keyController.getUserKeyPair(), aesKey, iv, sendProfileName);

                if (groupInviteMessageModel != null) {
                    groupInvites.add(groupInviteMessageModel);
                }
            }

            return mGson.toJson(groupInvites.toArray(new GroupInvMessageModel[0]), GroupInvMessageModel[].class);
        }

        private ChatRoomModel buildChatRoom(final String groupName, final String roomType, final byte[] groupImage, final SecretKey aesKey, final IvParameterSpec iv)
                throws LocalizedException {
            final AccountController accountController = mApp.getAccountController();
            final ChatRoomModel chatRoom = new ChatRoomModel();

            chatRoom.owner = accountController.getAccount().getAccountGuid();
            chatRoom.guid = GuidUtil.generateRoomGuid();

            boolean hasData = false;
            final JsonObject dataObject = new JsonObject();
            if (!StringUtil.isNullOrEmpty(groupName)) {
                dataObject.addProperty(DataContainer.GROUP_NAME, groupName);
                hasData = true;
            }

            if(!StringUtil.isNullOrEmpty(roomType)) {
                chatRoom.roomType = roomType;
                dataObject.addProperty(DataContainer.ROOM_TYPE, roomType);
                dataObject.addProperty(DataContainer.GROUP_TYPE, roomType);
                hasData = true;
            }

            if (groupImage != null) {
                final String data = Base64.encodeToString(groupImage, Base64.NO_WRAP);

                if (data != null) {
                    dataObject.addProperty(DataContainer.GROUP_IMAGE, data);
                    hasData = true;
                }
            }

            if (hasData) {
                final String dataObjectAsJson = dataObject.toString();
                final byte[] encryptedMessage = SecurityUtil.encryptMessageWithAES(dataObjectAsJson.getBytes(StandardCharsets.UTF_8),
                        aesKey, iv);

                final String data = Base64.encodeToString(encryptedMessage, Base64.NO_WRAP);

                if (data != null) {
                    chatRoom.data = data;
                    final byte[] ivBytes = iv.getIV();
                    chatRoom.keyIv = Base64.encodeToString(ivBytes, Base64.NO_WRAP);
                }
            }

            return chatRoom;
        }

        private Chat buildChat(final String guid,
                               final String title,
                               final SecretKey aesKey,
                               final IvParameterSpec iv)
                throws LocalizedException {
            final AccountController accountController = mApp.getAccountController();
            Chat chat = getChatByGuid(guid);

            if (chat == null) {
                chat = new Chat();
            }

            chat.setType(Chat.TYPE_GROUP_CHAT);
            chat.setChatGuid(guid);
            chat.setTitle(title);
            chat.setOwner(accountController.getAccount().getAccountGuid());

            final JsonArray members = new JsonArray();

            members.add(new JsonPrimitive(accountController.getAccount().getAccountGuid()));
            chat.setMembers(members);

            chat.setChatAESKey(aesKey);
            chat.setChatInfoIV(iv);

            return chat;
        }
    }

    private abstract class GroupTask extends AsyncTask<Void, Void, Void> {
        final SimsMeApplication mApp;
        boolean mHasError;
        String mErrorDetail;
        boolean mHasSaveImage;

        GroupTask(final SimsMeApplication application) {
            mApp = application;
        }

        boolean updateChatWithResponse(final String chatGuid,
                                       final Chat groupChat,
                                       final BackendResponse response,
                                       final boolean setType,
                                       final boolean setModifiedDate,
                                       final boolean setChatEnabled)
                throws LocalizedException {

            final Chat chat;
            final String guid;

            if (groupChat != null) {
                chat = groupChat;
                guid = chat.getChatGuid();
            } else {
                chat = getChatByGuid(chatGuid);
                guid = chatGuid;
            }

            if (chat == null) {
                return false;
            }

            final ChatRoomModel chatRoomModel = mGson.fromJson(response.jsonObject, ChatRoomModel.class);

            if (!StringUtil.isNullOrEmpty(chatRoomModel.keyIv)) {
                chat.setChatInfoIVAsBase64(chatRoomModel.keyIv);
            }


            String roomType = chatRoomModel.roomType;
            if(StringUtil.isNullOrEmpty(roomType)) {
                roomType = Chat.ROOM_TYPE_STD;
            }

            // First of all look if we have a crypt data container attached. If yes, process its contents.
            final IvParameterSpec iv = chat.getChatInfoIV();
            if (iv != null && !StringUtil.isNullOrEmpty(chatRoomModel.data)) {
                final String jsonData = SecurityUtil.decryptBase64StringWithAES(chatRoomModel.data, chat.getChatAESKey(), iv);
                final JsonObject jsonObject = JsonUtil.getJsonObjectFromString(jsonData);

                if (jsonObject != null) {
                    final String chatName = JsonUtil.stringFromJO(DataContainer.GROUP_NAME, jsonObject);
                    if (!StringUtil.isNullOrEmpty(chatName)) {
                        chat.setTitle(chatName);
                    }

                    String tempRoomType = JsonUtil.stringFromJO(DataContainer.ROOM_TYPE, jsonObject);
                    if (!StringUtil.isNullOrEmpty(tempRoomType)) {
                        roomType = tempRoomType;
                    } else {
                        tempRoomType = JsonUtil.stringFromJO(DataContainer.GROUP_TYPE, jsonObject);
                        if (!StringUtil.isNullOrEmpty(tempRoomType)) {
                            roomType = tempRoomType;
                        }
                    }

                    final String groupImageAsBase64 = JsonUtil.stringFromJO(DataContainer.GROUP_IMAGE, jsonObject);

                    if (!StringUtil.isNullOrEmpty(groupImageAsBase64)) {
                        final byte[] image = Base64.decode(groupImageAsBase64, Base64.NO_WRAP);

                        if (image != null) {
                            mApp.getImageController().saveProfileImageRaw(guid, image);
                            mHasSaveImage = true;
                        }
                    }
                }
            }

            chat.setRoomType(roomType);

            final String accountGuid = mApp.getAccountController().getAccount().getAccountGuid();

            switch(roomType) {
                case Chat.ROOM_TYPE_RESTRICTED: {
                    boolean readOnly = true;
                    if (chatRoomModel.writers != null) {
                        chat.setWriters(chatRoomModel.writers);

                        for (final String writer : chatRoomModel.writers) {
                            if (StringUtil.isEqual(accountGuid, writer)) {
                                readOnly = false;
                                break;
                            }
                        }
                    }
                    chat.setIsReadOnly(readOnly);
                    break;
                }
                case Chat.ROOM_TYPE_ANNOUNCEMENT: {
                    if (chatRoomModel.member == null) {
                        return false;
                    }
                    chat.setMembers(mGson.toJsonTree(chatRoomModel.member).getAsJsonArray());

                    boolean readOnly = true;
                    if (chatRoomModel.admins != null) {
                        chat.setAdmins(mGson.toJsonTree(chatRoomModel.admins).getAsJsonArray());
                        for (final String admin : chatRoomModel.admins) {
                            if (StringUtil.isEqual(accountGuid, admin)) {
                                readOnly = false;
                                break;
                            }
                        }
                    }
                    if (chatRoomModel.owner != null) {
                        chat.setOwner(chatRoomModel.owner);
                        if (StringUtil.isEqual(accountGuid, chatRoomModel.owner)) {
                            readOnly = false;
                        }
                    }
                    chat.setIsReadOnly(readOnly);
                    break;
                }
                default:
                    if (chatRoomModel.member == null) {
                        return false;
                    }
                    chat.setMembers(mGson.toJsonTree(chatRoomModel.member).getAsJsonArray());

                    if (chatRoomModel.admins != null) {
                        chat.setAdmins(mGson.toJsonTree(chatRoomModel.admins).getAsJsonArray());
                    }

                    if (chatRoomModel.owner != null) {
                        chat.setOwner(chatRoomModel.owner);
                    }
                    chat.setIsReadOnly(chatRoomModel.isReadonly);
            }

            chat.setSilentTill(DateUtil.utcWithoutMillisStringToMillis(chatRoomModel.pushSilentTill));

            if (setType) {
                chat.setType(Chat.TYPE_GROUP_CHAT);
            }

            if (setModifiedDate) {
                final Date now = new Date();
                chat.setLastChatModifiedDate(now.getTime());
            }

            if (setChatEnabled) {
                chat.setIsRemoved(false);
            }

            insertOrUpdateChat(chat);

            return true;
        }

        GroupInvMessageModel createGroupInvMessage(final ContactController contactController,
                                                   final String groupGuid,
                                                   final String groupName,
                                                   final String roomType,
                                                   final Account ownAccount,
                                                   final Contact contact,
                                                   final KeyPair ownKeyPair,
                                                   final SecretKey aesKey,
                                                   final IvParameterSpec iv,
                                                   final boolean sendProfileName)
                throws LocalizedException {
            final MessageModelBuilder messageModelBuilder = MessageModelBuilder.getInstance(contactController);

            final GroupInvMessageModel groupInviteMessageModel = messageModelBuilder.buildGroupInviteMessage(groupGuid,
                    groupName,
                    roomType,
                    null,
                    ownAccount,
                    contact.getAccountGuid(),
                    contact.getPublicKey(),
                    ownKeyPair,
                    aesKey,
                    iv,
                    null);

            if (groupInviteMessageModel != null) {
                if (sendProfileName && !StringUtil.isNullOrEmpty(ownAccount.getName())) {
                    groupInviteMessageModel.from.nickname = Base64.encodeToString(ownAccount.getName().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                }
            }

            return groupInviteMessageModel;
        }

        GroupInvMessageModel createOwnGroupInvMessage(final ContactController contactController,
                                                      final String groupGuid,
                                                      final String groupName,
                                                      final String roomType,
                                                      final Account ownAccount,
                                                      final KeyPair ownKeyPair,
                                                      final SecretKey aesKey,
                                                      final IvParameterSpec iv,
                                                      final boolean sendProfileName)
                throws LocalizedException {
            final MessageModelBuilder messageModelBuilder = MessageModelBuilder.getInstance(contactController);

            final GroupInvMessageModel groupInviteMessageModel = messageModelBuilder.buildGroupInviteMessage(groupGuid,
                    groupName,
                    roomType,
                    null,
                    ownAccount,
                    ownAccount.getAccountGuid(),
                    ownAccount.getPublicKey(),
                    ownKeyPair,
                    aesKey,
                    iv,
                    null);

            if (groupInviteMessageModel != null) {
                if (sendProfileName && !StringUtil.isNullOrEmpty(ownAccount.getName())) {
                    groupInviteMessageModel.from.nickname = Base64.encodeToString(ownAccount.getName().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                }
            }

            return groupInviteMessageModel;
        }

        List<Contact> loadPublicKeys(final List<String> contactGuids, List<Contact> contactList, final ContactController contactController)
                throws LocalizedException {
            if ((contactList == null || contactList.size() < 1) && (contactGuids == null || contactGuids.size() < 1)) {
                return null;
            }
            final List<Contact> contacts = new ArrayList<>();

            final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
                @Override
                public void onBackendResponse(final BackendResponse response) {
                    if (!response.isError) {
                        if ((response.jsonArray != null) && (response.jsonArray.size() > 0)) {
                            for (int i = 0; i < response.jsonArray.size(); i++) {
                                final JsonElement jsonElement = response.jsonArray.get(i);

                                if (!jsonElement.isJsonObject()) {
                                    continue;
                                }
                                final JsonObject jsonObject = jsonElement.getAsJsonObject();

                                if (!jsonObject.has("Account")) {
                                    continue;
                                }

                                final JsonElement jsonAccountElement = jsonObject.get("Account");

                                if (!jsonElement.isJsonObject()) {
                                    continue;
                                }

                                final JsonObject jsonAccountObject = jsonAccountElement.getAsJsonObject();

                                if (jsonAccountObject.has("guid") &&
                                        jsonAccountObject.has("publicKey")) {
                                    try {
                                        final String guid = jsonAccountObject.get("guid").getAsString();
                                        final String publicKey = jsonAccountObject.get("publicKey")
                                                .getAsString();

                                        final Contact contact = contactController.getContactByGuid(guid);

                                        if (contact != null) {
                                            contact.setPublicKey(publicKey);

                                            if (jsonAccountObject.has(JsonConstants.ACCOUNT_ID)) {
                                                contact.setSimsmeId(jsonAccountObject.get(JsonConstants.ACCOUNT_ID).getAsString());
                                            }

                                            contactController.setEncryptedProfileInfosToContact(
                                                    jsonAccountObject, contact);

                                            contactController.insertOrUpdateContact(contact);

                                            contacts.add(contact);
                                        }
                                    } catch (final LocalizedException e) {
                                        LogUtil.w(TAG, e.getMessage(), e);
                                    }
                                }
                            }
                        }
                    }
                }
            };

            final String guids;
            if (contactList != null) {
                guids = getStringFromList(contactList);
            } else {
                guids = StringUtil.getStringFromList(",", contactGuids);
            }

            BackendService.withSyncConnection(mApp)
                    .getAccountInfoBatch(guids, true, false, listener);

            return contacts;
        }

        String getStringFromList(final List<Contact> list) {
            if (list == null) {
                return null;
            }

            final String separator = ",";
            final StringBuilder stringBuilder = new StringBuilder();

            for (final Contact contact : list) {
                final String guid = contact.getAccountGuid();
                if (!StringUtil.isNullOrEmpty(guid)) {
                    if (stringBuilder.length() > 0) {
                        stringBuilder.append(separator);
                    }

                    stringBuilder.append(guid);
                }
            }

            return stringBuilder.toString();
        }
    }
}
