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

package org.apache.skywalking.oap.log.analyzer.v2.compiler;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.JSONLog;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TextLog;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec;
import org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.library.kubernetes.ObjectID;
import org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Data-driven runtime execution tests for compiled LAL expressions.
 *
 * <p>Loads LAL rules from {@code .yaml} files and mock input from
 * corresponding {@code .input.data} files in the {@code test-lal/}
 * directory tree. For each rule that has a matching input entry,
 * compiles the DSL via {@link LALClassGenerator}, executes it against
 * a real {@link FilterSpec} + {@link ExecutionContext}, and asserts on the
 * expected state defined in the {@code expect} block.
 */
class LALExpressionExecutionTest {

    private static MockedStatic<K8sInfoRegistry> K8S_MOCK;
    private static MockedStatic<MetricsStreamProcessor> MSP_MOCK;

    @BeforeAll
    static void setupMocks() {
        // Mock K8sInfoRegistry for ProcessRegistry.generateVirtualRemoteProcess()
        final K8sInfoRegistry mockK8s = mock(K8sInfoRegistry.class);
        when(mockK8s.findPodByIP(anyString())).thenReturn(ObjectID.EMPTY);
        when(mockK8s.findServiceByIP(anyString())).thenReturn(ObjectID.EMPTY);
        K8S_MOCK = Mockito.mockStatic(K8sInfoRegistry.class);
        K8S_MOCK.when(K8sInfoRegistry::getInstance).thenReturn(mockK8s);

        // Mock MetricsStreamProcessor for ProcessRegistry.generateVirtualProcess()
        final MetricsStreamProcessor mockMsp = mock(MetricsStreamProcessor.class);
        doNothing().when(mockMsp).in(any());
        MSP_MOCK = Mockito.mockStatic(MetricsStreamProcessor.class);
        MSP_MOCK.when(MetricsStreamProcessor::getInstance).thenReturn(mockMsp);
    }

    @AfterAll
    static void teardownMocks() {
        if (K8S_MOCK != null) {
            K8S_MOCK.close();
        }
        if (MSP_MOCK != null) {
            MSP_MOCK.close();
        }
    }

    @TestFactory
    Collection<DynamicTest> lalExecutionTests() throws Exception {
        final List<DynamicTest> tests = new ArrayList<>();
        final FilterSpec filterSpec = buildFilterSpec();
        final LALClassGenerator generator = new LALClassGenerator();
        final Yaml yaml = new Yaml();

        final Path testLalDir = findTestLalDir();
        if (testLalDir == null) {
            return tests;
        }

        // Scan subdirectories (oap-cases/, feature-cases/)
        final File[] subdirs = testLalDir.toFile().listFiles(File::isDirectory);
        if (subdirs == null) {
            return tests;
        }

        for (final File subdir : subdirs) {
            final File[] files = subdir.listFiles();
            if (files == null) {
                continue;
            }
            for (final File yamlFile : files) {
                if (!yamlFile.getName().endsWith(".yaml")
                        && !yamlFile.getName().endsWith(".yml")) {
                    continue;
                }

                // Look for matching .input.data file
                final String baseName = yamlFile.getName()
                    .replaceAll("\\.(yaml|yml)$", "");
                final File inputDataFile = new File(subdir,
                    baseName + ".input.data");
                if (!inputDataFile.exists()) {
                    continue;
                }

                // Parse the YAML rules
                final String yamlContent =
                    Files.readString(yamlFile.toPath());
                final Map<String, Object> config = yaml.load(yamlContent);
                if (config == null || !config.containsKey("rules")) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                final List<Map<String, String>> rules =
                    (List<Map<String, String>>) config.get("rules");
                if (rules == null) {
                    continue;
                }

                // Parse the input data
                final String inputContent =
                    Files.readString(inputDataFile.toPath());
                @SuppressWarnings("unchecked")
                final Map<String, Object> inputData =
                    yaml.load(inputContent);
                if (inputData == null) {
                    continue;
                }

                final String category = subdir.getName();
                for (final Map<String, String> rule : rules) {
                    final String ruleName = rule.get("name");
                    final String dsl = rule.get("dsl");
                    final String ruleLayer = rule.get("layer");
                    final String inputType = rule.get("inputType");
                    if (ruleName == null || dsl == null) {
                        continue;
                    }
                    final Object ruleInput = inputData.get(ruleName);
                    if (ruleInput == null) {
                        continue;
                    }

                    if (ruleInput instanceof List) {
                        @SuppressWarnings("unchecked")
                        final List<Map<String, Object>> inputs =
                            (List<Map<String, Object>>) ruleInput;
                        for (int i = 0; i < inputs.size(); i++) {
                            final Map<String, Object> input = inputs.get(i);
                            final int idx = i;
                            tests.add(DynamicTest.dynamicTest(
                                category + "/" + baseName + " | "
                                    + ruleName + " [" + idx + "]",
                                () -> executeAndAssert(
                                    generator, filterSpec,
                                    ruleName + " [" + idx + "]",
                                    dsl, ruleLayer, inputType, input)
                            ));
                        }
                    } else {
                        @SuppressWarnings("unchecked")
                        final Map<String, Object> input =
                            (Map<String, Object>) ruleInput;
                        tests.add(DynamicTest.dynamicTest(
                            category + "/" + baseName + " | " + ruleName,
                            () -> executeAndAssert(
                                generator, filterSpec, ruleName,
                                dsl, ruleLayer, inputType, input)
                        ));
                    }
                }
            }
        }
        return tests;
    }

    private void executeAndAssert(
            final LALClassGenerator generator,
            final FilterSpec filterSpec,
            final String ruleName,
            final String dsl,
            final String ruleLayer,
            final String inputType,
            final Map<String, Object> input) throws Exception {
        if (inputType != null) {
            generator.setInputType(Class.forName(inputType));
        } else if (ruleLayer != null) {
            // Resolve via LALSourceTypeProvider SPI
            generator.setInputType(spiInputTypes().get(ruleLayer));
        } else {
            generator.setInputType(null);
        }
        final LalExpression expr = generator.compile(dsl);
        final LogData.Builder logData = buildLogData(input);
        if (ruleLayer != null) {
            logData.setLayer(ruleLayer);
        }
        final ExecutionContext ctx = new ExecutionContext();
        ctx.log(logData);

        // Set proto extraLog if specified
        final Message extraLog = buildExtraLog(input);
        if (extraLog != null) {
            ctx.extraLog(extraLog);
        }

        expr.execute(filterSpec, ctx);

        // Assert expected values
        @SuppressWarnings("unchecked")
        final Map<String, Object> expect =
            (Map<String, Object>) input.get("expect");
        if (expect == null) {
            return;
        }

        for (final Map.Entry<String, Object> entry : expect.entrySet()) {
            final String key = entry.getKey();
            final String expected = String.valueOf(entry.getValue());

            switch (key) {
                case "service":
                    assertEquals(expected, ctx.log().getService(),
                        ruleName + ": service mismatch");
                    break;
                case "instance":
                    assertEquals(expected,
                        ctx.log().getServiceInstance(),
                        ruleName + ": serviceInstance mismatch");
                    break;
                case "endpoint":
                    assertEquals(expected, ctx.log().getEndpoint(),
                        ruleName + ": endpoint mismatch");
                    break;
                case "layer":
                    assertEquals(expected, ctx.log().getLayer(),
                        ruleName + ": layer mismatch");
                    break;
                case "save":
                    assertEquals(Boolean.parseBoolean(expected),
                        ctx.shouldSave(),
                        ruleName + ": shouldSave mismatch");
                    break;
                case "abort":
                    assertEquals(Boolean.parseBoolean(expected),
                        ctx.shouldAbort(),
                        ruleName + ": shouldAbort mismatch");
                    break;
                case "timestamp":
                    assertEquals(Long.parseLong(expected),
                        ctx.log().getTimestamp(),
                        ruleName + ": timestamp mismatch");
                    break;
                default:
                    if (key.startsWith("tag.")) {
                        final String tagKey = key.substring(4);
                        final List<KeyStringValuePair> tags =
                            ctx.log().getTags().getDataList();
                        assertTrue(tags.stream().anyMatch(
                            t -> tagKey.equals(t.getKey())
                                && expected.equals(t.getValue())),
                            ruleName + ": expected tag "
                                + tagKey + "=" + expected
                                + ", got: " + tags.stream()
                                .map(t -> t.getKey() + "=" + t.getValue())
                                .collect(Collectors.joining(", ")));
                    }
                    break;
            }
        }
    }

    // ==================== LogData builder ====================

    @SuppressWarnings("unchecked")
    private static LogData.Builder buildLogData(
            final Map<String, Object> input) {
        final LogData.Builder builder = LogData.newBuilder();

        final String service = (String) input.get("service");
        if (service != null) {
            builder.setService(service);
        }

        final String instance = (String) input.get("instance");
        if (instance != null) {
            builder.setServiceInstance(instance);
        }

        final String traceId = (String) input.get("trace-id");
        if (traceId != null) {
            builder.setTraceContext(
                org.apache.skywalking.apm.network.logging.v3.TraceContext
                    .newBuilder().setTraceId(traceId));
        }

        final Object tsObj = input.get("timestamp");
        if (tsObj != null) {
            builder.setTimestamp(Long.parseLong(String.valueOf(tsObj)));
        }

        final String bodyType = (String) input.get("body-type");
        final String body = (String) input.get("body");

        if ("json".equals(bodyType) && body != null) {
            builder.setBody(LogDataBody.newBuilder()
                .setJson(JSONLog.newBuilder().setJson(body)));
        } else if ("text".equals(bodyType) && body != null) {
            builder.setBody(LogDataBody.newBuilder()
                .setText(TextLog.newBuilder().setText(body)));
        }

        final Map<String, String> tags =
            (Map<String, String>) input.get("tags");
        if (tags != null && !tags.isEmpty()) {
            final LogTags.Builder tagsBuilder = LogTags.newBuilder();
            for (final Map.Entry<String, String> tag : tags.entrySet()) {
                tagsBuilder.addData(KeyStringValuePair.newBuilder()
                    .setKey(tag.getKey())
                    .setValue(tag.getValue()));
            }
            builder.setTags(tagsBuilder);
        }

        return builder;
    }

    // ==================== Proto extraLog builder ====================

    @SuppressWarnings("unchecked")
    private static Message buildExtraLog(
            final Map<String, Object> input) throws Exception {
        final Map<String, String> extraLog =
            (Map<String, String>) input.get("extra-log");
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
        JsonFormat.parser()
            .ignoringUnknownFields()
            .merge(protoJson, builder);
        return builder.build();
    }

    // ==================== SPI lookup ====================

    private Map<String, Class<?>> spiTypes;

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

    // ==================== FilterSpec setup ====================

    private FilterSpec buildFilterSpec() throws Exception {
        final ModuleManager manager = mock(ModuleManager.class);
        setInternalField(manager, "isInPrepareStage", false);

        when(manager.find(anyString()))
            .thenReturn(mock(ModuleProviderHolder.class));

        final ModuleProviderHolder logHolder =
            mock(ModuleProviderHolder.class);
        final LogAnalyzerModuleProvider logProvider =
            mock(LogAnalyzerModuleProvider.class);
        when(logProvider.getMetricConverts())
            .thenReturn(Collections.emptyList());
        when(logHolder.provider()).thenReturn(logProvider);
        when(manager.find(LogAnalyzerModule.NAME)).thenReturn(logHolder);

        final ModuleProviderHolder coreHolder =
            mock(ModuleProviderHolder.class);
        final ModuleServiceHolder coreServices =
            mock(ModuleServiceHolder.class);
        when(coreHolder.provider()).thenReturn(coreServices);
        when(manager.find(CoreModule.NAME)).thenReturn(coreHolder);

        when(coreServices.getService(SourceReceiver.class))
            .thenReturn(mock(SourceReceiver.class));
        when(coreServices.getService(NamingControl.class))
            .thenReturn(new NamingControl(
                200, 200, 200, new EndpointNameGrouping()));
        final ConfigService configService = mock(ConfigService.class);
        when(configService.getSearchableLogsTags()).thenReturn("");
        when(coreServices.getService(ConfigService.class))
            .thenReturn(configService);

        final FilterSpec filterSpec =
            new FilterSpec(manager, new LogAnalyzerModuleConfig());
        setInternalField(filterSpec, "sinkListenerFactories",
            Collections.emptyList());

        return filterSpec;
    }

    // ==================== Directory resolution ====================

    private Path findTestLalDir() {
        final String[] candidates = {
            // From repo root (e.g., running with -pl from top level)
            "test/script-cases/scripts/lal/test-lal",
            // From oap-server/analyzer/log-analyzer/ module directory
            "../../../test/script-cases/scripts/lal/test-lal",
            // From script-runtime-with-groovy checker location
            "../../scripts/lal/test-lal"
        };
        for (final String candidate : candidates) {
            final Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return null;
    }

    // ==================== Reflection helpers ====================

    private static void setInternalField(final Object target,
                                         final String fieldName,
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
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to set field " + fieldName, e);
        }
    }
}
