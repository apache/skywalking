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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.client.Client;
import org.apache.skywalking.oap.server.library.client.healthcheck.DelegatedHealthChecker;
import org.apache.skywalking.oap.server.library.client.healthcheck.HealthCheckable;
import org.apache.skywalking.oap.server.library.util.HealthChecker;

@Slf4j
public class GRPCClient implements Client, HealthCheckable {
    @Getter
    private final String host;

    @Getter
    private final int port;

    private SslContext sslContext;

    private ManagedChannel channel;

    private final DelegatedHealthChecker healthChecker = new DelegatedHealthChecker();

    private ScheduledExecutorService healthCheckExecutor;

    private boolean enableHealthCheck = false;

    private long initialDelay = 5; // Initial delay for health check in seconds

    private long period = 20; // Period for health check in seconds

    // The default health check runnable that checks the health of the gRPC channel.
    private Runnable healthCheckRunnable = () -> {
        if (getChannel() != null && !getChannel().isShutdown()) {
            HealthGrpc.HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(getChannel());
            HealthCheckRequest request = HealthCheckRequest.newBuilder().setService("").build();
            try {
                HealthCheckResponse response = healthStub.check(request);
                handleStateChange(response);
            } catch (StatusRuntimeException s) {
                if (s.getStatus().getCode() == Status.Code.UNIMPLEMENTED) {
                    log.warn("Health check is not implemented on the remote gRPC server, regard as healthy. Host: {}, Port: {}", getHost(), getPort());
                    healthChecker.health();
                } else {
                    log.warn("Health check failed for gRPC channel. Host: {}, Port: {}", getHost(), getPort(), s);
                    healthChecker.unHealth(s);
                }
            } catch (Throwable t) {
                log.warn("Health check failed for gRPC channel. Host: {}, Port: {}", getHost(), getPort(), t);
                healthChecker.unHealth(t);
            }
        } else {
            healthChecker.unHealth("gRPC channel is not available or shutting down. Host: " + getHost() + ", Port: " + getPort());
        }
    };

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
            log.error(t.getMessage(), t);
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

    /**
     * Override the default health check runnable with a custom one.
     * Must override before calling connect()
     * This can be used to provide a different health check logic.
     *
     * @param healthCheckRunnable The custom health check runnable.
     * @param initialDelay Initial delay before the first health check.
     * @param period Period between subsequent health checks.
     */
    public void overrideCheckerRunnable(final Runnable healthCheckRunnable, final long initialDelay, final long period) {
        this.healthCheckRunnable = healthCheckRunnable;
        if (initialDelay < 0) {
            throw new IllegalArgumentException("initialDelay must be non-negative. Provided value: " + initialDelay);
        }
        if (period < 0) {
            throw new IllegalArgumentException("period must be non-negative. Provided value: " + period);
        }
        this.initialDelay = initialDelay;
        this.period = period;
    }

    private void checkHealth() {
        if (healthCheckExecutor == null) {
            healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
            healthCheckExecutor.scheduleAtFixedRate(healthCheckRunnable, initialDelay, period, TimeUnit.SECONDS
            );
        }
    }

    private void handleStateChange(HealthCheckResponse response) {
        switch (response.getStatus()) {
            case SERVING:
                this.healthChecker.health();
                break;
            case NOT_SERVING:
                this.healthChecker.unHealth("Remote gRPC Server NOT_SERVING. Host: " + host + ", Port: " + port);
                break;
            case SERVICE_UNKNOWN:
                this.healthChecker.unHealth("Remote gRPC Server SERVICE_UNKNOWN. Host: " + host + ", Port: " + port);
                break;
            case UNKNOWN:
                this.healthChecker.unHealth("Remote gRPC Server UNKNOWN. Host: " + host + ", Port: " + port);
                break;
            case UNRECOGNIZED:
                this.healthChecker.unHealth("Remote gRPC Server UNRECOGNIZED. Host: " + host + ", Port: " + port);
                break;
        }
    }
}
