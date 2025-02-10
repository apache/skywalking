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

package org.apache.skywalking.e2e;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import java.net.InetSocketAddress;
import lombok.SneakyThrows;

public class ServiceApplication {
    @SneakyThrows
    public static void main(String[] args) {
        Server server;
        NettyServerBuilder nettyServerBuilder;
        InetSocketAddress address = new InetSocketAddress("0.0.0.0", 18080);
        nettyServerBuilder = NettyServerBuilder.forAddress(address);
        nettyServerBuilder.addService(new BaselineQueryServer());
        server = nettyServerBuilder.build();
        server.start();
        server.awaitTermination();
    }
}
