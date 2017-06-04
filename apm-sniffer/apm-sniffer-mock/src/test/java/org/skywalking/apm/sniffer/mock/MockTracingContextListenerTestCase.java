package org.skywalking.apm.sniffer.mock;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.sniffer.mock.context.MockTracerContextListener;
import org.skywalking.apm.sniffer.mock.context.SegmentAssert;
import org.skywalking.apm.sniffer.mock.trace.TraceSegmentBuilderFactory;
import org.skywalking.apm.trace.TraceSegment;

/**
 * Created by wusheng on 2017/2/21.
 */
public class MockTracingContextListenerTestCase {
    @BeforeClass
    public static void setup() {
        ServiceManager.INSTANCE.boot();
    }

    @Test
    public void testAfterFinished() {
        MockTracerContextListener listener = new MockTracerContextListener();
        listener.afterFinished(TraceSegmentBuilderFactory.INSTANCE.singleTomcat200Trace());

        Assert.assertNotNull(listener.getFinished(0));
    }

    @Test(expected = AssertionError.class)
    public void testAssertSize() {
        MockTracerContextListener listener = new MockTracerContextListener();
        listener.afterFinished(TraceSegmentBuilderFactory.INSTANCE.singleTomcat404Trace());

        listener.assertSize(0);
    }

    @Test
    public void testAssertTraceSegment() {
        MockTracerContextListener listener = new MockTracerContextListener();
        listener.afterFinished(TraceSegmentBuilderFactory.INSTANCE.singleTomcat404Trace());
        listener.afterFinished(TraceSegmentBuilderFactory.INSTANCE.singleTomcat500Trace());

        listener.assertTraceSegment(0, new SegmentAssert() {
            @Override
            public void call(TraceSegment finishedSegment) {
                Assert.assertNotNull(finishedSegment);
            }
        });
    }

    @Test(expected = AssertionError.class)
    public void testClear() {
        MockTracerContextListener listener = new MockTracerContextListener();
        listener.afterFinished(TraceSegmentBuilderFactory.INSTANCE.singleTomcat404Trace());
        listener.afterFinished(TraceSegmentBuilderFactory.INSTANCE.singleTomcat500Trace());

        listener.clear();
        listener.assertValidIndex(0);
    }

    @Test
    public void testTraceOf_Tomcat_DubboClient() {
        TraceSegment segment = TraceSegmentBuilderFactory.INSTANCE.traceOf_Tomcat_DubboClient();

        Assert.assertEquals(2, segment.getSpans().size());
    }

    @Test
    public void testTraceOf_DubboServer_MySQL() {
        TraceSegment segment = TraceSegmentBuilderFactory.INSTANCE.traceOf_DubboServer_MySQL();

        Assert.assertEquals(2, segment.getSpans().size());
    }
}
