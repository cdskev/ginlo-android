// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import eu.ginlo_apps.ginlo.model.backend.SignatureModel;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class SignatureModelSerializer
        implements JsonSerializer<SignatureModel> {

    @Override
    public JsonElement serialize(SignatureModel signatureModel,
                                 Type type,
                                 JsonSerializationContext context) {
        return signatureModel.getModel(false);
    }
}
