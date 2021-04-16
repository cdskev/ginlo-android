// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.content.Context;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.util.DateUtil;

import java.util.Calendar;
import java.util.Date;

public class TimeUtil {
    private final Context context;

    public TimeUtil(Context ctx) {
        this.context = ctx;
    }

    public String getDateLabel(Long lastMessageDate) {
        if (lastMessageDate == 0) {
            return "";
        }

        Date dateNow = new Date();
        Date date = eu.ginlo_apps.ginlo.util.DateUtil.getDateFromMillis(lastMessageDate);

        Calendar calendarNow = eu.ginlo_apps.ginlo.util.DateUtil.getCalendarFromDate(dateNow);
        Calendar calendar = eu.ginlo_apps.ginlo.util.DateUtil.getCalendarFromDate(date);

        boolean sameYear = calendarNow.get(Calendar.YEAR) == calendar.get(Calendar.YEAR);
        int diffDays = calendarNow.get(Calendar.DAY_OF_YEAR) - calendar.get(Calendar.DAY_OF_YEAR);

        if (sameYear && (diffDays == 0)) {
            return eu.ginlo_apps.ginlo.util.DateUtil.getTimeStringFromMillis(lastMessageDate);
        }
        if (sameYear && (diffDays == 1)) {
            return context.getString(R.string.chat_overview_date_yesterday);
        }
        return DateUtil.getDateStringFromMillis(lastMessageDate);
    }
}
