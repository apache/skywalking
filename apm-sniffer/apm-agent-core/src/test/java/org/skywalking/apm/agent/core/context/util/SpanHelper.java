package org.skywalking.apm.agent.core.context.util;

import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;

public class SpanHelper {

    public static SpanLayer getLayer(AbstractSpan tracingSpan) {
        try {
            return FieldGetter.get2LevelParentFieldValue(tracingSpan, "layer");
        } catch (Exception e) {
        }

        return null;
    }

    public static int getComponentId(AbstractSpan tracingSpan) {
        try {
            return FieldGetter.get2LevelParentFieldValue(tracingSpan, "componentId");
        } catch (Exception e) {
        }

        return -1;
    }

}
