// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.ShapeDrawable.ShaderFactory;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.util.AttributeSet;
import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import eu.ginlo_apps.ginlo.R;

public class FloatingActionButton
        extends AppCompatImageButton {
    private static final int SIZE_NORMAL = 0;

    private static final int MAX_ALPHA = 255;

    private int mColorNormal;

    private int mColorPressed;

    private int mColorDisabled;

    @DrawableRes
    private int mIcon;

    private Drawable mIconDrawable;

    private int mSize;

    private double mCircleSize;

    private double mShadowRadius;

    private double mShadowOffset;

    private int mDrawableSize;

    private boolean mStrokeVisible;

    public FloatingActionButton(final Context context) {
        this(context, null);
    }

    public FloatingActionButton(final Context context,
                                final AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public FloatingActionButton(final Context context,
                                final AttributeSet attrs,
                                final int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(final Context context,
                      final AttributeSet attributeSet) {
        final TypedArray attr = context.obtainStyledAttributes(attributeSet, R.styleable.FloatingActionButton, 0, 0);

        mColorNormal = attr.getColor(R.styleable.FloatingActionButton_fab_colorNormal,
                getColor(android.R.color.holo_blue_dark));
        mColorPressed = attr.getColor(R.styleable.FloatingActionButton_fab_colorPressed,
                getColor(android.R.color.holo_blue_light));
        mColorDisabled = attr.getColor(R.styleable.FloatingActionButton_fab_colorDisabled,
                getColor(android.R.color.darker_gray));
        mSize = attr.getInt(R.styleable.FloatingActionButton_fab_size, SIZE_NORMAL);
        mIcon = attr.getResourceId(R.styleable.FloatingActionButton_fab_icon, 0);
        mStrokeVisible = attr.getBoolean(R.styleable.FloatingActionButton_fab_stroke_visible, true);

        updateCircleSize();
        mShadowRadius = getDimension(R.dimen.fab_shadow_radius);
        mShadowOffset = getDimension(R.dimen.fab_shadow_offset);
        updateDrawableSize();

        updateBackground();
    }

    private void updateDrawableSize() {
        mDrawableSize = (int) (mCircleSize + (2 * mShadowRadius));
    }

    private void updateCircleSize() {
        mCircleSize = getDimension((mSize == SIZE_NORMAL) ? R.dimen.fab_size_normal : R.dimen.fab_size_mini);
    }

    public void setColorNormal(final int color) {
        if (mColorNormal != color) {
            mColorNormal = color;
            updateBackground();
        }
    }

    public void setColorPressed(final int color) {
        if (mColorPressed != color) {
            mColorPressed = color;
            updateBackground();
        }
    }

    public void setColorDisabled(final int color) {
        if (mColorDisabled != color) {
            mColorDisabled = color;
            updateBackground();
        }
    }

    private int getColor(@ColorRes final int id) {
        return getResources().getColor(id);
    }

    private float getDimension(@DimenRes final int id) {
        return getResources().getDimension(id);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec,
                             final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(mDrawableSize, mDrawableSize);
    }

    private void updateBackground() {
        final double strokeWidth = getDimension(R.dimen.fab_stroke_width);
        final double halfStrokeWidth = strokeWidth / 2;

        final LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]
                {
                        getResources().getDrawable((mSize == SIZE_NORMAL)
                                ? R.drawable.fab_bg
                                : R.drawable.fab_bg),
                        createFillDrawable(strokeWidth),
                        createOuterStrokeDrawable(strokeWidth),
                        getIconDrawable()
                });

        final int iconOffset = (int) (mCircleSize - getDimension(R.dimen.fab_icon_size)) / 2;

        final int circleInsetHorizontal = (int) (mShadowRadius);
        final int circleInsetTop = (int) (mShadowRadius - mShadowOffset);
        final int circleInsetBottom = (int) (mShadowRadius + mShadowOffset);

        layerDrawable.setLayerInset(1, circleInsetHorizontal, circleInsetTop, circleInsetHorizontal, circleInsetBottom);

        layerDrawable.setLayerInset(2, (int) (circleInsetHorizontal - halfStrokeWidth),
                (int) (circleInsetTop - halfStrokeWidth),
                (int) (circleInsetHorizontal - halfStrokeWidth),
                (int) (circleInsetBottom - halfStrokeWidth));

        layerDrawable.setLayerInset(3, circleInsetHorizontal + iconOffset, circleInsetTop + iconOffset,
                circleInsetHorizontal + iconOffset, circleInsetBottom + iconOffset);

        setBackgroundCompat(layerDrawable);
    }

    public Drawable getIconDrawable() {
        if (mIconDrawable != null) {
            return mIconDrawable;
        } else if (mIcon != 0) {
            return getResources().getDrawable(mIcon);
        } else {
            return new ColorDrawable(Color.TRANSPARENT);
        }
    }

    public void setIconDrawable(@NonNull final Drawable iconDrawable) {
        if (mIconDrawable != iconDrawable) {
            mIcon = 0;
            mIconDrawable = iconDrawable;
            updateBackground();
        }
    }

    private StateListDrawable createFillDrawable(final double strokeWidth) {
        final StateListDrawable drawable = new StateListDrawable();

        drawable.addState(new int[]{-android.R.attr.state_enabled}, createCircleDrawable(mColorDisabled, strokeWidth));
        drawable.addState(new int[]{android.R.attr.state_pressed}, createCircleDrawable(mColorPressed, strokeWidth));
        drawable.addState(new int[]{}, createCircleDrawable(mColorNormal, strokeWidth));
        return drawable;
    }

    private Drawable createCircleDrawable(final int color,
                                          final double strokeWidth) {
        final int alpha = Color.alpha(color);
        final int opaqueColor = opaque(color);

        final ShapeDrawable fillDrawable = new ShapeDrawable(new OvalShape());

        final Paint paint = fillDrawable.getPaint();

        paint.setAntiAlias(true);
        paint.setColor(opaqueColor);

        final Drawable[] layers = {
                fillDrawable,
                createInnerStrokesDrawable(opaqueColor, strokeWidth)
        };

        final LayerDrawable drawable = ((alpha == MAX_ALPHA) || !mStrokeVisible) ? new LayerDrawable(layers)
                : new TranslucentLayerDrawable(alpha, layers);

        final int halfStrokeWidth = (int) (strokeWidth / 2);

        drawable.setLayerInset(1, halfStrokeWidth, halfStrokeWidth, halfStrokeWidth, halfStrokeWidth);

        return drawable;
    }

    private Drawable createOuterStrokeDrawable(final double strokeWidth) {
        final ShapeDrawable shapeDrawable = new ShapeDrawable(new OvalShape());

        final Paint paint = shapeDrawable.getPaint();

        paint.setAntiAlias(true);
        paint.setStrokeWidth((float) strokeWidth);
        paint.setStyle(Style.STROKE);
        paint.setColor(Color.BLACK);
        paint.setAlpha(opacityToAlpha());

        return shapeDrawable;
    }

    private int opacityToAlpha() {
        return (int) (MAX_ALPHA * 0.02);
    }

    private int darkenColor(final int argb) {
        return adjustColorBrightness(argb, 0.9f);
    }

    private int lightenColor(final int argb) {
        return adjustColorBrightness(argb, 1.1f);
    }

    private int adjustColorBrightness(final int argb,
                                      final double factor) {
        float[] hsv = new float[3];

        Color.colorToHSV(argb, hsv);

        hsv[2] = (float) Math.min(hsv[2] * factor, 1f);

        return Color.HSVToColor(Color.alpha(argb), hsv);
    }

    private int halfTransparent(final int argb) {
        return Color.argb(Color.alpha(argb) / 2, Color.red(argb), Color.green(argb), Color.blue(argb));
    }

    private int opaque(final int argb) {
        return Color.rgb(Color.red(argb), Color.green(argb), Color.blue(argb));
    }

    private Drawable createInnerStrokesDrawable(final int color,
                                                final double strokeWidth) {
        if (!mStrokeVisible) {
            return new ColorDrawable(Color.TRANSPARENT);
        }

        final ShapeDrawable shapeDrawable = new ShapeDrawable(new OvalShape());

        final int bottomStrokeColor = darkenColor(color);
        final int bottomStrokeColorHalfTransparent = halfTransparent(bottomStrokeColor);
        final int topStrokeColor = lightenColor(color);
        final int topStrokeColorHalfTransparent = halfTransparent(topStrokeColor);

        final Paint paint = shapeDrawable.getPaint();

        paint.setAntiAlias(true);
        paint.setStrokeWidth((float) strokeWidth);
        paint.setStyle(Style.STROKE);
        shapeDrawable.setShaderFactory(new ShaderFactory() {
            @Override
            public Shader resize(final int width,
                                 final int height) {
                return new LinearGradient(width / 2, 0, width / 2, height,
                        new int[]
                                {
                                        topStrokeColor,
                                        topStrokeColorHalfTransparent,
                                        color,
                                        bottomStrokeColorHalfTransparent,
                                        bottomStrokeColor
                                }, new float[]
                        {
                                0f,
                                0.2f,
                                0.5f,
                                0.8f,
                                1f
                        }, TileMode.CLAMP);
            }
        });

        return shapeDrawable;
    }

    private void setBackgroundCompat(final Drawable drawable) {
        setBackground(drawable);
    }

    @Override
    public void setVisibility(final int visibility) {
        super.setVisibility(visibility);
    }

    private static class TranslucentLayerDrawable
            extends LayerDrawable {

        private final int mAlpha;

        private TranslucentLayerDrawable(final int alpha,
                                         final Drawable... layers) {
            super(layers);
            mAlpha = alpha;
        }

        @Override
        public void draw(final Canvas canvas) {
            final Rect bounds = getBounds();

            canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, mAlpha, Canvas.ALL_SAVE_FLAG);
            super.draw(canvas);
            canvas.restore();
        }
    }
}
