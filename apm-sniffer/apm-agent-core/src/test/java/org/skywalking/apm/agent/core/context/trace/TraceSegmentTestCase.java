package org.skywalking.apm.agent.core.context.trace;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.skywalking.apm.agent.core.context.util.TraceSegmentHelper;

public class TraceSegmentTestCase {

    @Before
    public void setUp() {

    }

    @Test
    public void testRef() {

    }

    @Test
    public void testArchiveSpan() {
        TraceSegment segment = new TraceSegment();
        AbstractTracingSpan span1 = new LocalSpan(1, 0, "/serviceA");
        segment.archive(span1);

        AbstractTracingSpan span2 = new LocalSpan(2, 1, "/db/sql");
        segment.archive(span2);

        Assert.assertEquals(span1, TraceSegmentHelper.getSpans(segment).get(0));
        Assert.assertEquals(span2, TraceSegmentHelper.getSpans(segment).get(1));
    }
}
