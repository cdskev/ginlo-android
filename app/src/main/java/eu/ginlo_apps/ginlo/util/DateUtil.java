// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import android.widget.TextView;
import androidx.annotation.NonNull;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

public class DateUtil {
    private static final Locale locale = Locale.forLanguageTag("en_US_POSIX");

    public static Date utcStringToDate(String utcString) {
        try {
            SimpleDateFormat dateFormat = getDateFormat();

            return dateFormat.parse(utcString);
        } catch (ParseException e) {
            LogUtil.e(DateUtil.class.getName(), e.getMessage(), e);
        }
        return null;
    }

    public static Date utcWithoutMillisStringToDate(String utcString) {
        try {
            SimpleDateFormat dateFormat = getDateFormatWithoutMillis();

            return dateFormat.parse(utcString);
        } catch (ParseException e) {
            LogUtil.e(DateUtil.class.getName(), e.getMessage(), e);
        }
        return null;
    }

    public static Date getDateFromMillis(long millis) {
        Date date = new Date();

        date.setTime(millis);

        return date;
    }

    public static Calendar getCalendarFromDate(Date date) {
        Calendar calendar = new GregorianCalendar();

        calendar.setTime(date);

        return calendar;
    }

    /**
     * @return String in Format dd.MM.yy
     */
    public static String getDateStringFromUTC(String dateInUTC) {
        SimpleDateFormat serverRefDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");
        SimpleDateFormat newFormat = new SimpleDateFormat("dd.MM.yyyy");
        try {
            String dateString = newFormat.format(serverRefDate.parse(dateInUTC));
            return dateString;
        } catch (ParseException e) {
            return dateInUTC;
        }
    }

    /**
     * @return String in Format dd.MM.yy
     */
    public static String getDateStringFromLocale() {
        Calendar calendar = new GregorianCalendar();

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy");

        return dateFormat.format(calendar.getTime());
    }

    /**
     * @param dateStringToCheck String in Format dd.MM.yy
     * @return result
     */
    public static boolean isSameDay(String dateStringToCheck) {
        String today = getDateStringFromLocale();

        return StringUtil.isEqual(dateStringToCheck, today);
    }

    public static String getDateStringFromMillis(long millis) {
        // TODO: Localize Date!
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy");

        Date date = new Date();

        if (millis > -1) {
            date.setTime(millis);
        }

        return dateFormat.format(date);
    }

    public static String getDateAndTimeStringFromMillis(long millis) {
        // TODO: Localize Date!
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy HH:mm");
        Date date = new Date();

        date.setTime(millis);

        return dateFormat.format(date);
    }

    public static void setDateToTextView(@NonNull final Context context, @NonNull final TextView textView, @NonNull final Long date) {
        final String today = DateUtil.getDateStringFromMillis(new Date().getTime());

        final String time = DateUtil.getTimeStringFromMillis(date);
        final String date2 = DateUtil.getDateStringFromMillis(date);
        if (StringUtil.isEqual(today, date2)) {
            textView.setText(context.getResources().getString(R.string.chat_overview_date_today) + " " + time);
        } else {
            textView.setText(date2 + " " + time);
        }
    }

    public static String getTimeStringFromMillis(long millis) {
        // TODO: Localize Date!
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
        Date date = new Date();

        if (millis > -1) {
            date.setTime(millis);
        }

        return dateFormat.format(date);
    }

    public static String dateToUtcString(Date date) {
        SimpleDateFormat dateFormat = getDateFormat();

        return dateFormat.format(date);
    }

    public static String dateToUtcStringWithoutMillis(Date date) {
        SimpleDateFormat dateFormat = getDateFormatWithoutMillis();

        return dateFormat.format(date);
    }

    public static String getCurrentDate() {
        SimpleDateFormat dateFormat = getDateFormat();

        return dateFormat.format(new Date());
    }

    /**
     * @return yyyy.MM.dd
     */
    public static String getDateStringInBackupFormat() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

        Date date = new Date();

        return dateFormat.format(date);
    }

    public static long utcStringToMillis(final String utcString) {
        if (utcString == null) {
            return 0;
        }

        final Date date = utcStringToDate(utcString);

        if (date != null) {
            return date.getTime();
        } else {
            return 0;
        }
    }

    public static long utcWithoutMillisStringToMillis(String utcString) {
        if (utcString == null) {
            return 0;
        }

        Date date = utcWithoutMillisStringToDate(utcString);

        if (date != null) {
            return date.getTime();
        } else {
            return 0;
        }
    }

    public static String utcStringFromMillis(long millis) {
        Date date = DateUtil.getDateFromMillis(millis);

        SimpleDateFormat dateFormat = getDateFormat();

        return dateFormat.format(date);
    }

    private static SimpleDateFormat getDateFormat() {

        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", locale);
    }

    private static SimpleDateFormat getDateFormatWithoutMillis() {

        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");
    }

    public static void fillSilentTillTextView(final TextView textView,
                                              final long timeNow,
                                              final long silentTill,
                                              final String onText,
                                              final String infiniteText,
                                              final String offText
    ) {

        if (textView == null) {
            return;
        }

        if (silentTill == 0 || timeNow > silentTill) {
            textView.setText(offText);
        } else {
            long seconds = (silentTill - timeNow) / 1000;
            final long hours = seconds / 3600;
            seconds = seconds % 3600;
            final long minutes = seconds / 60;

            if (hours > 24) {
                textView.setText(String.format(infiniteText, hours, minutes));
            } else {
                textView.setText(String.format(onText, hours, minutes));
            }
        }
    }
}
