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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Profile task process thread, dump segment executing thread stack.
 *
 * @author MrPro
 */
public class ProfileThread extends Thread {

    private static final ILog logger = LogManager.getLogger(ProfileThread.class);

    // per segment max profiling time (millisecond)
    private static final long MAX_PROFILING_TIME_MILLS = TimeUnit.MINUTES.toMillis(Config.Profile.MAX_DURATION);

    // current thread running status
    private volatile boolean running = true;

    // wait and notify has new profile task
    private final LinkedBlockingQueue<ProfileTaskExecutionContext> executionContextListener = new LinkedBlockingQueue<>();

    private final ProfileTaskExecutionService profileTaskExecutionService;
    private final ProfileTaskChannelService profileTaskChannelService;

    public ProfileThread() {
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
    public ProfilingSegmentContext checkAndAddSegmentContext(TraceSegment segment, ProfileTaskExecutionContext taskExecutionContext) {
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

        ProfilingSegmentContext[] profilingSegmentSlot = taskExecutionContext.getProfilingSegmentSlot();
        final ProfilingSegmentContext segmentContext = new ProfilingSegmentContext(segment, Thread.currentThread(), taskExecutionContext);
        for (int slot = 0; slot < profilingSegmentSlot.length; slot++) {
            if (profilingSegmentSlot[slot] == null) {
                profilingSegmentSlot[slot] = segmentContext;
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
        ProfilingSegmentContext[] profilingSegmentSlot = currentExecutionContext.getProfilingSegmentSlot();
        for (int slot = 0; slot < profilingSegmentSlot.length; slot++) {
            ProfilingSegmentContext currentSlotSegment = profilingSegmentSlot[slot];
            if (currentSlotSegment != null && Objects.equal(profilingSegmentSlot[slot].getSegment().getTraceSegmentId(), segment.getTraceSegmentId())) {
                profilingSegmentSlot[slot] = null;

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
            for (ProfilingSegmentContext slot : executionContext.getProfilingSegmentSlot()) {
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
     * @param segmentContext
     * @return
     */
    private boolean dumpSegment(ProfilingSegmentContext segmentContext) {
        // dump stack
        if (!checkSegmentProfilingCanContinue(segmentContext)) {
            return false;
        }

        return dumpThread(segmentContext);
    }

    /**
     * dump thread stack, and push data to backend
     * @param segmentContext
     * @return still can dump
     */
    private boolean dumpThread(ProfilingSegmentContext segmentContext) {
        long currentTime = System.currentTimeMillis();
        // dump thread
        final StackTraceElement[] stackTrace = segmentContext.getProfilingThread().getStackTrace();

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
        ProfileTaskSegmentSnapshot snapshot = new ProfileTaskSegmentSnapshot(segmentContext, segmentContext.getCurrentAndIncrementSequence(), currentTime, stackList);
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
    private boolean checkSegmentProfilingCanContinue(ProfilingSegmentContext context) {
        // check segment still executing
        if (!context.getSegmentIsRunning()) {
            return false;
        }

        // check is out of limit monitor time
        if (System.currentTimeMillis() - context.getProfilingStartTime() > MAX_PROFILING_TIME_MILLS) {
            return false;
        }

        // check segment executing thread is still running
        if (!context.getProfilingThread().isAlive()) {
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
