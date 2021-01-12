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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.context.TracingThreadListener;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.network.constants.ProfileConstants;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * Profile task executor, use {@link #addProfileTask(ProfileTask)} to add a new profile task.
 */
@DefaultImplementor
public class ProfileTaskExecutionService implements BootService, TracingThreadListener {

    private static final ILog LOGGER = LogManager.getLogger(ProfileTaskExecutionService.class);

    // add a schedule while waiting for the task to start or finish
    private final static ScheduledExecutorService PROFILE_TASK_SCHEDULE = Executors.newSingleThreadScheduledExecutor(
        new DefaultNamedThreadFactory("PROFILE-TASK-SCHEDULE"));

    // last command create time, use to next query task list
    private volatile long lastCommandCreateTime = -1;

    // current processing profile task context
    private final AtomicReference<ProfileTaskExecutionContext> taskExecutionContext = new AtomicReference<>();

    // profile executor thread pool, only running one thread
    private final static ExecutorService PROFILE_EXECUTOR = Executors.newSingleThreadExecutor(
        new DefaultNamedThreadFactory("PROFILING-TASK"));

    // profile task list, include running and waiting running tasks
    private final List<ProfileTask> profileTaskList = Collections.synchronizedList(new LinkedList<>());

    /**
     * add profile task from OAP
     */
    public void addProfileTask(ProfileTask task) {
        // update last command create time
        if (task.getCreateTime() > lastCommandCreateTime) {
            lastCommandCreateTime = task.getCreateTime();
        }

        // check profile task limit
        final CheckResult dataError = checkProfileTaskSuccess(task);
        if (!dataError.isSuccess()) {
            LOGGER.warn(
                "check command error, cannot process this profile task. reason: {}", dataError.getErrorReason());
            return;
        }

        // add task to list
        profileTaskList.add(task);

        // schedule to start task
        long timeToProcessMills = task.getStartTime() - System.currentTimeMillis();
        PROFILE_TASK_SCHEDULE.schedule(() -> processProfileTask(task), timeToProcessMills, TimeUnit.MILLISECONDS);
    }

    /**
     * check and add {@link TracingContext} profiling
     */
    public ProfileStatusReference addProfiling(TracingContext tracingContext,
                                               String traceSegmentId,
                                               String firstSpanOPName) {
        // get current profiling task, check need profiling
        final ProfileTaskExecutionContext executionContext = taskExecutionContext.get();
        if (executionContext == null) {
            return ProfileStatusReference.createWithNone();
        }

        return executionContext.attemptProfiling(tracingContext, traceSegmentId, firstSpanOPName);
    }

    /**
     * Re-check current trace need profiling, in case that third-party plugins change the operation name.
     */
    public void profilingRecheck(TracingContext tracingContext, String traceSegmentId, String firstSpanOPName) {
        // get current profiling task, check need profiling
        final ProfileTaskExecutionContext executionContext = taskExecutionContext.get();
        if (executionContext == null) {
            return;
        }

        executionContext.profilingRecheck(tracingContext, traceSegmentId, firstSpanOPName);
    }

    /**
     * active the selected profile task to execution task, and start a removal task for it.
     */
    private synchronized void processProfileTask(ProfileTask task) {
        // make sure prev profile task already stopped
        stopCurrentProfileTask(taskExecutionContext.get());

        // make stop task schedule and task context
        final ProfileTaskExecutionContext currentStartedTaskContext = new ProfileTaskExecutionContext(task);
        taskExecutionContext.set(currentStartedTaskContext);

        // start profiling this task
        currentStartedTaskContext.startProfiling(PROFILE_EXECUTOR);

        PROFILE_TASK_SCHEDULE.schedule(
            () -> stopCurrentProfileTask(currentStartedTaskContext), task.getDuration(), TimeUnit.MINUTES);
    }

    /**
     * stop profile task, remove context data
     */
    synchronized void stopCurrentProfileTask(ProfileTaskExecutionContext needToStop) {
        // stop same context only
        if (needToStop == null || !taskExecutionContext.compareAndSet(needToStop, null)) {
            return;
        }

        // current execution stop running
        needToStop.stopProfiling();

        // remove task
        profileTaskList.remove(needToStop.getTask());

        // notify profiling task has finished
        ServiceManager.INSTANCE.findService(ProfileTaskChannelService.class)
                               .notifyProfileTaskFinish(needToStop.getTask());
    }

    @Override
    public void prepare() {
    }

    @Override
    public void boot() {
    }

    @Override
    public void onComplete() {
        // add trace finish notification
        TracingContext.TracingThreadListenerManager.add(this);
    }

    @Override
    public void shutdown() {
        // remove trace listener
        TracingContext.TracingThreadListenerManager.remove(this);

        PROFILE_TASK_SCHEDULE.shutdown();

        PROFILE_EXECUTOR.shutdown();
    }

    public long getLastCommandCreateTime() {
        return lastCommandCreateTime;
    }

    /**
     * check profile task data success, make the re-check, prevent receiving wrong data from database or OAP
     */
    private CheckResult checkProfileTaskSuccess(ProfileTask task) {
        // endpoint name
        if (StringUtil.isEmpty(task.getFirstSpanOPName())) {
            return new CheckResult(false, "endpoint name cannot be empty");
        }

        // duration
        if (task.getDuration() < ProfileConstants.TASK_DURATION_MIN_MINUTE) {
            return new CheckResult(
                false, "monitor duration must greater than " + ProfileConstants.TASK_DURATION_MIN_MINUTE + " minutes");
        }
        if (task.getDuration() > ProfileConstants.TASK_DURATION_MAX_MINUTE) {
            return new CheckResult(
                false,
                "The duration of the monitoring task cannot be greater than " + ProfileConstants.TASK_DURATION_MAX_MINUTE + " minutes"
            );
        }

        // min duration threshold
        if (task.getMinDurationThreshold() < 0) {
            return new CheckResult(false, "min duration threshold must greater than or equals zero");
        }

        // dump period
        if (task.getThreadDumpPeriod() < ProfileConstants.TASK_DUMP_PERIOD_MIN_MILLIS) {
            return new CheckResult(
                false,
                "dump period must be greater than or equals " + ProfileConstants.TASK_DUMP_PERIOD_MIN_MILLIS + " milliseconds"
            );
        }

        // max sampling count
        if (task.getMaxSamplingCount() <= 0) {
            return new CheckResult(false, "max sampling count must greater than zero");
        }
        if (task.getMaxSamplingCount() >= ProfileConstants.TASK_MAX_SAMPLING_COUNT) {
            return new CheckResult(
                false, "max sampling count must less than " + ProfileConstants.TASK_MAX_SAMPLING_COUNT);
        }

        // check task queue, check only one task in a certain time
        long taskProcessFinishTime = calcProfileTaskFinishTime(task);
        for (ProfileTask profileTask : profileTaskList) {

            // if the end time of the task to be added is during the execution of any data, means is a error data
            if (taskProcessFinishTime >= profileTask.getStartTime() && taskProcessFinishTime <= calcProfileTaskFinishTime(
                profileTask)) {
                return new CheckResult(
                    false,
                    "there already have processing task in time range, could not add a new task again. processing task monitor endpoint name: "
                        + profileTask.getFirstSpanOPName()
                );
            }
        }

        return new CheckResult(true, null);
    }

    private long calcProfileTaskFinishTime(ProfileTask task) {
        return task.getStartTime() + TimeUnit.MINUTES.toMillis(task.getDuration());
    }

    @Override
    public void afterMainThreadFinish(TracingContext tracingContext) {
        if (tracingContext.profileStatus().isBeingWatched()) {
            // stop profiling tracing context
            ProfileTaskExecutionContext currentExecutionContext = taskExecutionContext.get();
            if (currentExecutionContext != null) {
                currentExecutionContext.stopTracingProfile(tracingContext);
            }
        }
    }

    /**
     * check profile task is processable
     */
    private static class CheckResult {
        private boolean success;
        private String errorReason;

        public CheckResult(boolean success, String errorReason) {
            this.success = success;
            this.errorReason = errorReason;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorReason() {
            return errorReason;
        }
    }
}
