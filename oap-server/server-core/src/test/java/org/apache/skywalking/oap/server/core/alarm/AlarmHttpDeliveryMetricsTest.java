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

package org.apache.skywalking.oap.server.core.alarm;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.GaugeMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AlarmHttpDeliveryMetricsTest {

    @Test
    public void resultOfGrpcErrorMapsStatusCodes() {
        assertEquals(
            AlarmHttpDeliveryMetrics.RESULT_TIMEOUT,
            AlarmHttpDeliveryMetrics.resultOfGrpcError(new StatusRuntimeException(Status.DEADLINE_EXCEEDED))
        );
        assertEquals(
            AlarmHttpDeliveryMetrics.RESULT_IO_ERROR,
            AlarmHttpDeliveryMetrics.resultOfGrpcError(new StatusRuntimeException(Status.UNAVAILABLE))
        );
        assertEquals(
            AlarmHttpDeliveryMetrics.RESULT_REJECTED,
            AlarmHttpDeliveryMetrics.resultOfGrpcError(new StatusRuntimeException(Status.CANCELLED))
        );
        assertEquals(
            AlarmHttpDeliveryMetrics.RESULT_SERVER_ERROR,
            AlarmHttpDeliveryMetrics.resultOfGrpcError(new StatusRuntimeException(Status.INTERNAL))
        );
        assertEquals(
            AlarmHttpDeliveryMetrics.RESULT_SERVER_ERROR,
            AlarmHttpDeliveryMetrics.resultOfGrpcError(new RuntimeException("boom"))
        );
        assertEquals(
            AlarmHttpDeliveryMetrics.RESULT_TIMEOUT,
            AlarmHttpDeliveryMetrics.resultOfGrpcError(new StatusException(Status.DEADLINE_EXCEEDED))
        );
        assertEquals(
            AlarmHttpDeliveryMetrics.RESULT_IO_ERROR,
            AlarmHttpDeliveryMetrics.resultOfGrpcError(new RuntimeException("wrap", new StatusException(Status.UNAVAILABLE)))
        );
    }

    @Test
    public void classifyGrpcDeliveryResultPrefersInterrupt() {
        final StatusRuntimeException deadline =
            new StatusRuntimeException(Status.DEADLINE_EXCEEDED.withDescription("deadline"));
        assertEquals(
            AlarmHttpDeliveryMetrics.RESULT_INTERRUPTED,
            AlarmHttpDeliveryMetrics.classifyGrpcDeliveryResult(true, deadline)
        );
        assertEquals(
            AlarmHttpDeliveryMetrics.RESULT_TIMEOUT,
            AlarmHttpDeliveryMetrics.classifyGrpcDeliveryResult(false, deadline)
        );
        assertEquals(
            AlarmHttpDeliveryMetrics.RESULT_SUCCESS,
            AlarmHttpDeliveryMetrics.classifyGrpcDeliveryResult(false, null)
        );
        assertEquals(
            AlarmHttpDeliveryMetrics.RESULT_REJECTED,
            AlarmHttpDeliveryMetrics.classifyGrpcDeliveryResult(
                false, new StatusRuntimeException(Status.CANCELLED)
            )
        );
    }

    @Test
    public void statusClassOfMapsHttpRanges() {
        assertEquals(AlarmHttpDeliveryMetrics.STATUS_2XX, AlarmHttpDeliveryMetrics.statusClassOf(204));
        assertEquals(AlarmHttpDeliveryMetrics.STATUS_3XX, AlarmHttpDeliveryMetrics.statusClassOf(302));
        assertEquals(AlarmHttpDeliveryMetrics.STATUS_4XX, AlarmHttpDeliveryMetrics.statusClassOf(429));
        assertEquals(AlarmHttpDeliveryMetrics.STATUS_5XX, AlarmHttpDeliveryMetrics.statusClassOf(500));
        assertEquals(AlarmHttpDeliveryMetrics.STATUS_OTHER, AlarmHttpDeliveryMetrics.statusClassOf(100));
    }

    @Test
    public void recordIncrementsCountersAndObservesLatency() {
        final RecordingCreator creator = new RecordingCreator();
        final AlarmHttpDeliveryMetrics metrics = new AlarmHttpDeliveryMetrics(creator);

        metrics.record("webhook", AlarmHttpDeliveryMetrics.RESULT_SUCCESS, AlarmHttpDeliveryMetrics.STATUS_2XX, 0.12);
        metrics.record(
            "webhook", AlarmHttpDeliveryMetrics.RESULT_SERVER_ERROR, AlarmHttpDeliveryMetrics.STATUS_5XX, 0.2);
        metrics.record("slack", AlarmHttpDeliveryMetrics.RESULT_TIMEOUT, null, 1.0);

        assertEquals(3, creator.deliveryIncrements.get());
        assertEquals(2, creator.statusIncrements.get());
        assertEquals(3, creator.latencyObservations.size());
        assertTrue(creator.latencyObservations.stream().allMatch(v -> v >= 0));
    }

    @Test
    public void recordSwallowsCreatorFailures() {
        final MetricsCreator broken = new MetricsCreator() {
            @Override
            public CounterMetrics createCounter(final String name, final String tips, final MetricsTag.Keys tagKeys,
                                                final MetricsTag.Values tagValues) {
                throw new IllegalStateException("boom");
            }

            @Override
            public GaugeMetrics createGauge(final String name, final String tips, final MetricsTag.Keys tagKeys,
                                            final MetricsTag.Values tagValues) {
                throw new IllegalStateException("boom");
            }

            @Override
            public HistogramMetrics createHistogramMetric(final String name, final String tips,
                                                          final MetricsTag.Keys tagKeys,
                                                          final MetricsTag.Values tagValues, final double... buckets) {
                throw new IllegalStateException("boom");
            }
        };

        new AlarmHttpDeliveryMetrics(broken)
            .record("webhook", AlarmHttpDeliveryMetrics.RESULT_SUCCESS, AlarmHttpDeliveryMetrics.STATUS_2XX, 0.01);
    }

    @Test
    public void negativeLatencyIsClamped() {
        final RecordingCreator creator = new RecordingCreator();
        final AlarmHttpDeliveryMetrics metrics = new AlarmHttpDeliveryMetrics(creator);

        metrics.record("webhook", AlarmHttpDeliveryMetrics.RESULT_SUCCESS, AlarmHttpDeliveryMetrics.STATUS_2XX, -1.5);

        assertEquals(1, creator.deliveryIncrements.get());
        assertEquals(1, creator.latencyObservations.size());
        assertEquals(0.0, creator.latencyObservations.get(0), 0.0);
    }

    @Test
    public void repeatedRecordsReuseTheSameCounterInstance() {
        final RecordingCreator creator = new RecordingCreator();
        final AlarmHttpDeliveryMetrics metrics = new AlarmHttpDeliveryMetrics(creator);

        metrics.record("webhook", AlarmHttpDeliveryMetrics.RESULT_SUCCESS, AlarmHttpDeliveryMetrics.STATUS_2XX, 0.1);
        metrics.record("webhook", AlarmHttpDeliveryMetrics.RESULT_SUCCESS, AlarmHttpDeliveryMetrics.STATUS_2XX, 0.2);

        assertEquals(1, creator.deliveryCreates.get());
        assertEquals(1, creator.statusCreates.get());
        assertEquals(1, creator.histogramCreates.get());
        assertEquals(2, creator.deliveryIncrements.get());
        assertEquals(2, creator.statusIncrements.get());
        assertEquals(2, creator.latencyObservations.size());
    }

    private static final class RecordingCreator extends MetricsCreatorNoop {
        private final AtomicInteger deliveryIncrements = new AtomicInteger();
        private final AtomicInteger statusIncrements = new AtomicInteger();
        private final AtomicInteger deliveryCreates = new AtomicInteger();
        private final AtomicInteger statusCreates = new AtomicInteger();
        private final AtomicInteger histogramCreates = new AtomicInteger();
        private final List<String> deliveryKeys = new CopyOnWriteArrayList<>();
        private final List<Double> latencyObservations = new ArrayList<>();

        @Override
        public CounterMetrics createCounter(final String name, final String tips, final MetricsTag.Keys tagKeys,
                                            final MetricsTag.Values tagValues) {
            final String joined = String.join(",", tagValues.getValues());
            if ("alarm_http_delivery_total".equals(name)) {
                deliveryCreates.incrementAndGet();
            } else if ("alarm_http_delivery_status_total".equals(name)) {
                statusCreates.incrementAndGet();
            }
            return new CounterMetrics() {
                @Override
                public void inc() {
                    if ("alarm_http_delivery_total".equals(name)) {
                        deliveryIncrements.incrementAndGet();
                        deliveryKeys.add(joined);
                    } else if ("alarm_http_delivery_status_total".equals(name)) {
                        statusIncrements.incrementAndGet();
                    }
                }

                @Override
                public void inc(final double value) {
                    inc();
                }
            };
        }

        @Override
        public HistogramMetrics createHistogramMetric(final String name, final String tips,
                                                      final MetricsTag.Keys tagKeys,
                                                      final MetricsTag.Values tagValues, final double... buckets) {
            histogramCreates.incrementAndGet();
            return new HistogramMetrics() {
                @Override
                public void observe(final double value) {
                    latencyObservations.add(value);
                }
            };
        }
    }
}
