package org.skywalking.apm.api.context;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.TraceId.DistributedTraceId;
import org.skywalking.apm.trace.TraceId.PropagatedTraceId;
import org.skywalking.apm.trace.TraceSegment;
import org.skywalking.apm.trace.tag.Tags;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by wusheng on 2017/2/19.
 */
public class TracerContextTestCase {
    @Test
    public void testSpanLifeCycle() {
        TracerContext context = new TracerContext();
        Span span = context.createSpan("/serviceA");

        Assert.assertEquals(span, context.activeSpan());

        TracerContext.ListenerManager.add(TestTracerContextListener.INSTANCE);
        final TraceSegment[] finishedSegmentCarrier = TestTracerContextListener.INSTANCE.finishedSegmentCarrier;
        context.stopSpan(span);

        Assert.assertNotNull(finishedSegmentCarrier[0]);
        Assert.assertEquals(1, finishedSegmentCarrier[0].getSpans().size());
        Assert.assertEquals(span, finishedSegmentCarrier[0].getSpans().get(0));
    }

    @Test
    public void testChildOfSpan() {
        TracerContext context = new TracerContext();
        Span serviceSpan = context.createSpan("/serviceA");
        Span dbSpan = context.createSpan("db/preparedStatement/execute");

        Assert.assertEquals(dbSpan, context.activeSpan());

        TracerContext.ListenerManager.add(TestTracerContextListener.INSTANCE);
        final TraceSegment[] finishedSegmentCarrier = TestTracerContextListener.INSTANCE.finishedSegmentCarrier;

        try {
            context.stopSpan(serviceSpan);
        } catch (Throwable t) {
            Assert.assertTrue(t instanceof IllegalStateException);
        }

        context.stopSpan(dbSpan);
        context.stopSpan(serviceSpan);

        Assert.assertNotNull(finishedSegmentCarrier[0]);
        Assert.assertEquals(2, finishedSegmentCarrier[0].getSpans().size());
        Assert.assertEquals(dbSpan, finishedSegmentCarrier[0].getSpans().get(0));
    }

    @Test
    public void testInject() {
        TracerContext context = new TracerContext();
        Span serviceSpan = context.createSpan("/serviceA");
        Span dbSpan = context.createSpan("db/preparedStatement/execute");
        Tags.PEER_HOST.set(dbSpan, "127.0.0.1");
        Tags.PEER_PORT.set(dbSpan, 8080);

        ContextCarrier carrier = new ContextCarrier();
        context.inject(carrier);

        Assert.assertEquals("127.0.0.1:8080", carrier.getPeerHost());
        Assert.assertEquals(1, carrier.getSpanId());
    }

    @Test
    public void testExtract() {
        ContextCarrier carrier = new ContextCarrier();
        carrier.setTraceSegmentId("trace_id_1");
        carrier.setSpanId(5);
        carrier.setApplicationCode("REMOTE_APP");
        carrier.setPeerHost("10.2.3.16:8080");
        List<DistributedTraceId> ids = new LinkedList<DistributedTraceId>();
        ids.add(new PropagatedTraceId("Trace.global.id.123"));
        carrier.setDistributedTraceIds(ids);

        Assert.assertTrue(carrier.isValid());

        TracerContext context = new TracerContext();
        context.extract(carrier);
        Span span = context.createSpan("/serviceC");

        TracerContext.ListenerManager.add(TestTracerContextListener.INSTANCE);
        final TraceSegment[] finishedSegmentCarrier = TestTracerContextListener.INSTANCE.finishedSegmentCarrier;

        context.stopSpan(span);

        Assert.assertEquals("trace_id_1", finishedSegmentCarrier[0].getRefs().get(0).getTraceSegmentId());
        Assert.assertEquals(5, finishedSegmentCarrier[0].getRefs().get(0).getSpanId());
    }

    @After
    public void reset() {
        TracerContext.ListenerManager.remove(TestTracerContextListener.INSTANCE);
    }
}
