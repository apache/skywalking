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

import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;

/**
 * @author MrPro
 */
public class ThreadProfiler {

    // current segment id
    private final TraceSegment segment;
    // need to profiling thread
    private final Thread profilingThread;
    // profiling execution context
    private final ProfileTaskExecutionContext executionContext;

    // current segment running status, each dump will judge it. Will set false when trace notification
    private volatile boolean segmentIsRunning = true;
    // profiling start time
    private long profilingStartTime;

    // after min duration threshold check, it will start dump
    private boolean startDump = false;
    // thread dump sequence
    private int dumpSequence = 0;

    public ThreadProfiler(TraceSegment segment, Thread profilingThread, ProfileTaskExecutionContext executionContext) {
        this.segment = segment;
        this.profilingThread = profilingThread;
        this.executionContext = executionContext;
        this.profilingStartTime = System.currentTimeMillis();
    }

    public TraceSegment getSegment() {
        return segment;
    }

    public Thread getProfilingThread() {
        return profilingThread;
    }

    public boolean getSegmentIsRunning() {
        return segmentIsRunning;
    }

    public void setSegmentIsRunning(boolean segmentIsRunning) {
        this.segmentIsRunning = segmentIsRunning;
    }

    public ProfileTaskExecutionContext getExecutionContext() {
        return executionContext;
    }

    public long getProfilingStartTime() {
        return profilingStartTime;
    }

    public boolean getStartDump() {
        return startDump;
    }

    public void setStartDump(boolean startDump) {
        this.startDump = startDump;
    }

    /**
     * get current sequence then increment it
     * @return
     */
    public int nextSeq() {
        return dumpSequence++;
    }
}
