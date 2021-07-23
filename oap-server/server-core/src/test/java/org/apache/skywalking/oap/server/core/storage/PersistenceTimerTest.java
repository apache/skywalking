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

package org.apache.skywalking.oap.server.core.storage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsPersistentWorker;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.worker.TopNStreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.worker.TopNWorker;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.junit.Assert;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class PersistenceTimerTest {

    @Test
    public void testExtractDataAndSave() throws Exception {
        Set<PrepareRequest> result = new HashSet();
        int count = 101;
        int workCount = 10;
        CoreModuleConfig moduleConfig = new CoreModuleConfig();
        moduleConfig.setPersistentPeriod(Integer.MAX_VALUE);
        IBatchDAO iBatchDAO = new IBatchDAO() {
            @Override
            public void insert(InsertRequest insertRequest) {

            }

            @Override
            public void flush(final List<PrepareRequest> prepareRequests) {
                synchronized (result) {
                    result.addAll(prepareRequests);
                }
            }
        };
        for (int i = 0; i < workCount; i++) {
            MetricsStreamProcessor.getInstance().getPersistentWorkers().add(genWorkers(i, count));
            TopNStreamProcessor.getInstance().getPersistentWorkers().add(genTopNWorkers(i, count));
        }
        ModuleManager moduleManager = mock(ModuleManager.class);
        ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);
        doReturn((ModuleProviderHolder) () -> moduleServiceHolder).when(moduleManager).find(anyString());
        doReturn(new MetricsCreatorNoop()).when(moduleServiceHolder).getService(MetricsCreator.class);
        doReturn(iBatchDAO).when(moduleServiceHolder).getService(IBatchDAO.class);
        PersistenceTimer.INSTANCE.isStarted = true;

        PersistenceTimer.INSTANCE.start(moduleManager, moduleConfig);
        Whitebox.invokeMethod(PersistenceTimer.INSTANCE, "extractDataAndSave", iBatchDAO);

        Assert.assertEquals(count * workCount * 2, result.size());
    }

    private MetricsPersistentWorker genWorkers(int num, int count) {
        MetricsPersistentWorker persistenceWorker = mock(MetricsPersistentWorker.class);
        doAnswer(invocation -> {
            List<MockStorageData> results = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                results.add(new MockStorageData(num + " " + UUID.randomUUID()));
            }
            return results;
        }).when(persistenceWorker).buildBatchRequests();
        return persistenceWorker;
    }

    private TopNWorker genTopNWorkers(int num, int count) {
        TopNWorker persistenceWorker = mock(TopNWorker.class);
        doAnswer(invocation -> {
            List<MockStorageData> results = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                results.add(new MockStorageData(num + " " + UUID.randomUUID()));
            }
            return results;
        }).when(persistenceWorker).buildBatchRequests();
        return persistenceWorker;
    }

    @Data
    static class MockStorageData implements StorageData {
        private final String id;

        @Override
        public String id() {
            return id;
        }

    }

}
