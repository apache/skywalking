package org.skywalking.apm.agent.core.context.util;

import java.util.List;

public class KeyValuePairReader {
    public static String get(List<KeyValuePair> pairs, String key) {
        for (KeyValuePair pair : pairs) {
            if (pair.getKey().equals(key)) {
                return pair.getValue();
            }
        }

        return null;
    }
}
