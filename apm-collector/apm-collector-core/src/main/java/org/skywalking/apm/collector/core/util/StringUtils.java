package org.skywalking.apm.collector.core.util;

import com.sun.istack.internal.Nullable;

/**
 * @author pengys5
 */
public class StringUtils {

    public static final String EMPTY_STRING = "";

    public static boolean isEmpty(@Nullable Object str) {
        return (str == null || EMPTY_STRING.equals(str));
    }
}
