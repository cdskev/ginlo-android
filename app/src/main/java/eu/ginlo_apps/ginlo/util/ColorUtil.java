// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.app.Application;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.model.Mandant;
import eu.ginlo_apps.ginlo.model.backend.CompanyLayoutModel;
import eu.ginlo_apps.ginlo.model.backend.serialization.CompanyLayoutDeserializer;

import static eu.ginlo_apps.ginlo.util.RuntimeConfig.isBAMandant;

public class ColorUtil {
    private static final String TAG = ColorUtil.class.getSimpleName();
    private static ColorUtil instance;
    private static Resources.Theme currentTheme;
    private static Map<String, String> tenantColorMap;
    private CompanyLayoutModel companyLayoutModel;

    public static final String COMPANY_LAYOUT_JSON = "AccountControllerBusiness.companyLayoutJson";

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

    // KS: setColorFilter is deprecated/has changed since API29
    public static void setColorFilter(Drawable drawable, @ColorInt int color) {
        if(drawable == null) {
            return;
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            drawable.setColorFilter(new BlendModeColorFilter(color, BlendMode.SRC_ATOP));
        } else {
            drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        }
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

    /**
     * Check if we have a mandatory company layout.
     * If yes, set up colors accordingly, if not yet done, and return 'true'
     * 'false' if we use client defaults
     * @param context
     * @return
     */
    public boolean hasLayoutModel(final Application context) {
        if(!isBAMandant()) {
            return false;
        }

        if (companyLayoutModel == null) {
            final SimsMeApplication simsMeApplication = (SimsMeApplication) context;

            final String layoutString = simsMeApplication.getPreferencesController().getSharedPreferences().getString(COMPANY_LAYOUT_JSON, null);

            if (!StringUtil.isNullOrEmpty(layoutString)) {
                final GsonBuilder gsonBuilder = new GsonBuilder();
                gsonBuilder.registerTypeAdapter(CompanyLayoutModel.class, new CompanyLayoutDeserializer());
                Gson gson = gsonBuilder.create();

                companyLayoutModel = gson.fromJson(layoutString, CompanyLayoutModel.class);
            }

            if(companyLayoutModel == null) {
                return false;
            }
        }
        // KS: We only have a company layout model, if color settings differ from default!
        return !companyLayoutModel.defaultSettings;
    }

    // Mandant Text View is the tag in contacts which shows type and origin of an account
    // KS: Found no other usage. (???)
    public void colorizeMandantTextView(final Application context,
                                        final Mandant mandant,
                                        final TextView mandantTextView,
                                        boolean isPrivateIndexContact) {
        mandantTextView.setAllCaps(true);
        if (isPrivateIndexContact) {
            if (isBAMandant() && StringUtil.isEqual(BuildConfig.SIMSME_MANDANT_DEFAULT, mandant.ident)) {
                mandantTextView.setText(context.getResources().getText(R.string.private_contact_label_text));
            } else {
                mandantTextView.setText(mandant.label);
            }
            mandantTextView.setTextColor(getColorForTenant(context, mandant.ident, true));
            mandantTextView.getBackground().setColorFilter(getColorForTenant(context, mandant.ident, false), PorterDuff.Mode.SRC_ATOP);
        } else {
            mandantTextView.setText(context.getResources().getText(R.string.intern_contact_label_text));
            mandantTextView.setTextColor(ColorUtil.getInstance().getHighContrastColor(context));
            mandantTextView.getBackground().setColorFilter(ColorUtil.getInstance().getHighColor(context), PorterDuff.Mode.SRC_ATOP);
        }
    }

    public void reset(final Application context) {
        companyLayoutModel = null;
        hasLayoutModel(context);
    }

    // Return style value for given style attribute in current theme
    public int getThemeAttributeStyle(int style_attribute) {
        TypedValue tv = new TypedValue();
        currentTheme.resolveAttribute(style_attribute, tv, true);
        return tv.data;
    }

    // Return style value for given size attribute in current theme
    public float getThemeAttributeTextSize(int size_attribute) {
        TypedValue tv = new TypedValue();
        currentTheme.resolveAttribute(size_attribute, tv, true);
        return tv.getFloat();
    }

    public Float getNamedTextSize(String name, Application c) {
        switch (name) {
            case "baseTextSize":
                return getThemeAttributeTextSize(R.attr.baseTextSize);
            case "messageTextSize":
                return getThemeAttributeTextSize(R.attr.messageTextSize);
            case "commentTextSize":
                return getThemeAttributeTextSize(R.attr.commentTextSize);
            case "labelTextSize":
                return getThemeAttributeTextSize(R.attr.labelTextSize);
            case "statusTextSize":
                return getThemeAttributeTextSize(R.attr.statusTextSize);
            case "chooserTextSize":
                return getThemeAttributeTextSize(R.attr.chooserTextSize);
            case "mainTitleTextSize":
                return getThemeAttributeTextSize(R.attr.mainTitleTextSize);
            case "subTitleTextSize":
                return getThemeAttributeTextSize(R.attr.subTitleTextSize);
            case "tanViewTextSize":
                return getThemeAttributeTextSize(R.attr.tanViewTextSize);
            case "tanEditTextSize":
                return getThemeAttributeTextSize(R.attr.tanEditTextSize);
            case "mediumTitleTextSize":
                return getThemeAttributeTextSize(R.attr.mediumTitleTextSize);
            default:
                return null;
        }
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
        if (hasLayoutModel(context) && companyLayoutModel.mainColor != 0) {
            return companyLayoutModel.mainColor;
        }
        return getThemeAttributeColor(R.attr.mainColor);
    }

    // Must recalculate here and ignore attribute color, because mainColor may be modified
    // by customer.
    private int getMain50Color(final Application context) {
        int main = getMainColor(context);
        return Color.argb(128,
                Color.red(main),
                Color.green(main),
                Color.blue(main));
    }

    public int getMainContrastColor(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.mainContrastColor != 0) {
            return companyLayoutModel.mainContrastColor;
        }
        return getThemeAttributeColor(R.attr.mainContrastColor);
    }

    public int getMainContrast80Color(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.mainContrastColor != 0) {
            return Color.argb(204,
                    Color.red(companyLayoutModel.mainContrastColor),
                    Color.green(companyLayoutModel.mainContrastColor),
                    Color.blue(companyLayoutModel.mainContrastColor));
        }
        return getThemeAttributeColor(R.attr.mainContrast80Color);
    }

    public int getMainContrast50Color(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.mainContrastColor != 0) {
            return Color.argb(128,
                    Color.red(companyLayoutModel.mainContrastColor),
                    Color.green(companyLayoutModel.mainContrastColor),
                    Color.blue(companyLayoutModel.mainContrastColor));
        }
        return getThemeAttributeColor(R.attr.mainContrast50Color);
    }

    public int getAppAccentColor(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.actionColor != 0) {
            return companyLayoutModel.actionColor;
        }
        return getThemeAttributeColor(R.attr.actionColor);
    }

    public int getAppAccentContrastColor(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.actionContrastColor != 0) {
            return companyLayoutModel.actionContrastColor;
        }
        return getThemeAttributeColor(R.attr.actionContrastColor);
    }

    private int getAppAccentSecondaryColor(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.actionColor != 0) {
            return companyLayoutModel.actionColor;
        }
        return getThemeAttributeColor(R.attr.actionSecondaryColor);
    }

    public int getHighColor(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.highColor != 0) {
            return companyLayoutModel.highColor;
        }
        return getThemeAttributeColor(R.attr.secureColor);
    }

    public int getHighContrastColor(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.highContrastColor != 0) {
            return companyLayoutModel.highContrastColor;
        }
        return getThemeAttributeColor(R.attr.secureContrastColor);
    }

    public int getMediumColor(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.mediumColor != 0) {
            return companyLayoutModel.mediumColor;
        }
        return getThemeAttributeColor(R.attr.mediumColor);
    }

    public int getMediumContrastColor(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.mediumContrastColor != 0) {
            return companyLayoutModel.mediumContrastColor;
        }
        return getThemeAttributeColor(R.attr.mediumContrastColor);
    }

    public int getLowColor(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.lowColor != 0) {
            return companyLayoutModel.lowColor;
        }
        return getThemeAttributeColor(R.attr.insecureColor);
    }

    public int getLowContrastColor(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.lowContrastColor != 0) {
            return companyLayoutModel.lowContrastColor;
        }
        return getThemeAttributeColor(R.attr.insecureContrastColor);
    }

    private int getOverlayColor(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.mainColor != 0) {
            return companyLayoutModel.mainColor;
        }
        return getThemeAttributeColor(R.attr.overlayColor);
    }

    private int getOverlayContrastColor(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.mainContrastColor != 0) {
            return companyLayoutModel.mainContrastColor;
        }
        return getThemeAttributeColor(R.attr.overlayContrastColor);
    }

    public int getFabColor(final Application context) {
        return getThemeAttributeColor(R.attr.fabColor);
    }

    public int getFabIconColor(final Application context) {
        return getThemeAttributeColor(R.attr.fabIconColor);
    }

    public int getAlertColor(final Application context) {
        return getThemeAttributeColor(R.attr.alertColor);
    }

    public int getBlackColor(final Application context) {
        return getThemeAttributeColor(R.attr.blackColor);
    }

    public int getWhiteColor(final Application context) {
        return getThemeAttributeColor(R.attr.whiteColor);
    }

    public int getTransparentColor(final Application context) {
        return getThemeAttributeColor(R.attr.transparentColor);
    }

    // Doesn't uses available individual theme color attribute
    // getThemeAttributeColor(R.attr.sendButtonColor);
    private int getSendButtonColor(final Application context) {
        return getMainContrastColor(context);
    }

    // Doesn't uses available individual theme color attribute
    // getThemeAttributeColor(R.attr.sendButtonIconColor);
    private int getSendButtonIconColor(final Application context) {
        return getMainColor(context);
    }

    private int getUnreadBubbleColor(final Application context) {
        return getThemeAttributeColor(R.attr.unreadBubbleColor);
    }

    private int getUnreadBubbleContrastColor(final Application context) {
        return getThemeAttributeColor(R.attr.unreadBubbleContrastColor);
    }

    public int getToolbarColor(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.mainColor != 0) {
            return companyLayoutModel.mainColor;
        }
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
            case "black":
                return getBlackColor(c);
            case "white":
                return getWhiteColor(c);
            case "alert":
                return getAlertColor(c);
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
        if (hasLayoutModel(context) && companyLayoutModel.actionColor != 0) {
            return companyLayoutModel.actionColor;
        }
        return getThemeAttributeColor(R.attr.fabOverviewColor);
    }

    public int getFabIconOverviewColor(final Application context) {
        if (hasLayoutModel(context) && companyLayoutModel.actionContrastColor != 0) {
            return companyLayoutModel.actionContrastColor;
        }
        return getThemeAttributeColor(R.attr.fabIconOverviewColor);
    }

    // Doesn't uses available individual theme color attribute
    // getThemeAttributeColor(R.attr.chatItemColor);
    public int getChatItemColor(final Application context) {
        return getAppAccentColor(context);
    }

    public int getContextMainColor(final Application context) {
        return getThemeAttributeColor(R.attr.contextMainColor);
    }

    public int getContextTextColor(final Application context) {
        return getThemeAttributeColor(R.attr.contextTextColor);
    }

    public int getAlertDialogStyle(final Application context) {
        return getThemeAttributeStyle(R.attr.alertDialogTheme);
    }

    public void colorizeSwitch(final SwitchCompat switchCompat, final Application context) {
        if(!isBAMandant()) {
            return;
        }

        if (switchCompat == null) return;

        int colorEnabled = getAppAccentColor(context);
        int colorDisabled = ContextCompat.getColor(context, R.color.kColorSwitchOff);
        int colorTrackEnabled = Color.argb(70,
                Color.red(colorEnabled),
                Color.green(colorEnabled),
                Color.blue(colorEnabled));

        int colorTrackDisabled = ContextCompat.getColor(context, R.color.color3_50);

        final ColorStateList thumbStates = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{}
                },
                new int[]{
                        colorEnabled,
                        colorDisabled,
                        colorDisabled
                });

        final ColorStateList trackStates = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{}
                },
                new int[]{
                        colorTrackEnabled,
                        colorTrackDisabled,
                        colorTrackDisabled
                });

        switchCompat.setThumbTintList(thumbStates);
        switchCompat.setTrackTintList(trackStates);
    }

    public void colorizeTabLayoutHeader(final Application context, final TabLayout tabLayout) {
        if(!isBAMandant()) {
            return;
        }

        if (tabLayout == null) return;

        final int mainColor = getMainColor(context);
        final int contrastColor = getMainContrastColor(context);
        for (int i = 0; i < tabLayout.getChildCount(); ++i) {
            final View child = tabLayout.getChildAt(i);
            if (child instanceof LinearLayout) {
                final LinearLayout castedChild = (LinearLayout) child;
                for (int j = 0; j < castedChild.getChildCount(); ++j) {
                    castedChild.getChildAt(j).setBackgroundColor(mainColor);
                }
            }
            tabLayout.getChildAt(i).setBackgroundColor(mainColor);
        }
        tabLayout.setTabTextColors(contrastColor, contrastColor);
    }

}
