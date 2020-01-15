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

import com.google.common.base.Objects;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Profile task process thread, dump segment executing thread stack.
 *
 * @author MrPro
 */
public class ProfileThread extends Thread {

    private static final ILog logger = LogManager.getLogger(ProfileThread.class);

    // per segment max profiling time (millisecond)
    private final long maxProfilingTimeMills;

    // current thread running status
    private volatile boolean running = true;

    // wait and notify has new profile task
    private final LinkedBlockingQueue<ProfileTaskExecutionContext> executionContextListener = new LinkedBlockingQueue<>();

    private final ProfileTaskExecutionService profileTaskExecutionService;
    private final ProfileTaskChannelService profileTaskChannelService;

    public ProfileThread(long maxProfilingTimeMills) {
        this.maxProfilingTimeMills = maxProfilingTimeMills;
        profileTaskExecutionService = ServiceManager.INSTANCE.findService(ProfileTaskExecutionService.class);
        profileTaskChannelService = ServiceManager.INSTANCE.findService(ProfileTaskChannelService.class);
    }

    @Override
    public void run() {

        while (running) {
            // waiting new profile task
            ProfileTaskExecutionContext taskExecutionContext = null;
            try {
                taskExecutionContext = executionContextListener.take();
            } catch (InterruptedException e) {
                continue;
            }

            try {
                profiling(taskExecutionContext);
            } catch (InterruptedException e) {
                // ignore interrupted
                continue;
            } catch (Exception e) {
                logger.error(e, "Profiling task fail. taskId:{}", taskExecutionContext.getTask().getTaskId());
            } finally {
                // finally stop current profiling task, tell execution service task has stop
                profileTaskExecutionService.stopCurrentProfileTask(taskExecutionContext);
            }
        }
    }

    /**
     * notify have new task need to process
     */
    void processNewProfileTask(ProfileTaskExecutionContext context) {
        executionContextListener.add(context);
    }

    /**
     * check have available slot to profile and add it
     * @param segment
     * @return
     */
    public ThreadProfiler attemptProfiling(TraceSegment segment, ProfileTaskExecutionContext taskExecutionContext) {
        // check has available slot
        AtomicInteger currentProfilingCount = taskExecutionContext.getCurrentProfilingCount();
        final int usingSlotCount = currentProfilingCount.get();
        if (usingSlotCount >= Config.Profile.MAX_PARALLEL) {
            return null;
        }

        // try to occupy slot
        if (!currentProfilingCount.compareAndSet(usingSlotCount, usingSlotCount + 1)) {
            return null;
        }

        ThreadProfiler[] profilerSlot = taskExecutionContext.getThreadProfilerSlot();
        final ThreadProfiler segmentContext = new ThreadProfiler(segment, Thread.currentThread(), taskExecutionContext);
        for (int slot = 0; slot < profilerSlot.length; slot++) {
            if (profilerSlot[slot] == null) {
                profilerSlot[slot] = segmentContext;
                break;
            }
        }
        return segmentContext;
    }

    /**
     * find segment and clear on slot
     *
     * @param segment
     */
    public void stopSegmentProfile(TraceSegment segment) {
        ProfileTaskExecutionContext currentExecutionContext = profileTaskExecutionService.getCurrentTaskExecutionContext();
        if (currentExecutionContext == null) {
            return;
        }

        // find current segment and clear it
        boolean find = false;
        ThreadProfiler[] profilerSlot = currentExecutionContext.getThreadProfilerSlot();
        for (int slot = 0; slot < profilerSlot.length; slot++) {
            ThreadProfiler currentSlotSegment = profilerSlot[slot];
            if (currentSlotSegment != null && Objects.equal(profilerSlot[slot].getSegment().getTraceSegmentId(), segment.getTraceSegmentId())) {
                profilerSlot[slot] = null;

                // setting stop running
                currentSlotSegment.setSegmentIsRunning(false);
                find = true;
                break;
            }
        }

        // decrease profile count
        if (find) {
            currentExecutionContext.getCurrentProfilingCount().addAndGet(-1);
        }
    }

    /**
     * shutdown profiling thread
     */
    public void shutdown() {
        running = false;
    }

    /**
     * start profiling
     * @param executionContext
     */
    private void profiling(ProfileTaskExecutionContext executionContext) throws InterruptedException {

        int maxSleepPeriod = executionContext.getTask().getThreadDumpPeriod();
        int minDurationThreshold = executionContext.getTask().getMinDurationThreshold();

        // run loop when current task still running
        long currentLoopStartTime = -1;
        while (checkCanKeepRunning(executionContext)) {
            currentLoopStartTime = System.currentTimeMillis();

            // each all slot
            for (ThreadProfiler slot : executionContext.getThreadProfilerSlot()) {
                if (slot == null) {
                    continue;
                }

                // check is already start dump stack
                if (slot.getStartDump()) {

                    // dump stack
                    if (!dumpSegment(slot)) {
                        stopSegmentProfile(slot.getSegment());
                        continue;
                    }

                } else {

                    // check segment running time
                    if (System.currentTimeMillis() - slot.getProfilingStartTime() > minDurationThreshold) {
                        slot.setStartDump(true);
                    }

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
        final StackTraceElement[] stackTrace = threadProfiler.getProfilingThread().getStackTrace();

        // stack depth is zero, means thread is already run finished
        if (stackTrace.length == 0) {
            return false;
        }

        int dumpElementCount = Math.min(stackTrace.length, Config.Profile.DUMP_MAX_STACK_DEPTH);

        // use inverted order, because thread dump is start with bottom
        final ArrayList<String> stackList = new ArrayList<>(dumpElementCount);
        for (int i = dumpElementCount - 1; i >= 0; i--) {
            stackList.add(buildStackElementCodeSignature(stackTrace[i]));
        }

        // build snapshot and send
        TracingThreadSnapshot snapshot = new TracingThreadSnapshot(threadProfiler, threadProfiler.nextSeq(), currentTime, stackList);
        profileTaskChannelService.addProfilingSnapshot(snapshot);
        return true;
    }

    private String buildStackElementCodeSignature(StackTraceElement element) {
        // className.methodName:lineNumber
        return element.getClassName() + "." + element.getMethodName() + ":" + element.getLineNumber();
    }

    /**
     * check segment profiling is should continue
     * @param context
     * @return
     */
    private boolean isSegmentProfilingContinuable(ThreadProfiler context) {
        // check segment still executing
        if (!context.getSegmentIsRunning()) {
            return false;
        }

        // check is out of limit monitor time
        if (System.currentTimeMillis() - context.getProfilingStartTime() > maxProfilingTimeMills) {
            return false;
        }

        return true;
    }

    /**
     * check can still monitor segment slot
     * @return
     */
    private boolean checkCanKeepRunning(ProfileTaskExecutionContext context) {
        // check current profile is still running
        if (!context.getRunning()) {
            return false;
        }

        // check thread still running
        if (!running) {
            return false;
        }

        return true;
    }
}
