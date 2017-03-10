package com.a.eye.skywalking.sniffer.mock;

import com.a.eye.skywalking.sniffer.mock.context.MockTracerContextListener;
import com.a.eye.skywalking.sniffer.mock.context.SegmentAssert;
import com.a.eye.skywalking.sniffer.mock.trace.TraceSegmentBuilderFactory;
import com.a.eye.skywalking.trace.TraceSegment;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/21.
 */
public class MockTracerContextListenerTestCase {
    @Test
    public void testAfterFinished(){
        MockTracerContextListener listener = new MockTracerContextListener();
        listener.afterFinished(TraceSegmentBuilderFactory.INSTANCE.singleTomcat200Trace());

        Assert.assertNotNull(listener.getFinished(0));
    }

    @Test(expected = AssertionError.class)
    public void testAssertSize(){
        MockTracerContextListener listener = new MockTracerContextListener();
        listener.afterFinished(TraceSegmentBuilderFactory.INSTANCE.singleTomcat404Trace());

        listener.assertSize(0);
    }

    @Test
    public void testAssertTraceSegment(){
        MockTracerContextListener listener = new MockTracerContextListener();
        listener.afterFinished(TraceSegmentBuilderFactory.INSTANCE.singleTomcat404Trace());
        listener.afterFinished(TraceSegmentBuilderFactory.INSTANCE.singleTomcat500Trace());

        listener.assertTraceSegment(0, new SegmentAssert() {
            @Override public void call(TraceSegment finishedSegment) {
                Assert.assertNotNull(finishedSegment);
            }
        });
    }

    @Test(expected = AssertionError.class)
    public void testClear(){
        MockTracerContextListener listener = new MockTracerContextListener();
        listener.afterFinished(TraceSegmentBuilderFactory.INSTANCE.singleTomcat404Trace());
        listener.afterFinished(TraceSegmentBuilderFactory.INSTANCE.singleTomcat500Trace());

        listener.clear();
        listener.assertValidIndex(0);
    }

    @Test
    public void testTraceOf_Tomcat_DubboClient(){
        TraceSegment segment = TraceSegmentBuilderFactory.INSTANCE.traceOf_Tomcat_DubboClient();

        Assert.assertEquals(2, segment.getSpans().size());
    }

    @Test
    public void testTraceOf_DubboServer_MySQL(){
        TraceSegment segment = TraceSegmentBuilderFactory.INSTANCE.traceOf_DubboServer_MySQL();

        Assert.assertEquals(2, segment.getSpans().size());
    }
}
