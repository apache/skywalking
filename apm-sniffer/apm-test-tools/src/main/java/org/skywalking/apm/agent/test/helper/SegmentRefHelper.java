package org.skywalking.apm.agent.test.helper;

import org.skywalking.apm.agent.core.context.trace.TraceSegmentRef;

public class SegmentRefHelper {
    public static String getPeerHost(TraceSegmentRef ref) {
        try {
            return FieldGetter.getValue(ref, "peerHost");
        } catch (Exception e) {
        }

        return null;
    }

    public static String getTraceSegmentId(TraceSegmentRef ref) {
        try {
            return FieldGetter.getValue(ref, "traceSegmentId");
        } catch (Exception e) {
        }

        return null;
    }

    public static int getSpanId(TraceSegmentRef ref) {
        try {
            return FieldGetter.getValue(ref, "spanId");
        } catch (Exception e) {
        }

        return -1;
    }
}
