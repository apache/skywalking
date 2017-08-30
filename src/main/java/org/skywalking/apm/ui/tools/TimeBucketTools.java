package org.skywalking.apm.ui.tools;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * @author pengys5
 */
public class TimeBucketTools {
    public static String format(long milliseconds) {
        Instant instant = Instant.ofEpochMilli(milliseconds);
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, TimeZone.getDefault().toZoneId());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss SSS");
        return dateTime.format(formatter);
    }

    public static long buildToSecondTimeBucket(String sliceType, long timeBucket) {
        if (Type.MINUTE.name().toLowerCase().equals(sliceType.toLowerCase())) {
            return timeBucket * 100;
        } else if (Type.HOUR.name().toLowerCase().equals(sliceType.toLowerCase())) {
            return timeBucket * 100 * 100;
        } else if (Type.DAY.name().toLowerCase().equals(sliceType.toLowerCase())) {
            return timeBucket * 100 * 100 * 100;
        } else if (Type.SECOND.name().toLowerCase().equals(sliceType.toLowerCase())) {
            return timeBucket;
        } else {
            throw new IllegalArgumentException("slice type error");
        }
    }

    public static String buildXAxis(String timeBucketType, String timeBucket) {
        if (Type.MINUTE.name().toLowerCase().equals(timeBucketType.toLowerCase())) {
            String hourValue = timeBucket.substring(8, 10);
            String minuteValue = timeBucket.substring(10, 12);
            return hourValue + ":" + minuteValue;
        } else if (Type.HOUR.name().toLowerCase().equals(timeBucketType.toLowerCase())) {
            String dayValue = timeBucket.substring(6, 8);
            String hourValue = timeBucket.substring(8, 10);
            return dayValue + " " + hourValue;
        } else {
            String monthValue = timeBucket.substring(4, 6);
            String dayValue = timeBucket.substring(6, 8);
            return monthValue + "-" + dayValue;
        }
    }

    public enum Type {
        DAY, HOUR, MINUTE, SECOND
    }
}
