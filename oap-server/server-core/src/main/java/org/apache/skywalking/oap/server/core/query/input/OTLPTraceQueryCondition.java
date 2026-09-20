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

package org.apache.skywalking.oap.server.core.query.input;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.type.QueryOrder;

/**
 * The search condition over natively stored OTLP spans, {@code otlp_span}. Every field is optional except that
 * either {@link #traceId} or {@link #queryDuration} must be set: a lookup by trace id may search everything the
 * storage retains, any other search needs a time range.
 *
 * <p>The scalar fields are equality conditions on the record's index columns; {@link #tags} are equality
 * conditions on the {@code key=value} attribute index. Durations are nanoseconds, the unit of the
 * {@code duration} column.
 */
@Getter
@Setter
@ToString
public class OTLPTraceQueryCondition {
    private String serviceName;
    private String serviceInstance;
    private String scopeName;
    private String spanName;
    private String peerService;
    /**
     * The OTLP {@code SpanKind} enum number, or null for any kind.
     */
    private Integer kind;
    /**
     * The OTLP {@code StatusCode} enum number, or null for any status.
     */
    private Integer statusCode;
    /**
     * Inclusive lower bound in nanoseconds; 0 means unbounded.
     */
    private long minDurationNanos;
    /**
     * Inclusive upper bound in nanoseconds; 0 means unbounded.
     */
    private long maxDurationNanos;
    private List<Tag> tags = new ArrayList<>();
    private String traceId;
    private Duration queryDuration;
    private QueryOrder queryOrder = QueryOrder.BY_START_TIME;
    /**
     * The most traces to return.
     */
    private int limit = 20;
}
