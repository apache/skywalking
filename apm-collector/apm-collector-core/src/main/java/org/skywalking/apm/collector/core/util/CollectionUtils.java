package org.skywalking.apm.collector.core.util;

import com.sun.istack.internal.Nullable;
import java.util.Map;

/**
 * @author pengys5
 */
public class CollectionUtils {

    public static boolean isEmpty(@Nullable Map map) {
        return (map == null || map.size() == 0);
    }
}
