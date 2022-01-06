// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.InternalMessageModel;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.log.LogUtil;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class InternalMessageModelDeserializer
        implements JsonDeserializer<InternalMessageModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public InternalMessageModel deserialize(JsonElement jsonElement,
                                            Type type,
                                            JsonDeserializationContext context)
            throws JsonParseException {
        final JsonObject jsonObject = jsonElement.getAsJsonObject().get("InternalMessage")
                .getAsJsonObject();

        final String guid = jsonObject.has("guid") ? jsonObject.get("guid").getAsString()
                : null;
        final String from = jsonObject.has("from") ? jsonObject.get("from").getAsString()
                : null;
        final String to = jsonObject.has("to") ? jsonObject.get("to").getAsString()
                : null;
        final String data = jsonObject.has("data") ? jsonObject.get("data").toString()
                : null;

        final InternalMessageModel internalMessageModel = new InternalMessageModel();

        internalMessageModel.guid = guid;
        internalMessageModel.from = from;
        internalMessageModel.to = to;

        internalMessageModel.data = (data != null) ? data.getBytes(StandardCharsets.UTF_8) : null;

        return internalMessageModel;
    }
}
