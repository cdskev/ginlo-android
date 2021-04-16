// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import eu.ginlo_apps.ginlo.model.backend.BaseModel;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ConfirmMessageSendModel
        extends BaseModel {

    public long datesend;

    public String[] notSend;

    public JsonArray receiver;

    public static ConfirmMessageSendModel createFromJson(JsonObject outerJsonObject) {
        final JsonObject jsonObject = JsonUtil.searchJsonObjectRecursive(outerJsonObject, "ConfirmMessageSend");
        if (jsonObject == null) {
            return null;
        }

        ConfirmMessageSendModel confirmMessageSendModel = new ConfirmMessageSendModel();

        confirmMessageSendModel.guid = JsonUtil.stringFromJO("guid", jsonObject);
        String dateString = JsonUtil.stringFromJO("datesend", jsonObject);
        if (!StringUtil.isNullOrEmpty(dateString)) {
            confirmMessageSendModel.datesend = DateUtil.utcStringToMillis(dateString);
        }

        if (JsonUtil.hasKey("not-send", jsonObject)) {
            confirmMessageSendModel.notSend = JsonUtil.getStringArrayFromJsonArray((JsonArray) jsonObject.get("not-send"));
        }

        if (JsonUtil.hasKey(AppConstants.MESSAGE_JSON_RECEIVERS, jsonObject)
                && jsonObject.get(AppConstants.MESSAGE_JSON_RECEIVERS) != null
                && !StringUtil.isEqual(jsonObject.get(AppConstants.MESSAGE_JSON_RECEIVERS).toString(), "null")
        ) {
            confirmMessageSendModel.receiver = jsonObject.getAsJsonArray(AppConstants.MESSAGE_JSON_RECEIVERS);
        }

        return confirmMessageSendModel;
    }
}
