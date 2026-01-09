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

package org.apache.skywalking.library.banyandb.v1.client;

import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Trace represents the trace of a request.
 */
@Getter
@Setter(value = AccessLevel.PRIVATE)
public class Trace {
    private String traceId;
    private List<Span> spans;
    private boolean error;

    static Trace convertFromProto(org.apache.skywalking.banyandb.common.v1.BanyandbCommon.Trace protoTrace) {
        Trace traceBean = new Trace();
        traceBean.setTraceId(protoTrace.getTraceId());
        traceBean.setError(protoTrace.getError());
        traceBean.setSpans(protoTrace.getSpansList().stream()
                .map(Span::convertSpanFromProto)
                .collect(Collectors.toList()));
        return traceBean;
    }
}
