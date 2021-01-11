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

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.server.tcp.TCPBinder;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.ZabbixMetrics;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.ZabbixModuleConfig;

@Slf4j
public class ZabbixTCPBinder implements TCPBinder {

    private final ZabbixModuleConfig config;
    private final ZabbixMetrics metrics;

    public ZabbixTCPBinder(ZabbixModuleConfig config, ZabbixMetrics metrics) {
        this.config = config;
        this.metrics = metrics;
    }

    @Override
    public int exportPort() {
        return config.getPort();
    }

    @Override
    public void initChannel(SocketChannel channel) {
        ChannelPipeline pipeline = channel.pipeline();

        // encoder and decoder
        pipeline.addLast(new ZabbixProtocolDataCodec());
        // handler
        pipeline.addLast(new ZabbixProtocolHandler(this.metrics));
    }

    @Override
    public void afterStarted() {
        log.info("Zabbix receiver started at port: {}", config.getPort());
    }
}
