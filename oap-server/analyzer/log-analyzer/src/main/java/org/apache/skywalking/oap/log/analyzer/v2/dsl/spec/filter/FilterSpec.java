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
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.Binding;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.AbstractSpec;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor.ExtractorSpec;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.parser.JsonParserSpec;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.parser.TextParserSpec;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.parser.YamlParserSpec;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.sink.SinkSpec;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogSinkListenerFactory;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.RecordSinkListener;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.TrafficSinkListener;
import org.apache.skywalking.oap.server.core.source.Log;

import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The top-level runtime API that compiled LAL expressions invoke.
 *
 * <p>A compiled {@link org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression}
 * calls methods on this class in the order defined by the LAL script:
 * <ol>
 *   <li><b>Parser</b>: {@code json()}, {@code text()}, or {@code yaml()} — parses the log body
 *       into structured data stored in {@link Binding#parsed()}.</li>
 *   <li><b>Extractor</b>: {@code extractor(Consumer)} — extracts service name, layer, tags,
 *       metrics, slow SQL, sampled traces, etc.</li>
 *   <li><b>Sink</b>: {@code sink()} or {@code sink(Consumer)} — materializes the log into
 *       storage via {@link LogSinkListenerFactory} instances (RecordSinkListener,
 *       TrafficSinkListener), unless the log has been dropped or aborted.</li>
 * </ol>
 *
 * <p>All methods read the current log data from the ThreadLocal {@code BINDING}
 * (inherited from {@link AbstractSpec}), which is set by
 * {@link org.apache.skywalking.oap.log.analyzer.v2.dsl.DSL#bind(Binding)} before each execution.
 * Every method checks {@code shouldAbort()} first and short-circuits if a previous
 * step aborted the pipeline.
 */
public class FilterSpec extends AbstractSpec {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilterSpec.class);

    private final List<LogSinkListenerFactory> sinkListenerFactories;

    private final TextParserSpec textParser;

    private final JsonParserSpec jsonParser;

    private final YamlParserSpec yamlParser;

    private final ExtractorSpec extractor;

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

        extractor = new ExtractorSpec(moduleManager(), moduleConfig());

        sink = new SinkSpec(moduleManager(), moduleConfig());
    }

    public void text(final Consumer<TextParserSpec> consumer) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        consumer.accept(textParser);
    }

    public void text() {
        if (BINDING.get().shouldAbort()) {
            return;
        }
    }

    public void json(final Consumer<JsonParserSpec> consumer) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        consumer.accept(jsonParser);
        doJson();
    }

    public void json() {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        doJson();
    }

    private void doJson() {
        final LogData.Builder logData = BINDING.get().log();
        try {
            final Map<String, Object> parsed = jsonParser.create().readValue(
                logData.getBody().getJson().getJson(), parsedType
            );
            BINDING.get().parsed(parsed);
        } catch (final Exception e) {
            if (jsonParser.abortOnFailure()) {
                BINDING.get().abort();
            }
        }
    }

    public void yaml(final Consumer<YamlParserSpec> consumer) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        consumer.accept(yamlParser);
        doYaml();
    }

    public void yaml() {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        doYaml();
    }

    private void doYaml() {
        final LogData.Builder logData = BINDING.get().log();
        try {
            final Map<String, Object> parsed = yamlParser.create().load(
                logData.getBody().getYaml().getYaml()
            );
            BINDING.get().parsed(parsed);
        } catch (final Exception e) {
            if (yamlParser.abortOnFailure()) {
                BINDING.get().abort();
            }
        }
    }

    public void extractor(final Consumer<ExtractorSpec> consumer) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        consumer.accept(extractor);
    }

    public void sink(final Consumer<SinkSpec> consumer) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        consumer.accept(sink);
        doSink();
    }

    public void sink() {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        doSink();
    }

    private void doSink() {
        final Binding b = BINDING.get();
        final LogData.Builder logData = b.log();
        final Message extraLog = b.extraLog();

        if (!b.shouldSave()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Log is dropped: {}", TextFormat.shortDebugString(logData));
            }
            return;
        }

        final Optional<AtomicReference<Log>> container = BINDING.get().logContainer();
        if (container.isPresent()) {
            sinkListenerFactories.stream()
                     .map(LogSinkListenerFactory::create)
                     .filter(it -> it instanceof RecordSinkListener)
                     .map(it -> it.parse(logData, extraLog))
                     .map(it -> (RecordSinkListener) it)
                     .map(RecordSinkListener::getLog)
                     .findFirst()
                     .ifPresent(log -> container.get().set(log));
        } else {
            sinkListenerFactories.stream()
                     .map(LogSinkListenerFactory::create)
                     .forEach(it -> it.parse(logData, extraLog).build());
        }
    }

    public void abort() {
        BINDING.get().abort();
    }
}
