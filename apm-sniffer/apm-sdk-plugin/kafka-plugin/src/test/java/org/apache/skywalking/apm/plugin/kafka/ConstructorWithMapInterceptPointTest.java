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

package org.apache.skywalking.apm.plugin.kafka;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConstructorWithMapInterceptPointTest {

    @Mock
    private Map<String, String> consumerConfig;

    @Mock
    private ConstructorWithMapInterceptPoint constructorInterceptor;

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {
        private ConsumerEnhanceRequiredInfo consumerEnhanceRequiredInfo;

        @Override
        public Object getSkyWalkingDynamicField() {
            return consumerEnhanceRequiredInfo;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            consumerEnhanceRequiredInfo = (ConsumerEnhanceRequiredInfo) value;
        }
    };

    @Before
    public void setUp() {
        String mockBootstrapServers = "localhost:9092,localhost:19092";
        when(consumerConfig.get("bootstrap.servers")).thenReturn(mockBootstrapServers);

        constructorInterceptor = new ConstructorWithMapInterceptPoint();
    }

    @Test
    public void testOnConsumer() {
        constructorInterceptor.onConstruct(enhancedInstance, new Object[] {consumerConfig});
        ConsumerEnhanceRequiredInfo consumerEnhanceRequiredInfo = (ConsumerEnhanceRequiredInfo) enhancedInstance.getSkyWalkingDynamicField();
        assertThat(consumerEnhanceRequiredInfo.getBrokerServers(), is("localhost:9092;localhost:19092"));
    }

}
