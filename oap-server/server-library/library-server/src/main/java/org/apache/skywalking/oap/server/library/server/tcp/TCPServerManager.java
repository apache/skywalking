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

package org.apache.skywalking.oap.server.library.server.tcp;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manager all the tcp servers.
 */
@Slf4j
public class TCPServerManager {

    private final String host;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final List<Server> servers = new ArrayList<>();

    public TCPServerManager(String host, int bossGroupCount, int workerGroupCount) {
        this.host = host;

        this.bossGroup = new NioEventLoopGroup(bossGroupCount, new ThreadFactoryBuilder()
            .setDaemon(true).setNameFormat("TCP-BOSS-THREAD-%d").build());
        this.workerGroup = new NioEventLoopGroup(workerGroupCount, new ThreadFactoryBuilder()
            .setDaemon(true).setNameFormat("TCP-WORKER-THREAD-%d").build());
    }

    /**
     * Add new binder and save to {@link #servers}
     */
    public void addBinder(TCPBinder binder) {
        ServerBootstrap bootstrap = new ServerBootstrap()
            // All server using same group
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .handler(new LoggingHandler(LogLevel.INFO))
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) throws Exception {
                    binder.initChannel(channel);
                }
            });

        servers.add(new Server(binder, bootstrap));
    }

    /**
     * Sync startup all TCP server
     */
    public void startAllServer() throws TCPServerException {
        // Only start once
        if (!started.compareAndSet(false, true)) {
            return;
        }

        if (CollectionUtils.isEmpty(servers)) {
            return;
        }

        // Bind all TCP server
        for (Server server : servers) {
            try {
                server.bind();
            } catch (Exception e) {
                throw new TCPServerException("Starting TCP port " + server.binder.exportPort() + " failed", e);
            }
        }
    }

    private class Server {
        private final TCPBinder binder;
        private final ServerBootstrap bootstrap;

        public Server(TCPBinder binder, ServerBootstrap bootstrap) {
            this.binder = binder;
            this.bootstrap = bootstrap;
        }

        public void bind() throws Exception {
            bootstrap.bind(host, binder.exportPort()).sync();
            // Notify bind finished
            binder.afterStarted();
        }
    }
}
