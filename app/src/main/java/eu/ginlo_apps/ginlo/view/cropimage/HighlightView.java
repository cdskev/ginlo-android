// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view.cropimage;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.view.View;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.util.ViewUtil;

// This class is used by CropImageActivity to display a highlighted cropping rectangle
// overlayed with the image. There are two coordinate spaces in use. One is
// image, another is screen. computeLayout() uses mMatrix to map from image
// space to screen space.

class HighlightView {
    static final int GROW_NONE = (1);
    static final int MOVE = (1 << 5);
    private static final int GROW_LEFT_EDGE = (1 << 1);
    private static final int GROW_RIGHT_EDGE = (1 << 2);
    private static final int GROW_TOP_EDGE = (1 << 3);
    private static final int GROW_BOTTOM_EDGE = (1 << 4);

    private final View mContext;
    private final Paint mFocusPaint = new Paint();
    private final Paint mNoFocusPaint = new Paint();
    private final Paint mOutlinePaint = new Paint();
    boolean mIsFocused;
    Rect mDrawRect;  // in screen space
    RectF mCropRect;  // in image space
    Matrix mMatrix;
    private int mOutlinePaintColor;
    private boolean mHidden;
    private ModifyMode mMode = ModifyMode.None;
    private RectF mImageRect;  // in image space
    private boolean mMaintainAspectRatio = false;
    private float mInitialAspectRatio;
    private boolean mCircle = false;
    private Drawable mResizeDrawableDiagonal;
    private float mDpiValue = 0F;

    HighlightView(final View ctx) {
        mContext = ctx;
    }

    private void init() {
        final android.content.res.Resources resources = mContext.getResources();
        mResizeDrawableDiagonal = resources.getDrawable(R.drawable.ic_zoom_canvas);

        mOutlinePaintColor = ViewUtil.getColor(R.color.color_primary, mContext.getContext());
    }

    boolean hasFocus() {
        return mIsFocused;
    }

    void setFocus(final boolean f) {
        mIsFocused = f;
    }

    void setHidden() {
        mHidden = true;
    }

    void draw(final Canvas canvas) {
        if (mHidden) {
            return;
        }

        final Path path = new Path();

        if (!hasFocus()) {
            mOutlinePaint.setColor(mOutlinePaintColor);
            canvas.drawRect(mDrawRect, mOutlinePaint);
        } else {
            final Rect viewDrawingRect = new Rect();

            mContext.getDrawingRect(viewDrawingRect);
            if (mCircle) {
                canvas.save();

                final float width = mDrawRect.width();
                final float height = mDrawRect.height();

                path.addCircle(mDrawRect.left + (width / 2), mDrawRect.top + (height / 2), width / 2, Path.Direction.CW);
                mOutlinePaint.setColor(mOutlinePaintColor);

                canvas.clipPath(path, Region.Op.DIFFERENCE);
                canvas.drawRect(viewDrawingRect, hasFocus() ? mFocusPaint : mNoFocusPaint);

                canvas.restore();
            } else {
                final Rect topRect = new Rect(viewDrawingRect.left, viewDrawingRect.top, viewDrawingRect.right, mDrawRect.top);

                if ((topRect.width() > 0) && (topRect.height() > 0)) {
                    canvas.drawRect(topRect, hasFocus() ? mFocusPaint : mNoFocusPaint);
                }

                final Rect bottomRect = new Rect(viewDrawingRect.left, mDrawRect.bottom, viewDrawingRect.right,
                        viewDrawingRect.bottom);

                if ((bottomRect.width() > 0) && (bottomRect.height() > 0)) {
                    canvas.drawRect(bottomRect, hasFocus() ? mFocusPaint : mNoFocusPaint);
                }

                final Rect leftRect = new Rect(viewDrawingRect.left, topRect.bottom, mDrawRect.left, bottomRect.top);

                if ((leftRect.width() > 0) && (leftRect.height() > 0)) {
                    canvas.drawRect(leftRect, hasFocus() ? mFocusPaint : mNoFocusPaint);
                }

                final Rect rightRect = new Rect(mDrawRect.right, topRect.bottom, viewDrawingRect.right, bottomRect.top);

                if ((rightRect.width() > 0) && (rightRect.height() > 0)) {
                    canvas.drawRect(rightRect, hasFocus() ? mFocusPaint : mNoFocusPaint);
                }

                path.addRect(new RectF(mDrawRect), Path.Direction.CW);

                mOutlinePaint.setColor(mOutlinePaintColor);
            }

            canvas.drawPath(path, mOutlinePaint);

            if (mCircle) {
                final int width = mResizeDrawableDiagonal.getIntrinsicWidth();
                final int height = mResizeDrawableDiagonal.getIntrinsicHeight();

                final int d = (int) Math.round(Math.cos(  /*45deg*/Math.PI / 4D) * (mDrawRect.width() / 2D));
                final int x = mDrawRect.left + (mDrawRect.width() / 2) + d - (width / 2);
                final int y = mDrawRect.top + (mDrawRect.height() / 2) - d - (height / 2);

                mResizeDrawableDiagonal.setBounds(x, y, x + mResizeDrawableDiagonal.getIntrinsicWidth(),
                        y + mResizeDrawableDiagonal.getIntrinsicHeight());
                mResizeDrawableDiagonal.draw(canvas);
            }
        }
    }

    public void setMode(final ModifyMode mode) {
        if (mode != mMode) {
            mMode = mode;
            mContext.invalidate();
        }
    }

    // Determines which edges are hit by touching at (x, y).
    int getHit(final float x,
               final float y) {
        final Rect r = computeLayout();
        final float hysteresis = 20F * mDpiValue;
        int retval = GROW_NONE;

        if (mCircle) {
            final float distX = x - r.centerX();
            final float distY = y - r.centerY();
            final int distanceFromCenter = (int) Math.sqrt((distX * distX) + (distY * distY));
            final int radius = mDrawRect.width() / 2;
            final int delta = distanceFromCenter - radius;

            if (Math.abs(delta) <= hysteresis) {
                if (Math.abs(distY) > Math.abs(distX)) {
                    if (distY < 0) {
                        retval = GROW_TOP_EDGE;
                    } else {
                        retval = GROW_BOTTOM_EDGE;
                    }
                } else {
                    if (distX < 0) {
                        retval = GROW_LEFT_EDGE;
                    } else {
                        retval = GROW_RIGHT_EDGE;
                    }
                }
            } else if (distanceFromCenter < radius) {
                retval = MOVE;
            } else {
                retval = GROW_NONE;
            }
        } else {
            // verticalCheck makes sure the position is between the top and
            // the bottom edge (with some tolerance). Similar for horizCheck.
            final boolean verticalCheck = (y >= (r.top - hysteresis)) && (y < (r.bottom + hysteresis));
            final boolean horizCheck = (x >= (r.left - hysteresis)) && (x < (r.right + hysteresis));

            // Check whether the position is near some edge(s).
            if ((Math.abs(r.left - x) < hysteresis) && verticalCheck) {
                retval |= GROW_LEFT_EDGE;
            }
            if ((Math.abs(r.right - x) < hysteresis) && verticalCheck) {
                retval |= GROW_RIGHT_EDGE;
            }
            if ((Math.abs(r.top - y) < hysteresis) && horizCheck) {
                retval |= GROW_TOP_EDGE;
            }
            if ((Math.abs(r.bottom - y) < hysteresis) && horizCheck) {
                retval |= GROW_BOTTOM_EDGE;
            }

            // Not near any edge but inside the rectangle: move.
            if ((retval == GROW_NONE) && r.contains((int) x, (int) y)) {
                retval = MOVE;
            }
        }
        return retval;
    }

    // Handles motion (dx, dy) in screen space.
    // The "edge" parameter specifies which edges the user is dragging.
    void handleMotion(final int edge,
                      float dx,
                      float dy) {
        final Rect r = computeLayout();

        if (edge == MOVE) {
            // Convert to image space before sending to moveBy().
            moveBy(dx * (mCropRect.width() / r.width()), dy * (mCropRect.height() / r.height()));
        } else {
            if (((GROW_LEFT_EDGE | GROW_RIGHT_EDGE) & edge) == 0) {
                dx = 0;
            }

            if (((GROW_TOP_EDGE | GROW_BOTTOM_EDGE) & edge) == 0) {
                dy = 0;
            }

            // Convert to image space before sending to growBy().
            final float xDelta = dx * (mCropRect.width() / r.width());
            final float yDelta = dy * (mCropRect.height() / r.height());

            growBy((((edge & GROW_LEFT_EDGE) != 0) ? -1 : 1) * xDelta, (((edge & GROW_TOP_EDGE) != 0) ? -1 : 1) * yDelta);
        }
    }

    // Grows the cropping rectange by (dx, dy) in image space.
    private void moveBy(final float dx,
                        final float dy) {
        final Rect invalRect = new Rect(mDrawRect);

        mCropRect.offset(dx, dy);

        // Put the cropping rectangle inside image rectangle.
        mCropRect.offset(Math.max(0, mImageRect.left - mCropRect.left), Math.max(0, mImageRect.top - mCropRect.top));

        mCropRect.offset(Math.min(0, mImageRect.right - mCropRect.right),
                Math.min(0, mImageRect.bottom - mCropRect.bottom));

        mDrawRect = computeLayout();
        invalRect.union(mDrawRect);
        invalRect.inset(-10, -10);
        mContext.invalidate(invalRect);
    }

    // Grows the cropping rectange by (dx, dy) in image space.
    private void growBy(float dx,
                        float dy) {
        if (mMaintainAspectRatio) {
            if (dx != 0) {
                dy = dx / mInitialAspectRatio;
            } else if (dy != 0) {
                dx = dy * mInitialAspectRatio;
            }
        }

        // Don't let the cropping rectangle grow too fast.
        // Grow at most half of the difference between the image rectangle and
        // the cropping rectangle.
        final RectF r = new RectF(mCropRect);

        if ((dx > 0) && ((r.width() + (2 * dx)) > mImageRect.width())) {
            dx = (mImageRect.width() - r.width()) / 2;

            if (mMaintainAspectRatio) {
                dy = dx / mInitialAspectRatio;
            }
        }
        if ((dy > 0) && ((r.height() + (2 * dy)) > mImageRect.height())) {
            dy = (mImageRect.height() - r.height()) / 2;

            if (mMaintainAspectRatio) {
                dx = dy * mInitialAspectRatio;
            }
        }

        r.inset(-dx, -dy);

        // Don't let the cropping rectangle shrink too fast.
        final float widthCap = 25F;

        if (r.width() < widthCap) {
            r.inset((-(widthCap - r.width()) / 2), 0);
        }

        final float heightCap = mMaintainAspectRatio ? (widthCap / mInitialAspectRatio) : widthCap;

        if (r.height() < heightCap) {
            r.inset(0, (-(heightCap - r.height()) / 2));
        }

        // Put the cropping rectangle inside the image rectangle.
        if (r.left < mImageRect.left) {
            r.offset(mImageRect.left - r.left, 0);
        } else if (r.right > mImageRect.right) {
            r.offset(-(r.right - mImageRect.right), 0);
        }
        if (r.top < mImageRect.top) {
            r.offset(0, mImageRect.top - r.top);
        } else if (r.bottom > mImageRect.bottom) {
            r.offset(0, -(r.bottom - mImageRect.bottom));
        }

        mCropRect.set(r);
        mDrawRect = computeLayout();
        mContext.invalidate();
    }

    // Returns the cropping rectangle in image space.
    Rect getCropRect() {
        return new Rect((int) mCropRect.left, (int) mCropRect.top, (int) mCropRect.right, (int) mCropRect.bottom);
    }

    // Maps the cropping rectangle from image space to screen space.
    private Rect computeLayout() {
        final RectF r = new RectF(mCropRect.left, mCropRect.top, mCropRect.right, mCropRect.bottom);

        mMatrix.mapRect(r);
        return new Rect(Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom));
    }

    void invalidate() {
        mDrawRect = computeLayout();
    }

    void setup(final Matrix m,
               final Rect imageRect,
               final RectF cropRect,
               final boolean circle,
               boolean maintainAspectRatio) {
        if (circle) {
            maintainAspectRatio = true;
        }
        mMatrix = new Matrix(m);

        mCropRect = cropRect;
        mImageRect = new RectF(imageRect);
        mMaintainAspectRatio = maintainAspectRatio;
        mCircle = circle;

        mInitialAspectRatio = mCropRect.width() / mCropRect.height();
        mDrawRect = computeLayout();

        mDpiValue = mContext.getResources().getDisplayMetrics().densityDpi / 160F;

        mFocusPaint.setARGB(125, 50, 50, 50);
        mNoFocusPaint.setARGB(125, 50, 50, 50);
        mOutlinePaint.setStrokeWidth((5F * mDpiValue));
        mOutlinePaint.setPathEffect(new DashPathEffect(new float[]
                {
                        (10F * mDpiValue),
                        (5F * mDpiValue),
                        (10F * mDpiValue),
                        (5F * mDpiValue)
                }, 0));
        mOutlinePaint.setStyle(Paint.Style.STROKE);
        mOutlinePaint.setAntiAlias(true);

        mMode = ModifyMode.None;
        init();
    }

    enum ModifyMode {
        None, Move, Grow
    }
}
