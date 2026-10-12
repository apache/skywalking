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

import java.util.List;
import java.util.Objects;

/**
 * One metric a rule declares: everything {@code MeterSystem.create} needs, collected at compile so the storage
 * model is installed before a span arrives.
 *
 * <p>Two blocks declaring the same name with the same shape share one metric, which is how {@code if} branches
 * feed one series; the same name with a different function, scope, buckets or ranks is a compile error, since one
 * stored series cannot hold two bucket layouts.
 */
public final class TALMetricDeclaration {
    public final String fullName;
    public final String function;
    public final String scopeType;
    /**
     * The name as the rule wrote it, without the file's prefix, for diagnostics.
     */
    public final String declaredName;
    /**
     * The histogram or percentile bounds as declared, empty for other functions.
     */
    public final List<Long> buckets;
    /**
     * The percentile ranks, empty for other functions.
     */
    public final List<Integer> ranks;
    /**
     * The most label sets one row holds, 0 for a metric that is not labeled.
     */
    public final int labelLimit;

    public TALMetricDeclaration(final String fullName, final String function,
                                final String scopeType, final String declaredName,
                                final List<Long> buckets, final List<Integer> ranks, final int labelLimit) {
        this.fullName = fullName;
        this.function = function;
        this.scopeType = scopeType;
        this.declaredName = declaredName;
        this.buckets = List.copyOf(buckets);
        this.ranks = List.copyOf(ranks);
        this.labelLimit = labelLimit;
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
            && Objects.equals(scopeType, other.scopeType)
            && buckets.equals(other.buckets)
            && ranks.equals(other.ranks)
            && labelLimit == other.labelLimit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullName, function, scopeType, buckets, ranks, labelLimit);
    }

    @Override
    public String toString() {
        return fullName + "(" + function + ", " + scopeType
            + (buckets.isEmpty() ? "" : ", buckets " + buckets)
            + (ranks.isEmpty() ? "" : ", ranks " + ranks)
            + (labelLimit == 0 ? "" : ", labelLimit " + labelLimit) + ")";
    }
}
