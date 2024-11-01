/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.skywalking.oap.server.library.jfr.parser.type.event;

import java.lang.reflect.Field;

public abstract class Event implements Comparable<Event> {
    public final long time;
    public final int tid;
    public final int stackTraceId;

    protected Event(long time, int tid, int stackTraceId) {
        this.time = time;
        this.tid = tid;
        this.stackTraceId = stackTraceId;
    }

    @Override
    public int compareTo(Event o) {
        return Long.compare(time, o.time);
    }

    @Override
    public int hashCode() {
        return stackTraceId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName())
                .append("{time=").append(time)
                .append(",tid=").append(tid)
                .append(",stackTraceId=").append(stackTraceId);
        for (Field f : getClass().getDeclaredFields()) {
            try {
                sb.append(',').append(f.getName()).append('=').append(f.get(this));
            } catch (ReflectiveOperationException e) {
                break;
            }
        }
        return sb.append('}').toString();
    }

    public boolean sameGroup(Event o) {
        return getClass() == o.getClass();
    }

    public long classId() {
        return 0;
    }

    public long value() {
        return 1;
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }
}
