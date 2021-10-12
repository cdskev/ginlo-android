// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller.message;

import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.ContactController.OnLoadPublicKeyListener;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.GroupChatController;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnAcceptInvitationListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnDeclineInvitationListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.SilenceChatListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.AESKeyDataContainer;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil;

public class SingleChatController extends ChatController {

    final private static String TAG = SingleChatController.class.getSimpleName();

    public SingleChatController(final SimsMeApplication application) {
        super(application);
    }

    @Override
    public int[] getTypeResponsibility() {
        return new int[]{Message.TYPE_PRIVATE};
    }

    @Override
    public int getMainTypeResponsibility() {
        return Message.TYPE_PRIVATE;
    }


    @Override
    public AESKeyDataContainer getEncryptionData(String recipientGuid) throws LocalizedException {
        ContactController contactController = mApplication.getContactController();
        final Contact contact = contactController.getContactByGuid(recipientGuid);

        if(contact == null) {
            LogUtil.e(TAG, "getEncryptionData: No contact info for recipientGuid " + recipientGuid);
            throw new LocalizedException(LocalizedException.OBJECT_NULL);
        }

        if (contact.getKey2() == null) {
            contact.setKey2(SecurityUtil.generateAESKey());
            contactController.insertOrUpdateContact(contact);
            LogUtil.d(TAG, "getEncryptionData: Key2 generated and saved for recipientGuid " + recipientGuid);
        }

        return new AESKeyDataContainer((SecretKey) contact.getKey2(), null);
    }

    @Override
    public void acceptInvitation(final Chat chat,
                                 final OnAcceptInvitationListener onAcceptInvitationListener) {
        if (chat != null) {
            try {
                AccountController accountController = mApplication.getAccountController();
                ContactController contactController = mApplication.getContactController();

                Contact contact = contactController.createContactIfNotExists(chat.getChatGuid(), null, null, false);

                if (chat.getType() != Chat.TYPE_SINGLE_CHAT_INVITATION) {
                    onAcceptInvitationListener.onAcceptSuccess(chat);
                    contactController.setIsFirstContact(contact, false);
                    return;
                }

                contactController.upgradeTrustLevel(contact, Contact.STATE_MIDDLE_TRUST);
                contactController.setIsFirstContact(contact, false);

                if (contact.getPublicKey() != null) {
                    chat.setType(Chat.TYPE_SINGLE_CHAT);
                    synchronized (chatDao) {
                        chatDao.update(chat);
                    }
                    if (onAcceptInvitationListener != null) {
                        onAcceptInvitationListener.onAcceptSuccess(chat);
                    }
                } else {
                    OnLoadPublicKeyListener onLoadPublicKeyListener = new OnLoadPublicKeyListener() {
                        @Override
                        public void onLoadPublicKeyError(String message) {
                            deleteChat(chat.getChatGuid(), true, null);
                            if (onAcceptInvitationListener != null) {
                                onAcceptInvitationListener.onAcceptError(message, true);
                            }
                        }

                        @Override
                        public void onLoadPublicKeyComplete(Contact contact) {
                            chat.setType(Chat.TYPE_SINGLE_CHAT);
                            synchronized (chatDao) {
                                chatDao.update(chat);
                            }

                            if (onAcceptInvitationListener != null) {
                                onAcceptInvitationListener.onAcceptSuccess(chat);
                            }
                        }
                    };
                    contactController.loadPublicKey(contact, onLoadPublicKeyListener);
                }

                List<Contact> cl = new ArrayList<>(1);
                cl.add(contact);

                this.privateInternalMessageController.broadcastProfileNameChange(cl, accountController.getAccount().getName());
            } catch (LocalizedException e) {
                onAcceptInvitationListener.onAcceptError(null, false);
            }
        }
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
                        final ContactController contactController = mApplication.getContactController();
                        final Contact contactByGuid = contactController.getContactByGuid(chatGuid);
                        contactByGuid.setSilentTill(DateUtil.utcWithoutMillisStringToMillis(dateString));
                        contactController.insertOrUpdateContact(contactByGuid);
                    }
                } catch (final LocalizedException le) {
                    LogUtil.w(TAG, "silenceChat: " + le.getMessage(), le);
                    if (silenceChatListener != null) {
                        silenceChatListener.onFail(mApplication.getResources().getString(R.string.chat_mute_error));
                    }
                }
            }
        };

        BackendService.withAsyncConnection(mApplication)
                .setSilentSingleChat(chatGuid,
                        dateString,
                        onBackendResponseListener);
    }

    @Override
    public void declineInvitation(Chat chat, OnDeclineInvitationListener onDeclineInvitationListener) {
        if (chat.getType() != Chat.TYPE_SINGLE_CHAT_INVITATION) {
            onDeclineInvitationListener.onDeclineSuccess(chat);
            return;
        }

        try {
            deleteChat(chat.getChatGuid(), true, null);
            mApplication.getContactController().blockContact(chat.getChatGuid(), true, true, null);
            onDeclineInvitationListener.onDeclineSuccess(chat);
        } catch (LocalizedException e) {
            onDeclineInvitationListener.onDeclineError(chat, null, false);
        }
    }
}
