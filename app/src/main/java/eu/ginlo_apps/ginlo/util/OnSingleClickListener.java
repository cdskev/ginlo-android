// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.os.SystemClock;
import android.view.View;

public abstract class OnSingleClickListener
        implements View.OnClickListener {
    private static final long MIN_CLICK_INTERVAL = 600;

    private long lastClickTime;

    protected abstract void onSingleClick();

    @Override
    public final void onClick(View v) {
        long currentClickTime = SystemClock.uptimeMillis();
        long elapsedTime = currentClickTime - lastClickTime;

        lastClickTime = currentClickTime;

        if (elapsedTime <= MIN_CLICK_INTERVAL) {
            return;
        }

        onSingleClick();
    }
}
