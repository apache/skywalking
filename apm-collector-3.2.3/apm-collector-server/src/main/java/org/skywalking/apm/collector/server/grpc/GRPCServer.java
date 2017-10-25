/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.server.grpc;

import io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.core.server.ServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class GRPCServer implements Server {

    private final Logger logger = LoggerFactory.getLogger(GRPCServer.class);

    private final String host;
    private final int port;
    private io.grpc.Server server;
    private NettyServerBuilder nettyServerBuilder;

    public GRPCServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override public String hostPort() {
        return host + ":" + port;
    }

    @Override public String serverClassify() {
        return "Google-RPC";
    }

    @Override public void initialize() throws ServerException {
        InetSocketAddress address = new InetSocketAddress(host, port);
        nettyServerBuilder = NettyServerBuilder.forAddress(address);
        logger.info("Server started, host {} listening on {}", host, port);
    }

    @Override public void start() throws ServerException {
        try {
            server = nettyServerBuilder.build();
            server.start();
        } catch (IOException e) {
            throw new GRPCServerException(e.getMessage(), e);
        }
    }

    @Override public void addHandler(Handler handler) {
        nettyServerBuilder.addService((io.grpc.BindableService)handler);
    }
}
