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

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

/**
 * Profile task process thread, dump segment executing thread stack.
 *
 * @author MrPro
 */
public class ProfileThread implements Runnable {

    private static final ILog logger = LogManager.getLogger(ProfileThread.class);

    // profiling task context
    private final ProfileTaskExecutionContext taskExecutionContext;

    private final ProfileTaskExecutionService profileTaskExecutionService;
    private final ProfileTaskChannelService profileTaskChannelService;

    public ProfileThread(ProfileTaskExecutionContext taskExecutionContext) {
        this.taskExecutionContext = taskExecutionContext;
        profileTaskExecutionService = ServiceManager.INSTANCE.findService(ProfileTaskExecutionService.class);
        profileTaskChannelService = ServiceManager.INSTANCE.findService(ProfileTaskChannelService.class);
    }

    @Override
    public void run() {

        try {
            profiling(taskExecutionContext);
        } catch (InterruptedException e) {
            // ignore interrupted
            // means current task has stopped
        } catch (Exception e) {
            logger.error(e, "Profiling task fail. taskId:{}", taskExecutionContext.getTask().getTaskId());
        } finally {
            // finally stop current profiling task, tell execution service task has stop
            profileTaskExecutionService.stopCurrentProfileTask(taskExecutionContext);
        }

    }

    /**
     * start profiling
     * @param executionContext
     */
    private void profiling(ProfileTaskExecutionContext executionContext) throws InterruptedException {

        int maxSleepPeriod = executionContext.getTask().getThreadDumpPeriod();

        // run loop when current thread still running
        long currentLoopStartTime = -1;
        while (!Thread.currentThread().isInterrupted()) {
            currentLoopStartTime = System.currentTimeMillis();

            // each all slot
            for (ThreadProfiler slot : executionContext.threadProfilerSlot()) {
                if (slot == null) {
                    continue;
                }

                switch (slot.profilingStatus()) {

                    case READY:
                        // check tracing context running time
                        slot.startProfilingIfNeed();
                        break;

                    case PROFILING:
                        // dump stack
                        TracingThreadSnapshot snapshot = slot.buildSnapshot();
                        if (snapshot != null) {
                            profileTaskChannelService.addProfilingSnapshot(snapshot);
                        } else {
                            // tell execution context current tracing thread dump failed, stop it
                            executionContext.stopTracingProfile(slot.tracingContext());
                        }
                        break;

                }
            }

            // sleep to next period
            // if out of period, sleep one period
            long needToSleep = (currentLoopStartTime + maxSleepPeriod) - System.currentTimeMillis();
            needToSleep = needToSleep > 0 ? needToSleep : maxSleepPeriod;
            Thread.sleep(needToSleep);
        }
    }

}
