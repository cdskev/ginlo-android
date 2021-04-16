// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.model.backend.GroupMessageModel;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.log.LogUtil;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class GroupMessageModelSerializer
        implements JsonSerializer<GroupMessageModel> {

    @Override
    public JsonElement serialize(GroupMessageModel groupMessageModel,
                                 Type type,
                                 JsonSerializationContext context) {
        final JsonObject jsonObject = new JsonObject();
        final JsonObject groupMessageJsonObject = new JsonObject();

        if (groupMessageModel.requestGuid != null) {
            groupMessageJsonObject.addProperty("senderId", groupMessageModel.requestGuid);
        }
        if (groupMessageModel.guid != null) {
            groupMessageJsonObject.addProperty("guid", groupMessageModel.guid);
        }
        if (groupMessageModel.from != null) {
            groupMessageJsonObject.add("from", context.serialize(groupMessageModel.from, KeyContainerModel.class));
        }
        if (groupMessageModel.to != null) {
            groupMessageJsonObject.addProperty("to", groupMessageModel.to);
        }
        if (groupMessageModel.data != null) {
            groupMessageJsonObject.addProperty("data", groupMessageModel.data);
        }
        if (groupMessageModel.datesend != null) {
            groupMessageJsonObject.addProperty("datesend", DateUtil.dateToUtcString(groupMessageModel.datesend));
        }
      /*if (groupMessageModel.requestGuid != null)
      {
         groupMessageJsonObject.addProperty("requestGuid", groupMessageModel.requestGuid);
      }*/
        if (groupMessageModel.isSystemMessage != null) {
            groupMessageJsonObject.addProperty("isSystemMessage", groupMessageModel.isSystemMessage);
        }
        if (groupMessageModel.features != null) {
            groupMessageJsonObject.addProperty("features", groupMessageModel.features);
        }
        if (groupMessageModel.attachment != null) {
            try {
                JsonArray jsonArray = new JsonArray();

                if (GuidUtil.isRequestGuid(groupMessageModel.attachment)) {
                    byte[] encryptedDataFromAttachment = AttachmentController.loadEncryptedBase64AttachmentFile(groupMessageModel.attachment);

                    if ((encryptedDataFromAttachment != null) && (encryptedDataFromAttachment.length > 0)) {
                        String attachment = new String(encryptedDataFromAttachment, "US-ASCII");//Base64.encodeToString(encryptedDataFromAttachment, Base64.DEFAULT);
                        jsonArray.add(new JsonPrimitive(attachment));
                    }
                } else {
                    jsonArray.add(new JsonPrimitive(groupMessageModel.attachment));
                }

                if (jsonArray.size() > 0) {
                    groupMessageJsonObject.add("attachment", jsonArray);
                }
            } catch (LocalizedException | UnsupportedEncodingException e) {
                return null;
            }
        }
        //    if (groupMessageModel.signature != null)
        //       groupMessageJsonObject.add("signature",
        //             context.serialize(groupMessageModel.signature, SignatureModel.class));

        if (groupMessageModel.signatureSha256Bytes != null) {
            JsonParser parser = new JsonParser();

            try {
                JsonElement element = parser.parse(new String(groupMessageModel.signatureSha256Bytes, Encoding.UTF8));

                groupMessageJsonObject.add("signature-sha256", element);
            } catch (UnsupportedEncodingException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            }
        }

        if (groupMessageModel.signatureBytes != null) {
            JsonParser parser = new JsonParser();

            try {
                JsonElement element = parser.parse(new String(groupMessageModel.signatureBytes, Encoding.UTF8));

                groupMessageJsonObject.add("signature", element);
            } catch (UnsupportedEncodingException e) {
                LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            }
        }

        if (groupMessageModel.mimeType != null) {
            groupMessageJsonObject.addProperty("messageType", groupMessageModel.mimeType);
        }

        if (groupMessageModel.isPriority != null && groupMessageModel.isPriority) {
            groupMessageJsonObject.addProperty("importance", "high");
        }

        jsonObject.add("GroupMessage", groupMessageJsonObject);
        return jsonObject;
    }
}
