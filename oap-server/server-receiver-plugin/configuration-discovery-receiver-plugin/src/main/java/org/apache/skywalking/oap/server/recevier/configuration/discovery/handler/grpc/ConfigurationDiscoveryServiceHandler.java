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
import org.apache.skywalking.apm.network.language.agent.v3.ConfigurationDiscoveryServiceGrpc;
import org.apache.skywalking.apm.network.language.agent.v3.ConfigurationSyncRequest;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.trace.component.command.ConfigurationDiscoveryCommand;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.recevier.configuration.discovery.AgentConfigurations;
import org.apache.skywalking.oap.server.recevier.configuration.discovery.AgentConfigurationsWatcher;

/**
 * Provide query agent dynamic configuration, through the gRPC protocol,
 */
@Slf4j
public class ConfigurationDiscoveryServiceHandler extends ConfigurationDiscoveryServiceGrpc.ConfigurationDiscoveryServiceImplBase implements GRPCHandler {

    private final AgentConfigurationsWatcher agentConfigurationsWatcher;

    /**
     * If the current configuration is true, the requestId and uuid will not be judged, and the dynamic configuration of
     * the service corresponding to the agent will be returned directly
     */
    private boolean disableMessageDigest = false;

    public ConfigurationDiscoveryServiceHandler(AgentConfigurationsWatcher agentConfigurationsWatcher,
                                                boolean disableMessageDigest) {
        this.agentConfigurationsWatcher = agentConfigurationsWatcher;
        this.disableMessageDigest = disableMessageDigest;
    }

    /*
     * Process the request for querying the dynamic configuration of the agent.
     * If there is agent dynamic configuration information corresponding to the service,
     * the ConfigurationDiscoveryCommand is returned to represent the dynamic configuration information.
     */
    @Override
    public void fetchConfigurations(final ConfigurationSyncRequest request,
                                    final StreamObserver<Commands> responseObserver) {
        Commands.Builder commandsBuilder = Commands.newBuilder();

        AgentConfigurations agentConfigurations = agentConfigurationsWatcher.getAgentConfigurations(
            request.getService());
        if (null != agentConfigurations) {
            if (disableMessageDigest || !Objects.equals(agentConfigurations.getUuid(), request.getUuid())) {
                ConfigurationDiscoveryCommand configurationDiscoveryCommand =
                    newAgentDynamicConfigCommand(agentConfigurations);
                commandsBuilder.addCommands(configurationDiscoveryCommand.serialize().build());
            }
        }
        responseObserver.onNext(commandsBuilder.build());
        responseObserver.onCompleted();
    }

    public ConfigurationDiscoveryCommand newAgentDynamicConfigCommand(AgentConfigurations agentConfigurations) {
        List<KeyStringValuePair> configurationList = Lists.newArrayList();
        agentConfigurations.getConfiguration().forEach((k, v) -> {
            KeyStringValuePair.Builder builder = KeyStringValuePair.newBuilder().setKey(k).setValue(v);
            configurationList.add(builder.build());
        });
        return new ConfigurationDiscoveryCommand(
            UUID.randomUUID().toString(), agentConfigurations.getUuid(), configurationList);
    }
}
