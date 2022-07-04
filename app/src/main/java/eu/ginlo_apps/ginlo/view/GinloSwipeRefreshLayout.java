// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view;

import android.content.Context;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

public class GinloSwipeRefreshLayout
        extends SwipeRefreshLayout {

    private static final String TAG = "GinloSwipeRefreshLayout";

    private boolean mMeasured = false;

    private boolean mPreMeasureRefreshing = false;

    private int mTouchSlop;
    private float mPrevX;

    public GinloSwipeRefreshLayout(final Context context,
                                   final AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public GinloSwipeRefreshLayout(final Context context) {
        super(context);
    }

    @Override
    public void onMeasure(final int widthMeasureSpec,
                          final int heightMeasureSpec) {
        //LogUtil.d(TAG, "onMeasure: ");
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!mMeasured) {
            mMeasured = true;
            setRefreshing(mPreMeasureRefreshing);
        }
    }

    @Override
    public void setRefreshing(final boolean refreshing) {
        //LogUtil.d(TAG, "setRefreshing: ");
        if (mMeasured) {
            super.setRefreshing(refreshing);
        } else {
            mPreMeasureRefreshing = refreshing;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {

        //LogUtil.d(TAG, "onInterceptTouchEvent: ");
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                final MotionEvent obtain = MotionEvent.obtain(event);
                mPrevX = obtain.getX();
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
