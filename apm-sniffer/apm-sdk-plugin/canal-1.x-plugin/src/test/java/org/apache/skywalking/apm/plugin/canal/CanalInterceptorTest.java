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

package org.apache.skywalking.apm.plugin.canal;

import com.alibaba.otter.canal.client.impl.SimpleCanalConnector;
import com.alibaba.otter.canal.common.utils.AddressUtils;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class CanalInterceptorTest {

    private CanalInterceptor canalInterceptor;

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private Object[] arguments;

    private Class[] argumentType;

    private class CanalConnector extends SimpleCanalConnector implements EnhancedInstance {

        public CanalConnector(SocketAddress address, String username, String password, String destination) {
            super(address, username, password, destination);
        }

        @Override
        public Object getSkyWalkingDynamicField() {
            return null;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {

        }
    }

    private EnhancedInstance enhancedInstance = new CanalConnector(new InetSocketAddress(AddressUtils.getHostIp(), 11111), "example", "", "") {
        @Override
        public Object getSkyWalkingDynamicField() {
            CanalEnhanceInfo canalEnhanceInfo = new CanalEnhanceInfo();
            canalEnhanceInfo.setUrl("localhost:11111");
            canalEnhanceInfo.setDestination("example");
            return canalEnhanceInfo;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
        }

    };

    @Before
    public void setUp() {
        canalInterceptor = new CanalInterceptor();
        arguments = new Object[] {100};
    }

    @Test
    public void testSendMessage() throws Throwable {
        canalInterceptor.beforeMethod(enhancedInstance, null, arguments, null, null);
        canalInterceptor.afterMethod(enhancedInstance, null, arguments, null, null);

        List<TraceSegment> traceSegmentList = segmentStorage.getTraceSegments();
        assertThat(traceSegmentList.size(), is(1));

        TraceSegment segment = traceSegmentList.get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segment);
        assertThat(spans.size(), is(1));
    }

}
