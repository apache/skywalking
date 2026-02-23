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
import org.apache.skywalking.oap.server.library.util.VirtualThreads;

/**
 * gRPC server backed by Netty. Used by up to 4 OAP server endpoints (core-grpc,
 * receiver-grpc, ebpf-grpc, als-grpc). gRPC is the primary telemetry ingestion path.
 *
 * <h3>Thread model</h3>
 * gRPC-netty uses a three-tier thread model:
 * <ol>
 *   <li><b>Boss event loop</b> — 1 thread. Accepts TCP connections, creates Netty channels,
 *       then hands them off to worker event loop. Shared across all gRPC servers.</li>
 *   <li><b>Worker event loop</b> — non-blocking I/O multiplexing (HTTP/2 framing, read/write,
 *       TLS). gRPC defaults to {@code cores} threads (halves Netty's {@code cores * 2}),
 *       shared across all servers via {@code SharedResourcePool}. Must never block —
 *       a few threads can serve thousands of connections.</li>
 *   <li><b>Application executor</b> — where gRPC service methods actually run
 *       ({@code onMessage}, {@code onHalfClose}, {@code onComplete}). gRPC dispatches
 *       callbacks from the event loop to this executor via
 *       {@code JumpToApplicationThreadServerStreamListener}. For streaming RPCs, the
 *       thread is held only during each individual callback, not for the entire stream —
 *       between messages the thread returns to the pool.</li>
 * </ol>
 *
 * <h3>Application executor</h3>
 * gRPC's default application executor is an <b>unbounded {@code CachedThreadPool}</b>
 * ({@code Executors.newCachedThreadPool()}, named {@code grpc-default-executor}).
 * gRPC chose this for safety — application code may block (JDBC, file I/O, synchronized),
 * and blocking the event loop would freeze all connections. The {@code CachedThreadPool}
 * never rejects work but grows unboundedly: each burst creates new threads (expensive),
 * idle threads die after 60s, then the next burst creates them again.
 *
 * <p>While benchmarks show {@code CachedThreadPool} is <b>2x slower</b> than a fixed pool
 * (see <a href="https://github.com/grpc/grpc-java/issues/7381">grpc-java#7381</a>),
 * we keep the default on JDK &lt;25 because SkyWalking extensions may register gRPC handlers
 * that perform long-blocking I/O (on-demand queries, external calls). A bounded pool would
 * risk starving other gRPC services. On JDK 25+, virtual threads replace this pool —
 * each callback gets its own virtual thread, combining unbounded concurrency with
 * minimal resource overhead.
 *
 * <p>Using {@code directExecutor()} is unsafe for SkyWalking because some handlers call
 * {@code BatchQueue.produce()} with {@code BLOCKING} strategy which can block the thread
 * — that would freeze the event loop and stall all connections.
 *
 * <h3>Thread policies</h3>
 * <pre>
 *                     gRPC default                 SkyWalking
 *   Boss EL:          1, shared                    (unchanged)
 *   Worker EL:        cores, shared                (unchanged)
 *   App executor:     CachedThreadPool (unbounded) JDK 25+: virtual threads
 *                                                   JDK &lt;25: gRPC default (unchanged)
 * </pre>
 *
 * <h4>Worker event loop: {@code cores}, shared by gRPC (default, unchanged)</h4>
 * <pre>
 *   cores:    2    4    8   10   24
 *   threads:  2    4    8   10   24
 * </pre>
 * Non-blocking I/O multiplexing — a few threads handle thousands of connections.
 * gRPC's internal {@code SharedResourcePool} already shares one event loop group across
 * all {@code NettyServerBuilder} instances that use the default. No custom configuration
 * needed.
 *
 * <h3>Comparison with HTTP (Armeria)</h3>
 * <pre>
 *                     gRPC                                HTTP (Armeria)
 *   Event loop:       cores, shared (gRPC default)        min(5, cores), shared
 *   Handler/blocking: JDK 25+: virtual threads            JDK 25+: virtual threads
 *                     JDK &lt;25: CachedThreadPool (default) JDK &lt;25: Armeria default cached pool
 * </pre>
 * Both gRPC and HTTP keep their framework's default unbounded pool on JDK &lt;25 because
 * handlers may block on long I/O (storage queries, extension callbacks). On JDK 25+,
 * virtual threads replace both pools.
 *
 * <h3>User-configured thread pool</h3>
 * When {@code threadPoolSize > 0} is set via config, it overrides the default with a
 * per-server fixed pool of that size. On JDK 25+ it is ignored — virtual threads
 * are always used.
 */
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
    private String threadPoolName = "grpcServerPool";
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

    public void setThreadPoolName(String threadPoolName) {
        this.threadPoolName = threadPoolName;
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

    /**
     * Build the gRPC server with optional TLS and handler executor.
     *
     * <p>Handler executor assignment:
     * <ul>
     *   <li>JDK 25+: virtual-thread-per-task executor (ignores threadPoolSize)</li>
     *   <li>JDK &lt;25, threadPoolSize &gt; 0: per-server fixed pool (legacy config)</li>
     *   <li>JDK &lt;25, threadPoolSize == 0: gRPC default CachedThreadPool (unbounded)</li>
     * </ul>
     */
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
        // JDK 25+: virtual threads for all servers (threadPoolSize ignored)
        // JDK <25, threadPoolSize > 0: per-server fixed pool (legacy config override)
        // JDK <25, threadPoolSize == 0: gRPC default CachedThreadPool (safe for extensions)
        final ExecutorService executor = VirtualThreads.createExecutor(
            threadPoolName,
            () -> {
                if (threadPoolSize > 0) {
                    return new ThreadPoolExecutor(
                        threadPoolSize, threadPoolSize, 60, TimeUnit.SECONDS,
                        new SynchronousQueue<>(),
                        new CustomThreadFactory(threadPoolName),
                        new CustomRejectedExecutionHandler()
                    );
                }
                return null;
            }
        );
        if (executor != null) {
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

    public void addInterceptor(ServerInterceptor serverInterceptor) {
        log.info("Bind interceptor {} into gRPC server {}:{}", serverInterceptor.getClass().getSimpleName(), host, port);
        nettyServerBuilder.intercept(serverInterceptor);
    }

}
