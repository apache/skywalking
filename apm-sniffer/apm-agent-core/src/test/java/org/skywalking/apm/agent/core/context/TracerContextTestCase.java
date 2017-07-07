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
import org.skywalking.apm.agent.core.context.util.TraceSegmentHelper;
import org.skywalking.apm.agent.core.context.util.TraceSegmentRefHelper;

/**
 * Created by wusheng on 2017/2/19.
 */
public class TracerContextTestCase {
    @Test
    public void testSpanLifeCycle() {
        TracingContext context = new TracingContext();
        AbstractSpan span = context.createLocalSpan("/serviceA");

        Assert.assertEquals(span, context.activeSpan());

        TracingContext.ListenerManager.add(TestTracingContextListener.INSTANCE);
        final TraceSegment[] finishedSegmentCarrier = TestTracingContextListener.INSTANCE.finishedSegmentCarrier;
        context.stopSpan(span);

        TraceSegment traceSegment = finishedSegmentCarrier[0];
        Assert.assertNotNull(traceSegment);
        Assert.assertEquals(1, TraceSegmentHelper.getSpans(traceSegment).size());
        Assert.assertEquals(span, TraceSegmentHelper.getSpans(traceSegment).get(0));
    }

    @Test
    public void testChildOfSpan() {
        TracingContext context = new TracingContext();
        AbstractSpan serviceSpan = context.createLocalSpan("/serviceA");
        AbstractSpan dbSpan = context.createExitSpan("db/preparedStatement/execute", "127.0.0.1:3306");

        Assert.assertEquals(dbSpan, context.activeSpan());

        TracingContext.ListenerManager.add(TestTracingContextListener.INSTANCE);
        final TraceSegment[] finishedSegmentCarrier = TestTracingContextListener.INSTANCE.finishedSegmentCarrier;

        try {
            context.stopSpan(serviceSpan);
        } catch (Throwable t) {
            Assert.assertTrue(t instanceof IllegalStateException);
        }

        context.stopSpan(dbSpan);
        context.stopSpan(serviceSpan);

        TraceSegment traceSegment = finishedSegmentCarrier[0];
        Assert.assertNotNull(traceSegment);
        Assert.assertEquals(2, TraceSegmentHelper.getSpans(traceSegment).size());
        Assert.assertEquals(dbSpan, TraceSegmentHelper.getSpans(traceSegment).get(0));
    }

    @Test
    public void testInject() {
        TracingContext context = new TracingContext();
        AbstractSpan serviceSpan = context.createLocalSpan("/serviceA");
        AbstractSpan dbSpan = context.createExitSpan("db/preparedStatement/execute", "127.0.0.1:3306");

        ContextCarrier carrier = new ContextCarrier();
        context.inject(carrier);

        Assert.assertEquals("127.0.0.1:3306", carrier.getPeerHost());
        Assert.assertEquals(1, carrier.getSpanId());
    }

    @Test
    public void testExtract() {
        ContextCarrier carrier = new ContextCarrier();
        carrier.setTraceSegmentId("trace_id_1");
        carrier.setSpanId(5);
        carrier.setPeerHost("10.2.3.16:8080");
        List<DistributedTraceId> ids = new LinkedList<DistributedTraceId>();
        ids.add(new PropagatedTraceId("Trace.global.id.123"));
        carrier.setDistributedTraceIds(ids);

        Assert.assertTrue(carrier.isValid());

        TracingContext context = new TracingContext();
        context.extract(carrier);
        AbstractSpan span = context.createLocalSpan("/serviceC");

        TracingContext.ListenerManager.add(TestTracingContextListener.INSTANCE);
        final TraceSegment[] finishedSegmentCarrier = TestTracingContextListener.INSTANCE.finishedSegmentCarrier;

        context.stopSpan(span);

        TraceSegment segment = finishedSegmentCarrier[0];
        Assert.assertEquals("trace_id_1", TraceSegmentRefHelper.getTraceSegmentId(segment.getRefs().get(0)));
        Assert.assertEquals(5, TraceSegmentRefHelper.getSpanId(segment.getRefs().get(0)));
    }

    @After
    public void reset() {
        TracingContext.ListenerManager.remove(TestTracingContextListener.INSTANCE);
    }
}
