// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view.cropimage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.util.ArrayList;

import eu.ginlo_apps.ginlo.view.cropimage.CropImageActivity;
import eu.ginlo_apps.ginlo.view.cropimage.HighlightView;
import eu.ginlo_apps.ginlo.view.cropimage.ImageViewTouchBase;

public class CropImageView
        extends ImageViewTouchBase {
    final ArrayList<HighlightView> mHighlightViews = new ArrayList<>();
    private final Context mContext;
    private HighlightView mMotionHighlightView = null;
    private float mLastX, mLastY;
    private int mMotionEdge;

    public CropImageView(final Context context,
                         final AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
    }

    @Override
    protected void onLayout(final boolean changed,
                            final int left,
                            final int top,
                            final int right,
                            final int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mBitmapDisplayed.getBitmap() != null) {
            for (final HighlightView hv : mHighlightViews) {
                hv.mMatrix.set(getImageMatrix());
                hv.invalidate();
                if (hv.mIsFocused) {
                    centerBasedOnHighlightView(hv);
                }
            }
        }
    }

    @Override
    protected void zoomTo(final float scale,
                          final float centerX,
                          final float centerY) {
        super.zoomTo(scale, centerX, centerY);
        for (final HighlightView hv : mHighlightViews) {
            hv.mMatrix.set(getImageMatrix());
            hv.invalidate();
        }
    }

    @Override
    protected void zoomIn() {
        super.zoomIn();
        for (final HighlightView hv : mHighlightViews) {
            hv.mMatrix.set(getImageMatrix());
            hv.invalidate();
        }
    }

    @Override
    protected void zoomOut() {
        super.zoomOut();
        for (final HighlightView hv : mHighlightViews) {
            hv.mMatrix.set(getImageMatrix());
            hv.invalidate();
        }
    }

    @Override
    protected void postTranslate(final float deltaX,
                                 final float deltaY) {
        super.postTranslate(deltaX, deltaY);
        for (int i = 0; i < mHighlightViews.size(); i++) {
            final HighlightView hv = mHighlightViews.get(i);

            hv.mMatrix.postTranslate(deltaX, deltaY);
            hv.invalidate();
        }
    }

    // According to the event's position, change the focus to the first
    // hitting cropping rectangle.
    private void recomputeFocus(final MotionEvent event) {
        for (int i = 0; i < mHighlightViews.size(); i++) {
            final HighlightView hv = mHighlightViews.get(i);

            hv.setFocus(false);
            hv.invalidate();
        }

        for (int i = 0; i < mHighlightViews.size(); i++) {
            final HighlightView hv = mHighlightViews.get(i);
            final int edge = hv.getHit(event.getX(), event.getY());

            if (edge != HighlightView.GROW_NONE) {
                if (!hv.hasFocus()) {
                    hv.setFocus(true);
                    hv.invalidate();
                }
                break;
            }
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        final CropImageActivity cropImageActivity = (CropImageActivity) mContext;

        if (cropImageActivity.mSaving) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (cropImageActivity.mWaitingToPick) {
                    recomputeFocus(event);
                } else {
                    for (int i = 0; i < mHighlightViews.size(); i++) {
                        final HighlightView hv = mHighlightViews.get(i);
                        final int edge = hv.getHit(event.getX(), event.getY());

                        if (edge != HighlightView.GROW_NONE) {
                            mMotionEdge = edge;
                            mMotionHighlightView = hv;
                            mLastX = event.getX();
                            mLastY = event.getY();
                            mMotionHighlightView.setMode((edge == HighlightView.MOVE) ? HighlightView.ModifyMode.Move
                                    : HighlightView.ModifyMode.Grow);
                            break;
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (cropImageActivity.mWaitingToPick) {
                    for (int i = 0; i < mHighlightViews.size(); i++) {
                        final HighlightView hv = mHighlightViews.get(i);

                        if (hv.hasFocus()) {
                            cropImageActivity.mCrop = hv;
                            for (int j = 0; j < mHighlightViews.size(); j++) {
                                if (j == i) {
                                    continue;
                                }
                                mHighlightViews.get(j).setHidden();
                            }
                            centerBasedOnHighlightView(hv);
                            ((CropImageActivity) mContext).mWaitingToPick = false;
                            return true;
                        }
                    }
                } else if (mMotionHighlightView != null) {
                    centerBasedOnHighlightView(mMotionHighlightView);
                    mMotionHighlightView.setMode(HighlightView.ModifyMode.None);
                }
                mMotionHighlightView = null;
                break;
            case MotionEvent.ACTION_MOVE:
                if (cropImageActivity.mWaitingToPick) {
                    recomputeFocus(event);
                } else if (mMotionHighlightView != null) {
                    mMotionHighlightView.handleMotion(mMotionEdge, event.getX() - mLastX, event.getY() - mLastY);
                    mLastX = event.getX();
                    mLastY = event.getY();

                    // This section of code is optional. It has some user
                    // benefit in that moving the crop rectangle against
                    // the edge of the screen causes scrolling but it means
                    // that the crop rectangle is no longer fixed under
                    // the user's finger.
                    ensureVisible(mMotionHighlightView);
                }
                break;
            default:
                // NO_PMD
                break;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                center();
                break;
            case MotionEvent.ACTION_MOVE:

                // if we're not zoomed then there's no point in even allowing
                // the user to move the image around.  This call to center puts
                // it back to the normalized location (with false meaning don't
                // animate).
                if (getScale() == 1F)
                {
                    center();
                }
                break;
            default:
                break;
        }

        return true;
    }

    // Pan the displayed image to make sure the cropping rectangle is visible.
    private void ensureVisible(final HighlightView hv) {
        final Rect r = hv.mDrawRect;

        final int panDeltaX1 = Math.max(0, mLeft - r.left);
        final int panDeltaX2 = Math.min(0, mRight - r.right);

        final int panDeltaY1 = Math.max(0, mTop - r.top);
        final int panDeltaY2 = Math.min(0, mBottom - r.bottom);

        final int panDeltaX = (panDeltaX1 != 0) ? panDeltaX1 : panDeltaX2;
        final int panDeltaY = (panDeltaY1 != 0) ? panDeltaY1 : panDeltaY2;

        if ((panDeltaX != 0) || (panDeltaY != 0)) {
            panBy(panDeltaX, panDeltaY);
        }
    }

    // If the cropping rectangle's size changed significantly, change the
    // view's center and scale according to the cropping rectangle.
    private void centerBasedOnHighlightView(final HighlightView hv) {
        final Rect drawRect = hv.mDrawRect;

        final float width = drawRect.width();
        final float height = drawRect.height();

        final float thisWidth = getWidth();
        final float thisHeight = getHeight();

        final float z1 = thisWidth / width * .6F;
        final float z2 = thisHeight / height * .6F;

        float zoom = Math.min(z1, z2);

        zoom = zoom * this.getScale();
        zoom = Math.max(1F, zoom);
        if ((Math.abs(zoom - getScale()) / zoom) > .1)
        {
            final float[] coordinates = new float[]
                    {
                            hv.mCropRect.centerX(),
                            hv.mCropRect.centerY()
                    };

            getImageMatrix().mapPoints(coordinates);
            zoomTo(zoom, coordinates[0], coordinates[1], 300F);
        }

        ensureVisible(hv);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        for (int i = 0; i < mHighlightViews.size(); i++) {
            mHighlightViews.get(i).draw(canvas);
        }
    }

    public void add(final HighlightView hv) {
        mHighlightViews.add(hv);
        invalidate();
    }
}
