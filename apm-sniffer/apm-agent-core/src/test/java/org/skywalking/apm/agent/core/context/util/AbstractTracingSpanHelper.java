package org.skywalking.apm.agent.core.context.util;

import java.util.Collections;
import java.util.List;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.LogDataEntity;

public class AbstractTracingSpanHelper {
    public static int getParentSpanId(AbstractTracingSpan tracingSpan) {
        try {
            return FieldGetter.get2LevelParentFieldValue(tracingSpan, "parentSpanId");
        } catch (Exception e) {
        }

        return -9999;
    }

    public static List<LogDataEntity> getLogs(AbstractTracingSpan tracingSpan) {
        try {
            return FieldGetter.get2LevelParentFieldValue(tracingSpan, "logs");
        } catch (Exception e) {
        }

        return Collections.emptyList();
    }
}
