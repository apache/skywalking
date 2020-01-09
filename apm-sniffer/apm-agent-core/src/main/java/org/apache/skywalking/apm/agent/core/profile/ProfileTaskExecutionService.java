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

package org.apache.skywalking.apm.agent.core.profile;

import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.network.constants.ProfileConstants;
import org.apache.skywalking.apm.util.StringUtil;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Profile task executor, use {@link #addProfileTask(ProfileTask)} to add a new profile task.
 *
 * @author MrPro
 */
@DefaultImplementor
public class ProfileTaskExecutionService implements BootService {

    private static final ILog logger = LogManager.getLogger(ProfileTaskExecutionService.class);

    // add a schedule while waiting for the task to start or finish
    private final static ScheduledExecutorService PROFILE_TASK_SCHEDULE = Executors.newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory("PROFILE-TASK-SCHEDULE"));

    // last command create time, use to next query task list
    private volatile long lastCommandCreateTime = -1;

    // current processing profile task context
    private final AtomicReference<ProfileTaskExecutionContext> taskExecutionContext = new AtomicReference<>();

    // profile task list, include running and waiting running tasks
    private final List<ProfileTask> profileTaskList = Collections.synchronizedList(new LinkedList<>());

    /**
     * get profile task from OAP
     * @param task
     */
    public void addProfileTask(ProfileTask task) {
        // update last command create time
        if (task.getCreateTime() > lastCommandCreateTime) {
            lastCommandCreateTime = task.getCreateTime();
        }

        // check profile task limit
        final String dataError = checkProfileTaskSuccess(task);
        if (dataError != null) {
            logger.warn("check command error, cannot process this profile task. reason: {}", dataError);
            return;
        }

        // add task to list
        profileTaskList.add(task);

        // schedule to start task
        long timeToProcessMills = task.getStartTime() - System.currentTimeMillis();
        PROFILE_TASK_SCHEDULE.schedule(new Runnable() {
            @Override
            public void run() {
                processProfileTask(task);
            }
        }, timeToProcessMills, TimeUnit.MILLISECONDS);
    }

    /**
     * active the selected profile task to execution task, and start a removal task for it.
     * @param task
     */
    private synchronized void processProfileTask(ProfileTask task) {
        // make sure prev profile task already stopped
        stopCurrentProfileTask(taskExecutionContext.get());

        // make stop task schedule and task context
        // TODO process task on next step
        final ProfileTaskExecutionContext currentStartedTaskContext = new ProfileTaskExecutionContext(task, System.currentTimeMillis());
        taskExecutionContext.set(currentStartedTaskContext);

        PROFILE_TASK_SCHEDULE.schedule(new Runnable() {
            @Override
            public void run() {
                stopCurrentProfileTask(currentStartedTaskContext);
            }
        }, task.getDuration(), TimeUnit.MINUTES);
    }

    /**
     * stop profile task, remove context data
     */
    private synchronized void stopCurrentProfileTask(ProfileTaskExecutionContext needToStop) {
        // stop same context only
        if (needToStop == null || !taskExecutionContext.compareAndSet(needToStop, null)) {
            return;
        }

        // remove task
        profileTaskList.remove(needToStop.getTask());

        // TODO notify OAP current profile task execute finish
    }

    @Override
    public void prepare() throws Throwable {

    }

    @Override
    public void boot() throws Throwable {

    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        PROFILE_TASK_SCHEDULE.shutdown();
    }

    public long getLastCommandCreateTime() {
        return lastCommandCreateTime;
    }

    /**
     * check profile task data success, make the re-check, prevent receiving wrong data from database or OAP
     * @param task
     * @return
     */
    private String checkProfileTaskSuccess(ProfileTask task) {
        // endpoint name
        if (StringUtil.isEmpty(task.getEndpointName())) {
            return "endpoint name cannot be empty";
        }

        // duration
        if (task.getDuration() < ProfileConstants.TASK_DURATION_MIN_MINUTE) {
            return "monitor duration must greater than " + ProfileConstants.TASK_DURATION_MIN_MINUTE + " minutes";
        }
        if (task.getDuration() > ProfileConstants.TASK_DURATION_MAX_MINUTE) {
            return "The duration of the monitoring task cannot be greater than " + ProfileConstants.TASK_DURATION_MAX_MINUTE + " minutes";
        }

        // min duration threshold
        if (task.getMinDurationThreshold() < 0) {
            return "min duration threshold must greater than or equals zero";
        }

        // dump period
        if (task.getThreadDumpPeriod() < ProfileConstants.TASK_DUMP_PERIOD_MIN_MILLIS) {
            return "dump period must be greater than or equals " + ProfileConstants.TASK_DUMP_PERIOD_MIN_MILLIS + " milliseconds";
        }

        // max sampling count
        if (task.getMaxSamplingCount() <= 0) {
            return "max sampling count must greater than zero";
        }
        if (task.getMaxSamplingCount() >= ProfileConstants.TASK_MAX_SAMPLING_COUNT) {
            return "max sampling count must less than " + ProfileConstants.TASK_MAX_SAMPLING_COUNT;
        }

        // check task queue, check only one task in a certain time
        long taskProcessFinishTime = calcProfileTaskFinishTime(task);
        for (ProfileTask profileTask : profileTaskList) {

            // if the end time of the task to be added is during the execution of any data, means is a error data
            if (taskProcessFinishTime >= profileTask.getStartTime() && taskProcessFinishTime <= calcProfileTaskFinishTime(profileTask)) {
                return "there already have processing task in time range, could not add a new task again. processing task monitor endpoint name: " + profileTask.getEndpointName();
            }
        }

        return null;
    }

    private long calcProfileTaskFinishTime(ProfileTask task) {
        return task.getStartTime() + TimeUnit.MINUTES.toMillis(task.getDuration());
    }

}
