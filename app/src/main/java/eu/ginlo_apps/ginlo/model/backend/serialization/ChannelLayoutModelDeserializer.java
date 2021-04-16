// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model.backend.serialization;

import com.google.gson.*;
import eu.ginlo_apps.ginlo.model.backend.ChannelLayoutModel;

import java.lang.reflect.Type;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class ChannelLayoutModelDeserializer
        implements JsonDeserializer<ChannelLayoutModel> {

    /**
     * @throws JsonParseException [!EXC_DESCRIPTION!]
     */
    @Override
    public ChannelLayoutModel deserialize(JsonElement jsonElement,
                                          Type type,
                                          JsonDeserializationContext context)
            throws JsonParseException {
        if (jsonElement == null) {
            return null;
        }

        final JsonObject jsonObject = jsonElement.getAsJsonObject();

        ChannelLayoutModel cm = new ChannelLayoutModel();

        cm.colorLabelDisable = jsonObject.has("color_label_disable")
                ? jsonObject.get("color_label_disable").getAsString() : null;
        cm.colorLabelEnable = jsonObject.has("color_label_enable")
                ? jsonObject.get("color_label_enable").getAsString() : null;
        cm.colorText = jsonObject.has("color_text") ? jsonObject.get("color_text").getAsString() : null;

        cm.overviewColorTime = jsonObject.has("overview_color_time")
                ? jsonObject.get("overview_color_time").getAsString() : null;
        cm.overviewBkColorBubble = jsonObject.has("overview_bkcolor_bubble")
                ? jsonObject.get("overview_bkcolor_bubble").getAsString() : null;
        cm.overviewColorBubble = jsonObject.has("overview_color_bubble")
                ? jsonObject.get("overview_color_bubble").getAsString() : null;
        cm.msgColorSectionInactive = jsonObject.has("msg_color_section_inactive")
                ? jsonObject.get("msg_color_section_inactive").getAsString() : null;
        cm.msgColorSectionActive = jsonObject.has("msg_color_section_active")
                ? jsonObject.get("msg_color_section_active").getAsString() : null;
        cm.msgColorTime = jsonObject.has("msg_color_time") ? jsonObject.get("msg_color_time").getAsString()
                : null;
        cm.headBkColor = jsonObject.has("head_bkcolor") ? jsonObject.get("head_bkcolor").getAsString() : null;
        cm.headColor = jsonObject.has("head_color") ? jsonObject.get("head_color").getAsString() : null;
        cm.menuBkColor = jsonObject.has("menu_bkcolor") ? jsonObject.get("menu_bkcolor").getAsString() : null;
        cm.menuColor = jsonObject.has("menu_color") ? jsonObject.get("menu_color").getAsString() : null;
        cm.menuTheme = jsonObject.has("menu_theme") ? jsonObject.get("menu_theme").getAsString() : null;
        cm.settingsColorToggle = jsonObject.has("settings_color_toggle")
                ? jsonObject.get("settings_color_toggle").getAsString() : null;

        cm.ibColor = jsonObject.has("asset_ib_color") ? jsonObject.get("asset_ib_color").getAsString() : null;
        cm.cbColor = jsonObject.has("asset_cb_color") ? jsonObject.get("asset_cb_color").getAsString() : null;

        return cm;
    }
}
