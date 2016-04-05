package com.ai.cloud.skywalking.web.util;

public class StringUtil {

    public static boolean isBlank(String str) {
        if (str == null || str.length() == 0) {
            return true;
        }
        return false;
    }
}
