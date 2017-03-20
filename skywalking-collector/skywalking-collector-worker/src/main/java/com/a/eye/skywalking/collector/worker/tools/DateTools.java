package com.a.eye.skywalking.collector.worker.tools;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * @author pengys5
 */
public class DateTools {

    private static final SimpleDateFormat dayDateFormat = new SimpleDateFormat("yyyyMMdd");
    private static final SimpleDateFormat hourDateFormat = new SimpleDateFormat("yyyyMMddHH");
    private static final SimpleDateFormat minuteDateFormat = new SimpleDateFormat("yyyyMMddHHmm");

    public static int getSecond(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        return calendar.get(Calendar.SECOND);
    }

    public static long getMinuteSlice(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = minuteDateFormat.format(calendar.getTime());
        return Long.valueOf(timeStr);
    }

    public static long getHourSlice(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = hourDateFormat.format(calendar.getTime()) + "00";
        return Long.valueOf(timeStr);
    }

    public static long getDaySlice(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = dayDateFormat.format(calendar.getTime()) + "0000";
        return Long.valueOf(timeStr);
    }
}
