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

package org.apache.skywalking.oap.server.admin.dsl.debugging.oal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.AbstractDebugRecorder;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.Sample;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.SessionLimits;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.dsldebug.OALDebugRecorder;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;
import org.apache.skywalking.oap.server.core.dsldebug.ToJson;
import org.apache.skywalking.oap.server.core.source.ISource;

/**
 * Concrete recorder for OAL debug sessions. One execution = one source
 * walking the rule's pipeline; the terminal probe ({@link #appendEmit})
 * publishes the in-flight {@link org.apache.skywalking.oap.server.admin.dsl.debugging.session.ExecutionRecord}
 * and the recorder bookkeeps the cap.
 *
 * <p>Each captured sample carries:
 * <ul>
 *   <li>{@code sourceText} — verbatim DSL fragment (e.g.
 *       {@code from(ServiceRelation.*)}, {@code .filter(detectPoint == DetectPoint.SERVER)},
 *       {@code .cpm()}). Pulled from the ANTLR Interval slice at parse time.</li>
 *   <li>{@code continueOn} — true when the pipeline kept going past this
 *       step, false when a filter clause short-circuited the rule.</li>
 *   <li>{@code payloadJson} — the source's or metrics' own
 *       {@link ToJson#toJson} output, hand-built per type. No Gson
 *       reflection on the hot path.</li>
 *   <li>{@code sourceLine} — 1-based line in {@code core.oal} the rule
 *       lives on, so the UI can render an in-source pointer.</li>
 * </ul>
 *
 * <p>Build / aggregation / emit collapse into samples whose
 * {@code sourceText} is the verbatim DSL fragment — the legacy "stage"
 * label is gone from the wire.
 */
public final class OALDebugRecorderImpl extends AbstractDebugRecorder
                                        implements OALDebugRecorder {

    public OALDebugRecorderImpl(final String sessionId, final RuleKey ruleKey,
                                final GateHolder boundHolder, final SessionLimits limits) {
        super(sessionId, ruleKey, boundHolder, limits);
    }

    @Override
    public void appendSource(final String sourceText, final int sourceLine, final ISource source) {
        // appendSource is the start-of-execution probe — publish any in-flight
        // execution from a prior source that short-circuited mid-pipeline (e.g.
        // a filter clause rejected before reaching the terminal emit) so this
        // new source's samples don't bleed into it.
        publishCurrentExecution();
        addSample(new Sample(Sample.TYPE_INPUT, sourceText, true, payload(source), sourceLine));
    }

    // Override the type field for filter / aggregation paths (the source path used the default INPUT above).
    @Override
    public void appendFilter(final String sourceText, final int sourceLine,
                             final ISource source, final boolean kept) {
        // OAL filters are deterministic, low-cardinality discriminators
        // (CLIENT vs SERVER, layer, status, latency thresholds) — not the
        // high-cardinality tag noise MAL has to deal with. When an operator
        // installs on `service_relation_server_cpm`, seeing CLIENT sources
        // arrive and get rejected (continueOn=false) is the filter doing
        // its job in plain view, not noise to hide. Both branches stay in
        // the captured records; the source sample at the head of the
        // execution carries the rejected-source's payload so the operator
        // can grep "what did the filter reject" by scrolling samples.
        addSample(new Sample(Sample.TYPE_FILTER, sourceText, kept, payload(source), sourceLine));
        if (!kept) {
            // Filter rejected → the FTL's `return` ends the per-source pipeline
            // here. Publish what we have so the next source's appendSource starts
            // a clean slate. Without this the next source's samples would bleed
            // into this one's record.
            publishCurrentExecution();
        }
    }

    @Override
    public void appendBuild(final String metric, final int sourceLine, final Metrics built) {
        // Pre-aggregation Metrics state: source columns copied into the metric's
        // storage fields, but the entry function (cpm() / apdex() / percentile() /
        // ...) hasn't yet run — counts/totals/buckets are at their initial values.
        // Captured because the diff between this and appendAggregation is the
        // entry function's effect — diagnostic value when verifying field copy
        // bugs (entityId, serviceId, scope) before aggregation muddies the view.
        addSample(new Sample(Sample.TYPE_FUNCTION, "build_metrics", true,
                             payload(built), sourceLine));
    }

    @Override
    public void appendAggregation(final String metric, final String aggregationText,
                                  final int sourceLine, final Metrics afterEntryFunction) {
        addSample(new Sample(Sample.TYPE_AGGREGATION, aggregationText, true, payload(afterEntryFunction), sourceLine));
    }

    @Override
    public void appendEmit(final String metric, final int sourceLine, final Metrics readyForL1) {
        // emit is the terminal — append the L1-ready Metrics snapshot as a
        // TYPE_OUTPUT sample (matches the wire contract documented in
        // dsl-debugging-oal.md) and close the in-flight record. The
        // post-aggregation diff against the preceding TYPE_AGGREGATION sample
        // shows the difference between in-flight and L1-ready state.
        addSample(new Sample(Sample.TYPE_OUTPUT, metric, true,
                             payload(readyForL1), sourceLine));
        publishCurrentExecution();
    }

    private static String payload(final ISource source) {
        final JsonObject obj = new JsonObject();
        if (source == null) {
            obj.addProperty("type", "null");
            return obj.toString();
        }
        obj.addProperty("type", source.getClass().getSimpleName());
        // Source's own toJson() returns a JSON object literal — parse once
        // so we splice it in as a real child rather than as a quoted string.
        obj.add("fields", JsonParser.parseString(source.toJson()));
        return obj.toString();
    }

    private static String payload(final Metrics metrics) {
        if (metrics == null) {
            final JsonObject nullObj = new JsonObject();
            nullObj.addProperty("type", "null");
            return nullObj.toString();
        }
        // Metrics implements ToJson via the base class hierarchy — each
        // family (CPMMetrics, SumMetrics, AvgMetrics, ...) overrides
        // appendDebugFields to expose its semantic value columns
        // (count / total / summation / percentile values / ...). Generated
        // OAL Metrics classes inherit through their parent family's override.
        return metrics.toJson();
    }
}
