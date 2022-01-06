// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

public class KeyboardUtil {
    // magic number that seems to be valid in different android screen sizes
    private final static double KEYBOARD_MIN_HEIGHT_RATIO = 0.15;

    public static void toggleSoftInputKeyboard(Context context,
                                               final View view,
                                               boolean showKeyboard) {
        if (view == null || context == null) {
            return;
        }

        final InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);

        if (showKeyboard) {
            view.requestFocus();

            final Handler handler = new Handler();
            final Runnable runnable = new Runnable() {
                public void run() {
                    inputMethodManager.showSoftInput(view, 0);
                }
            };
            handler.postDelayed(runnable, 100);
        } else {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static boolean isKeyboardVisible(Activity activity) {
        Rect r = new Rect();

        View activityRoot = getActivityRoot(activity);

        activityRoot.getWindowVisibleDisplayFrame(r);

        int screenHeight = activityRoot.getRootView().getHeight();
        int heightDiff = screenHeight - r.height();

        return heightDiff > screenHeight * KEYBOARD_MIN_HEIGHT_RATIO;
    }

    private static View getActivityRoot(Activity activity) {
        return ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
    }
}
