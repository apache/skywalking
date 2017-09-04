package org.skywalking.apm.collector.core.framework;

import java.util.List;

/**
 * @author pengys5
 */
public class PriorityDecision implements Decision {

    public Object decide(List<Priority> source) {
        return source.get(0);
    }

    public static class Priority {
        private final int value;
        private final Object object;

        public Priority(int value, Object object) {
            this.value = value;
            this.object = object;
        }

        public int getValue() {
            return value;
        }

        public Object getObject() {
            return object;
        }
    }
}
