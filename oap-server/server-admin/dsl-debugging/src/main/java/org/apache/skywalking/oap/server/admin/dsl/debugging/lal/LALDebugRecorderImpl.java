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

package org.apache.skywalking.oap.server.admin.dsl.debugging.lal;

import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsldebug.LALDebugRecorder;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.AbstractDebugRecorder;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.Sample;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.SessionLimits;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;

/**
 * Concrete recorder for LAL debug sessions. One execution = one log
 * walking the rule's pipeline; either {@link #appendOutputRecord} or
 * {@link #appendOutputMetric} is the terminal probe that publishes the
 * in-flight execution.
 *
 * <p>Block- vs statement-mode is recorder-side: with
 * {@code statementGranularity=false} the {@link #appendLine} verb
 * short-circuits, so block-mode sessions only carry the broad
 * text/parser/extractor probes. With {@code statementGranularity=true}
 * each extractor statement (tag/field/output assignment) emits its own
 * sample, with {@code sourceText} carrying the verbatim statement
 * fragment.
 *
 * <p>{@code continueOn} reflects the abort flag: {@code !ctx.shouldAbort()}.
 * A sample on a log that has been aborted by a previous statement
 * carries {@code false}, signalling the operator that downstream
 * statements were skipped.
 */
public final class LALDebugRecorderImpl extends AbstractDebugRecorder
                                        implements LALDebugRecorder {

    private final boolean statementGranularity;

    public LALDebugRecorderImpl(final String sessionId, final RuleKey ruleKey,
                                final GateHolder boundHolder, final SessionLimits limits,
                                final boolean statementGranularity) {
        super(sessionId, ruleKey, boundHolder, limits);
        this.statementGranularity = statementGranularity;
    }

    @Override
    public void appendText(final String rule, final int sourceLine, final ExecutionContext ctx) {
        // appendText is the start-of-execution probe (entry point of the LAL
        // pipeline). Publish any leftover in-flight execution from a prior log
        // that aborted before the terminal sink probe fired so the new log's
        // samples don't bleed into it.
        // Block-mode probes don't map to a verbatim DSL slice; the `type` field
        // (input/function/output) carries the semantic and sourceText stays empty.
        // Statement-mode samples emitted by appendLine carry the verbatim
        // per-statement slice as their sourceText.
        // Input payload is captured ONCE here at the top of the pipeline —
        // subsequent samples render only the output (builder state) so the
        // wire doesn't repeat the raw input on every probe.
        publishCurrentExecution();
        addSample(new Sample(Sample.TYPE_INPUT, "", continueOn(ctx), inputPayload(ctx), sourceLine));
    }

    @Override
    public void appendParser(final String rule, final int sourceLine, final ExecutionContext ctx) {
        addSample(new Sample(Sample.TYPE_FUNCTION, "", continueOn(ctx), outputPayload(ctx), sourceLine));
    }

    @Override
    public void appendExtractor(final String rule, final int sourceLine, final ExecutionContext ctx) {
        if (statementGranularity) {
            // statement-mode emits per-statement samples via appendLine — skip the
            // block-level synopsis to avoid noise.
            return;
        }
        addSample(new Sample(Sample.TYPE_FUNCTION, "", continueOn(ctx), outputPayload(ctx), sourceLine));
    }

    @Override
    public void appendLine(final String rule, final int sourceLine, final String sourceText,
                           final ExecutionContext ctx) {
        if (!statementGranularity) {
            return;
        }
        addSample(new Sample(Sample.TYPE_FUNCTION, sourceText == null ? "" : sourceText,
                             continueOn(ctx), outputPayload(ctx), sourceLine));
    }

    @Override
    public void appendOutputRecord(final String rule, final int sourceLine, final ExecutionContext ctx,
                                   final String outputClass) {
        // Terminal probe: append the output-type sample (sourceText empty,
        // type=output), then close. The output entity itself is captured in
        // the payload via ExecutionContext.outputPayloadJson().
        addSample(new Sample(Sample.TYPE_OUTPUT, "", continueOn(ctx), outputPayload(ctx), sourceLine));
        publishCurrentExecution();
    }

    @Override
    public void appendOutputMetric(final String rule, final int sourceLine, final ExecutionContext ctx,
                                   final SampleFamily family) {
        addSample(new Sample(Sample.TYPE_OUTPUT, "", continueOn(ctx), outputPayload(ctx), sourceLine));
        publishCurrentExecution();
    }

    private static boolean continueOn(final ExecutionContext ctx) {
        return ctx == null || !ctx.shouldAbort();
    }

    private static String inputPayload(final ExecutionContext ctx) {
        if (ctx == null) {
            return "{\"ctx\":\"null\"}";
        }
        return ctx.inputPayloadJson();
    }

    private static String outputPayload(final ExecutionContext ctx) {
        if (ctx == null) {
            return "{\"ctx\":\"null\"}";
        }
        return ctx.outputPayloadJson();
    }
}
