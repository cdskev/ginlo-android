// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class KeyContainerModelDeserializer
        implements JsonDeserializer<KeyContainerModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public KeyContainerModel deserialize(JsonElement jsonElement,
                                         Type type,
                                         JsonDeserializationContext context)
            throws JsonParseException {
        KeyContainerModel keyContainerModel = null;
        final JsonObject jsonObject = jsonElement.getAsJsonObject();

        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            final JsonObject keyContainerObject = entry.getValue().getAsJsonObject();

            JsonElement key2 = keyContainerObject.get("key2");
            String key2String = key2 == null ? null : key2.toString();

            keyContainerModel = new KeyContainerModel(entry.getKey(), keyContainerObject.get("key").getAsString(), key2String);
        }
        return keyContainerModel;
    }
}
