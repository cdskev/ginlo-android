// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.ChannelListModel;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ChannelListModelDeserializer
        implements JsonDeserializer<ChannelListModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public ChannelListModel deserialize(JsonElement jsonElement,
                                        Type type,
                                        JsonDeserializationContext context)
            throws JsonParseException {
        if (jsonElement == null) {
            return null;
        }

        final JsonObject jsonObject = jsonElement.getAsJsonObject();

        if (!jsonObject.has("Channel")) {
            return null;
        }

        final JsonObject jsonChannel = jsonObject.getAsJsonObject("Channel");

        ChannelListModel cm = new ChannelListModel();

        if (!jsonChannel.has("guid")) {
            return null;
        }

        cm.guid = jsonChannel.get("guid").getAsString();
        cm.shortDesc = jsonChannel.has("short_desc") ? jsonChannel.get("short_desc").getAsString() : null;
        cm.checksum = jsonChannel.has("checksum") ? jsonChannel.get("checksum").getAsString() : null;

        return cm;
    }
}
