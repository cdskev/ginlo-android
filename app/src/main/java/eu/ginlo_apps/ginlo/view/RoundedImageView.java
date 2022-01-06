// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

public class RoundedImageView
        extends AppCompatImageView {

    public RoundedImageView(Context context) {
        super(context);
    }

    public RoundedImageView(Context context,
                            AttributeSet attrs) {
        super(context, attrs);
    }

    public RoundedImageView(Context context,
                            AttributeSet attrs,
                            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setImageBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }

        RoundedBitmapDrawable dr = RoundedBitmapDrawableFactory.create(getResources(), bitmap);

        //dr.setCornerRadius(bitmap.getWidth() / 2.0f);
        dr.setCircular(true);
        dr.setAntiAlias(true);

        super.setImageDrawable(dr);
    }
}
