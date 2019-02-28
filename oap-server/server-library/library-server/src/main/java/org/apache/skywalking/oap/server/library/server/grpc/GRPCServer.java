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

package org.apache.skywalking.oap.server.library.server.grpc;

import io.grpc.*;
import io.grpc.netty.*;
import io.netty.handler.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.Objects;
import org.apache.skywalking.oap.server.library.server.Server;
import org.apache.skywalking.oap.server.library.server.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng, wusheng
 */
public class GRPCServer implements Server {

    private static final Logger logger = LoggerFactory.getLogger(GRPCServer.class);

    private final String host;
    private final int port;
    private int maxConcurrentCallsPerConnection;
    private int maxMessageSize;
    private io.grpc.Server server;
    private NettyServerBuilder nettyServerBuilder;
    private SslContextBuilder sslContextBuilder;
    private File certChainFile;
    private File privateKeyFile;

    public GRPCServer(String host, int port) {
        this.host = host;
        this.port = port;
        this.maxConcurrentCallsPerConnection = 4;
        this.maxMessageSize = Integer.MAX_VALUE;
    }

    public void setMaxConcurrentCallsPerConnection(int maxConcurrentCallsPerConnection) {
        this.maxConcurrentCallsPerConnection = maxConcurrentCallsPerConnection;
    }

    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    /**
     * Require for `server.crt` and `server.pem` for open ssl at server side.
     *
     * @param host
     * @param port
     * @param certChainFile `server.crt` file
     * @param privateKeyFile `server.pem` file
     */
    public GRPCServer(String host, int port, File certChainFile, File privateKeyFile) {
        this.host = host;
        this.port = port;
        this.certChainFile = certChainFile;
        this.privateKeyFile = privateKeyFile;
        this.sslContextBuilder = SslContextBuilder.forServer(certChainFile,
            privateKeyFile);
    }

    @Override
    public String hostPort() {
        return host + ":" + port;
    }

    @Override
    public String serverClassify() {
        return "Google-RPC";
    }

    @Override
    public void initialize() {
        InetSocketAddress address = new InetSocketAddress(host, port);

        nettyServerBuilder = NettyServerBuilder.forAddress(address);
        nettyServerBuilder = nettyServerBuilder.maxConcurrentCallsPerConnection(maxConcurrentCallsPerConnection).maxMessageSize(maxMessageSize);
        logger.info("Server started, host {} listening on {}", host, port);
    }

    @Override
    public void start() throws ServerException {
        try {
            if (sslContextBuilder != null) {
                nettyServerBuilder = nettyServerBuilder.sslContext(
                    GrpcSslContexts.configure(sslContextBuilder,
                        SslProvider.OPENSSL).build());
            }
            server = nettyServerBuilder.build();
            server.start();
        } catch (IOException e) {
            throw new GRPCServerException(e.getMessage(), e);
        }
    }

    public void addHandler(BindableService handler) {
        nettyServerBuilder.addService(handler);
    }

    public void addHandler(ServerServiceDefinition definition) {
        nettyServerBuilder.addService(definition);
    }

    @Override
    public boolean isSSLOpen() {
        return sslContextBuilder == null;
    }

    @Override
    public boolean isStatusEqual(Server target) {
        if (this == target)
            return true;
        if (target == null || getClass() != target.getClass())
            return false;
        GRPCServer that = (GRPCServer)target;
        return port == that.port &&
            Objects.equals(host, that.host) &&
            Objects.equals(certChainFile, that.certChainFile) &&
            Objects.equals(privateKeyFile, that.privateKeyFile);
    }
}
