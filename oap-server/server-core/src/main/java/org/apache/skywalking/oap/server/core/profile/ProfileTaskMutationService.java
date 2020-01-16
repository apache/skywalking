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
package org.apache.skywalking.oap.server.core.profile;

import org.apache.skywalking.apm.network.constants.ProfileConstants;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamingProcessor;
import org.apache.skywalking.oap.server.core.profile.entity.ProfileTaskCreationResult;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author MrPro
 */
public class ProfileTaskMutationService implements Service {

    private final ModuleManager moduleManager;
    private IProfileTaskQueryDAO profileTaskQueryDAO;

    public ProfileTaskMutationService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IProfileTaskQueryDAO getProfileTaskDAO() {
        if (profileTaskQueryDAO == null) {
            this.profileTaskQueryDAO = moduleManager.find(StorageModule.NAME).provider().getService(IProfileTaskQueryDAO.class);
        }
        return profileTaskQueryDAO;
    }

    /**
     * create new profile task
     * @param serviceId monitor service id
     * @param endpointName monitor endpoint name
     * @param monitorStartTime create fix start time task when it's bigger 0
     * @param monitorDuration monitor task duration(minute)
     * @param minDurationThreshold min duration threshold
     * @param dumpPeriod dump period
     * @param maxSamplingCount max trace count on sniffer
     * @return task create result
     */
    public ProfileTaskCreationResult createTask(final int serviceId, final String endpointName, final long monitorStartTime, final int monitorDuration,
                                                final int minDurationThreshold, final int dumpPeriod, final int maxSamplingCount) throws IOException {

        // calculate task execute range
        long taskStartTime = monitorStartTime > 0 ? monitorStartTime : System.currentTimeMillis();
        long taskEndTime = taskStartTime + TimeUnit.MINUTES.toMillis(monitorDuration);

        // check data
        final String errorMessage = checkDataSuccess(serviceId, endpointName, taskStartTime, taskEndTime, monitorDuration, minDurationThreshold, dumpPeriod, maxSamplingCount);
        if (errorMessage != null) {
            return ProfileTaskCreationResult.builder().errorReason(errorMessage).build();
        }

        // create task
        final long createTime = System.currentTimeMillis();
        final ProfileTaskNoneStream task = new ProfileTaskNoneStream();
        task.setServiceId(serviceId);
        task.setEndpointName(endpointName.trim());
        task.setStartTime(taskStartTime);
        task.setDuration(monitorDuration);
        task.setMinDurationThreshold(minDurationThreshold);
        task.setDumpPeriod(dumpPeriod);
        task.setCreateTime(createTime);
        task.setMaxSamplingCount(maxSamplingCount);
        task.setTimeBucket(TimeBucket.getRecordTimeBucket(taskEndTime));
        NoneStreamingProcessor.getInstance().in(task);

        return ProfileTaskCreationResult.builder().id(task.id()).build();
    }

    private String checkDataSuccess(final Integer serviceId, final String endpointName, final long monitorStartTime, final long monitorEndTime, final int monitorDuration,
                                    final int minDurationThreshold, final int dumpPeriod, final int maxSamplingCount) throws IOException {
        // basic check
        if (serviceId == null) {
            return "service cannot be null";
        }
        if (StringUtil.isEmpty(endpointName)) {
            return "endpoint name cannot be empty";
        }
        if (monitorDuration < ProfileConstants.TASK_DURATION_MIN_MINUTE) {
            return "monitor duration must greater than " + ProfileConstants.TASK_DURATION_MIN_MINUTE + " minutes";
        }
        if (minDurationThreshold < 0) {
            return "min duration threshold must greater than or equals zero";
        }
        if (maxSamplingCount <= 0) {
            return "max sampling count must greater than zero";
        }

        // check limit
        if (monitorDuration > ProfileConstants.TASK_DURATION_MAX_MINUTE) {
            return "The duration of the monitoring task cannot be greater than " + ProfileConstants.TASK_DURATION_MAX_MINUTE + " minutes";
        }

        if (dumpPeriod < ProfileConstants.TASK_DUMP_PERIOD_MIN_MILLIS) {
            return "dump period must be greater than or equals " + ProfileConstants.TASK_DUMP_PERIOD_MIN_MILLIS + " milliseconds";
        }

        if (maxSamplingCount >= ProfileConstants.TASK_MAX_SAMPLING_COUNT) {
            return "max sampling count must less than " + ProfileConstants.TASK_MAX_SAMPLING_COUNT;
        }

        // Each service can monitor up to 1 endpoints during the execution of tasks
        long startTimeBucket = TimeBucket.getTimeBucket(monitorStartTime, Downsampling.Second);
        long endTimeBucket = TimeBucket.getTimeBucket(monitorEndTime, Downsampling.Second);
        final List<ProfileTask> alreadyHaveTaskList = getProfileTaskDAO().getTaskList(serviceId, null, startTimeBucket, endTimeBucket, 1);
        if (CollectionUtils.isNotEmpty(alreadyHaveTaskList)) {
            // if any task time bucket in this range, means already have task, because time bucket is base on task end time
            return "current service already has monitor task execute at this time";
        }

        return null;
    }

}
