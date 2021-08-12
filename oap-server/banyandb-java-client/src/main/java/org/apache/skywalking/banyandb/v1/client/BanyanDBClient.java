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

package org.apache.skywalking.banyandb.v1.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolverRegistry;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * BanyanDBClient represents a client instance interacting with BanyanDB server. This is built on the top of BanyanDB v1
 * gRPC APIs.
 */
@RequiredArgsConstructor
@Slf4j
public class BanyanDBClient {
    /**
     * The hostname of BanyanDB server.
     */
    private final String host;
    /**
     * The port of BanyanDB server.
     */
    private final int port;
    /**
     * The instance name.
     */
    private final String group;
    /**
     * Options for server connection.
     */
    @Setter
    private Options options = new Options();
    /**
     * Managed gRPC connection.
     */
    private volatile ManagedChannel managedChannel;
    /**
     * The connection status.
     */
    private volatile boolean isConnected = false;

    /**
     * Connect to the server.
     *
     * @throws RuntimeException if server is not reachable.
     */
    public void connect() {
        NameResolverRegistry.getDefaultRegistry().register(new DnsNameResolverProvider());

        final ManagedChannelBuilder nettyChannelBuilder = NettyChannelBuilder.forAddress(host, port);
        nettyChannelBuilder.maxInboundMessageSize(options.getMaxInboundMessageSize());

        managedChannel = nettyChannelBuilder.build();
        isConnected = true;
    }

    /**
     * Client connection options.
     */
    @Setter
    @Getter
    public class Options {
        private int maxInboundMessageSize = 1024 * 1024 * 50;

        private Options() {
        }

    }
}
