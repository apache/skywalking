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

package org.apache.skywalking.oap.server.admin.dsl.debugging.mal;

import com.google.gson.JsonObject;
import java.util.Map;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.dsldebug.MALDebugRecorder;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.AbstractDebugRecorder;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.Sample;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.SessionLimits;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.MeterFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.DoubleValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabeledValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.dsldebug.RuleKey;

/**
 * Concrete recorder for MAL debug sessions. One execution = one
 * {@link SampleFamily} walking the rule's pipeline; the terminal
 * {@code appendMeterEmit} probe publishes the in-flight execution.
 *
 * <p>Each sample carries the verbatim DSL fragment (chain method,
 * top-level filter closure, downsample call) as {@code sourceText}; the
 * payload is the {@link SampleFamily}'s own
 * {@link SampleFamily#toJson()} output — no Gson reflection.
 */
public final class MALDebugRecorderImpl extends AbstractDebugRecorder
                                        implements MALDebugRecorder {

    /**
     * Last downsampling annotation captured during the in-flight pass —
     * shape: {@code "<function>(<origin>)"} (e.g. {@code "AVG(default)"} /
     * {@code "SUM(explicit)"}). Used as the terminal {@code output}
     * sample's {@code sourceText} so operators can see which downsampling
     * MAL applied to the emitted meter without correlating across samples.
     *
     * <p>Per-thread because the receiver runs MAL passes for the same rule
     * concurrently across analyzer threads (one OTLP push per thread); a
     * shared field would let one thread's emit observe another thread's
     * downsample annotation. The thread-local mirrors the per-thread
     * {@code current} ExecutionRecord on {@link AbstractDebugRecorder} —
     * both bound to the analysis pass that the thread is driving.
     */
    private final ThreadLocal<String> lastDownsample = ThreadLocal.withInitial(() -> "");

    public MALDebugRecorderImpl(final String sessionId, final RuleKey ruleKey,
                                final GateHolder boundHolder, final SessionLimits limits) {
        super(sessionId, ruleKey, boundHolder, limits);
    }

    @Override
    public void appendBeginAnalysis(final String rule) {
        // Per-execution boundary: flush any in-flight execution from a prior
        // analysis pass that ran on this thread but didn't reach a terminal
        // (empty meterSamples → no meterEmit). The next pass's samples must
        // land in a fresh ExecutionRecord, not the orphan.
        publishCurrentExecution();
        lastDownsample.remove();
    }

    @Override
    public void appendEndAnalysis(final String rule) {
        // Close the in-flight execution. One analysis pass = one record;
        // multiple meterEmits inside the pass all share that record.
        publishCurrentExecution();
        lastDownsample.remove();
    }

    @Override
    public void appendInput(final String rule, final String metricRef, final SampleFamily family) {
        addSample(new Sample(Sample.TYPE_INPUT, metricRef, true, payload(family), 0));
    }

    @Override
    public void appendFilter(final String rule, final String filterText,
                             final Map<String, SampleFamily> surviving,
                             final boolean kept) {
        if (!kept) {
            // Kept-only capture: a rule whose file-level filter rejected the
            // entire input map isn't relevant to this rule's operator-facing
            // pipeline, and publishing it would burn recordCap on noise
            // (tag-discriminating rules can hit 99% rejection in busy
            // environments). Discard the in-flight execution silently —
            // every published record represents a rule pass that survived
            // the filter and walked through to meterEmit.
            discardCurrentExecution();
            return;
        }
        addSample(new Sample(Sample.TYPE_FILTER, filterText, true,
                             multiFamilyPayload(surviving), 0));
    }

    /**
     * Renders the post-filter survivor map as a single payload that
     * lists every metric whose family survived. Shape:
     * {@code {"families": N, "items": [ {family-1 items...}, {family-2 items...} ]}}.
     * Operators viewing the wire see at a glance how many metric refs
     * the rule's expression covers and what each family's surviving
     * samples look like — far more useful than the previous
     * "first-family representative" rendering.
     */
    private static String multiFamilyPayload(final Map<String, SampleFamily> surviving) {
        if (surviving == null || surviving.isEmpty()) {
            return "{\"families\":0,\"items\":[]}";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("{\"families\":").append(surviving.size()).append(",\"items\":[");
        boolean first = true;
        for (final SampleFamily family : surviving.values()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(payload(family));
        }
        sb.append("]}");
        return sb.toString();
    }

    @Override
    public void appendStage(final String rule, final String sourceText, final SampleFamily family) {
        addSample(new Sample(Sample.TYPE_FUNCTION, sourceText, true, payload(family), 0));
    }

    @Override
    public void appendDownsample(final String rule, final String function,
                                 final String origin, final SampleFamily family) {
        // sourceText is the verbatim downsample function name (AVG / SUM / etc.).
        // The `origin` argument tells the UI whether the operator declared
        // `downsampling:` explicitly or MAL filled it in based on the metric
        // type — record both so the terminal output sample can echo it.
        lastDownsample.set(function + "(" + origin + ")");
        addSample(new Sample(Sample.TYPE_FUNCTION, function, true, payload(family), 0));
    }

    @Override
    public void appendMeterEmit(final String rule, final MeterEntity entity,
                                final String metricName, final AcceptableValue<?> value,
                                final long timeBucket) {
        // One analysis pass can emit multiple meters (one per entity in
        // meterSamples) — they all land in the same record. The pass-level
        // appendEndAnalysis is the close point, not this one.
        // The terminal sample's sourceText echoes the downsampling annotation
        // captured upstream by appendDownsample (e.g. "AVG(default)" /
        // "SUM(explicit)") so operators can see which downsampling MAL applied
        // without scanning sibling samples. Falls back to the function name
        // resolved from the MeterFunction annotation when no captureDownsample
        // probe fired in this pass (defensive — every MAL rule emits one).
        final String captured = lastDownsample.get();
        final String sourceText = captured.isEmpty()
            ? resolveFunctionName(value)
            : captured;
        addSample(new Sample(Sample.TYPE_OUTPUT, sourceText, true,
            meterEmitPayload(entity, metricName, value, timeBucket), 0));
    }

    private static String payload(final SampleFamily family) {
        if (family == null) {
            return "{\"empty\":true,\"samples\":0}";
        }
        return family.toJson();
    }

    private static String meterEmitPayload(final MeterEntity entity, final String metricName,
                                           final AcceptableValue<?> value, final long timeBucket) {
        final JsonObject obj = new JsonObject();
        obj.addProperty("metric", metricName);
        obj.addProperty("entity", entity == null ? null : entity.toString());
        obj.addProperty("valueType", resolveFunctionName(value));
        obj.addProperty("timeBucket", timeBucket);
        appendValue(obj, value);
        return obj.toString();
    }

    /**
     * Surface the metric's terminal reading on the captured {@code output}
     * sample so the operator sees the actual MAL emission, not just the
     * function name. The shape of {@code "value"} depends on the holder
     * the generated function class implements:
     * <ul>
     *   <li>{@link LongValueHolder} / {@link IntValueHolder} → JSON number
     *       (Sum, Avg, Max, Min, Latest, SumPerMin, …).</li>
     *   <li>{@link DoubleValueHolder} → JSON number; non-finite values
     *       (NaN, ±Infinity) render as the corresponding string so the
     *       wire stays valid JSON and the reading is still visible.</li>
     *   <li>{@link LabeledValueHolder} → JSON object {@code {key: long}}
     *       — labeled metrics ({@code *Labeled}) and histogram/percentile
     *       functions ({@code AvgHistogramPercentileFunction},
     *       {@code SumHistogramPercentileFunction}) whose reading is a
     *       {@link DataTable}. Keys are label combos for {@code *Labeled},
     *       {@code p=<rank>} entries for percentile functions.</li>
     * </ul>
     * If {@code value} is null or not one of the recognised holders the
     * field is omitted; the operator still sees {@code valueType} and can
     * tell from the function name that the shape is non-scalar.
     *
     * <p><b>Two-phase functions.</b> Some functions split work between
     * {@code accept()} and {@code calculate()} — accept() populates raw
     * aggregates (e.g. {@code summation} + {@code count} for histogram-
     * percentile), and calculate() turns those into the user-visible
     * field returned by {@code getValue()} (e.g. {@code percentileValues}).
     * The MAL {@code captureMeterEmit} probe fires AFTER accept() but
     * BEFORE the streaming pipeline calls calculate(), so without forcing
     * calculate() here the labeled value column would be an empty map for
     * histogram-percentile rules — exactly when operators most need to
     * verify what the rule emits. We force calculate() at probe time so
     * the captured value matches what the storage row will contain.
     *
     * <p>The cost is bounded but real: calling calculate() twice runs the
     * computation twice. Most {@code Metrics.calculate()} implementations
     * have no idempotence guard
     * ({@code AvgFunction}, {@code CPMMetrics}, {@code SumMetrics} just
     * recompute every call), and the histogram-percentile pair
     * ({@code AvgHistogramPercentileFunction},
     * {@code SumHistogramPercentileFunction}) check {@code isCalculated}
     * at entry but never set it to {@code true} on exit, so even those
     * re-run on the streaming pipeline's later call. The cost is paid
     * only when a debug session is installed: the probe site is gated
     * (the JIT elides the call into this recorder on the hot path when
     * the gate is off), and a single emission's calculate() is bounded
     * (one source-tick of percentile work, not a stream's worth). On
     * cluster paths, {@code combine()} resets {@code isCalculated=false}
     * before a peer reads — pre-computing here on the local snapshot
     * doesn't leak stale values into the cluster.
     */
    private static void appendValue(final JsonObject obj, final AcceptableValue<?> value) {
        if (value == null) {
            return;
        }
        // Force two-phase functions to compute their user-visible reading
        // before we read getValue(). One extra calculate() per probed
        // emission per debug session — see the javadoc above for the cost
        // model and why we don't rely on isCalculated as a no-op guard.
        if (value instanceof Metrics) {
            ((Metrics) value).calculate();
        }
        // Order matters: LabeledValueHolder is checked before scalar holders
        // because some labeled functions could in principle implement both;
        // the labeled (DataTable) view is the operator-meaningful one.
        if (value instanceof LabeledValueHolder) {
            final DataTable table = ((LabeledValueHolder) value).getValue();
            if (table == null) {
                return;
            }
            final JsonObject map = new JsonObject();
            for (final String key : table.keys()) {
                final Long v = table.get(key);
                if (v != null) {
                    map.addProperty(key, v);
                }
            }
            obj.add("value", map);
        } else if (value instanceof LongValueHolder) {
            obj.addProperty("value", ((LongValueHolder) value).getValue());
        } else if (value instanceof IntValueHolder) {
            obj.addProperty("value", ((IntValueHolder) value).getValue());
        } else if (value instanceof DoubleValueHolder) {
            final double v = ((DoubleValueHolder) value).getValue();
            if (Double.isFinite(v)) {
                obj.addProperty("value", v);
            } else {
                // Gson rejects NaN / ±Infinity as numbers — surface them as
                // strings so an operator inspecting a divide-by-zero or
                // empty-window emit can still see the actual reading.
                obj.addProperty("value", Double.toString(v));
            }
        }
    }

    /**
     * Resolve the human-readable MAL function name (e.g. {@code sum},
     * {@code avg}, {@code maxHistogram}) by walking the class hierarchy
     * for the {@link MeterFunction} annotation. The runtime type is the
     * Javassist-generated subclass whose simple name equals the metric
     * name (e.g. {@code e2e_dsldbg_filtered_requests}); the annotation
     * sits on the framework parent ({@code SumFunction}, {@code AvgFunction},
     * etc.). Falls back to the generated simple name when no annotation
     * is found in the chain — defensive only; every shipped MAL function
     * carries the annotation.
     */
    private static String resolveFunctionName(final AcceptableValue<?> value) {
        if (value == null) {
            return null;
        }
        Class<?> c = value.getClass();
        while (c != null) {
            final MeterFunction ann = c.getAnnotation(MeterFunction.class);
            if (ann != null) {
                return ann.functionName();
            }
            c = c.getSuperclass();
        }
        return value.getClass().getSimpleName();
    }
}
