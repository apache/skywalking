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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.util.Objects;

/**
 * One metric a rule declares: everything {@code MeterSystem.create} needs, collected at compile so the storage
 * model is installed before a span arrives.
 *
 * <p>Two blocks declaring the same name with the same shape share one metric, which is how {@code if} branches
 * feed one series; the same name with a different function or scope is a compile error.
 */
public final class TALMetricDeclaration {
    public final String fullName;
    public final String function;
    public final String scopeType;
    /**
     * The name as the rule wrote it, without the file's prefix, for diagnostics.
     */
    public final String declaredName;

    public TALMetricDeclaration(final String fullName, final String function,
                         final String scopeType, final String declaredName) {
        this.fullName = fullName;
        this.function = function;
        this.scopeType = scopeType;
        this.declaredName = declaredName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TALMetricDeclaration)) {
            return false;
        }
        final TALMetricDeclaration other = (TALMetricDeclaration) o;
        return Objects.equals(fullName, other.fullName)
            && Objects.equals(function, other.function)
            && Objects.equals(scopeType, other.scopeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullName, function, scopeType);
    }

    @Override
    public String toString() {
        return fullName + "(" + function + ", " + scopeType + ")";
    }
}
