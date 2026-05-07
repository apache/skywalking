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

package org.apache.skywalking.oap.log.analyzer.v2.dsl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import lombok.Getter;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.v2.dsldebug.LalPayloadDebugDump;
import org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.server.core.dsldebug.GateHolder;
import org.apache.skywalking.oap.server.core.source.LALOutputBuilder;
import org.apache.skywalking.oap.server.core.source.LogMetadata;

/**
 * Mutable property storage for a single LAL script execution cycle.
 *
 * <p>A new ExecutionContext is created for each incoming log. It carries all
 * per-log state through the compiled LAL pipeline:
 * <ul>
 *   <li>{@code metadata} — the {@link LogMetadata} (service, layer, timestamp, trace context)</li>
 *   <li>{@code input} — the raw input object (LogData.Builder for standard logs, typed proto for ALS)</li>
 *   <li>{@code parsed} — structured data extracted by json/text/yaml parsers</li>
 *   <li>{@code save}/{@code abort} — control flags set by extractor/sink logic</li>
 *   <li>{@code metrics_container} — optional list for LAL-extracted metrics (log-MAL)</li>
 *   <li>{@code dry_run} — when true, sink skips persistence (used by the LAL test tool)</li>
 * </ul>
 */
public class ExecutionContext {
    public static final String KEY_INPUT = "input";
    public static final String KEY_METADATA = "metadata";
    public static final String KEY_PARSED = "parsed";
    public static final String KEY_SAVE = "save";
    public static final String KEY_ABORT = "abort";
    public static final String KEY_METRICS_CONTAINER = "metrics_container";
    public static final String KEY_DRY_RUN = "dry_run";
    public static final String KEY_AUTO_LAYER = "auto_layer";
    public static final String KEY_OUTPUT = "output";

    private final Map<String, Object> properties = new HashMap<>();

    /**
     * The rule's debug capture binding, set on entry to the generated
     * {@code execute()} method. Downstream analyzer wrappers
     * (RecordSinkListener, MetricExtractor) read it here to fire the
     * terminal-output probes without having to re-resolve the rule's
     * compiled {@code LalExpression}.
     */
    @Getter
    @lombok.Setter
    private GateHolder debugHolder;

    /**
     * Resolved {@link LALSourceTypeProvider} for this log's layer. Set
     * by {@link org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogFilterListener}
     * at parse time using {@code metadata.getLayer()}; remains
     * {@code null} for the default agent path (no provider registered =
     * input is {@code LogData} / {@code LogData.Builder}).
     *
     * <p>The provider supplies layer-level type resolution
     * ({@link LALSourceTypeProvider#inputType()} /
     * {@link LALSourceTypeProvider#outputType()}) at compile time. The
     * dsl-debugging dump path renders payloads via the
     * {@link LALOutputBuilder} the rule installed on this context; the
     * provider is not consulted at dump time.
     */
    @Getter
    @lombok.Setter
    private LALSourceTypeProvider sourceTypeProvider;

    public ExecutionContext() {
        setProperty(KEY_PARSED, new Parsed());
    }

    public void setProperty(final String name, final Object value) {
        properties.put(name, value);
    }

    public Object getProperty(final String name) {
        return properties.get(name);
    }

    /**
     * Initialize from metadata + input.
     */
    public ExecutionContext init(final LogMetadata metadata, final Object input) {
        setProperty(KEY_METADATA, metadata);
        setProperty(KEY_INPUT, input);
        setProperty(KEY_SAVE, true);
        setProperty(KEY_ABORT, false);
        setProperty(KEY_METRICS_CONTAINER, null);
        setProperty(KEY_DRY_RUN, false);
        setProperty(KEY_OUTPUT, null);
        return this;
    }

    public LogMetadata metadata() {
        return (LogMetadata) getProperty(KEY_METADATA);
    }

    /**
     * Returns the raw input object. For standard logs this is a {@code LogData.Builder};
     * for ALS it is the typed proto (e.g., {@code HTTPAccessLogEntry}).
     */
    public Object input() {
        return getProperty(KEY_INPUT);
    }

    public ExecutionContext parsed(final Matcher parsed) {
        parsed().matcher = parsed;
        return this;
    }

    public ExecutionContext parsed(final Map<String, Object> parsed) {
        parsed().map = parsed;
        return this;
    }

    public Parsed parsed() {
        return (Parsed) getProperty(KEY_PARSED);
    }

    public ExecutionContext save() {
        setProperty(KEY_SAVE, true);
        return this;
    }

    public ExecutionContext drop() {
        setProperty(KEY_SAVE, false);
        return this;
    }

    public boolean shouldSave() {
        return (boolean) getProperty(KEY_SAVE);
    }

    public ExecutionContext abort() {
        setProperty(KEY_ABORT, true);
        return this;
    }

    public boolean shouldAbort() {
        return (boolean) getProperty(KEY_ABORT);
    }

    public ExecutionContext metricsContainer(final List<SampleFamily> container) {
        setProperty(KEY_METRICS_CONTAINER, container);
        return this;
    }

    @SuppressWarnings("unchecked")
    public Optional<List<SampleFamily>> metricsContainer() {
        return Optional.ofNullable((List<SampleFamily>) getProperty(KEY_METRICS_CONTAINER));
    }

    public ExecutionContext dryRun(final boolean dryRun) {
        setProperty(KEY_DRY_RUN, dryRun);
        return this;
    }

    public boolean isDryRun() {
        return (boolean) getProperty(KEY_DRY_RUN);
    }

    public ExecutionContext autoLayerMode(final boolean autoLayer) {
        setProperty(KEY_AUTO_LAYER, autoLayer);
        return this;
    }

    public boolean isAutoLayerMode() {
        final Object val = getProperty(KEY_AUTO_LAYER);
        return val != null && (boolean) val;
    }

    public void setOutput(final Object output) {
        setProperty(KEY_OUTPUT, output);
    }

    public Object output() {
        return getProperty(KEY_OUTPUT);
    }

    public LALOutputBuilder outputAsBuilder() {
        return (LALOutputBuilder) getProperty(KEY_OUTPUT);
    }

    /**
     * Payload for the input-type sample fired at the top of the LAL
     * pipeline (one per execution). Renders the raw input directly via
     * the framework's native {@link LogData.Builder} renderer — does
     * NOT consult the output {@link LALOutputBuilder}, which has just
     * been constructed by the generated {@code execute()} body and is
     * not yet populated by {@code init()} (init runs at the sink stage).
     *
     * <p>Mirrors the {@code aborted} / {@code hasParsed} flags and the
     * parsed-map key set so operators can see which extractor branches
     * a log walked through. No {@code output} key — that data evolves
     * in subsequent samples and would be empty here.
     */
    public String inputPayloadJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("aborted", shouldAbort());
        obj.addProperty("hasParsed", parsed() != null);
        obj.add("input", LalPayloadDebugDump.parse(renderRawInput()));
        obj.add("parsedKeys", parsedKeysJson());
        return obj.toString();
    }

    /**
     * Payload for every sample after the input probe — block / statement
     * / function / output. Renders only the {@link LALOutputBuilder}'s
     * accumulated state (the DB-bound fields the rule has set so far,
     * including {@code content} / {@code contentType} from the cached
     * input body — see {@code LogBuilder.appendBodyContent}). The raw
     * input is captured once at the top of the pipeline; repeating it
     * on every probe would be redundant.
     */
    public String outputPayloadJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("aborted", shouldAbort());
        obj.addProperty("hasParsed", parsed() != null);
        obj.add("output", LalPayloadDebugDump.parse(renderOutput()));
        obj.add("parsedKeys", parsedKeysJson());
        return obj.toString();
    }

    /**
     * Native renderer for {@code ctx.input()} — used at the input probe
     * before any builder has been initialised. Agent path delivers a
     * {@link LogData.Builder}; receiver-typed paths (envoy ALS,
     * arbitrary protobufs) deliver a {@link com.google.protobuf.Message}
     * subclass. Falls back to the unknown-type hint if neither fits, so
     * a misconfigured layer doesn't crash the dump.
     */
    private String renderRawInput() {
        final Object value = input();
        if (value == null) {
            return "null";
        }
        if (value instanceof LogData.Builder) {
            return LalPayloadDebugDump.toJson((LogData.Builder) value);
        }
        if (value instanceof LogData) {
            return LalPayloadDebugDump.toJson((LogData) value);
        }
        if (value instanceof com.google.protobuf.Message) {
            return LalPayloadDebugDump.toJson((com.google.protobuf.Message) value);
        }
        return LalPayloadDebugDump.unknownTypeHint(value);
    }

    /**
     * Render the output payload — every LAL rule installs a
     * {@link LALOutputBuilder} on the context, and the builder owns its
     * own debug serialization via
     * {@link LALOutputBuilder#outputToJson()}. The interface contract
     * makes the dispatch a single static call.
     *
     * <p>If the output slot is empty (the rule aborted before
     * installing a builder), nothing meaningful exists to dump — the
     * helper returns a hint object so the operator can see the type
     * the framework received and add an {@link LALOutputBuilder} for
     * it.
     */
    private String renderOutput() {
        final Object value = output();
        if (value == null) {
            return "null";
        }
        if (value instanceof LALOutputBuilder) {
            return ((LALOutputBuilder) value).outputToJson();
        }
        final JsonObject hint = new JsonObject();
        hint.addProperty("type", value.getClass().getName());
        hint.addProperty("note", "cannot-serialize-output-no-LALOutputBuilder-impl");
        return hint.toString();
    }

    private JsonArray parsedKeysJson() {
        final JsonArray keys = new JsonArray();
        if (parsed() != null && parsed().getMap() != null) {
            for (final String key : parsed().getMap().keySet()) {
                keys.add(key);
            }
        }
        return keys;
    }

    public static class Parsed {
        @Getter
        private Matcher matcher;

        @Getter
        private Map<String, Object> map;
    }
}
