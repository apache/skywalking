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
