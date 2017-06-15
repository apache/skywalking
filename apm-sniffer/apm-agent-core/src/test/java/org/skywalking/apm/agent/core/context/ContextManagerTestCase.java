package org.skywalking.apm.agent.core.context;

import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.NoopSpan;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;

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
        AbstractSpan span = ContextManager.createSpan("serviceA");
        Tags.COMPONENT.set(span, "test");

        Assert.assertEquals(span, ContextManager.activeSpan());

        TracerContext.ListenerManager.add(TestTracerContextListener.INSTANCE);
        ContextManager.stopSpan();

        TraceSegment segment = TestTracerContextListener.INSTANCE.finishedSegmentCarrier[0];

        Assert.assertEquals(span, segment.getSpans().get(0));
    }

    @Test
    public void testSwitchToIgnoredTracerContext() throws NoSuchFieldException, IllegalAccessException {
        AbstractSpan span = ContextManager.createSpan("/webresource/jquery.js");
        Tags.COMPONENT.set(span, "test");

        Assert.assertTrue(span instanceof NoopSpan);
        Assert.assertTrue(ContextManager.activeSpan() instanceof NoopSpan);

        Field context = ContextManager.class.getDeclaredField("CONTEXT");
        context.setAccessible(true);
        AbstractTracerContext tracerContext = ((ThreadLocal<AbstractTracerContext>)context.get(null)).get();

        Assert.assertTrue(tracerContext instanceof IgnoredTracerContext);

        ContextManager.stopSpan();
        tracerContext = ((ThreadLocal<AbstractTracerContext>)context.get(null)).get();
        Assert.assertNull(tracerContext);

        // check normal trace again
        span = ContextManager.createSpan("serviceA");
        Tags.COMPONENT.set(span, "test");

        tracerContext = ((ThreadLocal<AbstractTracerContext>)context.get(null)).get();
        Assert.assertTrue(tracerContext instanceof TracerContext);
        ContextManager.stopSpan();
        tracerContext = ((ThreadLocal<AbstractTracerContext>)context.get(null)).get();
        Assert.assertNull(tracerContext);
    }

    @After
    public void reset() {
        TracerContext.ListenerManager.remove(TestTracerContextListener.INSTANCE);
    }
}
