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

import java.lang.reflect.Field;
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
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec;
import org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.core.source.LogMetadataUtils;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.testing.dsl.DslRuleLoader;
import org.apache.skywalking.oap.server.testing.dsl.lal.LalLogDataBuilder;
import org.apache.skywalking.oap.server.testing.dsl.lal.LalRuleLoader;
import org.apache.skywalking.oap.server.testing.dsl.lal.LalTestRule;
import org.apache.skywalking.library.kubernetes.ObjectID;
import org.apache.skywalking.oap.meter.analyzer.v2.k8s.K8sInfoRegistry;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
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
        final K8sInfoRegistry mockK8s = mock(K8sInfoRegistry.class);
        when(mockK8s.findPodByIP(anyString())).thenReturn(ObjectID.EMPTY);
        when(mockK8s.findServiceByIP(anyString())).thenReturn(ObjectID.EMPTY);
        K8S_MOCK = Mockito.mockStatic(K8sInfoRegistry.class);
        K8S_MOCK.when(K8sInfoRegistry::getInstance).thenReturn(mockK8s);

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

        final Path testLalDir = DslRuleLoader.findScriptsDir(
            "test/script-cases/scripts/lal/test-lal",
            "../../../test/script-cases/scripts/lal/test-lal",
            "../../scripts/lal/test-lal");
        if (testLalDir == null) {
            return tests;
        }

        final Map<String, List<LalTestRule>> allRules =
            LalRuleLoader.loadAllRules(testLalDir);

        for (final Map.Entry<String, List<LalTestRule>> entry : allRules.entrySet()) {
            for (final LalTestRule rule : entry.getValue()) {
                if (rule.getInputs().isEmpty()) {
                    continue;
                }
                for (int i = 0; i < rule.getInputs().size(); i++) {
                    final Map<String, Object> input = rule.getInputs().get(i);
                    final int idx = i;
                    final String testName = rule.getInputs().size() == 1
                        ? rule.getName() : rule.getName() + " [" + idx + "]";
                    tests.add(DynamicTest.dynamicTest(
                        entry.getKey() + " | " + testName,
                        () -> executeAndAssert(
                            generator, filterSpec, testName,
                            rule.getDsl(), rule.getLayer(),
                            rule.getInputType(), rule.getOutputType(), input)
                    ));
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
            final String outputType,
            final Map<String, Object> input) throws Exception {
        if (inputType != null) {
            generator.setInputType(Class.forName(inputType));
        } else if (ruleLayer != null) {
            generator.setInputType(spiInputTypes().get(ruleLayer));
        } else {
            generator.setInputType(null);
        }

        final Class<?> resolvedOutput = resolveOutputType(outputType);
        generator.setOutputType(resolvedOutput);

        final LalExpression expr = generator.compile(dsl);
        final LogData.Builder logData = LalLogDataBuilder.buildLogData(input);
        if (ruleLayer != null) {
            logData.setLayer(ruleLayer);
        }
        final LogMetadata metadata = LogMetadataUtils.fromLogData(logData);

        final Message extraLog = LalLogDataBuilder.buildExtraLog(input);
        final Object lalInput = extraLog != null ? extraLog : logData;

        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(metadata, lalInput);

        expr.execute(filterSpec, ctx);

        final Object outputObj = ctx.output();

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
                case "service":
                    assertOutputField(ruleName, outputObj, "service", expected, ctx.metadata().getService());
                    break;
                case "instance":
                    assertOutputField(ruleName, outputObj, "serviceInstance", expected, ctx.metadata().getServiceInstance());
                    break;
                case "endpoint":
                    assertOutputField(ruleName, outputObj, "endpoint", expected, ctx.metadata().getEndpoint());
                    break;
                case "layer":
                    assertOutputField(ruleName, outputObj, "layer", expected, ctx.metadata().getLayer());
                    break;
                case "timestamp":
                    assertOutputField(ruleName, outputObj, "timestamp", expected, String.valueOf(ctx.metadata().getTimestamp()));
                    break;
                default:
                    if (key.startsWith("tag.")) {
                        final String tagKey = key.substring(4);
                        if (outputObj instanceof org.apache.skywalking.oap.server.core.source.LogBuilder) {
                            assertLalTag(ruleName, outputObj, tagKey, expected);
                        } else if (ctx.input() instanceof LogData.Builder) {
                            final List<KeyStringValuePair> tags =
                                ((LogData.Builder) ctx.input()).getTags().getDataList();
                            assertTrue(tags.stream().anyMatch(
                                t -> tagKey.equals(t.getKey())
                                    && expected.equals(t.getValue())),
                                ruleName + ": expected tag "
                                    + tagKey + "=" + expected
                                    + ", got: " + tags.stream()
                                    .map(t -> t.getKey() + "=" + t.getValue())
                                    .collect(Collectors.joining(", ")));
                        }
                    } else if (key.startsWith("outputField.")) {
                        final String fieldName = key.substring("outputField.".length());
                        assertOutputField(ruleName, outputObj, fieldName, expected, "");
                    }
                    break;
            }
        }
    }

    private static final Map<String, String[]> FIELD_GETTER_CANDIDATES;

    static {
        FIELD_GETTER_CANDIDATES = new HashMap<>();
        FIELD_GETTER_CANDIDATES.put("service",
            new String[]{"getService", "getServiceName"});
        FIELD_GETTER_CANDIDATES.put("serviceInstance",
            new String[]{"getServiceInstance", "getServiceInstanceName", "getInstance"});
    }

    private void assertOutputField(final String ruleName,
                                   final Object output,
                                   final String fieldName,
                                   final String expected,
                                   final String protoValue) {
        if (output == null) {
            assertEquals(expected, protoValue, ruleName + ": " + fieldName + " mismatch (no output)");
            return;
        }

        // Try getter candidates for known fields, then standard getter
        final String[] candidates = FIELD_GETTER_CANDIDATES.get(fieldName);
        if (candidates != null) {
            for (final String getterName : candidates) {
                try {
                    final Object actual = output.getClass().getMethod(getterName).invoke(output);
                    if (actual != null) {
                        assertEquals(expected, String.valueOf(actual),
                            ruleName + ": expect." + fieldName + " mismatch");
                        return;
                    }
                } catch (final NoSuchMethodException ignored) {
                } catch (final Exception e) {
                    fail(ruleName + ": getter " + getterName + " failed: " + e.getMessage());
                    return;
                }
            }
        } else {
            final String getterName = "get"
                + Character.toUpperCase(fieldName.charAt(0))
                + fieldName.substring(1);
            try {
                final Object actual = output.getClass().getMethod(getterName).invoke(output);
                if (actual != null) {
                    assertEquals(expected, String.valueOf(actual),
                        ruleName + ": expect." + fieldName + " mismatch");
                    return;
                }
            } catch (final NoSuchMethodException ignored) {
            } catch (final Exception e) {
                fail(ruleName + ": getter " + getterName + " failed: " + e.getMessage());
                return;
            }
        }

        // Fall back to field access
        Class<?> clazz = output.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                final Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                final Object actual = f.get(output);
                if (actual != null) {
                    assertEquals(expected, String.valueOf(actual),
                        ruleName + ": expect." + fieldName + " mismatch");
                    return;
                }
                break;
            } catch (final NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (final Exception e) {
                fail(ruleName + ": field access " + fieldName + " failed: " + e.getMessage());
                return;
            }
        }

        // Final fallback to proto value if output field is null/missing
        assertEquals(expected, protoValue, ruleName + ": " + fieldName + " mismatch");
    }

    private void assertLalTag(final String ruleName, final Object output,
                               final String key, final String expected) throws Exception {
        final Field f = output.getClass().getDeclaredField("lalTags");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        final List<String[]> tags = (List<String[]>) f.get(output);
        assertTrue(tags.stream().anyMatch(t -> key.equals(t[0]) && expected.equals(t[1])),
            ruleName + ": expected tag " + key + "=" + expected + ", got: "
                + tags.stream().map(t -> t[0] + "=" + t[1]).collect(Collectors.joining(", ")));
    }

    private Map<String, Class<?>> outputBuilderNames;

    private Map<String, Class<?>> outputBuilderNameMap() {
        if (outputBuilderNames == null) {
            outputBuilderNames = new HashMap<>();
            for (final org.apache.skywalking.oap.server.core.source.LALOutputBuilder builder :
                    ServiceLoader.load(org.apache.skywalking.oap.server.core.source.LALOutputBuilder.class)) {
                outputBuilderNames.put(builder.name(), builder.getClass());
            }
        }
        return outputBuilderNames;
    }

    private Class<?> resolveOutputType(final String outputType) throws ClassNotFoundException {
        if (outputType == null) {
            return null;
        }
        if (!outputType.contains(".")) {
            final Class<?> byName = outputBuilderNameMap().get(outputType);
            if (byName != null) {
                return byName;
            }
        }
        return Class.forName(outputType);
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
