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

package org.apache.skywalking.oap.meter.analyzer.v2.dsl;

import com.google.common.collect.ImmutableMap;

/**
 * Test-only factory for {@link SampleFamily}. Lives in the same package as {@code SampleFamily}
 * so it can call the package-private {@code SampleFamily.build(RunningContext, Sample...)}
 * factory — the public constructor is private so production callers come through
 * {@code SampleFamily.build}; ITs need the same privileged access to feed synthetic samples
 * into the real MAL pipeline without going through a receiver.
 *
 * <p>Used by the runtime-rule ITs: tests construct a {@code SampleFamily} with controlled
 * labels / values / timestamps, hand it to the applied rule's {@code MetricConvert.toMeter}
 * and then verify the derived measure lands in BanyanDB.
 */
public final class TestSampleFamily {

    private TestSampleFamily() {
    }

    /**
     * Build a non-empty {@link SampleFamily} from one or more {@link Sample}s.
     * Delegates to {@code SampleFamily.build} which filters {@code NaN} samples and returns
     * {@link SampleFamily#EMPTY} if all are filtered — identical semantics to the production
     * path that reaches this factory from a receiver.
     */
    public static SampleFamily of(final Sample... samples) {
        return SampleFamily.build(SampleFamily.RunningContext.EMPTY, samples);
    }

    /**
     * Build a {@link Sample} — convenience wrapper that hides Lombok's Builder behind a
     * call-site-friendly signature. Labels are passed as an {@link ImmutableMap} so tests
     * don't import {@code ImmutableMap.Builder} boilerplate.
     */
    public static Sample sample(final String metricName,
                                 final long timestampMillis,
                                 final double value,
                                 final ImmutableMap<String, String> labels) {
        return Sample.builder()
            .name(metricName)
            .labels(labels)
            .value(value)
            .timestamp(timestampMillis)
            .build();
    }
}
