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

package org.apache.skywalking.oap.server.library.client.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import lombok.Getter;
import org.apache.skywalking.oap.server.library.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GRPCClient implements Client {

    private static final Logger LOGGER = LoggerFactory.getLogger(GRPCClient.class);

    @Getter
    private final String host;

    @Getter
    private final int port;

    private SslContext sslContext;

    private ManagedChannel channel;

    public GRPCClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public GRPCClient(String host, int port, final SslContext sslContext) {
        this(host, port);
        this.sslContext = sslContext;
    }

    @Override
    public void connect() {
        if (sslContext == null) {
            channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
            return;
        }
        channel = NettyChannelBuilder.forAddress(host, port).sslContext(sslContext).build();
    }

    @Override
    public void shutdown() {
        try {
            channel.shutdownNow();
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }

    public ManagedChannel getChannel() {
        return channel;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
