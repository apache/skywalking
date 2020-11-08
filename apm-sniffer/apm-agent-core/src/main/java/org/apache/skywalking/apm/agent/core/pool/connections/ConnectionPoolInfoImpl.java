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
 */

package org.apache.skywalking.apm.agent.core.pool.connections;

import com.google.common.util.concurrent.AtomicDouble;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.meter.Gauge;
import org.apache.skywalking.apm.agent.core.meter.Histogram;
import org.apache.skywalking.apm.agent.core.meter.MeterId;
import org.apache.skywalking.apm.agent.core.meter.MeterService;
import org.apache.skywalking.apm.agent.core.meter.MeterTag;
import org.apache.skywalking.apm.agent.core.meter.MeterType;

public class ConnectionPoolInfoImpl implements ConnectionPoolInfo {
    public static final String POOL_ID = "pool_id";
    public static final String CONNECTION_POOL_ACTIVE_COUNTS = "connection_pool_active_counts";
    public static final String CONNECTION_POOL_GET_CONNECTION_LATENCY = "connection_pool_get_connection_latency";
    public static final String CONNECTION_POOL_AWAITING_CONNECTION_THREAD_NUM = "connection_pool_awaiting_connection_thread_num";
    public static final String CONNECTION_POOL_GET_CONNECTION_FAILURE_RATE = "connection_pool_get_connection_failure_rate";
    private static final List<Double> STEPS = Arrays.asList(
        10d, 50d, 100d, 200d, 400d, 800d, 1000d, 1500d, 2000d, 2500d, 3000d, 4000d, 5000d, 10000d, 50000d,
        100000d, 500000d, 1000000d, 5000000d
    );

    private final String poolId;
    private final List<MeterId> meterIds = new ArrayList<>();
    private final PoolCapabilityMetricValueRecorder recorder;

    private AvgWindow awaitConnectionThreadNumberWindowA = new AvgWindow();
    private AvgWindow awaitConnectionThreadNumberWindowB = new AvgWindow();

    private AvgWindow activeCountWindowA = new AvgWindow();
    private AvgWindow activeCountWindowB = new AvgWindow();

    private Histogram getConnectionLatencyHistogram;
    private FailureRateSupplier failureRateSupplier;

    public ConnectionPoolInfoImpl(final String poolId, PoolCapabilityMetricValueRecorder recorder) {
        this.poolId = poolId;
        this.recorder = recorder;
    }

    @Override
    public void registerToMeterSystem(MeterService meterService) {
        this.meterIds.addAll(Arrays.asList(
            registerActiveCountMeter(meterService),
            registerAwaitConnectionThreadNumMeter(meterService),

            registerGetConnectionLatencyMeter(meterService),
            registerGetConnectionFailureRateMeter(meterService)
        ));
    }

    @Override
    public void unregisterFromMeterSystem() {
        this.meterIds.forEach(meterId -> ServiceManager.INSTANCE.findService(MeterService.class).unregister(meterId));
    }

    @Override
    public void recordPoolCapabilityMetricValue() throws ObjectHadBeenRecycledException {
        activeCountWindowB.increase(recorder.getActiveConnections());
        awaitConnectionThreadNumberWindowB.increase(recorder.getThreadsAwaitingConnection());
    }

    @Override
    public void recordGetConnectionTime(final long time) {
        this.getConnectionLatencyHistogram.addValue(time);
    }

    @Override
    public void recordGetConnectionStatue(final boolean failed) {
        this.failureRateSupplier.recordGetConnectionStatue(failed);
    }

    private MeterId registerAwaitConnectionThreadNumMeter(final MeterService meterService) {
        MeterId awaitConnectionThreadNumMeter = newAwaitConnectionThreadNumMeter(poolId);
        meterService.register(
            new Gauge(awaitConnectionThreadNumMeter, () -> {
                awaitConnectionThreadNumberWindowA = awaitConnectionThreadNumberWindowB;
                awaitConnectionThreadNumberWindowB = new AvgWindow();
                return awaitConnectionThreadNumberWindowA.getData();
            }));
        return awaitConnectionThreadNumMeter;
    }

    private MeterId registerActiveCountMeter(final MeterService meterService) {
        MeterId activeCountMeter = newActiveCountMeter(poolId);
        meterService.register(new Gauge(activeCountMeter, () -> {
            activeCountWindowA = activeCountWindowB;
            activeCountWindowB = new AvgWindow();
            return activeCountWindowA.getData();
        }));
        return activeCountMeter;
    }

    private MeterId registerGetConnectionLatencyMeter(final MeterService meterService) {
        MeterId getConnectionLatencyMeter = newGetConnectionLatencyMeter(poolId);
        this.getConnectionLatencyHistogram = new Histogram(getConnectionLatencyMeter, STEPS);
        meterService.register(this.getConnectionLatencyHistogram);
        return getConnectionLatencyMeter;
    }

    private MeterId registerGetConnectionFailureRateMeter(final MeterService meterService) {
        this.failureRateSupplier = new FailureRateSupplier();
        MeterId getConnectionFailureRateMeter = newGetConnectionFailureRate(poolId);
        meterService.register(new Gauge(getConnectionFailureRateMeter, failureRateSupplier));
        return getConnectionFailureRateMeter;
    }

    private MeterId newActiveCountMeter(String poolId) {
        return new MeterId(
            CONNECTION_POOL_ACTIVE_COUNTS, MeterType.GAUGE, Arrays.asList(new MeterTag(POOL_ID, poolId)));
    }

    private MeterId newAwaitConnectionThreadNumMeter(String poolId) {
        return new MeterId(
            CONNECTION_POOL_AWAITING_CONNECTION_THREAD_NUM, MeterType.GAUGE, Arrays.asList(
            new MeterTag(POOL_ID, poolId)
        ));
    }

    private MeterId newGetConnectionLatencyMeter(String poolId) {
        return new MeterId(
            CONNECTION_POOL_GET_CONNECTION_LATENCY, MeterType.HISTOGRAM, Arrays.asList(
            new MeterTag(POOL_ID, poolId)
        ));
    }

    private MeterId newGetConnectionFailureRate(String poolId) {
        return new MeterId(
            CONNECTION_POOL_GET_CONNECTION_FAILURE_RATE, MeterType.HISTOGRAM, Arrays.asList(
            new MeterTag(POOL_ID, poolId)
        ));
    }

    private class AvgWindow {
        private final AtomicDouble data;
        private final AtomicInteger count;

        public AvgWindow() {
            this.data = new AtomicDouble();
            this.count = new AtomicInteger();
        }

        public Double getData() {
            return data.get() / this.count.get();
        }

        private void increase(final Double data) {
            this.data.addAndGet(data);
            this.count.incrementAndGet();
        }
    }
}
