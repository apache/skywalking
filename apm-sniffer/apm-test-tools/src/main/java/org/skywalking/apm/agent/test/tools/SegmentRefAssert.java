package org.skywalking.apm.agent.test.tools;

import org.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.skywalking.apm.agent.test.helper.SegmentRefHelper;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SegmentRefAssert {
    public static void assertSegmentId(TraceSegmentRef ref, String segmentId) {
        assertThat(SegmentRefHelper.getTraceSegmentId(ref).toString(), is(segmentId));
    }

    public static void assertSpanId(TraceSegmentRef ref, int spanId) {
        assertThat(SegmentRefHelper.getSpanId(ref), is(spanId));
    }

    public static void assertPeerHost(TraceSegmentRef ref, String peerHost) {
        assertThat(SegmentRefHelper.getPeerHost(ref), is(peerHost));
    }
}
