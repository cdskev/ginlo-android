// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.MessageReceiverModel;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.lang.reflect.Type;

/**
 * Created by Florian on 08.09.16.
 */
public class MessageReceiverModelDeserializer implements JsonDeserializer<MessageReceiverModel> {
    @Override
    public MessageReceiverModel deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
            throws JsonParseException {
        if (jsonElement == null || !jsonElement.isJsonObject()) {
            return null;
        }

        final JsonObject jsonObject = jsonElement.getAsJsonObject().get(AppConstants.MESSAGE_JSON_RECEIVER).getAsJsonObject();

        MessageReceiverModel messageReceiverModel = new MessageReceiverModel();
        messageReceiverModel.guid = JsonUtil.stringFromJO("guid", jsonObject);

        String sendsReadConfirmation = JsonUtil.stringFromJO("sendsReadConfirmation", jsonObject);

        if (!StringUtil.isNullOrEmpty(sendsReadConfirmation)) {
            try {
                messageReceiverModel.sendsReadConfirmation = Integer.parseInt(sendsReadConfirmation);
            } catch (NumberFormatException e) {
                messageReceiverModel.sendsReadConfirmation = 0;
            }
        }

        String dateDownloaded = JsonUtil.stringFromJO("dateDownloaded", jsonObject);

        if (!StringUtil.isNullOrEmpty(dateDownloaded)) {
            messageReceiverModel.dateDownloaded = DateUtil.utcStringToMillis(dateDownloaded);
        }

        String dateRead = JsonUtil.stringFromJO("dateRead", jsonObject);

        if (!StringUtil.isNullOrEmpty(dateRead)) {
            messageReceiverModel.dateRead = DateUtil.utcStringToMillis(dateRead);
        }

        return messageReceiverModel;
    }
}
