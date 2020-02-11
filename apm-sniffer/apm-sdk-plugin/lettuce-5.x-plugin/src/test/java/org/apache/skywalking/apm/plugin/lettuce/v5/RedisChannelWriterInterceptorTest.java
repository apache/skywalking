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

package org.apache.skywalking.apm.plugin.lettuce.v5;

import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.RedisCommand;
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
import org.apache.skywalking.apm.plugin.lettuce.v5.mock.MockRedisClusterClient;
import org.apache.skywalking.apm.plugin.lettuce.v5.mock.MockRedisClusterClientConstructorInterceptor;
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

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RedisChannelWriterInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private MockInstance mockClientOptionsInstance;
    @Mock
    private MockInstance mockRedisChannelWriterInstance;

    private RedisChannelWriterInterceptor interceptor;

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
        mockRedisChannelWriterInstance = new MockInstance();
        mockClientOptionsInstance = new MockInstance();
        mockClientOptionsInstance.setSkyWalkingDynamicField("127.0.0.1:6379;127.0.0.1:6378;");
        interceptor = new RedisChannelWriterInterceptor();
    }

    @Test
    public void testInterceptor() throws Throwable {
        interceptor.onConstruct(mockRedisChannelWriterInstance, new Object[] {mockClientOptionsInstance});
        RedisCommand redisCommand = new Command(CommandType.SET, null);
        interceptor.beforeMethod(mockRedisChannelWriterInstance, null, new Object[] {redisCommand}, null, null);
        interceptor.afterMethod(mockRedisChannelWriterInstance, null, null, null, null);
        MatcherAssert.assertThat((String) mockRedisChannelWriterInstance.getSkyWalkingDynamicField(), Is.is("127.0.0.1:6379;127.0.0.1:6378;"));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertThat(spans.size(), is(1));
        assertThat(spans.get(0).getOperationName(), is("Lettuce/SET"));
        assertThat(spans.get(0).isExit(), is(true));
        assertThat(SpanHelper.getComponentId(spans.get(0)), is(57));
        List<TagValuePair> tags = SpanHelper.getTags(spans.get(0));
        assertThat(tags.get(0).getValue(), is("Redis"));
        assertThat(SpanHelper.getLayer(spans.get(0)), CoreMatchers.is(SpanLayer.CACHE));
    }

    @Test
    public void testOnHugeClusterConsumerConfig() {
        List<RedisURI> redisURIs = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            redisURIs.add(RedisURI.create("localhost", i));
        }
        MockRedisClusterClient mockRedisClusterClient = new MockRedisClusterClient();
        MockRedisClusterClientConstructorInterceptor constructorInterceptor = new MockRedisClusterClientConstructorInterceptor();
        constructorInterceptor.onConstruct(mockRedisClusterClient, new Object[] {
            null,
            redisURIs
        });
        assertThat(mockRedisClusterClient.getOptions().getSkyWalkingDynamicField().toString().length(), Is.is(200));
    }
}
