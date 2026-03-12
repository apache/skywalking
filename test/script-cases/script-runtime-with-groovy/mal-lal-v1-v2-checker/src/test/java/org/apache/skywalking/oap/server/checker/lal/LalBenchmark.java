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

package org.apache.skywalking.oap.server.checker.lal;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.JSONLog;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TextLog;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALClassGenerator;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.yaml.snakeyaml.Yaml;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JMH benchmark comparing LAL v1 (Groovy) vs v2 (ANTLR4 + Javassist)
 * compilation and execution performance using envoy-als.yaml (2 rules).
 *
 * <p>Run: mvn test -pl test/script-cases/script-runtime-with-groovy/mal-lal-v1-v2-checker
 *     -Dtest=LalBenchmark#runBenchmark -DfailIfNoTests=false
 *
 * <h2>Reference results (Apple M3 Max, 128 GB RAM, macOS 26.2, JDK 25)</h2>
 * <pre>
 * Benchmark               Mode  Cnt      Score      Error  Units
 * LalBenchmark.compileV1  avgt    5  34534.987 ± 3811.245  us/op
 * LalBenchmark.compileV2  avgt    5    881.997 ±  102.587  us/op
 * LalBenchmark.executeV1  avgt    5     36.683 ±    5.223  us/op
 * LalBenchmark.executeV2  avgt    5     12.909 ±    2.378  us/op
 * </pre>
 *
 * <p>Compile speedup: v2 is ~39x faster than v1 (Groovy script compilation is expensive).
 * Execute speedup: v2 is ~2.8x faster than v1.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 5)
@Fork(1)
public class LalBenchmark {

    private List<RuleEntry> rules;

    // Pre-compiled expressions for execute benchmarks
    private List<org.apache.skywalking.oap.log.analyzer.dsl.DSL> v1Dsls;
    private List<org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression> v2Exprs;

    // Module managers for v1/v2
    private ModuleManager v1Manager;
    private ModuleManager v2Manager;

    // Pre-created FilterSpec for v2 execute benchmark (reusable)
    private org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec v2FilterSpec;

    // Test log data per rule
    private List<LogData> testLogs;
    private List<Message> extraLogs;

    // SPI lookup cache
    private Map<String, Class<?>> spiTypes;

    @Setup(Level.Trial)
    @SuppressWarnings("unchecked")
    public void setup() throws Exception {
        // Load envoy-als.yaml
        final Path lalYaml = findScript("lal", "test-lal/oap-cases/envoy-als.yaml");
        final Yaml yaml = new Yaml();
        final Map<String, Object> config = yaml.load(Files.readString(lalYaml));
        final List<Map<String, String>> ruleConfigs =
            (List<Map<String, String>>) config.get("rules");

        // Load envoy-als.data.yaml
        final Path inputDataPath = lalYaml.getParent().resolve("envoy-als.data.yaml");
        Map<String, Map<String, Object>> inputData = null;
        if (Files.isRegularFile(inputDataPath)) {
            inputData = yaml.load(Files.readString(inputDataPath));
        }

        // Parse rules
        rules = new ArrayList<>();
        testLogs = new ArrayList<>();
        extraLogs = new ArrayList<>();
        for (final Map<String, String> rule : ruleConfigs) {
            final String name = rule.get("name");
            final String dsl = rule.get("dsl");
            final String layer = rule.get("layer");
            if (name == null || dsl == null) {
                continue;
            }
            final Map<String, Object> ruleInput =
                inputData != null ? inputData.get(name) : null;

            // Resolve inputType via SPI
            Class<?> inputType = null;
            if (layer != null) {
                inputType = spiInputTypes().get(layer);
            }

            rules.add(new RuleEntry(name, dsl, layer, inputType));
            testLogs.add(buildLogData(ruleInput, dsl));
            extraLogs.add(buildExtraLog(ruleInput));
        }

        // Set up module managers
        v1Manager = buildMockModuleManager(true);
        v2Manager = buildMockModuleManager(false);

        // Pre-create v2 FilterSpec (reusable across iterations)
        v2FilterSpec =
            new org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec(
                v2Manager, new LogAnalyzerModuleConfig());
        disableSinkListenersOnSpec(v2FilterSpec);

        // Pre-compile for execute benchmarks
        v1Dsls = new ArrayList<>();
        v2Exprs = new ArrayList<>();
        for (final RuleEntry rule : rules) {
            final org.apache.skywalking.oap.log.analyzer.dsl.DSL v1Dsl =
                org.apache.skywalking.oap.log.analyzer.dsl.DSL.of(
                    v1Manager,
                    new org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig(),
                    rule.dsl);
            disableSinkListeners(v1Dsl);
            v1Dsls.add(v1Dsl);

            final LALClassGenerator gen = new LALClassGenerator();
            if (rule.inputType != null) {
                gen.setInputType(rule.inputType);
            }
            v2Exprs.add(gen.compile(rule.dsl));
        }
    }

    @Benchmark
    public void compileV1(final Blackhole bh) {
        for (final RuleEntry rule : rules) {
            try {
                final org.apache.skywalking.oap.log.analyzer.dsl.DSL dsl =
                    org.apache.skywalking.oap.log.analyzer.dsl.DSL.of(
                        v1Manager,
                        new org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig(),
                        rule.dsl);
                bh.consume(dsl);
            } catch (Exception ignored) {
            }
        }
    }

    @Benchmark
    public void compileV2(final Blackhole bh) {
        for (final RuleEntry rule : rules) {
            try {
                final LALClassGenerator gen = new LALClassGenerator();
                if (rule.inputType != null) {
                    gen.setInputType(rule.inputType);
                }
                bh.consume(gen.compile(rule.dsl));
            } catch (Exception ignored) {
            }
        }
    }

    @Benchmark
    public void executeV1(final Blackhole bh) {
        for (int i = 0; i < v1Dsls.size(); i++) {
            try {
                final org.apache.skywalking.oap.log.analyzer.dsl.Binding ctx =
                    new org.apache.skywalking.oap.log.analyzer.dsl.Binding()
                        .log(testLogs.get(i));
                if (extraLogs.get(i) != null) {
                    ctx.extraLog(extraLogs.get(i));
                }
                v1Dsls.get(i).bind(ctx);
                v1Dsls.get(i).evaluate();
                bh.consume(ctx);
            } catch (Exception ignored) {
            }
        }
    }

    @Benchmark
    public void executeV2(final Blackhole bh) {
        for (int i = 0; i < v2Exprs.size(); i++) {
            try {
                final LogData logData = testLogs.get(i);
                final Message extraLog = extraLogs.get(i);
                final org.apache.skywalking.oap.server.core.source.LogMetadata metadata =
                    org.apache.skywalking.oap.server.core.source.LogMetadataUtils.fromLogData(logData);
                final Object input = extraLog != null ? extraLog : logData;
                final org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext ctx =
                    new org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext();
                ctx.init(metadata, input);
                v2Exprs.get(i).execute(v2FilterSpec, ctx);
                bh.consume(ctx);
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== Mock setup ====================

    private ModuleManager buildMockModuleManager(final boolean isV1) {
        final ModuleManager manager = mock(ModuleManager.class);
        setInternalField(manager, "isInPrepareStage", false);
        when(manager.find(anyString())).thenReturn(mock(ModuleProviderHolder.class));

        final ModuleProviderHolder logAnalyzerHolder = mock(ModuleProviderHolder.class);
        if (isV1) {
            final org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleProvider
                provider = mock(
                    org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleProvider.class);
            when(provider.getMetricConverts()).thenReturn(Collections.emptyList());
            when(logAnalyzerHolder.provider()).thenReturn(provider);
        } else {
            final org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider
                provider = mock(
                    org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider.class);
            when(provider.getMetricConverts()).thenReturn(Collections.emptyList());
            when(logAnalyzerHolder.provider()).thenReturn(provider);
        }
        when(manager.find(LogAnalyzerModule.NAME)).thenReturn(logAnalyzerHolder);

        when(manager.find(CoreModule.NAME).provider()).thenReturn(mock(ModuleServiceHolder.class));
        when(manager.find(CoreModule.NAME).provider().getService(SourceReceiver.class))
            .thenReturn(mock(SourceReceiver.class));
        when(manager.find(CoreModule.NAME).provider().getService(ConfigService.class))
            .thenReturn(mock(ConfigService.class));
        when(manager.find(CoreModule.NAME)
            .provider()
            .getService(ConfigService.class)
            .getSearchableLogsTags())
            .thenReturn("");
        final NamingControl namingControl = mock(NamingControl.class);
        when(namingControl.formatServiceName(anyString()))
            .thenAnswer(inv -> inv.getArgument(0));
        when(namingControl.formatInstanceName(anyString()))
            .thenAnswer(inv -> inv.getArgument(0));
        when(namingControl.formatEndpointName(anyString(), anyString()))
            .thenAnswer(inv -> inv.getArgument(1));
        when(manager.find(CoreModule.NAME).provider().getService(NamingControl.class))
            .thenReturn(namingControl);
        return manager;
    }

    // ==================== Log data builders ====================

    @SuppressWarnings("unchecked")
    private LogData buildLogData(final Map<String, Object> inputData,
                                 final String dsl) {
        if (inputData == null) {
            return buildSyntheticLogData(dsl);
        }

        final LogData.Builder builder = LogData.newBuilder();
        final String service = (String) inputData.get("service");
        if (service != null) {
            builder.setService(service);
        }
        final String instance = (String) inputData.get("instance");
        if (instance != null) {
            builder.setServiceInstance(instance);
        }
        final String traceId = (String) inputData.get("trace-id");
        if (traceId != null) {
            builder.setTraceContext(TraceContext.newBuilder().setTraceId(traceId));
        }
        final Object tsObj = inputData.get("timestamp");
        if (tsObj != null) {
            builder.setTimestamp(Long.parseLong(String.valueOf(tsObj)));
        }

        final String bodyType = (String) inputData.get("body-type");
        final String body = (String) inputData.get("body");
        if ("json".equals(bodyType) && body != null) {
            builder.setBody(LogDataBody.newBuilder()
                .setJson(JSONLog.newBuilder().setJson(body)));
        } else if ("text".equals(bodyType) && body != null) {
            builder.setBody(LogDataBody.newBuilder()
                .setText(TextLog.newBuilder().setText(body)));
        }

        final Map<String, String> tags =
            (Map<String, String>) inputData.get("tags");
        if (tags != null && !tags.isEmpty()) {
            final LogTags.Builder tagsBuilder = LogTags.newBuilder();
            for (final Map.Entry<String, String> tag : tags.entrySet()) {
                tagsBuilder.addData(KeyStringValuePair.newBuilder()
                    .setKey(tag.getKey())
                    .setValue(tag.getValue()));
            }
            builder.setTags(tagsBuilder);
        }
        return builder.build();
    }

    private LogData buildSyntheticLogData(final String dsl) {
        final LogData.Builder builder = LogData.newBuilder()
            .setService("test-service")
            .setServiceInstance("test-instance")
            .setTimestamp(System.currentTimeMillis())
            .setTraceContext(TraceContext.newBuilder()
                .setTraceId("test-trace-id-123")
                .setTraceSegmentId("test-segment-id-456")
                .setSpanId(1));
        if (dsl.contains("json")) {
            builder.setBody(LogDataBody.newBuilder()
                .setJson(JSONLog.newBuilder()
                    .setJson("{\"level\":\"ERROR\",\"msg\":\"test\"}")));
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static Message buildExtraLog(
            final Map<String, Object> inputData) throws Exception {
        if (inputData == null) {
            return null;
        }
        final Map<String, String> extraLog =
            (Map<String, String>) inputData.get("extra-log");
        if (extraLog == null) {
            return null;
        }
        final String protoClass = extraLog.get("proto-class");
        final String protoJson = extraLog.get("proto-json");
        if (protoClass == null || protoJson == null) {
            return null;
        }
        final Class<?> clazz = Class.forName(protoClass);
        final Message.Builder builder = (Message.Builder)
            clazz.getMethod("newBuilder").invoke(null);
        JsonFormat.parser().ignoringUnknownFields().merge(protoJson, builder);
        return builder.build();
    }

    // ==================== SPI lookup ====================

    private Map<String, Class<?>> spiInputTypes() {
        if (spiTypes == null) {
            spiTypes = new HashMap<>();
            for (final LALSourceTypeProvider p :
                    ServiceLoader.load(LALSourceTypeProvider.class)) {
                spiTypes.put(p.layer().name(), p.inputType());
            }
        }
        return spiTypes;
    }

    // ==================== Reflection helpers ====================

    private void disableSinkListeners(final Object dsl) {
        try {
            final Object filterSpec = getInternalField(dsl, "filterSpec");
            setInternalField(filterSpec, "sinkListenerFactories", Collections.emptyList());
        } catch (Exception ignored) {
        }
    }

    private void disableSinkListenersOnSpec(final Object filterSpec) {
        try {
            setInternalField(filterSpec, "sinkListenerFactories", Collections.emptyList());
        } catch (Exception ignored) {
        }
    }

    private static void setInternalField(final Object target, final String fieldName,
                                         final Object value) {
        try {
            Field field = null;
            Class<?> clazz = target.getClass();
            while (clazz != null && field == null) {
                try {
                    field = clazz.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (field != null) {
                field.setAccessible(true);
                field.set(target, value);
            }
        } catch (Exception ignored) {
        }
    }

    private static Object getInternalField(final Object target, final String fieldName) {
        try {
            Field field = null;
            Class<?> clazz = target.getClass();
            while (clazz != null && field == null) {
                try {
                    field = clazz.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (field != null) {
                field.setAccessible(true);
                return field.get(target);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ==================== Utilities ====================

    private static Path findScript(final String language, final String relative) {
        final String[] candidates = {
            "test/script-cases/scripts/" + language + "/" + relative,
            "../../scripts/" + language + "/" + relative
        };
        for (final String candidate : candidates) {
            final Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        throw new IllegalStateException("Cannot find " + relative + " in scripts/" + language);
    }

    private static class RuleEntry {
        final String name;
        final String dsl;
        final String layer;
        final Class<?> inputType;

        RuleEntry(final String name, final String dsl,
                  final String layer, final Class<?> inputType) {
            this.name = name;
            this.dsl = dsl;
            this.layer = layer;
            this.inputType = inputType;
        }
    }

    // ==================== JMH launcher ====================

    @Test
    void runBenchmark() throws Exception {
        final Options opt = new OptionsBuilder()
            .include(getClass().getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
