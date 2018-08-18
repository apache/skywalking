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

import io.grpc.stub.StreamObserver;
import java.util.UUID;
import org.apache.skywalking.apm.collector.analysis.metric.define.service.IInstanceHeartBeatService;
import org.apache.skywalking.apm.collector.analysis.register.define.service.IInstanceIDService;
import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.network.proto.ApplicationInstance;
import org.apache.skywalking.apm.network.proto.ApplicationInstanceHeartbeat;
import org.apache.skywalking.apm.network.proto.ApplicationInstanceMapping;
import org.apache.skywalking.apm.network.proto.Downstream;
import org.apache.skywalking.apm.network.proto.OSInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
@RunWith(MockitoJUnitRunner.class)
public class InstanceDiscoveryServiceHandlerTest {

    private InstanceDiscoveryServiceHandler instanceDiscoveryServiceHandler;

    @Mock
    private IInstanceIDService instanceIDService;
    @Mock
    private IInstanceHeartBeatService instanceHeartBeatService;

    @Before
    public void setUp() throws Exception {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        instanceDiscoveryServiceHandler = new InstanceDiscoveryServiceHandler(moduleManager);
        Whitebox.setInternalState(instanceDiscoveryServiceHandler, "instanceIDService", instanceIDService);
        Whitebox.setInternalState(instanceDiscoveryServiceHandler, "instanceHeartBeatService", instanceHeartBeatService);
    }

    @Test
    public void registerInstance() {
        ApplicationInstance applicationInstance = ApplicationInstance.newBuilder()
            .setAgentUUID(UUID.randomUUID().toString())
            .setApplicationId(10)
            .setRegisterTime(System.currentTimeMillis())
            .setOsinfo(
                OSInfo.newBuilder()
                    .setOsName("MAC OS")
                    .setHostname("test")
                    .addIpv4S("127.0.0.1")
                    .setProcessNo(123456)
                    .build()
            ).build();
        when(instanceIDService.getOrCreateByAgentUUID(anyInt(), anyString(), anyLong(), anyObject())).thenReturn(100);
        instanceDiscoveryServiceHandler.registerInstance(applicationInstance, new StreamObserver<ApplicationInstanceMapping>() {
            @Override
            public void onNext(ApplicationInstanceMapping applicationInstanceMapping) {
                Assert.assertEquals(100, applicationInstanceMapping.getApplicationInstanceId());
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        });
    }

    @Test
    public void heartbeat() {
        ApplicationInstanceHeartbeat heartbeat = ApplicationInstanceHeartbeat.newBuilder()
            .setApplicationInstanceId(100)
            .setHeartbeatTime(System.currentTimeMillis())
            .build();
        instanceDiscoveryServiceHandler.heartbeat(heartbeat, new StreamObserver<Downstream>() {
            @Override
            public void onNext(Downstream downstream) {
                Assert.assertEquals(Downstream.getDefaultInstance(), downstream);
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        });
    }
}
