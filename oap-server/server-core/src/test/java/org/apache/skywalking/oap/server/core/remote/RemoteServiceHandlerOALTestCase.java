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
import io.grpc.util.MutableHandlerRegistry;
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
import org.apache.skywalking.oap.server.library.module.DuplicateProviderException;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.apache.skywalking.oap.server.library.module.ProviderNotFoundException;
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

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RemoteServiceHandlerOALTestCase {

    private Server server;
    private ManagedChannel channel;
    private MutableHandlerRegistry serviceRegistry;

    @BeforeEach
    public void beforeEach() throws IOException {
        serviceRegistry = new MutableHandlerRegistry();
        final String name = UUID.randomUUID().toString();
        InProcessServerBuilder serverBuilder =
                InProcessServerBuilder
                        .forName(name)
                        .fallbackHandlerRegistry(serviceRegistry);

        server = serverBuilder.build();
        server.start();

        channel = InProcessChannelBuilder.forName(name).build();
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
    public void callTest() throws DuplicateProviderException, ProviderNotFoundException, IOException {
        final String testWorkerId = "mock-worker";

        ModuleManagerTesting moduleManager = new ModuleManagerTesting();
        ModuleDefineTesting moduleDefine = new ModuleDefineTesting();
        moduleManager.put(CoreModule.NAME, moduleDefine);

        WorkerInstancesService workerInstancesService = new WorkerInstancesService();
        moduleDefine.provider().registerServiceImplementation(IWorkerInstanceGetter.class, workerInstancesService);
        moduleDefine.provider().registerServiceImplementation(IWorkerInstanceSetter.class, workerInstancesService);

        TestWorker worker = new TestWorker(moduleManager);
        workerInstancesService.put(testWorkerId, worker, MetricStreamKind.OAL, TestRemoteData.class);

        MetricsCreator metricsCreator = mock(MetricsCreator.class);
        when(metricsCreator.createCounter(any(), any(), any(), any())).thenReturn(new CounterMetrics() {
            @Override
            public void inc() {

            }

            @Override
            public void inc(double value) {

            }
        });
        when(metricsCreator.createHistogramMetric(any(), any(), any(), any())).thenReturn(new HistogramMetrics() {
            @Override
            public Timer createTimer() {
                return super.createTimer();
            }

            @Override
            public void observe(double value) {

            }
        });

        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricsCreator.class, metricsCreator);
        serviceRegistry.addService(new RemoteServiceHandler(moduleManager));

        RemoteServiceGrpc.RemoteServiceStub remoteServiceStub = RemoteServiceGrpc.newStub(channel);

        StreamObserver<RemoteMessage> streamObserver = remoteServiceStub.call(new StreamObserver<Empty>() {
            @Override
            public void onNext(Empty empty) {

            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        });

        RemoteMessage.Builder remoteMessage = RemoteMessage.newBuilder();
        remoteMessage.setNextWorkerName(testWorkerId);

        RemoteData.Builder remoteData = RemoteData.newBuilder();
        remoteData.addDataStrings("test1");
        remoteData.addDataStrings("test2");

        remoteData.addDataLongs(10);
        remoteData.addDataLongs(20);
        remoteMessage.setRemoteData(remoteData);

        streamObserver.onNext(remoteMessage.build());
        streamObserver.onCompleted();
    }

    public static class TestRemoteData extends StreamData {

        private String str1;
        private String str2;
        private long long1;
        private long long2;

        @Override
        public int remoteHashCode() {
            return 10;
        }

        @Override
        public void deserialize(RemoteData remoteData) {
            str1 = remoteData.getDataStrings(0);
            str2 = remoteData.getDataStrings(1);
            long1 = remoteData.getDataLongs(0);
            long2 = remoteData.getDataLongs(1);

            Assertions.assertEquals("test1", str1);
            Assertions.assertEquals("test2", str2);
            Assertions.assertEquals(10, long1);
            Assertions.assertEquals(20, long2);
        }

        @Override
        public RemoteData.Builder serialize() {
            return null;
        }
    }

    static class TestWorker extends AbstractWorker {

        public TestWorker(ModuleDefineHolder moduleDefineHolder) {
            super(moduleDefineHolder);
        }

        @Override
        public void in(Object o) {
            TestRemoteData data = (TestRemoteData) o;

            Assertions.assertEquals("test1", data.str1);
            Assertions.assertEquals("test2", data.str2);
            Assertions.assertEquals(10, data.long1);
            Assertions.assertEquals(20, data.long2);
        }
    }
}
