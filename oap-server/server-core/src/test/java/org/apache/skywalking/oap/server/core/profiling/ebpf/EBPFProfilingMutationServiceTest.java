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

package org.apache.skywalking.oap.server.core.profiling.ebpf;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamProcessor;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.type.EBPFNetworkKeepProfilingResult;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EBPFProfilingMutationServiceTest {
    private static final String INSTANCE_ID = "Y2hlY2tvdXQ=.1_Y2hlY2tvdXQtMQ==";

    /**
     * The copy that extends a network task has the minute it is created in as its time bucket, as every task has:
     * the task model has minute downsampling, and Elasticsearch writes a task into the daily index of that minute,
     * which the TTL deletes with its day.
     */
    @Test
    void keepsANetworkTaskAliveWithACopyOfTheMinuteItIsCreatedIn() throws Exception {
        // Created to end in thirty seconds, within the minute before its end in which a keep-alive extends it.
        final long createTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(600 - 30);
        final EBPFProfilingTaskRecord task = new EBPFProfilingTaskRecord();
        task.setServiceId("Y2hlY2tvdXQ=.1");
        task.setProcessLabelsJson(Const.EMPTY_STRING);
        task.setInstanceId(INSTANCE_ID);
        task.setStartTime(createTime);
        task.setTriggerType(EBPFProfilingTriggerType.FIXED_TIME.value());
        task.setFixedTriggerDuration(600);
        task.setTargetType(EBPFProfilingTargetType.NETWORK.value());
        task.setCreateTime(createTime);
        task.setLastUpdateTime(createTime);
        task.setTimeBucket(TimeBucket.getMinuteTimeBucket(createTime));
        task.generateLogicalId();
        final IEBPFProfilingTaskDAO dao = mock(IEBPFProfilingTaskDAO.class);
        when(dao.getTaskRecord(task.getLogicalId())).thenReturn(Collections.singletonList(task));
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder providerHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder serviceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(StorageModule.NAME)).thenReturn(providerHolder);
        when(providerHolder.provider()).thenReturn(serviceHolder);
        when(serviceHolder.getService(IEBPFProfilingTaskDAO.class)).thenReturn(dao);

        final NoneStreamProcessor tasks = mock(NoneStreamProcessor.class);
        final EBPFNetworkKeepProfilingResult result;
        final long before;
        final long after;
        try (MockedStatic<NoneStreamProcessor> processor = mockStatic(NoneStreamProcessor.class)) {
            processor.when(NoneStreamProcessor::getInstance).thenReturn(tasks);
            before = System.currentTimeMillis();
            result = new EBPFProfilingMutationService(moduleManager).keepEBPFNetworkProfiling(task.getLogicalId());
            after = System.currentTimeMillis();
        }

        assertTrue(result.isStatus(), result.getErrorReason());
        final ArgumentCaptor<NoneStream> written = ArgumentCaptor.forClass(NoneStream.class);
        verify(tasks).in(written.capture());
        final EBPFProfilingTaskRecord copy = (EBPFProfilingTaskRecord) written.getValue();
        assertTrue(copy.getCreateTime() >= before && copy.getCreateTime() <= after,
                   "created at " + copy.getCreateTime());
        assertEquals(task.getLogicalId(), copy.getLogicalId());
        assertEquals(1200, copy.getFixedTriggerDuration());
        assertEquals(TimeBucket.getMinuteTimeBucket(copy.getCreateTime()), copy.getTimeBucket());
    }
}
