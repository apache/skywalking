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

package org.apache.skywalking.apm.agent.core.remote;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.net.InetSocketAddress;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.network.register.v2.*;
import org.junit.*;

public class GRPCChannelManagerTest {
    @BeforeClass
    public static void setup() {
        Config.Collector.BACKEND_SERVICE = "127.0.0.1:8080";
    }

    @AfterClass
    public static void clear() {
        Config.Collector.BACKEND_SERVICE = "";
    }

    //@Test
    public void testConnected() throws Throwable {
        GRPCChannelManager manager = new GRPCChannelManager();
        manager.addChannelListener(new GRPCChannelListener() {
            @Override public void statusChanged(GRPCChannelStatus status) {
            }
        });

        manager.boot();
        Thread.sleep(1000);

        RegisterGrpc.RegisterBlockingStub stub = RegisterGrpc.newBlockingStub(manager.getChannel());
        try {
            stub.doServiceRegister(Services.newBuilder().addServices(Service.newBuilder().setServiceName("abc")).build());
        } catch (Exception e) {
            e.printStackTrace();
        }

        NettyServerBuilder nettyServerBuilder = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 8080));
        nettyServerBuilder.addService(new RegisterGrpc.RegisterImplBase() {
            @Override
            public void doServiceRegister(Services request, StreamObserver<ServiceRegisterMapping> responseObserver) {
            }
        });
        Server server = nettyServerBuilder.build();
        server.start();
        Thread.sleep(1000);

        boolean registerSuccess = false;
        try {
            stub.doServiceRegister(Services.newBuilder().addServices(Service.newBuilder().setServiceName("abc")).build());
            registerSuccess = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        Assert.assertTrue(registerSuccess);
        server.shutdownNow();
    }
}
