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

package org.apache.skywalking.apm.collector.agent.grpc.provider;

import io.grpc.ServerServiceDefinition;
import io.grpc.StatusRuntimeException;
import io.grpc.testing.GrpcServerRule;
import org.apache.skywalking.apm.collector.agent.grpc.provider.handler.ApplicationRegisterServiceHandler;
import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.server.ServerException;
import org.apache.skywalking.apm.collector.server.grpc.GRPCServer;
import org.apache.skywalking.apm.network.proto.Application;
import org.apache.skywalking.apm.network.proto.ApplicationMapping;
import org.apache.skywalking.apm.network.proto.ApplicationRegisterServiceGrpc;
import org.apache.skywalking.apm.network.proto.KeyWithIntegerValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationSimpleCheckerTest {

    @Rule
    public final GrpcServerRule grpcServerRule = new GrpcServerRule().directExecutor();

    @Mock
    private ModuleManager moduleManager;

    @Before
    public void setUp() throws Exception {
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
    }

    @Test(expected = StatusRuntimeException.class)
    public void build() throws ServerException {
        ApplicationRegisterServiceHandler applicationRegisterServiceHandler = new ApplicationRegisterServiceHandler(moduleManager);
        MockGRPCServer mockGRPCServer = new MockGRPCServer(grpcServerRule);
        mockGRPCServer.initialize();

        AuthenticationSimpleChecker.INSTANCE.build(mockGRPCServer, applicationRegisterServiceHandler);
        grpcServerRule.getServiceRegistry().addService(applicationRegisterServiceHandler);

        ApplicationRegisterServiceGrpc.ApplicationRegisterServiceBlockingStub stub = ApplicationRegisterServiceGrpc.newBlockingStub(grpcServerRule.getChannel());
        Application application = Application.newBuilder().setApplicationCode("test").build();

        ApplicationMapping applicationMapping = stub.applicationCodeRegister(application);
        assertEquals(applicationMapping.getApplication(), KeyWithIntegerValue.getDefaultInstance());

        AuthenticationSimpleChecker.INSTANCE.setExpectedToken("test");
        AuthenticationSimpleChecker.INSTANCE.build(mockGRPCServer, applicationRegisterServiceHandler);
        stub.applicationCodeRegister(application);

    }

    @Test
    public void setExpectedToken() {

    }

    class MockGRPCServer extends GRPCServer {

        private GrpcServerRule grpcServerRule;

        public MockGRPCServer(String host, int port) {
            super(host, port);
        }

        public MockGRPCServer(GrpcServerRule grpcServerRule) {
            super("127.0.0.1", 0);
            this.grpcServerRule = grpcServerRule;
        }

        public void addHandler(ServerServiceDefinition definition) {
            grpcServerRule.getServiceRegistry().addService(definition);
        }


    }
}