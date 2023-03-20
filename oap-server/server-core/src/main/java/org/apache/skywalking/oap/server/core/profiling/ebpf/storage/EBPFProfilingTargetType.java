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

package org.apache.skywalking.oap.server.core.profiling.ebpf.storage;

import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingTargetType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * eBPF type for profiling the process
 */
public enum EBPFProfilingTargetType {

    UNKNOWN(0, null),

    ON_CPU(1, ContinuousProfilingTargetType.ON_CPU),

    OFF_CPU(2, ContinuousProfilingTargetType.OFF_CPU),

    NETWORK(3, ContinuousProfilingTargetType.NETWORK),
    ;
    private final int value;
    private final ContinuousProfilingTargetType continuousProfilingTargetType;
    private static final Map<Integer, EBPFProfilingTargetType> DICTIONARY = new HashMap<>();
    private static final Map<ContinuousProfilingTargetType, EBPFProfilingTargetType> CONTINUOUS_PROFILING_TARGET_DICTIONARY = new HashMap<>();

    static {
        Arrays.stream(EBPFProfilingTargetType.values()).collect(Collectors.toMap(EBPFProfilingTargetType::value, type -> type)).forEach(DICTIONARY::put);
        Arrays.stream(EBPFProfilingTargetType.values()).filter(s -> Objects.nonNull(s.getContinuousProfilingTargetType()))
            .collect(Collectors.toMap(EBPFProfilingTargetType::getContinuousProfilingTargetType, type -> type)).forEach(CONTINUOUS_PROFILING_TARGET_DICTIONARY::put);
    }

    EBPFProfilingTargetType(int value, ContinuousProfilingTargetType continuousProfilingTargetType) {
        this.value = value;
        this.continuousProfilingTargetType = continuousProfilingTargetType;
    }

    public int value() {
        return value;
    }

    public ContinuousProfilingTargetType getContinuousProfilingTargetType() {
        return continuousProfilingTargetType;
    }

    public static EBPFProfilingTargetType valueOf(int value) {
        EBPFProfilingTargetType type = DICTIONARY.get(value);
        if (type == null) {
            throw new UnexpectedException("Unknown EBPFProfilingTargetType value");
        }
        return type;
    }

    public static EBPFProfilingTargetType valueOf(ContinuousProfilingTargetType value) {
        EBPFProfilingTargetType type = CONTINUOUS_PROFILING_TARGET_DICTIONARY.get(value);
        if (type == null) {
            throw new UnexpectedException("Unknown ContinuousProfilingTargetType value: " + value);
        }
        return type;
    }
}