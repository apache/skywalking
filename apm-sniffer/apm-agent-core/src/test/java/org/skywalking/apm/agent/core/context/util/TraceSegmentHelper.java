package org.skywalking.apm.agent.core.context.util;

import java.lang.reflect.Field;
import java.util.List;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;

public class TraceSegmentHelper {

    public static List<AbstractTracingSpan> getSpans(TraceSegment traceSegment) {
        try {
            Field field = AbstractTracingSpan.class.getDeclaredField("spans");
            return (List<AbstractTracingSpan>)field.get(traceSegment);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get spans", e);
        }
    }


}
