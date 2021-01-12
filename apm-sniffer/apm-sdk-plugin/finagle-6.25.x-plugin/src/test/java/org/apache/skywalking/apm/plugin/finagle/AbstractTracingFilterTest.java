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

package org.apache.skywalking.apm.plugin.finagle;

import com.twitter.finagle.Service;
import com.twitter.util.Future;
import com.twitter.util.Promise;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mock;

import java.util.List;

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.FINAGLE;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public abstract class AbstractTracingFilterTest {

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    @Mock
    protected MethodInterceptResult methodInterceptResult;

    protected EnhancedInstance enhancedInstance = new MockEnhancedInstance();

    protected AnnotationInterceptor.Rpc rpcInterceptor = new AnnotationInterceptor.Rpc();

    protected String rpc = "finagleRpc";

    protected Object[] allArguments;
    protected Class[] argumentTypes;

    protected Promise result;

    @Before
    public void setup() throws Throwable {
        result = new Promise();
        allArguments = new Object[]{new Object(), new Service() {
            @Override
            public Future apply(Object request) {
                return result;
            }
        } };
        argumentTypes = new Class[]{Object.class, Service.class};
        prepareForTest();
    }

    protected abstract void prepareForTest() throws Throwable;

    protected abstract void assertSpan(AbstractTracingSpan span);

    protected void assertTraceSegments(List<TraceSegment> traceSegments) {
        assertThat(traceSegments.size(), is(1));
        TraceSegment traceSegment = traceSegments.get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertFinagleSpan(spans.get(0));
    }

    protected void assertTraceSegmentsWithError(List<TraceSegment> traceSegments) {
        assertThat(traceSegments.size(), is(1));
        TraceSegment traceSegment = traceSegments.get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertFinagleSpan(spans.get(0));

        assertThat(SpanHelper.getLogs(spans.get(0)).size(), is(1));
        assertErrorLog(SpanHelper.getLogs(spans.get(0)).get(0));
    }

    private void assertFinagleSpan(AbstractTracingSpan span) {
        List<TagValuePair> tags = SpanHelper.getTags(span);
        assertThat(tags.size(), is(0));
        assertThat(SpanHelper.getLayer(span), CoreMatchers.is(SpanLayer.RPC_FRAMEWORK));
        assertThat(SpanHelper.getComponentId(span), is(FINAGLE.getId()));
        assertThat(span.getOperationName(), is(rpc));

        assertSpan(span);
    }

    private void assertErrorLog(LogDataEntity logData) {
        assertThat(logData.getLogs().size(), is(4));
        assertThat(logData.getLogs().get(0).getValue(), CoreMatchers.<Object>is("error"));
        assertThat(logData.getLogs().get(1).getValue(), CoreMatchers.<Object>is(RuntimeException.class.getName()));
        assertNull(logData.getLogs().get(2).getValue());
    }
}
