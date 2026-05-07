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

package org.apache.skywalking.oap.meter.analyzer.v2.dsldebug;

import java.util.Map;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.dsldebug.DebugRecorder;

/**
 * Per-session recorder for MAL rules. The probe class {@link MALDebug} fans
 * every call out to every recorder bound on the rule's {@code GateHolder},
 * downcasting to this interface inside the loop.
 *
 * <p>Probe payloads carry verbatim DSL fragments as the {@code sourceText}
 * — MAL doesn't have a clean per-line mapping (binary ops / scope suffixes
 * span multiple AST nodes), so the verbatim source text is the accurate
 * locator the UI renders. No {@code sourceLine} field.
 */
public interface MALDebugRecorder extends DebugRecorder {

    /**
     * Marks the start of one analysis pass — fired at the top of
     * {@code Analyzer.doAnalysis} for this rule. The recorder uses it
     * as the per-execution boundary: any leftover in-flight execution
     * from a prior pass that didn't reach a terminal probe (an empty
     * meterSamples that fired no meterEmit) is flushed here so the
     * next pass starts on a clean slate.
     *
     * <p>One MAL execution = one analysis pass = the
     * {@code Analyzer.doAnalysis} call for this rule on one OTLP
     * push. All probes between
     * {@link #appendBeginAnalysis}/{@link #appendEndAnalysis} land in
     * one {@code ExecutionRecord} (the per-rule capture struct in
     * the {@code dsl-debugging} module — kept as a {@code @code}
     * reference rather than a {@code @link} because referencing it
     * by FQCN would require a circular module dependency from
     * meter-analyzer back to dsl-debugging).
     * Default no-op so recorders that don't bucket by analysis pass
     * (e.g. test fixtures) don't have to implement it.
     */
    default void appendBeginAnalysis(String rule) {
    }

    /**
     * Marks the end of one analysis pass — fired in a finally block in
     * {@code Analyzer.doAnalysis}. The recorder publishes the current
     * in-flight execution here. Default no-op.
     */
    default void appendEndAnalysis(String rule) {
    }

    /** First read of an input metric reference at the top of run(). */
    void appendInput(String rule, String metricRef, SampleFamily family);

    /**
     * File-level filter probe. {@code surviving} carries every
     * SampleFamily that passed the filter, keyed by metric name — so
     * a rule combining {@code metric_a + metric_b + metric_c} shows up
     * to three entries (metrics filtered to EMPTY are absent). {@code
     * kept = true} when at least one family survived, {@code false}
     * when the rule short-circuits post-filter.
     */
    void appendFilter(String rule, String filterText, Map<String, SampleFamily> surviving, boolean kept);

    /** Per-stage capture (chain method, binary op, unary neg, etc.). */
    void appendStage(String rule, String sourceText, SampleFamily family);

    /** Downsample stage (rate / irate / increase / etc.). */
    void appendDownsample(String rule, String function, String origin, SampleFamily family);

    /**
     * Captures the {@code (value, timeBucket)} pair the ingest path is about to push —
     * the L1-bound boundary that ends MAL's debugger scope.
     *
     * <p>Note: there is no separate {@code appendMeterBuild} probe. In MAL the typed
     * {@code AcceptableValue} is constructed and {@code accept()}-populated in one
     * step, immediately followed by emit. The pre-emit and post-emit states are
     * identical, so capturing them as separate samples would emit duplicate payloads
     * (unlike OAL where {@code appendBuild} fires before the entry function runs).
     */
    void appendMeterEmit(String rule, MeterEntity entity, String metricName,
                         AcceptableValue<?> value, long timeBucket);
}
