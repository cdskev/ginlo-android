// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import eu.ginlo_apps.ginlo.model.backend.MessageReceiverModel;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.lang.reflect.Type;
import java.util.Date;

/**
 * Created by Florian on 08.09.16.
 */
public class MessageReceiverModelSerializer implements JsonSerializer<MessageReceiverModel> {
    @Override
    public JsonElement serialize(MessageReceiverModel messageReceiverModel, Type type, JsonSerializationContext jsonSerializationContext) {
        if (messageReceiverModel == null) {
            return null;
        }

        final JsonObject jsonObject = new JsonObject();
        final JsonObject messageReceiverJO = new JsonObject();

        if (StringUtil.isNullOrEmpty(messageReceiverModel.guid)) {
            return null;
        }

        messageReceiverJO.addProperty("guid", messageReceiverModel.guid);

        String value = messageReceiverModel.sendsReadConfirmation == 1 ? "1" : "0";
        messageReceiverJO.addProperty("sendsReadConfirmation", value);

        if (messageReceiverModel.dateDownloaded > 0) {
            messageReceiverJO.addProperty("dateDownloaded", DateUtil.dateToUtcString(new Date(messageReceiverModel.dateDownloaded)));
        }

        if (messageReceiverModel.dateRead > 0) {
            messageReceiverJO.addProperty("dateRead", DateUtil.dateToUtcString(new Date(messageReceiverModel.dateRead)));
        }

        jsonObject.add(AppConstants.MESSAGE_JSON_RECEIVER, messageReceiverJO);

        return jsonObject;
    }
}
