// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import eu.ginlo_apps.ginlo.model.backend.ToggleSettingsModel;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ToggleSettingsModelSerializer
        implements JsonSerializer<ToggleSettingsModel> {

    @Override
    public JsonElement serialize(ToggleSettingsModel tsModel,
                                 Type type,
                                 JsonSerializationContext context) {
        if (tsModel == null) {
            return null;
        }

        final JsonObject tsObject = new JsonObject();

        tsObject.addProperty("filter", tsModel.filter);
        tsObject.addProperty("value", tsModel.value);

        return tsObject;
    }
}
