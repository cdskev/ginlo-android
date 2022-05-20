// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.view.menu.ActionMenuItemView;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import java.util.ArrayList;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.view.EmojiconMediumTextView;
import eu.ginlo_apps.ginlo.view.MaskImageView;

/**
 * Helper class that iterates through Toolbar views, and sets dynamically icons
 * and texts color
 */
public final class ToolbarColorizeHelper {
    public static void colorizeToolbar(final Toolbar toolbarView,
                                       final int toolbarIconsColor,
                                       final int toolbarBackgroundColor,
                                       final Activity activity
    ) {
        final PorterDuffColorFilter colorFilter = new PorterDuffColorFilter(toolbarIconsColor, PorterDuff.Mode.SRC_ATOP);
        final ColorDrawable newColorDrawable = new ColorDrawable(toolbarBackgroundColor);
        toolbarView.setBackground(newColorDrawable);
        colorizeChildren(activity, toolbarView, toolbarIconsColor, colorFilter);

        //Step 4: Changing the color of the Overflow Menu icon.
        setOverflowButtonColor(activity, colorFilter);
    }

    private static void colorizeChildren(final Context context,
                                         final ViewGroup parentView,
                                         final int toolbarIconsColor,
                                         final ColorFilter colorFilter) {
        for (int i = 0; i < parentView.getChildCount(); i++) {
            final View v = parentView.getChildAt(i);

            // ahctung: Reihenfolge der Vererbung beachten!

            //Step 1 : Changing the color of back button (or open drawer button).
            if (v instanceof ImageButton) {
                //Action Bar back button
                ((ImageButton) v).getDrawable().setColorFilter(colorFilter);
            } else if (v instanceof MaskImageView) {
                final Drawable d = ((MaskImageView) v).getDrawable();

                if (d != null) {
                    d.setColorFilter(colorFilter);
                }
            } else if (v instanceof EmojiconMediumTextView) {
                ((EmojiconMediumTextView) v).setTextColor(toolbarIconsColor);
            } else if (v instanceof AppCompatTextView) {
                final AppCompatTextView appCompatTextView = (AppCompatTextView) v;
                appCompatTextView.setTextColor(toolbarIconsColor);
                final Drawable[] compoundDrawables = appCompatTextView.getCompoundDrawables();
                for (int j = 0; j < compoundDrawables.length; ++j) {
                    final Drawable compoundDrawable = compoundDrawables[j];
                    if (compoundDrawable != null) {
                        compoundDrawable.setColorFilter(colorFilter);
                    }
                }
            } else if (v instanceof SwitchCompat) {
                ScreenDesignUtil.getInstance().colorizeSwitch((SwitchCompat) v, (Application) context.getApplicationContext());
            } else if (v instanceof TextView) {
                ((TextView) v).setTextColor(toolbarIconsColor);
            /* KS: Don't colorize *any* image!
            } else if (v instanceof ImageView) {
                // das Company-Logo nicht einfaerben
                if ("toolbar_logo" != v.getTag()) {
                    final Drawable d = ((ImageView) v).getDrawable();

                    if (d != null) {
                        d.setColorFilter(colorFilter);
                    }
                }
            */
            } else if (v instanceof ImageView) {
                // Don't colorize images!
            } else if (v instanceof LinearLayout) {
                colorizeChildren(context, (LinearLayout) v, toolbarIconsColor, colorFilter);
            } else if (v instanceof RelativeLayout) {
                colorizeChildren(context, (RelativeLayout) v, toolbarIconsColor, colorFilter);
            } else if (v instanceof ActionMenuView) {
                for (int j = 0; j < ((ActionMenuView) v).getChildCount(); j++) {
                    //Step 2: Changing the color of any ActionMenuViews - icons that are not back button, nor text, nor overflow menu icon.
                    //Colorize the ActionViews -> all icons that are NOT: back button | overflow menu
                    final View innerView = ((ActionMenuView) v).getChildAt(j);

                    if (innerView instanceof ActionMenuItemView) {
                        for (int k = 0; k < ((ActionMenuItemView) innerView).getCompoundDrawables().length; k++) {
                            if (((ActionMenuItemView) innerView).getCompoundDrawables()[k] != null) {
                                final int finalK = k;

                                //Important to set the color filter in seperate thread, by adding it to the message queue
                                //Won't work otherwise.
                                innerView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        ((ActionMenuItemView) innerView).getCompoundDrawables()[finalK].setColorFilter(colorFilter);
                                    }
                                });
                            }
                        }
                    }
                }
            } else if (v instanceof LinearLayoutCompat) {
                colorizeChildren(context, (LinearLayoutCompat) v, toolbarIconsColor, colorFilter);
            } else if (v instanceof CoordinatorLayout) {
                colorizeChildren(context, (CoordinatorLayout) v, toolbarIconsColor, colorFilter);
            }
        }
    }

    /**
     * It's important to set overflowDescription attribute in styles, so we can
     * grab the reference to the overflow icon. Check: res/values/styles.xml
     */
    private static void setOverflowButtonColor(final Activity activity,
                                               final PorterDuffColorFilter colorFilter) {
        final String overflowDescription = activity.getString(R.string.abc_action_menu_overflow_description);
        final ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        final ViewTreeObserver viewTreeObserver = decorView.getViewTreeObserver();

        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                final ArrayList<View> outViews = new ArrayList<>();

                decorView.findViewsWithText(outViews, overflowDescription, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                if (outViews.isEmpty()) {
                    return;
                }

                final ImageView overflow = (ImageView) outViews.get(0);

                overflow.setColorFilter(colorFilter);
                overflow.setBackground(null);
                removeOnGlobalLayoutListener(decorView, this);
            }
        });
    }

    private static void removeOnGlobalLayoutListener(final View v,
                                                     final ViewTreeObserver.OnGlobalLayoutListener listener) {
        v.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
    }
}
