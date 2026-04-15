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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import lombok.Getter;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
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

    public static class Parsed {
        @Getter
        private Matcher matcher;

        @Getter
        private Map<String, Object> map;
    }
}
