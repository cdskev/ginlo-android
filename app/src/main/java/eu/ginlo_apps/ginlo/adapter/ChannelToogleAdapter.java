// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.adapter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.RelativeLayout;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.model.backend.ChannelLayoutModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleChildModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleSettingsModel;
import eu.ginlo_apps.ginlo.util.ChannelColorUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ChannelToogleAdapter
        extends ArrayAdapter<ToggleModel>
        implements OnCheckedChangeListener {
    private static final int TOGGLE_BG_COLOR_DEPTH_0 = (int) Long.parseLong("333333", 16);  // 0% 00

    private static final int TOGGLE_BG_COLOR_DEPTH_1 = (int) Long.parseLong("1A333333", 16);  // 10% 1a

    private static final int TOGGLE_BG_COLOR_DEPTH_2 = (int) Long.parseLong("33333333", 16);  // 20% 33

    private static final int TOGGLE_BG_COLOR_DEPTH_3 = (int) Long.parseLong("4D333333", 16);  // 30% 4d

    private static final int TOGGLE_BG_COLOR_DEPTH_4 = (int) Long.parseLong("66333333", 16);  // 40% 66

    private static final int TOGGLE_BG_COLOR_DEPTH_5 = (int) Long.parseLong("80333333", 16);  // 50% 80

    private static final int TOGGLE_BG_COLOR_DEPTH_6 = (int) Long.parseLong("99333333", 16);  // 60% 99

    private final LayoutInflater mInflater;
    private final HashMap<String, ToggleSettingsModel> mFilterValues;
    private final OnCheckedChangeListener mOnCheckedChangeListener;

    private final boolean mDisabled;
    /**
     * Map mit Referenzen auf die Parents der Switches, falls man programmatisch darauf zugreifen muss z.B. DHL-Service
     * SGA: leider wird getView pro Switch bis zu 9 mal aufgerufen
     * ein zwischenspeichenr der Views (container) funktioniert nicht, da dann anscheinend der container mehrfach als Kind hinzugefuegt wird und der Switch nicht mehr switcht
     * als Notloesung habe ich eine Multimap genutzt, die auch die mehrfach erzeugten Switches ansprechen kann. Darf gere verbessert werden...
     */
    private final Map<String, ArrayList<SwitchCompat>> mToggleIdentViewMap;
    private int mColorEnable = Integer.MIN_VALUE;
    private int mColorDisable = Integer.MIN_VALUE;
    private int mColorToggle = Integer.MIN_VALUE;

    public ChannelToogleAdapter(final Activity act,
                                final ToggleModel[] toggles,
                                final HashMap<String, ToggleSettingsModel> filterValues,
                                final OnCheckedChangeListener listener,
                                final boolean disabled) {
        super(act, R.layout.channel_toogle_view, new ArrayList<ToggleModel>());
        mOnCheckedChangeListener = listener;
        mInflater = act.getLayoutInflater();
        mDisabled = disabled;

        mToggleIdentViewMap = new HashMap<>();

        if (filterValues == null) {
            mFilterValues = new HashMap<>();
        } else {
            mFilterValues = filterValues;
        }

        addToggles(toggles, mFilterValues, 0);
    }

    @Override
    @SuppressLint(
            {
                    "ViewHolder",
                    "InflateParams"
            }
    )
    public View getView(final int position,
                        final View convertView,
                        final ViewGroup parent) {
        if (position >= this.getCount()) {
            return new RelativeLayout(getContext());
        }

        final ToggleModel toggleModel = getItem(position);

        RelativeLayout container = null;

        // immer neu, da sonst beim scrollen Fehler auftreten
        container = (RelativeLayout) mInflater.inflate(R.layout.channel_toogle_view, null);
        SwitchCompat switchCompat = container.findViewById(R.id.channel_toggle_switch);

        // SGA: Beschreibung Multimap siehe @mToggleIdentViewMap
        if (toggleModel != null) {
            ArrayList<SwitchCompat> list = mToggleIdentViewMap.get(toggleModel.ident);
            if (list == null) {
                list = new ArrayList<>();
                list.add(switchCompat);
                mToggleIdentViewMap.put(toggleModel.ident, list);
            } else {
                list.add(switchCompat);
            }
        }

        //toggle Farben setzen
        if ((mColorToggle != Integer.MIN_VALUE) && (mColorDisable != Integer.MIN_VALUE)) {
            Drawable thumbDrawable = switchCompat.getThumbDrawable();

            thumbDrawable = DrawableCompat.wrap(thumbDrawable);
            DrawableCompat.setTintList(thumbDrawable, createSwitchThumbColorStateList());

            Drawable trackDrawable = switchCompat.getTrackDrawable();

            trackDrawable = DrawableCompat.wrap(trackDrawable);
            DrawableCompat.setTintList(trackDrawable, createSwitchTrackColorStateList());
        }

        final ToggleSettingsModel toggleSettings = mFilterValues.get(toggleModel.ident);

        String toggleValue = null;
        boolean singleToggleDisabled = false;

        if (!StringUtil.isNullOrEmpty(toggleModel.filterOn) && StringUtil.isEqual(toggleModel.filterOn, toggleModel.filterOff)) {
            toggleValue = "on";
            singleToggleDisabled = true;
        } else if ((toggleSettings != null) && (toggleSettings.value != null)) {
            toggleValue = toggleSettings.value;
        } else if (toggleModel.defaultValue != null) {
            toggleValue = toggleModel.defaultValue;
        } else {
            toggleValue = "off";
        }

        final boolean isOn = toggleValue.equalsIgnoreCase("on");

        switchCompat.setChecked(isOn);

        switchCompat.setText((toggleModel.label != null) ? toggleModel.label : "");

        container.setBackgroundColor(getDepthColor(toggleModel.depth));

        if (mColorEnable != Integer.MIN_VALUE) {
            switchCompat.setTextColor(isOn ? mColorEnable : mColorDisable);
            if (mDisabled || singleToggleDisabled) {
                switchCompat.setEnabled(false);
            }
        }

        switchCompat.setOnCheckedChangeListener(this);
        switchCompat.setTag(toggleModel);

        return container;
    }

    @Override
    public void onCheckedChanged(final CompoundButton buttonView,
                                 final boolean isChecked) {
        setTextColor(isChecked, buttonView);

        final Object obj = buttonView.getTag();

        if ((obj instanceof ToggleModel)) {
            final ToggleModel toggleModel = (ToggleModel) obj;

            putFilterValue(isChecked, toggleModel);

            if (toggleModel.children != null) {
                for (final ToggleChildModel child : toggleModel.children) {
                    if (child.forToggle == null) {
                        continue;
                    }

                    final int parentPos = getPosition(toggleModel);

                    if (child.forToggle.equalsIgnoreCase("off") && !isChecked) {
                        addChildren(child, parentPos, toggleModel.depth + 1);
                    } else if (child.forToggle.equalsIgnoreCase("on") && isChecked) {
                        addChildren(child, parentPos, toggleModel.depth + 1);
                    } else if (child.forToggle.equalsIgnoreCase("off") && isChecked) {
                        removeChildren(child, parentPos);
                    } else if (child.forToggle.equalsIgnoreCase("on") && !isChecked) {
                        removeChildren(child, parentPos);
                    }
                }
            }
        }
        mOnCheckedChangeListener.onCheckedChanged(buttonView, isChecked);
    }

    public HashMap<String, ToggleSettingsModel> getFilterValues() {
        return mFilterValues;
    }

    private void addToggles(final ToggleModel[] toggles,
                            final HashMap<String, ToggleSettingsModel> filterValues,
                            final int depth) {
        for (int i = 0; i < toggles.length; i++) {
            final ToggleModel toggle = toggles[i];

            toggle.depth = depth;

            add(toggle);

            final ToggleSettingsModel toggleSettings = filterValues.get(toggle.ident);

            String toggleValue = null;

            if ((toggleSettings != null) && (toggleSettings.value != null)) {
                toggleValue = toggleSettings.value;
            } else if (toggle.defaultValue != null) {
                toggleValue = toggle.defaultValue;
            } else {
                toggleValue = "off";
            }

            final boolean isOn = toggleValue.equalsIgnoreCase("on");

            if (toggleSettings == null) {
                putFilterValue(isOn, toggle);
            }

            if (toggle.children != null) {
                for (final ToggleChildModel child : toggle.children) {
                    if (child.forToggle == null) {
                        continue;
                    }

                    if (child.forToggle.equalsIgnoreCase("off") && !isOn) {
                        if (child.items != null) {
                            addToggles(child.items, filterValues, depth + 1);
                        }
                    } else if (child.forToggle.equalsIgnoreCase("on") && isOn) {
                        if (child.items != null) {
                            addToggles(child.items, filterValues, depth + 1);
                        }
                    }
                }
            }
        }
    }

    private void addChildren(final ToggleChildModel child,
                             final int parentPos,
                             final int depth) {
        if ((parentPos > -1) && (child.items != null)) {
            for (int pos = 0; pos < child.items.length; pos++) {
                final ToggleModel toggleChild = child.items[pos];

                toggleChild.depth = depth;

                final boolean isOn = (toggleChild.defaultValue != null)
                        && toggleChild.defaultValue.equalsIgnoreCase("on");

                putFilterValue(isOn, toggleChild);

                insert(toggleChild, parentPos + pos + 1);
            }
        }
    }

    private void removeChildren(final ToggleChildModel child,
                                final int parentPos) {
        if ((parentPos > -1) && (child.items != null)) {
            for (int pos = 0; pos < child.items.length; pos++) {
                final ToggleModel toggleChild = child.items[pos];

                mFilterValues.remove(toggleChild.ident);
                remove(toggleChild);
            }
        }
    }

    private void putFilterValue(final boolean isChecked,
                                final ToggleModel toggleModel) {
        final String filter = isChecked ? toggleModel.filterOn : toggleModel.filterOff;
        final String value = isChecked ? "on" : "off";

        mFilterValues.put(toggleModel.ident, new ToggleSettingsModel(filter, value));
    }

    public void setChannelLayout(final ChannelLayoutModel model) {
        ChannelColorUtil ccu = new ChannelColorUtil(model, getContext());

        mColorEnable = ccu.getColorLabelEnable();
        mColorDisable = ccu.getColorLabelDisable();
        mColorToggle = ccu.getSettingsColorToggle();
    }

    private void setTextColor(final boolean isChecked,
                              final CompoundButton compat) {
        if ((compat != null) && (mColorEnable != Integer.MIN_VALUE)) {
            compat.setTextColor(isChecked ? mColorEnable : mColorDisable);
        }
    }

    private int getDepthColor(final int depth) {
        final int returnValue;

        switch (depth) {
            case 0:
                returnValue = TOGGLE_BG_COLOR_DEPTH_0;
                break;
            case 1:
                returnValue = TOGGLE_BG_COLOR_DEPTH_1;
                break;
            case 2:
                returnValue = TOGGLE_BG_COLOR_DEPTH_2;
                break;
            case 3:
                returnValue = TOGGLE_BG_COLOR_DEPTH_3;
                break;
            case 4:
                returnValue = TOGGLE_BG_COLOR_DEPTH_4;
                break;
            case 5:
                returnValue = TOGGLE_BG_COLOR_DEPTH_5;
                break;
            case 6:
                returnValue = TOGGLE_BG_COLOR_DEPTH_6;
                break;
            default:
                returnValue = TOGGLE_BG_COLOR_DEPTH_6;
                break;
        }

        return returnValue;
    }

    private ColorStateList createSwitchThumbColorStateList() {
        final int[][] states = new int[3][];
        final int[] colors = new int[3];
        int i = 0;

        // Disabled state
        states[i] = new int[]{-android.R.attr.state_enabled};
        colors[i] = mColorToggle;
        i++;

        states[i] = new int[]{android.R.attr.state_checked};
        colors[i] = mColorToggle;
        i++;

        // Default enabled state
        states[i] = new int[0];
        colors[i] = mColorToggle;
        i++;

        return new ColorStateList(states, colors);
    }

    private ColorStateList createSwitchTrackColorStateList() {
        final int[][] states = new int[3][];
        final int[] colors = new int[3];
        int i = 0;

        // Disabled state
        states[i] = new int[]{-android.R.attr.state_enabled};
        colors[i] = ChannelColorUtil.getAlphaColor(mColorDisable, 0.1f);
        i++;

        states[i] = new int[]{android.R.attr.state_checked};
        colors[i] = ChannelColorUtil.getAlphaColor(mColorToggle, 0.3f);
        i++;

        // Default enabled state
        states[i] = new int[0];
        colors[i] = ChannelColorUtil.getAlphaColor(mColorDisable, 0.3f);
        i++;

        return new ColorStateList(states, colors);
    }
}
