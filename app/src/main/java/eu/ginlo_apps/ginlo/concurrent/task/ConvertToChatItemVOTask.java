// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.task;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.controller.message.ChannelChatController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.GroupChatController;
import eu.ginlo_apps.ginlo.controller.message.MessageDataResolver;
import eu.ginlo_apps.ginlo.controller.message.SingleChatController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.chat.AVChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.AppGinloControlChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.AttachmentChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ChannelChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ChannelSelfDestructionChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.FileChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ImageChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.LocationChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.RichContentChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.SelfDestructionChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.SystemInfoChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.TextChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VCardChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VideoChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VoiceChatItemVO;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.DataContainer;
import eu.ginlo_apps.ginlo.util.MimeUtil;
import eu.ginlo_apps.ginlo.util.AudioUtil;
import eu.ginlo_apps.ginlo.util.ImageUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.StorageUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import ezvcard.VCard;
import ezvcard.property.Photo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConvertToChatItemVOTask
        extends ConcurrentTask {
    private final static String TAG = ConvertToChatItemVOTask.class.getSimpleName();
    private final ChatController mChatController;

    private final ChannelController mChannelController;

    private final List<Message> mMessages;
    private final String mGuid;
    private final boolean mOnlyShowTimedMessages;
    private final SimsMeApplication mApplication;
    private List<BaseChatItemVO> mItems;
    private int mState;

    public ConvertToChatItemVOTask(String chatGuid,
                                   ChatController chatController,
                                   SimsMeApplication application,
                                   List<Message> messages,
                                   boolean onlyShowTimedMessages) {
        super();

        this.mChatController = chatController;
        this.mApplication = application;
        this.mChannelController = application.getChannelController();
        if (messages != null) {
            this.mMessages = new ArrayList<>(messages);
        } else {
            this.mMessages = null;
        }
        this.mGuid = chatGuid;
        this.mOnlyShowTimedMessages = onlyShowTimedMessages;
    }

    public static BaseChatItemVO getChatItemVO(Message message,
                                               final SimsMeApplication application,
                                               final ChatController chatController,
                                               final ChannelController channelController,
                                               final int state)
            throws LocalizedException {
        String shortLinkText = null;
        String channelType = null;

        if (message.getType() == Message.TYPE_CHANNEL) {
            Channel channel = channelController.getChannelFromDB(MessageDataResolver.getGuidForMessage(message));
            shortLinkText = channel.getShortLinkText();
            channelType = channel.getType();
        }

        return getChatItemVO(message, application, chatController, state, shortLinkText, channelType);
    }

    private static BaseChatItemVO getChatItemVO(final Message message,
                                                final SimsMeApplication application,
                                                final ChatController chatController,
                                                final int state,
                                                final String shortLinkText,
                                                final String channelType)
            throws LocalizedException {

        BaseChatItemVO returnChatItemVO = null;
        StorageUtil storageUtil = new StorageUtil(application);

        DecryptedMessage decryptedMsg = application.getMessageDecryptionController().decryptMessage(message, false);
        if (decryptedMsg == null) {
            LogUtil.w(TAG, "getChatItemVO: decryptedMsg = null!");
            return null;
        }

        String contentType = decryptedMsg.getContentType();
        String fileMimetype = decryptedMsg.getFileMimetype();

        if (contentType == null) {
            LogUtil.w(TAG, "getChatItemVO: contentType = null!");
            return null;
        }

        if (decryptedMsg.getMessage().getType() == Message.TYPE_CHANNEL) {
            if ((decryptedMsg.getMessage().getIsSystemInfo() != null)
                    && decryptedMsg.getMessage().getIsSystemInfo()
                    && !StringUtil.isEqual(decryptedMsg.getMessage().getFrom(), AppConstants.GUID_SYSTEM_CHAT)) {
                SystemInfoChatItemVO chatItemVO = new SystemInfoChatItemVO();

                chatItemVO.infoText = decryptedMsg.getText();
                returnChatItemVO = chatItemVO;
            } else {
                returnChatItemVO = getChannelChatItem(decryptedMsg, chatController, shortLinkText, channelType);
            }
        } else if (contentType.equals(MimeUtil.MIME_TYPE_TEXT_RSS)) {
            ChannelChatItemVO chatItemVO = new ChannelChatItemVO();

            chatItemVO.name = null;

            chatItemVO.channelType = Channel.TYPE_CHANNEL;
            chatItemVO.messageHeader = decryptedMsg.getRssTitle();

            chatItemVO.message = chatItemVO.messageContent = decryptedMsg.getRssText();

            chatItemVO.shortLinkText = application.getString(R.string.content_rss_link_more);

            returnChatItemVO = chatItemVO;
        } else if (contentType.equals(MimeUtil.MIME_TYPE_TEXT_PLAIN)) {
            if ((decryptedMsg.getMessage().getIsSystemInfo() != null)
                    && decryptedMsg.getMessage().getIsSystemInfo()
                    && !StringUtil.isEqual(decryptedMsg.getMessage().getFrom(), AppConstants.GUID_SYSTEM_CHAT)) {
                SystemInfoChatItemVO chatItemVO = new SystemInfoChatItemVO();

                String msg = decryptedMsg.getText();

                if (!StringUtil.isNullOrEmpty(msg)) {
                    while (msg.contains("0:{")) {
                        int startGuidIndex = msg.indexOf("0:{");
                        int endGuidIndex = msg.indexOf("}", startGuidIndex);
                        if (endGuidIndex > startGuidIndex) {
                            String guid = msg.substring(startGuidIndex, endGuidIndex + 1);

                            String name = chatController.getTitleForChat(guid);
                            if (StringUtil.isNullOrEmpty(name)) {
                                name = application.getString(R.string.chats_contact_unknown);
                            }

                            String prefix = null;
                            String suffix = null;
                            if (startGuidIndex > 0) {
                                prefix = msg.substring(0, startGuidIndex);
                            }

                            if (endGuidIndex + 1 < msg.length()) {
                                suffix = msg.substring(endGuidIndex + 1);
                            }

                            String newMsg = null;
                            if (!StringUtil.isNullOrEmpty(prefix)) {
                                newMsg = prefix;
                            }

                            newMsg = newMsg != null ? (newMsg + name) : name;

                            if (!StringUtil.isNullOrEmpty(suffix)) {
                                newMsg = newMsg + suffix;
                            }

                            msg = newMsg;
                        } else {
                            break;
                        }
                    }
                }
                chatItemVO.infoText = msg;
                chatItemVO.isAbsentMessage = decryptedMsg.getMessage().getIsAbsentMessage();
                returnChatItemVO = chatItemVO;
            } else if (decryptedMsg.getMessageDestructionParams() != null) {
                SelfDestructionChatItemVO chatItemVO = new SelfDestructionChatItemVO();

                chatItemVO.destructionType = SelfDestructionChatItemVO.TYPE_TEXT;
                chatItemVO.destructionParams = decryptedMsg.getMessageDestructionParams();
                chatItemVO.text = decryptedMsg.getText();
                chatItemVO.name = chatController.getNameForMessage(decryptedMsg);
                returnChatItemVO = chatItemVO;
            } else {
                TextChatItemVO chatItemVO = new TextChatItemVO();

                chatItemVO.message = decryptedMsg.getText();
                chatItemVO.name = chatController.getNameForMessage(decryptedMsg);
                returnChatItemVO = chatItemVO;
            }

            // KS: APP_GINLO_CONTROL
        } else if (contentType.equals(MimeUtil.MIME_TYPE_APP_GINLO_CONTROL)) {
            AppGinloControlChatItemVO chatItemVO = new AppGinloControlChatItemVO();
            chatItemVO.loadControlMessageFromString(decryptedMsg.getAppGinloControl(), application);
            returnChatItemVO = chatItemVO;

            // KS: AVC
        } else if (contentType.equals(MimeUtil.MIME_TYPE_TEXT_V_CALL)) {
            AVChatItemVO chatItemVO = new AVChatItemVO();

            chatItemVO.room = decryptedMsg.getAVCRoom();
            // That must be refactored!
            chatItemVO.image = AudioUtil.getWaveformFromLevels();
            chatItemVO.name = chatController.getNameForMessage(decryptedMsg);
            returnChatItemVO = chatItemVO;

        } else if (contentType.equals(MimeUtil.MIME_TYPE_APP_GINLO_RICH_CONTENT) ||
                MimeUtil.isRichContentMimetype(contentType) ||
                MimeUtil.isRichContentMimetype(fileMimetype)
        ) {
            RichContentChatItemVO chatItemVO = new RichContentChatItemVO();

            chatItemVO.attachmentGuid = decryptedMsg.getMessage().getAttachment();
            chatItemVO.name = chatController.getNameForMessage(decryptedMsg);
            chatItemVO.fileMimeType = decryptedMsg.getFileMimetype();
            chatItemVO.fileName = decryptedMsg.getFilename();
            chatItemVO.fileSize = decryptedMsg.getFileSize();

            returnChatItemVO = chatItemVO;
        } else if (contentType.equals(MimeUtil.MIME_TYPE_IMAGE_JPEG)) {
            AttachmentChatItemVO chatItemVO;

            if (decryptedMsg.getMessageDestructionParams() != null) {
                chatItemVO = new SelfDestructionChatItemVO();
                ((SelfDestructionChatItemVO) chatItemVO).destructionType = SelfDestructionChatItemVO.TYPE_IMAGE;
                ((SelfDestructionChatItemVO) chatItemVO).destructionParams = decryptedMsg.getMessageDestructionParams();
            } else {
                chatItemVO = new ImageChatItemVO();
            }
            chatItemVO.image = decryptedMsg.getPreviewImage();
            chatItemVO.attachmentGuid = decryptedMsg.getMessage().getAttachment();
            chatItemVO.attachmentDesc = decryptedMsg.getAttachmentDescription();
            chatItemVO.name = chatController.getNameForMessage(decryptedMsg);

            returnChatItemVO = chatItemVO;
        } else if (contentType.equals(MimeUtil.MIME_TYPE_MODEL_LOCATION)) {
            LocationChatItemVO chatItemVO = new LocationChatItemVO();

            chatItemVO.image = decryptedMsg.getLocationImage();
            chatItemVO.latitude = decryptedMsg.getLocation().getLatitude();
            chatItemVO.longitude = decryptedMsg.getLocation().getLongitude();
            chatItemVO.name = chatController.getNameForMessage(decryptedMsg);

            returnChatItemVO = chatItemVO;
        } else if (contentType.equals(MimeUtil.MIME_TYPE_VIDEO_MPEG)) {
            AttachmentChatItemVO chatItemVO;

            if (decryptedMsg.getMessageDestructionParams() != null) {
                chatItemVO = new SelfDestructionChatItemVO();
                ((SelfDestructionChatItemVO) chatItemVO).destructionType = SelfDestructionChatItemVO.TYPE_VIDEO;
                ((SelfDestructionChatItemVO) chatItemVO).destructionParams = decryptedMsg.getMessageDestructionParams();
            } else {
                chatItemVO = new VideoChatItemVO();
            }

            chatItemVO.image = decryptedMsg.getPreviewImage();
            chatItemVO.attachmentGuid = decryptedMsg.getMessage().getAttachment();
            chatItemVO.attachmentDesc = decryptedMsg.getAttachmentDescription();
            chatItemVO.name = chatController.getNameForMessage(decryptedMsg);

            returnChatItemVO = chatItemVO;
        } else if (contentType.equals(MimeUtil.MIME_TYPE_AUDIO_MPEG)) {
            AttachmentChatItemVO chatItemVO;

            if (decryptedMsg.getMessageDestructionParams() != null) {
                chatItemVO = new SelfDestructionChatItemVO();
                ((SelfDestructionChatItemVO) chatItemVO).destructionType = SelfDestructionChatItemVO.TYPE_VOICE;
                ((SelfDestructionChatItemVO) chatItemVO).destructionParams = decryptedMsg.getMessageDestructionParams();
            } else {
                chatItemVO = new VoiceChatItemVO();
            }

            chatItemVO.image = decryptedMsg.getPreviewImage();
            chatItemVO.attachmentGuid = decryptedMsg.getMessage().getAttachment();
            chatItemVO.name = chatController.getNameForMessage(decryptedMsg);

            returnChatItemVO = chatItemVO;
        } else if (contentType.equals(MimeUtil.MIME_TYPE_TEXT_V_CARD)) {
            VCardChatItemVO chatItemVO = new VCardChatItemVO();

            chatItemVO.name = chatController.getNameForMessage(decryptedMsg);

            VCard vCard = decryptedMsg.getVCard();

            if (vCard == null) {
                return null;
            }

            chatItemVO.vCard = vCard;
            if (vCard.getPhotos().size() > 0) {
                Photo photo = vCard.getPhotos().get(0);

                chatItemVO.photo = (photo.getData() != null) ? ImageUtil.decodeByteArray(photo.getData()) : null;
                chatItemVO.photoUrl = photo.getUrl();
            }

            if (vCard.getTelephoneNumbers().size() > 0) {
                chatItemVO.phonenumber = vCard.getTelephoneNumbers().get(0).getText();
            }

            if (vCard.getFormattedName() != null
                    && vCard.getFormattedName().getValue() != null
                    && vCard.getFormattedName().getValue().trim().length() != 0) {
                chatItemVO.displayInfo = vCard.getFormattedName().getValue().trim();
            } else if (vCard.getEmails().size() > 0 && vCard.getEmails().get(0).getValue() != null) {
                chatItemVO.displayInfo = vCard.getEmails().get(0).getValue().trim();
            } else if (chatItemVO.phonenumber != null) {
                chatItemVO.displayInfo = chatItemVO.phonenumber.trim();
            } else {
                chatItemVO.displayInfo = "N/A";
            }

            chatItemVO.accountId = JsonUtil.stringFromJO(DataContainer.ACCOUNT_ID, decryptedMsg.getDecryptedDataContainer());
            chatItemVO.accountGuid = JsonUtil.stringFromJO(DataContainer.ACCOUNT_GUID, decryptedMsg.getDecryptedDataContainer());

            returnChatItemVO = chatItemVO;
        } else if (MimeUtil.hasUnspecificBinaryMimeType(contentType)) {
            FileChatItemVO chatItemVO = new FileChatItemVO();

            chatItemVO.attachmentGuid = decryptedMsg.getMessage().getAttachment();
            chatItemVO.name = chatController.getNameForMessage(decryptedMsg);
            chatItemVO.fileMimeType = decryptedMsg.getFileMimetype();
            chatItemVO.fileName = decryptedMsg.getFilename();
            chatItemVO.fileSize = decryptedMsg.getFileSize();

            returnChatItemVO = chatItemVO;
        }

        if (returnChatItemVO != null) {

            LogUtil.d(TAG, "getChatItemVO: returnChatItemVO has been set to " + returnChatItemVO.getClass().getSimpleName());

            returnChatItemVO.setState(state);
            returnChatItemVO.messageId = decryptedMsg.getMessage().getId();
            returnChatItemVO.setFromGuid(message.getFrom());
            returnChatItemVO.setToGuid(decryptedMsg.getMessage().getTo());
            returnChatItemVO.setMessageGuid(message.getGuid());

            if (chatController instanceof SingleChatController) {
                returnChatItemVO.type = BaseChatItemVO.TYPE_SINGLE;
            } else if (chatController instanceof ChannelChatController) {
                returnChatItemVO.type = BaseChatItemVO.TYPE_CHANNEL;
            } else {
                returnChatItemVO.type = BaseChatItemVO.TYPE_GROUP;
            }

            if (chatController instanceof ChannelChatController) {
                returnChatItemVO.isValid = true;
            } else {
                if (message.getSignatureSha256() != null) {
                    returnChatItemVO.isValid = chatController.checkSignatureSha256(decryptedMsg.getMessage());
                } else {
                    returnChatItemVO.isValid = chatController.checkSignature(decryptedMsg.getMessage());
                }
            }

            returnChatItemVO.setCommonValues(decryptedMsg.getMessage());

            if (decryptedMsg.getMessage().getIsSentMessage()) {
                returnChatItemVO.direction = BaseChatItemVO.DIRECTION_RIGHT;

                //pruefen ob die Message noch nicht versand wurde
                if (returnChatItemVO.messageId != -1 && !returnChatItemVO.isSendConfirmed() && !returnChatItemVO.hasSendError) {
                    //wurde noch nicht versand
                    //pruefen ob sie gerade versand wird
                    if (!application.getMessageController().isMessageStillSending(returnChatItemVO.messageId)) {
                        //Nachricht wird gerade nicht versand, daher als Senden fehlgeschlagen markieren
                        application.getMessageController().markAsError(message, true);
                        returnChatItemVO.setHasSendError(true);
                    }
                }
            } else {
                returnChatItemVO.direction = BaseChatItemVO.DIRECTION_LEFT;
            }

            returnChatItemVO.isPriority = message.getIsPriority();

            returnChatItemVO.setCitation(decryptedMsg.getCitation());
        } else {
            LogUtil.w(TAG, "getChatItemVO: returnChatItemVO = null!");
        }

        return returnChatItemVO;
    }

    private static ChannelChatItemVO getChannelChatItem(final DecryptedMessage decryptedMsg,
                                                        final ChatController chatController,
                                                        final String shortLinkText,
                                                        final String channelType)
            throws LocalizedException {
        String contentType = decryptedMsg.getContentType();

        ChannelChatItemVO chatItemVO;
        if (decryptedMsg.getMessageDestructionParams() != null) {
            chatItemVO = new ChannelSelfDestructionChatItemVO();
            ((ChannelSelfDestructionChatItemVO) chatItemVO).destructionParams = decryptedMsg.getMessageDestructionParams();
        } else {
            chatItemVO = new ChannelChatItemVO();
        }

        if (StringUtil.isEqual(contentType, MimeUtil.MIME_TYPE_TEXT_PLAIN)) {
            boolean split = true;
            if (shortLinkText != null) {
                chatItemVO.shortLinkText = shortLinkText;
                if (StringUtil.isEqual(Channel.TYPE_SERVICE, channelType)) {
                    split = false;
                }
            }

            chatItemVO.message = chatItemVO.messageContent = decryptedMsg.getText();

            String decryptedText = decryptedMsg.getText();
            LogUtil.d(TAG, "getChannelChatItem: " + decryptedText);

            //index of nutzen, falls nur ein zeilenumbruch drin ist
            int idx = decryptedText.indexOf('\n');

            if (idx > 1 && split) {
                String messageText = decryptedText.substring(idx + 1);
                String headerText = decryptedText.substring(0, idx);

                if (headerText.endsWith("\r")) {
                    headerText = headerText.substring(0, headerText.length() - 1);
                }
                chatItemVO.messageHeader = headerText;
                chatItemVO.messageContent = messageText;
            } else {
                chatItemVO.messageHeader = "";
            }

            //Channel Preview Image
            chatItemVO.image = decryptedMsg.getChannelImagePreview();

            if (!StringUtil.isNullOrEmpty(decryptedMsg.getMessage().getAttachment())) {
                chatItemVO.attachmentGuid = decryptedMsg.getMessage().getAttachment();
            }
        } else if (StringUtil.isEqual(contentType, MimeUtil.MIME_TYPE_IMAGE_JPEG)) {
            chatItemVO.image = decryptedMsg.getPreviewImage();
            chatItemVO.attachmentGuid = decryptedMsg.getMessage().getAttachment();
        }

        chatItemVO.name = chatController.getNameForMessage(decryptedMsg);

        chatItemVO.section = decryptedMsg.getSection();
        chatItemVO.channelType = channelType;
        return chatItemVO;
    }

    @Override
    public void run() {
        super.run();

        try {
            String shortLinkText = null;
            String channelType = null;

            if (mChatController instanceof ChannelChatController) {
                mState = Contact.STATE_MIDDLE_TRUST;
                Channel channel = mChannelController.getChannelFromDB(mGuid);
                shortLinkText = channel.getShortLinkText();
                channelType = channel.getType();
            } else if (mChatController instanceof SingleChatController) {
                mState = mChatController.getStateForContact(mGuid);
            } else if (mChatController instanceof GroupChatController) {
                mState = ((GroupChatController) mChatController).getStateForGroupChat(mGuid);
            }

            if ((mMessages != null) && (mMessages.size() > 0)) {
                mItems = new ArrayList<>(mMessages.size());
                for (Message message : mMessages) {
                    try {
                        if (message == null) {
                            continue;
                        }

                        BaseChatItemVO item = getChatItemVO(message, mApplication, mChatController, mState, shortLinkText, channelType);

                        if (item != null) {
                            if (mOnlyShowTimedMessages) {
                                if (message.getDateSendTimed() != null) {
                                    item.setDateSend(message.getDateSendTimed());
                                    mItems.add(item);
                                }
                            } else {
                                if (message.getDateSendTimed() == null) {
                                    mItems.add(item);
                                }
                            }
                        }
                    } catch (LocalizedException e) {
                        LogUtil.e(this.getClass().getName(), e.getMessage(), e);
                    }
                }
            }
            complete();
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            error();
        }
    }

    @Override
    public Object[] getResults() {
        if ((mItems != null) && (mItems.size() > 0)) {
            return new Object[]{mItems};
        } else {
            return null;
        }
    }
}
