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
 * eBPF Profiling stack type means that the element in stack from where
 */
public enum EBPFProfilingStackType {

    UNKNOWN(0, null),

    KERNEL_SPACE(1, org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingStackType.PROCESS_KERNEL_SPACE),

    USER_SPACE(2, org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingStackType.PROCESS_USER_SPACE)
    ;
    private final int value;
    private final org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingStackType mapping;
    private static final Map<Integer, EBPFProfilingStackType> DICTIONARY = new HashMap<>();
    private static final Map<org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingStackType, EBPFProfilingStackType> MAPPING = new HashMap<>();

    static {
        Arrays.stream(EBPFProfilingStackType.values()).collect(Collectors.toMap(EBPFProfilingStackType::value, type -> type)).forEach(DICTIONARY::put);
        Arrays.stream(EBPFProfilingStackType.values()).collect(Collectors.toMap(EBPFProfilingStackType::mapping, type -> type)).forEach(MAPPING::put);
    }

    EBPFProfilingStackType(int value, org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingStackType mapping) {
        this.value = value;
        this.mapping = mapping;
    }

    public int value() {
        return value;
    }

    public org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingStackType mapping() {
        return mapping;
    }

    public static EBPFProfilingStackType valueOf(int value) {
        EBPFProfilingStackType type = DICTIONARY.get(value);
        if (type == null) {
            throw new UnexpectedException("Unknown EBPFProfilingStackType value");
        }
        return type;
    }

    public static EBPFProfilingStackType valueOf(org.apache.skywalking.apm.network.ebpf.profiling.v3.EBPFProfilingStackType protocolStackType) {
        final EBPFProfilingStackType type = MAPPING.get(protocolStackType);
        if (type == null) {
            throw new UnexpectedException("Unknown EBPFProfilingStackType value");
        }
        return type;
    }
}