// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.model.backend.GroupMessageModel;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.log.LogUtil;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

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
            JsonArray jsonArray = new JsonArray();
            if (GuidUtil.isRequestGuid(groupMessageModel.attachment)) {
                // KS: Optimized this code to work with file streams instead of RAM.
                // That allows bigger attachments which would otherwise result in oom.
                // Return link to file instead of attachment contents. Must be processed
                // by the caller.

                //groupMessageJsonObject.add("attachment",
                //        AttachmentController.loadEncryptedBase64AttachmentAsJsonElementFromFile(groupMessageModel.attachment));

                jsonArray.add(new JsonPrimitive("@" + AttachmentController.convertEncryptedAttachmentBase64FileToJsonArrayFile(groupMessageModel.attachment)));
                groupMessageJsonObject.add("attachment", jsonArray);

            } else {
                jsonArray.add(new JsonPrimitive(groupMessageModel.attachment));
                if (jsonArray.size() > 0) {
                    groupMessageJsonObject.add("attachment", jsonArray);
                }
            }
        }

        //    if (groupMessageModel.signature != null)
        //       groupMessageJsonObject.add("signature",
        //             context.serialize(groupMessageModel.signature, SignatureModel.class));

        if (groupMessageModel.signatureSha256Bytes != null) {
            JsonParser parser = new JsonParser();

            JsonElement element = parser.parse(new String(groupMessageModel.signatureSha256Bytes, StandardCharsets.UTF_8));

            groupMessageJsonObject.add("signature-sha256", element);
        }

        if (groupMessageModel.signatureBytes != null) {
            JsonParser parser = new JsonParser();

            JsonElement element = parser.parse(new String(groupMessageModel.signatureBytes, StandardCharsets.UTF_8));

            groupMessageJsonObject.add("signature", element);
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
