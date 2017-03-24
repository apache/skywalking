package com.a.eye.skywalking.collector.worker.tools;

import java.util.List;

/**
 * @author pengys5
 */
public class CollectionTools {

    public static boolean isEmpty(List list) {
        if (list == null || list.size() == 0) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isNotEmpty(List list) {
        if (list == null || list.size() == 0) {
            return false;
        } else {
            return true;
        }
    }
}
