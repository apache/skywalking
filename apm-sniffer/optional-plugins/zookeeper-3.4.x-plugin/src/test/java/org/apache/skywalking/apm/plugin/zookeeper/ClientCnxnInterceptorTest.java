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

package org.apache.skywalking.apm.plugin.zookeeper;

import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.zookeeper.client.StaticHostProvider;
import org.apache.zookeeper.proto.CreateRequest;
import org.apache.zookeeper.proto.RequestHeader;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class ClientCnxnInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private MockInstance instance;

    private ClientCnxnInterceptor interceptor;

    private class MockInstance implements EnhancedInstance {
        private Object object;

        @Override
        public Object getSkyWalkingDynamicField() {
            return object;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.object = value;
        }
    }

    @SuppressWarnings({
        "rawtypes",
        "unchecked"
    })
    @Before
    public void setUp() throws Exception {
        instance = new MockInstance();
        interceptor = new ClientCnxnInterceptor();
    }

    @Test
    public void testInterceptor() throws Throwable {
        InetSocketAddress address = new InetSocketAddress("localhost", 2800);
        List<InetSocketAddress> serverAddresses = new ArrayList<InetSocketAddress>();
        serverAddresses.add(address);
        StaticHostProvider provider = new StaticHostProvider(serverAddresses);
        interceptor.onConstruct(instance, new Object[] {
            null,
            provider
        });
        RequestHeader header = new RequestHeader(1, 1);
        CreateRequest createRequest = new CreateRequest("/path", null, null, 0);
        interceptor.beforeMethod(instance, null, new Object[] {
            header,
            null,
            createRequest
        }, null, null);
        interceptor.afterMethod(instance, null, null, null, null);
        MatcherAssert.assertThat((String) instance.getSkyWalkingDynamicField(), Is.is("localhost:2800;"));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertThat(spans.get(0).getOperationName(), is("Zookeeper/create"));
        assertThat(spans.get(0).isExit(), is(true));
        assertThat(SpanHelper.getComponentId(spans.get(0)), is(58));
        List<TagValuePair> tags = SpanHelper.getTags(spans.get(0));
        assertThat(tags.get(0).getValue(), is("Zookeeper"));
        assertThat(SpanHelper.getLayer(spans.get(0)), CoreMatchers.is(SpanLayer.CACHE));
    }
}
