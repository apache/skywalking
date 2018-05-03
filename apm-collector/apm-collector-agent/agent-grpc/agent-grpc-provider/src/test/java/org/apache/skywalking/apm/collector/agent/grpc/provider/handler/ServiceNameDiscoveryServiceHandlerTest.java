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
import org.apache.skywalking.apm.collector.analysis.register.define.service.IServiceNameService;
import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.network.proto.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
@RunWith(MockitoJUnitRunner.class)
public class ServiceNameDiscoveryServiceHandlerTest {

    @Mock
    private IServiceNameService serviceNameService;

    private ServiceNameDiscoveryServiceHandler serviceNameDiscoveryServiceHandler;

    @Before
    public void setUp() throws Exception {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        serviceNameDiscoveryServiceHandler = new ServiceNameDiscoveryServiceHandler(moduleManager);
        Whitebox.setInternalState(serviceNameDiscoveryServiceHandler, "serviceNameService", serviceNameService);

    }

    @Test
    public void discovery() {
        ServiceNameElement element = ServiceNameElement.newBuilder()
                .setApplicationId(10)
                .setServiceName("/hello/world")
                .setSrcSpanType(SpanType.Entry)
                .build();
        ServiceNameCollection nameCollection = ServiceNameCollection.newBuilder()
                .addElements(element)
                .build();
        when(serviceNameService.get(anyInt(), anyInt(), anyString())).thenReturn(1);
        serviceNameDiscoveryServiceHandler.discovery(nameCollection, new StreamObserver<ServiceNameMappingCollection>() {
            @Override
            public void onNext(ServiceNameMappingCollection serviceNameMappingCollection) {
                ServiceNameMappingElement mappingElement = serviceNameMappingCollection.getElementsList().get(0);
                assertEquals(mappingElement.getElement(), element);
                int serviceId = mappingElement.getServiceId();
                assertEquals(serviceId, 1);
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