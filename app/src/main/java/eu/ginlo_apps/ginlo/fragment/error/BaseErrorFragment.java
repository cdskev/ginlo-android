// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment.error;

import androidx.fragment.app.Fragment;
import android.view.View;

/**
 * Created by SGA on 08.02.2017.
 */

public abstract class BaseErrorFragment extends Fragment {
    /** */
    int mButtonColor;

    /** */
    int mButtonTextColor;

    /**
     * @param view
     */
    public abstract void onButton1Click(final View view);

    /**
     * @param view
     */
    public abstract void onButton2Click(final View view);

    /**
     * @param color
     */
    public void setButtonColor(final int color) {
        mButtonColor = color;
    }

    /**
     * @param color
     */
    public void setButtonTextColor(final int color) {
        mButtonTextColor = color;
    }
}
