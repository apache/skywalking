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

package org.apache.skywalking.oap.log.analyzer.dsl.spec.filter;

import com.google.gson.reflect.TypeToken;
import com.google.protobuf.TextFormat;
import groovy.lang.Closure;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.dsl.Binding;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.AbstractSpec;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.extractor.ExtractorSpec;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.parser.JsonParserSpec;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.parser.TextParserSpec;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.parser.YamlParserSpec;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.sink.SinkSpec;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.provider.log.listener.LogAnalysisListenerFactory;
import org.apache.skywalking.oap.log.analyzer.provider.log.listener.RecordAnalysisListener;
import org.apache.skywalking.oap.log.analyzer.provider.log.listener.TrafficAnalysisListener;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilterSpec extends AbstractSpec {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilterSpec.class);

    private final List<LogAnalysisListenerFactory> factories;

    private final TextParserSpec textParser;

    private final JsonParserSpec jsonParser;

    private final YamlParserSpec yamlParser;

    private final ExtractorSpec extractor;

    private final SinkSpec sink;

    private final Type parsedType;

    public FilterSpec(final ModuleManager moduleManager,
                      final LogAnalyzerModuleConfig moduleConfig) throws ModuleStartException {
        super(moduleManager, moduleConfig);

        parsedType = new TypeToken<Map<String, Object>>() {
        }.getType();

        factories = Arrays.asList(
            new RecordAnalysisListener.Factory(moduleManager(), moduleConfig()),
            new TrafficAnalysisListener.Factory(moduleManager(), moduleConfig())
        );

        textParser = new TextParserSpec(moduleManager(), moduleConfig());
        jsonParser = new JsonParserSpec(moduleManager(), moduleConfig());
        yamlParser = new YamlParserSpec(moduleManager(), moduleConfig());

        extractor = new ExtractorSpec(moduleManager(), moduleConfig());

        sink = new SinkSpec(moduleManager(), moduleConfig());
    }

    @SuppressWarnings("unused")
    public void text(final Closure<Void> cl) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        cl.setDelegate(textParser);
        cl.call();
    }

    @SuppressWarnings("unused")
    public void json(final Closure<Void> cl) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        cl.setDelegate(jsonParser);
        cl.call();

        final LogData.Builder logData = BINDING.get().log();
        final Map<String, Object> parsed = jsonParser.create().fromJson(
            logData.getBody().getJson().getJson(), parsedType
        );

        BINDING.get().parsed(parsed);
    }

    @SuppressWarnings({"unused", "unchecked"})
    public void yaml(final Closure<Void> cl) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        cl.setDelegate(yamlParser);
        cl.call();

        final LogData.Builder logData = BINDING.get().log();
        final Map<String, Object> parsed = (Map<String, Object>) yamlParser.create().load(
            logData.getBody().getYaml().getYaml()
        );

        BINDING.get().parsed(parsed);
    }

    @SuppressWarnings("unused")
    public void extractor(final Closure<Void> cl) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        cl.setDelegate(extractor);
        cl.call();
    }

    @SuppressWarnings("unused")
    public void sink(final Closure<Void> cl) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        cl.setDelegate(sink);
        cl.call();

        final Binding b = BINDING.get();
        final LogData.Builder logData = b.log();

        if (!b.shouldSave()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Log is dropped: {}", TextFormat.shortDebugString(logData));
            }
            return;
        }

        factories.stream()
                 .map(LogAnalysisListenerFactory::create)
                 .forEach(it -> it.parse(logData).build());
    }

    @SuppressWarnings("unused")
    public void filter(final Closure<Void> cl) {
        cl.call();
    }
}
