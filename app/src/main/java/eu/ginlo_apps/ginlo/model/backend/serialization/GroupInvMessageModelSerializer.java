// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.model.backend.GroupInvMessageModel;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.log.LogUtil;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class GroupInvMessageModelSerializer
        implements JsonSerializer<GroupInvMessageModel> {

    @Override
    public JsonElement serialize(GroupInvMessageModel groupInvMessageModel,
                                 Type type,
                                 JsonSerializationContext context) {
        final JsonObject jsonObject = new JsonObject();
        final JsonObject groupInvMessageJsonObject = new JsonObject();

        if (groupInvMessageModel.requestGuid != null) {
            groupInvMessageJsonObject.addProperty("senderId", groupInvMessageModel.requestGuid);
        }
        if (groupInvMessageModel.guid != null) {
            groupInvMessageJsonObject.addProperty("guid", groupInvMessageModel.guid);
        }
        if (groupInvMessageModel.from != null) {
            groupInvMessageJsonObject.add("from", context.serialize(groupInvMessageModel.from, KeyContainerModel.class));
        }
        if (groupInvMessageModel.to != null) {
            groupInvMessageJsonObject.add("to", context.serialize(groupInvMessageModel.to, KeyContainerModel[].class));
        }
        if (groupInvMessageModel.data != null) {
            groupInvMessageJsonObject.addProperty("data", groupInvMessageModel.data);
        }
      /*if (groupInvMessageModel.requestGuid != null)
      {
         groupInvMessageJsonObject.addProperty("requestGuid", groupInvMessageModel.requestGuid);
      }*/
        if (groupInvMessageModel.attachment != null) {
            try {
                JsonArray jsonArray = new JsonArray();

                if (GuidUtil.isRequestGuid(groupInvMessageModel.attachment)) {
                    byte[] encryptedDataFromAttachment = AttachmentController.loadEncryptedBase64AttachmentFile(groupInvMessageModel.attachment);

                    if ((encryptedDataFromAttachment != null) && (encryptedDataFromAttachment.length > 0)) {
                        String attachment = new String(encryptedDataFromAttachment, StandardCharsets.US_ASCII);
                        jsonArray.add(new JsonPrimitive(attachment));
                    }
                } else {
                    jsonArray.add(new JsonPrimitive(groupInvMessageModel.attachment));
                }

                if (jsonArray.size() > 0) {
                    groupInvMessageJsonObject.add("attachment", jsonArray);
                }
            } catch (LocalizedException e) {
                return null;
            }
        }
        //    if (groupInvMessageModel.signature != null)
        //       groupInvMessageJsonObject.add("signature",
        //             context.serialize(groupInvMessageModel.signature, SignatureModel.class));

        if (groupInvMessageModel.signatureSha256Bytes != null) {
            JsonParser parser = new JsonParser();

            JsonElement element = parser.parse(new String(groupInvMessageModel.signatureSha256Bytes, StandardCharsets.UTF_8));

            groupInvMessageJsonObject.add("signature-sha256", element);
        }

        if (groupInvMessageModel.signatureBytes != null) {
            JsonParser parser = new JsonParser();

            JsonElement element = parser.parse(new String(groupInvMessageModel.signatureBytes, StandardCharsets.UTF_8));

            groupInvMessageJsonObject.add("signature", element);
        }

        if (groupInvMessageModel.mimeType != null) {
            groupInvMessageJsonObject.addProperty("messageType", groupInvMessageModel.mimeType);
        }

        jsonObject.add("GroupInvMessage", groupInvMessageJsonObject);
        return jsonObject;
    }
}
