// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller;

import android.os.AsyncTask;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import android.util.SparseArray;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.concurrent.manager.SerialExecutor;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.contracts.AppLifecycleCallbacks;
import eu.ginlo_apps.ginlo.controller.message.GroupChatController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.controller.message.MessageDataResolver;
import eu.ginlo_apps.ginlo.controller.message.contracts.MessageControllerListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.action.Action;
import eu.ginlo_apps.ginlo.model.backend.action.ConfigVersionsChangedAction;
import eu.ginlo_apps.ginlo.model.backend.action.ConfirmChatDeletedV1Action;
import eu.ginlo_apps.ginlo.model.backend.action.ConfirmDeletedAction;
import eu.ginlo_apps.ginlo.model.backend.action.ConfirmMessageSendAction;
import eu.ginlo_apps.ginlo.model.backend.action.ConfirmV1Action;
import eu.ginlo_apps.ginlo.model.backend.action.GroupAction;
import eu.ginlo_apps.ginlo.model.backend.action.GroupInfoChangedV1Action;
import eu.ginlo_apps.ginlo.model.backend.action.InviteGroupMemberAction;
import eu.ginlo_apps.ginlo.model.backend.action.NewGroupAdminAction;
import eu.ginlo_apps.ginlo.model.backend.action.NewGroupMemberAction;
import eu.ginlo_apps.ginlo.model.backend.action.OooStatusAction;
import eu.ginlo_apps.ginlo.model.backend.action.ProfilInfoChangedV1Action;
import eu.ginlo_apps.ginlo.model.backend.action.RemoveChatRoomV1Action;
import eu.ginlo_apps.ginlo.model.backend.action.RemoveGroupMemberAction;
import eu.ginlo_apps.ginlo.model.backend.action.RevokeGroupAdminAction;
import eu.ginlo_apps.ginlo.model.backend.action.RevokeMailAction;
import eu.ginlo_apps.ginlo.model.backend.action.RevokePhoneAction;
import eu.ginlo_apps.ginlo.model.backend.action.UpdateAccountID;
import eu.ginlo_apps.ginlo.model.backend.serialization.InternalMessageModelActionDeserializer;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class InternalMessageController
        implements MessageControllerListener, AppLifecycleCallbacks {

    private static final SerialExecutor INTERNAL_MSG_SERIAL_EXECUTOR = new SerialExecutor();
    private static final SerialExecutor DELETE_MSG_SERIAL_EXECUTOR = new SerialExecutor();
    private final SimsMeApplication mApplication;
    private final Gson gson;
    private InternalMsgTask mInternalMsgTask;
    private boolean mHaveToStartInternalTaskAgain;

    public InternalMessageController(final SimsMeApplication application) {
        mApplication = application;

        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(Action.class, new InternalMessageModelActionDeserializer());
        this.gson = gsonBuilder.create();

        mApplication.getAppLifecycleController().registerAppLifecycleCallbacks(this);
    }

    @Override
    public void onNewMessages(final int types) {
        if ((types & AppConstants.Message.NewMessagesStates.TYPE_INTERNAL) == AppConstants.Message.NewMessagesStates.TYPE_INTERNAL) {
            if (mInternalMsgTask != null) {
                mHaveToStartInternalTaskAgain = true;
            }

            mInternalMsgTask = new InternalMsgTask(gson, new GenericActionListener<Void>() {
                @Override
                public void onSuccess(Void object) {
                    mInternalMsgTask = null;
                    if (mHaveToStartInternalTaskAgain) {
                        onNewMessages(AppConstants.Message.NewMessagesStates.TYPE_INTERNAL);
                    }
                }

                @Override
                public void onFail(String message, String errorIdent) {
                    //wird nicht aufgerufen
                }
            });

            mInternalMsgTask.executeOnExecutor(INTERNAL_MSG_SERIAL_EXECUTOR);
        }
    }

    @Override
    public void onMessagesChanged(SparseArray<List<Message>> messagesListContainer) {
        if (messagesListContainer.get(Message.TYPE_INTERNAL) != null) {
            MessageController messageController = mApplication.getMessageController();
            final List<Message> messages = messagesListContainer.get(Message.TYPE_INTERNAL);

            if (messages == null || messages.isEmpty()) {
                return;
            }

            for (final Message message : messages) {
                final boolean isSent = (message.getIsSentMessage() != null && message.getIsSentMessage())
                        && (message.getDateSendConfirm() != null);
                final boolean isReceivedAndDownloaded = (message.getIsSentMessage() == null || !message.getIsSentMessage())
                        && (message.getDateDownloaded() != null)
                        && (message.getDateDownloaded() != 0);

                if (isSent || isReceivedAndDownloaded) {
                    messageController.deleteMessage(message);
                }
            }
        }
    }

    @Override
    public void appDidEnterForeground() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                mApplication.getMessageController().deleteAllReadInternalMessages();
                return null;
            }
        }.executeOnExecutor(DELETE_MSG_SERIAL_EXECUTOR);
    }

    @Override
    public void appGoesToBackGround() {

    }

    private static class InternalMsgTask extends AsyncTask<Void, Void, Boolean> {
        private final ArrayMap<String, String> privateIndexGuidsAndChecksum = new ArrayMap<>();
        private final SimsMeApplication mApp;
        private final Gson mGson;
        private final GenericActionListener<Void> mListener;

        InternalMsgTask(final Gson gson, @NonNull final GenericActionListener<Void> listener) {
            mApp = SimsMeApplication.getInstance();
            mGson = gson;
            mListener = listener;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            MessageController messageController = mApp.getMessageController();
            List<Message> internalMessages = messageController.getUnreadInternalMessages();

            if (internalMessages == null || internalMessages.size() < 1) {
                return false;
            }

            List<Message> readMessages = new ArrayList<>();
            ArrayList<ConfirmV1Action> confirmActions = new ArrayList<>(internalMessages.size());

            try {
                for (Message message : internalMessages) {
                    if (message == null) {
                        continue;
                    }

                    try {
                        if (message.getType() == Message.TYPE_INTERNAL && message.getData() != null) {
                            String data = new String(message.getData(), StandardCharsets.UTF_8);
                            Action action = mGson.fromJson(data, Action.class);

                            if (action == null) {
                                continue;
                            }

                            if (StringUtil.isEqual(message.getFrom(), mApp.getAccountController().getAccount().getAccountGuid())) {
                                action.setIsOwnMessage(true);
                            }

                            if (action instanceof ConfirmV1Action) {
                                ConfirmV1Action confirmV1Action = (ConfirmV1Action) action;

                                confirmV1Action.fromGuid = message.getFrom();

                                if (message.getDateSend() != null) {
                                    confirmV1Action.dateSend = message.getDateSend();
                                }

                                confirmActions.add(confirmV1Action);
                                readMessages.add(message);
                            } else if (action instanceof ConfirmDeletedAction) {
                                for (String guid : ((ConfirmDeletedAction) action).getMessagesToDelete()) {
                                    String chatGuid = null;

                                    Message m = mApp.getMessageController().getMessageByGuid(guid);
                                    if (m != null) {
                                        chatGuid = MessageDataResolver.getGuidForMessage(m);
                                    }

                                    if (guid.startsWith("100:")) {
                                        mApp.getSingleChatController().deleteMessage(guid, false, null);
                                    }
                                    if (guid.startsWith("101:")) {
                                        mApp.getGroupChatController().deleteMessage(guid, false, null);
                                    }

                                    if (chatGuid != null) {
                                        mApp.getChatOverviewController().chatChanged(null, chatGuid, null, eu.ginlo_apps.ginlo.controller.ChatOverviewController.CHAT_CHANGED_MSG_DELETED);
                                    }
                                }
                                readMessages.add(message);
                            } else if (action instanceof ConfirmChatDeletedV1Action) {
                                ConfirmChatDeletedV1Action chatDeleted = (ConfirmChatDeletedV1Action) action;
                                Chat chat = mApp.getSingleChatController().getChatByGuid(chatDeleted.getGuid());

                                if (chat != null) {

                                    mApp.getSingleChatController().deleteChat(chat.getChatGuid(), false, null)
                                    ;
                                    mApp.getChatOverviewController().chatChanged(null, chatDeleted.getGuid(), null,
                                            ChatOverviewController.CHAT_CHANGED_DELETE_CHAT);
                                }

                                readMessages.add(message);
                            } else if (action instanceof ConfirmMessageSendAction) {
                                ConfirmMessageSendAction msa = (ConfirmMessageSendAction) action;
                                List<String> pendingMessageGuid = new ArrayList<>();
                                pendingMessageGuid.add(msa.getMessageGuid());
                                messageController.loadTimedMessages(pendingMessageGuid);
                                readMessages.add(message);
                            } else if (action instanceof RevokePhoneAction) {
                                mApp.getAccountController().loadConfirmedIdentitiesConfig(null, null);
                                readMessages.add(message);
                            } else if (action instanceof RevokeMailAction) {
                                mApp.getAccountController().loadConfirmedIdentitiesConfig(null, null);
                                readMessages.add(message);
                            } else if (action instanceof UpdateAccountID) {
                                mApp.getAccountController().loadConfirmedIdentitiesConfig(null, null);
                                readMessages.add(message);
                            } else if (action instanceof OooStatusAction) {
                                final OooStatusAction oooStatusAction = (OooStatusAction) action;
                                if (oooStatusAction.mJsonObject != null
                                        && oooStatusAction.mJsonObject.has(JsonConstants.OOO_STATUS_TEXT)
                                        && oooStatusAction.mJsonObject.has(JsonConstants.OOO_STATUS_TEXT_IV)) {
                                    final Contact contactByGuid = mApp.getContactController().getContactByGuid(message.getFrom());
                                    if (contactByGuid != null) {
                                        final String encodedDecryptedText = oooStatusAction.mJsonObject.get(JsonConstants.OOO_STATUS_TEXT).getAsString();
                                        final String encodedIv = oooStatusAction.mJsonObject.get(JsonConstants.OOO_STATUS_TEXT_IV).getAsString();

                                        byte[] dataBytes = android.util.Base64.decode(encodedIv, android.util.Base64.NO_WRAP);

                                        String statusText;

                                        if (contactByGuid.getProfileInfoAesKey() != null) {

                                            final SecretKey aesKeyFromBase64String = SecurityUtil.getAESKeyFromBase64String(contactByGuid.getProfileInfoAesKey());

                                            statusText = SecurityUtil.decryptBase64StringWithAES(encodedDecryptedText,
                                                    aesKeyFromBase64String,
                                                    new IvParameterSpec(dataBytes)
                                            );
                                        } else {
                                            statusText = mApp.getResources().getString(R.string.absence_text_default);
                                            if (oooStatusAction.mJsonObject.has(JsonConstants.OOO_STATUS_STATE_VALID) && !oooStatusAction.mJsonObject.get(JsonConstants.OOO_STATUS_STATE_VALID).isJsonNull()) {
                                                final String validTill = oooStatusAction.mJsonObject.get(JsonConstants.OOO_STATUS_STATE_VALID).getAsString();
                                                Date validTillDate = DateUtil.utcWithoutMillisStringToDate(validTill);
                                                if (validTillDate != null) {
                                                    statusText = mApp.getResources().getString(R.string.absence_text_default2, DateUtil.getDateAndTimeStringFromMillis(validTillDate.getTime()));
                                                }
                                            }
                                        }
                                        mApp.getSingleChatController()
                                                .sendSystemInfo(message.getFrom(),
                                                        contactByGuid.getPublicKey(),
                                                        null,
                                                        null,
                                                        String.format(mApp.getResources().getString(R.string.chat_absence_title), statusText),
                                                        -1, null, true);
                                    }
                                }
                                readMessages.add(message);
                            } else {
                                performAction(action, privateIndexGuidsAndChecksum);
                                readMessages.add(message);
                            }
                        }
                    } catch (JsonSyntaxException e) {
                        LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                        readMessages.add(message);
                    }
                }

                if (confirmActions.size() > 0) {
                    performAction(confirmActions);
                }
            } catch (LocalizedException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            }

            if (readMessages.size() > 0) {
                for (Message message : readMessages) {
                    message.setRead(true);
                }

                messageController.updateMessages(readMessages);
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean value) {
            if (privateIndexGuidsAndChecksum.size() > 0) {
                mApp.getPreferencesController().addPrivateIndexGuidsToLoad(privateIndexGuidsAndChecksum);
                mApp.getContactController().loadPrivateIndexEntries(null);
            }

            mListener.onSuccess(null);
        }

        private void performAction(List<ConfirmV1Action> actions) {
            if (actions.size() > 0) {
                mApp.getMessageController().handleConfirmActions(actions);
            }
        }

        private void performAction(final Action action, @NonNull ArrayMap<String, String> privateIndexGuidsAndChecksum)
                throws LocalizedException {
            if (action instanceof RemoveChatRoomV1Action) {
                Handler handler = new Handler(mApp.getMainLooper());
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        GroupChatController groupChatController = mApp.getGroupChatController();
                        groupChatController.markAsRemoved((RemoveChatRoomV1Action) action);
                    }
                };

                handler.post(runnable);
            } else if (action instanceof ProfilInfoChangedV1Action) {
                final ContactController contactController = mApp.getContactController();
                final String accountGuid = ((ProfilInfoChangedV1Action) action).guid;
                final AccountController accountController = mApp.getAccountController();
                if (!StringUtil.isEqual(accountGuid, accountController.getAccount().getAccountGuid())) {
                    contactController.updateContactProfileInfosFromServer(accountGuid);
                }
            } else if (action instanceof NewGroupMemberAction) {
                GroupChatController groupChatController = mApp.getGroupChatController();
                final NewGroupMemberAction newGroupMemberAction = (NewGroupMemberAction) action;

                groupChatController.newRoomMember(newGroupMemberAction);
            } else if (action instanceof RemoveGroupMemberAction) {
                Handler handler = new Handler(mApp.getMainLooper());
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        GroupChatController groupChatController = mApp.getGroupChatController();
                        final RemoveGroupMemberAction removeGroupMemberAction = (RemoveGroupMemberAction) action;

                        groupChatController.removeRoomMember(removeGroupMemberAction);
                    }
                };
                handler.post(runnable);
            } else if (action instanceof GroupInfoChangedV1Action) {
                GroupChatController groupChatController = mApp.getGroupChatController();
                groupChatController.getAndUpdateRoomInfo(((GroupInfoChangedV1Action) action).guid);
            } else if (action instanceof InviteGroupMemberAction
                    || action instanceof NewGroupAdminAction
                    || action instanceof RevokeGroupAdminAction) {
                GroupChatController groupChatController = mApp.getGroupChatController();
                groupChatController.doRoomAction((GroupAction) action);
            } else if (action instanceof ConfigVersionsChangedAction) {
                ConfigVersionsChangedAction configAction = (ConfigVersionsChangedAction) action;
                if (configAction.details != null) {
                    String cmd = JsonUtil.stringFromJO(JsonConstants.CMD, configAction.details);
                    if (StringUtil.isEqual(cmd, "insUpdPrivateIndexEntry")) {
                        String guid = JsonUtil.stringFromJO(JsonConstants.GUID, configAction.details);
                        String checksum = JsonUtil.stringFromJO(JsonConstants.DATA_CHECKSUM, configAction.details);

                        if (!StringUtil.isNullOrEmpty(guid) && !StringUtil.isNullOrEmpty(checksum)) {
                            privateIndexGuidsAndChecksum.put(guid, checksum);

                            return;
                        }
                    }
                    if (StringUtil.isEqual(cmd, "setBlocked")) {
                        String accountGuid = JsonUtil.stringFromJO(JsonConstants.ACCOUNT_GUID, configAction.details);
                        String block = JsonUtil.stringFromJO(JsonConstants.BLOCK, configAction.details);
                        mApp.getContactController().blockContact(accountGuid, StringUtil.isEqual(block, "1"), false, null);
                    }
                }

                mApp.getPreferencesController().loadServerConfigVersions(true, null);
            }
        }
    }
}
