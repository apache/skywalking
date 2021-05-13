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

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ProducerConstructorInterceptorTest {
    @Mock
    private ProducerConfig producerConfig;

    @Mock
    private ProducerConstructorInterceptor constructorInterceptor;

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {
        private String brokerServers;

        @Override
        public Object getSkyWalkingDynamicField() {
            return brokerServers;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            brokerServers = (String) value;
        }
    };

    @Before
    public void setUp() {
        List<String> mockBootstrapServers = new ArrayList<String>();
        mockBootstrapServers.add("localhost:9092");
        mockBootstrapServers.add("localhost:19092");
        when(producerConfig.getList("bootstrap.servers")).thenReturn(mockBootstrapServers);
        constructorInterceptor = new ProducerConstructorInterceptor();
    }

    @Test
    public void testOnConsumer() {
        constructorInterceptor.onConstruct(enhancedInstance, new Object[] {producerConfig});
        assertThat(enhancedInstance.getSkyWalkingDynamicField().toString(), is("localhost:9092;localhost:19092"));
    }
}