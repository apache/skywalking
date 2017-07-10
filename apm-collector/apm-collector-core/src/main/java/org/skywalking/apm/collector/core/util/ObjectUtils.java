package org.skywalking.apm.collector.core.util;

import com.sun.istack.internal.Nullable;

/**
 * @author pengys5
 */
public class ObjectUtils {
    public static boolean isEmpty(@Nullable Object obj) {
        return obj == null;
    }
}
