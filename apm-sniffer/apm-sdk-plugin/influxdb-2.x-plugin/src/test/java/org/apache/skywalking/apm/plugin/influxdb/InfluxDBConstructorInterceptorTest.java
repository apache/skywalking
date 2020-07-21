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

package org.apache.skywalking.apm.plugin.influxdb;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.plugin.influxdb.interceptor.InfluxDBConstructorInterceptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class InfluxDBConstructorInterceptorTest {

    private static final String INFLUXDB_URL = "http://127.0.0.1:8086";

    private InfluxDBConstructorInterceptor interceptor;

    @Mock
    private EnhancedInstance enhancedInstance;

    @Before
    public void setUp() throws Exception {
        interceptor = new InfluxDBConstructorInterceptor();
    }

    @Test
    public void onConstruct() throws Exception {
        interceptor.onConstruct(enhancedInstance, new Object[] {INFLUXDB_URL});

        verify(enhancedInstance).setSkyWalkingDynamicField(INFLUXDB_URL);
    }

    @Test
    public void onConstructWithUsernameAndPassword() {
        interceptor.onConstruct(enhancedInstance, new Object[] {
            INFLUXDB_URL,
            "admin",
            "123456",
            null
        });

        verify(enhancedInstance).setSkyWalkingDynamicField(INFLUXDB_URL);
    }

}
