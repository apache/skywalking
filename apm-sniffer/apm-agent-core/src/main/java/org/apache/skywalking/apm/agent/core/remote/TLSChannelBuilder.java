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
import java.io.File;
import javax.net.ssl.SSLException;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.Constants;

/**
 * Detect the `/ca` folder in agent package, if `ca.crt` exists, start TLS (no mutual auth).
 */
public class TLSChannelBuilder implements ChannelBuilder<NettyChannelBuilder> {
    private static String CA_FILE_NAME = "ca" + Constants.PATH_SEPARATOR + "ca.crt";

    @Override
    public NettyChannelBuilder build(
        NettyChannelBuilder managedChannelBuilder) throws AgentPackageNotFoundException, SSLException {
        File caFile = new File(AgentPackagePath.getPath(), CA_FILE_NAME);
        boolean isCAFileExist = caFile.exists() && caFile.isFile();
        if (Config.Agent.FORCE_TLS || isCAFileExist) {
            SslContextBuilder builder = GrpcSslContexts.forClient();
            if (isCAFileExist) {
                builder.trustManager(caFile);
            }
            managedChannelBuilder = managedChannelBuilder.negotiationType(NegotiationType.TLS)
                                                         .sslContext(builder.build());
        }
        return managedChannelBuilder;
    }
}
