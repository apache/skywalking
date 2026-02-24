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

package org.apache.skywalking.oap.server.library.server.http;

import com.google.common.collect.Sets;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.util.EventLoopGroups;
import com.linecorp.armeria.server.Route;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.docs.DocService;
import com.linecorp.armeria.server.encoding.DecodingService;
import com.linecorp.armeria.server.logging.LoggingService;
import io.netty.channel.EventLoopGroup;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.server.Server;
import org.apache.skywalking.oap.server.library.server.ssl.PrivateKeyUtil;
import org.apache.skywalking.oap.server.library.util.VirtualThreads;

import static java.util.Objects.requireNonNull;

/**
 * Armeria-based HTTP server shared by all OAP HTTP endpoints (core-http, receiver-http,
 * promql-http, logql-http, zipkin-query-http, zipkin-http, firehose-http — up to 7 servers).
 *
 * <h3>Thread model</h3>
 * Armeria uses a two-tier thread model:
 * <ul>
 *   <li><b>Event loop threads</b> — non-blocking I/O multiplexers (epoll/kqueue). Handle
 *       connection accept, read/write, and protocol parsing. A few threads can serve
 *       thousands of connections because they never block.</li>
 *   <li><b>Blocking task executor threads</b> — where request handlers actually run when
 *       annotated with {@code @Blocking}. These threads block on storage queries,
 *       downstream calls, and computation. Each concurrent blocking request occupies
 *       one thread for its full duration.</li>
 * </ul>
 *
 * The blocking executor needs more threads than the event loop because it's where
 * requests spend most of their time (waiting on I/O), while event loop threads just
 * shuttle bytes and are immediately available for the next connection.
 *
 * <h3>Thread policies</h3>
 * <pre>
 *                     Armeria default        SkyWalking
 *   Event loop:       cores * 2 per server   min(5, cores) shared across all servers
 *   Blocking exec:    cached, up to 200      JDK 25+: virtual threads
 *                                             JDK &lt;25: Armeria default (unchanged)
 * </pre>
 *
 * <h4>Event loop: {@code min(5, cores)}, shared</h4>
 * <pre>
 *   cores:    2    4    8   10   24
 *   threads:  2    4    5    5    5
 * </pre>
 * Armeria's default creates cores*2 event loop threads <em>per server</em>, which for 7
 * HTTP servers means 7 * cores * 2 = 140 threads on 10-core — far more than needed for
 * HTTP traffic. All servers share one {@link EventLoopGroup} with min(5, cores) threads.
 *
 * <h4>Blocking executor: Armeria default on JDK &lt;25, virtual threads on JDK 25+</h4>
 * On JDK &lt;25, Armeria's default cached pool (up to 200 on-demand threads) is kept
 * unchanged. HTTP handlers block on storage/DB queries (BanyanDB, Elasticsearch) which
 * can take 10ms–seconds. A bounded pool would cause request queuing and UI timeouts
 * when many concurrent queries block simultaneously. The cached pool handles this
 * correctly — threads are created on demand and released after idle timeout.
 * On JDK 25+, virtual threads replace this pool entirely — each blocking request
 * gets its own virtual thread backed by ~cores shared carrier threads.
 *
 * <h3>Comparison with gRPC</h3>
 * gRPC is the primary telemetry ingestion path. HTTP is secondary (UI queries, PromQL,
 * LogQL, and optionally telemetry), so it uses fewer event loop threads.
 * <pre>
 *                     gRPC                                HTTP (Armeria)
 *   Event loop:       cores, shared (gRPC default)        min(5, cores), shared
 *   Handler/blocking: JDK 25+: virtual threads            JDK 25+: virtual threads
 *                     JDK &lt;25: CachedThreadPool (default) JDK &lt;25: Armeria default cached pool
 * </pre>
 * Both gRPC and HTTP keep their framework's default unbounded pool on JDK &lt;25 because
 * handlers may block on long I/O (storage queries, extension callbacks). On JDK 25+,
 * virtual threads replace both pools.
 */
@Slf4j
public class HTTPServer implements Server {
    /**
     * Shared event loop group for all HTTP servers.
     * Non-blocking I/O multiplexing — min(5, cores) threads can handle thousands
     * of connections. Replaces Armeria's default of cores*2 per server.
     */
    private static final EventLoopGroup SHARED_WORKER_GROUP;

    static {
        final int cores = Runtime.getRuntime().availableProcessors();
        SHARED_WORKER_GROUP = EventLoopGroups.newEventLoopGroup(Math.min(5, cores));
    }

    private final HTTPServerConfig config;
    protected ServerBuilder sb;
    // Health check service, supports HEAD, GET method.
    protected final Set<HttpMethod> allowedMethods = Sets.newHashSet(HttpMethod.HEAD);
    private String blockingTaskName = "http-blocking";

    public HTTPServer(HTTPServerConfig config) {
        this.config = config;
    }

    public void setBlockingTaskName(final String blockingTaskName) {
        this.blockingTaskName = blockingTaskName;
    }

    /**
     * Build the Armeria server with shared event loop, TLS, and blocking executor.
     *
     * <p>Thread pool assignment:
     * <ul>
     *   <li>{@code workerGroup} — shared event loop for I/O (min(5, cores) threads)</li>
     *   <li>{@code blockingTaskExecutor} — JDK 25+: virtual threads per request;
     *       JDK &lt;25: Armeria's default cached pool (handlers block on storage queries)</li>
     * </ul>
     */
    @Override
    public void initialize() {
        sb = com.linecorp.armeria.server.Server
            .builder()
            .workerGroup(SHARED_WORKER_GROUP, false)
            .baseContextPath(config.getContextPath())
            .serviceUnder("/docs", DocService.builder().build())
            .http1MaxHeaderSize(config.getMaxRequestHeaderSize())
            .idleTimeout(Duration.ofMillis(config.getIdleTimeOut()))
            .decorator(Route.ofCatchAll(), (delegate, ctx, req) -> {
                if (!allowedMethods.contains(ctx.method())) {
                    return HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED);
                }
                return delegate.serve(ctx, req);
            })
            .decorator(DecodingService.newDecorator())
            .decorator(LoggingService.newDecorator());
        if (config.isEnableTLS()) {
            sb.https(new InetSocketAddress(
                    config.getHost(),
                    config.getPort()));
            try (InputStream cert = new FileInputStream(config.getTlsCertChainPath());
                 InputStream key = PrivateKeyUtil.loadDecryptionKey(config.getTlsKeyPath())) {
                sb.tls(cert, key);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            sb.http(new InetSocketAddress(
                    config.getHost(),
                    config.getPort()
            ));
        }
        if (config.getAcceptQueueSize() > 0) {
            sb.maxNumConnections(config.getAcceptQueueSize());
        }

        if (config.isAcceptProxyRequest()) {
            sb.absoluteUriTransformer(this::transformAbsoluteURI);
        }

        // JDK 25+: virtual-thread-per-task executor (unbounded, ~cores carrier threads)
        // JDK <25: Armeria's default cached pool (up to 200 threads) — kept unchanged
        //          because HTTP handlers block on long storage queries (10ms-seconds)
        if (VirtualThreads.isSupported()) {
            final ScheduledExecutorService blockingExecutor = VirtualThreads.createScheduledExecutor(
                blockingTaskName, () -> null);
            if (blockingExecutor != null) {
                sb.blockingTaskExecutor(blockingExecutor, true);
            }
        }

        log.info("Server root context path: {}", config.getContextPath());
    }

    /**
     * @param handler        Specific service provider.
     * @param httpMethods    Register the http methods which the handler service accepts. Other methods respond "405, Method Not Allowed".
     */
    public void addHandler(Object handler, List<HttpMethod> httpMethods) {
        requireNonNull(allowedMethods, "allowedMethods");
        log.info(
            "Bind handler {} into http server {}:{}",
            handler.getClass().getSimpleName(), config.getHost(), config.getPort()
        );

        sb.annotatedService()
          .build(handler);
        this.allowedMethods.addAll(httpMethods);
    }

    @Override
    public void start() {
        sb.build().start().join();
    }

    private String transformAbsoluteURI(final String uri) {
        if (uri.startsWith("https://")) {
            return uri.substring(uri.indexOf("/", 8));
        }
        if (uri.startsWith("http://")) {
            return uri.substring(uri.indexOf("/", 7));
        }
        return uri;
    }
}
