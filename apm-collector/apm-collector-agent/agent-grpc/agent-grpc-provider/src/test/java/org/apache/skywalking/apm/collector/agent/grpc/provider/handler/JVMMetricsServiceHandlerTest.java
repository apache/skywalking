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
 */

package org.apache.skywalking.apm.collector.agent.grpc.provider.handler;

import org.apache.skywalking.apm.collector.analysis.jvm.define.service.ICpuMetricService;
import org.apache.skywalking.apm.collector.analysis.jvm.define.service.IGCMetricService;
import org.apache.skywalking.apm.collector.analysis.jvm.define.service.IMemoryMetricService;
import org.apache.skywalking.apm.collector.analysis.jvm.define.service.IMemoryPoolMetricService;
import org.apache.skywalking.apm.collector.analysis.metric.define.service.IInstanceHeartBeatService;
import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
@RunWith(MockitoJUnitRunner.class)
public class JVMMetricsServiceHandlerTest {

    private  ICpuMetricService cpuMetricService;
    private  IGCMetricService gcMetricService;
    private  IMemoryMetricService memoryMetricService;
    private  IMemoryPoolMetricService memoryPoolMetricService;
    private  IInstanceHeartBeatService instanceHeartBeatService;
    
    private JVMMetricsServiceHandler jvmMetricsServiceHandler;

    @Before
    public void setUp() throws Exception {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        jvmMetricsServiceHandler = new JVMMetricsServiceHandler(moduleManager);
        Whitebox.setInternalState(jvmMetricsServiceHandler, "cpuMetricService", cpuMetricService);
        Whitebox.setInternalState(jvmMetricsServiceHandler, "gcMetricService", gcMetricService);
        Whitebox.setInternalState(jvmMetricsServiceHandler, "memoryMetricService", memoryMetricService);
        Whitebox.setInternalState(jvmMetricsServiceHandler, "memoryPoolMetricService", memoryPoolMetricService);
        Whitebox.setInternalState(jvmMetricsServiceHandler, "instanceHeartBeatService", instanceHeartBeatService);
    }

    @Test
    public void collect() {
    }
}