// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class SquareView
        extends RelativeLayout {

    public SquareView(Context context) {
        super(context);
    }

    public SquareView(Context context,
                      AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareView(Context context,
                      AttributeSet attrs,
                      int defStyle) {
        super(context, attrs, defStyle);
    }

    protected void onMeasure(int widthMeasureSpec,
                             int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int size;

        size = Math.min(widthSize, heightSize);
        setMeasuredDimension(size, size);

        int finalMeasureSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);

        super.onMeasure(finalMeasureSpec, finalMeasureSpec);
    }
}
