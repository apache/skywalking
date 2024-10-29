/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

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
