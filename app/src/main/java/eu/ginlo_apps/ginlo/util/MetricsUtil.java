// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class MetricsUtil {
    public static int dpToPx(Context context,
                             int dp) {
        float density = context.getResources().getDisplayMetrics().density;

        return (int) (density * dp);
    }

    public static DisplayMetrics getDisplayMetrics(Context context) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics;
    }
}
