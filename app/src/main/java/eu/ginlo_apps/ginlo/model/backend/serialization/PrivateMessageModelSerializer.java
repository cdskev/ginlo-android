// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;
import eu.ginlo_apps.ginlo.model.backend.PrivateMessageModel;
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
public class PrivateMessageModelSerializer
        implements JsonSerializer<PrivateMessageModel> {

    @Override
    public JsonElement serialize(PrivateMessageModel privateMessageModel,
                                 Type type,
                                 JsonSerializationContext context) {
        final JsonObject jsonObject = new JsonObject();
        final JsonObject privateMessageJsonObject = new JsonObject();

        if (privateMessageModel.requestGuid != null) {
            privateMessageJsonObject.addProperty("senderId", privateMessageModel.requestGuid);
        }
        if (privateMessageModel.guid != null) {
            privateMessageJsonObject.addProperty("guid", privateMessageModel.guid);
        }
        if (privateMessageModel.from != null) {
            privateMessageJsonObject.add("from", context.serialize(privateMessageModel.from, KeyContainerModel.class));
        }
        if (privateMessageModel.to != null) {
            privateMessageJsonObject.add("to", context.serialize(privateMessageModel.to, KeyContainerModel[].class));
        }
        if (privateMessageModel.key2Iv != null) {
            privateMessageJsonObject.addProperty("key2-iv", privateMessageModel.key2Iv);
        }
        if (privateMessageModel.data != null) {
            privateMessageJsonObject.addProperty("data", privateMessageModel.data);
        }
        if (privateMessageModel.datesend != null) {
            privateMessageJsonObject.addProperty("datesend", DateUtil.dateToUtcString(privateMessageModel.datesend));
        }
      /*if (privateMessageModel.requestGuid != null)
      {
         privateMessageJsonObject.addProperty("requestGuid", privateMessageModel.requestGuid);
      }*/
        if (privateMessageModel.isSystemMessage != null) {
            privateMessageJsonObject.addProperty("isSystemMessage", privateMessageModel.isSystemMessage);
        }
        if (privateMessageModel.features != null) {
            privateMessageJsonObject.addProperty("features", privateMessageModel.features);
        }

        if (privateMessageModel.attachment != null) {
            JsonArray jsonArray = new JsonArray();
            if (GuidUtil.isRequestGuid(privateMessageModel.attachment)) {
                // KS: Optimized this code to work with file streams instead of RAM.
                // That allows bigger attachments which would otherwise result in oom.
                // Return link to file instead of attachment contents. Must be processed
                // by the caller.

                //privateMessageJsonObject.add("attachment",
                //        AttachmentController.loadEncryptedBase64AttachmentAsJsonElementFromFile(privateMessageModel.attachment));

                jsonArray.add(new JsonPrimitive("@" + AttachmentController.convertEncryptedAttachmentBase64FileToJsonArrayFile(privateMessageModel.attachment)));
                privateMessageJsonObject.add("attachment", jsonArray);

            } else {
                jsonArray.add(new JsonPrimitive(privateMessageModel.attachment));
                if (jsonArray.size() > 0) {
                    privateMessageJsonObject.add("attachment", jsonArray);
                }
            }
        }

        //    if (privateMessageModel.signature != null)
        //        {
        //       privateMessageJsonObject.add("signature",
        //                    context.serialize(privateMessageModel.signature, SignatureModel.class));
        //        }

        if (privateMessageModel.signatureSha256Bytes != null) {
            JsonParser parser = new JsonParser();

            JsonElement element = parser.parse(new String(privateMessageModel.signatureSha256Bytes, StandardCharsets.UTF_8));

            privateMessageJsonObject.add("signature-sha256", element);
        }

        if (privateMessageModel.signatureBytes != null) {
            JsonParser parser = new JsonParser();

            JsonElement element = parser.parse(new String(privateMessageModel.signatureBytes, StandardCharsets.UTF_8));

            privateMessageJsonObject.add("signature", element);
        }

        if (privateMessageModel.mimeType != null) {
            privateMessageJsonObject.addProperty("messageType", privateMessageModel.mimeType);
        }

        if (privateMessageModel.isPriority != null && privateMessageModel.isPriority) {
            privateMessageJsonObject.addProperty("importance", "high");
        }

        jsonObject.add("PrivateMessage", privateMessageJsonObject);
        return jsonObject;
    }
}
