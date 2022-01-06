// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.MainActivity;
import eu.ginlo_apps.ginlo.log.LogUtil;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class SystemUtil {
    public static void restart(Context context,
                               int delay) {
        restart(context, delay, MainActivity.class);
    }

    private static void restart(Context context,
                                int delay,
                                Class<?> activityClass) {
        Intent intent = new Intent(context, activityClass);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(MainActivity.EXTRA_IS_RESTART, true);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        manager.set(AlarmManager.RTC, System.currentTimeMillis() + delay, pendingIntent);

        Process.killProcess(Process.myPid());
    }

    public static Class<?> getClassForBuildConfigClassname(String className)
            throws ClassNotFoundException {
        String packageName = BuildConfig.class.getPackage().getName();

        return Class.forName(packageName + className);
    }

    @Nullable
    public static <T> T dynamicDownCast(@Nullable Object o, @NonNull Class<T> expectedType) {
        if (o == null) {
            return null;
        }
        if (expectedType.isInstance(o)) {
            return expectedType.cast(o);
        }
        return null;
    }

    public static <E> List<E> dynamicCastToList(@Nullable Object o, Class<E> clazzE) {
        try {
            List<?> list = dynamicDownCast(o, List.class);

            if (list == null) {
                return null;
            }

            for (Object entry : list) {
                checkCast(clazzE, entry);
            }

            @SuppressWarnings("unchecked")
            List<E> result = (List<E>) list;

            return result;
        } catch (ClassCastException e) {
            LogUtil.e("SystemUtil", e.getMessage(), e);
            return null;
        }
    }

    private static <T> void checkCast(Class<T> clazz, Object obj)
            throws ClassCastException {
        if (!clazz.isInstance(obj)) {
            throw new ClassCastException("Expected: " + clazz.getName() + "\nWas: " + obj.getClass().getName() + "\nValue: " + obj);
        }
    }
}
