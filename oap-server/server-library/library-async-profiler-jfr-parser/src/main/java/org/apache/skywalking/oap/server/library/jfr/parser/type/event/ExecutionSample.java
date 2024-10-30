/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.parser.type.event;

public class ExecutionSample extends Event {
    public final int threadState;

    public ExecutionSample(long time, int tid, int stackTraceId, int threadState) {
        super(time, tid, stackTraceId);
        this.threadState = threadState;
    }
}
