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
import org.elasticsearch.action.get.GetRequest;
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

import java.net.InetSocketAddress;
import java.util.List;

import static org.apache.skywalking.apm.agent.core.conf.Config.Plugin.Elasticsearch.TRACE_DSL;
import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.TRANSPORT_CLIENT;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * date 2020.03.15 21:02
 */
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
    private TransportClientEnhanceInfo enhanceInfo;

    private TransportActionNodeProxyExecuteMethodsInterceptor interceptor;

    @Before
    public void setUp() {

        InetSocketAddress inetSocketAddress = new InetSocketAddress("122.122.122.122", 9300);
        TransportAddress transportAddress = new TransportAddress(inetSocketAddress);
        when(discoveryNode.getAddress()).thenReturn(transportAddress);

        when(enhanceInfo.transportAddresses()).thenReturn("122.122.122.122:9300;");
        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn(enhanceInfo);

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
        Object[] allArguments = new Object[]{null, null, objInst1};

        interceptor.onConstruct(objInst2, allArguments);
        assertThat(objInst1.getSkyWalkingDynamicField(), is(objInst2.getSkyWalkingDynamicField()));
    }

    @Test
    public void testMethodsAround() throws Throwable {
        TRACE_DSL = true;
        Object[] allArguments = new Object[]{discoveryNode, getRequest};

        interceptor.beforeMethod(enhancedInstance, null, allArguments, null, null);
        interceptor.afterMethod(enhancedInstance, null, allArguments, null, null);

        List<TraceSegment> traceSegmentList = segmentStorage.getTraceSegments();
        Assert.assertThat(traceSegmentList.size(), is(1));
        TraceSegment traceSegment = traceSegmentList.get(0);

        AbstractTracingSpan getSpan = SegmentHelper.getSpans(traceSegment).get(0);
        assertGetSpan(getSpan, getRequest);
    }

    private void assertGetSpan(AbstractTracingSpan getSpan, Object ret) {
        assertThat(getSpan instanceof ExitSpan, is(true));

        ExitSpan span = (ExitSpan) getSpan;
        assertThat(span.getOperationName().split("[$$]")[0], is("Elasticsearch/GetRequest"));
        assertThat(SpanHelper.getComponentId(span), is(TRANSPORT_CLIENT.getId()));

        List<TagValuePair> tags = SpanHelper.getTags(span);
        Assert.assertTrue(tags.size() > 4);

    }

}
