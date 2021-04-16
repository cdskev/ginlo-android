// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.view;

import android.app.Dialog;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import android.widget.TextView;

/**
 * Created by SGA on 30.03.2016.
 */
public class ProgressDialog extends Dialog {
    private TextView mTitleTextView;

    public ProgressDialog(@NonNull Context context, @StyleRes int themeResId) {
        super(context, themeResId);
    }

    public void setTitleTextView(TextView tv) {
        mTitleTextView = tv;
    }

    public void setTitleText(final String title) {
        if (mTitleTextView != null) {
            mTitleTextView.setText(title);
        }
    }
}
