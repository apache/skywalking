/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.parser.type.event;

public class AllocationSample extends Event {
    public final int classId;
    public final long allocationSize;
    public final long tlabSize;

    public AllocationSample(long time, int tid, int stackTraceId, int classId, long allocationSize, long tlabSize) {
        super(time, tid, stackTraceId);
        this.classId = classId;
        this.allocationSize = allocationSize;
        this.tlabSize = tlabSize;
    }

    @Override
    public int hashCode() {
        return classId * 127 + stackTraceId + (tlabSize == 0 ? 17 : 0);
    }

    @Override
    public boolean sameGroup(Event o) {
        if (o instanceof AllocationSample) {
            AllocationSample a = (AllocationSample) o;
            return classId == a.classId && (tlabSize == 0) == (a.tlabSize == 0);
        }
        return false;
    }

    @Override
    public long classId() {
        return classId;
    }

    @Override
    public long value() {
        return tlabSize != 0 ? tlabSize : allocationSize;
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }
}
