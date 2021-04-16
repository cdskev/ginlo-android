// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.KeyContainerModel;
import eu.ginlo_apps.ginlo.model.backend.PrivateMessageModel;
import eu.ginlo_apps.ginlo.model.backend.serialization.PrivateInternalMessageModelDeserializer;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.log.LogUtil;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.Date;

/**
 * @author Florian
 * @version $Id$
 */
public class PrivateMessageModelDeserializer
        implements JsonDeserializer<PrivateMessageModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public PrivateMessageModel deserialize(final JsonElement jsonElement,
                                           final Type type,
                                           final JsonDeserializationContext context)
            throws JsonParseException {
        final JsonObject jsonObject = jsonElement.getAsJsonObject().get("PrivateMessage")
                .getAsJsonObject();

        final String senderId = jsonObject.has("senderId") ? jsonObject.get("senderId").getAsString() : null;

        final String guid = jsonObject.has("guid") ? jsonObject.get("guid").getAsString()
                : null;

        final KeyContainerModel from = context.deserialize(jsonObject.get("from"),
                KeyContainerModel.class);
        final KeyContainerModel[] to = context.deserialize(jsonObject.get("to"),
                KeyContainerModel[].class);

        final String data = jsonObject.has("data") ? jsonObject.get("data").getAsString()
                : null;
        final String attachment = (
                jsonObject.has("attachment")
                        && (jsonObject.get("attachment").getAsJsonArray().size() > 0)
        )
                ? jsonObject.get("attachment").getAsJsonArray().get(0)
                .getAsString() : null;

        final Date datesend = jsonObject.has("datesend")
                ? DateUtil.utcStringToDate(jsonObject.get("datesend")
                .getAsString()) : null;
        final Date datedownloaded = jsonObject.has("datedownloaded")
                ? DateUtil.utcStringToDate(jsonObject.get("datedownloaded")
                .getAsString()) : null;
        final Date dateread = jsonObject.has("dateread")
                ? DateUtil.utcStringToDate(jsonObject.get("dateread")
                .getAsString()) : null;

        //final SignatureModel      signature           = context.deserialize(jsonObject.get("signature"),
        //                                                                    SignatureModel.class);
      /*final String              requestGuid         = jsonObject.has("requestGuid")
                                                      ? jsonObject.get("requestGuid").getAsString() : null;*/
        final Boolean isSystemMessage = jsonObject.has("isSystemMessage")
                ? jsonObject.get("isSystemMessage").getAsBoolean() : null;

        final PrivateMessageModel privateMessageModel = new PrivateMessageModel();

        privateMessageModel.requestGuid = senderId;
        privateMessageModel.guid = guid;
        privateMessageModel.from = from;
        privateMessageModel.to = to;
        privateMessageModel.attachment = attachment;
        privateMessageModel.data = data;
        privateMessageModel.datedownloaded = datedownloaded;
        privateMessageModel.dateread = dateread;
        privateMessageModel.datesend = datesend;

        //privateMessageModel.signature      = signature;
        try {
            privateMessageModel.signatureBytes = jsonObject.get("signature").toString().getBytes(Encoding.UTF8);
        } catch (UnsupportedEncodingException e) {
            LogUtil.w(PrivateInternalMessageModelDeserializer.class.getSimpleName(), e.getMessage(), e);
        }

        //privateMessageModel.requestGuid     = requestGuid;
        privateMessageModel.isSystemMessage = isSystemMessage;
        return privateMessageModel;
    }
}
