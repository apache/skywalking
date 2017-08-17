package org.skywalking.apm.collector.core.framework;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public enum CollectorContextHelper {
    INSTANCE;

    private Map<String, Context> contexts = new LinkedHashMap();

    public Context getContext(String moduleGroupName) {
        return contexts.get(moduleGroupName);
    }

    public void putContext(Context context) {
        if (contexts.containsKey(context.getGroupName())) {
            throw new UnsupportedOperationException("This module context was put, do not allow put a new one");
        } else {
            contexts.put(context.getGroupName(), context);
        }
    }
}
