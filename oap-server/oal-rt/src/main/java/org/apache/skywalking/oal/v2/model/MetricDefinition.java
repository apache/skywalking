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

package org.apache.skywalking.oal.v2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;

/**
 * Immutable representation of a metric definition from OAL script.
 *
 * Example OAL:
 * <pre>
 * service_resp_time = from(Service.latency).filter(latency &gt; 0).longAvg().decorator("ServiceDecorator");
 * </pre>
 *
 * This class is immutable and thread-safe. Use the Builder to construct instances.
 */
@Getter
public final class MetricDefinition {
    private final String name;
    private final String tableName;
    private final SourceReference source;
    private final List<FilterExpression> filters;
    private final FunctionCall aggregationFunction;
    private final Optional<String> decorator;
    private final SourceLocation location;
    /**
     * Verbatim text of this metric's full statement in the {@code .oal} file —
     * the ANTLR Interval slice covering {@code <name> = from(...).filter(...).<func>();}.
     * Threaded into the per-metric {@code GateHolder} so dsl-debugging records
     * carry the rule source inline. {@code ""} for synthesised metrics.
     */
    private final String sourceText;
    /**
     * Verbatim text of just the {@code from(SourceName.attr)} /
     * {@code from(SourceName.*)} segment — the ANTLR Interval slice from the
     * FROM token through the closing source-paren. The dsl-debugging
     * captureSource probe stamps this as the source sample's
     * {@code sourceText} so the operator sees what they literally wrote.
     */
    private final String fromText;

    private MetricDefinition(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name cannot be null");
        this.tableName = builder.tableName != null ? builder.tableName : builder.name;
        this.source = Objects.requireNonNull(builder.source, "source cannot be null");
        this.filters = Collections.unmodifiableList(new ArrayList<>(builder.filters));
        this.aggregationFunction = Objects.requireNonNull(builder.aggregationFunction, "aggregationFunction cannot be null");
        this.decorator = Optional.ofNullable(builder.decorator);
        this.location = builder.location != null ? builder.location : SourceLocation.UNKNOWN;
        this.sourceText = builder.sourceText == null ? "" : builder.sourceText;
        this.fromText = builder.fromText == null ? "" : builder.fromText;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricDefinition that = (MetricDefinition) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "MetricDefinition{" +
            "name='" + name + '\'' +
            ", source=" + source.getName() +
            ", function=" + aggregationFunction.getName() +
            '}';
    }

    /**
     * Builder for MetricDefinition.
     */
    public static class Builder {
        private String name;
        private String tableName;
        private SourceReference source;
        private List<FilterExpression> filters = new ArrayList<>();
        private FunctionCall aggregationFunction;
        private String decorator;
        private SourceLocation location;
        private String sourceText;
        private String fromText;

        public Builder sourceText(String sourceText) {
            this.sourceText = sourceText;
            return this;
        }

        public Builder fromText(String fromText) {
            this.fromText = fromText;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder source(SourceReference source) {
            this.source = source;
            return this;
        }

        public Builder addFilter(FilterExpression filter) {
            this.filters.add(filter);
            return this;
        }

        public Builder filters(List<FilterExpression> filters) {
            this.filters = new ArrayList<>(filters);
            return this;
        }

        public Builder aggregationFunction(FunctionCall function) {
            this.aggregationFunction = function;
            return this;
        }

        public Builder decorator(String decorator) {
            this.decorator = decorator;
            return this;
        }

        public Builder location(SourceLocation location) {
            this.location = location;
            return this;
        }

        public MetricDefinition build() {
            return new MetricDefinition(this);
        }
    }
}
