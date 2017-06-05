package org.skywalking.apm.collector.worker.tools;

import java.util.List;

/**
 * @author pengys5
 */
public class CollectionTools {

    public static boolean isEmpty(List list) {
        return list == null || list.size() == 0;
    }

    public static boolean isNotEmpty(List list) {
        return !isEmpty(list);
    }
}
