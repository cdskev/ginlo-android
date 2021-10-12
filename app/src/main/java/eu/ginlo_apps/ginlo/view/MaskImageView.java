// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.appcompat.widget.AppCompatImageView;
import android.util.AttributeSet;
import eu.ginlo_apps.ginlo.R;

public class MaskImageView
        extends AppCompatImageView {
    private Bitmap mask;

    private boolean mForceNoMask;
    private float mMaskRatio = 0.0f;
    private float mIndicatorBubbleRadiusFactor = 0.0f;
    private int mIndicatorBubbleColor = -1;


    public MaskImageView(Context context) {
        super(context);
    }

    public MaskImageView(Context context,
                         AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            setMaskBitmap(context, attrs);
        }
    }

    public MaskImageView(Context context,
                         AttributeSet attrs,
                         int defStyle) {
        super(context, attrs, defStyle);
        if (!isInEditMode()) {
            setMaskBitmap(context, attrs);
        }
    }

    @Override
    public void setImageBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }

        if ((mask != null) && !mForceNoMask) {
            Bitmap b;

            if(mMaskRatio != 0.0f) {
                // Scale the given bitmap to match mask size and optionally modify the extract.
                // Zoom out (ratio < 1), keep original (ratio = 1) or zoom in (ratio > 1).
                b = Bitmap.createScaledBitmap(bitmap, (int)(mask.getWidth() * mMaskRatio), (int)(mask.getHeight() * mMaskRatio), false);
            } else {
                b = bitmap;
            }

            Bitmap resultBmp = Bitmap.createBitmap(mask.getWidth(), mask.getHeight(), Config.ARGB_8888);
            Canvas customCanvas = new Canvas(resultBmp);

            float xOffset = (float) (resultBmp.getWidth() - b.getWidth()) / 2;
            float yOffset = (float) (resultBmp.getHeight() - b.getHeight()) / 2;

            customCanvas.drawBitmap(b, xOffset, yOffset, null);

            Paint paint = new Paint();
            paint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
            customCanvas.drawBitmap(mask, 0, 0, paint);

            if(mIndicatorBubbleRadiusFactor > 0.0f) {
                paint.setXfermode(new PorterDuffXfermode(Mode.SRC_OVER));
                paint.setColor(mIndicatorBubbleColor);

                customCanvas.drawCircle(mask.getWidth() * (1 - mIndicatorBubbleRadiusFactor),
                        mask.getHeight() * mIndicatorBubbleRadiusFactor,
                        mask.getHeight() * (mIndicatorBubbleRadiusFactor), paint);

            }

            super.setImageBitmap(resultBmp);
        } else {
            super.setImageBitmap(bitmap);
        }
    }

    public void setImageBitmapFormColor(int color) {
        if ((mask != null) && !mForceNoMask) {
            ColorDrawable drawable = new ColorDrawable(color);
            PorterDuffXfermode xferModeDstIn = new PorterDuffXfermode(Mode.DST_IN);
            Bitmap result = Bitmap.createBitmap(mask.getWidth(), mask.getHeight(), Config.ARGB_8888);

            Canvas customCanvas = new Canvas(result);

            drawable.setBounds(0, 0, mask.getWidth(), mask.getHeight());

            drawable.draw(customCanvas);

            Paint paint = new Paint();

            paint.setXfermode(xferModeDstIn);

            customCanvas.drawBitmap(mask, 0, 0, paint);
            super.setImageBitmap(result);
        }
    }

    public Bitmap getMaskImageFromImage(Bitmap bitmap) {
        Bitmap result = null;

        if ((mask != null) && !mForceNoMask) {
            PorterDuffXfermode xferModeDstIn = new PorterDuffXfermode(Mode.DST_IN);

            result = Bitmap.createBitmap(mask.getWidth(), mask.getHeight(), Config.ARGB_8888);

            float xoffset = (float)(mask.getWidth() - bitmap.getWidth()) / 2;
            float yoffset = (float)(mask.getHeight() - bitmap.getHeight()) / 2;

            Canvas customCanvas = new Canvas(result);

            customCanvas.drawBitmap(bitmap, xoffset, yoffset, null);

            Paint paint = new Paint();

            paint.setXfermode(xferModeDstIn);

            customCanvas.drawBitmap(mask, 0, 0, paint);
        }

        return result;
    }

    public void setMask(final int maskId) {
        mask = BitmapFactory.decodeResource(getResources(), maskId);
    }

    private void setMask(Context context,
                         AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.MaskImageView);
        Drawable maskDrawable = typedArray.getDrawable(R.styleable.MaskImageView_mask);

        if (maskDrawable != null) {
            if (maskDrawable instanceof BitmapDrawable) {
                mask = ((BitmapDrawable) maskDrawable).getBitmap();
            }
        }
    }

    private void setMaskBitmap(Context context,
                               AttributeSet attrs) {
        setMask(context, attrs);

        Drawable imageDrawable = getDrawable();
        Bitmap image = null;

        if ((imageDrawable instanceof BitmapDrawable)) {
            image = ((BitmapDrawable) imageDrawable).getBitmap();
            setImageBitmap(image);
        }
    }

    public void setForceNoMask(boolean forceNoMask) {
        mForceNoMask = forceNoMask;
    }

    /**
     * Set a mask size ratio for the given image bitmap.
     * = 1 - original image will be rescaled to match mask
     * < 1 - image will be shrinked resulting in a zoom-out
     * > 1 - image will be enlarged resulting in a zoom-in
     * @param maskRatio
     */
    public void setMaskRatio(float maskRatio) {
        mMaskRatio = maskRatio;
    }

    /**
     * Set (relative) size and color of an optional indicator bubble
     * The bubble appears in the upper right corner of the image.
     * @param radiusFactor
     * @param color
     */
    public void setIndicatorBubble(float radiusFactor, int color) {
        mIndicatorBubbleRadiusFactor = radiusFactor;
        mIndicatorBubbleColor = color;
    }

}
