package com.a.eye.skywalking.collector.worker.tools;

import java.util.Calendar;

/**
 * @author pengys5
 */
public class DateTools {
    public static int timeStampToSecond(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        return calendar.get(Calendar.SECOND);
    }
}
