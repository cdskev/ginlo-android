// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;
import eu.ginlo_apps.ginlo.model.backend.PrivateInternalMessageModel;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.log.LogUtil;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class PrivateInternalMessageModelDeserializer
        implements JsonDeserializer<PrivateInternalMessageModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public PrivateInternalMessageModel deserialize(JsonElement jsonElement,
                                                   Type type,
                                                   JsonDeserializationContext context)
            throws JsonParseException {
        final JsonObject jsonObject = jsonElement.getAsJsonObject()
                .get("PrivateInternalMessage")
                .getAsJsonObject();

        final String senderId = jsonObject.has("senderId") ? jsonObject.get("senderId").getAsString() : null;

        final String guid = jsonObject.has("guid")
                ? jsonObject.get("guid").getAsString() : null;

        final KeyContainerModel from = context.deserialize(jsonObject.get("from"),
                KeyContainerModel.class);
        final KeyContainerModel[] to = context.deserialize(jsonObject.get("to"),
                KeyContainerModel[].class);

        final String data = jsonObject.has("data")
                ? jsonObject.get("data").getAsString() : null;
        final String attachment = (
                jsonObject.has("attachment")
                        && (
                        jsonObject.get("attachment").getAsJsonArray()
                                .size() > 0
                )
        )
                ? jsonObject.get("attachment").getAsJsonArray()
                .get(0).getAsString() : null;
        final Date datesend = jsonObject.has("datesend")
                ? DateUtil.utcStringToDate(jsonObject.get("datesend")
                .getAsString()) : null;
        final Date datedownloaded = jsonObject.has("datedownloaded")
                ? DateUtil.utcStringToDate(jsonObject.get("datedownloaded")
                .getAsString()) : null;
        final Date dateread = jsonObject.has("dateread")
                ? DateUtil.utcStringToDate(jsonObject.get("dateread")
                .getAsString()) : null;

        //    final SignatureModel signature = context.deserialize(jsonObject.get("signature"), SignatureModel.class);
      /*final String                      requestGuid                 = jsonObject.has("requestGuid")
                                                                      ? jsonObject.get("requestGuid").getAsString()
                                                                      : null;*/

        final PrivateInternalMessageModel privateInternalMessageModel = new PrivateInternalMessageModel();

        privateInternalMessageModel.requestGuid = senderId;
        privateInternalMessageModel.guid = guid;
        privateInternalMessageModel.from = from;
        privateInternalMessageModel.to = to;
        privateInternalMessageModel.attachment = attachment;
        privateInternalMessageModel.data = data;
        privateInternalMessageModel.datedownloaded = datedownloaded;
        privateInternalMessageModel.dateread = dateread;
        privateInternalMessageModel.datesend = datesend;

        //    privateInternalMessageModel.signature = signature;
        privateInternalMessageModel.signatureBytes = jsonObject.get("signature").toString().getBytes(StandardCharsets.UTF_8);
        //privateInternalMessageModel.requestGuid = requestGuid;
        return privateInternalMessageModel;
    }
}
