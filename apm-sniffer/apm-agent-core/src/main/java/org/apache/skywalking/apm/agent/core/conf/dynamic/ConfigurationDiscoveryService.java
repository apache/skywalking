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

package org.apache.skywalking.apm.agent.core.conf.dynamic;

import io.grpc.Channel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.commands.CommandService;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelListener;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelStatus;
import org.apache.skywalking.apm.network.agent.dynamic.configuration.v3.ConfigurationDiscoveryServiceGrpc;
import org.apache.skywalking.apm.network.agent.dynamic.configuration.v3.ConfigurationSyncRequest;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.trace.component.command.ConfigurationDiscoveryCommand;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.GRPC_UPSTREAM_TIMEOUT;

@DefaultImplementor
public class ConfigurationDiscoveryService implements BootService, GRPCChannelListener {

    /**
     * Uuid of the last return value.
     */
    private String uuid;
    private Register register = new Register();

    private volatile ScheduledFuture<?> getDynamicConfigurationFuture;
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;
    private volatile ConfigurationDiscoveryServiceGrpc.ConfigurationDiscoveryServiceBlockingStub configurationDiscoveryServiceBlockingStub;

    public static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n");
    private static final ILog LOGGER = LogManager.getLogger(ConfigurationDiscoveryService.class);

    @Override
    public void statusChanged(final GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            configurationDiscoveryServiceBlockingStub = ConfigurationDiscoveryServiceGrpc.newBlockingStub(channel);
        } else {
            configurationDiscoveryServiceBlockingStub = null;
        }
        this.status = status;
    }

    @Override
    public void prepare() throws Throwable {
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() throws Throwable {
        getDynamicConfigurationFuture = Executors.newSingleThreadScheduledExecutor(
            new DefaultNamedThreadFactory("ConfigurationDiscoveryService")
        ).scheduleAtFixedRate(
            new RunnableWithExceptionProtection(
                this::getAgentDynamicConfig,
                t -> LOGGER.error("Sync config from OAP error.", t)
            ),
            Config.Collector.GET_AGENT_DYNAMIC_CONFIG_INTERVAL,
            Config.Collector.GET_AGENT_DYNAMIC_CONFIG_INTERVAL, 
            TimeUnit.SECONDS
        );
    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        if (getDynamicConfigurationFuture != null) {
            getDynamicConfigurationFuture.cancel(true);
        }
    }

    /**
     * Register dynamic configuration watcher.
     *
     * @param watcher dynamic configuration watcher
     */
    public void registerAgentConfigChangeWatcher(AgentConfigChangeWatcher watcher) {
        WatcherHolder holder = new WatcherHolder(watcher);
        if (register.containsKey(holder.getKey())) {
            throw new IllegalStateException("Duplicate register, watcher=" + watcher);
        }
        register.put(holder.getKey(), holder);
    }

    /**
     * Process ConfigurationDiscoveryCommand and notify each configuration watcher.
     *
     * @param configurationDiscoveryCommand Describe dynamic configuration information
     */
    public void handConfigurationDiscoveryCommand(ConfigurationDiscoveryCommand configurationDiscoveryCommand) {
        final String responseUuid = configurationDiscoveryCommand.getUuid();
        final List<KeyStringValuePair> config = configurationDiscoveryCommand.getConfig();

        if (responseUuid != null && Objects.equals(this.uuid, responseUuid)) {
            return;
        }

        config.forEach(item -> {
            String itemName = item.getKey();
            WatcherHolder holder = register.get(itemName);
            if (holder != null) {
                AgentConfigChangeWatcher watcher = holder.getWatcher();
                String newItemValue = item.getValue();
                if (newItemValue == null) {
                    if (watcher.value() != null) {
                        // Notify watcher, the new value is null with delete event type.
                        watcher.notify(
                            new AgentConfigChangeWatcher.ConfigChangeEvent(
                                null, AgentConfigChangeWatcher.EventType.DELETE
                            ));
                    } else {
                        // Don't need to notify, stay in null.
                    }
                } else {
                    if (!newItemValue.equals(watcher.value())) {
                        watcher.notify(new AgentConfigChangeWatcher.ConfigChangeEvent(
                            newItemValue, AgentConfigChangeWatcher.EventType.MODIFY
                        ));
                    } else {
                        // Don't need to notify, stay in the same config value.
                    }
                }
            } else {
                LOGGER.warn("Config {} from OAP, doesn't match any watcher, ignore.", itemName);
            }
        });
        this.uuid = responseUuid;

        LOGGER.trace("Current configurations after the sync." + LINE_SEPARATOR + register.toString());
    }

    /**
     * get agent dynamic config through gRPC.
     */
    private void getAgentDynamicConfig() {
        LOGGER.debug("ConfigurationDiscoveryService running, status:{}.", status);

        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            try {
                ConfigurationSyncRequest.Builder builder = ConfigurationSyncRequest.newBuilder();
                builder.setService(Config.Agent.SERVICE_NAME);
                if (null != uuid) {
                    builder.setUuid(uuid);
                }

                if (configurationDiscoveryServiceBlockingStub != null) {
                    final Commands commands = configurationDiscoveryServiceBlockingStub.withDeadlineAfter(
                        GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS
                    ).fetchConfigurations(builder.build());
                    ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);
                }
            } catch (Throwable t) {
                LOGGER.error(t, "ConfigurationDiscoveryService execute fail.");
                ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(t);
            }
        }
    }

    /**
     * Local dynamic configuration center.
     */
    public class Register {
        private Map<String, WatcherHolder> register = new HashMap<>();

        private boolean containsKey(String key) {
            return register.containsKey(key);
        }

        private void put(String key, WatcherHolder holder) {
            register.put(key, holder);
        }

        public WatcherHolder get(String name) {
            return register.get(name);
        }

        @Override
        public String toString() {
            StringBuilder registerTableDescription = new StringBuilder();
            registerTableDescription.append("Following dynamic config items are available.").append(LINE_SEPARATOR);
            registerTableDescription.append("---------------------------------------------").append(LINE_SEPARATOR);
            register.forEach((key, holder) -> {
                AgentConfigChangeWatcher watcher = holder.getWatcher();
                registerTableDescription.append("key:")
                                        .append(key)
                                        .append("    value(current):")
                                        .append(watcher.value())
                                        .append(LINE_SEPARATOR);
            });
            return registerTableDescription.toString();
        }
    }

    @Getter
    private class WatcherHolder {
        private AgentConfigChangeWatcher watcher;
        private final String key;

        public WatcherHolder(AgentConfigChangeWatcher watcher) {
            this.watcher = watcher;
            this.key = watcher.getItemName();
        }
    }
}
