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

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum JFREventType {
    UNKNOWN(-1),
    EXECUTION_SAMPLE(1),
    JAVA_MONITOR_ENTER(2),
    THREAD_PARK(3),
    OBJECT_ALLOCATION_IN_NEW_TLAB(4),
    OBJECT_ALLOCATION_OUTSIDE_TLAB(5),
    PROFILER_LIVE_OBJECT(6);

    private final int code;
}
