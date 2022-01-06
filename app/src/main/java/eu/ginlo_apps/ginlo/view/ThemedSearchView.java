// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.view;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.widget.EditText;
import android.widget.ImageView;
import eu.ginlo_apps.ginlo.log.LogUtil;
import java.lang.reflect.Field;
import androidx.appcompat.widget.SearchView;

/**
 * Created by yves1 on 15.06.16.
 */
public class ThemedSearchView extends SearchView {
    public ThemedSearchView(Context context, int accentContrastColor) {
        super(context);

        int[] ids = new int[]{androidx.appcompat.R.id.search_button,
                androidx.appcompat.R.id.search_go_btn,
                androidx.appcompat.R.id.search_close_btn,
                androidx.appcompat.R.id.search_voice_btn,
                androidx.appcompat.R.id.search_mag_icon
        };
        for (int i = 0; i < ids.length; i++) {
            ImageView button = findViewById(ids[i]);
            if (button != null) {

                button.setColorFilter(accentContrastColor, PorterDuff.Mode.SRC_ATOP);
            }
        }
        final EditText searchEditText = findViewById(androidx.appcompat.R.id.search_src_text);
        if (searchEditText != null) {
            searchEditText.setTextColor(accentContrastColor);
            searchEditText.setHintTextColor(accentContrastColor);
        }

        try {
            Field field = SearchView.class.getDeclaredField("mSearchHintIcon");
            field.setAccessible(true);
            Drawable searchHintIcon = (Drawable) field.get(this);
            if (searchHintIcon != null) {
                searchHintIcon.setColorFilter(accentContrastColor, PorterDuff.Mode.SRC_ATOP);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        }
    }
}
