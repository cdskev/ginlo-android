// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.MsgExceptionModel;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class MsgExceptionModelDeserializer
        implements JsonDeserializer<MsgExceptionModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public MsgExceptionModel deserialize(JsonElement jsonElement,
                                         Type type,
                                         JsonDeserializationContext context)
            throws JsonParseException {
        final JsonObject jsonObject = jsonElement.getAsJsonObject().get("MsgException").getAsJsonObject();

        final String message = jsonObject.has("message") ? jsonObject.get("message").getAsString()
                : null;
        final String ident = jsonObject.has("ident") ? jsonObject.get("ident").getAsString()
                : null;

        final MsgExceptionModel msgExceptionModel = new MsgExceptionModel();

        msgExceptionModel.setMessage(message);
        msgExceptionModel.setIdent(ident);

        return msgExceptionModel;
    }
}
