/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.parser.type.event;

public class LiveObject extends Event {
    public final int classId;
    public final long allocationSize;
    public final long allocationTime;

    public LiveObject(long time, int tid, int stackTraceId, int classId, long allocationSize, long allocationTime) {
        super(time, tid, stackTraceId);
        this.classId = classId;
        this.allocationSize = allocationSize;
        this.allocationTime = allocationTime;
    }

    @Override
    public int hashCode() {
        return classId * 127 + stackTraceId;
    }

    @Override
    public boolean sameGroup(Event o) {
        if (o instanceof LiveObject) {
            LiveObject a = (LiveObject) o;
            return classId == a.classId;
        }
        return false;
    }

    @Override
    public long classId() {
        return classId;
    }

    @Override
    public long value() {
        return allocationSize;
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }
}
