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

package org.apache.skywalking.oap.server.core.query.enumeration;

import org.apache.skywalking.oap.server.core.UnexpectedException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The status of profiling support
 */
public enum ProfilingSupportStatus {

    NOT_SUPPORT(0),
    SUPPORT_EBPF_PROFILING(1);

    private final int value;
    private static final Map<Integer, ProfilingSupportStatus> DICTIONARY = new HashMap<>();

    static {
        Arrays.stream(ProfilingSupportStatus.values()).collect(Collectors.toMap(ProfilingSupportStatus::value, type -> type)).forEach(DICTIONARY::put);
    }

    ProfilingSupportStatus(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static ProfilingSupportStatus valueOf(int value) {
        ProfilingSupportStatus type = DICTIONARY.get(value);
        if (type == null) {
            throw new UnexpectedException("Unknown ProfilingSupportStatus value");
        }
        return type;
    }
}