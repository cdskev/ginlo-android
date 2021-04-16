// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import eu.ginlo_apps.ginlo.model.backend.DeviceModel;
import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class DeviceModelSerializer
        implements JsonSerializer<DeviceModel> {

    @Override
    public JsonElement serialize(DeviceModel deviceModel,
                                 Type type,
                                 JsonSerializationContext context) {
        final JsonObject jsonObject = new JsonObject();
        final JsonObject deviceJsonObject = new JsonObject();

        if (deviceModel.guid != null) {
            deviceJsonObject.addProperty("guid", deviceModel.guid);
        }
        if (deviceModel.accountGuid != null) {
            deviceJsonObject.addProperty("accountGuid", deviceModel.accountGuid);
        }
        if (deviceModel.publicKey != null) {
            deviceJsonObject.addProperty("publicKey", deviceModel.publicKey);
        }
        if (deviceModel.pkSign != null) {
            deviceJsonObject.addProperty("pkSign", deviceModel.pkSign);
        }
        if (deviceModel.passtoken != null) {
            deviceJsonObject.addProperty("passtoken", deviceModel.passtoken);
        }
        if (deviceModel.language != null) {
            deviceJsonObject.addProperty("language", deviceModel.language);
        }
        if (deviceModel.apnIdentifier != null) {
            deviceJsonObject.addProperty("apnIdentifier", deviceModel.apnIdentifier);
        }
        if (deviceModel.appName != null) {
            deviceJsonObject.addProperty("appName", deviceModel.appName);
        }
        if (deviceModel.appVersion != null) {
            deviceJsonObject.addProperty("appVersion", deviceModel.appVersion);
        }
        if (deviceModel.os != null) {
            deviceJsonObject.addProperty("os", deviceModel.os);
        }

        if (deviceModel.featureVersion != null) {
            JsonArray featureVersions = new JsonArray();

            for (int i = 0; i < deviceModel.featureVersion.length; i++) {
                featureVersions.add(new JsonPrimitive(Integer.toString(deviceModel.featureVersion[i])));
            }
            deviceJsonObject.add("features", featureVersions);
        }

        jsonObject.add("Device", deviceJsonObject);
        return jsonObject;
    }
}
