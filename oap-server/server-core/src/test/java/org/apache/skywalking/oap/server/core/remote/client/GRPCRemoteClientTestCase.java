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

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.util.MutableHandlerRegistry;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricStreamKind;
import org.apache.skywalking.oap.server.core.remote.RemoteServiceHandler;
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
import org.apache.skywalking.oap.server.telemetry.api.GaugeMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.testing.module.ModuleDefineTesting;
import org.apache.skywalking.oap.server.testing.module.ModuleManagerTesting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class GRPCRemoteClientTestCase {
    private final String nextWorkerName = "mock-worker";
    private ModuleManagerTesting moduleManager;

    private Server server;
    private ManagedChannel channel;
    private MutableHandlerRegistry serviceRegistry;
    private TestWorker worker;

    @BeforeEach
    public void before() throws IOException {
        moduleManager = new ModuleManagerTesting();
        serviceRegistry = new MutableHandlerRegistry();
        final String name = UUID.randomUUID().toString();
        InProcessServerBuilder serverBuilder =
                InProcessServerBuilder
                        .forName(name)
                        .fallbackHandlerRegistry(serviceRegistry);

        server = serverBuilder.build();
        server.start();

        channel = InProcessChannelBuilder.forName(name).build();

        ModuleDefineTesting moduleDefine = new ModuleDefineTesting();
        moduleManager.put(CoreModule.NAME, moduleDefine);

        WorkerInstancesService workerInstancesService = new WorkerInstancesService();
        moduleDefine.provider().registerServiceImplementation(IWorkerInstanceGetter.class, workerInstancesService);
        moduleDefine.provider().registerServiceImplementation(IWorkerInstanceSetter.class, workerInstancesService);

        worker = new TestWorker(moduleManager);
        workerInstancesService.put(nextWorkerName, worker, MetricStreamKind.OAL, TestStreamData.class);
    }

    @AfterEach
    public void after() {
        channel.shutdown();
        server.shutdown();

        try {
            channel.awaitTermination(1L, TimeUnit.MINUTES);
            server.awaitTermination(1L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            channel.shutdownNow();
            channel = null;
            server.shutdownNow();
            server = null;
        }
    }

    @Test
    public void testPush() throws Exception {
        final AtomicInteger outCount = new AtomicInteger();
        final AtomicInteger latencyCount = new AtomicInteger();
        final AtomicReference<Double> queueUsed = new AtomicReference<>(null);
        final AtomicReference<Double> inflight = new AtomicReference<>(null);

        MetricsCreator metricsCreator = mockMetricsCreator(outCount, latencyCount, null, queueUsed, inflight);

        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);

        serviceRegistry.addService(new RemoteServiceHandler(moduleManager));

        Address address = new Address("not-important", 11, false);
        GRPCRemoteClient remoteClient = spy(new GRPCRemoteClient(moduleManager, address, 10, 10, null));
        try {
            remoteClient.connect();

            doReturn(channel).when(remoteClient).getChannel();

            for (int i = 0; i < 12; i++) {
                remoteClient.push(nextWorkerName, new TestStreamData());
            }

            awaitCondition(
                "remote push metrics to settle",
                10,
                () -> outCount.get() >= 12
                    && latencyCount.get() >= 1
                    && queueUsed.get() != null
                    && inflight.get() != null
                    && Math.abs(inflight.get()) < 0.01
            );
            Assertions.assertTrue(outCount.get() >= 12, "expected remote_out_count >= 12, got " + outCount.get());
            Assertions.assertTrue(latencyCount.get() >= 1, "expected >=1 remote_out_latency observation per stream, got " + latencyCount.get());
            Assertions.assertTrue(
                latencyCount.get() <= outCount.get(),
                "stream latency observations must not exceed push attempts; latency="
                    + latencyCount.get() + " out=" + outCount.get()
            );
            // One observe per successful stream (batch), never more than messages pushed.
            Assertions.assertTrue(
                latencyCount.get() <= 12,
                "expected at most one latency sample per push when each flush is its own stream, got "
                    + latencyCount.get()
            );
            Assertions.assertNotNull(queueUsed.get(), "expected remote_out_queue_used_percentage to be set");
            Assertions.assertTrue(queueUsed.get() >= 0 && queueUsed.get() <= 100, "queue used % must stay in [0,100]");
            Assertions.assertEquals(0.0, inflight.get(), 0.01, "inflight should drain after streams finish");

            // Leave a non-zero pressure sample, then close — departed peers must not keep stale gauges.
            inflight.set(9.0);
            queueUsed.set(88.0);
            remoteClient.close();
            Assertions.assertEquals(0.0, inflight.get(), 0.01, "close must clear stream inflight gauge");
            Assertions.assertEquals(0.0, queueUsed.get(), 0.01, "close must clear queue used gauge");
        } finally {
            try {
                remoteClient.close();
            } catch (Throwable ignored) {
                // already closed in the happy path above
            }
        }
    }

    @Test
    public void publishQueueUsedAfterCloseKeepsGaugeCleared() throws Exception {
        final AtomicReference<Double> queueUsed = new AtomicReference<>(77.0);

        MetricsCreator metricsCreator = mockMetricsCreator(
            new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), queueUsed, new AtomicReference<>(0.0));
        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);

        Address address = new Address("not-important", 20, false);
        GRPCRemoteClient client = new GRPCRemoteClient(moduleManager, address, 10, 10, null);
        client.connect();
        client.close();
        Assertions.assertEquals(0.0, queueUsed.get(), 0.01, "close must clear the queue gauge");

        // A publish that reached the gauge would overwrite this marker with the (empty) queue's 0%.
        queueUsed.set(55.0);
        Method publishQueueUsed = GRPCRemoteClient.class.getDeclaredMethod("publishQueueUsed");
        publishQueueUsed.setAccessible(true);
        publishQueueUsed.invoke(client);
        Assertions.assertEquals(55.0, queueUsed.get(), 0.01, "a closed client must not publish the queue gauge");
    }

    @Test
    public void streamFailureCountsWhileDrainingButNotCancelOrAfterClose() throws Exception {
        final AtomicInteger errorCount = new AtomicInteger();

        MetricsCreator metricsCreator = mockMetricsCreator(
            new AtomicInteger(), new AtomicInteger(), errorCount, new AtomicReference<>(null), new AtomicReference<>(null));
        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);

        Address address = new Address("not-important", 21, false);
        GRPCRemoteClient remoteClient = spy(new GRPCRemoteClient(moduleManager, address, 10, 10, null));
        RemoteServiceGrpc.RemoteServiceStub stub = mock(RemoteServiceGrpc.RemoteServiceStub.class);
        RemoteServiceGrpc.RemoteServiceStub stubWithDeadline = mock(RemoteServiceGrpc.RemoteServiceStub.class);
        doReturn(stub).when(remoteClient).getStub();
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stubWithDeadline);
        final List<StreamObserver<Empty>> responseObservers = new ArrayList<>();
        when(stubWithDeadline.call(any())).thenAnswer(invocation -> {
            responseObservers.add(invocation.getArgument(0));
            return new StreamObserver<RemoteMessage>() {
                @Override
                public void onNext(RemoteMessage value) {
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onCompleted() {
                }
            };
        });

        // close() sets `closing` before the queue's final drain sends the remaining batches.
        Field closingField = GRPCRemoteClient.class.getDeclaredField("closing");
        closingField.setAccessible(true);
        closingField.setBoolean(remoteClient, true);
        Method createStreamObserver = GRPCRemoteClient.class.getDeclaredMethod(
            "createStreamObserver", long[].class, int.class, AtomicBoolean.class, AtomicBoolean.class, AtomicBoolean.class);
        createStreamObserver.setAccessible(true);
        for (int i = 0; i < 3; i++) {
            createStreamObserver.invoke(
                remoteClient, new long[1], 1, new AtomicBoolean(false), new AtomicBoolean(false), new AtomicBoolean(false));
        }
        Assertions.assertEquals(3, responseObservers.size());

        responseObservers.get(0).onError(Status.UNAVAILABLE.asRuntimeException());
        Assertions.assertEquals(1, errorCount.get(), "a stream that fails during the drain must be counted");

        responseObservers.get(1).onError(Status.CANCELLED.asRuntimeException());
        Assertions.assertEquals(1, errorCount.get(), "a cancelled stream must not be counted");

        remoteClient.close();
        responseObservers.get(2).onError(
            Status.UNAVAILABLE.withDescription("Channel shutdownNow invoked").asRuntimeException());
        Assertions.assertEquals(1, errorCount.get(), "streams failed by close() shutting the channel must not be counted");
    }

    @Test
    public void sendFailureAndSuccessWhileDrainingAreRecorded() throws Exception {
        final AtomicInteger latencyCount = new AtomicInteger();
        final AtomicInteger errorCount = new AtomicInteger();

        MetricsCreator metricsCreator = mockMetricsCreator(
            new AtomicInteger(), latencyCount, errorCount, new AtomicReference<>(null), new AtomicReference<>(null));
        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);

        Address address = new Address("not-important", 22, false);
        GRPCRemoteClient remoteClient = spy(new GRPCRemoteClient(moduleManager, address, 10, 10, null));
        RemoteServiceGrpc.RemoteServiceStub stub = mock(RemoteServiceGrpc.RemoteServiceStub.class);
        RemoteServiceGrpc.RemoteServiceStub stubWithDeadline = mock(RemoteServiceGrpc.RemoteServiceStub.class);
        doReturn(stub).when(remoteClient).getStub();
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stubWithDeadline);
        final List<StreamObserver<Empty>> responseObservers = new ArrayList<>();
        when(stubWithDeadline.call(any())).thenAnswer(invocation -> {
            final boolean failOnSend = responseObservers.isEmpty();
            responseObservers.add(invocation.getArgument(0));
            return new StreamObserver<RemoteMessage>() {
                @Override
                public void onNext(RemoteMessage value) {
                    if (failOnSend) {
                        throw new IllegalStateException("send failure while draining");
                    }
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onCompleted() {
                }
            };
        });

        Field closingField = GRPCRemoteClient.class.getDeclaredField("closing");
        closingField.setAccessible(true);
        closingField.setBoolean(remoteClient, true);
        final GRPCRemoteClient.RemoteMessageConsumer consumer = remoteClient.new RemoteMessageConsumer();
        final RemoteMessage message = RemoteMessage.newBuilder()
                                                   .setNextWorkerName(nextWorkerName)
                                                   .setRemoteData(new TestStreamData().serialize())
                                                   .build();

        consumer.consume(List.of(message));
        Assertions.assertEquals(1, errorCount.get(), "a send that fails during the drain must be counted");

        consumer.consume(List.of(message));
        responseObservers.get(1).onCompleted();
        Assertions.assertEquals(1, latencyCount.get(), "a stream that succeeds during the drain must record latency");
        Assertions.assertEquals(1, errorCount.get());
        remoteClient.close();
    }

    @Test
    public void pushCountsUnavailableStreamAsError() throws Exception {
        final AtomicInteger outCount = new AtomicInteger();
        final AtomicInteger latencyCount = new AtomicInteger();
        final AtomicInteger errorCount = new AtomicInteger();
        final AtomicReference<Double> queueUsed = new AtomicReference<>(null);
        final AtomicReference<Double> inflight = new AtomicReference<>(null);

        MetricsCreator metricsCreator = mockMetricsCreator(outCount, latencyCount, errorCount, queueUsed, inflight);

        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);

        serviceRegistry.addService(new RemoteServiceGrpc.RemoteServiceImplBase() {
            @Override
            public StreamObserver<RemoteMessage> call(StreamObserver<Empty> responseObserver) {
                return new StreamObserver<RemoteMessage>() {
                    @Override
                    public void onNext(RemoteMessage value) {
                    }

                    @Override
                    public void onError(Throwable t) {
                    }

                    @Override
                    public void onCompleted() {
                        responseObserver.onError(Status.UNAVAILABLE.asRuntimeException());
                    }
                };
            }
        });

        Address address = new Address("not-important", 12, false);
        GRPCRemoteClient remoteClient = spy(new GRPCRemoteClient(moduleManager, address, 10, 10, null));
        try {
            remoteClient.connect();
            doReturn(channel).when(remoteClient).getChannel();

            remoteClient.push(nextWorkerName, new TestStreamData());
            awaitCondition(
                "unavailable stream metrics to settle",
                10,
                () -> outCount.get() >= 1
                    && errorCount.get() > 0
                    && inflight.get() != null
                    && Math.abs(inflight.get()) < 0.01
            );

            Assertions.assertTrue(outCount.get() >= 1);
            Assertions.assertEquals(0, latencyCount.get(), "failed streams must not pollute remote_out_latency");
            Assertions.assertEquals(1, errorCount.get());
            Assertions.assertEquals(0.0, inflight.get(), 0.01);
        } finally {
            remoteClient.close();
        }
    }

    @Test
    public void syncOnNextFailureReleasesInflightAndRecordsErrorOnce() throws Exception {
        final AtomicInteger outCount = new AtomicInteger();
        final AtomicInteger latencyCount = new AtomicInteger();
        final AtomicInteger errorCount = new AtomicInteger();
        final AtomicReference<Double> queueUsed = new AtomicReference<>(null);
        final AtomicReference<Double> inflight = new AtomicReference<>(null);

        MetricsCreator metricsCreator = mockMetricsCreator(outCount, latencyCount, errorCount, queueUsed, inflight);

        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);

        Address address = new Address("not-important", 13, false);
        GRPCRemoteClient remoteClient = spy(new GRPCRemoteClient(moduleManager, address, 10, 10, null));
        RemoteServiceGrpc.RemoteServiceStub stub = mock(RemoteServiceGrpc.RemoteServiceStub.class);
        RemoteServiceGrpc.RemoteServiceStub stubWithDeadline = mock(RemoteServiceGrpc.RemoteServiceStub.class);
        doReturn(stub).when(remoteClient).getStub();
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stubWithDeadline);
        when(stubWithDeadline.call(any())).thenAnswer(invocation -> new StreamObserver<RemoteMessage>() {
            @Override
            public void onNext(RemoteMessage value) {
                throw new IllegalStateException("boom after stream acquired");
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
            }
        });

        try {
            remoteClient.connect();
            remoteClient.push(nextWorkerName, new TestStreamData());
            awaitCondition(
                "sync failure to release inflight",
                10,
                () -> inflight.get() != null
                    && Math.abs(inflight.get()) < 0.01
                    && errorCount.get() == 1
            );

            Assertions.assertEquals(0.0, inflight.get(), 0.01, "sync failure must release the stream slot");
            Assertions.assertEquals(0, latencyCount.get());
            Assertions.assertEquals(1, errorCount.get());
        } finally {
            remoteClient.close();
        }
    }

    private static void awaitCondition(String description,
                                       long timeoutSeconds,
                                       Callable<Boolean> condition) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(condition.call())) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        Assertions.fail("timed out waiting for " + description);
    }

    private static MetricsCreator mockMetricsCreator(AtomicInteger outCount,
                                                     AtomicInteger latencyCount,
                                                     AtomicInteger errorCount,
                                                     AtomicReference<Double> queueUsed,
                                                     AtomicReference<Double> inflight) {
        MetricsCreator metricsCreator = mock(MetricsCreator.class);
        when(metricsCreator.createCounter(any(), any(), any(), any())).thenAnswer(invocation -> {
            final String name = invocation.getArgument(0);
            return new CounterMetrics() {
                @Override
                public void inc() {
                    if ("remote_out_count".equals(name)) {
                        outCount.incrementAndGet();
                    }
                    if ("remote_out_error_count".equals(name) && errorCount != null) {
                        errorCount.incrementAndGet();
                    }
                }

                @Override
                public void inc(double value) {
                }
            };
        });
        when(metricsCreator.createHistogramMetric(any(), any(), any(), any())).thenAnswer(invocation -> {
            final String name = invocation.getArgument(0);
            return new HistogramMetrics() {
                @Override
                public void observe(double value) {
                    if ("remote_out_latency".equals(name)) {
                        latencyCount.incrementAndGet();
                    }
                }
            };
        });
        when(metricsCreator.createGauge(any(), any(), any(), any())).thenAnswer(invocation -> {
            final String name = invocation.getArgument(0);
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
                    if ("remote_out_queue_used_percentage".equals(name)) {
                        queueUsed.set(value);
                    }
                    if ("remote_out_stream_inflight".equals(name)) {
                        inflight.set(value);
                    }
                }

                @Override
                public double getValue() {
                    return 0;
                }
            };
        });
        return metricsCreator;
    }

    @Test
    public void interruptWaitingForStreamSlotCountsOneError() throws Exception {
        final AtomicInteger errorCount = new AtomicInteger();
        final AtomicReference<Double> blockSeconds = new AtomicReference<>(0d);

        MetricsCreator metricsCreator = mock(MetricsCreator.class);
        when(metricsCreator.createCounter(any(), any(), any(), any())).thenAnswer(invocation -> {
            final String name = invocation.getArgument(0);
            return new CounterMetrics() {
                @Override
                public void inc() {
                    if ("remote_out_error_count".equals(name)) {
                        errorCount.incrementAndGet();
                    }
                }

                @Override
                public void inc(double value) {
                    if ("remote_out_stream_block_seconds".equals(name)) {
                        blockSeconds.updateAndGet(current -> current + value);
                    }
                }
            };
        });
        when(metricsCreator.createHistogramMetric(any(), any(), any(), any())).thenReturn(new HistogramMetrics() {
            @Override
            public void observe(double value) {
            }
        });
        when(metricsCreator.createGauge(any(), any(), any(), any())).thenReturn(
            new GaugeMetrics() {
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
            });

        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);

        Address address = new Address("not-important", 100, false);
        GRPCRemoteClient remoteClient = new GRPCRemoteClient(moduleManager, address, 10, 10, null);

        Field inflight = GRPCRemoteClient.class.getDeclaredField("concurrentStreamObserverNumber");
        inflight.setAccessible(true);
        // At the max, the waiter blocks; one more would also block, but the limit itself is 10.
        ((AtomicInteger) inflight.get(remoteClient)).set(GRPCRemoteClient.MAX_CONCURRENT_STREAMS);

        Method createStreamObserver = GRPCRemoteClient.class.getDeclaredMethod(
            "createStreamObserver", long[].class, int.class, AtomicBoolean.class, AtomicBoolean.class, AtomicBoolean.class);
        createStreamObserver.setAccessible(true);

        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            started.countDown();
            try {
                createStreamObserver.invoke(
                    remoteClient, new long[1], 1, new AtomicBoolean(false), new AtomicBoolean(false), new AtomicBoolean(false));
                failure.set(new AssertionError("expected interrupt while waiting for a stream slot"));
            } catch (InvocationTargetException ex) {
                failure.set(ex.getCause());
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "remote-stream-slot-waiter");
        waiter.start();
        Assertions.assertTrue(started.await(2, TimeUnit.SECONDS));
        TimeUnit.MILLISECONDS.sleep(30);
        waiter.interrupt();
        waiter.join(TimeUnit.SECONDS.toMillis(5));
        Assertions.assertFalse(waiter.isAlive(), "waiter thread should exit after interrupt");

        Assertions.assertInstanceOf(GRPCRemoteClient.RemoteStreamInterruptedException.class, failure.get());
        Assertions.assertEquals(1, errorCount.get());
        Assertions.assertTrue(blockSeconds.get() >= 0d);
    }

    @Test
    public void pushWhenClosedSkipsErrorOutAndLatency() throws Exception {
        final AtomicInteger outCount = new AtomicInteger();
        final AtomicInteger latencyCount = new AtomicInteger();
        final AtomicInteger errorCount = new AtomicInteger();
        final AtomicReference<Double> queueUsed = new AtomicReference<>(null);
        final AtomicReference<Double> inflight = new AtomicReference<>(null);

        MetricsCreator metricsCreator = mockMetricsCreator(outCount, latencyCount, errorCount, queueUsed, inflight);
        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);

        Address address = new Address("not-important", 14, false);
        GRPCRemoteClient remoteClient = spy(new GRPCRemoteClient(moduleManager, address, 10, 10, null));
        remoteClient.connect();
        doReturn(channel).when(remoteClient).getChannel();
        remoteClient.close();

        remoteClient.push(nextWorkerName, new TestStreamData());

        Assertions.assertEquals(0, outCount.get(), "closed client must not count remote_out");
        Assertions.assertEquals(0, latencyCount.get(), "closed client must not record latency");
        Assertions.assertEquals(0, errorCount.get(), "closed client must not inflate remote_out_error");
    }

    @Test
    public void endStreamAfterCloseSkipsLatency() throws Exception {
        final AtomicInteger outCount = new AtomicInteger();
        final AtomicInteger latencyCount = new AtomicInteger();
        final AtomicInteger errorCount = new AtomicInteger();
        final AtomicReference<Double> queueUsed = new AtomicReference<>(null);
        final AtomicReference<Double> inflight = new AtomicReference<>(null);

        MetricsCreator metricsCreator = mockMetricsCreator(outCount, latencyCount, errorCount, queueUsed, inflight);
        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);

        Address address = new Address("not-important", 15, false);
        GRPCRemoteClient remoteClient = new GRPCRemoteClient(moduleManager, address, 10, 10, null);
        remoteClient.connect();
        remoteClient.close();

        Method endStream = GRPCRemoteClient.class.getDeclaredMethod(
            "endStream", AtomicBoolean.class, long.class, int.class, boolean.class);
        endStream.setAccessible(true);
        endStream.invoke(remoteClient, new AtomicBoolean(false), System.nanoTime(), 3, true);

        Assertions.assertEquals(0, latencyCount.get(), "late success callback after close must not observe latency");
    }

    @Test
    public void closeDrainsPendingPushesToThePeer() throws Exception {
        final AtomicInteger outCount = new AtomicInteger();
        final AtomicInteger latencyCount = new AtomicInteger();
        final AtomicInteger errorCount = new AtomicInteger();
        final AtomicReference<Double> queueUsed = new AtomicReference<>(null);
        final AtomicReference<Double> inflight = new AtomicReference<>(null);

        MetricsCreator metricsCreator = mockMetricsCreator(outCount, latencyCount, errorCount, queueUsed, inflight);
        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);
        serviceRegistry.addService(new RemoteServiceHandler(moduleManager));

        Address address = new Address("not-important", 18, false);
        GRPCRemoteClient remoteClient = spy(new GRPCRemoteClient(moduleManager, address, 10, 10, null));
        try {
            remoteClient.connect();
            doReturn(channel).when(remoteClient).getChannel();

            final int pushes = 20;
            for (int i = 0; i < pushes; i++) {
                remoteClient.push(nextWorkerName, new TestStreamData());
            }
            // close() sets closing, then BatchQueue final-drain must still open streams and flush.
            remoteClient.close();

            awaitCondition(
                "final drain to deliver queued pushes",
                10,
                () -> worker.received.get() >= pushes
            );
            Assertions.assertEquals(pushes, worker.received.get(), "the peer must receive every queued push");
            Assertions.assertEquals(0, errorCount.get(), "a successful drain must not count remote_out_error");
            Assertions.assertEquals(0.0, inflight.get() == null ? 0.0 : inflight.get(), 0.01);

            final int afterDrain = outCount.get();
            remoteClient.push(nextWorkerName, new TestStreamData());
            Assertions.assertEquals(afterDrain, outCount.get(), "push after close must not send");
            Assertions.assertEquals(pushes, worker.received.get(), "push after close must not reach the peer");
        } finally {
            try {
                remoteClient.close();
            } catch (Throwable ignored) {
                // close is idempotent for this test
            }
        }
    }

    @Test
    public void waitForStreamSlotWhileClosingStillAcquires() throws Exception {
        final AtomicInteger outCount = new AtomicInteger();
        final AtomicInteger latencyCount = new AtomicInteger();
        final AtomicInteger errorCount = new AtomicInteger();
        final AtomicReference<Double> queueUsed = new AtomicReference<>(null);
        final AtomicReference<Double> inflightGauge = new AtomicReference<>(null);

        MetricsCreator metricsCreator = mockMetricsCreator(outCount, latencyCount, errorCount, queueUsed, inflightGauge);
        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);

        Address address = new Address("not-important", 19, false);
        GRPCRemoteClient client = spy(new GRPCRemoteClient(moduleManager, address, 10, 10, null));
        client.connect();
        doReturn(channel).when(client).getChannel();
        serviceRegistry.addService(new RemoteServiceHandler(moduleManager));

        Field closingField = GRPCRemoteClient.class.getDeclaredField("closing");
        closingField.setAccessible(true);
        closingField.setBoolean(client, true);

        Field inflightField = GRPCRemoteClient.class.getDeclaredField("concurrentStreamObserverNumber");
        inflightField.setAccessible(true);
        ((AtomicInteger) inflightField.get(client)).set(GRPCRemoteClient.MAX_CONCURRENT_STREAMS);

        Method createStreamObserver = GRPCRemoteClient.class.getDeclaredMethod(
            "createStreamObserver", long[].class, int.class, AtomicBoolean.class, AtomicBoolean.class, AtomicBoolean.class);
        createStreamObserver.setAccessible(true);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            started.countDown();
            try {
                createStreamObserver.invoke(
                    client, new long[1], 1, new AtomicBoolean(false), new AtomicBoolean(false), new AtomicBoolean(false));
            } catch (InvocationTargetException ex) {
                waiterFailure.set(ex.getCause());
            } catch (Throwable t) {
                waiterFailure.set(t);
            } finally {
                finished.countDown();
            }
        }, "remote-wait-while-closing");
        waiter.start();
        Assertions.assertTrue(started.await(2, TimeUnit.SECONDS));
        TimeUnit.MILLISECONDS.sleep(40);

        // Free one slot as if a stream finished mid-drain; closing must still allow acquire.
        Method endStream = GRPCRemoteClient.class.getDeclaredMethod(
            "endStream", AtomicBoolean.class, long.class, int.class, boolean.class);
        endStream.setAccessible(true);
        endStream.invoke(client, new AtomicBoolean(false), System.nanoTime(), 1, false);

        Assertions.assertTrue(finished.await(5, TimeUnit.SECONDS), "closing waiter must acquire a stream slot");
        Assertions.assertNull(waiterFailure.get(), "closing must not abort drain stream wait: " + waiterFailure.get());

        Method acquire = GRPCRemoteClient.class.getDeclaredMethod("acquireStreamSlot");
        acquire.setAccessible(true);
        client.close();
        Assertions.assertEquals(Boolean.FALSE, acquire.invoke(client), "fully closed client must refuse streams");
    }

    @Test
    public void acquireAndWaitAfterCloseDoNotInflateInflight() throws Exception {
        final AtomicInteger outCount = new AtomicInteger();
        final AtomicInteger latencyCount = new AtomicInteger();
        final AtomicInteger errorCount = new AtomicInteger();
        final AtomicReference<Double> queueUsed = new AtomicReference<>(null);
        final AtomicReference<Double> inflightGauge = new AtomicReference<>(null);

        MetricsCreator metricsCreator = mockMetricsCreator(outCount, latencyCount, errorCount, queueUsed, inflightGauge);
        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);

        Address addressH1 = new Address("not-important", 16, false);
        GRPCRemoteClient clientH1 = new GRPCRemoteClient(moduleManager, addressH1, 10, 10, null);
        clientH1.connect();
        clientH1.close();
        Method acquire = GRPCRemoteClient.class.getDeclaredMethod("acquireStreamSlot");
        acquire.setAccessible(true);
        Assertions.assertEquals(Boolean.FALSE, acquire.invoke(clientH1));
        Assertions.assertEquals(0.0, inflightGauge.get(), 0.01, "closed client must not re-inflate inflight");

        final AtomicReference<Double> inflightGauge2 = new AtomicReference<>(null);
        MetricsCreator metricsCreator2 = mockMetricsCreator(outCount, latencyCount, errorCount, queueUsed, inflightGauge2);
        ModuleDefineTesting telemetryModuleDefine2 = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine2);
        telemetryModuleDefine2.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator2);

        Address addressH2 = new Address("not-important", 17, false);
        GRPCRemoteClient clientH2 = new GRPCRemoteClient(moduleManager, addressH2, 10, 10, null);
        Field inflightField = GRPCRemoteClient.class.getDeclaredField("concurrentStreamObserverNumber");
        inflightField.setAccessible(true);
        ((AtomicInteger) inflightField.get(clientH2)).set(GRPCRemoteClient.MAX_CONCURRENT_STREAMS);

        Method createStreamObserver = GRPCRemoteClient.class.getDeclaredMethod(
            "createStreamObserver", long[].class, int.class, AtomicBoolean.class, AtomicBoolean.class, AtomicBoolean.class);
        createStreamObserver.setAccessible(true);

        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            started.countDown();
            try {
                createStreamObserver.invoke(
                    clientH2, new long[1], 1, new AtomicBoolean(false), new AtomicBoolean(false), new AtomicBoolean(false));
                waiterFailure.set(new AssertionError("expected closed client to refuse stream create"));
            } catch (InvocationTargetException ex) {
                waiterFailure.set(ex.getCause());
            } catch (Throwable t) {
                waiterFailure.set(t);
            }
        }, "remote-wait-after-close");
        waiter.start();
        Assertions.assertTrue(started.await(2, TimeUnit.SECONDS));
        TimeUnit.MILLISECONDS.sleep(40);
        clientH2.close();
        waiter.join(TimeUnit.SECONDS.toMillis(5));
        Assertions.assertFalse(waiter.isAlive());
        Assertions.assertInstanceOf(GRPCRemoteClient.RemoteClientClosedException.class, waiterFailure.get());
        Assertions.assertTrue(
            waiterFailure.get().getMessage().contains("closed"),
            "waiter should fail because the client closed"
        );
        Assertions.assertTrue(
            inflightGauge2.get() == null || Math.abs(inflightGauge2.get()) < 0.01,
            "closed wait must not leave a non-zero inflight gauge"
        );
    }

    public static class TestStreamData extends StreamData {

        private long value;

        @Override
        public int remoteHashCode() {
            return 0;
        }

        @Override
        public void deserialize(RemoteData remoteData) {
            this.value = remoteData.getDataLongs(0);
        }

        @Override
        public RemoteData.Builder serialize() {
            RemoteData.Builder builder = RemoteData.newBuilder();
            builder.addDataLongs(987);
            return builder;
        }
    }

    static class TestWorker extends AbstractWorker {
        private final AtomicInteger received = new AtomicInteger();

        public TestWorker(ModuleDefineHolder moduleDefineHolder) {
            super(moduleDefineHolder);
        }

        @Override
        public void in(Object o) {
            TestStreamData streamData = (TestStreamData) o;
            Assertions.assertEquals(987, streamData.value);
            received.incrementAndGet();
        }
    }
}
