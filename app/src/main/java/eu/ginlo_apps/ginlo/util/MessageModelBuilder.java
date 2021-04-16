// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Base64;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONObject;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.AppGinloControlMessage;
import eu.ginlo_apps.ginlo.model.CitationModel;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.backend.BaseMessageModel;
import eu.ginlo_apps.ginlo.model.backend.ChannelMessageModel;
import eu.ginlo_apps.ginlo.model.backend.GroupInvMessageModel;
import eu.ginlo_apps.ginlo.model.backend.GroupMessageModel;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;
import eu.ginlo_apps.ginlo.model.backend.PrivateInternalMessageModel;
import eu.ginlo_apps.ginlo.model.backend.PrivateMessageModel;
import eu.ginlo_apps.ginlo.model.backend.SignatureModel;
import eu.ginlo_apps.ginlo.model.backend.serialization.SignatureModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.SignatureModelSerializer;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.DataContainer;
import eu.ginlo_apps.ginlo.model.constant.FeatureVersion;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import eu.ginlo_apps.ginlo.model.param.MessageDestructionParams;
import eu.ginlo_apps.ginlo.util.AudioUtil;
import eu.ginlo_apps.ginlo.util.BitmapUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.IOSMessageConversionUtil;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.VideoUtil;
import eu.ginlo_apps.ginlo.util.XMLUtil;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.HashMap;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class MessageModelBuilder {
    private static MessageModelBuilder gInstance;

    private final HashMap<String, PublicKey> toPublicKeyCache;

    private final JsonParser jsonParser;

    private final Gson gson;

    private final ContactController mContactController;

    private MessageModelBuilder(ContactController contactController) {
        mContactController = contactController;
        toPublicKeyCache = new HashMap<>();
        jsonParser = new JsonParser();

        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(SignatureModel.class, new SignatureModelDeserializer());
        gsonBuilder.registerTypeAdapter(SignatureModel.class, new SignatureModelSerializer());

        gson = gsonBuilder.create();
    }

    public static MessageModelBuilder getInstance(ContactController contactController) {
        synchronized (MessageModelBuilder.class) {
            if (gInstance == null) {
                gInstance = new MessageModelBuilder(contactController);
            }

            return gInstance;
        }
    }

    private BaseMessageModel prepareMessage(int type,
                                            Account fromAccount,
                                            String fromTempDeviceGuid,
                                            String fromTempDevicePublicKeyXML,
                                            String toGuid,
                                            String toPublicKeyXML,
                                            String toTempDeviceGuid,
                                            String toTempDevicePublicKeyXML,
                                            KeyPair userKeyPair,
                                            SecretKey aesKey,
                                            IvParameterSpec iv,
                                            final Date dateSendTimed,
                                            boolean isPriority
    )
            throws LocalizedException {
        BaseMessageModel messageModel;

        switch (type) {
            case Message.TYPE_PRIVATE:
            case Message.TYPE_PRIVATE_INTERNAL:
                messageModel = createPrivateMessageModelBase(new PrivateMessageModel(), fromAccount, fromTempDeviceGuid, fromTempDevicePublicKeyXML, toGuid, toPublicKeyXML, toTempDeviceGuid, toTempDevicePublicKeyXML, userKeyPair, aesKey, iv, dateSendTimed, isPriority);
                break;
            case Message.TYPE_GROUP:
            case Message.TYPE_GROUP_INVITATION:
                messageModel = createGroupMessageModelBase(fromAccount, toGuid, userKeyPair, aesKey, iv, dateSendTimed, isPriority);
                break;
            case Message.TYPE_CHANNEL:
                messageModel = createChannelMessageModelBase(toGuid);
                break;
            default:
                throw new LocalizedException(LocalizedException.UNDEFINED_ARGUMENT, this.getClass().getSimpleName());
        }

        if (messageModel != null) {
            messageModel.requestGuid = GuidUtil.generateRequestGuid();
            messageModel.messageType = type;
        } else {
            throw new LocalizedException(LocalizedException.MESSAGE_MODEL_IS_NULL);
        }

        return messageModel;
    }

    // KS: AVC
    public BaseMessageModel buildAVCMessage(int type,
                                             String roomInfo,
                                             Account fromAccount,
                                             String fromTempDeviceGuid,
                                             String fromTempDevicePublicKeyXML,
                                             String toGuid,
                                             String toPublicKeyXML,
                                             String toTempDeviceGuid,
                                             String toTempDevicePublicKeyXML,
                                             KeyPair userKeyPair,
                                             SecretKey aesKey,
                                             IvParameterSpec iv,
                                             Date dateSendTimed,
                                             boolean isPriority,
                                             CitationModel citation)
            throws LocalizedException {
        BaseMessageModel messageModel = prepareMessage(type,
                fromAccount,
                fromTempDeviceGuid,
                fromTempDevicePublicKeyXML,
                toGuid, toPublicKeyXML,
                toTempDeviceGuid,
                toTempDevicePublicKeyXML,
                userKeyPair,
                aesKey,
                iv,
                dateSendTimed,
                isPriority);
        messageModel.mimeType = MimeType.TEXT_V_CALL;
        attachAVCRoomInfo(messageModel, roomInfo);
        attachCitation(messageModel, citation);

        encryptData(messageModel, aesKey, iv);
        attachSignature(messageModel, userKeyPair.getPrivate());
        attachSignature(messageModel, userKeyPair.getPrivate(), true);

        return messageModel;
    }

    // KS: APP_GINLO_CONTROL
    /*
    The control message consist of a JSON with the following structure:
    Connected to Message
    {
         "message": <control-message-STRING>,
         "orig-message-type": <original-message-content-type-STRING>,
         "orig-message-identifier": <original-message-identifier-STRING>,
         "additional-payload" : <additional-payload-STRING> || ""
    }

    NOT Connected to Message
    {
         "message": <control-message-STRING>,
         "orig-message-type": "",
         "orig-message-identifier": "",
         "additional-payload" : <additional-payload-STRING> || ""
    }

    Sample:
    {
    “message”: “avCallExpired”,
    “orig-message-type”: “text/x-ginlo-call-invite”,
    “orig-message-identifier”: “password@room1@avc.mydomain.com”,
    “additional-payload”: “”
    }

    The message is implemented as a normal message with a specific content type.
    It is proposed to use the content-type application/x-ginlo-control-message.

    Content-Type: application/x-ginlo-control-message
    Message: <msg-body-json-as-STRING>
     */
    public BaseMessageModel buildAppGinloControlMessage(int type,
                                            AppGinloControlMessage controlMessage,
                                            Account fromAccount,
                                            String fromTempDeviceGuid,
                                            String fromTempDevicePublicKeyXML,
                                            String toGuid,
                                            String toPublicKeyXML,
                                            String toTempDeviceGuid,
                                            String toTempDevicePublicKeyXML,
                                            KeyPair userKeyPair,
                                            SecretKey aesKey,
                                            IvParameterSpec iv)
            throws LocalizedException {
        BaseMessageModel messageModel = prepareMessage(type,
                fromAccount,
                fromTempDeviceGuid,
                fromTempDevicePublicKeyXML,
                toGuid, toPublicKeyXML,
                toTempDeviceGuid,
                toTempDevicePublicKeyXML,
                userKeyPair,
                aesKey,
                iv,
                null,
                false);
        messageModel.mimeType = MimeType.APP_GINLO_CONTROL;
        attachAppGinloControlMessage(messageModel, controlMessage);

        encryptData(messageModel, aesKey, iv);
        attachSignature(messageModel, userKeyPair.getPrivate());
        attachSignature(messageModel, userKeyPair.getPrivate(), true);

        return messageModel;
    }


    public BaseMessageModel buildTextMessage(int type,
                                             String message,
                                             Account fromAccount,
                                             String fromTempDeviceGuid,
                                             String fromTempDevicePublicKeyXML,
                                             String toGuid,
                                             String toPublicKeyXML,
                                             String toTempDeviceGuid,
                                             String toTempDevicePublicKeyXML,
                                             KeyPair userKeyPair,
                                             SecretKey aesKey,
                                             IvParameterSpec iv,
                                             MessageDestructionParams destructionParams,
                                             Date dateSendTimed,
                                             boolean isPriority,
                                             CitationModel citation)
            throws LocalizedException {
        BaseMessageModel messageModel = prepareMessage(type, fromAccount, fromTempDeviceGuid, fromTempDevicePublicKeyXML, toGuid, toPublicKeyXML, toTempDeviceGuid, toTempDevicePublicKeyXML, userKeyPair, aesKey, iv, dateSendTimed, isPriority);
        messageModel.mimeType = MimeType.TEXT_PLAIN;
        if (destructionParams != null) {
            attachSelfDestruction(messageModel, destructionParams);
        }
        attachText(messageModel, message);
        attachCitation(messageModel, citation);

        encryptData(messageModel, aesKey, iv);
        attachSignature(messageModel, userKeyPair.getPrivate());
        attachSignature(messageModel, userKeyPair.getPrivate(), true);

        return messageModel;
    }

    public BaseMessageModel rebuildMessage(Message message,
                                           ChatController chatController) {
        BaseMessageModel messageModel = null;
        int type = message.getType();
        int base64Flag = Base64.NO_WRAP;

        if (type == Message.TYPE_PRIVATE) {
            PrivateMessageModel privateMessageModel = new PrivateMessageModel();

            privateMessageModel.from = new KeyContainerModel(message.getFrom(), Base64.encodeToString(message.getEncryptedFromKey(), base64Flag), null);
            privateMessageModel.to = new KeyContainerModel[]{new KeyContainerModel(message.getTo(), Base64.encodeToString(message.getEncryptedToKey(), base64Flag), message.getEncryptedToKey2())};

            privateMessageModel.requestGuid = message.getRequestGuid();
            privateMessageModel.data = Base64.encodeToString(message.getData(), base64Flag);

            privateMessageModel.signatureBytes = message.getSignature();
            privateMessageModel.signatureSha256Bytes = message.getSignatureSha256();

            if (message.getDateSendTimed() != null) {
                privateMessageModel.dateSendTimed = eu.ginlo_apps.ginlo.util.DateUtil.getDateFromMillis(message.getDateSendTimed());
            }

            DecryptedMessage decryptedMessage = chatController.decryptMessage(message);
            if (decryptedMessage != null) {
                String contentType = decryptedMessage.getContentType();

                if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(contentType)) {
                    if (contentType.equals(MimeType.APP_OCTET_STREAM)) {
                        privateMessageModel.features = Integer.toString(FeatureVersion.FILE_MSG);
                    } else if (contentType.equals(MimeType.AUDIO_MPEG)) {
                        privateMessageModel.features = Integer.toString(FeatureVersion.VOICEREC);
                    }
                }
            }

            messageModel = privateMessageModel;
        } else if (type == Message.TYPE_PRIVATE_INTERNAL) {
            PrivateInternalMessageModel privateInternalMessageModel = new PrivateInternalMessageModel();

            privateInternalMessageModel.from = new KeyContainerModel(message.getFrom(),
                    Base64.encodeToString(message
                                    .getEncryptedFromKey(),
                            base64Flag), null);
            privateInternalMessageModel.to = new KeyContainerModel[]{new KeyContainerModel(message.getTo(),
                    Base64.encodeToString(message.getEncryptedToKey(), base64Flag),
                    message.getEncryptedToKey2())};
            privateInternalMessageModel.requestGuid = message.getRequestGuid();
            privateInternalMessageModel.data = Base64.encodeToString(message.getData(), base64Flag);

            privateInternalMessageModel.signatureBytes = message.getSignature();
            privateInternalMessageModel.signatureSha256Bytes = message.getSignatureSha256();

            messageModel = privateInternalMessageModel;
        } else if (type == Message.TYPE_GROUP) {
            GroupMessageModel groupMessageModel = new GroupMessageModel();

            groupMessageModel.from = new KeyContainerModel(message.getFrom(),
                    Base64.encodeToString(message.getEncryptedFromKey(),
                            base64Flag), null);
            groupMessageModel.to = message.getTo();

            groupMessageModel.requestGuid = message.getRequestGuid();
            groupMessageModel.data = Base64.encodeToString(message.getData(), base64Flag);

            groupMessageModel.signatureBytes = message.getSignature();

            groupMessageModel.signatureSha256Bytes = message.getSignatureSha256();

            if (message.getDateSendTimed() != null) {
                groupMessageModel.dateSendTimed = eu.ginlo_apps.ginlo.util.DateUtil.getDateFromMillis(message.getDateSendTimed());
            }

            DecryptedMessage decryptedMessage = chatController.decryptMessage(message);
            if (decryptedMessage != null) {
                String contentType = decryptedMessage.getContentType();

                if (eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(contentType)) {
                    if (contentType.equals(MimeType.APP_OCTET_STREAM)) {
                        groupMessageModel.features = Integer.toString(FeatureVersion.FILE_MSG);
                    } else if (contentType.equals(MimeType.AUDIO_MPEG)) {
                        groupMessageModel.features = Integer.toString(FeatureVersion.VOICEREC);
                    }
                }
            }

            messageModel = groupMessageModel;
        }

        if (messageModel != null) {
            messageModel.attachment = message.getAttachment();
            messageModel.messageType = type;
            messageModel.datesend = new Date();
            messageModel.isPriority = message.getIsPriority();
        }

        return messageModel;
    }

    public BaseMessageModel buildLocationMessage(int type,
                                                 byte[] screenshot,
                                                 double longitude,
                                                 double latitude,
                                                 Account fromAccount,
                                                 String fromTempDeviceGuid,
                                                 String fromTempDevicePublicKeyXML,
                                                 String toGuid,
                                                 String toPublicKeyXML,
                                                 String toTempDeviceGuid,
                                                 String toTempDevicePublicKeyXML,
                                                 KeyPair userKeyPair,
                                                 SecretKey aesKey,
                                                 IvParameterSpec iv
    )
            throws LocalizedException {
        BaseMessageModel messageModel = prepareMessage(type, fromAccount, fromTempDeviceGuid, fromTempDevicePublicKeyXML, toGuid, toPublicKeyXML, toTempDeviceGuid, toTempDevicePublicKeyXML, userKeyPair, aesKey, iv, null, false);

        messageModel.mimeType = MimeType.MODEL_LOCATION;

        attachLocation(messageModel, screenshot, longitude, latitude);

        encryptData(messageModel, aesKey, iv);
        attachSignature(messageModel, userKeyPair.getPrivate());
        attachSignature(messageModel, userKeyPair.getPrivate(), true);

        return messageModel;
    }

    public BaseMessageModel buildVCardMessage(final int type,
                                              final String vCard,
                                              final Account fromAccount,
                                              final String fromTempDeviceGuid,
                                              final String fromTempDevicePublicKeyXML,
                                              final String toGuid,
                                              final String toPublicKeyXML,
                                              final String toTempDeviceGuid,
                                              final String toTempDevicePublicKeyXML,
                                              final KeyPair userKeyPair,
                                              final SecretKey aesKey,
                                              final IvParameterSpec iv,
                                              final String accountID,
                                              final String accountGuid

    )
            throws LocalizedException {
        final BaseMessageModel messageModel = prepareMessage(type,
                fromAccount,
                fromTempDeviceGuid,
                fromTempDevicePublicKeyXML,
                toGuid,
                toPublicKeyXML,
                toTempDeviceGuid,
                toTempDevicePublicKeyXML,
                userKeyPair,
                aesKey,
                iv,
                null,
                false);

        messageModel.mimeType = MimeType.TEXT_V_CARD;

        attachVCard(messageModel, vCard);

        attachSendContactInfo(messageModel, accountID, accountGuid);

        encryptData(messageModel, aesKey, iv);
        attachSignature(messageModel, userKeyPair.getPrivate());
        attachSignature(messageModel, userKeyPair.getPrivate(), true);

        return messageModel;
    }

    public BaseMessageModel buildFileMessage(int type,
                                             Activity activity,
                                             Uri fileUri,
                                             String fileName,
                                             String description,
                                             String mimeType,
                                             Account fromAccount,
                                             String fromTempDeviceGuid,
                                             String fromTempDevicePublicKeyXML,
                                             String toGuid,
                                             String toPublicKeyXML,
                                             String toTempDeviceGuid,
                                             String toTempDevicePublicKeyXML,
                                             KeyPair userKeyPair,
                                             SecretKey aesKey,
                                             IvParameterSpec iv,
                                             CitationModel citation
    )
            throws LocalizedException {
        BaseMessageModel messageModel = prepareMessage(type, fromAccount, fromTempDeviceGuid, fromTempDevicePublicKeyXML, toGuid, toPublicKeyXML, toTempDeviceGuid, toTempDevicePublicKeyXML, userKeyPair, aesKey, iv, null, false);

        messageModel.mimeType = MimeType.APP_OCTET_STREAM;

        attachFile(activity, messageModel, fileUri, fileName, description, mimeType, aesKey, iv);
        attachCitation(messageModel, citation);

        encryptData(messageModel, aesKey, iv);
        attachSignature(messageModel, userKeyPair.getPrivate());
        attachSignature(messageModel, userKeyPair.getPrivate(), true);

        return messageModel;
    }

    public BaseMessageModel buildImageMessage(int type,
                                              Activity activity,
                                              Uri imageUri,
                                              String description,
                                              Account fromAccount,
                                              String fromTempDeviceGuid,
                                              String fromTempDevicePublicKeyXML,
                                              String toGuid,
                                              String toPublicKeyXML,
                                              String toTempDeviceGuid,
                                              String toTempDevicePublicKeyXML,
                                              KeyPair userKeyPair,
                                              SecretKey aesKey,
                                              IvParameterSpec iv,
                                              MessageDestructionParams destructionParams,
                                              Date dateSendTimed,
                                              boolean isPriority,
                                              CitationModel citation
    )
            throws LocalizedException {
        BaseMessageModel messageModel = prepareMessage(type, fromAccount, fromTempDeviceGuid, fromTempDevicePublicKeyXML, toGuid, toPublicKeyXML, toTempDeviceGuid, toTempDevicePublicKeyXML, userKeyPair, aesKey, iv, dateSendTimed, isPriority);

        messageModel.mimeType = MimeType.IMAGE_JPEG;

        try {
            attachImage(activity, messageModel, imageUri, description, aesKey, iv);
        } catch (LocalizedException e) {
            // Bug 37967 - Datei war zu gross
            if (LocalizedException.FILE_TO_BIG_AFTER_COMPRESSION.equals(e.getIdentifier())) {
                messageModel.errorIdentifier = e.getIdentifier();
            } else {
                throw e;
            }
        }
        attachCitation(messageModel, citation);
        if (destructionParams != null) {
            attachSelfDestruction(messageModel, destructionParams);
        }

        encryptData(messageModel, aesKey, iv);
        attachSignature(messageModel, userKeyPair.getPrivate());
        attachSignature(messageModel, userKeyPair.getPrivate(), true);

        return messageModel;
    }

    public BaseMessageModel buildVideoMessage(int type,
                                              Activity activity,
                                              Uri imageUri,
                                              String description,
                                              Account fromAccount,
                                              String fromTempDeviceGuid,
                                              String fromTempDevicePublicKeyXML,
                                              String toGuid,
                                              String toPublicKeyXML,
                                              String toTempDeviceGuid,
                                              String toTempDevicePublicKeyXML,
                                              KeyPair userKeyPair,
                                              SecretKey aesKey,
                                              IvParameterSpec iv,
                                              MessageDestructionParams destructionParams,
                                              Date dateSendTimed,
                                              boolean isPriority
    )
            throws LocalizedException {
        BaseMessageModel messageModel = prepareMessage(type, fromAccount, fromTempDeviceGuid, fromTempDevicePublicKeyXML, toGuid, toPublicKeyXML, toTempDeviceGuid, toTempDevicePublicKeyXML, userKeyPair, aesKey, iv, dateSendTimed, isPriority);

        messageModel.mimeType = MimeType.VIDEO_MPEG;

        attachVideo(activity, messageModel, imageUri, description, aesKey, iv);

        if (destructionParams != null) {
            attachSelfDestruction(messageModel, destructionParams);
        }

        encryptData(messageModel, aesKey, iv);
        attachSignature(messageModel, userKeyPair.getPrivate());
        attachSignature(messageModel, userKeyPair.getPrivate(), true);

        return messageModel;
    }

    public BaseMessageModel buildVoiceMessage(int type,
                                              Activity activity,
                                              Uri voiceUri,
                                              Account fromAccount,
                                              String fromTempDeviceGuid,
                                              String fromTempDevicePublicKeyXML,
                                              String toGuid,
                                              String toPublicKeyXML,
                                              String toTempDeviceGuid,
                                              String toTempDevicePublicKeyXML,
                                              KeyPair userKeyPair,
                                              SecretKey aesKey,
                                              IvParameterSpec iv,
                                              MessageDestructionParams destructionParams,
                                              Date dateSendTimed,
                                              boolean isPriority
    )
            throws LocalizedException {
        BaseMessageModel messageModel = prepareMessage(type, fromAccount, fromTempDeviceGuid, fromTempDevicePublicKeyXML, toGuid, toPublicKeyXML, toTempDeviceGuid, toTempDevicePublicKeyXML, userKeyPair, aesKey, iv, dateSendTimed, isPriority);

        messageModel.mimeType = MimeType.AUDIO_MPEG;

        attachVoice(activity, messageModel, voiceUri, aesKey, iv);

        if (destructionParams != null) {
            attachSelfDestruction(messageModel, destructionParams);
        }

        encryptData(messageModel, aesKey, iv);
        attachSignature(messageModel, userKeyPair.getPrivate());
        attachSignature(messageModel, userKeyPair.getPrivate(), true);

        return messageModel;
    }

    public GroupInvMessageModel buildGroupInviteMessage(String groupGuid,
                                                        String groupName,
                                                        String roomType,
                                                        byte[] groupImageBytes,
                                                        Account fromAccount,
                                                        String toGuid,
                                                        String toPublicKeyXML,
                                                        String toTempDeviceGuid,
                                                        String toTempDevicePublicKeyXML,
                                                        KeyPair userKeyPair,
                                                        SecretKey aesKey,
                                                        IvParameterSpec iv,
                                                        Date dateSendTimed
    )
            throws LocalizedException {
        // TODO : Gruppeneinladungen an Temp Geräte senden
        GroupInvMessageModel groupInviteMessageModel = null;

        if (toPublicKeyXML != null) {
            groupInviteMessageModel = (GroupInvMessageModel) createPrivateInternalMessageModelBase(new GroupInvMessageModel(),
                    fromAccount,
                    toGuid,
                    toPublicKeyXML,
                    toTempDeviceGuid,
                    toTempDevicePublicKeyXML,
                    userKeyPair,
                    aesKey, iv, dateSendTimed);

            attachGroupInvitation(groupGuid, groupName, roomType, groupImageBytes, groupInviteMessageModel);
            encryptData(groupInviteMessageModel, aesKey, iv);
            attachSignature(groupInviteMessageModel, userKeyPair.getPrivate());
            attachSignature(groupInviteMessageModel, userKeyPair.getPrivate(), true);
            groupInviteMessageModel.mimeType = "groupInvite";
            groupInviteMessageModel.messageType = Message.TYPE_GROUP_INVITATION;
            groupInviteMessageModel.dateSendTimed = dateSendTimed;
        }
        return groupInviteMessageModel;
    }

    public PrivateInternalMessageModel buildPrivateInternalMessage(String action,
                                                                   Account fromAccount,
                                                                   String toGuid,
                                                                   String toPublicKeyXML,
                                                                   KeyPair userKeyPair,
                                                                   SecretKey aesKey,
                                                                   IvParameterSpec iv,
                                                                   Date dateSendTimed)
            throws LocalizedException {
        PrivateInternalMessageModel privateInternalMessageModel = (PrivateInternalMessageModel)
                createPrivateInternalMessageModelBase(new PrivateInternalMessageModel(),
                        fromAccount,
                        toGuid,
                        toPublicKeyXML,
                        null,
                        null,
                        userKeyPair,
                        aesKey,
                        iv,
                        dateSendTimed);

        privateInternalMessageModel.mimeType = "internal";

        attachAction(action, privateInternalMessageModel);
        encryptData(privateInternalMessageModel, aesKey, iv);
        attachSignature(privateInternalMessageModel, userKeyPair.getPrivate());
        attachSignature(privateInternalMessageModel, userKeyPair.getPrivate(), true);
        privateInternalMessageModel.messageType = Message.TYPE_PRIVATE_INTERNAL;

        return privateInternalMessageModel;
    }

    private void attachAction(String action,
                              PrivateInternalMessageModel privateInternalMessageModel) {
        JsonObject actionContainer = getDataJsonObject(privateInternalMessageModel);

        actionContainer.addProperty(DataContainer.CONTENT, action);
        actionContainer.addProperty(DataContainer.CONTENT_TYPE, MimeType.TEXT_PLAIN);

        privateInternalMessageModel.data = actionContainer.toString();
    }

    private void attachLocation(BaseMessageModel messageModel,
                                byte[] screenshot,
                                double longitude,
                                double latitude) {
        JsonObject dataJson = getDataJsonObject(messageModel);

        JsonObject contentJson = new JsonObject();

        contentJson.addProperty(DataContainer.LOCATION_CONTAINER_PREVIEW,
                Base64.encodeToString(screenshot, Base64.DEFAULT));
        contentJson.addProperty(DataContainer.LOCATION_CONTAINER_LONGITUDE, longitude);
        contentJson.addProperty(DataContainer.LOCATION_CONTAINER_LATITUDE, latitude);
        dataJson.addProperty(DataContainer.CONTENT, contentJson.toString());
        dataJson.addProperty(DataContainer.CONTENT_TYPE, MimeType.MODEL_LOCATION);

        messageModel.data = dataJson.toString();
    }

    private void attachContactInfo(Account fromAccount, BaseMessageModel messageModel)
            throws LocalizedException {
        JsonObject dataJson = getDataJsonObject(messageModel);

        String nickname = (fromAccount.getName() != null) ? fromAccount.getName() : fromAccount.getAccountGuid();

        dataJson.addProperty(DataContainer.NICKNAME, nickname);
        dataJson.addProperty(DataContainer.PHONE, eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(fromAccount.getAccountID()) ? "N/A" : fromAccount.getAccountID());

        if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(fromAccount.getAccountInfosAesKey())) {
            dataJson.addProperty(DataContainer.PROFIL_KEY, fromAccount.getAccountInfosAesKey());
        }

        messageModel.data = dataJson.toString();
    }

    private void attachSendContactInfo(final BaseMessageModel messageModel,
                                       final String accountID,
                                       final String accountGuid) {
        if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(accountGuid) && !eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(accountID)) {
            JsonObject dataJson = getDataJsonObject(messageModel);
            dataJson.addProperty(DataContainer.ACCOUNT_ID, accountID);
            dataJson.addProperty(DataContainer.ACCOUNT_GUID, accountGuid);
            messageModel.data = dataJson.toString();
        }
    }

    private void attachSelfDestruction(BaseMessageModel messageModel,
                                       MessageDestructionParams destructionParams) {
        JsonObject dataJson = getDataJsonObject(messageModel);

        if (destructionParams.countdown != null) {
            dataJson.addProperty(DataContainer.DESTRUCTION_COUNTDOWN, destructionParams.countdown);
        }
        if (destructionParams.date != null) {
            dataJson.addProperty(DataContainer.DESTRUCTION_DATE, eu.ginlo_apps.ginlo.util.DateUtil.dateToUtcString(destructionParams.date));
        }

        messageModel.data = dataJson.toString();
        if (eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(messageModel.mimeType)) {
            messageModel.mimeType = "/selfdest";
        } else {
            messageModel.mimeType += "/selfdest";
        }
    }

    private void attachCitation(final BaseMessageModel messageModel, final CitationModel citationModel) {
        if (citationModel == null) {
            return;
        }
        final JsonObject dataJson = getDataJsonObject(messageModel);

        final JsonObject citationJsonObject = new JsonObject();

        if (citationModel.msgGuid != null) {
            citationJsonObject.addProperty("msgGuid", citationModel.msgGuid);
        }

        if (citationModel.contentType != null) {
            citationJsonObject.addProperty("Content-Type", citationModel.contentType);
        }

        if (citationModel.contentDesc != null) {
            citationJsonObject.addProperty("Content-Desc", citationModel.contentDesc);
        }

        if (citationModel.toGuid != null) {
            citationJsonObject.addProperty("toGuid", citationModel.toGuid);
        }

        //Fill content
        if (eu.ginlo_apps.ginlo.util.StringUtil.isEqual(citationModel.contentType, MimeType.TEXT_PLAIN)
                || eu.ginlo_apps.ginlo.util.StringUtil.isEqual(citationModel.contentType, MimeType.TEXT_RSS)) {
            citationJsonObject.addProperty(DataContainer.CONTENT, citationModel.text);
        } else if (eu.ginlo_apps.ginlo.util.StringUtil.isEqual(citationModel.contentType, MimeType.VIDEO_MPEG)
                || eu.ginlo_apps.ginlo.util.StringUtil.isEqual(citationModel.contentType, MimeType.IMAGE_JPEG)
        ) {
            final byte[] previewImageBytes = eu.ginlo_apps.ginlo.util.BitmapUtil.compress(citationModel.previewImage, 60);
            final String previewImageBase64 = Base64.encodeToString(previewImageBytes, Base64.DEFAULT);
            citationJsonObject.addProperty(DataContainer.CONTENT, previewImageBase64);
        } else if (eu.ginlo_apps.ginlo.util.StringUtil.isEqual(citationModel.contentType, MimeType.MODEL_LOCATION)) {
            final JsonObject content = new JsonObject();

            final byte[] previewImageBytes = eu.ginlo_apps.ginlo.util.BitmapUtil.compress(citationModel.previewImage, 60);

            content.addProperty(DataContainer.LOCATION_CONTAINER_PREVIEW,
                    Base64.encodeToString(previewImageBytes, Base64.DEFAULT));

            //TODO falls benoetigt, die Koordinaten aus urspruenglicher Message suchen und mitschicken - ist aber bisher unnoetig
            citationJsonObject.addProperty(DataContainer.CONTENT, content.toString());
        } else {
            citationJsonObject.addProperty(DataContainer.CONTENT, "");
        }

        if (citationModel.nickname != null) {
            citationJsonObject.addProperty("Nickname", citationModel.nickname);
        } else {
            citationJsonObject.addProperty("Nickname", "");
        }

        if (citationModel.datesend != null) {
            final Date date = new Date();
            date.setTime(citationModel.datesend);
            citationJsonObject.addProperty("datesend", DateUtil.dateToUtcStringWithoutMillis(date));
        }

        if (citationModel.fromGuid != null) {
            citationJsonObject.addProperty("fromGuid", citationModel.fromGuid);
        }

        dataJson.add(DataContainer.CITATION, citationJsonObject);

        if (eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(messageModel.mimeType)) {
            messageModel.mimeType = "/citation";
        } else {
            messageModel.mimeType += "/citation";
        }
        messageModel.data = dataJson.toString();
    }

    private void attachText(BaseMessageModel messageModel,
                            String message) {
        JsonObject dataJson = getDataJsonObject(messageModel);

        dataJson.addProperty(DataContainer.CONTENT, message);
        dataJson.addProperty(DataContainer.CONTENT_TYPE, MimeType.TEXT_PLAIN);

        messageModel.data = dataJson.toString();
    }

    private void attachAppGinloControlMessage(BaseMessageModel messageModel,
                                              AppGinloControlMessage controlMessage) {
        JsonObject dataJson = getDataJsonObject(messageModel);

        dataJson.addProperty(DataContainer.CONTENT, controlMessage.toString());
        dataJson.addProperty(DataContainer.CONTENT_TYPE, MimeType.APP_GINLO_CONTROL);

        messageModel.data = dataJson.toString();
    }
    private void attachAVCRoomInfo(BaseMessageModel messageModel,
                            String roomInfo) {
        JsonObject dataJson = getDataJsonObject(messageModel);

        dataJson.addProperty(DataContainer.CONTENT, roomInfo);
        dataJson.addProperty(DataContainer.CONTENT_TYPE, MimeType.TEXT_V_CALL);

        messageModel.data = dataJson.toString();
    }
    private void attachVCard(BaseMessageModel messageModel,
                             String vCard) {
        JsonObject dataJson = getDataJsonObject(messageModel);

        dataJson.addProperty(DataContainer.CONTENT, vCard);
        dataJson.addProperty(DataContainer.CONTENT_TYPE, MimeType.TEXT_V_CARD);

        messageModel.data = dataJson.toString();
    }

    private void attachFile(final Activity activity,
                            final BaseMessageModel messageModel,
                            final Uri fileUri,
                            final String aFileName,
                            final String description,
                            final String mimeType,
                            final SecretKey aesKey,
                            final IvParameterSpec iv)
            throws LocalizedException {
        JsonObject dataJson = getDataJsonObject(messageModel);

        final eu.ginlo_apps.ginlo.util.FileUtil fu = new FileUtil(activity);
        long fileSize;
        try {
            fileSize = fu.getFileSize(fileUri);
        } catch (LocalizedException e) {
            fileSize = 0;
            LogUtil.e(this.getClass().getSimpleName(), e.getMessage(), e);
        }
        String fileName = aFileName;

        dataJson.addProperty(DataContainer.CONTENT_TYPE, MimeType.APP_OCTET_STREAM);

        if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(fileName)) {
            if (fileName.lastIndexOf(".") < 0) {
                String ext = fu.getExtensionForUri(fileUri);
                if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(ext)) {
                    fileName = fileName + "." + ext;
                }
            }
            dataJson.addProperty(DataContainer.FILE_NAME, fileName);
        }

        if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(description)) {
            dataJson.addProperty(DataContainer.CONTENT_DESC, description);
        }

        if (fileSize > 0) {
            dataJson.addProperty(DataContainer.FILE_SIZE, fileSize);
        }

        if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(mimeType)) {
            dataJson.addProperty(DataContainer.FILE_TYPE, mimeType);
        } else {
            dataJson.addProperty(DataContainer.FILE_TYPE, MimeType.APP_OCTET_STREAM);
        }

        dataJson.addProperty(DataContainer.ENCODING_VERSION, DecryptedMessage.ATTACHMENT_ENCODING_VERSION_1);

        byte[] bytes = fu.getByteArrayFromUri(fileUri);

        if (bytes == null) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "File has no data.");
        }

        if (fileSize <= 0 && bytes.length > 0) {
            dataJson.addProperty(DataContainer.FILE_SIZE, bytes.length);
        }

        messageModel.data = dataJson.toString();
        messageModel.features = Integer.toString(FeatureVersion.FILE_MSG);

        addAttachment(bytes, messageModel, aesKey, iv);
    }

    private void attachImage(final Activity activity,
                             final BaseMessageModel messageModel,
                             final Uri imageUri,
                             final String description,
                             final SecretKey aesKey,
                             final IvParameterSpec iv)
            throws LocalizedException {
        final JsonObject dataJson = getDataJsonObject(messageModel);

        final PreferencesController preferencesController = ((SimsMeApplication) activity.getApplication())
                .getPreferencesController();

        final int quality = preferencesController.getImageQuality();
        final int imageWidth;
        final int imageHeight;
        final int imageCompressionRatio;

        switch (quality) {
            case 0: {
                imageWidth = activity.getResources().getInteger(R.integer.image_width_s);
                imageHeight = activity.getResources().getInteger(R.integer.image_heigth_s);
                imageCompressionRatio = activity.getResources().getInteger(R.integer.image_compression_percent_s);
                break;
            }
            case 1: {
                imageWidth = activity.getResources().getInteger(R.integer.image_width_m);
                imageHeight = activity.getResources().getInteger(R.integer.image_heigth_m);
                imageCompressionRatio = activity.getResources().getInteger(R.integer.image_compression_percent_m);
                break;
            }
            case 2: {
                imageWidth = activity.getResources().getInteger(R.integer.image_width_l);
                imageHeight = activity.getResources().getInteger(R.integer.image_heigth_l);
                imageCompressionRatio = activity.getResources().getInteger(R.integer.image_compression_percent_l);
                break;
            }
            case 3: {
                imageWidth = activity.getResources().getInteger(R.integer.image_width_xl);
                imageHeight = activity.getResources().getInteger(R.integer.image_heigth_xl);
                imageCompressionRatio = activity.getResources().getInteger(R.integer.image_compression_percent_xl);
                break;
            }
            default: {
                imageWidth = activity.getResources().getInteger(R.integer.image_width_m);
                imageHeight = activity.getResources().getInteger(R.integer.image_heigth_m);
                imageCompressionRatio = activity.getResources().getInteger(R.integer.image_compression_percent_m);
                break;
            }
        }

        final Bitmap image = eu.ginlo_apps.ginlo.util.BitmapUtil.decodeUri(activity, imageUri, imageWidth, imageHeight, true);

        if (image == null) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "image is null");
        }

        /*
         * SGA, 05.01.2017, SVN Rev 174513
         * iOS kann mit Rohdaten nicht so gut umgehen und erwartet Preview-Bilder der Groesze 220x294.
         * Bei auch nur einem Pixel mehr wird auf iOS das Bild halbiert und kleiner dargestellt.
         * Aus diesem Grund muessen die Bilder - je nach Ausrichtung - croppen, skalieren
         * und so verschieben, dass das Preview mittig angezeigt wird
         *
         */
        final int height = image.getHeight();
        final int width = image.getWidth();
        final double previewRatio = 1.3364; // 294/220

        final int iOSMagicNumberWidth = 220;
        final int iOSMagicNumberHeight = 294;
        final int previewHeighRelativeToWidth = 393; // 294 * 1.3364

        Bitmap previewImage;

        try {

            if (width > height) //landscape
            {
                final double imageRatio = (double) width / (double) height;

                final double delta = previewRatio - imageRatio;

                final int xOffsetPreview = 86; // (393 - 220) / 2

                if (delta > 0.01) // previewRatio > imageRatio
                {
                    final int newHeight = (int) ((double) width / previewRatio);

                    previewImage = Bitmap.createBitmap(image, 0, (height - newHeight) / 2, width, newHeight);
                    previewImage = Bitmap.createScaledBitmap(previewImage, previewHeighRelativeToWidth, iOSMagicNumberHeight, true);
                    previewImage = Bitmap.createBitmap(previewImage, xOffsetPreview, 0, iOSMagicNumberWidth, iOSMagicNumberHeight);
                } else if (delta < -0.01) // previewRatio < imageRatio
                {
                    final int newWidth = (int) ((double) height * previewRatio);
                    previewImage = Bitmap.createBitmap(image, (width - newWidth) / 2, 0, newWidth, height);
                    previewImage = Bitmap.createScaledBitmap(previewImage, previewHeighRelativeToWidth, iOSMagicNumberHeight, true);
                    previewImage = Bitmap.createBitmap(previewImage, xOffsetPreview, 0, iOSMagicNumberWidth, iOSMagicNumberHeight);
                } else {
                    previewImage = Bitmap.createScaledBitmap(image, previewHeighRelativeToWidth, iOSMagicNumberHeight, true);
                    previewImage = Bitmap.createBitmap(previewImage, xOffsetPreview, 0, iOSMagicNumberWidth, iOSMagicNumberHeight);
                }
            } else //portrait
            {
                // todo SGA: hier sind width und height vertauscht, Grund unklar
                final int newHeight = (int) ((double) width / previewRatio);
                previewImage = Bitmap.createBitmap(image, (width - newHeight) / 2, (height - width) / 2, newHeight, width);
                previewImage = Bitmap.createScaledBitmap(previewImage, iOSMagicNumberWidth, iOSMagicNumberHeight, true);
            }
        } catch (final IllegalArgumentException e) {
            LogUtil.w(this.getClass().getName(), e.getMessage(), e);
            /*
             * SGA: falls irgendwo oben die Werte doch nicht passen (sollte nicht passieren), hier der Fallback
             */
            if (width >= iOSMagicNumberWidth && height >= iOSMagicNumberHeight) {
                previewImage = Bitmap.createBitmap(image, 0, 0, iOSMagicNumberWidth, iOSMagicNumberHeight);
            } else {
                previewImage = Bitmap.createBitmap(image, 0, 0, width, height);
            }
        }

        if (previewImage == null) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND);
        }

        final byte[] previewImageBytes = eu.ginlo_apps.ginlo.util.BitmapUtil.compress(previewImage, imageCompressionRatio);

        final byte[] imageBytes = eu.ginlo_apps.ginlo.util.BitmapUtil.compress(image, imageCompressionRatio);

        previewImage.recycle();
        image.recycle();

        // Bug 37967 Bilder sollen erts bei Kompression geprueft werden
        if (imageBytes.length > activity.getApplication().getResources().getInteger(R.integer.attachment_file_max_size)) {
            final String errorMsg = activity.getApplication().getResources().getString(R.string.chats_addAttachment_too_big);
            throw new LocalizedException(LocalizedException.FILE_TO_BIG_AFTER_COMPRESSION, errorMsg);
        }

        final String previewImageBase64 = Base64.encodeToString(previewImageBytes, Base64.DEFAULT);

        dataJson.addProperty(DataContainer.CONTENT_TYPE, MimeType.IMAGE_JPEG);
        dataJson.addProperty(DataContainer.CONTENT, previewImageBase64);
        dataJson.addProperty(DataContainer.ENCODING_VERSION, DecryptedMessage.ATTACHMENT_ENCODING_VERSION_1);
        if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(description)) {
            dataJson.addProperty(DataContainer.CONTENT_DESC, description);
        }

        messageModel.data = dataJson.toString();

        addAttachment(imageBytes, messageModel, aesKey, iv);
    }

    private void attachVideo(Activity activity,
                             BaseMessageModel messageModel,
                             Uri videoUri,
                             String description,
                             SecretKey aesKey,
                             IvParameterSpec iv)
            throws LocalizedException {
        JsonObject dataJson = getDataJsonObject(messageModel);

        Bitmap previewImage = eu.ginlo_apps.ginlo.util.VideoUtil.getThumbnail(activity, videoUri);

        byte[] fileBytes = VideoUtil.decodeUri(activity, videoUri);
        byte[] previewImageBytes = BitmapUtil.compress(previewImage, 100);

        String previewImageBase64 = Base64.encodeToString(previewImageBytes, Base64.DEFAULT);

        previewImage.recycle();

        dataJson.addProperty(DataContainer.CONTENT_TYPE, MimeType.VIDEO_MPEG);
        dataJson.addProperty(DataContainer.CONTENT, previewImageBase64);
        dataJson.addProperty(DataContainer.ENCODING_VERSION, DecryptedMessage.ATTACHMENT_ENCODING_VERSION_1);
        if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(description)) {
            dataJson.addProperty(DataContainer.CONTENT_DESC, description);
        }

        messageModel.data = dataJson.toString();

        addAttachment(fileBytes, messageModel, aesKey, iv);
    }

    private void attachVoice(Activity activity,
                             BaseMessageModel messageModel,
                             Uri voiceUri,
                             SecretKey aesKey,
                             IvParameterSpec iv)
            throws LocalizedException {
        JsonObject dataJson = getDataJsonObject(messageModel);
        JsonObject contentJson = new JsonObject();

        contentJson.addProperty(DataContainer.VOICE_CONTAINER_DURATION, eu.ginlo_apps.ginlo.util.AudioUtil.getDuration(activity, voiceUri));
        contentJson.add(DataContainer.VOICE_CONTAINER_WAVEFORM,
                jsonParser.parse(gson.toJson(eu.ginlo_apps.ginlo.util.AudioUtil.getLevels())));

        dataJson.addProperty(DataContainer.CONTENT, contentJson.toString());
        dataJson.addProperty(DataContainer.CONTENT_TYPE, MimeType.AUDIO_MPEG);
        dataJson.addProperty(DataContainer.ENCODING_VERSION, DecryptedMessage.ATTACHMENT_ENCODING_VERSION_1);

        byte[] fileBytes = AudioUtil.decodeUri(activity, voiceUri);

        messageModel.data = dataJson.toString();
        messageModel.features = Integer.toString(FeatureVersion.VOICEREC);

        addAttachment(fileBytes, messageModel, aesKey, iv);
    }

    private void attachGroupInvitation(String groupGuid,
                                       String groupName,
                                       String roomType,
                                       byte[] groupImageBytes,
                                       GroupInvMessageModel groupInviteMessage) {
        JsonObject dataJson = getDataJsonObject(groupInviteMessage);

        if (groupImageBytes != null) {
            String groupImageBase64 = Base64.encodeToString(groupImageBytes, Base64.DEFAULT);

            dataJson.addProperty(DataContainer.GROUP_IMAGE, groupImageBase64);
        }

        dataJson.addProperty(DataContainer.GROUP_GUID, groupGuid);
        dataJson.addProperty(DataContainer.GROUP_NAME, groupName);
        dataJson.addProperty(DataContainer.GROUP_TYPE, roomType);
        dataJson.addProperty(DataContainer.ROOM_TYPE, roomType);
        dataJson.addProperty(DataContainer.CONTENT_TYPE, AppConstants.MODEL_INVITATION);

        groupInviteMessage.data = dataJson.toString();
    }

    private void attachSignature(BaseMessageModel messageModel,
                                 PrivateKey key)
            throws LocalizedException {
        attachSignature(messageModel, key, false);
    }

    private void attachSignature(BaseMessageModel messageModel,
                                 PrivateKey key,
                                 boolean useSha256
    )
            throws LocalizedException {
        SignatureModel signature = new SignatureModel();

        signature.initWithMessage(messageModel, useSha256);

        {
            String concatSignatureString = signature.getCombinedHashes(false);

            if (useSha256) {
                byte[] signatureSha256Data = eu.ginlo_apps.ginlo.util.SecurityUtil.signData(key, concatSignatureString.getBytes(StandardCharsets.UTF_8), true);

                signature.setSignature(Base64.encodeToString(signatureSha256Data, Base64.DEFAULT));

                messageModel.signatureSha256Bytes = signature.getModel(false).toString().getBytes(StandardCharsets.UTF_8);
            } else {
                byte[] signatureData = eu.ginlo_apps.ginlo.util.SecurityUtil.signData(key, concatSignatureString.getBytes(StandardCharsets.UTF_8), false);
                signature.setSignature(Base64.encodeToString(signatureData, Base64.DEFAULT));

                messageModel.signatureBytes = signature.getModel(false).toString().getBytes(StandardCharsets.UTF_8);
            }
        }

        if (signature.hasTempDeviceInfo()) {
            String concatSignatureString = signature.getCombinedHashes(true);

            if (useSha256) {
                byte[] signatureSha256Data = eu.ginlo_apps.ginlo.util.SecurityUtil.signData(key, concatSignatureString.getBytes(StandardCharsets.UTF_8), true);
                signature.setSignatureWithTempInfo(Base64.encodeToString(signatureSha256Data, Base64.DEFAULT));

                messageModel.signatureTempSha256Bytes = signature.getModel(true).toString().getBytes(StandardCharsets.UTF_8);
            } else {
                byte[] signatureData = eu.ginlo_apps.ginlo.util.SecurityUtil.signData(key, concatSignatureString.getBytes(StandardCharsets.UTF_8), false);
                signature.setSignatureWithTempInfo(Base64.encodeToString(signatureData, Base64.DEFAULT));

                messageModel.signatureTempBytes = signature.getModel(true).toString().getBytes(StandardCharsets.UTF_8);
            }
        }
    }

    private void addAttachment(final byte[] attachmentData,
                               final BaseMessageModel messageModel,
                               final SecretKey aesKey,
                               final IvParameterSpec ivParameterSpec)
            throws LocalizedException {
        if (eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(messageModel.requestGuid)) {
            return;
        }

        if (messageModel instanceof GroupMessageModel) {
            final IvParameterSpec groupIv = eu.ginlo_apps.ginlo.util.SecurityUtil.generateIV();

            byte[] ivBytes = groupIv.getIV();

            byte[] encryptedBytes = eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithAES(attachmentData, aesKey, groupIv);
            byte[] payloadBytes = new byte[ivBytes.length + encryptedBytes.length];

            System.arraycopy(ivBytes, 0, payloadBytes, 0, ivBytes.length);
            System.arraycopy(encryptedBytes, 0, payloadBytes, ivBytes.length, encryptedBytes.length);

            //TODO für 1.8. bitte behalten
            AttachmentController.saveEncryptedMessageAttachmentAsBase64File(payloadBytes, messageModel.requestGuid);
            messageModel.attachment = messageModel.requestGuid;
        } else {
            byte[] encryptedBytes = eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithAES(attachmentData, aesKey, ivParameterSpec);

            //TODO für 1.8. bitte behalten
            AttachmentController.saveEncryptedMessageAttachmentAsBase64File(encryptedBytes, messageModel.requestGuid);
            messageModel.attachment = messageModel.requestGuid;
        }
    }

    private void encryptData(BaseMessageModel messageModel,
                             SecretKey aesKey,
                             IvParameterSpec iv)
            throws LocalizedException {
        JsonObject dataJson = getDataJsonObject(messageModel);

        byte[] encryptedMessage = eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithAES(dataJson.toString().getBytes(StandardCharsets.UTF_8),
                aesKey, iv);
        String encodedMessage;

        if (messageModel instanceof GroupMessageModel) {
            byte[] ivBytes = iv.getIV();
            byte[] payloadBytes = new byte[ivBytes.length + encryptedMessage.length];

            System.arraycopy(ivBytes, 0, payloadBytes, 0, ivBytes.length);
            System.arraycopy(encryptedMessage, 0, payloadBytes, ivBytes.length, encryptedMessage.length);
            encodedMessage = Base64.encodeToString(payloadBytes, Base64.NO_WRAP);
        } else {
            encodedMessage = Base64.encodeToString(encryptedMessage, Base64.NO_WRAP);
        }

        messageModel.data = encodedMessage;
    }

    private GroupMessageModel createGroupMessageModelBase(Account fromAccount,
                                                          String groupGuid,
                                                          KeyPair userKeyPair,
                                                          SecretKey aesKey,
                                                          IvParameterSpec iv,
                                                          Date dateSendTimed,
                                                          boolean isPriority
    )
            throws LocalizedException {
        GroupMessageModel groupMessage = new GroupMessageModel();

        byte[] aesKeyBytes = aesKey.getEncoded();
        byte[] ivBytes = iv.getIV();

        JsonObject keyJson = new JsonObject();

        keyJson.addProperty("key", Base64.encodeToString(aesKeyBytes, Base64.NO_WRAP));
        keyJson.addProperty("iv", Base64.encodeToString(ivBytes, Base64.NO_WRAP));

        byte[] keyContainerBytes = IOSMessageConversionUtil.convertJsonToXML(keyJson).getBytes(StandardCharsets.UTF_8);

        byte[] fromEncryptedKeyBytes = eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithRSA(keyContainerBytes, userKeyPair.getPublic());

        String fromEncodedEncryptedKey = Base64.encodeToString(fromEncryptedKeyBytes, Base64.NO_WRAP);

        groupMessage.from = new KeyContainerModel(fromAccount.getAccountGuid(), fromEncodedEncryptedKey, null);
        groupMessage.to = groupGuid;
        groupMessage.datesend = new Date();
        groupMessage.dateSendTimed = dateSendTimed;
        groupMessage.isPriority = isPriority;
        attachContactInfo(fromAccount, groupMessage);

        return groupMessage;
    }

    private PrivateMessageModel createPrivateMessageModelBase(final PrivateMessageModel privateMessage,
                                                              final Account fromAccount,
                                                              final String fromTempDeviceGuid,
                                                              final String fromTempDevicePublicKeyXML,
                                                              final String toGuid,
                                                              final String toPublicKeyXML,
                                                              final String toTempDeviceGuid,
                                                              final String toTempDevicePublicKeyXML,
                                                              final KeyPair userKeyPair,
                                                              final SecretKey aesKey,
                                                              final IvParameterSpec iv,
                                                              final Date dateSendTimed,
                                                              final boolean isPriority
    )
            throws LocalizedException {

        Contact contact = mContactController.getContactByGuid(toGuid);

        PublicKey toPublicKey;
        if (contact != null) {
            // toGuid = contact.getAccountGuid(); //SGA: auskommentiert am 24.08.2017
            toPublicKey = getToPublicKey(contact.getPublicKey());
        } else {
            toPublicKey = getToPublicKey(toPublicKeyXML);
        }

        // Bei temporären Devices nicht die AES - Schlüssel wiederverwenden
        if (toTempDeviceGuid != null) {
            contact = null;
        }

        // TODO: Correct signature!
        byte[] aesKeyBytes = aesKey.getEncoded();
        byte[] ivBytes = iv.getIV();
        String ivString = Base64.encodeToString(ivBytes, Base64.NO_WRAP);

        JsonObject keyJson = new JsonObject();

        String encodedAesKeyBytes = Base64.encodeToString(aesKeyBytes, Base64.NO_WRAP);

        keyJson.addProperty("key", encodedAesKeyBytes);
        keyJson.addProperty("iv", ivString);

        byte[] keyContainerBytes = IOSMessageConversionUtil.convertJsonToXML(keyJson).getBytes(StandardCharsets.UTF_8);
        byte[] fromEncryptedKeyBytes = eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithRSA(keyContainerBytes, userKeyPair.getPublic());
        String fromEncodedEncryptedKey = Base64.encodeToString(fromEncryptedKeyBytes, Base64.NO_WRAP);

        String fromTempDeviceKey = null;
        String fromTempDeviceKey2 = null;

        if (fromTempDevicePublicKeyXML != null) {
            PublicKey tempDevicePublicKey = getToPublicKey(fromTempDevicePublicKeyXML);
            fromTempDeviceKey = Base64.encodeToString(eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithRSA(keyContainerBytes, tempDevicePublicKey), Base64.NO_WRAP);
            fromTempDeviceKey2 = Base64.encodeToString(eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithRSA(encodedAesKeyBytes.getBytes(StandardCharsets.UTF_8), tempDevicePublicKey), Base64.NO_WRAP);
        }

        String contactFromKey2;
        String contactToKey2;
        if (contact != null) {
            boolean changed = false;
            // fromkey2 aus Kontakt holen oder ggf in Kontact speichern
            contactFromKey2 = contact.getFromKey2();
            if (eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(contactFromKey2)) {
                byte[] fromKey2Bytes = eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithRSA(encodedAesKeyBytes.getBytes(StandardCharsets.UTF_8), userKeyPair.getPublic());
                contactFromKey2 = Base64.encodeToString(fromKey2Bytes, Base64.NO_WRAP);
                contact.setFromKey2(contactFromKey2);
                changed = true;
            }

            // tokey2 aus Kontakt holen oder ggf in Kontact speichern
            contactToKey2 = contact.getToKey2();
            if (eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(contactToKey2)) {
                // aes key mit contact-schluessel vershcluesseln
                byte[] toKey2Bytes = eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithRSA(encodedAesKeyBytes.getBytes(StandardCharsets.UTF_8), toPublicKey);
                contactToKey2 = Base64.encodeToString(toKey2Bytes, Base64.NO_WRAP);
                contact.setToKey2(contactToKey2);
                changed = true;
            }
            if (changed) {
                mContactController.insertOrUpdateContact(contact);
            }
        } else {
            contactFromKey2 = null;
            // aes key mit contact-schluessel vershcluesseln
            byte[] toKey2Bytes = eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithRSA(encodedAesKeyBytes.getBytes(StandardCharsets.UTF_8), toPublicKey);
            contactToKey2 = Base64.encodeToString(toKey2Bytes, Base64.NO_WRAP);
        }

        byte[] toEncryptedKeyBytes = eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithRSA(keyContainerBytes, toPublicKey);
        String toEncodedEncryptedKey = Base64.encodeToString(toEncryptedKeyBytes, Base64.NO_WRAP);

        String toTempDeviceKey = null;
        String toTempDeviceKey2 = null;

        if (toTempDevicePublicKeyXML != null) {
            PublicKey tempDevicePublicKey = getToPublicKey(toTempDevicePublicKeyXML);
            toTempDeviceKey = Base64.encodeToString(eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithRSA(keyContainerBytes, tempDevicePublicKey), Base64.NO_WRAP);
            toTempDeviceKey2 = Base64.encodeToString(eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithRSA(encodedAesKeyBytes.getBytes(StandardCharsets.UTF_8), tempDevicePublicKey), Base64.NO_WRAP);
        }

        privateMessage.from = new KeyContainerModel(fromAccount.getAccountGuid(), fromEncodedEncryptedKey, contactFromKey2, fromTempDeviceGuid, fromTempDeviceKey, fromTempDeviceKey2);
        privateMessage.to = new KeyContainerModel[]{new KeyContainerModel(toGuid, toEncodedEncryptedKey, contactToKey2, toTempDeviceGuid, toTempDeviceKey, toTempDeviceKey2)};
        privateMessage.datesend = new Date();
        privateMessage.dateSendTimed = dateSendTimed;
        attachContactInfo(fromAccount, privateMessage);

        privateMessage.key2Iv = ivString;
        privateMessage.isPriority = isPriority;

        return privateMessage;
    }

    private PrivateMessageModel createPrivateInternalMessageModelBase(PrivateMessageModel privateMessage,
                                                                      Account fromAccount,
                                                                      String toGuid,
                                                                      String toPublicKeyXML,
                                                                      String toTempDeviceGuid,
                                                                      String toTempDevicePublicKeyXML,
                                                                      KeyPair userKeyPair,
                                                                      SecretKey aesKey,
                                                                      IvParameterSpec iv,
                                                                      final Date dateSendTimed)
            throws LocalizedException {

        PublicKey toPublicKey = getToPublicKey(toPublicKeyXML);

        byte[] aesKeyBytes = aesKey.getEncoded();
        byte[] ivBytes = iv.getIV();
        String ivString = Base64.encodeToString(ivBytes, Base64.NO_WRAP);

        String encodedAesKeyBytes = Base64.encodeToString(aesKeyBytes, Base64.NO_WRAP);

        JsonObject keyJson = new JsonObject();
        keyJson.addProperty("key", encodedAesKeyBytes);
        keyJson.addProperty("iv", ivString);

        byte[] keyContainerBytes = IOSMessageConversionUtil.convertJsonToXML(keyJson).getBytes(StandardCharsets.UTF_8);

        byte[] fromEncryptedKeyBytes = eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithRSA(keyContainerBytes, userKeyPair.getPublic());
        String fromEncodedEncryptedKey = Base64.encodeToString(fromEncryptedKeyBytes, Base64.NO_WRAP);

        byte[] toEncryptedKeyBytes = eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithRSA(keyContainerBytes, toPublicKey);
        String toEncodedEncryptedKey = Base64.encodeToString(toEncryptedKeyBytes, Base64.NO_WRAP);

        String toTempDeviceKey = null;
        String toTempDeviceKey2 = null;

        if (toTempDevicePublicKeyXML != null) {
            PublicKey tempDevicePublicKey = getToPublicKey(toTempDevicePublicKeyXML);
            toTempDeviceKey = Base64.encodeToString(eu.ginlo_apps.ginlo.util.SecurityUtil.encryptMessageWithRSA(keyContainerBytes, tempDevicePublicKey), Base64.NO_WRAP);
            toTempDeviceKey2 = Base64.encodeToString(SecurityUtil.encryptMessageWithRSA(encodedAesKeyBytes.getBytes(StandardCharsets.UTF_8), tempDevicePublicKey), Base64.NO_WRAP);
        }

        privateMessage.from = new KeyContainerModel(fromAccount.getAccountGuid(), fromEncodedEncryptedKey, null);
        privateMessage.to = new KeyContainerModel[]{new KeyContainerModel(toGuid, toEncodedEncryptedKey, null, toTempDeviceGuid, toTempDeviceKey, toTempDeviceKey2)};
        privateMessage.datesend = new Date();
        privateMessage.dateSendTimed = dateSendTimed;
        attachContactInfo(fromAccount, privateMessage);

        privateMessage.key2Iv = ivString;

        return privateMessage;
    }

    private ChannelMessageModel createChannelMessageModelBase(String channelGuid) {
        ChannelMessageModel channelMessage = new ChannelMessageModel();

        channelMessage.to = channelGuid;
        channelMessage.datesend = new Date();

        return channelMessage;
    }

    private PublicKey getToPublicKey(String publicKeyString)
            throws LocalizedException {
        PublicKey publicKey;

        if (toPublicKeyCache.containsKey(publicKeyString)) {
            publicKey = toPublicKeyCache.get(publicKeyString);
        } else {
            publicKey = XMLUtil.getPublicKeyFromXML(publicKeyString);
            if (publicKey != null) {
                toPublicKeyCache.put(publicKeyString, publicKey);
            }
        }
        if (publicKey == null) {
            throw new LocalizedException(LocalizedException.PUBLIC_KEY_IS_NULL);
        }

        return publicKey;
    }

    private JsonObject getDataJsonObject(BaseMessageModel messageModel) {
        JsonObject dataJson;

        if (!StringUtil.isNullOrEmpty(messageModel.data)) {
            dataJson = jsonParser.parse(messageModel.data).getAsJsonObject();
        } else {
            dataJson = new JsonObject();
        }

        return dataJson;
    }
}
