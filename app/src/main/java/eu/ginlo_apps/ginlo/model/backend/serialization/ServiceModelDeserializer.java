// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.ChannelLayoutModel;
import eu.ginlo_apps.ginlo.model.backend.ServiceModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleModel;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ServiceModelDeserializer
        implements JsonDeserializer<ServiceModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public ServiceModel deserialize(JsonElement jsonElement,
                                    Type type,
                                    JsonDeserializationContext context)
            throws JsonParseException {
        if (jsonElement == null) {
            return null;
        }

        final JsonElement jsonChannelElement = jsonElement.getAsJsonObject().get("Service");

        if (jsonChannelElement == null) {
            return null;
        }

        final JsonObject jsonObject = jsonChannelElement.getAsJsonObject();

        ServiceModel cm = new ServiceModel();

        cm.serviceId = jsonObject.has("serviceId") ? jsonObject.get("serviceId").getAsString() : null;
        cm.shortDesc = jsonObject.has("short_desc") ? jsonObject.get("short_desc").getAsString() : null;
        cm.desc = jsonObject.has("desc") ? jsonObject.get("desc").getAsString() : null;
        cm.options = jsonObject.has("options") ? jsonObject.get("options").getAsString() : null;
        cm.aesKey = jsonObject.has("aes_key") ? jsonObject.get("aes_key").getAsString() : null;
        cm.iv = jsonObject.has("iv") ? jsonObject.get("iv").getAsString() : null;
        cm.shortLinkText = jsonObject.has("shortLinkText") ? jsonObject.get("shortLinkText").toString() : null;
        cm.checksum = jsonObject.has("checksum") ? jsonObject.get("checksum").getAsString() : null;

      /*
      if (jsonObject.has("promotion") == true)
      {
         cm.promotion = true;
         if (jsonObject.get("promotion").getAsJsonObject().has("externalUrl") == true)
         {
            cm.externalUrl = jsonObject.get("promotion").getAsJsonObject().get("externalUrl").getAsString();
         }
         else
         {
            cm.externalUrl = null;
         }
      }
      else
      {
         cm.promotion   = false;
         cm.externalUrl = null;
      }
      */

        //cm.category        = jsonObject.has("@categories") ? jsonObject.get("@categories").getAsJsonArray().toString() : null;
        cm.searchText = jsonObject.has("searchText") ? jsonObject.get("searchText").getAsString() : null;

        cm.welcomeText = jsonObject.has("welcomeText") ? jsonObject.get("welcomeText").getAsString() : null;
        cm.suggestionText = jsonObject.has("suggestionText") ? jsonObject.get("suggestionText").getAsString() : null;
        cm.feedbackContact = jsonObject.has("feedbackContact") ? jsonObject.get("feedbackContact").toString() : null;

        if (jsonObject.has("@items")) {
            cm.toggles = context.deserialize(jsonObject.get("@items"), ToggleModel[].class);
        }

        if (jsonObject.has("layout")) {
            cm.layout = context.deserialize(jsonObject.get("layout"), ChannelLayoutModel.class);
        }

        cm.channelJsonObject = jsonElement.toString();

        return cm;
    }
}
