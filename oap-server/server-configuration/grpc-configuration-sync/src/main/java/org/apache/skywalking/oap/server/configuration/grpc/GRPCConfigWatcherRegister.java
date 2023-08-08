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

package org.apache.skywalking.oap.server.configuration.grpc;

import io.grpc.netty.NettyChannelBuilder;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.FetchingConfigWatcherRegister;
import org.apache.skywalking.oap.server.configuration.api.GroupConfigTable;
import org.apache.skywalking.oap.server.configuration.service.ConfigurationRequest;
import org.apache.skywalking.oap.server.configuration.service.ConfigurationResponse;
import org.apache.skywalking.oap.server.configuration.service.ConfigurationServiceGrpc;
import org.apache.skywalking.oap.server.configuration.service.GroupConfigurationResponse;

@Slf4j
public class GRPCConfigWatcherRegister extends FetchingConfigWatcherRegister {
    private RemoteEndpointSettings settings;
    private ConfigurationServiceGrpc.ConfigurationServiceBlockingStub stub;
    private String uuid = null;
    private String groupUuid = null;

    public GRPCConfigWatcherRegister(RemoteEndpointSettings settings) {
        super(settings.getPeriod());
        this.settings = settings;
        stub = ConfigurationServiceGrpc.newBlockingStub(
            NettyChannelBuilder.forAddress(settings.getHost(), settings.getPort())
                               .usePlaintext()
                               .build());
    }

    @Override
    public Optional<ConfigTable> readConfig(Set<String> keys) {
        ConfigTable table = new ConfigTable();
        try {
            ConfigurationRequest.Builder builder = ConfigurationRequest.newBuilder()
                                                                       .setClusterName(settings.getClusterName());
            if (uuid != null) {
                builder.setUuid(uuid);
            }
            ConfigurationResponse response = stub.call(builder.build());
            String responseUuid = response.getUuid();
            if (Objects.equals(uuid, responseUuid)) {
                // If UUID matched, the config table is expected as empty.
                return Optional.empty();
            }
            response.getConfigTableList().forEach(config -> {
                final String name = config.getName();
                if (keys.contains(name)) {
                    table.add(new ConfigTable.ConfigItem(name, config.getValue()));
                }
            });
            this.uuid = responseUuid;
        } catch (Exception e) {
            log.error("Remote config center [{}] is not available.", settings, e);
        }
        return Optional.of(table);
    }

    @Override
    public Optional<GroupConfigTable> readGroupConfig(final Set<String> keys) {
        GroupConfigTable groupConfigTable = new GroupConfigTable();
        try {
            ConfigurationRequest.Builder builder = ConfigurationRequest.newBuilder()
                                                                       .setClusterName(settings.getClusterName());
            if (groupUuid != null) {
                builder.setUuid(groupUuid);
            }
            GroupConfigurationResponse response = stub.callGroup(builder.build());
            String responseUuid = response.getUuid();
            if (Objects.equals(groupUuid, responseUuid)) {
                // If UUID matched, the config table is expected as empty.
                return Optional.empty();
            }

            response.getGroupConfigTableList().forEach(rspGroupConfigItems -> {
                String groupName = rspGroupConfigItems.getGroupName();
                if (keys.contains(groupName)) {
                    GroupConfigTable.GroupConfigItems groupConfigItems = new GroupConfigTable.GroupConfigItems(
                        groupName);
                    groupConfigTable.addGroupConfigItems(groupConfigItems);
                    rspGroupConfigItems.getItemsList().forEach(item -> {
                        groupConfigItems.add(new ConfigTable.ConfigItem(item.getName(), item.getValue()));
                    });
                }
            });
            this.groupUuid = responseUuid;
        } catch (Exception e) {
            log.error("Remote config center [{}] is not available.", settings, e);
        }
        return Optional.of(groupConfigTable);
    }
}
