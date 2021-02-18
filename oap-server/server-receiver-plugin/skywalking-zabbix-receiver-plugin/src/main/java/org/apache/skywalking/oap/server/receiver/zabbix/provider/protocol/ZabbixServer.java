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

package org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.ZabbixMetrics;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.ZabbixModuleConfig;

@Slf4j
public class ZabbixServer {
    private final ZabbixModuleConfig config;
    private final ZabbixMetrics metrics;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private Channel serverChannel;

    public ZabbixServer(final ZabbixModuleConfig config,
                        final ZabbixMetrics metrics) {
        this.config = config;
        this.metrics = metrics;
    }

    /**
     * Start zabbix receive server
     */
    public void start() throws Exception {
        this.bossGroup = new NioEventLoopGroup(1, new ThreadFactoryBuilder()
            .setDaemon(true).setNameFormat("TCP-BOSS-THREAD-%d").build());
        this.workerGroup = new NioEventLoopGroup(1, new ThreadFactoryBuilder()
            .setDaemon(true).setNameFormat("TCP-WORKER-THREAD-%d").build());

        ServerBootstrap bootstrap = new ServerBootstrap()
            // All server using same group
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .handler(new LoggingHandler(LogLevel.INFO))
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) throws Exception {
                    ZabbixServer.this.initChannel(channel);
                }
            });

        serverChannel = bootstrap.bind(config.getHost(), config.getPort()).sync().channel();
        log.info("Zabbix receiver started at port: {}", config.getPort());
    }

    protected void initChannel(SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();

        // encoder and decoder
        pipeline.addLast(new ZabbixProtocolDataCodec());
        // handler
        pipeline.addLast(new ZabbixProtocolHandler(metrics));
    }

    /**
     * Stop zabbix receive server
     */
    public void stop() {
        serverChannel.close().syncUninterruptibly();
    }
}
