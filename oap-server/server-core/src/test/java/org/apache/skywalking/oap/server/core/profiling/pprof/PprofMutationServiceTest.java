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

package org.apache.skywalking.oap.server.core.profiling.pprof;

import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamProcessor;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.PprofEventType;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskCreationResult;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskCreationType;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PprofMutationServiceTest {
    private static final String SERVICE_ID = "c2VydmljZQ==.1";

    /**
     * A CPU task created ten seconds ago runs for five minutes. Its row carries the second bucket of its creation, as
     * {@code createTask} stamps it, and the DAO keeps the rows whose time bucket is not past the bound, as the JDBC
     * and Elasticsearch DAOs filter.
     */
    @Test
    void refusesASecondTaskOfTheEventForTheServiceWhileTheFirstRuns() throws Exception {
        final long createTime = System.currentTimeMillis() - 10_000L;
        final PprofTask running = PprofTask.builder()
                                           .id(createTime + "_" + SERVICE_ID)
                                           .serviceId(SERVICE_ID)
                                           .events(PprofEventType.CPU)
                                           .createTime(createTime)
                                           .duration(5)
                                           .build();
        final long storedTimeBucket = TimeBucket.getRecordTimeBucket(createTime);
        final IPprofTaskQueryDAO dao = mock(IPprofTaskQueryDAO.class);
        when(dao.getTaskList(eq(SERVICE_ID), any(), anyLong(), any())).thenAnswer(invocation -> {
            final long endTimeBucket = invocation.getArgument(2);
            return storedTimeBucket <= endTimeBucket
                ? Collections.singletonList(running) : Collections.<PprofTask>emptyList();
        });

        final PprofTaskCreationResult result = new PprofMutationService(moduleManager(dao)).createTask(
            SERVICE_ID, List.of("instance-1"), 5, PprofEventType.CPU, 0);

        assertEquals(PprofTaskCreationType.ALREADY_PROFILING_ERROR, result.getCode());
    }

    /**
     * The row carries the second bucket of its creation, which the task lists, the creation check and the agent task
     * cache bound by second buckets.
     */
    @Test
    void writesTheTaskWithTheSecondBucketOfItsCreation() throws Exception {
        final IPprofTaskQueryDAO dao = mock(IPprofTaskQueryDAO.class);
        when(dao.getTaskList(any(), any(), any(), any())).thenReturn(Collections.emptyList());
        final NoneStreamProcessor processor = mock(NoneStreamProcessor.class);
        try (MockedStatic<NoneStreamProcessor> ignored = mockStatic(NoneStreamProcessor.class)) {
            ignored.when(NoneStreamProcessor::getInstance).thenReturn(processor);

            final PprofTaskCreationResult result = new PprofMutationService(moduleManager(dao)).createTask(
                SERVICE_ID, List.of("instance-1"), 5, PprofEventType.CPU, 0);

            assertEquals(PprofTaskCreationType.SUCCESS, result.getCode());
        }
        final ArgumentCaptor<NoneStream> written = ArgumentCaptor.forClass(NoneStream.class);
        verify(processor).in(written.capture());
        final PprofTaskRecord task = (PprofTaskRecord) written.getValue();
        assertEquals(TimeBucket.getRecordTimeBucket(task.getCreateTime()), task.getTimeBucket());
    }

    private static ModuleManager moduleManager(final IPprofTaskQueryDAO dao) {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder providerHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder serviceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(StorageModule.NAME)).thenReturn(providerHolder);
        when(providerHolder.provider()).thenReturn(serviceHolder);
        when(serviceHolder.getService(IPprofTaskQueryDAO.class)).thenReturn(dao);
        return moduleManager;
    }
}
