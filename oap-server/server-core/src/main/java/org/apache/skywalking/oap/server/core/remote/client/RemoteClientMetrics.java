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

package org.apache.skywalking.oap.server.core.remote.client;

import java.util.Objects;
import lombok.Getter;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.GaugeMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/** Client-side {@code remote_out_*} metrics for cluster remote RPC. */
public final class RemoteClientMetrics {
    @Getter
    private final CounterMetrics outCounter;
    private final HistogramMetrics outLatency;
    @Getter
    private final CounterMetrics errorCounter;
    private final GaugeMetrics streamInflight;
    private final CounterMetrics streamBlockSeconds;
    private final GaugeMetrics queueUsedPercentage;

    public static RemoteClientMetrics forGrpc(final MetricsCreator metricsCreator, final Address address) {
        return new RemoteClientMetrics(metricsCreator, address, false, true);
    }

    public static RemoteClientMetrics forSelf(final MetricsCreator metricsCreator, final Address address) {
        return new RemoteClientMetrics(metricsCreator, address, true, false);
    }

    private RemoteClientMetrics(final MetricsCreator metricsCreator,
                                final Address address,
                                final boolean self,
                                final boolean trackStreamAndQueue) {
        Objects.requireNonNull(metricsCreator, "metricsCreator");
        final String dest = Objects.requireNonNull(address, "address").toString();
        final String selfTag = self ? "Y" : "N";

        this.outCounter = metricsCreator.createCounter(
            "remote_out_count",
            "The number(client side) of inside remote inside aggregate rpc.",
            new MetricsTag.Keys("dest", "self"),
            new MetricsTag.Values(dest, selfTag)
        );
        this.outLatency = metricsCreator.createHistogramMetric(
            "remote_out_latency",
            "The latency(client side) of inside remote inside aggregate rpc.",
            new MetricsTag.Keys("dest", "self"),
            new MetricsTag.Values(dest, selfTag)
        );
        this.errorCounter = metricsCreator.createCounter(
            "remote_out_error_count",
            "The error number(client side) of inside remote inside aggregate rpc.",
            new MetricsTag.Keys("dest", "self"),
            new MetricsTag.Values(dest, selfTag)
        );
        this.errorCounter.inc(0);
        if (trackStreamAndQueue) {
            this.streamInflight = metricsCreator.createGauge(
                "remote_out_stream_inflight",
                "In-flight gRPC streams for one remote client.",
                new MetricsTag.Keys("dest", "self"),
                new MetricsTag.Values(dest, selfTag)
            );
            this.streamBlockSeconds = metricsCreator.createCounter(
                "remote_out_stream_block_seconds",
                "Seconds spent waiting for a free remote gRPC stream slot.",
                new MetricsTag.Keys("dest", "self"),
                new MetricsTag.Values(dest, selfTag)
            );
            this.streamBlockSeconds.inc(0);
            this.queueUsedPercentage = metricsCreator.createGauge(
                "remote_out_queue_used_percentage",
                "Send queue used percentage for one remote client.",
                new MetricsTag.Keys("dest", "self"),
                new MetricsTag.Values(dest, selfTag)
            );
        } else {
            this.streamInflight = null;
            this.streamBlockSeconds = null;
            this.queueUsedPercentage = null;
        }
    }

    public void observeLatencySeconds(final double seconds) {
        outLatency.observe(Math.max(0, seconds));
    }

    public void setStreamInflight(final int value) {
        if (streamInflight != null) {
            streamInflight.setValue(Math.max(0, value));
        }
    }

    public void addStreamBlockSeconds(final double seconds) {
        if (streamBlockSeconds != null && seconds > 0) {
            streamBlockSeconds.inc(seconds);
        }
    }

    public void setQueueUsedPercentage(final double percentage) {
        if (queueUsedPercentage != null) {
            queueUsedPercentage.setValue(Math.max(0, Math.min(100, percentage)));
        }
    }

    public void clearPressureGauges() {
        setStreamInflight(0);
        setQueueUsedPercentage(0);
    }
}
