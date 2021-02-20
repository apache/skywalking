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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.ZabbixMetrics;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.bean.ZabbixProtocolType;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.bean.ZabbixRequest;
import org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.bean.ZabbixResponse;

import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handle request on received the Zabbix data
 */
@Slf4j
public class ZabbixProtocolHandler extends SimpleChannelInboundHandler<ZabbixRequest> {

    private final ZabbixMetrics metrics;

    public ZabbixProtocolHandler(ZabbixMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, ZabbixRequest msg) {
        if (msg.getType() == ZabbixProtocolType.ACTIVE_CHECKS) {
            ZabbixResponse response = new ZabbixResponse();
            response.setType(msg.getType());
            String hostName = msg.getActiveChecks().getHostName();

            // Get all active tasks
            response.setActiveChecks(Optional.of(metrics.getAllMonitorMetricNames(hostName))
                .map(s -> s.stream().map(key ->
                    ZabbixResponse.ActiveChecks.builder().delay(60).lastlogsize(0).key(key).mtime(0).build()
                ).collect(Collectors.toList())).orElse(Collections.emptyList()));

            ctx.writeAndFlush(response);
        } else {
            ZabbixResponse response = new ZabbixResponse();
            response.setType(msg.getType());

            // Convert metrics to the meter system
            ZabbixMetrics.ConvertStatics convertStatics;
            try {
                convertStatics = metrics.convertMetrics(msg.getAgentDataList());
            } catch (Exception e) {
                log.warn("Convert the Zabbix metrics error", e);
                convertStatics = ZabbixMetrics.ConvertStatics.builder().total(1).failed(1).build();
            }

            response.setAgentData(ZabbixResponse.AgentData.builder()
                .info(String.format("processed: %d; failed: %d; total: %d; seconds spent: %f",
                    convertStatics.getSuccess(),
                    convertStatics.getFailed(),
                    convertStatics.getTotal(),
                    convertStatics.getUseTime()))
                .build());

            ctx.writeAndFlush(response);
        }
    }
}
