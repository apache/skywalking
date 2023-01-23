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
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.Route;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.docs.DocService;
import com.linecorp.armeria.server.healthcheck.HealthCheckService;
import com.linecorp.armeria.server.logging.LoggingService;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oap.server.library.server.Server;
import org.apache.skywalking.oap.server.library.server.ssl.PrivateKeyUtil;

import static java.util.Objects.requireNonNull;

@Slf4j
public class HTTPServer implements Server {
    private final HTTPServerConfig config;
    private ServerBuilder sb;
    // Health check service, supports HEAD, GET method.
    private final Set<HttpMethod> allowedMethods = Sets.newHashSet(HttpMethod.HEAD);

    private Set<Object> handlers;

    private Path tlsCertChainPath;
    private Path tlsKeyPath;

    private FileTime lastModifiedTimeCert;
    private FileTime lastModifiedTimeKey;

    private com.linecorp.armeria.server.Server httpServer;

    private ScheduledExecutorService scheduledExecutorService;
    
    public HTTPServer(HTTPServerConfig config) {
        this.config = config;
    }

    @Override
    public void initialize() {
        // TODO replace prefix with real context path when Armeria supports it
        final String contextPath = StringUtils.stripEnd(config.getContextPath(), "/");
        sb = com.linecorp.armeria.server.Server
            .builder();
        normalInitialize(sb);
        if (config.isEnableTLS()) {
            handlers = new HashSet<>();
            sb.https(new InetSocketAddress(
                    config.getHost(),
                    config.getPort()));
            tlsCertChainPath = Paths.get(config.getTlsCertChainPath());
            tlsKeyPath = Paths.get(config.getTlsKeyPath());

            try {
                lastModifiedTimeCert = Files.getLastModifiedTime(tlsCertChainPath);
                lastModifiedTimeKey = Files.getLastModifiedTime(tlsKeyPath);
            } catch (IOException e) {
                log.error("Failed to get last modified time of TLS cert chain file and TLS key file", e);
            }
            try (InputStream cert = new FileInputStream(tlsCertChainPath.toFile());
                 InputStream key = PrivateKeyUtil.loadDecryptionKey(tlsKeyPath.toString())) {
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

        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                        .setNameFormat("HTTPServer" + "-%d")
                        .setDaemon(true)
                        .build());

        log.info("Server root context path: {}", contextPath);
    }

    private void normalInitialize(ServerBuilder sb) {
        final String contextPath = StringUtils.stripEnd(config.getContextPath(), "/");
        sb.serviceUnder(contextPath + "/docs", DocService.builder().build())
        .service("/internal/l7check", HealthCheckService.of())
        .workerGroup(config.getMaxThreads())
        .http1MaxHeaderSize(config.getMaxRequestHeaderSize())
        .idleTimeout(Duration.ofMillis(config.getIdleTimeOut()))
        .decorator(Route.ofCatchAll(), (delegate, ctx, req) -> {
            if (!allowedMethods.contains(ctx.method())) {
                return HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED);
            }
            return delegate.serve(ctx, req);
        })
        .decorator(LoggingService.newDecorator());
        if (config.getAcceptQueueSize() > 0) {
            sb.maxNumConnections(config.getAcceptQueueSize());
        }
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
        if (config.isEnableTLS()) {
            handlers.add(handler);
        }
        sb.annotatedService()
          .pathPrefix(config.getContextPath())
          .build(handler);
        this.allowedMethods.addAll(httpMethods);
    }

    public void updateCert() {
        FileTime lastModifiedTimeCertNow;
        FileTime lastModifiedTimeKeyNow;
        try {
            lastModifiedTimeCertNow = Files.getLastModifiedTime(tlsCertChainPath);
            lastModifiedTimeKeyNow = Files.getLastModifiedTime(tlsKeyPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (lastModifiedTimeCertNow.equals(lastModifiedTimeCert)
                && lastModifiedTimeKeyNow.equals(lastModifiedTimeKey)) {
            return;
        }
        log.info("TLS cert chain file or TLS key file has been updated, reloading...");
        httpServer.reconfigure(sb -> {
            try (InputStream cert = new FileInputStream(tlsCertChainPath.toFile());
                 InputStream key = PrivateKeyUtil.loadDecryptionKey(tlsKeyPath.toString())) {
                sb.tls(cert, key);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
            normalInitialize(sb);
            sb.annotatedService()
              .pathPrefix(config.getContextPath())
              .build(handlers.toArray());
        });
        lastModifiedTimeCert = lastModifiedTimeCertNow;
        lastModifiedTimeKey = lastModifiedTimeKeyNow;
    }

    @Override
    public void start() {
        httpServer = sb.build();
        httpServer.start().join();
        if (config.isEnableTLS()) {
            scheduledExecutorService.schedule(this::updateCert, 10, TimeUnit.SECONDS);
        }
    }
}
