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

package org.apache.skywalking.oap.server.telemetry.prometheus.httpserver;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.server.ssl.HttpDynamicSslContext;
import org.apache.skywalking.oap.server.telemetry.prometheus.PrometheusConfig;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 */
@RequiredArgsConstructor
@Slf4j
public final class HttpServer {

    private final PrometheusConfig config;

    public void start() throws InterruptedException {
        // Configure SSL.
        final HttpDynamicSslContext sslCtx;
        if (config.isSslEnabled()) {
            sslCtx = HttpDynamicSslContext.forServer(config.getSslKeyPath(), config.getSslCertChainPath());
        } else {
            sslCtx = null;
        }

        // Configure the server.
        ThreadFactory tf = new ThreadFactoryBuilder().setDaemon(true).build();
        EventLoopGroup bossGroup = new NioEventLoopGroup(1, tf);
        EventLoopGroup workerGroup = new NioEventLoopGroup(0, tf);
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .handler(new LoggingHandler(LogLevel.INFO))
            .childHandler(new HttpServerInitializer(sslCtx));

        b.bind(config.getHost(), config.getPort()).sync();
        Optional.ofNullable(sslCtx).ifPresent(HttpDynamicSslContext::start);

        log.info("Prometheus exporter endpoint:" +
            (config.isSslEnabled() ? "https" : "http") + "://" + config.getHost() + ":" + config.getPort() + '/');
    }
}
