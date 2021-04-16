// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.app.Application;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.tabs.TabLayout;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.model.Mandant;

import java.util.HashMap;
import java.util.Map;

public class ColorUtil {
    private static ColorUtil instance;
    private static Resources.Theme currentTheme;
    private static Map<String, String> tenantColorMap;
    private ColorUtil() {
        tenantColorMap = new HashMap<>();
        tenantColorMap.put("default_color", "#00C1A7");
        tenantColorMap.put("default_contrast_color", "#FFFFFF");
        tenantColorMap.put("ba_color", "#00C1A7");
        tenantColorMap.put("ba_contrast_color", "#FFFFFF");
    }

    public static ColorUtil getInstance() {
        if (instance == null) {
            instance = new ColorUtil();
        }
        return instance;
    }

    // KS
    public void setCurrentTheme(Resources.Theme theme) {
        currentTheme = theme;
    }

    public static void changeEditTextUnderlineColor(EditText editText, int focusedColor, int normalColor, int disabledColor) {
        LayerDrawable drawable = (LayerDrawable) ContextCompat.getDrawable(editText.getContext(),
                R.drawable.layer_bg_edittext);
        if (drawable == null) {
            return;
        }
        GradientDrawable gradientDrawableNormal = (GradientDrawable) drawable
                .findDrawableByLayerId(R.id.normal_layer);
        gradientDrawableNormal.setStroke(ViewUtil.convertDpToPixelInt(1), normalColor);

        LayerDrawable drawableDisabled = (LayerDrawable) ContextCompat.getDrawable(editText.getContext(),
                R.drawable.layer_bg_disabled_edittext);
        if (drawableDisabled == null) {
            return;
        }
        GradientDrawable gradientDrawableDisabled = (GradientDrawable) drawableDisabled
                .findDrawableByLayerId(R.id.disabled_layer);
        gradientDrawableDisabled.setStroke(ViewUtil.convertDpToPixelInt(1), disabledColor, ViewUtil.convertDpToPixelFloat(2), ViewUtil.convertDpToPixelFloat(1));

        LayerDrawable drawableFocused = (LayerDrawable) ContextCompat.getDrawable(editText.getContext(),
                R.drawable.layer_bg_focused_edittext);

        if (drawableFocused == null) {
            return;
        }
        GradientDrawable gradientDrawableFocused = (GradientDrawable) drawableFocused
                .findDrawableByLayerId(R.id.focused_layer);
        gradientDrawableFocused.setStroke(ViewUtil.convertDpToPixelInt(2), focusedColor);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, drawableFocused);
        states.addState(new int[]{-android.R.attr.state_enabled}, drawableDisabled);
        states.addState(new int[]{}, drawable);

        editText.setBackground(states);
    }

    private int getColorForTenant(final Application context, final String tenant, final boolean contrast) {
        if (tenantColorMap == null) {
            if (contrast) {
                return getMainContrastColor(context);
            } else {
                return getMainColor(context);
            }
        } else {
            if (contrast) {
                final String contrastColor = tenantColorMap.get(tenant + "_contrast_color");
                if (StringUtil.isNullOrEmpty(contrastColor)) {
                    return getMainContrastColor(context);
                } else {
                    return Color.parseColor(contrastColor);
                }
            } else {
                final String color = tenantColorMap.get(tenant + "_color");
                if (StringUtil.isNullOrEmpty(color)) {
                    return getMainColor(context);
                } else {
                    return Color.parseColor(color);
                }
            }
        }
    }

    @SuppressWarnings("unused")
    public boolean hasLayoutModel(final Application context) {
        return false;
    }

    public void colorizeMandantTextView(final Application context,
                                        final Mandant mandant,
                                        final TextView mandantTextView,
                                        boolean isPrivateIndexContact) {
        mandantTextView.setAllCaps(true);
        if (isPrivateIndexContact) {
            if (RuntimeConfig.isBAMandant() && StringUtil.isEqual(BuildConfig.SIMSME_MANDANT_DEFAULT, mandant.ident)) {
                mandantTextView.setText(context.getResources().getText(R.string.private_contact_label_text));
            } else {
                mandantTextView.setText(mandant.label);
            }
            mandantTextView.setTextColor(getColorForTenant(context, mandant.ident, true));
            mandantTextView.getBackground().setColorFilter(getColorForTenant(context, mandant.ident, false), PorterDuff.Mode.SRC_ATOP);
        } else {
            mandantTextView.setText(context.getResources().getText(R.string.intern_contact_label_text));
            mandantTextView.setTextColor(getHighContrastColor(context));
            mandantTextView.getBackground().setColorFilter(getHighColor(context), PorterDuff.Mode.SRC_ATOP);
        }
    }

    @SuppressWarnings("unused")
    public void reset(final Application context) {
        //do nothing
    }

    // Return color value for given color attribute in current theme
    public int getThemeAttributeColor(int color_attribute) {
        TypedValue tv = new TypedValue();
        currentTheme.resolveAttribute(color_attribute, tv, true);
        return tv.data;
    }

    // All programmatically modified colors ...
    // KS: Changed all getters to use colors from selected theme instead of fixed values
    public int getMainColor(final Application context) {
        return getThemeAttributeColor(R.attr.mainColor);
    }

    private int getMain50Color(final Application context) {
        return getThemeAttributeColor(R.attr.main50Color);
    }

    public int getMainContrastColor(final Application context) {
        return getThemeAttributeColor(R.attr.mainContrastColor);
    }

    public int getMainContrast80Color(final Application context) {
        return getThemeAttributeColor(R.attr.mainContrast80Color);
    }

    public int getMainContrast50Color(final Application context) {
        return getThemeAttributeColor(R.attr.mainContrast50Color);
    }

    public int getAppAccentColor(final Application context) {
        return getThemeAttributeColor(R.attr.actionColor);
    }

    public int getAppAccentContrastColor(final Application context) {
        return getThemeAttributeColor(R.attr.actionContrastColor);
    }

    private int getAppAccentSecondaryColor(final Application context) {
        return getThemeAttributeColor(R.attr.actionSecondaryColor);
    }

    public int getHighColor(final Application context) {
        return getThemeAttributeColor(R.attr.secureColor);
    }

    public int getHighContrastColor(final Application context) {
        return getThemeAttributeColor(R.attr.secureContrastColor);
    }

    public int getMediumColor(final Application context) {
        return getThemeAttributeColor(R.attr.mediumColor);
    }

    public int getMediumContrastColor(final Application context) {
        return getThemeAttributeColor(R.attr.mediumContrastColor);
    }

    public int getLowColor(final Application context) {
        return getThemeAttributeColor(R.attr.insecureColor);
    }

    public int getLowContrastColor(final Application context) {
        return getThemeAttributeColor(R.attr.insecureContrastColor);
    }

    private int getOverlayColor(final Application context) {
        return getThemeAttributeColor(R.attr.overlayColor);
    }

    private int getOverlayContrastColor(final Application context) {
        return getThemeAttributeColor(R.attr.overlayContrastColor);
    }

    public int getFabColor(final Application context) {
        return getThemeAttributeColor(R.attr.fabColor);
    }

    public int getFabIconColor(final Application context) {
        return getThemeAttributeColor(R.attr.fabIconColor);
    }

    private int getSendButtonColor(final Application context) {
        return getThemeAttributeColor(R.attr.sendButtonColor);
    }

    private int getSendButtonIconColor(final Application context) {
        return getThemeAttributeColor(R.attr.sendButtonIconColor);
    }

    private int getUnreadBubbleColor(final Application context) {
        return getThemeAttributeColor(R.attr.unreadBubbleColor);
    }

    private int getUnreadBubbleContrastColor(final Application context) {
        return getThemeAttributeColor(R.attr.unreadBubbleContrastColor);
    }

    public int getToolbarColor(final Application context) {
        return getThemeAttributeColor(R.attr.toolbarColor);
    }

    public Integer getNamedColor(String name, Application c) {
        switch (name) {
            case "main":
                return getMainColor(c);
            case "main50":
                return getMain50Color(c);
            case "mainContrast":
                return getMainContrastColor(c);
            case "mainContrast50":
                return getMainContrast50Color(c);
            case "mainContrast80":
                return getMainContrast80Color(c);
            case "action":
                return getAppAccentColor(c);
            case "actionContrast":
                return getAppAccentContrastColor(c);
            case "actionSecondary":
                return getAppAccentSecondaryColor(c);
            case "secure":
                return getHighColor(c);
            case "secureContrast":
                return getHighContrastColor(c);
            case "medium":
                return getMediumColor(c);
            case "mediumContrast":
                return getMediumContrastColor(c);
            case "insecure":
                return getLowColor(c);
            case "insecureContrast":
                return getLowContrastColor(c);
            case "overlay":
                return getOverlayColor(c);
            case "overlayContrast":
                return getOverlayContrastColor(c);
            case "fab":
                return getFabColor(c);
            case "fabIcon":
                return getFabIconColor(c);
            case "sendButtonColor":
                return getSendButtonColor(c);
            case "sendButtonIconColor":
                return getSendButtonIconColor(c);
            case "unreadBubble":
                return getUnreadBubbleColor(c);
            case "unreadBubbleContrast":
                return getUnreadBubbleContrastColor(c);
            case "toolbar":
                return getToolbarColor(c);
            default:
                return null;
        }
    }

    // These are not in getNamedColor()
    public int getFabOverviewColor(final Application context) {
        return getThemeAttributeColor(R.attr.fabOverviewColor);
    }

    public int getFabIconOverviewColor(final Application context) {
        return getThemeAttributeColor(R.attr.fabIconOverviewColor);
    }

    public int getChatItemColor(final Application context) {
        return getThemeAttributeColor(R.attr.chatItemColor);
    }

    public int getContextMainColor(final Application context) {
        return getThemeAttributeColor(R.attr.contextMainColor);
    }

    public int getContextTextColor(final Application context) {
        return getThemeAttributeColor(R.attr.contextTextColor);
    }

    // Return style value for given style attribute in current theme
    public int getThemeAttributeStyle(int style_attribute) {
        TypedValue tv = new TypedValue();
        currentTheme.resolveAttribute(style_attribute, tv, true);
        return tv.data;
    }

    public int getAlertDialogStyle(final Application context) {
        return getThemeAttributeStyle(R.attr.alertDialogTheme);
    }


    // Unused junk!
    @SuppressWarnings("unused")
    public void colorizeSwitch(final SwitchCompat switchCompat, final Application context) {
        //do nothing
    }

    @SuppressWarnings("unused")
    public void colorizeTabLayoutHeader(final Application context, final TabLayout tabLayout) {
        //do nothing
    }

}
