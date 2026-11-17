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

package org.apache.skywalking.oap.server.core.profiling.asyncprofiler;

import java.util.Collections;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerEventType;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskCreationResult;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTaskCreationType;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsyncProfilerMutationServiceTest {
    private static final String SERVICE_ID = "c2VydmljZQ==.1";

    /**
     * A task created ten seconds ago runs for a minute. Its row carries the second bucket of its creation, as
     * {@code createTask} stamps it, and the DAO keeps the rows whose time bucket is not past the bound, as the JDBC
     * and Elasticsearch DAOs filter.
     */
    @Test
    void refusesASecondTaskForTheServiceWhileTheFirstRuns() throws Exception {
        final long createTime = System.currentTimeMillis() - 10_000L;
        final AsyncProfilerTask running = AsyncProfilerTask.builder()
                                                           .id(createTime + "_" + SERVICE_ID)
                                                           .serviceId(SERVICE_ID)
                                                           .createTime(createTime)
                                                           .duration(60)
                                                           .build();
        final long storedTimeBucket = TimeBucket.getRecordTimeBucket(createTime);
        final IAsyncProfilerTaskQueryDAO dao = mock(IAsyncProfilerTaskQueryDAO.class);
        when(dao.getTaskList(eq(SERVICE_ID), any(), anyLong(), any())).thenAnswer(invocation -> {
            final long endTimeBucket = invocation.getArgument(2);
            return storedTimeBucket <= endTimeBucket
                ? Collections.singletonList(running) : Collections.<AsyncProfilerTask>emptyList();
        });
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder providerHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder serviceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(StorageModule.NAME)).thenReturn(providerHolder);
        when(providerHolder.provider()).thenReturn(serviceHolder);
        when(serviceHolder.getService(IAsyncProfilerTaskQueryDAO.class)).thenReturn(dao);

        final AsyncProfilerTaskCreationResult result = new AsyncProfilerMutationService(moduleManager).createTask(
            SERVICE_ID, List.of("instance-1"), 60, List.of(AsyncProfilerEventType.CPU), "");

        assertEquals(AsyncProfilerTaskCreationType.ALREADY_PROFILING_ERROR, result.getCode());
    }
}
