// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class FontUtil {

    private static final SparseArray<Typeface> TYPE_FACES = new SparseArray<>(1);

    public static Typeface getTypeFace(Context context) {
        Typeface typeface = TYPE_FACES.get(0);

        if (typeface == null) {
            typeface = Typeface.createFromAsset(context.getAssets(), "fonts/roboto_medium.ttf");
            TYPE_FACES.append(0, typeface);
        }

        return typeface;
    }
}
