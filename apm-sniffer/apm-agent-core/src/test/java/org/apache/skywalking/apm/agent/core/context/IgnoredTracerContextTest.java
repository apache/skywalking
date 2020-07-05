/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.agent.core.context;

import java.util.LinkedList;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.NoopSpan;
import org.apache.skywalking.apm.agent.core.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.core.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.core.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.core.test.tools.TracingSegmentRunner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.TestCase.assertNull;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(TracingSegmentRunner.class)
public class IgnoredTracerContextTest {

    @SegmentStoragePoint
    private SegmentStorage storage;

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @BeforeClass
    public static void beforeClass() {
        Config.Agent.KEEP_TRACING = true;
    }

    @AfterClass
    public static void afterClass() {
        Config.Agent.KEEP_TRACING = false;
        ServiceManager.INSTANCE.shutdown();
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
        assertNull(contextCarrier.getParentEndpoint());
        assertThat(contextCarrier.getSpanId(), is(-1));
        assertNull(contextCarrier.getAddressUsedAtClient());

        LinkedList<IgnoredTracerContext> ignoredTracerContexts = storage.getIgnoredTracerContexts();
        assertThat(ignoredTracerContexts.size(), is(1));
    }

}
