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

package org.apache.skywalking.oap.server.core.storage.annotation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Caller-supplied metadata for a metric this OAP does not define locally ("foreign"), used only by
 * the admin inspect value path. It carries exactly what the read path cannot recover from the metric
 * name: the value column, its data type, the scope, and the empty-bucket default. The scope is the
 * metric's, not the queried entity's; it is derived from the query Entity by the caller side.
 *
 * <p>It is supplied per-request via {@link InspectQueryContext} and consulted PROVIDE-IF-ABSENT by
 * {@link ValueColumnMetadata} — it can only fill gaps for unregistered metrics, never shadow a
 * registered one.
 */
@Getter
@RequiredArgsConstructor
public class ForeignMetricMeta {
    private final String metricName;
    /**
     * Physical value column (post reserved-word override, e.g. {@code value_} on MySQL/PostgreSQL).
     */
    private final String valueColumn;
    /**
     * One of {@code LONG} / {@code INT} / {@code DOUBLE} (scalar) or {@code LABELED} (DataTable).
     * Distinguishes both the MQE branch (COMMON vs LABELED) and the storage decode (long vs double).
     */
    private final String valueType;
    private final int scopeId;
    private final int defaultValue;
}
