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

package org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.network.logging.v3.JSONLog;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.AbstractSpec;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor.MetricExtractor;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.parser.JsonParserSpec;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.parser.ParseFailureWarnLimiter;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.parser.TextParserSpec;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.parser.YamlParserSpec;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.sink.SamplerSpec;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.sink.SinkSpec;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogSinkListenerFactory;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.RecordSinkListener;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.TrafficSinkListener;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The top-level runtime API that compiled LAL expressions invoke.
 *
 * <p>A compiled {@link org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression}
 * calls methods on this class in the order defined by the LAL script.
 * All methods receive an explicit {@link ExecutionContext} parameter — no ThreadLocal state.
 */
public class FilterSpec extends AbstractSpec {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilterSpec.class);

    private final List<LogSinkListenerFactory> sinkListenerFactories;

    private final TextParserSpec textParser;

    private final JsonParserSpec jsonParser;

    private final YamlParserSpec yamlParser;

    private final MetricExtractor extractor;

    private final SinkSpec sink;

    private final TypeReference<Map<String, Object>> parsedType;

    private final ParseFailureWarnLimiter jsonWarnLimiter =
        new ParseFailureWarnLimiter(ParseFailureWarnLimiter.DEFAULT_INTERVAL_MS);

    private final ParseFailureWarnLimiter yamlWarnLimiter =
        new ParseFailureWarnLimiter(ParseFailureWarnLimiter.DEFAULT_INTERVAL_MS);

    public FilterSpec(final ModuleManager moduleManager,
                      final LogAnalyzerModuleConfig moduleConfig) throws ModuleStartException {
        super(moduleManager, moduleConfig);

        parsedType = new TypeReference<Map<String, Object>>() {
        };

        sinkListenerFactories = Arrays.asList(
            new RecordSinkListener.Factory(moduleManager(), moduleConfig()),
            new TrafficSinkListener.Factory(moduleManager(), moduleConfig())
        );

        textParser = new TextParserSpec(moduleManager(), moduleConfig());
        jsonParser = new JsonParserSpec(moduleManager(), moduleConfig());
        yamlParser = new YamlParserSpec(moduleManager(), moduleConfig());

        extractor = new MetricExtractor(moduleManager(), moduleConfig());

        sink = new SinkSpec(moduleManager(), moduleConfig());
    }

    /**
     * LAL {@code text {}} — no-op body parser, body is available as raw text.
     * Parsed data is not populated; use {@code log.body} to access raw content.
     */
    public void text(final ExecutionContext ctx) {
        if (ctx.shouldAbort()) {
            return;
        }
    }

    /**
     * LAL {@code text { regexp '...' }} — applies a named-group regexp to the
     * log body text. Matched groups are stored in {@code ctx.parsed().getMatcher()}
     * and accessed via {@code parsed.groupName} in the LAL script. {@code abortOnFailure}
     * is the rule's compile-time flag (default {@code true}): a non-matching body aborts
     * the filter chain only when it is set. The flag travels as a parameter rather than
     * as parser-spec state because one {@code FilterSpec} instance serves every compiled
     * rule concurrently.
     */
    public void textWithRegexp(final ExecutionContext ctx, final String regexp,
                               final boolean abortOnFailure) {
        if (ctx.shouldAbort()) {
            return;
        }
        textParser.regexp(ctx, regexp, abortOnFailure);
    }

    /**
     * LAL {@code json {}} — parses the JSON log body into a {@code Map<String, Object>}
     * and stores it in {@code ctx.parsed()}. Reads {@code LogData.body.json.json}; when
     * that is empty but a text body is present, falls back to parsing
     * {@code LogData.body.text.text} as JSON — the OTLP log receiver delivers every
     * string body (even JSON-shaped ones) as a text body, and a rule that declared
     * {@code json {}} means the content is JSON regardless of which body case carried it.
     * A genuine parse failure is logged — rate-limited WARN when the failure aborts the
     * log, DEBUG when {@code abortOnFailure false} makes the miss expected control flow —
     * and then honors {@code abortOnFailure} (the rule's compile-time flag, default
     * {@code true}); the flag travels as a parameter rather than as parser-spec state
     * because one {@code FilterSpec} instance serves every compiled rule concurrently.
     * A typed-proto input (e.g. Envoy ALS {@code HTTPAccessLogEntry}) is not a parse
     * failure: shipped rules use {@code json {}} on such layers as a routing guard, so
     * the mismatch stays quiet and just honors the flag.
     *
     * <p>On a successful parse that fell back to the text body, this rule's context input
     * is swapped to a copy whose body is a JSON body carrying the same content, so the log
     * this rule persists gets {@code ContentType.JSON} (persistence derives the stored
     * content type from the body case in {@code LogBuilder.toLog()}). The original input
     * object is shared by every rule analyzing the same log and is never mutated — other
     * rules still see the original text body.
     *
     * <p>Metadata fields (service, serviceInstance, endpoint, layer, timestamp)
     * are also added to the map via {@code putIfAbsent}, so body values take
     * priority while metadata fields serve as fallback — matching v1 Groovy
     * {@code Binding.Parsed.getAt(key)} behavior.
     */
    public void json(final ExecutionContext ctx, final boolean abortOnFailure) {
        if (ctx.shouldAbort()) {
            return;
        }
        final Object rawInput = ctx.input();
        if (!(rawInput instanceof LogData.Builder)) {
            abortOrContinueUnparsed(ctx, abortOnFailure, notLogBodyReason("json", rawInput));
            return;
        }
        try {
            final LogData.Builder logData = (LogData.Builder) rawInput;
            final LogDataBody body = logData.getBody();
            String content = body.getJson().getJson();
            final boolean fromText = content.isEmpty();
            if (fromText) {
                content = body.getText().getText();
            }
            final Map<String, Object> parsed = jsonParser.create().readValue(content, parsedType);
            addMetadataFields(parsed, ctx.metadata());
            ctx.parsed(parsed);
            if (fromText) {
                ctx.input(logData.build().toBuilder().setBody(LogDataBody.newBuilder()
                    .setJson(JSONLog.newBuilder().setJson(content).build())
                    .build()));
            }
        } catch (final Exception e) {
            warnParseFailure(jsonWarnLimiter, "json", ctx, abortOnFailure, e);
            abortOrContinueUnparsed(ctx, abortOnFailure, "json parse failed: " + e);
        }
    }

    /**
     * LAL {@code yaml {}} — parses {@code LogData.body.yaml.yaml} into a
     * {@code Map<String, Object>} and stores it in {@code ctx.parsed()}.
     * Metadata fields, failure logging, typed-proto inputs, and {@code abortOnFailure}
     * are handled the same way as {@link #json(ExecutionContext, boolean)}.
     */
    public void yaml(final ExecutionContext ctx, final boolean abortOnFailure) {
        if (ctx.shouldAbort()) {
            return;
        }
        final Object rawInput = ctx.input();
        if (!(rawInput instanceof LogData.Builder)) {
            abortOrContinueUnparsed(ctx, abortOnFailure, notLogBodyReason("yaml", rawInput));
            return;
        }
        try {
            final LogData.Builder logData = (LogData.Builder) rawInput;
            final Map<String, Object> parsed = yamlParser.create().load(
                logData.getBody().getYaml().getYaml()
            );
            addMetadataFields(parsed, ctx.metadata());
            ctx.parsed(parsed);
        } catch (final Exception e) {
            warnParseFailure(yamlWarnLimiter, "yaml", ctx, abortOnFailure, e);
            abortOrContinueUnparsed(ctx, abortOnFailure, "yaml parse failed: " + e);
        }
    }

    /**
     * Failed-parse epilogue: when the rule aborts, record the drop reason (surfaced in
     * live-debug via {@code Sample.reason}) and abort; otherwise install a metadata-only
     * parsed map so downstream {@code parsed.*} reads stay null-safe on the continuation
     * path — without it the generated extractor would NPE on the null map and drop the log
     * despite {@code abortOnFailure false}. The reason is set ONLY on the aborting path:
     * a continued log is not dropped, and leaving a stale reason on the context would let a
     * later {@code abort {}} statement wrongly inherit this step's parse-failure text.
     */
    private void abortOrContinueUnparsed(final ExecutionContext ctx, final boolean abortOnFailure,
                                         final String reason) {
        if (abortOnFailure) {
            ctx.dropReason(reason);
            ctx.abort();
            return;
        }
        final Map<String, Object> parsed = new HashMap<>();
        addMetadataFields(parsed, ctx.metadata());
        ctx.parsed(parsed);
    }

    private static String notLogBodyReason(final String parser, final Object rawInput) {
        final String actual = rawInput == null ? "null" : rawInput.getClass().getSimpleName();
        return parser + " parser: input is not a log body (expected LogData, got " + actual + ")";
    }

    /**
     * Parse-failure logging shared by {@code json {}} / {@code yaml {}}: WARN when the
     * failure aborts the log (rate-limited, with the suppressed count reported on the
     * next emission), DEBUG when {@code abortOnFailure false} makes the miss expected
     * control flow.
     */
    private static void warnParseFailure(final ParseFailureWarnLimiter limiter,
                                         final String parser,
                                         final ExecutionContext ctx,
                                         final boolean abortOnFailure,
                                         final Exception e) {
        if (abortOnFailure) {
            final long suppressed = limiter.acquire();
            if (suppressed >= 0) {
                LOGGER.warn("LAL {} parser failed to parse the log body (service={}): {}"
                        + " ({} similar failures suppressed since the last report)",
                    parser, ctx.metadata().getService(), e.toString(), suppressed);
            }
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("LAL {} parser failed to parse the log body (service={}, abortOnFailure=false)",
                parser, ctx.metadata().getService(), e);
        }
    }

    /**
     * LAL {@code sink {}} — persists the log via sink listeners if the log
     * was not dropped or aborted.
     */
    public void sink(final ExecutionContext ctx) {
        if (ctx.shouldAbort()) {
            return;
        }
        doSink(ctx);
    }

    private void doSink(final ExecutionContext ctx) {
        if (ctx.isDryRun()) {
            return;
        }

        final LogMetadata metadata = ctx.metadata();
        final Object input = ctx.input();

        if (!ctx.shouldSave()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Log is dropped: service={}, layer={}",
                    metadata.getService(), metadata.getLayer());
            }
            return;
        }

        // In auto-layer mode, the script must set the layer. If not set, drop the log.
        if (ctx.isAutoLayerMode()) {
            final String layer = metadata.getLayer();
            if (layer == null || layer.isEmpty()) {
                LOGGER.warn("Auto-layer LAL rule did not set layer for service={}, dropping log",
                    metadata.getService());
                ctx.drop();
                return;
            }
        }

        sinkListenerFactories.stream()
                 .map(LogSinkListenerFactory::create)
                 .forEach(it -> it.parse(metadata, input, ctx).build());
    }

    // ==================== Direct-access APIs for flattened generated code ====================

    public MetricExtractor extractor() {
        return extractor;
    }

    public SamplerSpec sampler() {
        return sink.sampler();
    }

    public void abort(final ExecutionContext ctx) {
        ctx.abort();
    }

    public void enforcer(final ExecutionContext ctx) {
        sink.enforcer(ctx);
    }

    public void dropper(final ExecutionContext ctx) {
        sink.dropper(ctx);
    }

    public void finalizeSink(final ExecutionContext ctx) {
        if (ctx.shouldAbort()) {
            return;
        }
        doSink(ctx);
    }

    /**
     * Add metadata fields to the parsed map so that {@code parsed.service},
     * {@code parsed.serviceInstance}, etc. resolve correctly — matching v1 Groovy
     * {@code Binding.Parsed.getAt(key)} fallback behavior.
     * Uses {@code putIfAbsent} so body-parsed values take priority.
     */
    private static void addMetadataFields(final Map<String, Object> parsed,
                                          final LogMetadata metadata) {
        if (metadata == null) {
            return;
        }
        putIfNotEmpty(parsed, "service", metadata.getService());
        putIfNotEmpty(parsed, "serviceInstance", metadata.getServiceInstance());
        putIfNotEmpty(parsed, "endpoint", metadata.getEndpoint());
        putIfNotEmpty(parsed, "layer", metadata.getLayer());
        final long ts = metadata.getTimestamp();
        if (ts > 0) {
            parsed.putIfAbsent("timestamp", ts);
        }
    }

    private static void putIfNotEmpty(final Map<String, Object> parsed,
                                      final String key, final String value) {
        if (value != null && !value.isEmpty()) {
            parsed.putIfAbsent(key, value);
        }
    }
}
