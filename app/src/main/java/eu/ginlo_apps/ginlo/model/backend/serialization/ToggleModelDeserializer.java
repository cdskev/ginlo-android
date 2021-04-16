// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.ToggleChildModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleModel;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ToggleModelDeserializer
        implements JsonDeserializer<ToggleModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public ToggleModel deserialize(JsonElement jsonElement,
                                   Type type,
                                   JsonDeserializationContext context)
            throws JsonParseException {
        if (jsonElement == null) {
            return null;
        }

        final JsonObject jsonObject = jsonElement.getAsJsonObject();

        ToggleModel tm = new ToggleModel();

        //    if( !jsonObject.has("type") || !jsonObject.get("type").getAsString().equalsIgnoreCase("toggle") )
        //    {
        //       return null;
        //    }

        //    tm.type = jsonObject.get("type").getAsString();
        if (hasKey(jsonObject, "filter_off")) {
            tm.filterOff = jsonObject.get("filter_off").getAsString();
        }

        //    tm.filterOff = hasKey(jsonObject,"filter_off") ? jsonObject.get("filter_off").getAsString() : null;
        tm.filterOn = jsonObject.has("filter_on") ? jsonObject.get("filter_on").getAsString() : null;
        tm.ident = jsonObject.has("ident") ? jsonObject.get("ident").getAsString() : null;
        tm.label = jsonObject.has("label") ? jsonObject.get("label").getAsString() : null;
        tm.defaultValue = jsonObject.has("default") ? jsonObject.get("default").getAsString() : null;

        if (jsonObject.has("@children")) {
            tm.children = context.deserialize(jsonObject.get("@children"), ToggleChildModel[].class);
        }

        return tm;
    }

    private boolean hasKey(JsonObject jo,
                           String key) {
        try {
            return jo.has(key);
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }
}
