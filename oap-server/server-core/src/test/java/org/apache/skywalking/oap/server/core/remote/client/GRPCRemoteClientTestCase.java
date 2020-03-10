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

import io.grpc.testing.GrpcServerRule;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.remote.RemoteServiceHandler;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class GRPCRemoteClientTestCase {

    private final String nextWorkerName = "mock-worker";
    private ModuleManagerTesting moduleManager;
    @Rule
    public final GrpcServerRule grpcServerRule = new GrpcServerRule().directExecutor();

    @Before
    public void before() {
        moduleManager = new ModuleManagerTesting();
        ModuleDefineTesting moduleDefine = new ModuleDefineTesting();
        moduleManager.put(CoreModule.NAME, moduleDefine);

        WorkerInstancesService workerInstancesService = new WorkerInstancesService();
        moduleDefine.provider().registerServiceImplementation(IWorkerInstanceGetter.class, workerInstancesService);
        moduleDefine.provider().registerServiceImplementation(IWorkerInstanceSetter.class, workerInstancesService);

        TestWorker worker = new TestWorker(moduleManager);
        workerInstancesService.put(nextWorkerName, worker, TestStreamData.class);
    }

    @Test
    public void testPush() throws InterruptedException {
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

        grpcServerRule.getServiceRegistry().addService(new RemoteServiceHandler(moduleManager));

        Address address = new Address("not-important", 11, false);
        GRPCRemoteClient remoteClient = spy(new GRPCRemoteClient(moduleManager, address, 1, 10, 10, null));
        remoteClient.connect();

        doReturn(grpcServerRule.getChannel()).when(remoteClient).getChannel();

        for (int i = 0; i < 12; i++) {
            remoteClient.push(nextWorkerName, new TestStreamData());
        }

        TimeUnit.SECONDS.sleep(2);
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

    class TestWorker extends AbstractWorker {

        public TestWorker(ModuleDefineHolder moduleDefineHolder) {
            super(moduleDefineHolder);
        }

        @Override
        public void in(Object o) {
            TestStreamData streamData = (TestStreamData) o;
            Assert.assertEquals(987, streamData.value);
        }
    }
}
