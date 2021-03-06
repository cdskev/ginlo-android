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

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class PrivateInternalMessageModelSerializer
        implements JsonSerializer<PrivateInternalMessageModel> {

    @Override
    public JsonElement serialize(PrivateInternalMessageModel privateInternalMessageModel,
                                 Type type,
                                 JsonSerializationContext context) {
        final JsonObject jsonObject = new JsonObject();
        final JsonObject privateMessageJsonObject = new JsonObject();

        if (privateInternalMessageModel.requestGuid != null) {
            privateMessageJsonObject.addProperty("senderId", privateInternalMessageModel.requestGuid);
        }
        if (privateInternalMessageModel.guid != null) {
            privateMessageJsonObject.addProperty("guid", privateInternalMessageModel.guid);
        }
        if (privateInternalMessageModel.from != null) {
            privateMessageJsonObject.add("from",
                    context.serialize(privateInternalMessageModel.from, KeyContainerModel.class));
        }
        if (privateInternalMessageModel.to != null) {
            privateMessageJsonObject.add("to",
                    context.serialize(privateInternalMessageModel.to, KeyContainerModel[].class));
        }
        if (privateInternalMessageModel.data != null) {
            privateMessageJsonObject.addProperty("data", privateInternalMessageModel.data);
        }
        if (privateInternalMessageModel.datesend != null) {
            privateMessageJsonObject.addProperty("datesend",
                    DateUtil.dateToUtcString(privateInternalMessageModel.datesend));
        }
      /*if (privateInternalMessageModel.requestGuid != null)
      {
         privateMessageJsonObject.addProperty("requestGuid", privateInternalMessageModel.requestGuid);
      }*/

        //    if (privateInternalMessageModel.signature != null)
        //       privateMessageJsonObject.add("signature",
        //                    context.serialize(privateInternalMessageModel.signature, SignatureModel.class));

        if (privateInternalMessageModel.signatureSha256Bytes != null) {
            JsonParser parser = new JsonParser();

            JsonElement element = parser.parse(new String(privateInternalMessageModel.signatureSha256Bytes, StandardCharsets.UTF_8));

            privateMessageJsonObject.add("signature-sha256", element);
        }

        if (privateInternalMessageModel.signatureBytes != null) {
            JsonParser parser = new JsonParser();

            JsonElement element = parser.parse(new String(privateInternalMessageModel.signatureBytes, StandardCharsets.UTF_8));

            privateMessageJsonObject.add("signature", element);
        }

        if (privateInternalMessageModel.mimeType != null) {
            privateMessageJsonObject.addProperty("messageType", privateInternalMessageModel.mimeType);
        }

        jsonObject.add("PrivateInternalMessage", privateMessageJsonObject);
        return jsonObject;
    }
}
