package com.a.eye.skywalking.collector.worker.tools;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * @author pengys5
 */
public class DateTools {

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");

    public static final String Time_Slice_Column_Name = "timeSlice";

    public static int timeStampToSecond(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        return calendar.get(Calendar.SECOND);
    }

    public static long timeStampToTimeSlice(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = sdf.format(calendar.getTime());
        return Long.valueOf(timeStr);
    }
}
