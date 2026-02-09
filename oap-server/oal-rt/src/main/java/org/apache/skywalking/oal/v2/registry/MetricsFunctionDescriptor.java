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

package org.apache.skywalking.oal.v2.registry;

import java.lang.reflect.Method;
import java.util.Objects;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;

/**
 * Descriptor containing metadata about a metrics function.
 *
 * This class holds all information needed to use a metrics function
 * during code generation and semantic analysis.
 */
@Getter
public final class MetricsFunctionDescriptor {
    private final String name;
    private final Class<? extends Metrics> metricsClass;
    private final Method entranceMethod;
    private final String description;

    private MetricsFunctionDescriptor(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name cannot be null");
        this.metricsClass = Objects.requireNonNull(builder.metricsClass, "metricsClass cannot be null");
        this.entranceMethod = Objects.requireNonNull(builder.entranceMethod, "entranceMethod cannot be null");
        this.description = builder.description != null ? builder.description : "";
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricsFunctionDescriptor that = (MetricsFunctionDescriptor) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "MetricsFunctionDescriptor{" +
            "name='" + name + '\'' +
            ", metricsClass=" + metricsClass.getSimpleName() +
            '}';
    }

    public static class Builder {
        private String name;
        private Class<? extends Metrics> metricsClass;
        private Method entranceMethod;
        private String description;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder metricsClass(Class<? extends Metrics> metricsClass) {
            this.metricsClass = metricsClass;
            return this;
        }

        public Builder entranceMethod(Method entranceMethod) {
            this.entranceMethod = entranceMethod;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public MetricsFunctionDescriptor build() {
            return new MetricsFunctionDescriptor(this);
        }
    }
}
