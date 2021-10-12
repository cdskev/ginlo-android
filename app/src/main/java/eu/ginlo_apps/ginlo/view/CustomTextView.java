// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view;

import android.content.Context;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import androidx.appcompat.widget.AppCompatTextView;

public class CustomTextView
        extends AppCompatTextView {
    private static final float MIN_TEXT_SIZE = 20;

    private static final String ELLIPSIS = "...";

    private boolean mNeedsResize = false;

    private float mTextSize;

    private float mMaxTextSize = 0;

    private final float mMinTextSize = MIN_TEXT_SIZE;

    private float mSpacingMult = 1.0f;

    private float mSpacingAdd = 0.0f;

    private final boolean mAddEllipsis = true;

    public CustomTextView(final Context context) {
        this(context, null);
    }

    public CustomTextView(final Context context,
                          final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomTextView(final Context context,
                          final AttributeSet attrs,
                          final int defStyle) {
        super(context, attrs, defStyle);
        mTextSize = getTextSize();
    }

    /**
     * When text changes, set the force resize flag to true and reset the text
     * size.
     */
    @Override
    protected void onTextChanged(final CharSequence text,
                                 final int start,
                                 final int before,
                                 final int after) {
        mNeedsResize = true;

        // Since this view may be reused, it is good to reset the text size
        resetTextSize();
    }

    /**
     * If the text view size changed, set the force resize flag to true
     */
    @Override
    protected void onSizeChanged(final int w,
                                 final int h,
                                 final int oldw,
                                 final int oldh) {
        if ((w != oldw) || (h != oldh)) {
            mNeedsResize = true;
        }
    }

    /**
     * Override the set text size to update our internal reference values
     */
    @Override
    public void setTextSize(final float size) {
        super.setTextSize(size);
        mTextSize = getTextSize();
    }

    /**
     * Override the set text size to update our internal reference values
     */
    @Override
    public void setTextSize(final int unit,
                            final float size) {
        super.setTextSize(unit, size);
        mTextSize = getTextSize();
    }

    /**
     * Override the set line spacing to update our internal reference values
     */
    @Override
    public void setLineSpacing(final float add,
                               final float mult) {
        super.setLineSpacing(add, mult);
        mSpacingMult = mult;
        mSpacingAdd = add;
    }

    /**
     * Reset the text to the original size
     */
    private void resetTextSize() {
        if (mTextSize > 0) {
            super.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
            mMaxTextSize = mTextSize;
        }
    }

    /**
     * Resize text after measuring
     */
    @Override
    protected void onLayout(final boolean changed,
                            final int left,
                            final int top,
                            final int right,
                            final int bottom) {
        if (changed || mNeedsResize) {
            final int widthLimit = (right - left) - getCompoundPaddingLeft() - getCompoundPaddingRight();
            final int heightLimit = (bottom - top) - getCompoundPaddingBottom() - getCompoundPaddingTop();

            resizeText(widthLimit, heightLimit);
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    /**
     * Resize the text size with specified width and height
     */
    private void resizeText(final int width,
                            final int height) {
        CharSequence text = getText();

        // Do not resize if the view does not have dimensions or there is no text
        if ((text == null) || (text.length() == 0) || (height <= 0) || (width <= 0) || (mTextSize == 0)) {
            return;
        }

        if (getTransformationMethod() != null) {
            text = getTransformationMethod().getTransformation(text, this);
        }

        // Get the text view's paint object
        final TextPaint textPaint = getPaint();

        // If there is a max text size set, use the lesser of that and the default text size
        float targetTextSize = (mMaxTextSize > 0) ? Math.min(mTextSize, mMaxTextSize) : mTextSize;

        // Get the required text height
        int textHeight = getTextHeight(text, textPaint, width, targetTextSize);

        // Until we either fit within our text view or we had reached our min text size, incrementally try smaller sizes
        while ((textHeight > height) && (targetTextSize > mMinTextSize)) {
            targetTextSize = Math.max(targetTextSize - 2, mMinTextSize);
            textHeight = getTextHeight(text, textPaint, width, targetTextSize);
        }

        // If we had reached our minimum text size and still don't fit, append an ellipsis
        if (mAddEllipsis && (targetTextSize == mMinTextSize) && (textHeight > height)) {
            // Draw using a static layout
            // modified: use a copy of TextPaint for measuring
            final TextPaint paint = new TextPaint(textPaint);

            // Draw using a static layout
            final StaticLayout layout = new StaticLayout(text, paint, width, Alignment.ALIGN_NORMAL, mSpacingMult,
                    mSpacingAdd, false);

            // Check that we have a least one line of rendered text
            if (layout.getLineCount() > 0) {
                // Since the line at the specific vertical position would be cut off,
                // we must trim up to the previous line
                final int lastLine = layout.getLineForVertical(height) - 1;

                // If the text would not even fit on a single line, clear it
                if (lastLine < 0) {
                    setText("");
                }

                // Otherwise, trim to the previous line and add an ellipsis
                else {
                    final int start = layout.getLineStart(lastLine);
                    int end = layout.getLineEnd(lastLine);
                    float lineWidth = layout.getLineWidth(lastLine);
                    final float ellipseWidth = textPaint.measureText(ELLIPSIS);

                    // Trim characters off until we have enough room to draw the ellipsis
                    while (width < (lineWidth + ellipseWidth)) {
                        lineWidth = textPaint.measureText(text.subSequence(start, --end + 1).toString());
                    }
                    setText(text.subSequence(0, end) + ELLIPSIS);
                }
            }
        }

        // Some devices try to auto adjust line spacing, so force default line spacing
        // and invalidate the layout as a side effect
        setTextSize(TypedValue.COMPLEX_UNIT_PX, targetTextSize);
        setLineSpacing(mSpacingAdd, mSpacingMult);

        // Reset force resize flag
        mNeedsResize = false;
    }

    // Set the text size of the text paint object and use a static layout to render text off screen before measuring
    private int getTextHeight(final CharSequence source,
                              final TextPaint paint,
                              final int width,
                              final float textSize) {
        // modified: make a copy of the original TextPaint object for measuring
        // (apparently the object gets modified while measuring, see also the
        // docs for TextView.getPaint() (which states to access it read-only)
        final TextPaint paintCopy = new TextPaint(paint);

        // Update the text paint object
        paintCopy.setTextSize(textSize);

        // Measure using a static layout
        final StaticLayout layout = new StaticLayout(source, paintCopy, width, Alignment.ALIGN_NORMAL, mSpacingMult,
                mSpacingAdd, true);

        return layout.getHeight();
    }
}
