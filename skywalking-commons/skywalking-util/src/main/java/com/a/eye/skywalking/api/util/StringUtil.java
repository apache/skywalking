package com.a.eye.skywalking.api.util;

public final class StringUtil {
    public static boolean isEmpty(String str) {
        if (str == null || "".equals(str) || str.length() == 0) {
            return true;
        }
        return false;
    }

    public static String join(final char delimiter, final String... strings) {
        if (strings.length == 0) {
            return null;
        }
        if (strings.length == 1) {
            return strings[0];
        }
        int length = strings.length - 1;
        for (final String s : strings) {
            length += s.length();
        }
        final StringBuilder sb = new StringBuilder(length);
        sb.append(strings[0]);
        for (int i = 1; i < strings.length; ++i) {
            if (!isEmpty(strings[i])) {
                sb.append(delimiter).append(strings[i]);
            }
        }
        return sb.toString();
    }
}
