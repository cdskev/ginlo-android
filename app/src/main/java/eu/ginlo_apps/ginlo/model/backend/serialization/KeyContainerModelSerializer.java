// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class KeyContainerModelSerializer
        implements JsonSerializer<KeyContainerModel> {

    @Override
    public JsonElement serialize(KeyContainerModel keyContainerModel,
                                 Type type,
                                 JsonSerializationContext context) {
        final JsonObject keyContainerObject = new JsonObject();
        final JsonObject innerContainerObject = new JsonObject();

        innerContainerObject.addProperty("key", keyContainerModel.keyContainer);
        if (!StringUtil.isNullOrEmpty(keyContainerModel.key2)) {
            innerContainerObject.addProperty("key2", keyContainerModel.key2);
        }

        if (keyContainerModel.nickname != null) {
            innerContainerObject.addProperty("nickname", keyContainerModel.nickname);
        }

        if (!StringUtil.isNullOrEmpty(keyContainerModel.tempDeviceGuid)) {
            final JsonObject tempDeviceObject = new JsonObject();
            tempDeviceObject.addProperty("guid", keyContainerModel.tempDeviceGuid);
            tempDeviceObject.addProperty("key", keyContainerModel.tempDeviceKey);
            tempDeviceObject.addProperty("key2", keyContainerModel.tempDeviceKey2);

            innerContainerObject.add("tempDevice", tempDeviceObject);
        }

        keyContainerObject.add(keyContainerModel.guid, innerContainerObject);

        return keyContainerObject;
    }
}
