// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.ChannelMessageModel;
import eu.ginlo_apps.ginlo.util.DateUtil;

import java.lang.reflect.Type;
import java.util.Date;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ChannelMessageModelDeserializer
        implements JsonDeserializer<ChannelMessageModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public ChannelMessageModel deserialize(JsonElement jsonElement,
                                           Type type,
                                           JsonDeserializationContext context)
            throws JsonParseException {
        if (jsonElement == null) {
            return null;
        }

        final JsonElement jsonChannelElement = jsonElement.getAsJsonObject().get("ChannelMessage");

        if (jsonChannelElement == null) {
            return null;
        }

        final JsonObject jsonObject = jsonChannelElement.getAsJsonObject();

        final String guid = jsonObject.has("guid") ? jsonObject.get("guid").getAsString() : null;
        final String to = jsonObject.has("to") ? jsonObject.get("to").getAsString() : null;
        final String data = jsonObject.has("data") ? jsonObject.get("data").getAsString() : null;
        final String attachment = (
                jsonObject.has("attachment")
                        && (jsonObject.get("attachment").getAsJsonArray().size() > 0)
        ) ? jsonObject.get("attachment").getAsJsonArray().get(0).getAsString()
                : null;
        final Date datesend = jsonObject.has("datesend")
                ? DateUtil.utcStringToDate(jsonObject.get("datesend").getAsString()) : null;
        final String features = jsonObject.has("features") ? jsonObject.get("features").getAsString()
                : null;
        final Boolean isSystemMessage = jsonObject.has("isSystemMessage")
                ? jsonObject.get("isSystemMessage").getAsBoolean() : null;

        ChannelMessageModel msgModel = new ChannelMessageModel();

        msgModel.guid = guid;
        msgModel.to = to;
        msgModel.data = data;
        msgModel.attachment = attachment;
        msgModel.datesend = datesend;
        msgModel.features = features;
        msgModel.isSystemMessage = isSystemMessage;

        return msgModel;
    }
}
