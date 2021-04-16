// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view;

import android.content.Context;
import android.util.AttributeSet;
import androidx.emoji.widget.EmojiAppCompatTextView;
import eu.ginlo_apps.ginlo.util.FontUtil;

/**
 * @author Florian
 * @version $Id$
 */
public class EmojiconMediumTextView
        extends EmojiAppCompatTextView {

    public EmojiconMediumTextView(final Context context) {
        super(context);

        createFont(context);
    }

    public EmojiconMediumTextView(final Context context,
                                  final AttributeSet attrs) {
        super(context, attrs);

        createFont(context);
    }

    public EmojiconMediumTextView(final Context context,
                                  final AttributeSet attrs,
                                  final int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        createFont(context);
    }

    private void createFont(final Context context) {
        if (!isInEditMode()) {
            setTypeface(FontUtil.getTypeFace(context));
        }
    }
}
