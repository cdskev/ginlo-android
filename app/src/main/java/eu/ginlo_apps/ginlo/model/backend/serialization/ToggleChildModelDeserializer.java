// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.ToggleChildModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleModel;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Id$
 */
public class ToggleChildModelDeserializer
        implements JsonDeserializer<ToggleChildModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public ToggleChildModel deserialize(final JsonElement jsonElement,
                                        final Type type,
                                        final JsonDeserializationContext context)
            throws JsonParseException {
        if (jsonElement == null) {
            return null;
        }

        final JsonObject jsonObject = jsonElement.getAsJsonObject();

        final ToggleChildModel tm = new ToggleChildModel();

        tm.forToggle = jsonObject.has("forToggle") ? jsonObject.get("forToggle").getAsString() : null;

        if (jsonObject.has("@items")) {
            tm.items = context.deserialize(jsonObject.get("@items"), ToggleModel[].class);
        }

        return tm;
    }
}
