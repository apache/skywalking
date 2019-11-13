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
 */

package org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor;

import static org.apache.skywalking.apm.agent.core.conf.Config.Plugin.Elasticsearch.TRACE_DSL;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.List;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.ExitSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.plugin.elasticsearch.v6.RestClientEnhanceInfo;
import org.elasticsearch.action.update.UpdateRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

/**
 * @author aderm
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RestHighLevelClientUpdateMethodsInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private EnhancedInstance enhancedInstance;

    @Mock
    private UpdateRequest updateRequest;

    private Object[] allArguments;

    @Mock
    private RestClientEnhanceInfo restClientEnhanceInfo;

    @Mock
    private RestHighLevelClientUpdateMethodsInterceptor interceptor;

    @Before
    public void setUp() throws Exception {
        when(restClientEnhanceInfo.getPeers()).thenReturn("172.0.0.1:9200");
        allArguments = new Object[] {updateRequest};
        when(updateRequest.index()).thenReturn("indexName");
        when(updateRequest.toString()).thenReturn("updateRequest");
        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn(restClientEnhanceInfo);
        interceptor = new RestHighLevelClientUpdateMethodsInterceptor();
    }

    @Test
    public void testMethodsAround() throws Throwable {
        TRACE_DSL = true;
        interceptor.beforeMethod(enhancedInstance, null, allArguments, null, null);
        interceptor.afterMethod(enhancedInstance, null, allArguments, null, null);

        List<TraceSegment> traceSegmentList = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegmentList.size(), is(1));
        TraceSegment traceSegment = traceSegmentList.get(0);

        AbstractTracingSpan updateSpan = SegmentHelper.getSpans(traceSegment).get(0);
        assertUpdateSpan(updateSpan);
    }

    private void assertUpdateSpan(AbstractTracingSpan getSpan) {
        assertThat(getSpan instanceof ExitSpan, is(true));

        ExitSpan exitSpan = (ExitSpan)getSpan;
        assertThat(exitSpan.getOperationName(), is("Elasticsearch/UpdateRequest"));
        assertThat(exitSpan.getPeer(), is("172.0.0.1:9200"));
        assertThat(SpanHelper.getComponentId(exitSpan), is(77));

        List<TagValuePair> tags = SpanHelper.getTags(exitSpan);
        assertThat(tags.size(), is(3));
        assertThat(tags.get(0).getValue(), is("Elasticsearch"));
        assertThat(tags.get(1).getValue(), is("indexName"));
        assertThat(tags.get(2).getValue(), is("updateRequest"));
    }

    @Test
    public void testMethodsAroundError() throws Throwable {
        TRACE_DSL = true;
        interceptor.beforeMethod(enhancedInstance, null, allArguments, null, null);
        interceptor.handleMethodException(enhancedInstance, null, allArguments, null, new RuntimeException());
        interceptor.afterMethod(enhancedInstance, null, allArguments, null, null);

        List<TraceSegment> traceSegmentList = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegmentList.size(), is(1));
        TraceSegment traceSegment = traceSegmentList.get(0);

        AbstractTracingSpan updateSpan = SegmentHelper.getSpans(traceSegment).get(0);
        assertUpdateSpan(updateSpan);

        Assert.assertEquals(true, SpanHelper.getErrorOccurred(updateSpan));
        SpanAssert.assertException(SpanHelper.getLogs(updateSpan).get(0), RuntimeException.class);
    }
}
