package org.skywalking.apm.collector.core.util;

import java.util.Map;

/**
 * @author pengys5
 */
public class CollectionUtils {

    public static boolean isEmpty(Map map) {
        return map == null || map.size() == 0;
    }
}
