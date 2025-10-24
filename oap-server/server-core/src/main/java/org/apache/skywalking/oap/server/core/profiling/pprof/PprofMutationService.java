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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamProcessor;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.PprofEventType;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskCreationResult;
import org.apache.skywalking.oap.server.core.query.type.PprofTaskCreationType;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

@Slf4j
@RequiredArgsConstructor
public class PprofMutationService implements Service {
    private final ModuleManager moduleManager;

    private IPprofTaskQueryDAO taskQueryDAO;

    private IPprofTaskQueryDAO getPprofTaskDAO() {
        if (taskQueryDAO == null) {
            this.taskQueryDAO = moduleManager.find(StorageModule.NAME)
                                             .provider()
                                             .getService(IPprofTaskQueryDAO.class);
        }
        return taskQueryDAO;
    }

    public PprofTaskCreationResult createTask(String serviceId,
                                              List<String> serviceInstanceIds,
                                              int duration,
                                              PprofEventType events,
                                              int dumpPeriod) throws IOException {
        long createTime = System.currentTimeMillis();
        // check data
        PprofTaskCreationResult checkResult = checkDataSuccess(
            serviceId, serviceInstanceIds, duration, createTime, events, dumpPeriod
        );
        if (checkResult != null) {
            return checkResult;
        }
        // create task
        PprofTaskRecord task = new PprofTaskRecord();
        String taskId = createTime + Const.ID_CONNECTOR + serviceId;
        task.setTaskId(taskId);
        task.setServiceId(serviceId);
        task.setServiceInstanceIdsFromList(serviceInstanceIds);
        task.setDuration(duration);
        task.setEvents(events.toString());
        task.setDumpPeriod(dumpPeriod);
        task.setCreateTime(createTime);
        task.setTimeBucket(TimeBucket.getRecordTimeBucket(createTime));
        NoneStreamProcessor.getInstance().in(task);
        return PprofTaskCreationResult.builder()
                                      .id(task.id().build())
                                      .code(PprofTaskCreationType.SUCCESS)
                                      .build();
    }

    private PprofTaskCreationResult checkDataSuccess(String serviceId,
                                                     List<String> serviceInstanceIds,
                                                     int duration,
                                                     long createTime,
                                                     PprofEventType events,
                                                     int dumpPeriod) throws IOException {
        String checkArgumentMessage = checkArgumentError(serviceId, serviceInstanceIds, duration, events, dumpPeriod);
        if (checkArgumentMessage != null) {
            return PprofTaskCreationResult.builder()
                                          .code(PprofTaskCreationType.ARGUMENT_ERROR)
                                          .errorReason(checkArgumentMessage)
                                          .build();
        }
        String checkTaskProfilingMessage = checkTaskProfiling(serviceId, events, createTime);
        if (checkTaskProfilingMessage != null) {
            return PprofTaskCreationResult.builder()
                                          .code(PprofTaskCreationType.ALREADY_PROFILING_ERROR)
                                          .errorReason(checkTaskProfilingMessage)
                                          .build();
        }
        return null;
    }

    private String checkArgumentError(String serviceId,
                                      List<String> serviceInstanceIds,
                                      int duration,
                                      PprofEventType events,
                                      int dumpPeriod) {
        if (serviceId == null) {
            return "service cannot be null";
        }
        if (events == null) {
            return "events cannot be empty";
        }
        if (events == PprofEventType.CPU || events == PprofEventType.BLOCK || events == PprofEventType.MUTEX) {
            if (duration <= 0) {
                return "duration cannot be negative";
            }
            if (duration > 15) {
                return "duration cannot be greater than 15 minutes";
            }
        }
        if (events == PprofEventType.BLOCK || events == PprofEventType.MUTEX) {
            if (dumpPeriod <= 0) {
                return "dumpPeriod cannot be negative";
            }
        }
        if (CollectionUtils.isEmpty(serviceInstanceIds)) {
            return "serviceInstanceIds cannot be empty";
        }
        return null;
    }

    private String checkTaskProfiling(String serviceId,
                                      PprofEventType events,
                                      long createTime) throws IOException {
        // Each service can only enable one task at a time
        long endTimeBucket = TimeBucket.getRecordTimeBucket(createTime);
        final List<PprofTask> alreadyHaveTaskList = getPprofTaskDAO().getTaskList(
            serviceId, null, endTimeBucket, null
        );
        if (CollectionUtils.isNotEmpty(alreadyHaveTaskList)) {
            for (PprofTask task : alreadyHaveTaskList) {
                if (task.getEvents().equals(events) && task.getCreateTime() + TimeUnit.MINUTES.toMillis(
                    task.getDuration()) >= createTime) {
                    // if the endTime is greater or equal than the createTime of the newly created task, i.e. there is overlap between two tasks, it is an invalid case, it will return an error
                    return "current service already has monitor pprof task execute at this time";
                }
            }
        }
        return null;
    }
}
