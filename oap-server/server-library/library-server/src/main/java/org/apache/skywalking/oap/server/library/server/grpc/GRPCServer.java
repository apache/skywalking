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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
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
    private DynamicSslContext sslContext;
    private int threadPoolSize = Runtime.getRuntime().availableProcessors() * 4;
    private int threadPoolQueueSize = 10000;

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

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public void setThreadPoolQueueSize(int threadPoolQueueSize) {
        this.threadPoolQueueSize = threadPoolQueueSize;
    }

    /**
     * Require for `server.crt` and `server.pem` for open ssl at server side.
     *
     * @param certChainFile  `server.crt` file
     * @param privateKeyFile `server.pem` file
     */
    public GRPCServer(String host, int port, String certChainFile, String privateKeyFile) {
        this(host, port);
        this.certChainFile = certChainFile;
        this.privateKeyFile = privateKeyFile;
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
        ArrayBlockingQueue blockingQueue = new ArrayBlockingQueue(threadPoolQueueSize);
        ExecutorService executor = new ThreadPoolExecutor(
            threadPoolSize, threadPoolSize, 60, TimeUnit.SECONDS, blockingQueue,
            new CustomThreadFactory("grpcServerPool"), new CustomRejectedExecutionHandler()
        );
        nettyServerBuilder = NettyServerBuilder.forAddress(address);
        nettyServerBuilder = nettyServerBuilder.maxConcurrentCallsPerConnection(maxConcurrentCallsPerConnection)
                                               .maxInboundMessageSize(maxMessageSize)
                                               .executor(executor);
        if (!Strings.isNullOrEmpty(privateKeyFile) && !Strings.isNullOrEmpty(certChainFile)) {
            sslContext = DynamicSslContext.forServer(privateKeyFile, certChainFile);
            nettyServerBuilder.sslContext(sslContext);
        }
        log.info("Server started, host {} listening on {}", host, port);
    }

    static class CustomRejectedExecutionHandler implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("Grpc server thread pool is full, rejecting the task");
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

    @Override
    public boolean isSSLOpen() {
        return !Strings.isNullOrEmpty(privateKeyFile) && !Strings.isNullOrEmpty(certChainFile);
    }

    @Override
    public boolean isStatusEqual(Server target) {
        if (this == target)
            return true;
        if (target == null || getClass() != target.getClass())
            return false;
        GRPCServer that = (GRPCServer) target;
        return port == that.port && Objects.equals(host, that.host) && Objects.equals(
            certChainFile, that.certChainFile) && Objects
            .equals(privateKeyFile, that.privateKeyFile);
    }
}
