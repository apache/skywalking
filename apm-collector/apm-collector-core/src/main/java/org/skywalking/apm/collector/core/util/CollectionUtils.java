package org.skywalking.apm.collector.core.util;

import java.util.List;
import java.util.Map;

/**
 * @author pengys5
 */
public class CollectionUtils {

    public static boolean isEmpty(Map map) {
        return map == null || map.size() == 0;
    }

    public static boolean isEmpty(List list) {
        return list == null || list.size() == 0;
    }

    public static boolean isNotEmpty(List list) {
        return !isEmpty(list);
    }

    public static boolean isNotEmpty(Map map) {
        return !isEmpty(map);
    }

    public static <T> boolean isNotEmpty(T[] array) {
        return array != null && array.length > 0;
    }
}
