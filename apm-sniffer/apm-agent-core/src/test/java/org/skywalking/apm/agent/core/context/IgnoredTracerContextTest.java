package org.skywalking.apm.agent.core.context;

import java.util.LinkedList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.NoopSpan;
import org.skywalking.apm.agent.core.test.tools.AgentServiceRule;
import org.skywalking.apm.agent.core.test.tools.SegmentStorage;
import org.skywalking.apm.agent.core.test.tools.SegmentStoragePoint;
import org.skywalking.apm.agent.core.test.tools.TracingSegmentRunner;

import static junit.framework.TestCase.assertNull;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(TracingSegmentRunner.class)
public class IgnoredTracerContextTest {

    @SegmentStoragePoint
    private SegmentStorage storage;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @Before
    public void setUp() throws Exception {
        RemoteDownstreamConfig.Agent.APPLICATION_ID = 1;
        RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID = 1;
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void ignoredTraceContextWithSampling() {
        Config.Agent.SAMPLE_N_PER_3_SECS = 1;
        ServiceManager.INSTANCE.boot();
        ContextManager.createLocalSpan("/test1");
        ContextManager.stopSpan();

        ContextManager.createLocalSpan("/test2");
        ContextManager.stopSpan();

        ContextManager.createLocalSpan("/test3");
        ContextManager.stopSpan();

        ContextManager.createLocalSpan("/test4");
        ContextManager.stopSpan();

        assertThat(storage.getIgnoredTracerContexts().size(), is(3));
        assertThat(storage.getTraceSegments().size(), is(1));

    }

    @Test
    public void ignoredTraceContextWithExcludeOperationName() {
        AbstractSpan abstractSpan = ContextManager.createEntrySpan("test.js", null);
        ContextManager.stopSpan();

        assertThat(abstractSpan.getClass().getName(), is(NoopSpan.class.getName()));
        LinkedList<IgnoredTracerContext> ignoredTracerContexts = storage.getIgnoredTracerContexts();
        assertThat(ignoredTracerContexts.size(), is(1));
    }

    @Test
    public void ignoredTraceContextWithEmptyOperationName() {
        ContextCarrier contextCarrier = new ContextCarrier();
        AbstractSpan abstractSpan = ContextManager.createExitSpan("", contextCarrier, "127.0.0.1:2181");
        ContextManager.stopSpan();

        assertThat(abstractSpan.getClass().getName(), is(NoopSpan.class.getName()));
        assertNull(contextCarrier.getEntryOperationName());
        assertThat(contextCarrier.getSpanId(), is(-1));
        assertNull(contextCarrier.getPeerHost());

        LinkedList<IgnoredTracerContext> ignoredTracerContexts = storage.getIgnoredTracerContexts();
        assertThat(ignoredTracerContexts.size(), is(1));
    }

}
