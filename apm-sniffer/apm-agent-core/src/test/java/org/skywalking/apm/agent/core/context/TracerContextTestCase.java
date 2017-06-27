package org.skywalking.apm.agent.core.context;

import java.util.LinkedList;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.agent.core.context.ids.DistributedTraceId;
import org.skywalking.apm.agent.core.context.ids.PropagatedTraceId;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;

/**
 * Created by wusheng on 2017/2/19.
 */
public class TracerContextTestCase {
    @Test
    public void testSpanLifeCycle() {
        TracerContext context = new TracerContext();
        AbstractSpan span = context.createSpan("/serviceA", false);

        Assert.assertEquals(span, context.activeSpan());

        TracerContext.ListenerManager.add(TestTracingContextListener.INSTANCE);
        final TraceSegment[] finishedSegmentCarrier = TestTracingContextListener.INSTANCE.finishedSegmentCarrier;
        context.stopSpan(span);

        Assert.assertNotNull(finishedSegmentCarrier[0]);
        Assert.assertEquals(1, finishedSegmentCarrier[0].getSpans().size());
        Assert.assertEquals(span, finishedSegmentCarrier[0].getSpans().get(0));
    }

    @Test
    public void testChildOfSpan() {
        TracerContext context = new TracerContext();
        AbstractSpan serviceSpan = context.createSpan("/serviceA", false);
        AbstractSpan dbSpan = context.createSpan("db/preparedStatement/execute", false);

        Assert.assertEquals(dbSpan, context.activeSpan());

        TracerContext.ListenerManager.add(TestTracingContextListener.INSTANCE);
        final TraceSegment[] finishedSegmentCarrier = TestTracingContextListener.INSTANCE.finishedSegmentCarrier;

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
        AbstractSpan serviceSpan = context.createSpan("/serviceA", false);
        AbstractSpan dbSpan = context.createSpan("db/preparedStatement/execute", false);
        dbSpan.setPeerHost("127.0.0.1");
        dbSpan.setPort(8080);

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
        AbstractSpan span = context.createSpan("/serviceC", false);

        TracerContext.ListenerManager.add(TestTracingContextListener.INSTANCE);
        final TraceSegment[] finishedSegmentCarrier = TestTracingContextListener.INSTANCE.finishedSegmentCarrier;

        context.stopSpan(span);

        Assert.assertEquals("trace_id_1", finishedSegmentCarrier[0].getRefs().get(0).getTraceSegmentId());
        Assert.assertEquals(5, finishedSegmentCarrier[0].getRefs().get(0).getSpanId());
    }

    @After
    public void reset() {
        TracerContext.ListenerManager.remove(TestTracingContextListener.INSTANCE);
    }
}
