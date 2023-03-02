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

package org.apache.skywalking.oap.server.core.profiling.continuous.storage;

import org.apache.skywalking.apm.network.ebpf.profiling.v3.ContinuousProfilingTriggeredMonitorType;
import org.apache.skywalking.oap.server.core.UnexpectedException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ContinuousProfilingMonitorType {

    UNKNOWN(0, null),
    PROCESS_CPU(1, ContinuousProfilingTriggeredMonitorType.ProcessCPU),
    PROCESS_THREAD_COUNT(2, ContinuousProfilingTriggeredMonitorType.ProcessThreadCount),
    SYSTEM_LOAD(3, ContinuousProfilingTriggeredMonitorType.SystemLoad),
    HTTP_ERROR_RATE(4, ContinuousProfilingTriggeredMonitorType.HTTPErrorRate),
    HTTP_AVG_RESPONSE_TIME(5, ContinuousProfilingTriggeredMonitorType.HTTPAvgResponseTime);

    private final int value;
    private final ContinuousProfilingTriggeredMonitorType causeType;
    private static final Map<Integer, ContinuousProfilingMonitorType> DICTIONARY = new HashMap<>();
    private static final Map<ContinuousProfilingTriggeredMonitorType, ContinuousProfilingMonitorType> CAUSE_DICTIONARY = new HashMap<>();

    static {
        DICTIONARY.putAll(Arrays.stream(ContinuousProfilingMonitorType.values()).collect(Collectors.toMap(ContinuousProfilingMonitorType::value, Function.identity())));
        CAUSE_DICTIONARY.putAll(Arrays.stream(ContinuousProfilingMonitorType.values()).filter(s -> Objects.nonNull(s.causeType))
            .collect(Collectors.toMap(s -> s.causeType, Function.identity())));
    }

    ContinuousProfilingMonitorType(int value, ContinuousProfilingTriggeredMonitorType causeType) {
        this.value = value;
        this.causeType = causeType;
    }

    public static ContinuousProfilingMonitorType valueOf(int value) {
        ContinuousProfilingMonitorType type = DICTIONARY.get(value);
        if (type == null) {
            throw new UnexpectedException("Unknown ContinuousProfilingTargetType value");
        }
        return type;
    }

    public static ContinuousProfilingMonitorType valueOf(ContinuousProfilingTriggeredMonitorType causeType) {
        ContinuousProfilingMonitorType type = CAUSE_DICTIONARY.get(causeType);
        if (type == null) {
            throw new UnexpectedException("Unknown ContinuousProfilingTargetType value");
        }
        return type;
    }

    public int value() {
        return this.value;
    }
}
