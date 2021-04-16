// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.ServiceListModel;

import java.lang.reflect.Type;

/**
 * @author SGA
 * @version $Revision$, $Date$, $Author$
 */
public class ServiceListModelDeserializer
        implements JsonDeserializer<ServiceListModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public ServiceListModel deserialize(JsonElement jsonElement,
                                        Type type,
                                        JsonDeserializationContext context)
            throws JsonParseException {
        if (jsonElement == null) {
            return null;
        }

        final JsonObject jsonObject = jsonElement.getAsJsonObject();

        if (!jsonObject.has("Service")) {
            return null;
        }

        final JsonObject serviceObject = jsonObject.getAsJsonObject("Service");

        ServiceListModel cm = new ServiceListModel();

        if (!serviceObject.has("guid")) {
            return null;
        }

        cm.guid = serviceObject.get("guid").getAsString();
        cm.serviceId = serviceObject.has("serviceId") ? serviceObject.get("serviceId").getAsString() : null;
        //String mandant     = serviceObject.has("mandant") ? serviceObject.get("mandant").getAsString() : null;
        cm.checksum = serviceObject.has("checksum") ? serviceObject.get("checksum").getAsString() : null;
        cm.shortDesc = serviceObject.has("short_desc") ? serviceObject.get("short_desc").getAsString() : null;

        return cm;
    }
}
