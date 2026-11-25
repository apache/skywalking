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

package org.apache.skywalking.oap.server.admin.dsl.debugging.tal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.AbstractDebugRecorder;
import org.apache.skywalking.oap.server.admin.dsl.debugging.session.SessionLimits;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.dsl.debug.GateHolder;
import org.apache.skywalking.oap.server.core.dsl.debug.RuleKey;
import org.apache.skywalking.oap.server.core.dsl.debug.ToJson;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.debug.TALDebugRecorder;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.nativetrace.SwNativeSegmentState;

/**
 * Builds the records of a TAL debug session. A record is the object a runner passed through the rule, as the
 * {@code context}, and the list of its spans; each span is the span as it arrived and the {@code run}: the steps the
 * rule executed on it, the outputs that left the rule, and the state at the run's end.
 *
 * <p>A record is assembled on the thread that runs it, which for a native segment is one thread from the first span
 * to the last build, and published as one JSON body. In {@code block} granularity a run keeps the blocks it entered
 * and any abort; the outputs and the end state are the same in both granularities.
 *
 * <p>No method throws: the probes run inside the rule, where an exception would fail the run, and for a native
 * segment every rule after it. A probe that cannot be recorded drops its record instead.
 */
@Slf4j
public final class TALDebugRecorderImpl extends AbstractDebugRecorder implements TALDebugRecorder {

    /**
     * Prints the fields a span leaves at their defaults too: span 0's {@code spanId}, an {@code isError} of false.
     */
    private static final JsonFormat.Printer PRINTER =
        JsonFormat.printer().alwaysPrintFieldsWithNoPresence().omittingInsignificantWhitespace();
    /**
     * The step kinds a {@code block} capture keeps besides the outputs: the blocks entered, the queued builds, and
     * how a run stopped. Every output, the data a run generates and pushes forward, has its own step in both
     * granularities.
     */
    private static final Set<String> BLOCK_KINDS = Set.of("block", "abort", "build", "failure");
    /**
     * The step kinds that vote on an OTLP span's persistence, whose value is the vote the run holds after them.
     */
    private static final Set<String> VOTE_KINDS = Set.of("enforcer", "dropper", "rateLimit");
    /**
     * The step kinds that set a value, which a capture shows even when it is null.
     */
    private static final Set<String> VALUE_KINDS = Set.of(
        "def", "assign", "segmentDef", "foreach", "service", "instance", "endpoint", "layer", "prepareNetworkAlias");
    private static final int MAX_ELEMENTS = 100;
    /**
     * How deep a value renders: a collection a rule nests in itself would otherwise never end.
     */
    private static final int MAX_DEPTH = 8;

    private final boolean statementGranularity;
    /**
     * The file line of the rule's first DSL line; 0 when the rule file is not known here, and a step then carries its
     * line within the DSL.
     */
    private final int dslFirstLine;
    /**
     * The rule's metadata on every record, with {@code dslFirstLine} when the file is known here, so a client places
     * the steps' file lines exactly, as the recorder numbered them.
     */
    private final Map<String, String> recordMetadata;
    private final String[] segmentDefNames;
    private final InFlight current = new InFlight();

    public TALDebugRecorderImpl(final String sessionId, final RuleKey ruleKey, final GateHolder boundHolder,
                                final SessionLimits limits, final boolean statementGranularity,
                                final int dslFirstLine) {
        super(sessionId, ruleKey, boundHolder, limits);
        this.statementGranularity = statementGranularity;
        this.dslFirstLine = dslFirstLine;
        final Map<String, String> metadata = new LinkedHashMap<>(ruleMetadata());
        if (dslFirstLine > 0) {
            metadata.put("dslFirstLine", Integer.toString(dslFirstLine));
        }
        this.recordMetadata = Collections.unmodifiableMap(metadata);
        final String names = ruleMetadata().get("segmentDefs");
        this.segmentDefNames = names == null || names.isEmpty() ? new String[0] : names.split(",");
    }

    // ==================== Records ====================

    @Override
    public void beginSegment(final SwNativeSegmentState segment) {
        guarded(current, () -> {
            // Every span of the segment is listed, in wire order, also those the rule never ran on, as after a failure.
            final Record record = new Record(segmentContext(segment), true);
            for (final SpanObject span : segment.getSegmentObject().getSpansList()) {
                record.add(span, spanEntry(span, null));
            }
            current.set(record);
        });
    }

    @Override
    public void beginSpan(final ExecutionContext ctx, final Map<String, String> resource,
                          final Map<String, String> scope) {
        guarded(current, () -> {
            if (ctx.getNativeSegment() == null) {
                final Record record = new Record(otlpContext(resource, scope), false);
                record.run = record.add(ctx.getInput(), spanEntry(ctx.getInput(), ctx.getMeta()));
                record.run.begun = true;
                current.set(record);
                return;
            }
            final Record record = current.get();
            if (record == null || !record.segment) {
                // The session installed after the segment started; only whole segments are recorded.
                return;
            }
            final SpanRun known = record.find(ctx.getInput());
            record.run = known != null ? known : record.add(ctx.getInput(), spanEntry(ctx.getInput(), null));
            record.run.begun = true;
        });
    }

    @Override
    public void beginBuild(final Object span, final int index) {
        guarded(current, () -> {
            final Record record = current.get();
            if (record != null && record.segment) {
                record.run = record.find(span);
            }
        });
    }

    @Override
    public void endSpan(final ExecutionContext ctx) {
        guarded(current, () -> {
            final Record record = current.get();
            if (record == null || record.run == null) {
                return;
            }
            final SpanRun run = record.run;
            run.state = state(ctx);
            if (record.segment) {
                return;
            }
            addHalves(run, ctx.getMeta());
            // The runner merges the vote of a run that continued only; an aborted or failed run's vote stays in its
            // steps and state.
            if (run.continueOn && ctx.isPersistVoted()) {
                final JsonObject persist = output("persist", run.persistLine);
                persist.addProperty("vote", vote(ctx));
                run.outputs.add(persist);
            }
            current.remove();
            publish(record);
        });
    }

    @Override
    public void endSegment() {
        guarded(current, () -> {
            final Record record = current.get();
            current.remove();
            if (record != null && record.segment) {
                publish(record);
            }
        });
    }

    @Override
    public void failed(final ExecutionContext ctx, final Throwable failure) {
        guarded(current, () -> {
            final SpanRun run = run();
            if (run == null) {
                return;
            }
            fail(run, failure);
            run.state = state(ctx);
            // Operands of a condition the failure cut short belong to no step.
            run.operands = new JsonArray();
        });
    }

    private static void fail(final SpanRun run, final Throwable failure) {
        final String reason = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        run.fail(reason);
        final JsonObject step = new JsonObject();
        step.addProperty("kind", "failure");
        step.addProperty("value", reason);
        run.steps.add(step);
    }

    // ==================== Pairing ====================

    @Override
    public void pairing(final String role, final String outcome, final String peer, final String offeredBy) {
        guarded(current, () -> {
            final SpanRun run = run();
            if (run == null) {
                return;
            }
            final String[] asked = run.asked.get("caller".equals(role) ? "prepareNetworkAlias" : "buildNetworkAlias");
            final int line = asked == null ? 0 : Integer.parseInt(asked[1]);
            final JsonObject pairing = output("pairing", line);
            pairing.addProperty("role", role);
            pairing.addProperty("outcome", outcome);
            if (peer != null) {
                pairing.addProperty("peer", peer);
            }
            if (offeredBy != null) {
                pairing.addProperty("offeredBy", offeredBy);
            }
            run.outputs.add(pairing);
            if (asked != null) {
                addStep(run, asked[0], "pairing", line, asked[2]).addProperty("output", run.outputs.size() - 1);
            }
        });
    }

    // ==================== Steps ====================

    @Override
    public void step(final ExecutionContext ctx, final String stage, final String kind, final int line,
                     final String text, final Object value) {
        guarded(current, () -> {
            final Record record = current.get();
            final SpanRun run = record == null ? null : record.run;
            if (run == null) {
                return;
            }
            // first {} and a build {} are runs of their own on the span: an abort ends that block only.
            if ("abort".equals(kind) && !"first".equals(stage) && !"build".equals(stage)) {
                run.stop("abort {} at line " + fileLine(line));
            }
            if ("block".equals(kind) && "persist".equals(stage) && run.persistLine == 0) {
                run.persistLine = line;
            }
            // The half a run asks for is sent once its span's rules ran; its output names the statement that asked:
            // the first, as the run keeps the first peer a rule names, and an empty one names none.
            final boolean asks = "buildNetworkAlias".equals(kind)
                || "prepareNetworkAlias".equals(kind) && value instanceof String && !((String) value).isEmpty();
            if (asks) {
                run.asked.putIfAbsent(kind, new String[] {stage, Integer.toString(line), text});
            }
            if ("writeNetworkAlias".equals(kind)) {
                final JsonObject alias = output("writeNetworkAlias", line);
                alias.add("address", render(value));
                run.outputs.add(alias);
                addStep(run, stage, kind, line, text).addProperty("output", run.outputs.size() - 1);
                return;
            }
            if (!statementGranularity && !BLOCK_KINDS.contains(kind)) {
                return;
            }
            final JsonObject step = addStep(run, stage, kind, line, text);
            if (VOTE_KINDS.contains(kind)) {
                step.addProperty("value", vote(ctx));
            } else if ("tag".equals(kind) && value instanceof Object[] && ((Object[]) value).length == 2) {
                final JsonObject tag = new JsonObject();
                tag.add(String.valueOf(((Object[]) value)[0]), render(((Object[]) value)[1]));
                step.add("value", tag);
            } else if (value != null || VALUE_KINDS.contains(kind)) {
                step.add("value", render(value));
            }
        });
    }

    @Override
    public void operand(final ExecutionContext ctx, final String text, final Object value) {
        guarded(current, () -> {
            final SpanRun run = run();
            if (run == null || !statementGranularity) {
                return;
            }
            final JsonObject operand = new JsonObject();
            operand.addProperty("text", text);
            operand.add("value", render(value));
            run.operands.add(operand);
        });
    }

    @Override
    public void condition(final ExecutionContext ctx, final String stage, final int line, final String text,
                          final boolean result) {
        guarded(current, () -> {
            final SpanRun run = run();
            if (run == null || !statementGranularity) {
                return;
            }
            final JsonObject step = addStep(run, stage, "if", line, text);
            if (run.operands.size() > 0) {
                step.add("operands", run.operands);
                run.operands = new JsonArray();
            }
            step.addProperty("result", result);
        });
    }

    @Override
    public void source(final ExecutionContext ctx, final String stage, final int line, final String text,
                       final ISource source) {
        guarded(current, () -> {
            final SpanRun run = run();
            if (run == null) {
                return;
            }
            final JsonObject output = output("source", line);
            output.addProperty("scope", DefaultScopeDefine.nameOf(source.scope()));
            output.add("fields", selfRendered(source));
            run.outputs.add(output);
            addStep(run, stage, "source", line, text).addProperty("output", run.outputs.size() - 1);
        });
    }

    @Override
    public void metric(final ExecutionContext ctx, final String stage, final int line, final String text,
                       final String metric, final MeterEntity entity, final Object observation,
                       final long timestamp) {
        guarded(current, () -> {
            final SpanRun run = run();
            if (run == null) {
                return;
            }
            final JsonObject output = output("metrics", line);
            output.addProperty("metric", metric);
            output.add("entity", entity(entity));
            output.add("observation", render(observation));
            output.addProperty("timestamp", timestamp);
            run.outputs.add(output);
            addStep(run, stage, "metrics", line, text).addProperty("output", run.outputs.size() - 1);
        });
    }

    @Override
    public void metricSkipped(final ExecutionContext ctx, final String stage, final int line, final String text,
                              final String metric, final String reason) {
        guarded(current, () -> {
            final SpanRun run = run();
            if (run == null) {
                return;
            }
            final JsonObject output = output("metrics", line);
            output.addProperty("metric", metric);
            output.addProperty("skipped", reason);
            run.outputs.add(output);
            addStep(run, stage, "metrics", line, text).addProperty("output", run.outputs.size() - 1);
        });
    }

    // ==================== Assembly ====================

    private SpanRun run() {
        final Record record = current.get();
        return record == null ? null : record.run;
    }

    private JsonObject addStep(final SpanRun run, final String stage, final String kind, final int line,
                               final String text) {
        final JsonObject step = new JsonObject();
        step.addProperty("stage", stage);
        step.addProperty("kind", kind);
        addLine(step, line);
        step.addProperty("sourceText", text);
        run.steps.add(step);
        return step;
    }

    private JsonObject output(final String kind, final int line) {
        final JsonObject output = new JsonObject();
        output.addProperty("kind", kind);
        if (line > 0) {
            addLine(output, line);
        }
        return output;
    }

    private void addLine(final JsonObject target, final int line) {
        if (dslFirstLine > 0) {
            target.addProperty("sourceLine", fileLine(line));
        } else {
            target.addProperty("dslLine", line);
        }
    }

    private int fileLine(final int line) {
        return dslFirstLine > 0 && line > 0 ? dslFirstLine + line - 1 : line;
    }

    private void publish(final Record record) {
        final JsonObject body = new JsonObject();
        body.add("context", record.context);
        final JsonArray spans = new JsonArray();
        for (final SpanRun run : record.spans) {
            final JsonObject entry = run.entry;
            if (!run.begun) {
                // A span the rule never ran on, after the segment failed on an earlier one, has no run.
                spans.add(entry);
                continue;
            }
            final JsonObject result = new JsonObject();
            result.addProperty("continueOn", run.continueOn);
            if (run.reason != null) {
                result.addProperty("reason", run.reason);
            }
            result.add("steps", run.steps);
            result.add("outputs", run.outputs);
            if (run.state != null) {
                result.add("state", run.state);
            }
            entry.add("run", result);
            spans.add(entry);
        }
        body.add("spans", spans);
        publishRecord(newRecord(record.startedAt, recordMetadata, body.toString()));
    }

    private static JsonObject segmentContext(final SwNativeSegmentState state) {
        final SegmentObject segment = state.getSegmentObject();
        final JsonObject wire = new JsonObject();
        wire.addProperty("traceId", segment.getTraceId());
        wire.addProperty("traceSegmentId", segment.getTraceSegmentId());
        wire.addProperty("service", segment.getService());
        wire.addProperty("serviceInstance", segment.getServiceInstance());
        wire.addProperty("isSizeLimited", segment.getIsSizeLimited());
        wire.addProperty("spanCount", segment.getSpansCount());
        // What the runner derived before any rule ran, named by the function a rule reads each with.
        final JsonObject derived = new JsonObject();
        derived.addProperty("segmentStartTime", state.getStartTime());
        derived.addProperty("segmentLatency", state.getLatency());
        derived.addProperty("segmentIsError", state.isError());
        derived.addProperty("segmentSampled", state.isSampled());
        final JsonArray tags = new JsonArray();
        for (final Tag tag : state.getTags()) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("key", tag.getKey());
            entry.addProperty("value", tag.getValue());
            tags.add(entry);
        }
        derived.add("segmentTags", tags);
        derived.addProperty("segmentBytes", segment.getSerializedSize());
        final JsonObject context = new JsonObject();
        context.addProperty("kind", "segment");
        context.add("segment", wire);
        context.add("derived", derived);
        return context;
    }

    private static JsonObject otlpContext(final Map<String, String> resource, final Map<String, String> scope) {
        final JsonObject context = new JsonObject();
        context.addProperty("kind", "span");
        context.add("resource", render(resource));
        context.add("scope", render(scope));
        return context;
    }

    /**
     * @param meta an OTLP span's metadata as the receiver read it, before the rule could overwrite its identity; null
     *             for a native span
     */
    private static JsonObject spanEntry(final Object span, final SpanMetadata meta) {
        final JsonObject entry = new JsonObject();
        entry.add("span", render(span));
        if (meta != null) {
            entry.add("meta", meta(meta));
        }
        return entry;
    }

    private static JsonObject meta(final SpanMetadata meta) {
        final JsonObject json = new JsonObject();
        json.addProperty("service", meta.getService());
        json.addProperty("serviceInstance", meta.getServiceInstance());
        json.addProperty("endpoint", meta.getEndpoint());
        json.addProperty("layer", meta.getLayer());
        json.addProperty("kind", meta.getKind());
        json.addProperty("statusCode", meta.getStatusCode());
        json.addProperty("statusMessage", meta.getStatusMessage());
        json.addProperty("startTime", meta.getStartTime());
        json.addProperty("timestamp", meta.getTimestamp());
        json.addProperty("latency", meta.getLatency());
        json.addProperty("traceId", meta.getTraceId());
        json.addProperty("spanId", meta.getSpanId());
        json.addProperty("parentSpanId", meta.getParentSpanId());
        json.addProperty("root", meta.isRoot());
        json.addProperty("parentLocation", meta.getParentLocation());
        json.addProperty("scopeName", meta.getScopeName());
        json.addProperty("scopeVersion", meta.getScopeVersion());
        json.addProperty("traceStorage", meta.getTraceStorage());
        return json;
    }

    /**
     * The run's state at its end: the identity it left on the span, its tags and its vote for OTLP; the
     * {@code segmentDef} values for native, which every later run of the rule on the segment reads.
     */
    private JsonObject state(final ExecutionContext ctx) {
        final JsonObject state = new JsonObject();
        if (ctx.getNativeSegment() != null) {
            final Object[] values = ctx.getSegmentDefs();
            final JsonObject defs = new JsonObject();
            for (int i = 0; i < values.length; i++) {
                defs.add(i < segmentDefNames.length ? segmentDefNames[i] : "#" + i, render(values[i]));
            }
            state.add("segmentDefs", defs);
            return state;
        }
        final SpanMetadata meta = ctx.getMeta();
        final JsonObject identity = new JsonObject();
        identity.addProperty("service", meta.getService());
        identity.addProperty("serviceInstance", meta.getServiceInstance());
        identity.addProperty("endpoint", meta.getEndpoint());
        identity.addProperty("layer", meta.getLayer());
        state.add("meta", identity);
        state.add("tags", render(ctx.getAdditionalTags()));
        if (ctx.isPersistVoted()) {
            state.addProperty("persist", vote(ctx));
        }
        return state;
    }

    private static String vote(final ExecutionContext ctx) {
        if (!ctx.isPersistVoted()) {
            return "abstain";
        }
        if (ctx.isSpanDropped()) {
            return "drop";
        }
        if (ctx.isEnforced()) {
            return "enforced";
        }
        return ctx.isSpanSampledOut() ? "sampledOut" : "keep";
    }

    /**
     * The half each {@code pairing} output asked for, as this run built it: the run's own metadata, after the
     * {@code service} it may have set, and a caller half's peer. Whether the span sent this run's half or an earlier
     * rule's is the output's {@code offeredBy}.
     */
    private static void addHalves(final SpanRun run, final SpanMetadata meta) {
        if (meta == null) {
            return;
        }
        for (final JsonElement element : run.outputs) {
            final JsonObject output = element.getAsJsonObject();
            if (!"pairing".equals(output.get("kind").getAsString())) {
                continue;
            }
            final JsonObject half = new JsonObject();
            half.addProperty("traceId", meta.getTraceId());
            half.addProperty("spanId", meta.getSpanId());
            half.addProperty("parentSpanId", meta.getParentSpanId());
            half.addProperty("service", meta.getService());
            if (output.has("peer")) {
                half.add("peer", output.get("peer"));
            }
            output.add("half", half);
        }
    }

    private static JsonObject entity(final MeterEntity entity) {
        final JsonObject json = new JsonObject();
        if (entity == null) {
            return json;
        }
        json.addProperty("scope", entity.getScopeType() == null ? null : entity.getScopeType().name());
        addIfSet(json, "service", entity.getServiceName());
        addIfSet(json, "serviceInstance", entity.getInstanceName());
        addIfSet(json, "endpoint", entity.getEndpointName());
        addIfSet(json, "sourceService", entity.getSourceServiceName());
        addIfSet(json, "destService", entity.getDestServiceName());
        addIfSet(json, "sourceServiceInstance", entity.getSourceInstanceName());
        addIfSet(json, "destServiceInstance", entity.getDestInstanceName());
        if (entity.getDetectPoint() != null) {
            json.addProperty("detectPoint", entity.getDetectPoint().name());
        }
        if (entity.getLayer() != null) {
            json.addProperty("layer", entity.getLayer().name());
        }
        if (entity.getDestLayer() != null) {
            json.addProperty("destLayer", entity.getDestLayer().name());
        }
        if (entity.getComponentId() != 0) {
            json.addProperty("componentId", entity.getComponentId());
        }
        return json;
    }

    private static void addIfSet(final JsonObject json, final String key, final String value) {
        if (value != null && !value.isEmpty()) {
            json.addProperty(key, value);
        }
    }

    /**
     * A value a statement produced, as JSON: the protobuf messages through their descriptor, the types that render
     * themselves through {@link ToJson}, collections element by element up to {@value #MAX_ELEMENTS}.
     */
    private static JsonElement render(final Object value) {
        return render(value, 0);
    }

    private static JsonElement render(final Object value, final int depth) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (depth > MAX_DEPTH) {
            return new JsonPrimitive("...");
        }
        if (value instanceof String) {
            return new JsonPrimitive((String) value);
        }
        if (value instanceof Number) {
            return new JsonPrimitive((Number) value);
        }
        if (value instanceof Boolean) {
            return new JsonPrimitive((Boolean) value);
        }
        if (value instanceof Enum) {
            return new JsonPrimitive(((Enum<?>) value).name());
        }
        if (value instanceof Layer) {
            return new JsonPrimitive(((Layer) value).name());
        }
        if (value instanceof MeterEntity) {
            return entity((MeterEntity) value);
        }
        if (value instanceof Message) {
            try {
                return JsonParser.parseString(PRINTER.print((Message) value));
            } catch (final InvalidProtocolBufferException e) {
                return new JsonPrimitive("unprintable " + value.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        if (value instanceof ToJson) {
            return selfRendered((ToJson) value);
        }
        if (value instanceof JsonElement) {
            return bounded((JsonElement) value, depth);
        }
        if (value instanceof byte[]) {
            final JsonObject bytes = new JsonObject();
            bytes.addProperty("bytes", ((byte[]) value).length);
            return bytes;
        }
        if (value.getClass().isArray() && value.getClass().getComponentType().isPrimitive()) {
            final JsonArray array = new JsonArray();
            final int length = Array.getLength(value);
            for (int i = 0; i < length && i < MAX_ELEMENTS; i++) {
                array.add(render(Array.get(value, i), depth + 1));
            }
            if (length > MAX_ELEMENTS) {
                array.add(length - MAX_ELEMENTS + " more");
            }
            return array;
        }
        if (value instanceof Object[]) {
            return array(Arrays.asList((Object[]) value), depth);
        }
        if (value instanceof Collection) {
            return array((Collection<?>) value, depth);
        }
        if (value instanceof Map) {
            final JsonObject map = new JsonObject();
            int count = 0;
            for (final Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                if (count++ == MAX_ELEMENTS) {
                    map.addProperty("...", ((Map<?, ?>) value).size() - MAX_ELEMENTS + " more");
                    break;
                }
                map.add(String.valueOf(e.getKey()), render(e.getValue(), depth + 1));
            }
            return map;
        }
        return new JsonPrimitive(String.valueOf(value));
    }

    /**
     * A JSON value a rule parsed or built, as JSON rather than its text, within the same bounds as any other value.
     */
    private static JsonElement bounded(final JsonElement value, final int depth) {
        if (depth > MAX_DEPTH) {
            return new JsonPrimitive("...");
        }
        if (value.isJsonObject()) {
            final JsonObject copy = new JsonObject();
            int count = 0;
            for (final Map.Entry<String, JsonElement> e : value.getAsJsonObject().entrySet()) {
                if (count++ == MAX_ELEMENTS) {
                    copy.addProperty("...", value.getAsJsonObject().size() - MAX_ELEMENTS + " more");
                    break;
                }
                copy.add(e.getKey(), bounded(e.getValue(), depth + 1));
            }
            return copy;
        }
        if (value.isJsonArray()) {
            final JsonArray copy = new JsonArray();
            final JsonArray source = value.getAsJsonArray();
            for (int i = 0; i < source.size() && i < MAX_ELEMENTS; i++) {
                copy.add(bounded(source.get(i), depth + 1));
            }
            if (source.size() > MAX_ELEMENTS) {
                copy.add(source.size() - MAX_ELEMENTS + " more");
            }
            return copy;
        }
        return value;
    }

    private static JsonArray array(final Collection<?> values, final int depth) {
        final JsonArray array = new JsonArray();
        int count = 0;
        for (final Object element : values) {
            if (count++ == MAX_ELEMENTS) {
                array.add(values.size() - MAX_ELEMENTS + " more");
                break;
            }
            array.add(render(element, depth + 1));
        }
        return array;
    }

    /**
     * A type that renders itself can fail to, as a source whose entity id needs a field the rule left unset; that
     * marks the value, not the record.
     */
    private static JsonElement selfRendered(final ToJson value) {
        try {
            return parse(value.toJson());
        } catch (final RuntimeException e) {
            return new JsonPrimitive("unrenderable " + value.getClass().getSimpleName() + ": " + e);
        }
    }

    private static JsonElement parse(final String json) {
        try {
            return JsonParser.parseString(json == null || json.isEmpty() ? "{}" : json);
        } catch (final RuntimeException e) {
            return new JsonPrimitive(json);
        }
    }

    /**
     * @param state the record the probe assembles, dropped when the probe fails; null for a probe that assembles none
     */
    private void guarded(final InFlight state, final Runnable probe) {
        try {
            probe.run();
        } catch (final RuntimeException e) {
            if (state != null) {
                state.remove();
            }
            log.debug("TAL debug session {} dropped a record: {}", sessionId(), e.getMessage(), e);
        }
    }

    @Override
    protected void onCaptured() {
        current.clear();
    }

    /**
     * The records being assembled, one per thread. The recorder owns them, unlike a {@code ThreadLocal}, whose value
     * the thread would keep until the session ends, so reaching the cap drops what is in flight at once.
     */
    private final class InFlight {
        private final Map<Thread, Record> records = new ConcurrentHashMap<>();

        private Record get() {
            return records.get(Thread.currentThread());
        }

        private void set(final Record record) {
            records.put(Thread.currentThread(), record);
            // The cap flips captured before onCaptured clears the maps: a put that reads it false happened before
            // that clear, which removes it; one that reads it true removes it here.
            if (isCaptured()) {
                records.remove(Thread.currentThread());
            }
        }

        private void remove() {
            records.remove(Thread.currentThread());
        }

        private void clear() {
            records.clear();
        }
    }

    /**
     * A record being assembled.
     */
    private static final class Record {
        private final long startedAt = System.currentTimeMillis();
        private final JsonObject context;
        private final boolean segment;
        private final List<SpanRun> spans = new ArrayList<>();
        /**
         * The span the next step goes to; null drops it.
         */
        private SpanRun run;

        private Record(final JsonObject context, final boolean segment) {
            this.context = context;
            this.segment = segment;
        }

        private SpanRun add(final Object input, final JsonObject entry) {
            final SpanRun run = new SpanRun(input, entry);
            spans.add(run);
            return run;
        }

        /**
         * @return the run of the span the runner passed, matched by identity: the runner passes the same object for
         * span 0's {@code first {}} run, its regular run and every {@code build {}} block a run queued
         */
        private SpanRun find(final Object input) {
            for (final SpanRun run : spans) {
                if (run.input == input) {
                    return run;
                }
            }
            return null;
        }
    }

    /**
     * One span and the rule's run on it.
     */
    private static final class SpanRun {
        private final Object input;
        private final JsonObject entry;
        private final JsonArray steps = new JsonArray();
        private final JsonArray outputs = new JsonArray();
        private JsonObject state;
        private boolean continueOn = true;
        private String reason;
        private int persistLine;
        /**
         * Whether the rule ran on the span; a native record lists every span of its segment from the start.
         */
        private boolean begun;
        /**
         * The network-alias statements the run executed, by kind: their stage, line and text.
         */
        private final Map<String, String[]> asked = new HashMap<>(2);
        /**
         * The operands of the condition being evaluated, which its {@code if} step takes.
         */
        private JsonArray operands = new JsonArray();

        private SpanRun(final Object input, final JsonObject entry) {
            this.input = input;
            this.entry = entry;
        }

        private void stop(final String why) {
            if (continueOn) {
                continueOn = false;
                reason = why;
            }
        }

        /**
         * A failure stops the run whatever stopped a block before it, and is the reason.
         */
        private void fail(final String why) {
            continueOn = false;
            reason = why;
        }

    }
}
