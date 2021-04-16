// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import android.graphics.Color;
import androidx.core.graphics.ColorUtils;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.ChannelLayoutModel;

/**
 * Created by Florian on 14.07.15.
 */
public class ChannelColorUtil {

    private final ChannelLayoutModel mLayout;

    private final Context mContext;

    public ChannelColorUtil(ChannelLayoutModel layout,
                            Context context) {
        mLayout = layout;
        mContext = context;
    }

    public static int getAlphaColor(int color,
                                    float alpha) {
        final int originalAlpha = Color.alpha(color);

        return ColorUtils.setAlphaComponent(color, Math.round(originalAlpha * alpha)); //wir muessen keine Transparenzen auf Millionstel ausrechnen...
    }

    /**
     * Backgroundcolor. Zu pruefen auf -1. Dann liegt ein Bild vor udn die BgColor soll nicht verwendet werden
     *
     * @return return 0 for no color or the color
     */
    public int getIbColor() {
        return getColor((mLayout != null) ? mLayout.ibColor : null, 0);
    }

    /**
     * Backgroundcolor. Zu pruefen auf -1. Dann liegt ein Bild vor udn die BgColor soll nicht verwendet werden
     *
     * @return return 0 for no color or the color
     */
    public int getCbColor() {
        return getColor((mLayout != null) ? mLayout.cbColor : null, 0);
    }

    private int getColor(String colorString,
                         int defaultColorResource) {
        int color = Integer.MIN_VALUE;

        if ((colorString != null) && (colorString.length() >= 2)) {
            try {
                String colorStringNormal = colorString;

                if (colorString.charAt(0) != '#') {
                    colorStringNormal = "#" + colorString;
                }

                color = Color.parseColor(colorStringNormal);
            } catch (IllegalArgumentException e) {
                LogUtil.w(ChannelColorUtil.class.getSimpleName(), e.getMessage(), e);
            }
        }

        if (color == Integer.MIN_VALUE) {
            if (defaultColorResource == 0) {
                //falls keine Farbe fuer den bg vorhanden ist, gib 0 zurück
                color = 0;
            } else if (mContext != null) {
                color = mContext.getResources().getColor(defaultColorResource);
            } else {
                color = Color.BLACK;
            }
        }

        return color;
    }

    public int getColorLabelDisable() {
        return getColor((mLayout != null) ? mLayout.colorLabelDisable : null, R.color.settings_disable_default);
    }

    public int getColorLabelEnable() {
        return getColor((mLayout != null) ? mLayout.colorLabelEnable : null, R.color.settings_enable_default);
    }

    public int getColorText() {
        return getColor((mLayout != null) ? mLayout.colorText : null, R.color.settings_text_default);
    }

    public int getOverviewColorTime() {
        return getColor((mLayout != null) ? mLayout.overviewColorTime : null, R.color.settings_overview_color_time);
    }

    public int getOverviewBkColorBubble() {
        return getColor((mLayout != null) ? mLayout.overviewBkColorBubble : null,
                R.color.settings_overview_bkcolor_bubble);
    }

    public int getOverviewColorBubble() {
        return getColor((mLayout != null) ? mLayout.overviewColorBubble : null, R.color.settings_overview_color_bubble);
    }

    public int getMsgColorSectionInactive() {
        return getColor((mLayout != null) ? mLayout.msgColorSectionInactive : null,
                R.color.settings_msg_color_section_inactive);
    }

    public int getMsgColorSectionActive() {
        return getColor((mLayout != null) ? mLayout.msgColorSectionActive : null,
                R.color.settings_msg_color_section_active);
    }

    public int getMsgColorTime() {
        return getColor((mLayout != null) ? mLayout.msgColorTime : null, R.color.settings_msg_color_time);
    }

    public int getHeadBkColor() {
        return getColor((mLayout != null) ? mLayout.headBkColor : null, R.color.settings_head_bk_color_default);
    }

    public int getHeadColor() {
        return getColor((mLayout != null) ? mLayout.headColor : null, R.color.settings_head_color_default);
    }

    public int getSettingsColorToggle() {
        return getColor((mLayout != null) ? mLayout.settingsColorToggle : null, R.color.settings_toggle_color_default);
    }
}
