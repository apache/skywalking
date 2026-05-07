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
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;

/**
 * Probe surface generated MAL bytecode calls into. Each method takes the
 * per-rule {@link GateHolder} as its first argument and fans the capture
 * out to every recorder currently bound on that holder.
 *
 * <h2>Probe payload — verbatim DSL fragment, no line numbers</h2>
 * MAL probes carry the verbatim DSL fragment ({@code "sum(['svc'])"},
 * {@code "tagEqual('region', 'us-east-1')"}, {@code "myext::scale(2.0)"})
 * as the {@code sourceText} argument. There is no separate line number —
 * MAL stages don't map cleanly to a single AST line (binary ops, scope
 * suffixes, and chain stages all interleave), so the verbatim text is the
 * accurate locator. The recorder serialises {@code sourceText} as-is and
 * the UI renders it directly.
 *
 * <h2>Workflow</h2>
 * <pre>
 *   Generated rule's run() body:
 *     if (debug.gate) MALDebug.captureInput(debug, ruleName, "metric_ref", _v);
 *     ...stage...
 *     if (debug.gate) MALDebug.captureStage(debug, ruleName, "stageText", _v);
 *
 *   Analyzer.doAnalysis (hand-written):
 *     if (debug.gate) MALDebug.captureFilter(debug, ruleName, filterText, family, kept);
 *     ...meterSamples.forEach...
 *     if (debug.gate) MALDebug.captureMeterBuild(debug, ruleName, entity, name, type, v);
 *     if (debug.gate) MALDebug.captureMeterEmit(debug, ruleName, entity, name, v, ts);
 *     send(...)
 * </pre>
 */
public final class MALDebug {

    private MALDebug() {
    }

    /** Functional dispatcher used by every probe to forward to one recorder. */
    @FunctionalInterface
    private interface Dispatch {
        void to(MALDebugRecorder recorder);
    }

    /**
     * Marks the start of one analysis pass for this rule. The recorder
     * uses it as the per-execution boundary so all probes between begin
     * and end land in one captured record. Fired at the top of
     * {@code Analyzer.doAnalysis} for this rule.
     */
    public static void captureBeginAnalysis(final GateHolder holder, final String rule) {
        fanOut(holder, r -> r.appendBeginAnalysis(rule));
    }

    /**
     * Marks the end of one analysis pass. Fired in a finally block in
     * {@code Analyzer.doAnalysis} so a thrown exception still publishes
     * the partial record.
     */
    public static void captureEndAnalysis(final GateHolder holder, final String rule) {
        fanOut(holder, r -> r.appendEndAnalysis(rule));
    }

    public static void captureInput(final GateHolder holder, final String rule,
                                    final String metricRef, final SampleFamily family) {
        fanOut(holder, r -> r.appendInput(rule, metricRef, family));
    }

    /**
     * File-level filter capture. The {@code surviving} map carries every
     * SampleFamily that passed the file-level filter — keyed by metric
     * name, one entry per metric reference the rule's expression reads
     * (so a rule that combines {@code metric_a + metric_b + metric_c}
     * shows up to three entries; metrics whose samples were entirely
     * filtered out are absent). {@code kept} is the per-rule survival
     * verdict — {@code true} when at least one family survived, {@code
     * false} when the file-level filter short-circuits the rule. Probes
     * on both branches so the UI can show "filter dropped this batch".
     */
    public static void captureFilter(final GateHolder holder, final String rule,
                                     final String filterText,
                                     final Map<String, SampleFamily> surviving,
                                     final boolean kept) {
        fanOut(holder, r -> r.appendFilter(rule, filterText, surviving, kept));
    }

    public static void captureStage(final GateHolder holder, final String rule,
                                    final String sourceText, final SampleFamily family) {
        fanOut(holder, r -> r.appendStage(rule, sourceText, family));
    }

    public static void captureDownsample(final GateHolder holder, final String rule,
                                         final String function, final String origin,
                                         final SampleFamily family) {
        fanOut(holder, r -> r.appendDownsample(rule, function, origin, family));
    }

    public static void captureMeterEmit(final GateHolder holder, final String rule,
                                        final MeterEntity entity, final String metricName,
                                        final AcceptableValue<?> value, final long timeBucket) {
        fanOut(holder, r -> r.appendMeterEmit(rule, entity, metricName, value, timeBucket));
    }

    private static void fanOut(final GateHolder holder, final Dispatch dispatch) {
        final DebugRecorder[] snapshot = holder.getRecorders();
        for (int i = 0; i < snapshot.length; i++) {
            final DebugRecorder recorder = snapshot[i];
            if (recorder.isCaptured()) {
                continue;
            }
            dispatch.to((MALDebugRecorder) recorder);
        }
    }
}
