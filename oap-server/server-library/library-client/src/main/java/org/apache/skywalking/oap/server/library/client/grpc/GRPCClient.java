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

package org.apache.skywalking.oap.server.library.client.grpc;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.healthcheck.DelegatedHealthChecker;
import org.apache.skywalking.oap.server.library.client.healthcheck.HealthCheckable;
import org.apache.skywalking.oap.server.library.util.HealthChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GRPCClient implements Client, HealthCheckable {

    private static final Logger LOGGER = LoggerFactory.getLogger(GRPCClient.class);

    @Getter
    private final String host;

    @Getter
    private final int port;

    private SslContext sslContext;

    private ManagedChannel channel;

    private final DelegatedHealthChecker healthChecker = new DelegatedHealthChecker();

    private ScheduledExecutorService healthCheckExecutor;

    private boolean enableHealthCheck = false;

    public GRPCClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public GRPCClient(String host, int port, final SslContext sslContext) {
        this(host, port);
        this.sslContext = sslContext;
    }

    @Override
    public void connect() {
        if (sslContext == null) {
            channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        } else {
            channel = NettyChannelBuilder.forAddress(host, port).sslContext(sslContext).build();
        }
        if (enableHealthCheck) {
            checkHealth();
        }
    }

    @Override
    public void shutdown() {
        try {
            channel.shutdownNow();
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
        } finally {
            if (healthCheckExecutor != null) {
                healthCheckExecutor.shutdownNow();
                healthChecker.unHealth("gRPC channel is shutting down. Host: " + host + ", Port: " + port);
                healthCheckExecutor = null;
            }
        }
    }

    public ManagedChannel getChannel() {
        return channel;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }

    /**
     * Must register a HealthChecker before calling connect() if you want to enable health check.
     * If the channel is shutdown by client side, the health check will not be performed.
     * Note: If you register a `org.apache.skywalking.oap.server.telemetry.api.HealthCheckMetrics` here
     * and the metric name start with `org.apache.skywalking.oap.server.telemetry.api.MetricsCreator.HEALTH_METRIC_PREFIX`,
     * this healthy status will be included in the whole OAP health evaluate.
     * @param healthChecker HealthChecker to be registered.
     */
    @Override
    public void registerChecker(final HealthChecker healthChecker) {
        this.healthChecker.register(healthChecker);
        this.enableHealthCheck = true;
    }

    private void checkHealth() {
        if (healthCheckExecutor == null) {
            healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
            healthCheckExecutor.scheduleAtFixedRate(
                () -> {
                    ConnectivityState currentState = channel.getState(true); // true means try to connect
                    handleStateChange(currentState);
                }, 5, 10, TimeUnit.SECONDS
            );
        }
    }

    private void handleStateChange(ConnectivityState newState) {
        switch (newState) {
            case READY:
            case IDLE:
                this.healthChecker.health();
                break;
            case CONNECTING:
                this.healthChecker.unHealth("gRPC connecting, waiting for ready. Host: " + host + ", Port: " + port);
                break;
            case TRANSIENT_FAILURE:
                this.healthChecker.unHealth("gRPC connection failed, will retry. Host: " + host + ", Port: " + port);
                break;
            case SHUTDOWN:
                this.healthChecker.unHealth("gRPC channel is shutting down. Host: " + host + ", Port: " + port);
                break;
        }
    }
}
