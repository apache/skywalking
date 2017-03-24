package com.a.eye.skywalking.ui.tools;

/**
 * @author pengys5
 */
public class TimeTools {

    public static final String Minute = "minute";
    public static final String Hour = "hour";
    public static final String Day = "day";

    public static long buildFullTime(String sliceType, long time) {
        if (Minute.equals(sliceType)) {
            return 0;
        } else if (Hour.equals(sliceType)) {
            return 0;
        } else if (Day.equals(sliceType)) {
            return 0;
        } else {
            throw new IllegalArgumentException("slice type error");
        }
    }
}
