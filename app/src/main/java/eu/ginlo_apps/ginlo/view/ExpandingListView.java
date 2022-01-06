// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ListView;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.util.MetricsUtil;

/**
 * @author SGA
 * @version $Id$ Important: This List REQUIRES a header and a footer to work properly The Size of the list is calculated and subtracted from the screensize this value is the new height of the footer
 */
public class ExpandingListView
        extends ListView {

    private int mOldCount = 0;

    private int mScreenHeight = 0;

    private int mHeaderheight = 0;

    private int mItemheight = 0;

    private int mDividerHeight = 0;

    private int mLastListHeight = 0;

    public ExpandingListView(final Context context,
                             final AttributeSet attrs) {
        super(context, attrs);

        mScreenHeight = MetricsUtil.getDisplayMetrics(context).heightPixels;  // - (int)getResources().getDimension(R.dimen.toolbar_height) + mStatusBarHeight;
        mDividerHeight = getDividerHeight();

        mHeaderheight = (int) getResources().getDimension(R.dimen.chatoverview_header_height) + mDividerHeight;
        mItemheight = (int) getResources().getDimension(R.dimen.chatoverview_item_height) + mDividerHeight;
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        if ((getCount() != mOldCount) || (getCount() > 2)) {
            mOldCount = getCount();

            final int newListHeight = ((getCount() - 2) * mItemheight) + mHeaderheight;  // dont count the header

            final View lastItem = getChildAt(getChildCount() - 1);

            if ((lastItem.getTag() != null) && lastItem.getTag().equals("expandable_list_footer")) {
                if (newListHeight != mLastListHeight) {
                    if (newListHeight > mScreenHeight) {
                        lastItem.getLayoutParams().height = 0;
                    } else {
                        lastItem.getLayoutParams().height = mScreenHeight - newListHeight;
                    }
                    lastItem.requestLayout();
                    mLastListHeight = newListHeight;
                }
            }
        }
        super.onDraw(canvas);
    }
}
