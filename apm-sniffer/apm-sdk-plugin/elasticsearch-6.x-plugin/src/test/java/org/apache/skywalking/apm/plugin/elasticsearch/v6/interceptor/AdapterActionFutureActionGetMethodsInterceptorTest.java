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

package org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor;

import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LocalSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHits;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.util.List;

import static org.apache.skywalking.apm.plugin.elasticsearch.v6.ElasticsearchPluginConfig.Plugin.Elasticsearch.TRACE_DSL;
import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.TRANSPORT_CLIENT;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class AdapterActionFutureActionGetMethodsInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private EnhancedInstance enhancedInstance;

    @Mock
    private SearchResponse searchResponse;

    @Mock
    private BulkResponse bulkItemResponses;

    private SearchHits searchHits;

    @Mock
    private AdapterActionFutureActionGetMethodsInterceptor interceptor;

    @Before
    public void setUp() {

        searchHits = new SearchHits(null, 309L, 0);

        when(searchResponse.getTook()).thenReturn(TimeValue.timeValueMillis(2020));
        when(searchResponse.getHits()).thenReturn(searchHits);

        when(bulkItemResponses.getTook()).thenReturn(TimeValue.timeValueMillis(2020));
        when(bulkItemResponses.getIngestTookInMillis()).thenReturn(1416L);

        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn(true);

        interceptor = new AdapterActionFutureActionGetMethodsInterceptor();
    }

    @Test
    public void testMethodsAround() throws Throwable {
        TRACE_DSL = true;
        interceptor.beforeMethod(enhancedInstance, null, null, null, null);
        interceptor.afterMethod(enhancedInstance, null, null, null, searchResponse);

        List<TraceSegment> traceSegmentList = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegmentList.size(), is(1));
        TraceSegment traceSegment = traceSegmentList.get(0);

        AbstractTracingSpan getSpan = SegmentHelper.getSpans(traceSegment).get(0);
        assertGetSpan(getSpan, searchResponse);
    }

    @Test
    public void testMethodsAround2() throws Throwable {
        TRACE_DSL = true;
        interceptor.beforeMethod(enhancedInstance, null, null, null, null);
        interceptor.afterMethod(enhancedInstance, null, null, null, bulkItemResponses);

        List<TraceSegment> traceSegmentList = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegmentList.size(), is(1));
        TraceSegment traceSegment = traceSegmentList.get(0);

        AbstractTracingSpan getSpan = SegmentHelper.getSpans(traceSegment).get(0);
        assertGetSpan(getSpan, bulkItemResponses);
    }

    private void assertGetSpan(AbstractTracingSpan getSpan, Object ret) {
        assertThat(getSpan instanceof LocalSpan, is(true));

        LocalSpan span = (LocalSpan) getSpan;
        assertThat(span.getOperationName(), is("Elasticsearch/actionGet"));
        assertThat(SpanHelper.getComponentId(span), is(TRANSPORT_CLIENT.getId()));

        List<TagValuePair> tags = SpanHelper.getTags(span);
        assertThat(tags.size(), is(4));
        if (ret instanceof SearchResponse) {
            assertThat(tags.get(0).getValue(), is("Elasticsearch"));
            assertThat(tags.get(1).getValue(), is("2020"));
            assertThat(tags.get(2).getValue(), is("309"));
        } else if (ret instanceof BulkResponse) {
            assertThat(tags.get(0).getValue(), is("Elasticsearch"));
            assertThat(tags.get(1).getValue(), is("2020"));
            assertThat(tags.get(2).getValue(), is("1416"));
        }

    }

}
