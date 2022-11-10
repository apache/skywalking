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

package org.apache.skywalking.oap.server.core.analysis.manual.spanattach;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@link SpanAttachedEventRecord} tracing context reference type.
 */
public enum SpanAttachedEventTraceType {

    SKYWALKING(0),

    ZIPKIN(1);

    private final int code;
    private static final Map<Integer, SpanAttachedEventTraceType> CODE_DICTIONARY = new HashMap<>();

    static {
        for (SpanAttachedEventTraceType val :SpanAttachedEventTraceType.values()) {
            CODE_DICTIONARY.put(val.value(), val);
        }
    }

    public static SpanAttachedEventTraceType valueOf(Integer code) {
        return CODE_DICTIONARY.get(code);
    }

    SpanAttachedEventTraceType(int code) {
        this.code = code;
    }

    public int value() {
        return this.code;
    }

}
