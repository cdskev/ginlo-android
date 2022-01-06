// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller.message;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Base64;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.concurrent.task.HttpBaseTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AttachmentController.OnAttachmentLoadedListener;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnAcceptInvitationListener;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnDeclineInvitationListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.model.AESKeyDataContainer;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.chat.AttachmentChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.spec.IvParameterSpec;

public class ChannelChatController extends ChatController {

    private final HashMap<String, IvParameterSpec> mChannelIvMap = new HashMap<>();

    private final Map<String, AsyncIconLoaderTask> mIconLoaderTaskMap;

    public ChannelChatController(final SimsMeApplication application) {
        super(application);

        mIconLoaderTaskMap = new HashMap<>();
    }

    @Override
    public void acceptInvitation(final Chat chat,
                                 final OnAcceptInvitationListener onAcceptInvitationListener) {
    }

    @Override
    public void declineInvitation(final Chat chat,
                                  final OnDeclineInvitationListener onDeclineInvitationListener) {
    }

    @Override
    public int[] getTypeResponsibility() {

        return new int[]{Message.TYPE_CHANNEL};
    }

    @Override
    public int getMainTypeResponsibility() {
        return Message.TYPE_CHANNEL;
    }

    @Override
    protected void filterMessages(final List<Message> messages) {
        final ConcurrentTaskListener listener = new ConcurrentTaskListener() {
            @Override
            public void onStateChanged(final ConcurrentTask task,
                                       final int state) {
                if (state == ConcurrentTask.STATE_COMPLETE) {
                    if (task != null && mCurrentChatAdapter != null) {
                        if (task.getResults() != null) {
                            final Object listObject = task.getResults()[0];

                            if ((listObject instanceof List<?>)) {
                                final List<BaseChatItemVO> itemList = (List<BaseChatItemVO>) listObject;
                                Long lastMsgId = null;

                                for (int i = 0; i < itemList.size(); i++) {
                                    final BaseChatItemVO itemObject = itemList.get(i);

                                    if (itemObject != null) {
                                        boolean notifyObserver = false;
                                        if (i == (itemList.size() - 1)) {
                                            notifyObserver = true;
                                            lastMsgId = itemObject.messageId;
                                        }

                                        addToRegistry(mCurrentChatAdapter.getChatGuid(), itemObject, notifyObserver, true);
                                    }
                                }
                                if (lastMsgId != null) {
                                    notifyListenerLoaded(lastMsgId);
                                }
                            }
                        }
                    }
                }
            }
        };

        if (mCurrentChatAdapter != null) {
            chatTaskManager.executeConvertToChatItemVOTask(mCurrentChatAdapter.getChatGuid(), this, mApplication, messages, listener, false);
        }
    }

    @Override
    public void getAttachment(final AttachmentChatItemVO attachmentChatItemVO,
                              final OnAttachmentLoadedListener listener, boolean safeToShareFolder,
                              final HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener) {
        try {
            final Message message = messageController.getMessageById(attachmentChatItemVO.messageId);

            final DecryptedMessage decryptedMessage = messageDecryptionController.decryptMessage(message,
                    false);

            if (decryptedMessage == null) {
                listener.onLoadedFailed("Error decrypting message.");
                return;
            }

            attachmentController.loadAttachment(decryptedMessage, listener, false, onConnectionDataUpdatedListener);
        } catch (LocalizedException e) {
            listener.onLoadedFailed("Error decrypting message.");
        }
    }

    public void loadChannelChatIconAsync(final String channelGuid) {
        AsyncIconLoaderTask asycnIconLoader = mIconLoaderTaskMap.get(channelGuid);

        if (asycnIconLoader == null || asycnIconLoader.getStatus() == AsyncTask.Status.FINISHED) {
            asycnIconLoader = new AsyncIconLoaderTask(mChannelController, channelGuid);
            mIconLoaderTaskMap.put(channelGuid, asycnIconLoader);

            asycnIconLoader.execute(null, null, null);
        }
    }

    private class AsyncIconLoaderTask extends AsyncTask<Void, Void, Boolean> {

        private final String mChannelGuid;
        private final ChannelController mChannelController;

        AsyncIconLoaderTask(final ChannelController controller,
                            final String channelGuid) {
            mChannelController = controller;
            mChannelGuid = channelGuid;
        }

        @Override
        protected Boolean doInBackground(final Void... params) {
            if (mChannelController == null || StringUtil.isNullOrEmpty(mChannelGuid)) {
                return false;
            }

            Bitmap icon = null;
            try {
                icon = mChannelController.loadImage(mChannelGuid, ChannelController.IMAGE_TYPE_PROVIDER_ICON);
            } catch (LocalizedException e) {
                LogUtil.w("AsyncIconLoaderTask", "Load Channel Icon failed!", e);
            }

            return icon != null;
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            if (result != null && result) {
                ChatImageController imageController = mApplication.getChatImageController();
                if (imageController != null) {
                    imageController.clearChatImageCache();
                }

                ChannelChatController.this.refreshAdapter();
            }

            mIconLoaderTaskMap.remove(mChannelGuid);
        }
    }

    @Override
    public AESKeyDataContainer getEncryptionData(String recipientGuid) throws LocalizedException {

        final Chat chat = getChatByGuid(recipientGuid);

        if (chat == null || chat.getChatAESKey() == null) {
            throw new LocalizedException(LocalizedException.CHAT_NOT_FOUND);
        }

        IvParameterSpec iv = mChannelIvMap.get(recipientGuid);

        if (iv == null) {
            final Channel channel = mChannelController.getChannelFromDB(recipientGuid);

            final byte[] ivBytes = Base64.decode(channel.getIv(), Base64.NO_WRAP);

            iv = new IvParameterSpec(ivBytes);

            mChannelIvMap.put(recipientGuid, iv);
        }

        return new AESKeyDataContainer(chat.getChatAESKey(), iv);
    }
}
