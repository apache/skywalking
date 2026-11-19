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

package org.apache.skywalking.oap.server.core.remote;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricStreamKind;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.Empty;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteMessage;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteServiceGrpc;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceGetter;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceSetter;
import org.apache.skywalking.oap.server.core.worker.WorkerInstancesService;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.testing.module.ModuleDefineTesting;
import org.apache.skywalking.oap.server.testing.module.ModuleManagerTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RemoteServiceHandlerWorkerFailureTestCase {
    private static final String FAILING_WORKER = "failing-worker";
    private static final String ACCEPTING_WORKER = "accepting-worker";

    private Server server;
    private ManagedChannel channel;
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private TestAcceptingWorker acceptingWorker;

    @BeforeEach
    public void before() throws IOException {
        final String name = UUID.randomUUID().toString();
        final ModuleManagerTesting moduleManager = new ModuleManagerTesting();
        final ModuleDefineTesting core = new ModuleDefineTesting();
        moduleManager.put(CoreModule.NAME, core);
        final WorkerInstancesService workers = new WorkerInstancesService();
        core.provider().registerServiceImplementation(IWorkerInstanceGetter.class, workers);
        core.provider().registerServiceImplementation(IWorkerInstanceSetter.class, workers);
        workers.put(FAILING_WORKER, new TestFailingWorker(moduleManager), MetricStreamKind.OAL, TestStreamData.class);
        acceptingWorker = new TestAcceptingWorker(moduleManager);
        workers.put(ACCEPTING_WORKER, acceptingWorker, MetricStreamKind.OAL, TestStreamData.class);

        final MetricsCreator metricsCreator = mock(MetricsCreator.class);
        when(metricsCreator.createCounter(any(), any(), any(), any())).thenAnswer(invocation -> {
            final String counterName = invocation.getArgument(0);
            return new CounterMetrics() {
                @Override
                public void inc() {
                    counters.computeIfAbsent(counterName, ignored -> new AtomicInteger()).incrementAndGet();
                }

                @Override
                public void inc(double value) {
                }
            };
        });
        when(metricsCreator.createHistogramMetric(any(), any(), any(), any())).thenReturn(new HistogramMetrics() {
            @Override
            public void observe(double value) {
            }
        });
        final ModuleDefineTesting telemetry = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetry);
        telemetry.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);

        server = InProcessServerBuilder.forName(name)
                                       .addService(new RemoteServiceHandler(moduleManager))
                                       .build()
                                       .start();
        channel = InProcessChannelBuilder.forName(name).build();
    }

    @AfterEach
    public void after() throws InterruptedException {
        channel.shutdownNow();
        server.shutdownNow();
        channel.awaitTermination(1, TimeUnit.MINUTES);
        server.awaitTermination(1, TimeUnit.MINUTES);
    }

    @Test
    public void workerFailureDiscardsOnlyThatMessageAndCountsAnInboundError() throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> streamError = new AtomicReference<>();
        final StreamObserver<RemoteMessage> requests = RemoteServiceGrpc.newStub(channel).call(
            new StreamObserver<Empty>() {
                @Override
                public void onNext(Empty empty) {
                }

                @Override
                public void onError(Throwable throwable) {
                    streamError.set(throwable);
                    done.countDown();
                }

                @Override
                public void onCompleted() {
                    done.countDown();
                }
            });

        requests.onNext(message(FAILING_WORKER));
        requests.onNext(message(ACCEPTING_WORKER));
        requests.onCompleted();

        Assertions.assertTrue(done.await(10, TimeUnit.SECONDS), "the stream must finish");
        Assertions.assertNull(streamError.get(), "one failing message must not fail the whole stream");
        Assertions.assertEquals(1, acceptingWorker.received.get(), "the message after the failure must still be processed");
        Assertions.assertEquals(1, counters.get("remote_in_error_count").get());
        Assertions.assertEquals(2, counters.get("remote_in_count").get());
    }

    private static RemoteMessage message(final String workerName) {
        return RemoteMessage.newBuilder()
                            .setNextWorkerName(workerName)
                            .setRemoteData(RemoteData.newBuilder().addDataLongs(1))
                            .build();
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
            return RemoteData.newBuilder();
        }
    }

    static class TestFailingWorker extends AbstractWorker {
        TestFailingWorker(ModuleDefineHolder moduleDefineHolder) {
            super(moduleDefineHolder);
        }

        @Override
        public void in(Object o) {
            throw new IllegalStateException("worker failure");
        }
    }

    static class TestAcceptingWorker extends AbstractWorker {
        private final AtomicInteger received = new AtomicInteger();

        TestAcceptingWorker(ModuleDefineHolder moduleDefineHolder) {
            super(moduleDefineHolder);
        }

        @Override
        public void in(Object o) {
            received.incrementAndGet();
        }
    }
}
