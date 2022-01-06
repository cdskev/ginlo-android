// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.SignatureModel;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class SignatureModelDeserializer
        implements JsonDeserializer<SignatureModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public SignatureModel deserialize(JsonElement jsonElement,
                                      Type type,
                                      JsonDeserializationContext context)
            throws JsonParseException {
        final JsonObject jsonObject = jsonElement.getAsJsonObject();

        return SignatureModel.parseModel(jsonObject);
    }
}
