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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricStreamKind;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceGetter;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceSetter;
import org.apache.skywalking.oap.server.core.worker.WorkerInstancesService;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.GaugeMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.apache.skywalking.oap.server.testing.module.ModuleDefineTesting;
import org.apache.skywalking.oap.server.testing.module.ModuleManagerTesting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SelfRemoteClientTest {
    private static final String WORKER = "self-remote-test-worker";

    private ModuleManagerTesting moduleManager;
    private WorkerInstancesService workers;
    private final Map<String, AtomicInteger> counterIncs = new HashMap<>();
    private final AtomicInteger latencyObservations = new AtomicInteger();
    private final AtomicReference<Double> lastLatencySeconds = new AtomicReference<>();
    private final Map<String, String[]> counterTags = new HashMap<>();
    private String[] latencyTags;

    @BeforeEach
    public void setUp() {
        moduleManager = new ModuleManagerTesting();
        ModuleDefineTesting core = new ModuleDefineTesting();
        moduleManager.put(CoreModule.NAME, core);
        workers = new WorkerInstancesService();
        core.provider().registerServiceImplementation(IWorkerInstanceGetter.class, workers);
        core.provider().registerServiceImplementation(IWorkerInstanceSetter.class, workers);

        MetricsCreator metricsCreator = mock(MetricsCreator.class);
        when(metricsCreator.createCounter(any(), any(), any(), any())).thenAnswer(invocation -> {
            final String name = invocation.getArgument(0);
            final MetricsTag.Values values = invocation.getArgument(3);
            counterTags.put(name, values.getValues());
            return new CounterMetrics() {
                @Override
                public void inc() {
                    counterIncs.computeIfAbsent(name, ignored -> new AtomicInteger()).incrementAndGet();
                }

                @Override
                public void inc(double value) {
                }
            };
        });
        when(metricsCreator.createHistogramMetric(any(), any(), any(), any())).thenAnswer(invocation -> {
            final MetricsTag.Values values = invocation.getArgument(3);
            latencyTags = values.getValues();
            return new HistogramMetrics() {
                @Override
                public void observe(double value) {
                    latencyObservations.incrementAndGet();
                    lastLatencySeconds.set(value);
                }
            };
        });
        when(metricsCreator.createGauge(any(), any(), any(), any())).thenReturn(noopGauge());

        ModuleDefineTesting telemetry = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetry);
        telemetry.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);
    }

    @Test
    public void pushSuccessRecordsOutAndLatency() {
        workers.put(WORKER, new AcceptingWorker(moduleManager), MetricStreamKind.OAL, TestStreamData.class);
        SelfRemoteClient client = new SelfRemoteClient(moduleManager, new Address("oap1", 11800, true));
        client.push(WORKER, new TestStreamData());

        Assertions.assertEquals(1, counterIncs.get("remote_out_count").get());
        Assertions.assertEquals(1, latencyObservations.get());
        Assertions.assertFalse(counterIncs.containsKey("remote_out_error_count"));
        Assertions.assertArrayEquals(new String[] {"oap1_11800", "Y"}, counterTags.get("remote_out_count"));
        Assertions.assertArrayEquals(new String[] {"oap1_11800", "Y"}, latencyTags);
        Assertions.assertNotNull(lastLatencySeconds.get());
        Assertions.assertTrue(lastLatencySeconds.get() >= 0, "self latency must not be negative");
    }

    @Test
    public void repeatedSuccessfulPushesEachObserveLatency() {
        workers.put(WORKER, new AcceptingWorker(moduleManager), MetricStreamKind.OAL, TestStreamData.class);
        SelfRemoteClient client = new SelfRemoteClient(moduleManager, new Address("oap1", 11800, true));
        for (int i = 0; i < 5; i++) {
            client.push(WORKER, new TestStreamData());
        }
        Assertions.assertEquals(5, counterIncs.get("remote_out_count").get());
        Assertions.assertEquals(5, latencyObservations.get(), "self client observes latency per successful push");
    }

    @Test
    public void pushFailureRecordsError() {
        workers.put(WORKER, new FailingWorker(moduleManager), MetricStreamKind.OAL, TestStreamData.class);
        SelfRemoteClient client = new SelfRemoteClient(moduleManager, new Address("oap1", 11800, true));
        client.push(WORKER, new TestStreamData());

        Assertions.assertEquals(1, counterIncs.get("remote_out_error_count").get());
        Assertions.assertEquals(0, latencyObservations.get());
    }

    @Test
    public void pushMissingWorkerRecordsError() {
        SelfRemoteClient client = new SelfRemoteClient(moduleManager, new Address("oap1", 11800, true));
        client.push("missing-worker", new TestStreamData());

        Assertions.assertEquals(1, counterIncs.get("remote_out_error_count").get());
        Assertions.assertEquals(0, latencyObservations.get());
        Assertions.assertFalse(counterIncs.containsKey("remote_out_count"));
    }

    @Test
    public void closeIsUnsupported() {
        SelfRemoteClient client = new SelfRemoteClient(moduleManager, new Address("oap1", 11800, true));
        Assertions.assertThrows(UnexpectedException.class, client::close);
    }

    private static GaugeMetrics noopGauge() {
        return new GaugeMetrics() {
            @Override
            public void inc() {
            }

            @Override
            public void inc(double value) {
            }

            @Override
            public void dec() {
            }

            @Override
            public void dec(double value) {
            }

            @Override
            public void setValue(double value) {
            }

            @Override
            public double getValue() {
                return 0;
            }
        };
    }

    public static class TestStreamData extends StreamData {
        @Override
        public int remoteHashCode() {
            return 0;
        }

        @Override
        public void deserialize(RemoteData remoteData) {
        }

        @Override
        public RemoteData.Builder serialize() {
            return RemoteData.newBuilder().addDataLongs(1);
        }
    }

    static class AcceptingWorker extends AbstractWorker {
        AcceptingWorker(ModuleDefineHolder moduleDefineHolder) {
            super(moduleDefineHolder);
        }

        @Override
        public void in(Object o) {
        }
    }

    static class FailingWorker extends AbstractWorker {
        FailingWorker(ModuleDefineHolder moduleDefineHolder) {
            super(moduleDefineHolder);
        }

        @Override
        public void in(Object o) {
            throw new IllegalStateException("boom");
        }
    }
}
