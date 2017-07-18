package org.skywalking.apm.collector.core.util;

/**
 * @author pengys5
 */
public class ObjectUtils {
    public static boolean isEmpty(Object obj) {
        return obj == null;
    }

    public static boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }
}
