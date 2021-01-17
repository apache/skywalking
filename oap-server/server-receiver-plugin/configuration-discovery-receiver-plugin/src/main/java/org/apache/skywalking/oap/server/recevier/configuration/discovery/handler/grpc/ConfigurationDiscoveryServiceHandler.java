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

package org.apache.skywalking.oap.server.recevier.configuration.discovery.handler.grpc;

import com.google.common.collect.Lists;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.agent.dynamic.configuration.v3.ConfigurationDiscoveryServiceGrpc;
import org.apache.skywalking.apm.network.agent.dynamic.configuration.v3.ConfigurationSyncRequest;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.trace.component.command.ConfigurationDiscoveryCommand;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.recevier.configuration.discovery.ConfigurationDiscoveryService;
import org.apache.skywalking.oap.server.recevier.configuration.discovery.ServiceConfiguration;

@Slf4j
public class ConfigurationDiscoveryServiceHandler extends ConfigurationDiscoveryServiceGrpc.ConfigurationDiscoveryServiceImplBase implements GRPCHandler {

    private final ConfigurationDiscoveryService configurationDiscoveryService;

    public ConfigurationDiscoveryServiceHandler(ConfigurationDiscoveryService configurationDiscoveryService) {
        this.configurationDiscoveryService = configurationDiscoveryService;
    }

    @Override
    public void fetchConfigurations(final ConfigurationSyncRequest request,
                                    final StreamObserver<Commands> responseObserver) {
        Commands.Builder commandsBuilder = Commands.newBuilder();

        ServiceConfiguration serviceDynamicConfig =
            configurationDiscoveryService.findServiceDynamicConfig(request.getService());
        if (null != serviceDynamicConfig) {
            ConfigurationDiscoveryCommand configurationDiscoveryCommand =
                newAgentDynamicConfigCommand(serviceDynamicConfig, request.getUuid());
            commandsBuilder.addCommands(configurationDiscoveryCommand.serialize().build());
        }
        responseObserver.onNext(commandsBuilder.build());
        responseObserver.onCompleted();
    }

    public ConfigurationDiscoveryCommand newAgentDynamicConfigCommand(ServiceConfiguration serviceConfiguration,
                                                                      String uuid) {
        List<KeyStringValuePair> configurationList = Lists.newArrayList();
        String hashCode = String.valueOf(serviceConfiguration.getConfiguration().hashCode());

        if (!Objects.equals(uuid, hashCode)) {
            serviceConfiguration.getConfiguration().forEach((k, v) -> {
                KeyStringValuePair.Builder builder = KeyStringValuePair.newBuilder().setKey(k).setValue(v);
                configurationList.add(builder.build());
            });
        }
        String serialNumber = UUID.randomUUID().toString();
        return new ConfigurationDiscoveryCommand(serialNumber, hashCode, configurationList);
    }
}
