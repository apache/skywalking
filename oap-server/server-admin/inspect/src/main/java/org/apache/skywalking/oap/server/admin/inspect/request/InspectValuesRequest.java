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

package org.apache.skywalking.oap.server.admin.inspect.request;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.query.input.Entity;

/**
 * Body of {@code POST /inspect/values}: an MQE expression evaluated over one or more metrics this OAP
 * does not define locally, whose metadata the caller supplies in {@link #foreignMetrics}.
 */
@Getter
@Setter
public class InspectValuesRequest {
    /**
     * The MQE expression to evaluate (e.g. a single foreign metric name, or an expression combining
     * foreign and/or catalog metrics).
     */
    private String expression;
    /**
     * The query entity; its {@code scope} is used to bind every foreign metric.
     */
    private Entity entity;
    private String start;
    private String end;
    /**
     * One of {@code MINUTE} / {@code HOUR} / {@code DAY}.
     */
    private String step;
    /**
     * Metadata for the foreign metrics referenced by {@link #expression}.
     */
    private List<ForeignMetricInput> foreignMetrics;

    /**
     * Caller-supplied metadata for a single foreign metric.
     */
    @Getter
    @Setter
    public static class ForeignMetricInput {
        private String name;
        /**
         * Physical value column (post reserved-word override, e.g. {@code value_} on MySQL/PostgreSQL).
         */
        private String valueColumn;
        /**
         * One of {@code LONG} / {@code INT} / {@code DOUBLE} (scalar) or {@code LABELED}.
         */
        private String valueType;
    }
}
