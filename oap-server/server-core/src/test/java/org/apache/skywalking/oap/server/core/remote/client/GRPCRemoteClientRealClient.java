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

import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.remote.annotation.StreamDataClassGetter;
import org.apache.skywalking.oap.server.core.remote.data.StreamData;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.*;
import org.apache.skywalking.oap.server.testing.module.*;
import org.junit.Assert;

import static org.mockito.Mockito.*;

/**
 * @author peng-yongsheng
 */
public class GRPCRemoteClientRealClient {

    public static void main(String[] args) throws InterruptedException {
        Address address = new Address("localhost", 10000, false);
        ModuleManagerTesting moduleManager = new ModuleManagerTesting();
        MetricCreator metricCreator = mock(MetricCreator.class);
        when(metricCreator.createCounter(any(), any(), any(), any())).thenReturn(new CounterMetric() {
            @Override public void inc() {

            }

            @Override public void inc(double value) {

            }
        });
        ModuleDefineTesting telemetryModuleDefine = new ModuleDefineTesting();
        moduleManager.put(TelemetryModule.NAME, telemetryModuleDefine);
        telemetryModuleDefine.provider().registerServiceImplementation(MetricCreator.class, metricCreator);

        GRPCRemoteClient remoteClient = spy(new GRPCRemoteClient(moduleManager, new TestClassGetter(), address, 1, 10));
        remoteClient.connect();

        for (int i = 0; i < 10000; i++) {
            remoteClient.push(1, new TestStreamData());
            TimeUnit.SECONDS.sleep(1);
        }

        TimeUnit.MINUTES.sleep(10);
    }

    public static class TestClassGetter implements StreamDataClassGetter {

        @Override public int findIdByClass(Class streamDataClass) {
            return 1;
        }

        @Override public Class<StreamData> findClassById(int id) {
            Class<?> clazz = TestStreamData.class;
            return (Class<StreamData>)clazz;
        }
    }

    public static class TestStreamData extends StreamData {

        private long value;

        @Override public int remoteHashCode() {
            return 0;
        }

        @Override public void deserialize(RemoteData remoteData) {
            this.value = remoteData.getDataLongs(0);
        }

        @Override public RemoteData.Builder serialize() {
            RemoteData.Builder builder = RemoteData.newBuilder();
            builder.addDataLongs(987);
            return builder;
        }
    }

    static class TestWorker extends AbstractWorker {

        public TestWorker(int workerId) {
            super(workerId);
        }

        @Override public void in(Object o) {
            TestStreamData streamData = (TestStreamData)o;
            Assert.assertEquals(987, streamData.value);
        }
    }
}
