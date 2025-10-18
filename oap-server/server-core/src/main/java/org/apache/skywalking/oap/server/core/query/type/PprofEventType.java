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

package org.apache.skywalking.oap.server.core.query.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PprofEventType {
    CPU(0, "cpu"),
    HEAP(1, "heap"),
    BLOCK(2, "block"),
    MUTEX(3, "mutex"),
    GOROUTINE(4, "goroutine"),
    THREADCREATE(5, "threadcreate"),
    ALLOCS(6, "allocs");


    private final int code;
    private final String name;

    public static PprofEventType valueOfString(String event) {
        return PprofEventType.valueOf(event);
    }
}
