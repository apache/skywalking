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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Define when the profiling task would be executed
 */
public enum EBPFProfilingTriggerType {

    UNKNOWN(0),

    /**
     * Appoint the task start time
     */
    FIXED_TIME(1),

    /**
     * Trigger by the reach the continuous profiling policy
     */
    CONTINUOUS_PROFILING(2)
    ;
    private final int value;
    private static final Map<Integer, EBPFProfilingTriggerType> DICTIONARY = new HashMap<>();

    static {
        Arrays.stream(EBPFProfilingTriggerType.values()).collect(Collectors.toMap(EBPFProfilingTriggerType::value, type -> type)).forEach(DICTIONARY::put);
    }

    EBPFProfilingTriggerType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static EBPFProfilingTriggerType valueOf(int value) {
        EBPFProfilingTriggerType type = DICTIONARY.get(value);
        if (type == null) {
            throw new UnexpectedException("Unknown EBPFProfilingTriggerType value");
        }
        return type;
    }
}
