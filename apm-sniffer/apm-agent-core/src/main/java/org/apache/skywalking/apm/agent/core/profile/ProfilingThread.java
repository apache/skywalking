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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Profiling segment thread, dump segment executing thread stack
 *
 * @author MrPro
 */
public class ProfilingThread extends Thread {

    private static final ILog logger = LogManager.getLogger(ProfilingThread.class);

    // per segment max profiling time (millisecond)
    private static final long MAX_PROFILING_TIME_MILLS = TimeUnit.MINUTES.toMillis(Config.Profile.MAX_DURATION);

    // current profiling task is running
    private volatile boolean running = true;

    // profiling task, if not null means has task profiling
    private final AtomicReference<ProfilingSegmentContext> currentExecutingSegmentContext = new AtomicReference<>();

    // wait and notify has segment need to profile
    private final LinkedBlockingQueue<Boolean> profilingNotification = new LinkedBlockingQueue<>();

    private final ProfileTaskExecutionService profileTaskExecutionService;
    private final ProfileTaskChannelService profileTaskChannelService;

    public ProfilingThread() {
        profileTaskExecutionService = ServiceManager.INSTANCE.findService(ProfileTaskExecutionService.class);
        profileTaskChannelService = ServiceManager.INSTANCE.findService(ProfileTaskChannelService.class);
    }

    @Override
    public void run() {

        while (running) {
            // waiting profiling notify
            try {
                profilingNotification.take();
            } catch (InterruptedException e) {
                continue;
            }

            // check has profiling context again
            final ProfilingSegmentContext segmentContext = currentExecutingSegmentContext.get();
            if (segmentContext == null) {
                continue;
            }

            try {
                profiling(segmentContext);
            } catch (InterruptedException e) {
                // ignore interrupted
                continue;
            } catch (Exception e) {
                logger.error(e, "Profiling {} error, monitor thread id: {}.", segmentContext.getExecutionContext().getTask().getEndpointName(), segmentContext.getProfilingThread().getId());
            } finally {
                // finally stop current profiling task
                stopProfileCurrentRunning();
            }
        }
    }

    /**
     * check current has profiling segment, if not then add to monitor
     * @param segment
     * @return
     */
    public ProfilingSegmentContext checkAndAddSegmentContext(TraceSegment segment, ProfileTaskExecutionContext taskExecutionContext) {
        // setting current executing thread, it will setting success when no monitor running
        final ProfilingSegmentContext profilingSegmentContext = new ProfilingSegmentContext(segment, Thread.currentThread(), taskExecutionContext);
        if (!currentExecutingSegmentContext.compareAndSet(null, profilingSegmentContext)) {
            return null;
        }

        // notify to start profiling
        profilingNotification.add(true);

        return profilingSegmentContext;
    }

    /**
     * @param segment
     * @return is current thread profiling
     */
    public boolean stopSegmentProfile(TraceSegment segment) {
        // check is current thread monitoring this segment
        final ProfilingSegmentContext profilingSegmentContext = currentExecutingSegmentContext.get();
        if (profilingSegmentContext == null) {
            return false;
        }
        if (!Objects.equal(profilingSegmentContext.getSegment().getTraceSegmentId(), segment.getTraceSegmentId())) {
            return false;
        }

        // segment is stop running flag
        profilingSegmentContext.setSegmentIsRunning(false);

        stopProfileCurrentRunning();
        return true;
    }

    /**
     * shutdown profiling thread
     */
    public void shutdown() {
        running = false;
    }

    /**
     * start profiling
     * @param segmentContext
     */
    private void profiling(ProfilingSegmentContext segmentContext) throws InterruptedException {
        // check can start profiling
        if (!checkProfilingCanContinue(segmentContext, false)) {
            stopProfileCurrentRunning();
            return;
        }

        // setting is starting monitor
        segmentContext.setProfilingStartTime(System.currentTimeMillis());

        // check min duration threshold, use Thread.sleep
        final ProfileTask profilingTask = segmentContext.getExecutionContext().getTask();
        final int taskMinDurationThreshold = profilingTask.getMinDurationThreshold();
        if (taskMinDurationThreshold > 0) {
            Thread.sleep(taskMinDurationThreshold);
        }

        // starting thread dumping loop
        int dumpSeq = 0;
        while (checkProfilingCanContinue(segmentContext, true)) {

            // dump thread
            if (!dumpThread(segmentContext, dumpSeq)) {
                break;
            }

            // increment sequence
            dumpSeq++;

            // dump period
            Thread.sleep(profilingTask.getThreadDumpPeriod());

        }
    }

    /**
     * dump thread stack, and push data to backend
     * @param segmentContext
     * @return still can dump
     */
    private boolean dumpThread(ProfilingSegmentContext segmentContext, int dumpSeq) {
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
        final ProfileTaskSegmentSnapshot snapshot = new ProfileTaskSegmentSnapshot(segmentContext, dumpSeq, currentTime, stackList);
        profileTaskChannelService.addProfilingSnapshot(snapshot);

        return true;
    }

    private String buildStackElementCodeSignature(StackTraceElement element) {
        // className.methodName:lineNumber
        return element.getClassName() + "." + element.getMethodName() + ":" + element.getLineNumber();
    }

    /**
     * check profiling is should continue
     * @param context
     * @param checkProfilingTime if true, check profiling is out limit {@link Config.Profile#MAX_DURATION}
     * @return
     */
    private boolean checkProfilingCanContinue(ProfilingSegmentContext context, boolean checkProfilingTime) {
        // check segment still executing
        if (!context.getSegmentIsRunning()) {
            return false;
        }

        // check current has profiling task
        final ProfileTaskExecutionContext executionContext = profileTaskExecutionService.getCurrentTaskExecutionContext();
        if (executionContext == null) {
            return false;
        }

        // current profiling task dont match, use task create time to quick match
        if (context.getExecutionContext().getTask().getCreateTime() != executionContext.getTask().getCreateTime()) {
            return false;
        }

        // check is out of limit monitor time
        if (checkProfilingTime && System.currentTimeMillis() - context.getProfilingStartTime() > MAX_PROFILING_TIME_MILLS) {
            return false;
        }

        // check segment executing thread is still running
        if (!context.getProfilingThread().isAlive()) {
            return false;
        }

        // check current thread is already stopped
        if (!running) {
            return false;
        }

        return true;
    }

    /**
     * stop current profiling
     */
    private void stopProfileCurrentRunning() {
        currentExecutingSegmentContext.set(null);
    }

}
