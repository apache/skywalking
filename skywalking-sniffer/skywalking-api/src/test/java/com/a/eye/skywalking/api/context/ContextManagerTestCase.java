package com.a.eye.skywalking.api.context;

import com.a.eye.skywalking.trace.Span;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.tag.Tags;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by wusheng on 2017/2/19.
 */
public class ContextManagerTestCase {
    @Test
    public void testDelegateToTracerContext(){
        Span span = ContextManager.INSTANCE.createSpan("serviceA");
        Tags.COMPONENT.set(span, "test");

        Assert.assertEquals(span, ContextManager.INSTANCE.activeSpan());

        TracerContext.ListenerManager.add(TestTracerContextListener.INSTANCE);
        ContextManager.INSTANCE.stopSpan();

        TraceSegment segment = TestTracerContextListener.INSTANCE.finishedSegmentCarrier[0];

        Assert.assertEquals(span, segment.getSpans().get(0));
    }

    @After
    public void reset(){
        TracerContext.ListenerManager.remove(TestTracerContextListener.INSTANCE);
    }
}
