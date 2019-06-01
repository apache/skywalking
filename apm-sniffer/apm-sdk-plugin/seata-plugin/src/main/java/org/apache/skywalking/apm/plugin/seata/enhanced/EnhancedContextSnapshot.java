package org.apache.skywalking.apm.plugin.seata.enhanced;

import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;

import java.util.HashMap;
import java.util.Map;

public class EnhancedContextSnapshot {
    private final ContextSnapshot contextSnapshot;
    private final Map<String, String> headers = new HashMap<String, String>();

    public EnhancedContextSnapshot(final ContextSnapshot contextSnapshot) {
        this.contextSnapshot = contextSnapshot;
    }

    public ContextSnapshot getContextSnapshot() {
        return contextSnapshot;
    }

    public void set(final String key, final String value) {
        headers.put(key, value);
    }

    public String get(final String key) {
        return headers.get(key);
    }
}
