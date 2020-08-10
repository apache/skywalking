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

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
public class Span {
    @Setter
    private String traceId;
    @Setter
    private String segmentId;
    @Setter
    private int spanId;
    @Setter
    private int parentSpanId;
    private final List<Ref> refs;
    @Setter
    private String serviceCode;
    @Setter
    private String serviceInstanceName;
    @Setter
    private long startTime;
    @Setter
    private long endTime;
    @Setter
    private String endpointName;
    @Setter
    private String type;
    @Setter
    private String peer;
    @Setter
    private String component;
    @Setter
    private boolean isError;
    @Setter
    private String layer;
    private final List<KeyValue> tags;
    private final List<LogEntity> logs;
    @Setter
    private boolean isRoot;
    @Setter
    private String segmentSpanId;
    @Setter
    private String segmentParentSpanId;

    public Span() {
        this.refs = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.logs = new ArrayList<>();
    }
}
