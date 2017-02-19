package com.a.eye.skywalking.context;

import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/19.
 */
public class TracerContextTest {
    @Test
    public void testSpanLifeCycle(){
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
    public void testChildOfSpan(){
        TracerContext context = new TracerContext();
        Span serviceSpan = context.createSpan("/serviceA");
        Span dbSpan = context.createSpan("db/preparedStatement/execute");

        Assert.assertEquals(dbSpan, context.activeSpan());

        TracerContext.ListenerManager.add(TestTracerContextListener.INSTANCE);
        final TraceSegment[] finishedSegmentCarrier = TestTracerContextListener.INSTANCE.finishedSegmentCarrier;

        try {
            context.stopSpan(serviceSpan);
        }catch (Throwable t){
            Assert.assertTrue(t instanceof IllegalStateException);
        }

        context.stopSpan(dbSpan);
        context.stopSpan(serviceSpan);

        Assert.assertNotNull(finishedSegmentCarrier[0]);
        Assert.assertEquals(2, finishedSegmentCarrier[0].getSpans().size());
        Assert.assertEquals(dbSpan, finishedSegmentCarrier[0].getSpans().get(0));
    }

    @Test
    public void testInject(){
        TracerContext context = new TracerContext();
        Span serviceSpan = context.createSpan("/serviceA");
        Span dbSpan = context.createSpan("db/preparedStatement/execute");

        ContextCarrier carrier = new ContextCarrier();
        context.inject(carrier);

        Assert.assertTrue(carrier.isValid());
        Assert.assertEquals(1, carrier.getSpanId());
    }

    @Test
    public void testExtract(){
        ContextCarrier carrier = new ContextCarrier();
        carrier.setTraceSegmentId("trace_id_1");
        carrier.setSpanId(5);

        Assert.assertTrue(carrier.isValid());

        TracerContext context = new TracerContext();
        context.extract(carrier);
        Span span = context.createSpan("/serviceC");

        TracerContext.ListenerManager.add(TestTracerContextListener.INSTANCE);
        final TraceSegment[] finishedSegmentCarrier = TestTracerContextListener.INSTANCE.finishedSegmentCarrier;

        context.stopSpan(span);

        Assert.assertEquals("trace_id_1", finishedSegmentCarrier[0].getPrimaryRef().getTraceSegmentId());
        Assert.assertEquals(5, finishedSegmentCarrier[0].getPrimaryRef().getSpanId());
    }

    @After
    public void reset(){
        TracerContext.ListenerManager.remove(TestTracerContextListener.INSTANCE);
    }

    public enum TestTracerContextListener implements TracerContextListener {
        INSTANCE;
        final TraceSegment[] finishedSegmentCarrier = {null};

        @Override public void afterFinished(TraceSegment traceSegment) {
            finishedSegmentCarrier[0] = traceSegment;
        }
    }
}
