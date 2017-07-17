package org.skywalking.apm.collector.core.util;

/**
 * @author pengys5
 */
public class StringUtils {

    public static final String EMPTY_STRING = "";

    public static boolean isEmpty(Object str) {
        return str == null || EMPTY_STRING.equals(str);
    }
}
