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
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(MockitoJUnitRunner.class)
public class ProducerConstructorPropertiesInterceptorTest {
    private static Properties PRODUCER_CONFIG_WITH_LIST_BOOTSTRAP_SERVERS;

    private static Properties PRODUCER_CONFIG_WITH_STRING_BOOTSTRAP_SERVERS;

    @Mock
    private ProducerConstructorPropertiesInterceptor constructorPropertiesInterceptor;

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

    @BeforeClass
    public static void setup() {
        List<String> mockBootstrapServers = new ArrayList<String>();
        mockBootstrapServers.add("localhost:9092");
        mockBootstrapServers.add("localhost:19092");
        PRODUCER_CONFIG_WITH_LIST_BOOTSTRAP_SERVERS = new Properties() {{
            put("bootstrap.servers", mockBootstrapServers);
        }};
        PRODUCER_CONFIG_WITH_STRING_BOOTSTRAP_SERVERS = new Properties() {{
            // deliberately add whitespaces
            put("bootstrap.servers", String.join(" , ", mockBootstrapServers));
        }};
    }

    @Before
    public void setUpForEach() {
        constructorPropertiesInterceptor = new ProducerConstructorPropertiesInterceptor();
    }

    @Test
    public void givenListTypeBootstrapServers_whenConstructProducer_thenServersSaves() {
        constructorPropertiesInterceptor.onConstruct(enhancedInstance, new Object[]{PRODUCER_CONFIG_WITH_LIST_BOOTSTRAP_SERVERS});
        assertThat(enhancedInstance.getSkyWalkingDynamicField().toString(), is("localhost:9092;localhost:19092"));
    }

    @Test
    public void givenStringTypeBootstrapServers_whenConstructProducer_thenServersSaves() {
        constructorPropertiesInterceptor.onConstruct(enhancedInstance, new Object[]{PRODUCER_CONFIG_WITH_STRING_BOOTSTRAP_SERVERS});
        assertThat(enhancedInstance.getSkyWalkingDynamicField().toString(), is("localhost:9092;localhost:19092"));
    }
}