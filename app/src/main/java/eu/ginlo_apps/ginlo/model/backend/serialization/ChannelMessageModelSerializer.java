// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.model.backend.ChannelMessageModel;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ChannelMessageModelSerializer
        implements JsonSerializer<ChannelMessageModel> {

    @Override
    public JsonElement serialize(ChannelMessageModel channelMessageModel,
                                 Type type,
                                 JsonSerializationContext context) {
        final JsonObject jsonObject = new JsonObject();
        final JsonObject channelMessageJsonObject = new JsonObject();

        if (channelMessageModel.guid != null) {
            channelMessageJsonObject.addProperty("guid", channelMessageModel.guid);
        }
        if (channelMessageModel.to != null) {
            channelMessageJsonObject.addProperty("to", channelMessageModel.to);
        }
        if (channelMessageModel.data != null) {
            channelMessageJsonObject.addProperty("data", channelMessageModel.data);
        }
        if (channelMessageModel.datesend != null) {
            channelMessageJsonObject.addProperty("datesend", DateUtil.dateToUtcString(channelMessageModel.datesend));
        }
        if (channelMessageModel.features != null) {
            channelMessageJsonObject.addProperty("features", channelMessageModel.features);
        }
        if (channelMessageModel.isSystemMessage != null) {
            channelMessageJsonObject.addProperty("isSystemMessage", channelMessageModel.isSystemMessage);
        }
        if (channelMessageModel.attachment != null) {
            try {
                JsonArray jsonArray = new JsonArray();

                if (GuidUtil.isRequestGuid(channelMessageModel.attachment)) {
                    byte[] encryptedDataFromAttachment = AttachmentController.loadEncryptedBase64AttachmentFile(channelMessageModel.attachment);

                    if ((encryptedDataFromAttachment != null) && (encryptedDataFromAttachment.length > 0)) {
                        String attachment = new String(encryptedDataFromAttachment, "US-ASCII");
                        jsonArray.add(new JsonPrimitive(attachment));
                    }
                } else {
                    jsonArray.add(new JsonPrimitive(channelMessageModel.attachment));
                }

                if (jsonArray.size() > 0) {
                    channelMessageJsonObject.add("attachment", jsonArray);
                }
            } catch (LocalizedException | UnsupportedEncodingException e) {
                return null;
            }
        }

        //    if (privateMessageModel.mimeType != null)
        //    {
        //       privateMessageJsonObject.addProperty("messageType", privateMessageModel.mimeType);
        //    }

        jsonObject.add("ChannelMessage", channelMessageJsonObject);
        return jsonObject;
    }
}
