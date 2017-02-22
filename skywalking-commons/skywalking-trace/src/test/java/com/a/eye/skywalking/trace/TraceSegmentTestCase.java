package com.a.eye.skywalking.trace;

import com.a.eye.skywalking.trace.tag.Tags;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/18.
 */
public class TraceSegmentTestCase {
    @Test
    public void testConstructor() {
        TraceSegment segment = new TraceSegment("trace_1");

        Assert.assertEquals("trace_1", segment.getTraceSegmentId());
        Assert.assertTrue(segment.getStartTime() > 0);
    }

    @Test
    public void testRef() {
        TraceSegment segment = new TraceSegment("trace_3");

        TraceSegmentRef ref1 = new TraceSegmentRef();
        ref1.setTraceSegmentId("parent_trace_0");
        ref1.setSpanId(1);
        segment.ref(ref1);

        TraceSegmentRef ref2 = new TraceSegmentRef();
        ref2.setTraceSegmentId("parent_trace_1");
        ref2.setSpanId(5);
        segment.ref(ref2);

        TraceSegmentRef ref3 = new TraceSegmentRef();
        ref3.setTraceSegmentId("parent_trace_1");
        ref3.setSpanId(5);
        segment.ref(ref3);

        Assert.assertEquals(ref1, segment.getPrimaryRef());
        Assert.assertEquals(ref2, segment.getRefs().get(0));
        Assert.assertEquals(ref3, segment.getRefs().get(1));

        Assert.assertEquals("parent_trace_0", segment.getPrimaryRef().getTraceSegmentId());
        Assert.assertEquals(1, segment.getPrimaryRef().getSpanId());
    }

    @Test
    public void testArchiveSpan() {
        TraceSegment segment = new TraceSegment("trace_1");
        Span span1 = new Span(1, "/serviceA");
        segment.archive(span1);

        Span span2 = new Span(2, "/db/sql");
        segment.archive(span2);

        Assert.assertEquals(span1, segment.getSpans().get(0));
        Assert.assertEquals(span2, segment.getSpans().get(1));
    }

    @Test
    public void testFinish() {
        TraceSegment segment = new TraceSegment("trace_1");

        Assert.assertTrue(segment.getEndTime() == 0);
        segment.finish();
        Assert.assertTrue(segment.getEndTime() > 0);
    }

    @Test
    public void testSerialize() {
        TraceSegment segment = new TraceSegment("trace_3");

        TraceSegmentRef ref1 = new TraceSegmentRef();
        ref1.setTraceSegmentId("parent_trace_0");
        ref1.setSpanId(1);
        segment.ref(ref1);

        TraceSegmentRef ref2 = new TraceSegmentRef();
        ref2.setTraceSegmentId("parent_trace_1");
        ref2.setSpanId(5);
        segment.ref(ref2);

        TraceSegmentRef ref3 = new TraceSegmentRef();
        ref3.setTraceSegmentId("parent_trace_1");
        ref3.setSpanId(5);
        segment.ref(ref3);

        Span span1 = new Span(1, "/serviceA");
        Tags.SPAN_LAYER.asHttp(span1);
        segment.archive(span1);

        Span span2 = new Span(2, span1, "/db/sql");
        Tags.SPAN_LAYER.asNoSQL(span2);
        span2.log(new NullPointerException());
        segment.archive(span2);

        TraceSegment newSegment = new TraceSegment(segment.serialize());

        Assert.assertEquals(segment.getSpans().size(), newSegment.getSpans().size());
        Assert.assertEquals(segment.getPrimaryRef().getTraceSegmentId(), newSegment.getPrimaryRef().getTraceSegmentId());
        Assert.assertEquals(Tags.SPAN_LAYER.get(segment.getSpans().get(0)), Tags.SPAN_LAYER.get(newSegment.getSpans().get(0)));
        Assert.assertEquals(segment.getSpans().get(1).getLogs().get(0).getTime(), newSegment.getSpans().get(1).getLogs().get(0).getTime());
    }
}
