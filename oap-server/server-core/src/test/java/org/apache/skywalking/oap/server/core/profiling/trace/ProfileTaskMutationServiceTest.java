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

package org.apache.skywalking.oap.server.core.profiling.trace;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamProcessor;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskCreationResult;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ProfileTaskMutationServiceTest {
    private static final String SERVICE_ID = "c2VydmljZQ==.1";
    private static final String CONFLICT = "current service already has monitor task execute at this time";

    /**
     * A new task from 12:00:10 to 12:05:10. The task list reads whole minutes, so it also holds a task starting at
     * 12:05:50, in the new task's last minute but after it ends: that task does not overlap it.
     */
    @Test
    void acceptsATaskEndingBeforeOneThatStartsLaterInItsLastMinute() throws Exception {
        final IProfileTaskQueryDAO dao = taskList(task("11:50:00", 5), task("12:05:50", 5));

        assertNull(create(dao, "12:00:10", 5).getErrorReason());
    }

    /**
     * The task overlapping the new one started before the one listed latest, which starts after the new one ends.
     */
    @Test
    void refusesATaskOverlappingOneThatStartedBeforeTheLatestListed() throws Exception {
        final IProfileTaskQueryDAO dao = taskList(task("11:58:00", 3), task("12:05:50", 5));

        assertEquals(CONFLICT, create(dao, "12:00:10", 5).getErrorReason());
    }

    @Test
    void refusesATaskStartingBeforeTheLatestOneEnds() throws Exception {
        final IProfileTaskQueryDAO dao = taskList(task("11:58:00", 3));

        assertEquals(CONFLICT, create(dao, "12:01:00", 5).getErrorReason());
        assertNull(create(dao, "12:01:01", 5).getErrorReason());
    }

    private static ProfileTaskCreationResult create(final IProfileTaskQueryDAO dao,
                                                    final String start,
                                                    final int minutes) throws Exception {
        try (MockedStatic<NoneStreamProcessor> processor = mockStatic(NoneStreamProcessor.class)) {
            processor.when(NoneStreamProcessor::getInstance).thenReturn(mock(NoneStreamProcessor.class));
            return new ProfileTaskMutationService(moduleManager(dao))
                .createTask(SERVICE_ID, "/checkout", millis(start), minutes, 0, 10, 5);
        }
    }

    /**
     * The tasks of the service whose minute bucket is not past the end bucket, latest first, up to the limit, as the
     * storages list them.
     */
    private static IProfileTaskQueryDAO taskList(final ProfileTask... tasks) throws Exception {
        final IProfileTaskQueryDAO dao = mock(IProfileTaskQueryDAO.class);
        when(dao.getTaskList(eq(SERVICE_ID), any(), any(), any(), any())).thenAnswer(invocation -> {
            final Long endTimeBucket = invocation.getArgument(3);
            final Integer limit = invocation.getArgument(4);
            return Arrays.stream(tasks)
                         .filter(task -> TimeBucket.getMinuteTimeBucket(task.getStartTime()) <= endTimeBucket)
                         .sorted(Comparator.comparingLong(ProfileTask::getStartTime).reversed())
                         .limit(limit)
                         .collect(Collectors.toList());
        });
        return dao;
    }

    private static ProfileTask task(final String start, final int minutes) {
        return ProfileTask.builder().serviceId(SERVICE_ID).startTime(millis(start)).duration(minutes).build();
    }

    private static long millis(final String time) {
        return Instant.parse("2026-11-18T" + time + "Z").toEpochMilli();
    }

    private static ModuleManager moduleManager(final IProfileTaskQueryDAO dao) {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        final ModuleProviderHolder providerHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder serviceHolder = mock(ModuleServiceHolder.class);
        when(moduleManager.find(StorageModule.NAME)).thenReturn(providerHolder);
        when(providerHolder.provider()).thenReturn(serviceHolder);
        when(serviceHolder.getService(IProfileTaskQueryDAO.class)).thenReturn(dao);
        return moduleManager;
    }
}
