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

import java.net.InetSocketAddress;
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
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.plugin.elasticsearch.v6.TransportClientEnhanceInfo;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.transport.TransportAddress;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.TRANSPORT_CLIENT;
import static org.apache.skywalking.apm.plugin.elasticsearch.v6.ElasticsearchPluginConfig.Plugin.Elasticsearch.TRACE_DSL;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class TransportActionNodeProxyExecuteMethodsInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private EnhancedInstance enhancedInstance;

    @Mock
    private DiscoveryNode discoveryNode;

    @Mock
    private GetRequest getRequest;

    @Mock
    private IndexRequest indexRequest;

    @Mock
    private UpdateRequest updateRequest;

    @Mock
    private DeleteRequest deleteRequest;

    @Mock
    private DeleteIndexRequest deleteIndexRequest;

    @Mock
    private TransportClientEnhanceInfo enhanceInfo;

    private TransportActionNodeProxyExecuteMethodsInterceptor interceptor;

    @Before
    public void setUp() {

        InetSocketAddress inetSocketAddress = new InetSocketAddress("122.122.122.122", 9300);
        TransportAddress transportAddress = new TransportAddress(inetSocketAddress);
        when(discoveryNode.getAddress()).thenReturn(transportAddress);

        when(enhanceInfo.transportAddresses()).thenReturn("122.122.122.122:9300");
        when(enhanceInfo.getClusterName()).thenReturn("skywalking-es");
        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn(enhanceInfo);

        when(getRequest.index()).thenReturn("endpoint");
        when(getRequest.type()).thenReturn("getType");

        when(indexRequest.index()).thenReturn("endpoint");
        when(indexRequest.type()).thenReturn("indexType");

        when(updateRequest.index()).thenReturn("endpoint");
        when(updateRequest.type()).thenReturn("updateType");

        when(deleteRequest.index()).thenReturn("endpoint");
        when(deleteRequest.type()).thenReturn("deleteType");

        when(deleteIndexRequest.indices()).thenReturn(new String[] {"endpoint"});

        interceptor = new TransportActionNodeProxyExecuteMethodsInterceptor();
    }

    @Test
    public void testConstruct() {

        final EnhancedInstance objInst1 = new EnhancedInstance() {
            private Object object = null;

            @Override
            public Object getSkyWalkingDynamicField() {
                return object;
            }

            @Override
            public void setSkyWalkingDynamicField(Object value) {
                this.object = value;
            }
        };

        final EnhancedInstance objInst2 = new EnhancedInstance() {
            private Object object = null;

            @Override
            public Object getSkyWalkingDynamicField() {
                return object;
            }

            @Override
            public void setSkyWalkingDynamicField(Object value) {
                this.object = value;
            }
        };

        objInst1.setSkyWalkingDynamicField(123);
        Object[] allArguments = new Object[] {
            null,
            null,
            objInst1
        };

        interceptor.onConstruct(objInst2, allArguments);
        assertThat(objInst1.getSkyWalkingDynamicField(), is(objInst2.getSkyWalkingDynamicField()));
    }

    @Test
    public void testGetRequest() throws Throwable {

        AbstractTracingSpan getSpan = getSpan(getRequest);
        assertGetSpan(getSpan, getRequest);
    }

    @Test
    public void testIndexRequest() throws Throwable {

        AbstractTracingSpan getSpan = getSpan(indexRequest);
        assertGetSpan(getSpan, indexRequest);
    }

    @Test
    public void testUpdateRequest() throws Throwable {

        AbstractTracingSpan getSpan = getSpan(updateRequest);
        assertGetSpan(getSpan, updateRequest);
    }

    @Test
    public void testDeleteRequest() throws Throwable {

        AbstractTracingSpan getSpan = getSpan(deleteRequest);
        assertGetSpan(getSpan, deleteRequest);
    }

    @Test
    public void testDeleteIndexRequest() throws Throwable {

        AbstractTracingSpan getSpan = getSpan(deleteIndexRequest);
        assertGetSpan(getSpan, deleteIndexRequest);
    }

    private AbstractTracingSpan getSpan(ActionRequest actionRequest) throws Throwable {
        TRACE_DSL = true;
        Object[] allArguments = new Object[] {
            discoveryNode,
            actionRequest
        };

        interceptor.beforeMethod(enhancedInstance, null, allArguments, null, null);
        interceptor.afterMethod(enhancedInstance, null, allArguments, null, null);

        List<TraceSegment> traceSegmentList = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegmentList.size(), is(1));
        TraceSegment traceSegment = traceSegmentList.get(0);
        return SegmentHelper.getSpans(traceSegment).get(0);

    }

    private void assertGetSpan(AbstractTracingSpan getSpan, Object ret) {
        assertThat(getSpan instanceof ExitSpan, is(true));

        ExitSpan span = (ExitSpan) getSpan;
        assertThat(SpanHelper.getComponentId(span), is(TRANSPORT_CLIENT.getId()));

        List<TagValuePair> tags = SpanHelper.getTags(span);
        assertThat(tags.get(0).getValue(), is("Elasticsearch"));
        assertThat(tags.get(1).getValue(), is("skywalking-es"));
        assertThat(tags.get(2).getValue(), is("122.122.122.122:9300"));
        if (ret instanceof SearchRequest) {
            assertThat(span.getOperationName().split("[$$]")[0], is("Elasticsearch/SearchRequest"));
            assertThat(tags.get(3).getValue(), is("endpoint"));
            assertThat(tags.get(4).getValue(), is("searchType"));
        } else if (ret instanceof GetRequest) {
            assertThat(span.getOperationName().split("[$$]")[0], is("Elasticsearch/GetRequest"));
            assertThat(tags.get(3).getValue(), is("endpoint"));
            assertThat(tags.get(4).getValue(), is("getType"));
        } else if (ret instanceof IndexRequest) {
            assertThat(span.getOperationName().split("[$$]")[0], is("Elasticsearch/IndexRequest"));
            assertThat(tags.get(3).getValue(), is("endpoint"));
            assertThat(tags.get(4).getValue(), is("indexType"));
        } else if (ret instanceof UpdateRequest) {
            assertThat(span.getOperationName().split("[$$]")[0], is("Elasticsearch/UpdateRequest"));
            assertThat(tags.get(3).getValue(), is("endpoint"));
            assertThat(tags.get(4).getValue(), is("updateType"));
        } else if (ret instanceof DeleteRequest) {
            assertThat(span.getOperationName().split("[$$]")[0], is("Elasticsearch/DeleteRequest"));
            assertThat(tags.get(3).getValue(), is("endpoint"));
            assertThat(tags.get(4).getValue(), is("deleteType"));
        } else if (ret instanceof DeleteIndexRequest) {
            assertThat(span.getOperationName().split("[$$]")[0], is("Elasticsearch/DeleteIndexRequest"));
            assertThat(tags.get(3).getValue(), is("endpoint"));
        }

    }

}
