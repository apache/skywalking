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
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

import java.util.ArrayList;

/**
 * Profile task process thread, dump segment executing thread stack.
 *
 * @author MrPro
 */
public class ProfileThread implements Runnable {

    private static final ILog logger = LogManager.getLogger(ProfileThread.class);

    // per segment max profiling time (millisecond)
    private final long maxProfilingTimeMills;

    // profiling task context
    private final ProfileTaskExecutionContext taskExecutionContext;

    private final ProfileTaskExecutionService profileTaskExecutionService;
    private final ProfileTaskChannelService profileTaskChannelService;

    public ProfileThread(ProfileTaskExecutionContext taskExecutionContext, long maxProfilingTimeMills) {
        this.taskExecutionContext = taskExecutionContext;
        this.maxProfilingTimeMills = maxProfilingTimeMills;
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
        int minDurationThreshold = executionContext.getTask().getMinDurationThreshold();

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
                        if (System.currentTimeMillis() - slot.tracingContext().createTime() > minDurationThreshold) {
                            slot.startProfiling();
                        }
                        break;

                    case PROFILING:
                        // dump stack
                        if (!dumpSegment(slot)) {
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

    /**
     * dump segemnt thread stack
     * @param threadProfiler
     * @return
     */
    private boolean dumpSegment(ThreadProfiler threadProfiler) {
        // dump stack
        if (!isSegmentProfilingContinuable(threadProfiler)) {
            return false;
        }

        return dumpThread(threadProfiler);
    }

    /**
     * dump thread stack, and push data to backend
     * @param threadProfiler
     * @return still can dump
     */
    private boolean dumpThread(ThreadProfiler threadProfiler) {
        long currentTime = System.currentTimeMillis();
        // dump thread
        StackTraceElement[] stackTrace;
        try {
            stackTrace = threadProfiler.targetThread().getStackTrace();

            // stack depth is zero, means thread is already run finished
            if (stackTrace.length == 0) {
                return false;
            }
        } catch (Exception e) {
            // dump error ignore and make this profiler stop
            return false;
        }

        int dumpElementCount = Math.min(stackTrace.length, Config.Profile.DUMP_MAX_STACK_DEPTH);

        // use inverted order, because thread dump is start with bottom
        final ArrayList<String> stackList = new ArrayList<>(dumpElementCount);
        for (int i = dumpElementCount - 1; i >= 0; i--) {
            stackList.add(buildStackElementCodeSignature(stackTrace[i]));
        }

        // build snapshot and send
        TracingThreadSnapshot snapshot = threadProfiler.buildSnapshot(currentTime, stackList);
        profileTaskChannelService.addProfilingSnapshot(snapshot);
        return true;
    }

    private String buildStackElementCodeSignature(StackTraceElement element) {
        // className.methodName:lineNumber
        return element.getClassName() + "." + element.getMethodName() + ":" + element.getLineNumber();
    }

    /**
     * check segment profiling is should continue
     * @param profiler
     * @return
     */
    private boolean isSegmentProfilingContinuable(ThreadProfiler profiler) {
        // check is out of limit monitor time
        return System.currentTimeMillis() - profiler.profilingStartTime() < maxProfilingTimeMills;
    }

}
