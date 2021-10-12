// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.LeadingMarginSpan;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;

import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;

public class ViewUtil {
    @ColorInt
    public static int getColor(@ColorRes final int color, final Context context) {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            return context.getColor(color);
        } else {
            return context.getResources().getColor(color);
        }
    }

    public static void createTextWatcher(final Context context,
                                         @Nullable final EditText editTextBefore,
                                         final EditText editTextTarget,
                                         @Nullable final EditText editTextAfter,
                                         final int numberOfDigits) {
        if (editTextTarget == null) {
            return;
        }

        editTextTarget.addTextChangedListener(new TextWatcher() {
            private int oldCount = 0;

            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {

            }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
                if (s.length() == numberOfDigits && oldCount < editTextTarget.length()) {
                    if (editTextAfter != null) {
                        editTextAfter.requestFocus();
                        editTextAfter.setSelection(0);
                    } else {
                        KeyboardUtil.toggleSoftInputKeyboard(context, editTextTarget, false);
                    }
                } else if (editTextTarget.getSelectionStart() == 0) {
                    if (editTextBefore != null) {
                        editTextBefore.requestFocus();
                        editTextBefore.setSelection(editTextBefore.length());
                    }
                }
                oldCount = editTextTarget.length();
            }

            @Override
            public void afterTextChanged(final Editable s) {

            }
        });
    }

    static float convertDpToPixelFloat(float dp) {
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();

        return dp * (metrics.densityDpi / 160f);
    }

    static int convertDpToPixelInt(float dp) {
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        float px = dp * (metrics.densityDpi / 160f);
        return Math.round(px);
    }

    public static void createOnKeyListener(final EditText editTextBefore, final EditText editTextTarget) {
        if (editTextTarget == null || editTextBefore == null) {
            return;
        }

        editTextTarget.setOnKeyListener(new View.OnKeyListener() {

            @Override
            public boolean onKey(final View v, final int keyCode, final KeyEvent event) {
                if (editTextTarget.getText().length() == 0 && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                    editTextBefore.requestFocus();
                    editTextBefore.setSelection(editTextBefore.length());
                    return true;
                }

                return false;
            }
        });
    }

    public static void tryFlowText(String text, View thumbnailView, TextView messageView, Display display, boolean onlyOneLine) {
        // Get height and width of the image and height of the text line
        final Point point = new Point();
        display.getSize(point);
        thumbnailView.measure(point.x, point.y);
        int height = thumbnailView.getMeasuredHeight();
        int width = thumbnailView.getMeasuredWidth();

        int lines;

        if (onlyOneLine) {
            lines = 1;
        } else {
            float textLineHeight = messageView.getPaint().getTextSize();

            // Set the span according to the number of lines and width of the image
            lines = (int) Math.ceil(height / textLineHeight);
        }

        //For an html text you can use this line: SpannableStringBuilder ss = (SpannableStringBuilder)Html.fromHtml(text);
        SpannableString ss = new SpannableString(text);
        ss.setSpan(new MyLeadingMarginSpan2(lines, width), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        messageView.setText(ss);
    }

    private static class MyLeadingMarginSpan2 implements LeadingMarginSpan.LeadingMarginSpan2 {
        private final int margin;
        private final int lines;
        private boolean wasDrawCalled = false;
        private int drawLineCount = 0;

        MyLeadingMarginSpan2(int lines, int margin) {
            this.margin = margin;
            this.lines = lines;
        }

        @Override
        public int getLeadingMargin(boolean first) {
            boolean isFirstMargin = first;
            // a different algorithm for api 21+
            if (this.lines > 1) {
                this.drawLineCount = this.wasDrawCalled ? this.drawLineCount + 1 : 0;
                this.wasDrawCalled = false;
                isFirstMargin = this.drawLineCount <= this.lines;
            }

            return isFirstMargin ? this.margin : 0;
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom, CharSequence text, int start, int end, boolean first, Layout layout) {
            this.wasDrawCalled = true;
        }

        @Override
        public int getLeadingMarginLineCount() {
            return this.lines;
        }
    }
}
