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

import com.google.common.base.Strings;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.apache.skywalking.oap.server.library.server.Server;
import org.apache.skywalking.oap.server.library.server.ServerException;
import org.apache.skywalking.oap.server.library.server.grpc.ssl.DynamicSslContext;
import org.apache.skywalking.oap.server.library.server.pool.CustomThreadFactory;

@Slf4j
public class GRPCServer implements Server {

    private final String host;
    private final int port;
    private int maxConcurrentCallsPerConnection;
    private int maxMessageSize;
    private io.grpc.Server server;
    private NettyServerBuilder nettyServerBuilder;
    private String certChainFile;
    private String privateKeyFile;
    private String trustedCAsFile;
    private DynamicSslContext sslContext;
    private int threadPoolSize;
    private static final Marker SERVER_START_MARKER = MarkerFactory.getMarker("Console");

    public GRPCServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setMaxConcurrentCallsPerConnection(int maxConcurrentCallsPerConnection) {
        this.maxConcurrentCallsPerConnection = maxConcurrentCallsPerConnection;
    }

    public void setMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * Require for `server.crt` and `server.pem` for open ssl at server side.
     *
     * @param certChainFile  `server.crt` file
     * @param privateKeyFile `server.pem` file
     */
    public GRPCServer(String host, int port, String certChainFile, String privateKeyFile, String trustedCAsFile) {
        this(host, port);
        this.certChainFile = certChainFile;
        this.privateKeyFile = privateKeyFile;
        this.trustedCAsFile = trustedCAsFile;
    }

    @Override
    public void initialize() {
        InetSocketAddress address = new InetSocketAddress(host, port);
        nettyServerBuilder = NettyServerBuilder.forAddress(address);

        if (maxConcurrentCallsPerConnection > 0) {
            nettyServerBuilder.maxConcurrentCallsPerConnection(maxConcurrentCallsPerConnection);
        }
        if (maxMessageSize > 0) {
            nettyServerBuilder.maxInboundMessageSize(maxMessageSize);
        }
        if (threadPoolSize > 0) {
            ExecutorService executor = new ThreadPoolExecutor(
                threadPoolSize, threadPoolSize, 60, TimeUnit.SECONDS, new SynchronousQueue<>(),
                new CustomThreadFactory("grpcServerPool"), new CustomRejectedExecutionHandler()
            );
            nettyServerBuilder.executor(executor);
        }

        if (!Strings.isNullOrEmpty(privateKeyFile) && !Strings.isNullOrEmpty(certChainFile)) {
            sslContext = DynamicSslContext.forServer(privateKeyFile, certChainFile, trustedCAsFile);
            nettyServerBuilder.sslContext(sslContext);
        }
        log.info(SERVER_START_MARKER, "Server started, host {} listening on {}", host, port);
    }

    static class CustomRejectedExecutionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("Task {} rejected from {}", r.toString(), executor.toString());
        }
    }

    @Override
    public void start() throws ServerException {
        try {
            Optional.ofNullable(sslContext).ifPresent(DynamicSslContext::start);
            server = nettyServerBuilder.build();
            server.start();
        } catch (IOException e) {
            throw new GRPCServerException(e.getMessage(), e);
        }
    }

    public void addHandler(BindableService handler) {
        log.info("Bind handler {} into gRPC server {}:{}", handler.getClass().getSimpleName(), host, port);
        nettyServerBuilder.addService(handler);
    }

    public void addHandler(ServerServiceDefinition definition) {
        log.info("Bind handler {} into gRPC server {}:{}", definition.getClass().getSimpleName(), host, port);
        nettyServerBuilder.addService(definition);
    }

    public void addHandler(ServerInterceptor serverInterceptor) {
        log.info("Bind interceptor {} into gRPC server {}:{}", serverInterceptor.getClass().getSimpleName(), host, port);
        nettyServerBuilder.intercept(serverInterceptor);
    }

}
