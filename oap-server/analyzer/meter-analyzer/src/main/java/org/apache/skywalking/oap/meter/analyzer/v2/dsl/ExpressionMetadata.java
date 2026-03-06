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

package org.apache.skywalking.oap.meter.analyzer.v2.dsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;

/**
 * Immutable metadata extracted from a MAL expression at compile time.
 * Replaces the ThreadLocal-based {@code ExpressionParsingContext} pattern.
 */
@Getter
public class ExpressionMetadata {

    private final List<String> samples;
    private final ScopeType scopeType;
    private final Set<String> scopeLabels;
    private final Set<String> aggregationLabels;
    private final DownsamplingType downsampling;
    private final boolean isHistogram;
    private final int[] percentiles;

    public ExpressionMetadata(final List<String> samples,
                              final ScopeType scopeType,
                              final Set<String> scopeLabels,
                              final Set<String> aggregationLabels,
                              final DownsamplingType downsampling,
                              final boolean isHistogram,
                              final int[] percentiles) {
        this.samples = Collections.unmodifiableList(samples);
        this.scopeType = scopeType;
        this.scopeLabels = Collections.unmodifiableSet(scopeLabels);
        this.aggregationLabels = Collections.unmodifiableSet(aggregationLabels);
        this.downsampling = downsampling;
        this.isHistogram = isHistogram;
        this.percentiles = percentiles;
    }

    /**
     * Get labels not related to scope (aggregation labels minus scope labels).
     */
    public List<String> getLabels() {
        final List<String> result = new ArrayList<>(aggregationLabels);
        result.removeAll(scopeLabels);
        return result;
    }
}
