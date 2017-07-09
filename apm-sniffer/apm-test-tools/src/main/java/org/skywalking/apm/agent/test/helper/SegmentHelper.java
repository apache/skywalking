package org.skywalking.apm.agent.test.helper;

import java.util.List;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;

public class SegmentHelper {

    public static List<AbstractTracingSpan> getSpans(TraceSegment traceSegment) {
        try {
            return FieldGetter.getValue(traceSegment, "spans");
        } catch (Exception e) {
        }

        return null;
    }
}
