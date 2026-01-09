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

package org.apache.skywalking.library.banyandb.v1.client.grpc.channel;

import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.internal.PlatformDependent;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.banyandb.v1.client.Options;

@Slf4j
@RequiredArgsConstructor
public class DefaultChannelFactory implements ChannelFactory {
    private final URI[] targets;
    private final Options options;

    @Override
    public ManagedChannel create() throws IOException {
        NettyChannelBuilder managedChannelBuilder = NettyChannelBuilder.forAddress(resolveAddress())
                .maxInboundMessageSize(options.getMaxInboundMessageSize())
                .usePlaintext();

        File caFile = new File(options.getSslTrustCAPath());
        boolean isCAFileExist = caFile.exists() && caFile.isFile();
        if (options.isForceTLS() || isCAFileExist) {
            SslContextBuilder builder = GrpcSslContexts.forClient();

            if (isCAFileExist) {
                builder.trustManager(caFile);
            }
            managedChannelBuilder.negotiationType(NegotiationType.TLS).sslContext(builder.build());
        }
        return managedChannelBuilder.build();
    }

    private SocketAddress resolveAddress() throws UnknownHostException {
        int numAddresses = this.targets.length;
        if (numAddresses < 1) {
            throw new UnknownHostException();
        }
        int offset = numAddresses == 1 ? 0 : PlatformDependent.threadLocalRandom().nextInt(numAddresses);
        return new InetSocketAddress(this.targets[offset].getHost(), this.targets[offset].getPort());
    }
}
