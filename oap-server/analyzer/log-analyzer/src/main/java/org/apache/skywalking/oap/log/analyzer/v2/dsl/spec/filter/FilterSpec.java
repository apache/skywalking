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
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.AbstractSpec;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor.MetricExtractor;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.parser.JsonParserSpec;
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
     * and accessed via {@code parsed.groupName} in the LAL script.
     */
    public void textWithRegexp(final ExecutionContext ctx, final String regexp) {
        if (ctx.shouldAbort()) {
            return;
        }
        textParser.regexp(ctx, regexp);
    }

    /**
     * LAL {@code json {}} — parses {@code LogData.body.json.json} into a
     * {@code Map<String, Object>} and stores it in {@code ctx.parsed()}.
     * Metadata fields (service, serviceInstance, endpoint, layer, timestamp)
     * are also added to the map via {@code putIfAbsent}, so body values take
     * priority while metadata fields serve as fallback — matching v1 Groovy
     * {@code Binding.Parsed.getAt(key)} behavior.
     */
    public void json(final ExecutionContext ctx) {
        if (ctx.shouldAbort()) {
            return;
        }
        try {
            final LogData.Builder logData = (LogData.Builder) ctx.input();
            final Map<String, Object> parsed = jsonParser.create().readValue(
                logData.getBody().getJson().getJson(), parsedType
            );
            addMetadataFields(parsed, ctx.metadata());
            ctx.parsed(parsed);
        } catch (final Exception e) {
            if (jsonParser.abortOnFailure()) {
                ctx.abort();
            }
        }
    }

    /**
     * LAL {@code yaml {}} — parses {@code LogData.body.yaml.yaml} into a
     * {@code Map<String, Object>} and stores it in {@code ctx.parsed()}.
     * Metadata fields are added the same way as {@link #json(ExecutionContext)}.
     */
    public void yaml(final ExecutionContext ctx) {
        if (ctx.shouldAbort()) {
            return;
        }
        try {
            final LogData.Builder logData = (LogData.Builder) ctx.input();
            final Map<String, Object> parsed = yamlParser.create().load(
                logData.getBody().getYaml().getYaml()
            );
            addMetadataFields(parsed, ctx.metadata());
            ctx.parsed(parsed);
        } catch (final Exception e) {
            if (yamlParser.abortOnFailure()) {
                ctx.abort();
            }
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
