// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AccountController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.BaseMessageModel;
import eu.ginlo_apps.ginlo.model.backend.ChannelMessageModel;
import eu.ginlo_apps.ginlo.model.backend.GroupMessageModel;
import eu.ginlo_apps.ginlo.model.backend.InternalMessageModel;
import eu.ginlo_apps.ginlo.model.backend.MessageReceiverModel;
import eu.ginlo_apps.ginlo.model.backend.PrivateMessageModel;
import eu.ginlo_apps.ginlo.model.backend.serialization.MessageDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.MessageReceiverModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.MessageReceiverModelSerializer;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class MessageDaoHelper {
    private static MessageDaoHelper mInstance;

    private final AccountController accountController;

    private Gson mGson;

    private MessageDaoHelper(AccountController accountController) {
        this.accountController = accountController;
        initGson();
    }

    public static MessageDaoHelper getInstance(SimsMeApplication application) {
        synchronized (MessageDaoHelper.class) {
            if (mInstance == null) {
                mInstance = new MessageDaoHelper(application.getAccountController());
            }

            return mInstance;
        }
    }

    public static void setMessageAttributes(@NonNull Message message, @NonNull JsonObject msgJO, final int msgType, final String ownAccounGuid) {
        try {
            String guid = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("guid", msgJO);
            if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(guid)) {
                message.setGuid(guid);
            }

            message.setType(msgType);

            String requestGuid = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("senderId", msgJO);
            if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(requestGuid)) {
                message.setRequestGuid(requestGuid);
            }

            setFrom(msgJO, message);
            setTo(msgJO, message);

            if (ownAccounGuid != null) {
                setSentType(message, ownAccounGuid);
            }

            String data = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("data", msgJO);
            if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(data)) {
                if (msgType == Message.TYPE_INTERNAL) {
                    message.setData(data.getBytes(StandardCharsets.UTF_8));
                } else {
                    message.setData(Base64.decode(data, Base64.NO_WRAP));
                }
            }

            String serverMimeType = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("messageType", msgJO);
            if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(serverMimeType)) {
                message.setServerMimeTye(serverMimeType);
            }

            String pushInfo = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("pushInfo", msgJO);
            if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(pushInfo)) {
                message.setPushInfo(pushInfo);
            }

            String dateSendLocal = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("localSendDate", msgJO);
            //Backup Value
            if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(dateSendLocal)) {
                message.setDateSend(eu.ginlo_apps.ginlo.util.DateUtil.utcStringToMillis(dateSendLocal));

                String dateSend = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("datesend", msgJO);
                if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(dateSend)) {
                    message.setDateSendConfirm(eu.ginlo_apps.ginlo.util.DateUtil.utcStringToMillis(dateSend));
                }
            } else {
                String dateSend = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("datesend", msgJO);
                if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(dateSend)) {
                    message.setDateSend(eu.ginlo_apps.ginlo.util.DateUtil.utcStringToMillis(dateSend));
                }
                if (message.isSentMessage()) {
                    message.setDateSendConfirm(message.getDateSend());
                }
            }

            if (eu.ginlo_apps.ginlo.util.JsonUtil.hasKey("isSystemMessage", msgJO)) {
                message.setIsSystemInfo(msgJO.get("isSystemMessage").getAsBoolean());
            } else {
                message.setIsSystemInfo(false);
            }

            if (eu.ginlo_apps.ginlo.util.JsonUtil.hasPrimitiveKey("importance", msgJO)) {
                message.setImportance(msgJO.get("importance").getAsString());
            }

            //Backup Values -->
            String localReadDate = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("localReadDate", msgJO);
            if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(localReadDate)) {
                message.setDateRead(eu.ginlo_apps.ginlo.util.DateUtil.utcStringToMillis(localReadDate));
                message.setRead(true);
            }

            String sendingFailed = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("sendingFailed", msgJO);
            if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(sendingFailed)) {
                message.setHasSendError(sendingFailed.equalsIgnoreCase("true"));
            } else {
                String readDate = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("dateread", msgJO);
                if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(readDate)) {
                    message.setDateRead(eu.ginlo_apps.ginlo.util.DateUtil.utcStringToMillis(readDate));
                    message.setRead(true);
                }
            }

            String localDownloadedDate = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("localDownloadedDate", msgJO);
            if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(localDownloadedDate)) {
                message.setDateDownloaded(eu.ginlo_apps.ginlo.util.DateUtil.utcStringToMillis(localDownloadedDate));
            } else {
                localDownloadedDate = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("datedownloaded", msgJO);
                if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(localDownloadedDate)) {
                    message.setDateDownloaded(eu.ginlo_apps.ginlo.util.DateUtil.utcStringToMillis(localDownloadedDate));
                }
            }

            String dateToSend = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("dateToSend", msgJO);
            if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(dateToSend)) {
                message.setDateSendTimed(eu.ginlo_apps.ginlo.util.DateUtil.utcStringToMillis(dateToSend));

                String dateCreated = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("dateCreated", msgJO);
                if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(dateCreated)) {
                    //nochmal setzen, wegen iOS
                    message.setDateSend(DateUtil.utcStringToMillis(dateCreated));
                }
            }

            String signatureValid = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("signatureValid", msgJO);
            if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(signatureValid)) {
                message.setIsSignatureValid(signatureValid.equalsIgnoreCase("true"));
            }
            //<-- Backup Values

            if (eu.ginlo_apps.ginlo.util.JsonUtil.hasKey("attachment", msgJO)) {
                JsonElement jeAttachment = msgJO.get("attachment");
                if (jeAttachment.isJsonArray()) {
                    JsonArray jaAttachment = (JsonArray) jeAttachment;
                    if (jaAttachment.size() > 0) {
                        JsonElement jeAttachmentGuid = jaAttachment.get(0);
                        String attachmentGuid = jeAttachmentGuid.getAsString();
                        message.setAttachment(attachmentGuid);
                    }
                }
            }

            if (eu.ginlo_apps.ginlo.util.JsonUtil.hasKey("key2-iv", msgJO)) {
                String key2Iv = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("key2-iv", msgJO);
                if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(key2Iv)) {
                    message.setKey2Iv(key2Iv);
                }
            }

            if (eu.ginlo_apps.ginlo.util.JsonUtil.hasKey("signature-temp256", msgJO)) {
                message.setSignatureTemp256(msgJO.get("signature-temp256"));
            }

            if (eu.ginlo_apps.ginlo.util.JsonUtil.hasKey("signature-sha256", msgJO)) {
                byte[] signatureBytesSha256 = msgJO.get("signature-sha256").toString().getBytes(StandardCharsets.UTF_8);
                message.setSignatureSha256(signatureBytesSha256);
            }

            if (eu.ginlo_apps.ginlo.util.JsonUtil.hasKey("signature", msgJO)) {
                byte[] signatureBytes = msgJO.get("signature").toString().getBytes(StandardCharsets.UTF_8);
                message.setSignature(signatureBytes);
            }

            if (eu.ginlo_apps.ginlo.util.JsonUtil.hasKey(AppConstants.MESSAGE_JSON_RECEIVERS, msgJO)) {
                message.setElementToAttributes(AppConstants.MESSAGE_JSON_RECEIVERS, msgJO.get(AppConstants.MESSAGE_JSON_RECEIVERS));
            }
        } catch (IllegalArgumentException e) {
            // Bad BASE64
            LogUtil.e(MessageDaoHelper.class.getSimpleName(), e.getMessage(), e);
        }
    }

    private static void setFrom(JsonObject msgJO, Message message) {
        JsonElement je = msgJO.get("from");
        if (je == null) {
            return;
        }

        if (message.getType() == Message.TYPE_INTERNAL && je.isJsonPrimitive()) {
            message.setFrom(je.getAsString());
        } else if (je.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : ((JsonObject) je).entrySet()) {
                if (entry == null || entry.getKey() == null) {
                    continue;
                }

                String from = entry.getKey();
                message.setFrom(from);

                if (entry.getValue() != null && entry.getValue().isJsonObject()) {
                    final JsonObject keyContainerObject = entry.getValue().getAsJsonObject();
                    String key = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("key", keyContainerObject);

                    if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(key)) {
                        byte[] bytes = Base64.decode(key, Base64.NO_WRAP);
                        message.setEncryptedFromKey(bytes);
                    }

                    String key2 = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("key2", keyContainerObject);
                    if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(key2)) {
                        message.setEncryptedFromKey2(key2);
                    }

                    if (keyContainerObject.has("tempDevice")) {
                        JsonElement jsonElement = keyContainerObject.get("tempDevice");
                        if (jsonElement.isJsonObject()) {
                            JsonObject tempInfo = jsonElement.getAsJsonObject();
                            message.setFromTempDeviceInfo(tempInfo);
                        }
                    }
                }

                break;
            }
        }
    }

    private static void setTo(JsonObject msgJO, Message message) {
        JsonElement je = msgJO.get("to");
        if (je == null) {
            return;
        }

        switch (message.getType()) {
            case Message.TYPE_PRIVATE:
            case Message.TYPE_GROUP_INVITATION:
            case Message.TYPE_PRIVATE_INTERNAL: {
                if (je.isJsonArray()) {
                    JsonArray ja = je.getAsJsonArray();
                    if (ja.size() > 0) {
                        JsonElement jeTo = ja.get(0);
                        if (jeTo.isJsonObject()) {
                            setToValuesFromJsonObject((JsonObject) jeTo, message);
                        }
                    }
                }

                if (je.isJsonObject()) {
                    setToValuesFromJsonObject((JsonObject) je, message);
                }
                break;
            }
            case Message.TYPE_GROUP:
            case Message.TYPE_CHANNEL: {
                String to = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("to", msgJO);
                if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(to)) {
                    message.setTo(to);
                }

                break;
            }
            case Message.TYPE_INTERNAL: {
                break;
            }
            default: {
                LogUtil.w(MessageDaoHelper.class.getSimpleName(), LocalizedException.UNDEFINED_ARGUMENT);
                break;
            }
        }
    }

    private static void setToValuesFromJsonObject(JsonObject jsonObject, Message message) {
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            if (entry == null || entry.getValue() == null || entry.getKey() == null || !entry.getValue().isJsonObject()) {
                continue;
            }

            final JsonObject keyContainerObject = entry.getValue().getAsJsonObject();
            String to = entry.getKey();
            String key = eu.ginlo_apps.ginlo.util.JsonUtil.stringFromJO("key", keyContainerObject);

            if (eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(key)) {
                continue;
            }

            byte[] bytes = Base64.decode(key, Base64.NO_WRAP);

            message.setTo(to);
            message.setEncryptedToKey(bytes);

            String key2 = JsonUtil.stringFromJO("key2", keyContainerObject);

            if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(key2)) {
                message.setEncryptedToKey2(key2);
            }

            if (keyContainerObject.has("tempDevice")) {
                JsonElement jsonElement = keyContainerObject.get("tempDevice");
                if (jsonElement.isJsonObject()) {
                    JsonObject tempInfo = jsonElement.getAsJsonObject();
                    message.setToTempDeviceInfo(tempInfo);
                }
            }

            break;
        }
    }

    public static int getMessageType(String messageObjectKey) {
        int type = -1;

        switch (messageObjectKey) {
            case MessageController.TYPE_PRIVATE_MESSAGE: {
                type = Message.TYPE_PRIVATE;
                break;
            }
            case MessageController.TYPE_PRIVATE_TIMED_MESSAGE: {
                type = Message.TYPE_PRIVATE;
                break;
            }
            case MessageController.TYPE_GROUP_MESSAGE: {
                type = Message.TYPE_GROUP;
                break;
            }
            case MessageController.TYPE_GROUP_TIMED_MESSAGE: {
                type = Message.TYPE_GROUP;
                break;
            }
            case MessageController.TYPE_CHANNEL_MESSAGE:
            case MessageController.TYPE_SERVICE_MESSAGE: {
                type = Message.TYPE_CHANNEL;
                break;
            }
            case MessageController.TYPE_GROUP_INVITATION_MESSAGE: {
                type = Message.TYPE_GROUP_INVITATION;
                break;
            }
            case MessageController.TYPE_PRIVATE_INTERNAL_MESSAGE: {
                type = Message.TYPE_PRIVATE_INTERNAL;
                break;
            }
            case MessageController.TYPE_INTERNAL_MESSAGE: {
                type = Message.TYPE_INTERNAL;
                break;
            }
            default: {
                LogUtil.w(MessageDaoHelper.class.getSimpleName(), LocalizedException.UNDEFINED_ARGUMENT);
                break;
            }
        }

        return type;
    }

    public static Message buildMessageFromMessageModel(@NotNull final BaseMessageModel messageModel,
                                                       final String ownerAccountGuid) {
        Message message = new Message();

        message.setType(messageModel.messageType);
        message.setGuid(messageModel.guid);

        if (messageModel instanceof PrivateMessageModel) {
            PrivateMessageModel privateMessageModel = (PrivateMessageModel) messageModel;

            message.setTo(privateMessageModel.to[0].guid);
            message.setEncryptedToKey(Base64.decode(privateMessageModel.to[0].keyContainer, Base64.DEFAULT));
            message.setFrom(privateMessageModel.from.guid);
            message.setEncryptedFromKey(Base64.decode(privateMessageModel.from.keyContainer, Base64.DEFAULT));
            message.setData(Base64.decode(privateMessageModel.data, Base64.DEFAULT));
            message.setRequestGuid(privateMessageModel.requestGuid);
            message.setIsSystemInfo(privateMessageModel.isSystemMessage != null && privateMessageModel.isSystemMessage);
            message.setIsAbsentMessage(privateMessageModel.isAbesntMessage);
            message.setSignature(privateMessageModel.signatureBytes);
            message.setSignatureSha256(privateMessageModel.signatureSha256Bytes);
            message.setKey2Iv(privateMessageModel.key2Iv);
            if (privateMessageModel.dateSendTimed != null) {
                message.setDateSendTimed(privateMessageModel.dateSendTimed.getTime());
            }

            setSentType(message, ownerAccountGuid);
            setDates(privateMessageModel, message);

            if (privateMessageModel.attachment != null) {
                message.setAttachment(privateMessageModel.attachment);
            }
            if (privateMessageModel.isPriority != null && privateMessageModel.isPriority) {
                message.setImportance("high");
            }
        } else if (messageModel instanceof GroupMessageModel) {
            GroupMessageModel groupMessageModel = (GroupMessageModel) messageModel;

            message.setTo(groupMessageModel.to);
            message.setFrom(groupMessageModel.from.guid);
            message.setEncryptedFromKey(Base64.decode(groupMessageModel.from.keyContainer, Base64.DEFAULT));
            message.setData(Base64.decode(groupMessageModel.data, Base64.DEFAULT));
            message.setRequestGuid(groupMessageModel.requestGuid);
            message.setIsSystemInfo(groupMessageModel.isSystemMessage != null && groupMessageModel.isSystemMessage);
            message.setSignature(groupMessageModel.signatureBytes);
            message.setSignatureSha256(groupMessageModel.signatureSha256Bytes);
            if (groupMessageModel.dateSendTimed != null) {
                message.setDateSendTimed(groupMessageModel.dateSendTimed.getTime());
            }

            setSentType(message, ownerAccountGuid);
            setDates(groupMessageModel, message);

            if (groupMessageModel.attachment != null) {
                message.setAttachment(groupMessageModel.attachment);
            }
            if (groupMessageModel.isPriority != null && groupMessageModel.isPriority) {
                message.setImportance("high");
            }
        } else if (messageModel instanceof InternalMessageModel) {
            InternalMessageModel internalMessageModel = (InternalMessageModel) messageModel;

            message.setIsSentMessage(false);
            message.setGuid(internalMessageModel.guid);
            message.setTo(internalMessageModel.to);
            message.setFrom(internalMessageModel.from);
            message.setData(internalMessageModel.data);
        } else if (messageModel instanceof ChannelMessageModel) {
            final ChannelMessageModel channelMessageModel = (ChannelMessageModel) messageModel;

            message.setTo(channelMessageModel.to);
            message.setData(Base64.decode(channelMessageModel.data, Base64.DEFAULT));

            //
            message.setIsSentMessage((channelMessageModel.isSystemMessage != null) ? channelMessageModel.isSystemMessage
                    : false);
            message.setIsSystemInfo(channelMessageModel.isSystemMessage != null && channelMessageModel.isSystemMessage);
            setDates(channelMessageModel, message);

            if (channelMessageModel.attachment != null) {
                message.setAttachment(channelMessageModel.attachment);
            }
        } else {
            message = null;
        }

        return message;
    }

    private static void setDates(BaseMessageModel baseMessageModel,
                                 Message message) {
        if (baseMessageModel.datedownloaded != null) {
            message.setDateDownloaded(baseMessageModel.datedownloaded.getTime());
        }
        if (baseMessageModel.dateread != null) {
            message.setDateRead(baseMessageModel.dateread.getTime());
        }
        if (baseMessageModel.datesend != null) {
            message.setDateSend(baseMessageModel.datesend.getTime());
        }
    }

    private static void setSentType(Message message,
                                    String accountGuid) {
        if (accountGuid.equals(message.getFrom())) {
            message.setIsSentMessage(true);
            message.setDateSendConfirm(message.getDateSend());
        } else {
            message.setIsSentMessage(false);
        }
    }

    public Message buildMessageFromJson(final JsonObject messageJsonContainer) {
        return mGson.fromJson(messageJsonContainer, Message.class);
    }

    @Nullable
    public Map<String, MessageReceiverModel> getReceiversFromMessage(@NonNull JsonArray receiversJsonArray) {
        MessageReceiverModel[] receiverModels = mGson.fromJson(receiversJsonArray, MessageReceiverModel[].class);

        if (receiverModels == null) {
            return null;
        }

        Map<String, MessageReceiverModel> receiversMap = new HashMap<>(receiverModels.length);
        for (MessageReceiverModel receiver : receiverModels) {
            if (receiver != null && !StringUtil.isNullOrEmpty(receiver.guid)) {
                receiversMap.put(receiver.guid, receiver);
            }
        }

        return receiversMap;
    }

    @Nullable
    public JsonArray getJsonFromReceivers(@NonNull final Map<String, MessageReceiverModel> receiversMap) {
        MessageReceiverModel[] receivers = new MessageReceiverModel[receiversMap.size()];

        Collection<MessageReceiverModel> coll = receiversMap.values();

        if (coll.size() < 1) {
            return null;
        }

        coll.toArray(receivers);

        JsonElement je = mGson.toJsonTree(receivers);

        if (je == null || !je.isJsonArray()) {
            return null;
        }

        return je.getAsJsonArray();
    }

    private void initGson() {
        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(Message.class, new MessageDeserializer(accountController));
        gsonBuilder.registerTypeAdapter(MessageReceiverModel.class, new MessageReceiverModelDeserializer());
        gsonBuilder.registerTypeAdapter(MessageReceiverModel.class, new MessageReceiverModelSerializer());

        mGson = gsonBuilder.create();
    }
}
