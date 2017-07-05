package org.skywalking.apm.agent.core.context.util;

import java.lang.reflect.Field;
import org.skywalking.apm.agent.core.context.trace.TraceSegmentRef;

public class TraceSegmentRefHelper {

    public static int getSpanId(TraceSegmentRef ref) {
        try {
            Field field = TraceSegmentRef.class.getDeclaredField("spanId");
            return Integer.valueOf(field.get(ref).toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get span id", e);
        }
    }

    public static String getTraceSegmentId(TraceSegmentRef ref) {
        try {
            Field field = TraceSegmentRef.class.getDeclaredField("traceSegmentId");
            return field.get(ref).toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get span id", e);
        }
    }
}
