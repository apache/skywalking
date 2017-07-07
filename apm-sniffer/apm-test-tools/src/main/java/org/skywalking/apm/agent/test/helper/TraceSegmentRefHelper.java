package org.skywalking.apm.agent.test.helper;

import org.skywalking.apm.agent.core.context.trace.TraceSegmentRef;

public class TraceSegmentRefHelper {
    public static String getPeerHost(TraceSegmentRef ref) {
        try {
            return FieldGetter.getValue(ref, "peerHost");
        } catch (Exception e) {
        }

        return null;
    }
}
