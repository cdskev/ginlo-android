// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.GroupMessageModel;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.log.LogUtil;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.Date;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class GroupMessageModelDeserializer
        implements JsonDeserializer<GroupMessageModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public GroupMessageModel deserialize(JsonElement jsonElement,
                                         Type type,
                                         JsonDeserializationContext context)
            throws JsonParseException {
        final JsonObject jsonObject = jsonElement.getAsJsonObject().get("GroupMessage").getAsJsonObject();

        final String senderId = jsonObject.has("senderId") ? jsonObject.get("senderId").getAsString() : null;

        final String guid = jsonObject.has("guid") ? jsonObject.get("guid").getAsString() : null;

        final KeyContainerModel from = context.deserialize(jsonObject.get("from"), KeyContainerModel.class);
        final String to = jsonObject.get("to").getAsString();

        final String data = jsonObject.has("data") ? jsonObject.get("data").getAsString() : null;
        final String attachment = (
                jsonObject.has("attachment")
                        && (jsonObject.get("attachment").getAsJsonArray().size() > 0)
        ) ? jsonObject.get("attachment").getAsJsonArray().get(0).getAsString()
                : null;
        final Date datesend = jsonObject.has("datesend")
                ? DateUtil.utcStringToDate(jsonObject.get("datesend").getAsString())
                : null;

        //    final SignatureModel signature = context.deserialize(jsonObject.get("signature"), SignatureModel.class);
      /*final String            requestGuid       = jsonObject.has("requestGuid")
                                                  ? jsonObject.get("requestGuid").getAsString() : null;*/
        final Boolean isSystemMessage = jsonObject.has("isSystemMessage")
                ? jsonObject.get("isSystemMessage").getAsBoolean() : null;

        final GroupMessageModel groupMessageModel = new GroupMessageModel();

        groupMessageModel.requestGuid = senderId;
        groupMessageModel.guid = guid;
        groupMessageModel.from = from;
        groupMessageModel.to = to;
        groupMessageModel.attachment = attachment;
        groupMessageModel.data = data;
        groupMessageModel.datesend = datesend;
        //groupMessageModel.requestGuid = requestGuid;

        //    groupMessageModel.signature = signature;
        try {
            groupMessageModel.signatureBytes = jsonObject.get("signature").toString().getBytes(Encoding.UTF8);
        } catch (UnsupportedEncodingException e) {
            LogUtil.w(GroupMessageModelDeserializer.class.getSimpleName(), e.getMessage(), e);
        }
        groupMessageModel.isSystemMessage = isSystemMessage;
        return groupMessageModel;
    }
}
