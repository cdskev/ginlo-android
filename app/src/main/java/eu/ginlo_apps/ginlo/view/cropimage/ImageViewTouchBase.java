// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view.cropimage;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.KeyEvent;
import androidx.appcompat.widget.AppCompatImageView;

import eu.ginlo_apps.ginlo.view.cropimage.RotateBitmap;

abstract class ImageViewTouchBase
        extends AppCompatImageView {
    private static final float SCALE_RATE = 1.25F;
    // The current bitmap being displayed.
    final eu.ginlo_apps.ginlo.view.cropimage.RotateBitmap mBitmapDisplayed = new eu.ginlo_apps.ginlo.view.cropimage.RotateBitmap(null);
    // This is the base transformation which is used to show the image
    // initially.  The current computation for this shows the image in
    // it's entirety, letterboxing as needed.  One could choose to
    // show the image as cropped instead.
    //
    // This matrix is recomputed when we go from the thumbnail image to
    // the full size image.
    private final Matrix mBaseMatrix = new Matrix();
    // This is the supplementary transformation which reflects what
    // the user has done in terms of zooming and panning.
    //
    // This matrix remains the same when we go from the thumbnail image
    // to the full size image.
    private final Matrix mSuppMatrix = new Matrix();
    // This is the final matrix which is computed as the concatentation
    // of the base matrix and the supplementary matrix.
    private final Matrix mDisplayMatrix = new Matrix();
    // Temporary buffer used for getting the values out of a matrix.
    private final float[] mMatrixValues = new float[9];
    private final Handler mHandler = new Handler();
    int mLeft;
    int mRight;
    int mTop;
    int mBottom;
    private int mThisWidth = -1;
    private int mThisHeight = -1;
    private float mMaxZoom;
    private Runnable mOnLayoutRunnable = null;

    public ImageViewTouchBase(final Context context) {
        super(context);
        init();
    }

    public ImageViewTouchBase(final Context context,
                              final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    protected void onLayout(final boolean changed,
                            final int left,
                            final int top,
                            final int right,
                            final int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mLeft = left;
        mRight = right;
        mTop = top;
        mBottom = bottom;
        mThisWidth = right - left;
        mThisHeight = bottom - top;

        final Runnable r = mOnLayoutRunnable;

        if (r != null) {
            mOnLayoutRunnable = null;
            r.run();
        }
        if (mBitmapDisplayed.getBitmap() != null) {
            getProperBaseMatrix(mBitmapDisplayed, mBaseMatrix);
            setImageMatrix(getImageViewMatrix());
        }
    }

    @Override
    public boolean onKeyDown(final int keyCode,
                             final KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && (getScale() > 1.0F)) {
            // If we're zoomed in, pressing Back jumps out to show the entire
            // image, otherwise Back returns the user to the gallery.
            zoomTo(1.0F);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void setImageBitmap(final Bitmap bitmap) {
        setImageBitmap(bitmap, 0);
    }

    private void setImageBitmap(final Bitmap bitmap,
                                final int rotation) {
        super.setImageBitmap(bitmap);

        final Drawable d = getDrawable();

        if (d != null) {
            d.setDither(true);
        }
        mBitmapDisplayed.setBitmap(bitmap);
        mBitmapDisplayed.setRotation(rotation);
    }

    // This function changes bitmap, reset base matrix according to the size
    // of the bitmap, and optionally reset the supplementary matrix.
    public void setImageBitmapResetBase(final Bitmap bitmap,
                                        final boolean resetSupp) {
        setImageRotateBitmapResetBase(new eu.ginlo_apps.ginlo.view.cropimage.RotateBitmap(bitmap), resetSupp);
    }

    public void setImageRotateBitmapResetBase(final eu.ginlo_apps.ginlo.view.cropimage.RotateBitmap bitmap,
                                              final boolean resetSupp) {
        final int viewWidth = getWidth();

        if (viewWidth <= 0) {
            mOnLayoutRunnable = new Runnable() {
                public void run() {
                    setImageRotateBitmapResetBase(bitmap, resetSupp);
                }
            };
            return;
        }

        if (bitmap.getBitmap() != null) {
            getProperBaseMatrix(bitmap, mBaseMatrix);
            setImageBitmap(bitmap.getBitmap(), bitmap.getRotation());
        } else {
            mBaseMatrix.reset();
            setImageBitmap(null);
        }

        if (resetSupp) {
            mSuppMatrix.reset();
        }
        setImageMatrix(getImageViewMatrix());
        mMaxZoom = maxZoom();
    }

    // Center as much as possible in one or both axis.  Centering is
    // defined as follows:  if the image is scaled down below the
    // view's dimensions then center it (literally).  If the image
    // is scaled larger than the view and is translated out of view
    // then translate it back into view (i.e. eliminate black bars).
    void center() {
        if (mBitmapDisplayed.getBitmap() == null) {
            return;
        }

        final Matrix m = getImageViewMatrix();

        final RectF rect = new RectF(0F, 0F, mBitmapDisplayed.getBitmap().getWidth(), mBitmapDisplayed.getBitmap().getHeight());

        m.mapRect(rect);

        final float height = rect.height();
        final float width = rect.width();

        float deltaX = 0F, deltaY = 0F;

        final int viewHeight = getHeight();

        if (height < viewHeight) {
            deltaY = (((viewHeight - height) / 2F) - rect.top);
        } else if (rect.top > 0F) {
            deltaY = -rect.top;
        } else if (rect.bottom < viewHeight) {
            deltaY = getHeight() - rect.bottom;
        }

        final int viewWidth = getWidth();

        if (width < viewWidth) {
            deltaX = ((viewWidth - width) / 2F) - rect.left;
        } else if (rect.left > 0F) {
            deltaX = -rect.left;
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right;
        }

        postTranslate(deltaX, deltaY);
        setImageMatrix(getImageViewMatrix());
    }

    private void init() {
        setScaleType(ScaleType.MATRIX);
    }

    private float getValue(final Matrix matrix) {
        matrix.getValues(mMatrixValues);
        return mMatrixValues[Matrix.MSCALE_X];
    }

    // Get the scale factor out of the matrix.
    private float getScale(final Matrix matrix) {
        return getValue(matrix);
    }

    float getScale() {
        return getScale(mSuppMatrix);
    }

    // Setup the base matrix so that the image is centered and scaled properly.
    private void getProperBaseMatrix(final RotateBitmap bitmap,
                                     final Matrix matrix) {
        final float viewWidth = getWidth();
        final float viewHeight = getHeight();

        final float w = bitmap.getWidth();
        final float h = bitmap.getHeight();

        matrix.reset();

        // We limit up-scaling to 2x otherwise the result may look bad if it's
        // a small icon.
        final float widthScale = Math.min(viewWidth / w, 2.0F);
        final float heightScale = Math.min(viewHeight / h, 2.0F);
        final float scale = Math.min(widthScale, heightScale);

        matrix.postConcat(bitmap.getRotateMatrix());
        matrix.postScale(scale, scale);

        matrix.postTranslate((viewWidth - (w * scale)) / 2.0F, (viewHeight - (h * scale)) / 2.0F);
    }

    // Combine the base matrix and the supp matrix to make the final matrix.
    private Matrix getImageViewMatrix() {
        // The final matrix is computed as the concatentation of the base matrix
        // and the supplementary matrix.
        mDisplayMatrix.set(mBaseMatrix);
        mDisplayMatrix.postConcat(mSuppMatrix);
        return mDisplayMatrix;
    }

    // Sets the maximum zoom, which is a scale relative to the base matrix. It
    // is calculated to show the image at 400% zoom regardless of screen or
    // image orientation. If in the future we decode the full 3 megapixel image,
    // rather than the current 1024x768, this should be changed down to 200%.
    private float maxZoom() {
        if (mBitmapDisplayed.getBitmap() == null) {
            return 1F;
        }

        final float imageWidth = mBitmapDisplayed.getWidth();
        final float imageHeight = mBitmapDisplayed.getHeight();

        final float max;

        if (imageWidth < mThisWidth / 4 || imageHeight < mThisHeight / 4) {
            final float widthRatio = mThisWidth / imageWidth;
            final float heightRatio = mThisHeight / imageHeight;

            if (widthRatio < heightRatio) {
                max = widthRatio;
            } else {
                max = heightRatio;
            }
        } else {
            final float fw = imageWidth / (float) mThisWidth;
            final float fh = imageHeight / (float) mThisHeight;
            max = Math.max(fw, fh) * 4F;
        }

        return max;
    }

    void zoomTo(final float scale,
                final float centerX,
                final float centerY) {
        final float lScale;
        if (scale > mMaxZoom) {
            lScale = mMaxZoom;
        } else {
            lScale = scale;
        }

        final float oldScale = getScale();
        final float deltaScale = lScale / oldScale;

        mSuppMatrix.postScale(deltaScale, deltaScale, centerX, centerY);
        setImageMatrix(getImageViewMatrix());
        center();
    }

    void zoomTo(final float scale,
                final float centerX,
                final float centerY,
                final float durationMs) {
        final float incrementPerMs = (scale - getScale()) / durationMs;
        final float oldScale = getScale();
        final float startTime = System.currentTimeMillis();

        mHandler.post(new Runnable() {
            public void run() {
                final long now = System.currentTimeMillis();
                final float currentMs = Math.min(durationMs, now - startTime);
                final float target = oldScale + (incrementPerMs * currentMs);

                zoomTo(target, centerX, centerY);

                if (currentMs < durationMs) {
                    mHandler.post(this);
                }
            }
        });
    }

    private void zoomTo(final float scale) {
        final float cx = getWidth() / 2F;
        final float cy = getHeight() / 2F;

        zoomTo(scale, cx, cy);
    }

    void zoomIn() {
        zoomIn(SCALE_RATE);
    }

    void zoomOut() {
        zoomOut(SCALE_RATE);
    }

    private void zoomIn(final float rate) {
        if (getScale() >= mMaxZoom) {
            return;  // Don't let the user zoom into the molecular level.
        }
        if (mBitmapDisplayed.getBitmap() == null) {
            return;
        }

        final float cx = getWidth() / 2F;
        final float cy = getHeight() / 2F;

        mSuppMatrix.postScale(rate, rate, cx, cy);
        setImageMatrix(getImageViewMatrix());
    }

    private void zoomOut(final float rate) {
        if (mBitmapDisplayed.getBitmap() == null) {
            return;
        }

        final float cx = getWidth() / 2F;
        final float cy = getHeight() / 2F;

        // Zoom out to at most 1x.
        final Matrix tmp = new Matrix(mSuppMatrix);

        tmp.postScale(1F / rate, 1F / rate, cx, cy);

        if (getScale(tmp) < 1F) {
            mSuppMatrix.setScale(1F, 1F, cx, cy);
        } else {
            mSuppMatrix.postScale((1F / rate), (1F / rate), cx, cy);
        }
        setImageMatrix(getImageViewMatrix());
        center();
    }

    void postTranslate(final float dx,
                       final float dy) {
        mSuppMatrix.postTranslate(dx, dy);
    }

    void panBy(final float dx,
               final float dy) {
        postTranslate(dx, dy);
        setImageMatrix(getImageViewMatrix());
    }
}
