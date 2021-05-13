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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;

/**
 * ExpressionParsingContext contains states in parsing phase of an expression.
 */
@Getter
@ToString
@EqualsAndHashCode
@Builder
public class ExpressionParsingContext implements Closeable {

    static ExpressionParsingContext create() {
        if (CACHE.get() == null) {
            CACHE.set(ExpressionParsingContext.builder()
                                              .samples(Lists.newArrayList())
                                              .downsampling(DownsamplingType.AVG)
                                              .scopeLabels(Sets.newHashSet())
                                              .aggregationLabels(Sets.newHashSet()).build());
        }
        return CACHE.get();
    }

    static Optional<ExpressionParsingContext> get() {
        return Optional.ofNullable(CACHE.get());
    }

    private final static ThreadLocal<ExpressionParsingContext> CACHE = new ThreadLocal<>();

    List<String> samples;

    boolean isHistogram;
    int[] percentiles;

    Set<String> aggregationLabels;

    Set<String> scopeLabels;

    DownsamplingType downsampling;

    ScopeType scopeType;

    /**
     * Mark whether the retagByK8sMeta func in expressions is active
     */
    boolean isRetagByK8sMeta;

    /**
     * Get labels no scope related.
     *
     * @return labels
     */
    public List<String> getLabels() {
        List<String> result = new ArrayList<>(aggregationLabels);
        result.removeAll(scopeLabels);
        return result;
    }

    /**
     * Validate context after parsing
     * @param exp expression literal
     */
    public void validate(String exp) {
        Preconditions.checkNotNull(scopeType, exp + ": one of service(), instance() or endpoint() should be invoke");
    }

    @Override
    public void close() {
        CACHE.remove();
    }

}
