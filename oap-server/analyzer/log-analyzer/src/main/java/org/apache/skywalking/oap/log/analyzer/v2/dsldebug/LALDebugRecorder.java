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

package org.apache.skywalking.oap.log.analyzer.v2.dsldebug;

import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.server.core.dsldebug.DebugRecorder;

/**
 * Per-session recorder for LAL rules.
 *
 * <p>LAL keeps a {@code sourceLine} on every probe — unlike MAL/OAL, the LAL
 * grammar maps each AST statement to a clean source line, and the
 * statement-level granularity option needs that line so the UI can highlight
 * the originating statement.
 *
 * <p>Stages:
 * <ul>
 *   <li>{@code text}    — raw log body view as the rule first sees it.</li>
 *   <li>{@code parser}  — after {@code json}/{@code yaml}/{@code text} populates
 *       {@code ctx.parsed()}.</li>
 *   <li>{@code extractor} — after the {@code extractor} block runs (typed builder mid-flight).</li>
 *   <li>{@code line}    — per-statement capture (opt-in via {@code granularity: "statement"}).</li>
 *   <li>{@code outputRecord} — terminal: typed output builder before {@code complete()},
 *       captured iff the record survived the sink.</li>
 *   <li>{@code outputMetric} — terminal: {@link SampleFamily} handed off to MAL,
 *       captured iff the metric extractor produced one.</li>
 * </ul>
 *
 * <p>The {@code sink} stage is folded into {@code outputRecord} / {@code outputMetric}
 * — sink either keeps the record (an output appears) or drops it (no output capture).
 * No separate {@code sink} verb needed.
 */
public interface LALDebugRecorder extends DebugRecorder {

    void appendText(String rule, int sourceLine, ExecutionContext ctx);

    void appendParser(String rule, int sourceLine, ExecutionContext ctx);

    void appendExtractor(String rule, int sourceLine, ExecutionContext ctx);

    /**
     * Statement-level probe — opt-in via session granularity.
     * {@code sourceText} is the verbatim DSL fragment for the line so the UI
     * can render "line {sourceLine}: {sourceText}" without round-tripping to
     * the source. Block-mode recorders early-return; the probe call site is
     * unconditional in the bytecode so the choice stays recorder-local.
     */
    void appendLine(String rule, int sourceLine, String sourceText, ExecutionContext ctx);

    /**
     * Terminal record-output probe. {@code outputClass} is the typed builder
     * class name (e.g. {@code LogBuilder}, {@code EnvoyAccessLogBuilder}); the
     * recorder serialises the output's bean properties for UI rendering.
     * Fires once per surviving record, AFTER the sink kept it.
     */
    void appendOutputRecord(String rule, int sourceLine, ExecutionContext ctx, String outputClass);

    /**
     * Terminal metric-output probe. {@code family} is the {@link SampleFamily}
     * the LAL metric extractor produced for hand-off to MAL. The debugger
     * does not chain into MAL from here — that's a separate session boundary.
     */
    void appendOutputMetric(String rule, int sourceLine, ExecutionContext ctx, SampleFamily family);
}
