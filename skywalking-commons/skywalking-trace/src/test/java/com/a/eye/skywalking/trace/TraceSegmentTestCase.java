package com.a.eye.skywalking.trace;

import com.a.eye.skywalking.trace.tag.Tags;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/18.
 */
public class TraceSegmentTestCase {
    @Test
    public void testConstructor() {
        TraceSegment segment = new TraceSegment( "billing_app");

        Assert.assertTrue(segment.getTraceSegmentId().startsWith("Segment"));
        Assert.assertTrue(segment.getStartTime() > 0);
        Assert.assertEquals("billing_app", segment.getApplicationCode());
    }

    @Test
    public void testRef() {
        TraceSegment segment = new TraceSegment("billing_app");

        TraceSegmentRef ref1 = new TraceSegmentRef();
        ref1.setTraceSegmentId("parent_trace_0");
        ref1.setSpanId(1);
        segment.ref(ref1);

        TraceSegmentRef ref2 = new TraceSegmentRef();
        ref2.setTraceSegmentId("parent_trace_1");
        ref2.setSpanId(5);
        segment.ref(ref2);

        TraceSegmentRef ref3 = new TraceSegmentRef();
        ref3.setTraceSegmentId("parent_trace_3");
        ref3.setSpanId(5);
        segment.ref(ref3);

        Assert.assertEquals(ref1, segment.getRefs().get(0));
        Assert.assertEquals(ref2, segment.getRefs().get(1));
        Assert.assertEquals(ref3, segment.getRefs().get(2));

        Assert.assertEquals("parent_trace_0", segment.getRefs().get(0).getTraceSegmentId());
        Assert.assertEquals(1, segment.getRefs().get(0).getSpanId());
    }

    @Test
    public void testArchiveSpan() {
        TraceSegment segment = new TraceSegment("billing_app");
        Span span1 = new Span(1, "/serviceA");
        segment.archive(span1);

        Span span2 = new Span(2, "/db/sql");
        segment.archive(span2);

        Assert.assertEquals(span1, segment.getSpans().get(0));
        Assert.assertEquals(span2, segment.getSpans().get(1));
    }

    @Test
    public void testFinish() {
        TraceSegment segment = new TraceSegment("billing_app");

        Assert.assertTrue(segment.getEndTime() == 0);
        segment.finish();
        Assert.assertTrue(segment.getEndTime() > 0);
    }

    @Test
    public void testSerialize() {
        TraceSegment segment = new TraceSegment("billing_app");

        TraceSegmentRef ref1 = new TraceSegmentRef();
        ref1.setTraceSegmentId("parent_trace_0");
        ref1.setSpanId(1);
        ref1.setApplicationCode("REMOTE_APP");
        ref1.setPeerHost("10.2.3.16:8080");
        segment.ref(ref1);

        TraceSegmentRef ref2 = new TraceSegmentRef();
        ref2.setTraceSegmentId("parent_trace_1");
        ref2.setSpanId(5);
        ref2.setApplicationCode("REMOTE_APP");
        ref2.setPeerHost("10.2.3.16:8080");
        segment.ref(ref2);

        TraceSegmentRef ref3 = new TraceSegmentRef();
        ref3.setTraceSegmentId("parent_trace_2");
        ref3.setSpanId(5);
        ref3.setApplicationCode("REMOTE_APP");
        ref3.setPeerHost("10.2.3.16:8080");
        segment.ref(ref3);

        Span span1 = new Span(1, "/serviceA");
        Tags.SPAN_LAYER.asHttp(span1);
        segment.archive(span1);

        Span span2 = new Span(2, span1, "/db/sql");
        Tags.SPAN_LAYER.asDB(span2);
        span2.log(new NullPointerException());
        segment.archive(span2);

        Gson gson = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

        String json = gson.toJson(segment);
        System.out.println(json);


        TraceSegment newSegment = gson.fromJson(json, TraceSegment.class);

        Assert.assertEquals(segment.getSpans().size(), newSegment.getSpans().size());
        Assert.assertEquals(segment.getRefs().get(0).getTraceSegmentId(), newSegment.getRefs().get(0).getTraceSegmentId());
        Assert.assertEquals(Tags.SPAN_LAYER.get(segment.getSpans().get(1)), Tags.SPAN_LAYER.get(newSegment.getSpans().get(1)));
        Assert.assertEquals(segment.getSpans().get(1).getLogs().get(0).getTime(), newSegment.getSpans().get(1).getLogs().get(0).getTime());
    }
}
