package org.skywalking.apm.collector.agentstream.worker.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * @author pengys5
 */
public enum TimeBucketUtils {
    INSTANCE;

    private final SimpleDateFormat dayDateFormat = new SimpleDateFormat("yyyyMMdd");
    private final SimpleDateFormat hourDateFormat = new SimpleDateFormat("yyyyMMddHH");
    private final SimpleDateFormat minuteDateFormat = new SimpleDateFormat("yyyyMMddHHmm");
    private final SimpleDateFormat secondDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    public long getMinuteTimeBucket(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = minuteDateFormat.format(calendar.getTime());
        return Long.valueOf(timeStr);
    }

    public long getSecondTimeBucket(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = secondDateFormat.format(calendar.getTime());
        return Long.valueOf(timeStr);
    }

    public long getHourTimeBucket(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = hourDateFormat.format(calendar.getTime()) + "00";
        return Long.valueOf(timeStr);
    }

    public long getDayTimeBucket(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        String timeStr = dayDateFormat.format(calendar.getTime()) + "0000";
        return Long.valueOf(timeStr);
    }

    public long changeToUTCTimeBucket(long timeBucket) {
        String timeBucketStr = String.valueOf(timeBucket);

        if (TimeZone.getDefault().getID().equals("GMT+08:00") || timeBucketStr.endsWith("0000")) {
            return timeBucket;
        } else {
            return timeBucket - 800;
        }
    }
}
