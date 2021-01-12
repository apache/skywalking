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
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.TracingContext;

public class ThreadProfiler {

    // current tracing context
    private final TracingContext tracingContext;
    // current tracing segment id
    private final String traceSegmentId;
    // need to profiling thread
    private final Thread profilingThread;
    // profiling execution context
    private final ProfileTaskExecutionContext executionContext;

    // profiling time
    private long profilingStartTime;
    private long profilingMaxTimeMills;

    // after min duration threshold check, it will start dump
    private final ProfileStatusReference profilingStatus;
    // thread dump sequence
    private int dumpSequence = 0;

    public ThreadProfiler(TracingContext tracingContext, String traceSegmentId, Thread profilingThread,
        ProfileTaskExecutionContext executionContext) {
        this.tracingContext = tracingContext;
        this.traceSegmentId = traceSegmentId;
        this.profilingThread = profilingThread;
        this.executionContext = executionContext;
        if (tracingContext.profileStatus() == null) {
            this.profilingStatus = ProfileStatusReference.createWithPending();
        } else {
            this.profilingStatus = tracingContext.profileStatus();
            this.profilingStatus.updateStatus(ProfileStatus.PENDING);
        }
        this.profilingMaxTimeMills = TimeUnit.MINUTES.toMillis(Config.Profile.MAX_DURATION);
    }

    /**
     * If tracing start time greater than {@link ProfileTask#getMinDurationThreshold()}, then start to profiling trace
     */
    public void startProfilingIfNeed() {
        if (System.currentTimeMillis() - tracingContext.createTime() > executionContext.getTask()
                                                                                       .getMinDurationThreshold()) {
            this.profilingStartTime = System.currentTimeMillis();
            this.tracingContext.profileStatus().updateStatus(ProfileStatus.PROFILING);
        }
    }

    /**
     * Stop profiling status
     */
    public void stopProfiling() {
        this.tracingContext.profileStatus().updateStatus(ProfileStatus.STOPPED);
    }

    /**
     * dump tracing thread and build thread snapshot
     *
     * @return snapshot, if null means dump snapshot error, should stop it
     */
    public TracingThreadSnapshot buildSnapshot() {
        if (!isProfilingContinuable()) {
            return null;
        }

        long currentTime = System.currentTimeMillis();
        // dump thread
        StackTraceElement[] stackTrace;
        try {
            stackTrace = profilingThread.getStackTrace();

            // stack depth is zero, means thread is already run finished
            if (stackTrace.length == 0) {
                return null;
            }
        } catch (Exception e) {
            // dump error ignore and make this profiler stop
            return null;
        }

        // if is first dump, check is can start profiling
        if (dumpSequence == 0 && (!executionContext.isStartProfileable())) {
            return null;
        }

        int dumpElementCount = Math.min(stackTrace.length, Config.Profile.DUMP_MAX_STACK_DEPTH);

        // use inverted order, because thread dump is start with bottom
        final ArrayList<String> stackList = new ArrayList<>(dumpElementCount);
        for (int i = dumpElementCount - 1; i >= 0; i--) {
            stackList.add(buildStackElementCodeSignature(stackTrace[i]));
        }

        String taskId = executionContext.getTask().getTaskId();
        return new TracingThreadSnapshot(taskId, traceSegmentId, dumpSequence++, currentTime, stackList);
    }

    /**
     * build thread stack element code signature
     *
     * @return code sign: className.methodName:lineNumber
     */
    private String buildStackElementCodeSignature(StackTraceElement element) {
        return element.getClassName() + "." + element.getMethodName() + ":" + element.getLineNumber();
    }

    /**
     * matches profiling tracing context
     */
    public boolean matches(TracingContext context) {
        // match trace id
        return Objects.equal(context.getReadablePrimaryTraceId(), tracingContext.getReadablePrimaryTraceId());
    }

    /**
     * check whether profiling should continue
     *
     * @return if true means this thread profiling is continuable
     */
    private boolean isProfilingContinuable() {
        return System.currentTimeMillis() - profilingStartTime < profilingMaxTimeMills;
    }

    public TracingContext tracingContext() {
        return tracingContext;
    }

    public ProfileStatusReference profilingStatus() {
        return profilingStatus;
    }

}
