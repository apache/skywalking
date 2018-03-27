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

import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContextBuilder;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.conf.Constants;

import javax.net.ssl.SSLException;
import java.io.File;

/**
 * Detect the `/ca` folder in agent package, if `ca.crt` exists, start TLS (no mutual auth).
 *
 * @author wusheng
 */
public class TLSChannelBuilder {
    private static String CA_FILE_NAME = "ca" + Constants.PATH_SEPARATOR + "ca.crt";

    private NettyChannelBuilder nettyChannelBuilder;

    public TLSChannelBuilder(NettyChannelBuilder nettyChannelBuilder) {
        this.nettyChannelBuilder = nettyChannelBuilder;
    }

    /**
     * Build a TLS supported channel is necessary.
     *
     * @return chanel builder
     * @throws AgentPackageNotFoundException
     * @throws SSLException
     */
    NettyChannelBuilder buildTLS() throws AgentPackageNotFoundException, SSLException {
        File caFile = new File(AgentPackagePath.getPath(), CA_FILE_NAME);
        if (caFile.exists() && caFile.isFile()) {
            SslContextBuilder builder = GrpcSslContexts.forClient();
            builder.trustManager(caFile);
            nettyChannelBuilder = nettyChannelBuilder.negotiationType(NegotiationType.TLS)
                    .sslContext(builder.build());
        }
        return nettyChannelBuilder;
    }
}
