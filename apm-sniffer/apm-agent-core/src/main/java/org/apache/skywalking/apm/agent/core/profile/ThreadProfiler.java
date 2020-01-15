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
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.context.ids.ID;

import java.util.List;

/**
 * @author MrPro
 */
public class ThreadProfiler {

    // current tracing context
    private final TracingContext tracingContext;
    // current tracing segment id
    private final ID traceSegmentId;
    // need to profiling thread
    private final Thread profilingThread;
    // profiling execution context
    private final ProfileTaskExecutionContext executionContext;

    // profiling start time
    private long profilingStartTime;

    // after min duration threshold check, it will start dump
    private ProfilingStatus profilingStatus = ProfilingStatus.READY;
    // thread dump sequence
    private int dumpSequence = 0;

    public ThreadProfiler(TracingContext tracingContext, ID traceSegmentId, Thread profilingThread, ProfileTaskExecutionContext executionContext) {
        this.tracingContext = tracingContext;
        this.traceSegmentId = traceSegmentId;
        this.profilingThread = profilingThread;
        this.executionContext = executionContext;
    }

    public void startProfiling() {
        this.profilingStartTime = System.currentTimeMillis();
        this.profilingStatus = ProfilingStatus.PROFILING;
    }

    public void stopProfiling() {
        this.profilingStatus = ProfilingStatus.STOPPED;
    }

    public TracingThreadSnapshot buildSnapshot(long dumpTime, List<String> stack) {
        String taskId = executionContext.getTask().getTaskId();
        return new TracingThreadSnapshot(taskId, traceSegmentId, dumpSequence++, dumpTime, stack);
    }

    public boolean matches(TracingContext context) {
        // match trace id
        return Objects.equal(context.getReadableGlobalTraceId(), tracingContext.getReadableGlobalTraceId());
    }

    public TracingContext tracingContext() {
        return tracingContext;
    }

    public Thread targetThread() {
        return profilingThread;
    }

    public long profilingStartTime() {
        return profilingStartTime;
    }

    public ProfilingStatus profilingStatus() {
        return profilingStatus;
    }

    public ID getTraceSegmentId() {
        return traceSegmentId;
    }
}
