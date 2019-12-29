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
package org.apache.skywalking.oap.server.core.mutation;

import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamingProcessor;
import org.apache.skywalking.oap.server.core.profile.ThreadMonitorTaskNoneStream;
import org.apache.skywalking.oap.server.core.mutation.entity.ThreadMonitorTaskCreationResult;
import org.apache.skywalking.oap.server.core.query.entity.ThreadMonitorTask;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profile.IThreadMonitorTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author MrPro
 */
public class ThreadMonitorTaskMutationService implements Service {

    private final ModuleManager moduleManager;
    private IThreadMonitorTaskQueryDAO threadMonitorTaskQueryDAO;

    public ThreadMonitorTaskMutationService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IThreadMonitorTaskQueryDAO getThreadMonitorTaskDAO() {
        if (threadMonitorTaskQueryDAO == null) {
            this.threadMonitorTaskQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IThreadMonitorTaskQueryDAO.class);
        }
        return threadMonitorTaskQueryDAO;
    }

    /**
     * create new thread monitor task
     * @param serviceId monitor service id
     * @param endpointName monitor endpoint name
     * @param monitorStartTime create fix start time task when it's bigger 0
     * @param monitorDuration monitor task duration(second)
     * @param minDurationThreshold min duration threshold
     * @param dumpPeriod dump period
     * @return task create result
     */
    public ThreadMonitorTaskCreationResult createTask(final int serviceId, final String endpointName, final long monitorStartTime, final int monitorDuration,
                                                      final int minDurationThreshold, final int dumpPeriod) throws IOException {

        // calculate task execute range
        long taskStartTime = monitorStartTime > 0 ? monitorStartTime : System.currentTimeMillis();
        long taskEndTime = taskStartTime + TimeUnit.SECONDS.toMillis(monitorDuration);

        // check data
        final String errorMessage = checkDataSuccess(serviceId, endpointName, taskStartTime, taskEndTime, monitorDuration, minDurationThreshold, dumpPeriod);
        if (errorMessage != null) {
            return ThreadMonitorTaskCreationResult.builder().errorReason(errorMessage).build();
        }

        // create task
        final long createTime = System.currentTimeMillis();
        final ThreadMonitorTaskNoneStream task = new ThreadMonitorTaskNoneStream();
        task.setServiceId(serviceId);
        task.setEndpointName(endpointName.trim());
        task.setStartTime(taskStartTime);
        task.setDuration(monitorDuration);
        task.setMinDurationThreshold(minDurationThreshold);
        task.setDumpPeriod(dumpPeriod);
        task.setCreateTime(createTime);
        task.setTimeBucket(TimeBucket.getRecordTimeBucket(taskEndTime));
        NoneStreamingProcessor.getInstance().in(task);

        return ThreadMonitorTaskCreationResult.builder().id(task.id()).build();
    }

    private String checkDataSuccess(final Integer serviceId, final String endpointName, final long monitorStartTime, final long monitorEndTime, final int monitorDuration,
                                    final int minDurationThreshold, final int dumpPeriod) throws IOException {
        // basic check
        if (serviceId == null) {
            return "service cannot be null";
        }
        if (StringUtil.isBlank(endpointName)) {
            return "endpoint name cannot be empty";
        }
        if (monitorEndTime - monitorStartTime < TimeUnit.MINUTES.toMillis(1)) {
            return "monitor duration must greater than 1 minutes";
        }
        if (minDurationThreshold < 0) {
            return "min duration threshold must greater than or equals zero";
        }

        // check limit
        // The duration of the monitoring task cannot be greater than 15 minutes
        final long maxMonitorDurationInSec = TimeUnit.MINUTES.toSeconds(15);
        if (monitorDuration > maxMonitorDurationInSec) {
            return "The duration of the monitoring task cannot be greater than 15 minutes";
        }

        // dump period must be greater than or equals 10 milliseconds
        if (dumpPeriod < 10) {
            return "dump period must be greater than or equals 10 milliseconds";
        }

        // Each service can monitor up to 1 endpoints during the execution of tasks
        long searchStartTime = monitorStartTime - TimeUnit.SECONDS.toMillis(maxMonitorDurationInSec);
        long searchEndTime = monitorEndTime;
        final List<ThreadMonitorTask> alreadyHaveTaskList = getThreadMonitorTaskDAO().getTaskListSearchOnStartTime(serviceId, searchStartTime, searchEndTime);
        if (CollectionUtils.isNotEmpty(alreadyHaveTaskList)) {
            // check has any task end time bigger than the start time of this task
            for (ThreadMonitorTask alreadyHaveTask : alreadyHaveTaskList) {
                if (alreadyHaveTask.getStartTime() + TimeUnit.SECONDS.toMillis(alreadyHaveTask.getDuration()) > monitorStartTime) {
                    return "current service already has monitor task execute at this time";
                }
            }
        }

        return null;
    }

}
