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

package org.apache.skywalking.apm.agent.core.remote;

import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

import static org.apache.skywalking.apm.agent.core.conf.Config.Collector.IS_RESOLVE_DNS_PERIODICALLY;

@DefaultImplementor
public class GRPCChannelManager implements BootService, Runnable {
    private static final ILog LOGGER = LogManager.getLogger(GRPCChannelManager.class);

    private volatile GRPCChannel managedChannel = null;
    private volatile ScheduledFuture<?> connectCheckFuture;
    private volatile boolean reconnect = true;
    private final Random random = new Random();
    private final List<GRPCChannelListener> listeners = Collections.synchronizedList(new LinkedList<>());
    private volatile List<String> grpcServers;
    private volatile int selectedIdx = -1;
    private volatile int reconnectCount = 0;

    @Override
    public void prepare() {

    }

    @Override
    public void boot() {
        if (Config.Collector.BACKEND_SERVICE.trim().length() == 0) {
            LOGGER.error("Collector server addresses are not set.");
            LOGGER.error("Agent will not uplink any data.");
            return;
        }
        grpcServers = Arrays.asList(Config.Collector.BACKEND_SERVICE.split(","));
        connectCheckFuture = Executors.newSingleThreadScheduledExecutor(
            new DefaultNamedThreadFactory("GRPCChannelManager")
        ).scheduleAtFixedRate(
            new RunnableWithExceptionProtection(
                this,
                t -> LOGGER.error("unexpected exception.", t)
            ), 0, Config.Collector.GRPC_CHANNEL_CHECK_INTERVAL, TimeUnit.SECONDS
        );
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void shutdown() {
        if (connectCheckFuture != null) {
            connectCheckFuture.cancel(true);
        }
        if (managedChannel != null) {
            managedChannel.shutdownNow();
        }
        LOGGER.debug("Selected collector grpc service shutdown.");
    }

    @Override
    public void run() {
        LOGGER.debug("Selected collector grpc service running, reconnect:{}.", reconnect);
        if (IS_RESOLVE_DNS_PERIODICALLY && reconnect) {
            String backendService = Config.Collector.BACKEND_SERVICE.split(",")[0];
            try {
                String[] domainAndPort = backendService.split(":");

                List<String> newGrpcServers = Arrays
                        .stream(InetAddress.getAllByName(domainAndPort[0]))
                        .map(InetAddress::getHostAddress)
                        .map(ip -> String.format("%s:%s", ip, domainAndPort[1]))
                        .collect(Collectors.toList());

                grpcServers = newGrpcServers;
            } catch (Throwable t) {
                LOGGER.error(t, "Failed to resolve {} of backend service.", backendService);
            }
        }

        if (reconnect) {
            if (grpcServers.size() > 0) {
                String server = "";
                try {
                    int index = Math.abs(random.nextInt()) % grpcServers.size();
                    if (index != selectedIdx) {
                        selectedIdx = index;

                        server = grpcServers.get(index);
                        String[] ipAndPort = server.split(":");

                        if (managedChannel != null) {
                            managedChannel.shutdownNow();
                        }

                        managedChannel = GRPCChannel.newBuilder(ipAndPort[0], Integer.parseInt(ipAndPort[1]))
                                                    .addManagedChannelBuilder(new StandardChannelBuilder())
                                                    .addManagedChannelBuilder(new TLSChannelBuilder())
                                                    .addChannelDecorator(new AgentIDDecorator())
                                                    .addChannelDecorator(new AuthenticationDecorator())
                                                    .build();
                        notify(GRPCChannelStatus.CONNECTED);
                        reconnectCount = 0;
                        reconnect = false;
                    } else if (managedChannel.isConnected(++reconnectCount > Config.Agent.FORCE_RECONNECTION_PERIOD)) {
                        // Reconnect to the same server is automatically done by GRPC,
                        // therefore we are responsible to check the connectivity and
                        // set the state and notify listeners
                        reconnectCount = 0;
                        notify(GRPCChannelStatus.CONNECTED);
                        reconnect = false;
                    }

                    return;
                } catch (Throwable t) {
                    LOGGER.error(t, "Create channel to {} fail.", server);
                }
            }

            LOGGER.debug(
                "Selected collector grpc service is not available. Wait {} seconds to retry",
                Config.Collector.GRPC_CHANNEL_CHECK_INTERVAL
            );
        }
    }

    public void addChannelListener(GRPCChannelListener listener) {
        listeners.add(listener);
    }

    public Channel getChannel() {
        return managedChannel.getChannel();
    }

    /**
     * If the given expcetion is triggered by network problem, connect in background.
     */
    public void reportError(Throwable throwable) {
        if (isNetworkError(throwable)) {
            reconnect = true;
            notify(GRPCChannelStatus.DISCONNECT);
        }
    }

    private void notify(GRPCChannelStatus status) {
        for (GRPCChannelListener listener : listeners) {
            try {
                listener.statusChanged(status);
            } catch (Throwable t) {
                LOGGER.error(t, "Fail to notify {} about channel connected.", listener.getClass().getName());
            }
        }
    }

    private boolean isNetworkError(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException) {
            StatusRuntimeException statusRuntimeException = (StatusRuntimeException) throwable;
            return statusEquals(
                statusRuntimeException.getStatus(), Status.UNAVAILABLE, Status.PERMISSION_DENIED,
                Status.UNAUTHENTICATED, Status.RESOURCE_EXHAUSTED, Status.UNKNOWN
            );
        }
        return false;
    }

    private boolean statusEquals(Status sourceStatus, Status... potentialStatus) {
        for (Status status : potentialStatus) {
            if (sourceStatus.getCode() == status.getCode()) {
                return true;
            }
        }
        return false;
    }
}
