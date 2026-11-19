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

package org.apache.skywalking.oap.server.receiver.ebpf.provider.handler;

import io.grpc.stub.StreamObserver;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.ContinuousOnCPUProfilingTask;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.ContinuousProfilingReport;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamProcessor;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.continuous.IContinuousProfilingPolicyDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.receiver.ebpf.provider.EBPFReceiverModuleConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ContinuousProfilingServiceHandlerTest {
    /**
     * The task model has minute downsampling, and Elasticsearch writes a task into the daily index of its time bucket,
     * which the TTL deletes with its day.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void storesATriggeredTaskInTheMinuteItIsTriggeredIn() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder storage = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder storageServices = mock(ModuleServiceHolder.class);
        when(moduleManager.find(StorageModule.NAME)).thenReturn(storage);
        when(storage.provider()).thenReturn(storageServices);
        when(storageServices.getService(IContinuousProfilingPolicyDAO.class))
            .thenReturn(mock(IContinuousProfilingPolicyDAO.class));
        final ModuleProviderHolder core = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreServices = mock(ModuleServiceHolder.class);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(core);
        when(core.provider()).thenReturn(coreServices);
        when(coreServices.getService(CommandService.class)).thenReturn(new CommandService(moduleManager));
        final ContinuousProfilingServiceHandler handler =
            new ContinuousProfilingServiceHandler(moduleManager, new EBPFReceiverModuleConfig());

        final NoneStreamProcessor tasks = mock(NoneStreamProcessor.class);
        final long before;
        final long after;
        try (MockedStatic<NoneStreamProcessor> processor = mockStatic(NoneStreamProcessor.class)) {
            processor.when(NoneStreamProcessor::getInstance).thenReturn(tasks);
            before = System.currentTimeMillis();
            handler.reportProfilingTask(
                ContinuousProfilingReport.newBuilder()
                                         .setServiceName("checkout")
                                         .setInstanceName("checkout-1")
                                         .setProcessName("checkout")
                                         .setDuration(60)
                                         .setOnCPU(ContinuousOnCPUProfilingTask.newBuilder())
                                         .build(),
                mock(StreamObserver.class));
            after = System.currentTimeMillis();
        }

        final ArgumentCaptor<NoneStream> written = ArgumentCaptor.forClass(NoneStream.class);
        verify(tasks).in(written.capture());
        final EBPFProfilingTaskRecord task = (EBPFProfilingTaskRecord) written.getValue();
        assertTrue(task.getCreateTime() >= before && task.getCreateTime() <= after,
                   "created at " + task.getCreateTime());
        assertEquals(TimeBucket.getMinuteTimeBucket(task.getCreateTime()), task.getTimeBucket());
    }
}
