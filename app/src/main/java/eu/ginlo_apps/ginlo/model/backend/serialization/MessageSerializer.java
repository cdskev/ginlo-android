// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.serialization;

import android.util.Base64;
import com.google.gson.*;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Created by Florian on 03.05.16.
 */
public class MessageSerializer implements JsonSerializer<Message> {
    private static final String TAG = "MessageSerializer";

    @Override
    public JsonElement serialize(Message message, Type type, JsonSerializationContext jsonSerializationContext) {
        final JsonObject jsonObject = new JsonObject();
        final JsonObject privateMessageJsonObject = new JsonObject();
        int base64Flag = Base64.NO_WRAP;

        String requestGuid = message.getRequestGuid();
        if (requestGuid != null) {
            privateMessageJsonObject.addProperty("senderId", requestGuid);
        }

        String guid = message.getGuid();
        if (guid != null) {
            privateMessageJsonObject.addProperty("guid", guid);
        }

        setFrom(privateMessageJsonObject, message, jsonSerializationContext);
        setTo(privateMessageJsonObject, message, jsonSerializationContext);

        String data = Base64.encodeToString(message.getData(), base64Flag);

        if (data != null) {
            privateMessageJsonObject.addProperty("data", data);
        }

        if (message.getIsSystemInfo() != null && message.getIsSystemInfo().booleanValue()) {
            privateMessageJsonObject.addProperty("isSystemMessage", Boolean.valueOf(true));
        } else {
            privateMessageJsonObject.addProperty("isSystemMessage", Boolean.valueOf(false));
        }

        String features = message.getFeatures();
        if (features != null) {
            privateMessageJsonObject.addProperty("features", features);
        }

        if (message.isBackup()) {
            //Bei Backup ist "datesend" der Zeitstempel wann es am Server angekommen ist
            Long datesend = message.getDateSendConfirm();
            if (datesend != null) {
                privateMessageJsonObject.addProperty("datesend", DateUtil.dateToUtcString(new Date(datesend)));
            }

            Long datesendLocal = message.getDateSend();
            if (datesendLocal != null) {
                privateMessageJsonObject.addProperty("localSendDate", DateUtil.dateToUtcString(new Date(datesendLocal)));
            }

            Long read = message.getDateRead();
            if (read != null) {
                privateMessageJsonObject.addProperty("localReadDate", DateUtil.utcStringFromMillis(read));
            }

            Boolean hasSendError = message.getHasSendError();
            if (hasSendError != null) {
                privateMessageJsonObject.addProperty("sendingFailed", hasSendError ? "true" : "false");
            }

            Long downloaded = message.getDateDownloaded();
            if (downloaded != null) {
                privateMessageJsonObject.addProperty("localDownloadedDate", DateUtil.utcStringFromMillis(downloaded));
            }
            //
            Long dateSendTimed = message.getDateSendTimed();
            if (dateSendTimed != null && dateSendTimed > 0) {
                privateMessageJsonObject.addProperty("dateToSend", DateUtil.utcStringFromMillis(dateSendTimed));

                if (datesend != null) {
                    privateMessageJsonObject.addProperty("dateCreated", DateUtil.dateToUtcString(new Date(datesend)));
                }
            }

            Boolean isMsgValid = message.getIsSignatureValid();
            if (isMsgValid != null) {
                privateMessageJsonObject.addProperty("signatureValid", isMsgValid ? "true" : "false");
            }

            JsonElement receivers = message.getElementFromAttributes(AppConstants.MESSAGE_JSON_RECEIVERS);
            if (receivers != null) {
                privateMessageJsonObject.add(AppConstants.MESSAGE_JSON_RECEIVERS, receivers);
            }
        } else {
            Long datesend = message.getDateSend();
            if (datesend != null) {
                privateMessageJsonObject.addProperty("datesend", DateUtil.dateToUtcString(new Date(datesend)));
            }
        }

        String attachmentGuid = message.getAttachment();
        if (!StringUtil.isNullOrEmpty(attachmentGuid)) {
            if (message.isBackup()) {
                JsonArray jsonArray = new JsonArray();
                jsonArray.add(new JsonPrimitive(attachmentGuid));
                privateMessageJsonObject.add("attachment", jsonArray);
            } else {
                try {
                    byte[] encryptedDataFromAttachment = AttachmentController.loadEncryptedBase64AttachmentFile(attachmentGuid);

                    if ((encryptedDataFromAttachment != null) && (encryptedDataFromAttachment.length > 0)) {
                        String attachment = new String(encryptedDataFromAttachment, StandardCharsets.US_ASCII);

                        JsonArray jsonArray = new JsonArray();

                        jsonArray.add(new JsonPrimitive(attachment));
                        privateMessageJsonObject.add("attachment", jsonArray);
                    }
                } catch (LocalizedException e) {
                    LogUtil.w(TAG, "Can not load Attachment", e);
                }
            }
        }

        byte[] signatureBytes = message.getSignature();
        if (signatureBytes != null) {
            JsonParser parser = new JsonParser();

            JsonElement element = parser.parse(new String(signatureBytes, StandardCharsets.UTF_8));

            privateMessageJsonObject.add("signature", element);
        }

        byte[] signatureBytesSah256 = message.getSignatureSha256();
        if (signatureBytesSah256 != null) {
            JsonParser parser = new JsonParser();

            JsonElement element = parser.parse(new String(signatureBytesSah256, StandardCharsets.UTF_8));

            privateMessageJsonObject.add("signature-sha256", element);
        }

        if (!StringUtil.isNullOrEmpty(message.getImportance())) {
            privateMessageJsonObject.addProperty("importance", message.getImportance());
        }

        jsonObject.add(getMessageObjectKey(message), privateMessageJsonObject);

        return jsonObject;
    }

    private String getMessageObjectKey(Message message) {
        int type = message.getType();
        String returnValue;
        switch (type) {
            case Message.TYPE_PRIVATE: {
                returnValue = MessageController.TYPE_PRIVATE_MESSAGE;

                if (message.isBackup()) {
                    Long dateSendTimed = message.getDateSendTimed();
                    if (dateSendTimed != null && dateSendTimed > 0) {
                        returnValue = MessageController.TYPE_PRIVATE_TIMED_MESSAGE;
                    }
                }
                break;
            }
            case Message.TYPE_GROUP: {
                returnValue = MessageController.TYPE_GROUP_MESSAGE;

                if (message.isBackup()) {
                    Long dateSendTimed = message.getDateSendTimed();
                    if (dateSendTimed != null && dateSendTimed > 0) {
                        returnValue = MessageController.TYPE_GROUP_TIMED_MESSAGE;
                    }
                }
                break;
            }
            case Message.TYPE_CHANNEL: {
                returnValue = MessageController.TYPE_CHANNEL_MESSAGE;
                break;
            }
            case Message.TYPE_GROUP_INVITATION: {
                returnValue = MessageController.TYPE_GROUP_INVITATION_MESSAGE;
                break;
            }
            case Message.TYPE_PRIVATE_INTERNAL: {
                returnValue = MessageController.TYPE_PRIVATE_INTERNAL_MESSAGE;
                break;
            }
            case Message.TYPE_INTERNAL: {
                returnValue = MessageController.TYPE_INTERNAL_MESSAGE;
                break;
            }
            default: {
                returnValue = "";
            }
        }

        return returnValue;
    }

    private void setFrom(JsonObject privateMessageJsonObject, Message message, JsonSerializationContext jsonSerializationContext) {
        switch (message.getType()) {
            case Message.TYPE_PRIVATE:
            case Message.TYPE_GROUP_INVITATION:
            case Message.TYPE_PRIVATE_INTERNAL: {
                KeyContainerModel from = new KeyContainerModel(message.getFrom(),
                        Base64.encodeToString(message.getEncryptedFromKey(),
                                Base64.NO_WRAP), null);

                privateMessageJsonObject.add("from", jsonSerializationContext.serialize(from, KeyContainerModel.class));
                break;
            }
            case Message.TYPE_GROUP: {
                KeyContainerModel from = new KeyContainerModel(message.getFrom(),
                        Base64.encodeToString(new byte[]{}, Base64.NO_WRAP), null);

                privateMessageJsonObject.add("from", jsonSerializationContext.serialize(from, KeyContainerModel.class));
                break;
            }
            default: {
                LogUtil.w(this.getClass().getName(), LocalizedException.UNDEFINED_ARGUMENT);
                break;
            }
        }
    }

    private void setTo(JsonObject privateMessageJsonObject, Message message, JsonSerializationContext jsonSerializationContext) {
        switch (message.getType()) {
            case Message.TYPE_PRIVATE:
            case Message.TYPE_GROUP_INVITATION:
            case Message.TYPE_PRIVATE_INTERNAL: {

                KeyContainerModel[] to = new KeyContainerModel[]
                        {
                                new KeyContainerModel(message.getTo(),
                                        Base64.encodeToString(message.getEncryptedToKey(), Base64.NO_WRAP),
                                        message.getEncryptedToKey2())
                        };
                privateMessageJsonObject.add("to", jsonSerializationContext.serialize(to, KeyContainerModel[].class));
                privateMessageJsonObject.addProperty("key2-iv", message.getKey2Iv());
                break;
            }
            case Message.TYPE_GROUP:
            case Message.TYPE_CHANNEL: {
                if (message.getTo() != null) {
                    privateMessageJsonObject.addProperty("to", message.getTo());
                }

                break;
            }
            default: {
                LogUtil.w(this.getClass().getName(), LocalizedException.UNDEFINED_ARGUMENT);
                break;
            }
        }
    }
}
