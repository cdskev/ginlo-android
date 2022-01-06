// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller.message;

import android.os.AsyncTask;
import android.util.SparseArray;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import eu.ginlo_apps.ginlo.concurrent.manager.SerialExecutor;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.KeyController;
import eu.ginlo_apps.ginlo.controller.MessageDecryptionController;
import eu.ginlo_apps.ginlo.controller.message.GroupChatController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.controller.message.contracts.MessageControllerListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.BaseMessageModel;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;
import eu.ginlo_apps.ginlo.model.backend.PrivateInternalMessageModel;
import eu.ginlo_apps.ginlo.model.backend.action.Action;
import eu.ginlo_apps.ginlo.model.backend.action.ChangeGroupImageAction;
import eu.ginlo_apps.ginlo.model.backend.action.ChangeGroupNameAction;
import eu.ginlo_apps.ginlo.model.backend.action.ChangeProfileImageAction;
import eu.ginlo_apps.ginlo.model.backend.action.ChangeProfileNameAction;
import eu.ginlo_apps.ginlo.model.backend.action.ChangeStatusAction;
import eu.ginlo_apps.ginlo.model.backend.action.CompanyEncryptInfoAction;
import eu.ginlo_apps.ginlo.model.backend.action.NewGroupMemberAction;
import eu.ginlo_apps.ginlo.model.backend.action.RemoveGroupMemberAction;
import eu.ginlo_apps.ginlo.model.backend.action.RequestConfirmEmailAction;
import eu.ginlo_apps.ginlo.model.backend.action.RequestConfirmPhoneAction;
import eu.ginlo_apps.ginlo.model.backend.serialization.KeyContainerModelSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.PrivateInternalMessageModelActionDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.PrivateInternalMessageModelActionSerializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.PrivateInternalMessageModelSerializer;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.MessageModelBuilder;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.jetbrains.annotations.NotNull;

public class PrivateInternalMessageController
        implements MessageControllerListener {
    private static final SerialExecutor INTERNAL_MSG_SERIAL_EXECUTOR = new SerialExecutor();
    private final Gson gson;
    private final SimsMeApplication mContext;
    private BackendService mMessageBackendService;

    public PrivateInternalMessageController(final SimsMeApplication application) {
        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(ChangeStatusAction.class, new PrivateInternalMessageModelActionSerializer());
        gsonBuilder.registerTypeAdapter(ChangeProfileImageAction.class,
                new PrivateInternalMessageModelActionSerializer());
        gsonBuilder.registerTypeAdapter(ChangeProfileNameAction.class, new PrivateInternalMessageModelActionSerializer());
        gsonBuilder.registerTypeAdapter(ChangeGroupNameAction.class, new PrivateInternalMessageModelActionSerializer());
        gsonBuilder.registerTypeAdapter(ChangeGroupImageAction.class, new PrivateInternalMessageModelActionSerializer());
        gsonBuilder.registerTypeAdapter(NewGroupMemberAction.class, new PrivateInternalMessageModelActionSerializer());
        gsonBuilder.registerTypeAdapter(RemoveGroupMemberAction.class, new PrivateInternalMessageModelActionSerializer());
        gsonBuilder.registerTypeAdapter(Action.class, new PrivateInternalMessageModelActionDeserializer());
        gsonBuilder.registerTypeAdapter(PrivateInternalMessageModel.class, new PrivateInternalMessageModelSerializer());
        gsonBuilder.registerTypeAdapter(KeyContainerModel.class, new KeyContainerModelSerializer());

        this.gson = gsonBuilder.create();
        this.mContext = application;
    }

    public void broadcastStatusTextChange(final List<Contact> contacts,
                                          final String status)
            throws LocalizedException {
        final ChangeStatusAction action = new ChangeStatusAction();

        action.status = status;

        sendBroadcastActionToContacts(contacts, action);
    }

    public void broadcastProfileNameChange(final List<Contact> contacts,
                                           final String name)
            throws LocalizedException {
        final ChangeProfileNameAction action = new ChangeProfileNameAction();

        action.profileName = name;

        sendBroadcastActionToContacts(contacts, action);
    }

    public void broadcastProfileImageChange(final List<Contact> contacts,
                                            final byte[] imageBytes)
            throws LocalizedException {
        final ChangeProfileImageAction action = new ChangeProfileImageAction();

        action.profileImage = imageBytes;

        sendBroadcastActionToContacts(contacts, action);
    }

    void broadcastGroupImageChange(final Chat chat,
                                   final JsonArray chatMembersToSend,
                                   final byte[] imageBytes)
            throws LocalizedException {
        eu.ginlo_apps.ginlo.controller.message.GroupChatController groupChatController = mContext.getGroupChatController();
        final List<Contact> chatMembers = groupChatController.getChatMembers(chatMembersToSend);

        final ChangeGroupImageAction action = new ChangeGroupImageAction();

        action.groupImage = imageBytes;
        action.groupGuid = chat.getChatGuid();

        sendBroadcastActionToContacts(chatMembers, action);
    }

    void broadcastGroupNameChange(final Chat chat,
                                  final JsonArray chatMembersToSend,
                                  final String name)
            throws LocalizedException {
        eu.ginlo_apps.ginlo.controller.message.GroupChatController groupChatController = mContext.getGroupChatController();
        final List<Contact> chatMembers = groupChatController.getChatMembers(chatMembersToSend);

        final ChangeGroupNameAction action = new ChangeGroupNameAction();

        action.groupGuid = chat.getChatGuid();
        action.groupName = name;

        sendBroadcastActionToContacts(chatMembers, action);
    }

    void broadcastAddGroupMember(final String chatGuid,
                                 final JsonArray chatMembersToSend,
                                 final String[] addedMemberGuids,
                                 final String[] addedMemberNames)
            throws LocalizedException {
        eu.ginlo_apps.ginlo.controller.message.GroupChatController groupChatController = mContext.getGroupChatController();
        final List<Contact> chatMembers = groupChatController.getChatMembers(chatMembersToSend);

        final NewGroupMemberAction action = new NewGroupMemberAction();

        action.setGroupGuid(chatGuid);
        action.setGuids(addedMemberGuids);
        action.memberNames = addedMemberNames;

        sendBroadcastActionToContacts(chatMembers, action);
    }

    void broadcastRemoveGroupMember(final Chat chat,
                                    final JsonArray chatMembersToSend,
                                    final String[] removedMemberGuids)
            throws LocalizedException {
        eu.ginlo_apps.ginlo.controller.message.GroupChatController groupChatController = mContext.getGroupChatController();
        final List<Contact> chatMembers = groupChatController.getChatMembers(chatMembersToSend);

        ContactController contactController = mContext.getContactController();
        //Bug 35582 add removed members to tmp list
        for (String guid : removedMemberGuids) {
            Contact contact = contactController.getContactByGuid(guid);
            if (contact != null) {
                chatMembers.add(contact);
            }
        }

        final RemoveGroupMemberAction action = new RemoveGroupMemberAction();

        action.setGroupGuid(chat.getChatGuid());
        action.setGuids(removedMemberGuids);

        sendBroadcastActionToContacts(chatMembers, action);
    }

    @Override
    public void onNewMessages(final int types) {
        if ((types & AppConstants.Message.NewMessagesStates.TYPE_PRIVATE_INTERNAL) == AppConstants.Message.NewMessagesStates.TYPE_PRIVATE_INTERNAL) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    eu.ginlo_apps.ginlo.controller.message.MessageController messageController = mContext.getMessageController();
                    MessageDecryptionController messageDecryptionController = mContext.getMessageDecryptionController();

                    final List<Message> messages = messageController.loadUnreadMessages();

                    if (messages == null || messages.size() < 1) {
                        return null;
                    }

                    DecryptedMessage decryptedMessage = null;

                    for (final Message message : messages) {
                        if (!message.getIsSentMessage()) {
                            try {
                                decryptedMessage = messageDecryptionController.decryptMessage(message, false);
                                if (decryptedMessage == null) {
                                    continue;
                                }

                                final String actionData = decryptedMessage.getContentRaw();
                                Action action = null;
                                if (actionData != null) {
                                    action = gson.fromJson(actionData, Action.class);
                                } else if (decryptedMessage.getDecryptedDataContainer() != null) {
                                    action = gson.fromJson(decryptedMessage.getDecryptedDataContainer(), Action.class);
                                }

                                if (action != null) {
                                    performAction(message.getFrom(), action, decryptedMessage);
                                }
                            } catch (LocalizedException e) {
                                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                            }
                        }
                    }

                    if (messages.size() > 0) {
                        for (Message message : messages) {
                            message.setRead(true);
                        }

                        messageController.updateMessages(messages);
                    }

                    return null;
                }
            }.executeOnExecutor(INTERNAL_MSG_SERIAL_EXECUTOR);
        }
    }

    @Override
    public void onMessagesChanged(@NotNull final SparseArray<List<Message>> messagesListContainer) {
        if (messagesListContainer.get(Message.TYPE_PRIVATE_INTERNAL) != null) {
            final List<Message> messages = messagesListContainer.get(Message.TYPE_PRIVATE_INTERNAL);

            if (messages == null || messages.isEmpty()) {
                return;
            }

            eu.ginlo_apps.ginlo.controller.message.MessageController messageController = mContext.getMessageController();

            for (final Message message : messages) {
                if (message.getIsSentMessage() == null) {
                    continue;
                }

                if (message.getType() != Message.TYPE_PRIVATE_INTERNAL) {
                    continue;
                }

                final boolean isSent = (message.getIsSentMessage())
                        && (message.getDateSendConfirm() != null);
                final boolean isReceivedAndRead = (!message.getIsSentMessage()) && (message.getDateRead() != null)
                        && (message.getDateRead() != 0);

                if (isSent || isReceivedAndRead) {
                    messageController.deleteMessage(message);
                }
            }
        }
    }

    public void retrySend()
            throws LocalizedException {
        final eu.ginlo_apps.ginlo.controller.message.MessageController messageController = mContext.getMessageController();

        final List<Message> messages = messageController.getMessagesWithSendErrorByType();

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                List<BaseMessageModel> messageModels = new ArrayList<>();
                eu.ginlo_apps.ginlo.controller.message.GroupChatController groupChatController = mContext.getGroupChatController();
                MessageDecryptionController messageDecryptionController = mContext.getMessageDecryptionController();
                ContactController contactController = mContext.getContactController();

                for (Message message : messages) {
                    DecryptedMessage decryptedMessage = messageDecryptionController.decryptMessage(message, false);
                    if (decryptedMessage == null) {
                        return null;
                    }

                    messageController.markAsError(message, false);

                    final PrivateInternalMessageModel messageModel = (PrivateInternalMessageModel) MessageModelBuilder
                            .getInstance(contactController).rebuildMessage(message, messageDecryptionController);

                    messageModels.add(messageModel);
                }

                try {
                    sendMessages(messageModels, true);
                } catch (Exception e) {
                    LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                }

                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void sendBroadcastActionToContacts(final List<Contact> contacts,
                                               final Action action)
            throws LocalizedException {
        if ((contacts == null) || (contacts.size() < 1)) {
            return;
        }

        AsyncTask<Void, Void, String> task = new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                final ContactController contactController = mContext.getContactController();

                final List<Contact> contactsList = new ArrayList<>();

                //PublicKeys Checken
                final ContactController.OnLoadPublicKeysListener listener = new ContactController.OnLoadPublicKeysListener() {
                    @Override
                    public void onLoadPublicKeysComplete(Map<String, String> publicKeysMap) {
                        AccountController accountController = mContext.getAccountController();
                        String ownAccountGuid = accountController.getAccount().getAccountGuid();

                        for (Contact contact : contacts) {
                            String accountGuid = contact.getAccountGuid();

                            if (!StringUtil.isNullOrEmpty(accountGuid) && !ownAccountGuid.equals(accountGuid)) {
                                String publicKey = publicKeysMap.get(accountGuid);

                                if (!StringUtil.isNullOrEmpty(publicKey)) {
                                    contact.setPublicKey(publicKey);

                                    contactController.insertOrUpdateContact(contact);

                                    contactsList.add(contact);
                                }
                            }
                        }
                    }

                    @Override
                    public void onLoadPublicKeyError(String message) {
                        // weiter machen damit wir die Nachrichten in die DB bekommen
                        contactsList.addAll(contacts);
                    }
                };

                try {
                    contactController.loadPublicKeys(contacts, listener);
                } catch (Exception e) {
                    LogUtil.w(PrivateInternalMessageController.class.getSimpleName(), e.getMessage(), e);

                    // weiter machen damit wir die Nachrichten in die DB bekommen
                    contactsList.addAll(contacts);
                }

                List<BaseMessageModel> messageModels = new ArrayList<>();

                for (Contact contact : contactsList) {
                    if (!StringUtil.isNullOrEmpty(contact.getPublicKey())) {
                        try {
                            messageModels.add(buildMessage(contact, action));
                        } catch (LocalizedException e) {
                            LogUtil.w(PrivateInternalMessageController.class.getSimpleName(), "sendBroadcastActionToContacts()", e);
                        }
                    }
                }

                try {
                    sendMessages(messageModels, false);
                } catch (Exception e) {
                    LogUtil.w(PrivateInternalMessageController.class.getSimpleName(), "sendBroadcastActionToContacts()", e);
                }

                return null;
            }
        };
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private PrivateInternalMessageModel buildMessage(final Contact toContact,
                                                     final Action action)
            throws LocalizedException {
        ContactController contactController = mContext.getContactController();
        AccountController accountController = mContext.getAccountController();
        KeyController keyController = mContext.getKeyController();

        final String actionString = gson.toJson(action);

        final SecretKey aesKey = SecurityUtil.generateAESKey();
        final IvParameterSpec iv = SecurityUtil.generateIV();

        final PrivateInternalMessageModel privateInternalMessageModel = MessageModelBuilder.getInstance(contactController)
                .buildPrivateInternalMessage(actionString,
                        accountController.getAccount(),
                        //null,
                        // null,
                        toContact.getAccountGuid(),
                        toContact.getPublicKey(),
                        keyController.getUserKeyPair(),
                        aesKey,
                        iv,
                        null);

        privateInternalMessageModel.requestGuid = GuidUtil.generateRequestGuid();

        return privateInternalMessageModel;
    }

    private void sendMessages(final List<BaseMessageModel> messageModels,
                              final boolean resend) {
        if (!resend) {
            eu.ginlo_apps.ginlo.controller.message.MessageController messageController = mContext.getMessageController();
            messageController.persistSentMessages(messageModels);
        }
        sendMessageToBackend(messageModels);
    }

    // TODO gyan check how account can be null at this point. See the history here.
    private void sendMessageToBackend(final List<BaseMessageModel> messageModels) {

        final IBackendService.OnBackendResponseListener onBackendResponseListener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(final BackendResponse response) {
                MessageController messageController = mContext.getMessageController();
                for (BaseMessageModel messageModel : messageModels) {
                    final Message message = messageController.getMessageByRequestGuid(messageModel.requestGuid);

                    if (!response.isError) {
                        if (message != null) {
                            messageController.deleteMessage(message);
                        }
                    } else {
                        if (message != null) {
                            messageController.markAsError(message, true);
                        }
                    }
                }
            }
        };

        final String messagesJson = gson.toJson(messageModels);

        BackendService.withSyncConnection(mContext)
                .sendPrivateInternalMessages(messagesJson, onBackendResponseListener);
    }

    private void performAction(final String fromGuid,
                               final Action action,
                               final DecryptedMessage decryptedMessage)
            throws LocalizedException {
        ContactController contactController = mContext.getContactController();
        GroupChatController groupChatController = mContext.getGroupChatController();
        if (!StringUtil.isNullOrEmpty(decryptedMessage.getProfilKey())) {
            contactController.setProfilInfoAesKey(fromGuid, decryptedMessage.getProfilKey(), decryptedMessage);
        }

        if (action instanceof ChangeStatusAction) {
            contactController.setStatus(fromGuid, ((ChangeStatusAction) action).status);
        } else if (action instanceof ChangeProfileNameAction) {
            contactController.setNickname(fromGuid, ((ChangeProfileNameAction) action).profileName);
        } else if (action instanceof ChangeProfileImageAction) {
            contactController.setImage(fromGuid, ((ChangeProfileImageAction) action).profileImage);
        } else if (action instanceof ChangeGroupNameAction) {
            final ChangeGroupNameAction changeGroupNameAction = (ChangeGroupNameAction) action;

            groupChatController.setGroupName(changeGroupNameAction.groupGuid, changeGroupNameAction.groupName);
        } else if (action instanceof ChangeGroupImageAction) {
            final ChangeGroupImageAction changeGroupImageAction = (ChangeGroupImageAction) action;

            groupChatController.setGroupImage(changeGroupImageAction.groupGuid, changeGroupImageAction.groupImage);
        } else if (action instanceof NewGroupMemberAction) {
            final NewGroupMemberAction newGroupMemberAction = (NewGroupMemberAction) action;

            groupChatController.newRoomMember(newGroupMemberAction.getGroupGuid(), newGroupMemberAction.getGuids(),
                    newGroupMemberAction.memberNames);
        } else if (action instanceof RemoveGroupMemberAction) {
            final RemoveGroupMemberAction removeGroupMemberAction = (RemoveGroupMemberAction) action;

            groupChatController.removeRoomMember(removeGroupMemberAction);
        } else if (action instanceof CompanyEncryptInfoAction) {
            mContext.getAccountController().handleActionModel(action);
        } else if (action instanceof RequestConfirmPhoneAction) {
            mContext.getAccountController().handleActionModel(action);
        } else if (action instanceof RequestConfirmEmailAction) {
            mContext.getAccountController().handleActionModel(action);
        }
    }
}
