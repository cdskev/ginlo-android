// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import eu.ginlo_apps.ginlo.model.backend.ConfirmMessageSendModel;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ConfirmMessageSendModelDeserializer
        implements JsonDeserializer<ConfirmMessageSendModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public ConfirmMessageSendModel deserialize(JsonElement jsonElement,
                                               Type type,
                                               JsonDeserializationContext context)
            throws JsonParseException {
        return ConfirmMessageSendModel.createFromJson(jsonElement.getAsJsonObject());
    }
}
