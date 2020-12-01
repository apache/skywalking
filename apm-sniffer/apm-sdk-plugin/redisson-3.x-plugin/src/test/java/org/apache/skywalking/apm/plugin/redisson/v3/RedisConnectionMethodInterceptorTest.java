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

package org.apache.skywalking.apm.plugin.redisson.v3;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.plugin.redisson.v3.util.ClassUtil;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.redisson.config.Config;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class RedisConnectionMethodInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private MockInstance mockRedisClientInstance;
    private MockInstance mockRedisConnectionInstance;

    private RedisConnectionMethodInterceptor interceptor;

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
        mockRedisConnectionInstance = new MockInstance();
        mockRedisClientInstance = new MockInstance();
        mockRedisClientInstance.setSkyWalkingDynamicField("127.0.0.1:6379;127.0.0.1:6378;");
        interceptor = new RedisConnectionMethodInterceptor();
    }

    @Test
    public void testIntercept() throws Throwable {
        interceptor.onConstruct(mockRedisConnectionInstance, new Object[] {mockRedisClientInstance});
        MatcherAssert.assertThat(
            (String) mockRedisConnectionInstance.getSkyWalkingDynamicField(), Is.is("127.0.0.1:6379;127.0.0.1:6378;"));
    }

    @Test
    public void testSingleServerMode() throws Throwable {
        String redisAddress = "redis://127.0.0.1:6379";
        Config config = new Config();
        config.useSingleServer().setAddress(redisAddress);
        Object singleServerConfig = ClassUtil.getObjectField(config, "singleServerConfig");
        Object address = ClassUtil.getObjectField(singleServerConfig, "address");
        MatcherAssert.assertThat("127.0.0.1:6379", Is.is(ConnectionManagerInterceptor.getPeer(address)));
    }
}
