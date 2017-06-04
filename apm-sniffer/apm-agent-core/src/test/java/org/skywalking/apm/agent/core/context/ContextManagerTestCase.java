package org.skywalking.apm.agent.core.context;

import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.trace.Span;
import org.skywalking.apm.trace.TraceSegment;
import org.skywalking.apm.trace.tag.Tags;

/**
 * Created by wusheng on 2017/2/19.
 */
public class ContextManagerTestCase {
    @BeforeClass
    public static void setup() {
        ServiceManager.INSTANCE.boot();
    }

    @Test
    public void testDelegateToTracerContext() {
        Span span = ContextManager.createSpan("serviceA");
        Tags.COMPONENT.set(span, "test");

        Assert.assertEquals(span, ContextManager.activeSpan());

        TracerContext.ListenerManager.add(TestTracerContextListener.INSTANCE);
        ContextManager.stopSpan();

        TraceSegment segment = TestTracerContextListener.INSTANCE.finishedSegmentCarrier[0];

        Assert.assertEquals(span, segment.getSpans().get(0));
    }

    @After
    public void reset() {
        TracerContext.ListenerManager.remove(TestTracerContextListener.INSTANCE);
    }
}
