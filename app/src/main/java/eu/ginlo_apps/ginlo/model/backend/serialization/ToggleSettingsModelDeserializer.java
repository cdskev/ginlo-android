// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.ToggleSettingsModel;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ToggleSettingsModelDeserializer
        implements JsonDeserializer<ToggleSettingsModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public ToggleSettingsModel deserialize(JsonElement jsonElement,
                                           Type type,
                                           JsonDeserializationContext context)
            throws JsonParseException {
        if (jsonElement == null) {
            return null;
        }

        final JsonObject jsonObject = jsonElement.getAsJsonObject();

        ToggleSettingsModel tsModel = new ToggleSettingsModel();

        tsModel.filter = jsonObject.has("filter") ? jsonObject.get("filter").getAsString() : null;
        tsModel.value = jsonObject.has("value") ? jsonObject.get("value").getAsString() : null;

        return tsModel;
    }
}
