// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view;

import android.content.Context;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * SwipeRefreshLayout wurde überschireben da die Klasse noch ein Bug enthält.
 * Wenn setRefreshing(true) aufgerufen wird, bevor der View vom System seine
 * Größe verraten bekommen hat, wird der Waitindicator nicht aufgerufen.
 *
 * @author Florian
 * @version $Id$
 */
public class SimsmeSwipeRefreshLayout
        extends SwipeRefreshLayout {

    private boolean mMeasured = false;

    private boolean mPreMeasureRefreshing = false;

    private int mTouchSlop;
    private float mPrevX;

    public SimsmeSwipeRefreshLayout(final Context context,
                                    final AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public SimsmeSwipeRefreshLayout(final Context context) {
        super(context);
    }

    @Override
    public void onMeasure(final int widthMeasureSpec,
                          final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!mMeasured) {
            mMeasured = true;
            setRefreshing(mPreMeasureRefreshing);
        }
    }

    @Override
    public void setRefreshing(final boolean refreshing) {
        if (mMeasured) {
            super.setRefreshing(refreshing);
        } else {
            mPreMeasureRefreshing = refreshing;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                final MotionEvent obtain = MotionEvent.obtain(event);
                mPrevX = obtain.getX();
                obtain.recycle();
                break;

            case MotionEvent.ACTION_MOVE:
                final float eventX = event.getX();
                float xDiff = Math.abs(eventX - mPrevX);

                if (xDiff > mTouchSlop) {
                    return false;
                }
                break;
            default:
                break;
        }

        return super.onInterceptTouchEvent(event);
    }
}
