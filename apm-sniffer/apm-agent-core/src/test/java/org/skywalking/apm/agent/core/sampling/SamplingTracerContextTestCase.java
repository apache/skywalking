package org.skywalking.apm.agent.core.sampling;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.TracingContextListener;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;

/**
 * @author wusheng
 */
public class SamplingTracerContextTestCase {
    private int finishedTracerCounter = 0;

    private TracingContextListener listener = new TracingContextListener() {
        @Override
        public void afterFinished(TraceSegment traceSegment) {
            if (!traceSegment.isIgnore()) {
                finishedTracerCounter++;
            }
        }
    };

    @Before
    public void setUp() throws Exception {
        Config.Agent.SAMPLE_N_PER_10_SECS = 5;
        ServiceManager.INSTANCE.boot();
        TracerContext.ListenerManager.add(listener);
    }

    @Test
    public void testSample5InALoop() throws InterruptedException {
        for (int i = 0; i < 11; i++) {
            AbstractSpan span = ContextManager.createSpan("serviceA");
            Tags.COMPONENT.set(span, "test");
            ContextManager.stopSpan();
        }

        /**
         * Considering the reset cycle, in ci-env, may sample 5-7 trace through 1 or 2 cycle.
         */
        Assert.assertTrue(finishedTracerCounter >= 5);
        Assert.assertTrue(finishedTracerCounter <= 7);
        Thread.sleep(10 * 1000L);
    }

    @Test
    public void testSample5InLoopWithMultiSpans() {
        finishedTracerCounter = 0;
        for (int i = 0; i < 11; i++) {
            AbstractSpan span = ContextManager.createSpan("serviceA");
            Tags.COMPONENT.set(span, "test");
            AbstractSpan span2 = ContextManager.createSpan("serviceB");
            Tags.COMPONENT.set(span2, "test2");
            ContextManager.stopSpan();
            ContextManager.stopSpan();
        }

        /**
         * Considering the reset cycle, in ci-env, may sample 5-7 trace through 1 or 2 cycle.
         */
        Assert.assertTrue(finishedTracerCounter >= 5);
        Assert.assertTrue(finishedTracerCounter <= 7);
    }

    @After
    public void tearDown() throws Exception {
        Config.Agent.SAMPLE_N_PER_10_SECS = -1;
        TracerContext.ListenerManager.remove(listener);
    }
}
