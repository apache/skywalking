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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import com.google.protobuf.Message;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.LALOutputBuilder;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.testing.dsl.DslClassOutput;
import org.apache.skywalking.oap.server.testing.dsl.DslRuleLoader;
import org.apache.skywalking.oap.server.testing.dsl.lal.LalLogDataBuilder;
import org.apache.skywalking.oap.server.testing.dsl.lal.LalRuleLoader;
import org.apache.skywalking.oap.server.testing.dsl.lal.LalTestRule;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V2 execution test for LAL (Log Analysis Language) scripts.
 * Compiles via ANTLR4 + Javassist ({@link LALClassGenerator}) and
 * executes with mock LogData, validating expected results.
 */
class LALScriptExecutionTest {

    @TestFactory
    Collection<DynamicTest> lalScriptsCompileAndExecute() throws Exception {
        final List<DynamicTest> tests = new ArrayList<>();

        final Path scriptsDir = DslRuleLoader.findScriptsDir(
            "src/test/resources/scripts/lal",
            "oap-server/analyzer/log-analyzer/src/test/resources/scripts/lal");
        if (scriptsDir == null) {
            return tests;
        }
        final Path lalDir = scriptsDir.resolve("test-lal");
        if (!Files.isDirectory(lalDir)) {
            return tests;
        }

        final Map<String, List<LalTestRule>> allRules =
            LalRuleLoader.loadAllRules(lalDir);

        for (final Map.Entry<String, List<LalTestRule>> entry : allRules.entrySet()) {
            final String yamlFile = entry.getKey();
            for (final LalTestRule rule : entry.getValue()) {
                // Compile v2 once per rule — the expression is stateless
                org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression v2Expr = null;
                String v2CompileError = null;
                try {
                    v2Expr = compileV2(rule);
                } catch (Exception e) {
                    final Throwable cause = e.getCause() != null ? e.getCause() : e;
                    v2CompileError = cause.getClass().getSimpleName()
                        + ": " + cause.getMessage();
                }

                if (rule.getInputs().isEmpty()) {
                    final org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression expr = v2Expr;
                    final String err = v2CompileError;
                    tests.add(DynamicTest.dynamicTest(
                        yamlFile + " | " + rule.getName(),
                        () -> executeAndValidate(rule.getName(), rule.getDsl(),
                            null, expr, err)
                    ));
                } else {
                    for (int i = 0; i < rule.getInputs().size(); i++) {
                        final int idx = i;
                        final org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression expr = v2Expr;
                        final String err = v2CompileError;
                        final String testName = rule.getInputs().size() == 1
                            ? rule.getName() : rule.getName() + " [" + i + "]";
                        tests.add(DynamicTest.dynamicTest(
                            yamlFile + " | " + testName,
                            () -> executeAndValidate(testName, rule.getDsl(),
                                rule.getInputs().get(idx), expr, err)
                        ));
                    }
                }
            }
        }

        return tests;
    }

    private org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression compileV2(
            final LalTestRule rule) throws Exception {
        final LALClassGenerator generator = new LALClassGenerator();
        if (rule.getInputType() != null) {
            generator.setInputType(Class.forName(rule.getInputType()));
        } else if (rule.getLayer() != null) {
            generator.setInputType(spiInputTypes().get(rule.getLayer()));
        } else {
            generator.setInputType(null);
        }
        final Class<?> resolvedOutput = resolveOutputType(rule.getOutputType());
        if (resolvedOutput != null) {
            generator.setOutputType(resolvedOutput);
        }
        if (rule.getSourceFile() != null) {
            generator.setClassOutputDir(
                DslClassOutput.checkerTestDir(rule.getSourceFile()));
            generator.setClassNameHint(rule.getName());
            generator.setYamlSource(rule.getLineNo() > 0
                ? rule.getSourceFile().getName() + ":" + rule.getLineNo()
                : rule.getSourceFile().getName());
        }
        return generator.compile(rule.getDsl());
    }

    private void executeAndValidate(
            final String testName, final String dsl,
            final Map<String, Object> inputData,
            final org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression v2Expr,
            final String v2CompileError) throws Exception {

        final LogData.Builder testLogBuilder;
        if (inputData != null) {
            testLogBuilder = LalLogDataBuilder.buildLogData(inputData);
        } else {
            testLogBuilder = LalLogDataBuilder.buildSyntheticLogData(dsl).toBuilder();
        }
        final LogData testLog = testLogBuilder.build();

        // Build proto extraLog from input data if available
        final Message extraLog = inputData != null
            ? LalLogDataBuilder.buildExtraLog(inputData) : null;

        // ---- V2: ANTLR4 + Javassist path ----
        // v2 expression is pre-compiled (one compile per rule, multiple executions)
        final ModuleManager v2Manager = buildMockModuleManager();
        org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext v2Ctx = null;
        String v2Error = v2CompileError;
        if (v2Expr != null) {
            try {
                final org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec v2FilterSpec =
                    new org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec(
                        v2Manager, new LogAnalyzerModuleConfig());
                disableSinkListenersOnSpec(v2FilterSpec);

                final org.apache.skywalking.oap.server.core.source.LogMetadata v2Metadata =
                    org.apache.skywalking.oap.server.core.source.LogMetadataUtils.fromLogData(testLog);
                final Object v2Input = extraLog != null ? extraLog : testLogBuilder;
                v2Ctx = new org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext();
                v2Ctx.init(v2Metadata, v2Input);

                v2Expr.execute(v2FilterSpec, v2Ctx);
            } catch (Exception e) {
                final Throwable cause = e.getCause() != null ? e.getCause() : e;
                v2Error = cause.getClass().getSimpleName() + ": " + cause.getMessage();
            }
        }

        // ---- Validate ----
        if (v2Ctx == null) {
            fail(testName + ": v2 execution failed — " + v2Error);
            return;
        }

        // ---- Validate expected section ----
        if (inputData != null) {
            @SuppressWarnings("unchecked")
            final Map<String, Object> expect =
                (Map<String, Object>) inputData.get("expect");
            if (expect != null) {
                final LogData.Builder v2Log = v2Ctx.input() instanceof LogData.Builder
                    ? (LogData.Builder) v2Ctx.input() : null;
                validateExpected(testName, v2Ctx, v2Log, expect);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateExpected(final String ruleName,
                                  final org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext ctx,
                                  final LogData.Builder logBuilder,
                                  final Map<String, Object> expect) {
        final Object output = ctx.output();
        for (final Map.Entry<String, Object> entry : expect.entrySet()) {
            final String key = entry.getKey();
            final String expected = String.valueOf(entry.getValue());

            switch (key) {
                case "save":
                    assertEquals(Boolean.parseBoolean(expected), ctx.shouldSave(),
                        ruleName + ": expect.save mismatch");
                    break;
                case "abort":
                    assertEquals(Boolean.parseBoolean(expected), ctx.shouldAbort(),
                        ruleName + ": expect.abort mismatch");
                    break;
                case "service":
                    assertOutputField(ruleName, output, "service", expected);
                    break;
                case "instance":
                    assertOutputField(ruleName, output, "serviceInstance", expected);
                    break;
                case "endpoint":
                    assertOutputField(ruleName, output, "endpoint", expected);
                    break;
                case "layer":
                    assertOutputField(ruleName, output, "layer", expected);
                    break;
                case "timestamp":
                    assertOutputField(ruleName, output, "timestamp", expected);
                    break;
                default:
                    if (key.startsWith("tag.")) {
                        final String tagKey = key.substring(4);
                        if (output instanceof org.apache.skywalking.oap.server.core.source.LogBuilder) {
                            // v2 stores tags in LogBuilder.lalTags (private) — use reflection
                            assertLalTag(ruleName, output, tagKey, expected);
                        } else if (output != null) {
                            // Non-LogBuilder output: tags may not be applicable
                            assertOutputField(ruleName, output, tagKey, expected);
                        } else {
                            final String actual = logBuilder.getTags().getDataList().stream()
                                .filter(kv -> kv.getKey().equals(tagKey))
                                .map(KeyStringValuePair::getValue)
                                .findFirst().orElse("");
                            assertEquals(expected, actual,
                                ruleName + ": expect." + key + " mismatch");
                        }
                    } else if (key.startsWith("outputField.")) {
                        final String fieldName = key.substring("outputField.".length());
                        assertOutputField(ruleName, output, fieldName, expected);
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
                                   final String expected) {
        assertNotNull(output, ruleName + ": output is null for field " + fieldName);

        // Try getter candidates for known fields, then standard getter
        final String[] candidates = FIELD_GETTER_CANDIDATES.get(fieldName);
        if (candidates != null) {
            for (final String getterName : candidates) {
                try {
                    final Object actual = output.getClass().getMethod(getterName).invoke(output);
                    assertEquals(expected, String.valueOf(actual),
                        ruleName + ": expect." + fieldName + " mismatch");
                    return;
                } catch (final NoSuchMethodException ignored) {
                    // try next candidate
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
                assertEquals(expected, String.valueOf(actual),
                    ruleName + ": expect." + fieldName + " mismatch");
                return;
            } catch (final NoSuchMethodException ignored) {
                // fall through to field access
            } catch (final Exception e) {
                fail(ruleName + ": getter " + getterName + " failed: " + e.getMessage());
                return;
            }
        }

        // Fall back to field access (field may be null — that's valid)
        final Object[] result = getFieldValueOrMissing(output, fieldName);
        if (result == null) {
            fail(ruleName + ": field " + fieldName + " not found on "
                + output.getClass().getName());
            return;
        }
        final String actual = result[0] != null ? String.valueOf(result[0]) : "";
        assertEquals(expected, actual,
            ruleName + ": expect." + fieldName + " mismatch");
    }

    /**
     * Returns {value} if field found, or null if field not found.
     * Value itself may be null (returned as {null}).
     */
    private static Object[] getFieldValueOrMissing(final Object target, final String fieldName) {
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                final Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                return new Object[]{f.get(target)};
            } catch (final NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (final Exception e) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void assertLalTag(final String ruleName,
                              final Object output,
                              final String tagKey,
                              final String expected) {
        assertNotNull(output, ruleName + ": output is null for tag " + tagKey);
        try {
            final Field f = output.getClass().getDeclaredField("lalTags");
            f.setAccessible(true);
            final List<String[]> lalTags = (List<String[]>) f.get(output);
            final String actual = lalTags.stream()
                .filter(kv -> kv[0].equals(tagKey))
                .map(kv -> kv[1])
                .findFirst().orElse("");
            assertEquals(expected, actual,
                ruleName + ": expect.tag." + tagKey + " mismatch");
        } catch (final Exception e) {
            fail(ruleName + ": could not read lalTags from " + output.getClass().getName());
        }
    }

    private static String getFieldValue(final Object target, final String fieldName,
                                         final String defaultValue) {
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                final Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                final Object val = f.get(target);
                return val != null ? String.valueOf(val) : defaultValue;
            } catch (final NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (final Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private ModuleManager buildMockModuleManager() {
        final ModuleManager manager = mock(ModuleManager.class);
        setInternalField(manager, "isInPrepareStage", false);
        when(manager.find(anyString())).thenReturn(mock(ModuleProviderHolder.class));

        final ModuleProviderHolder logAnalyzerHolder = mock(ModuleProviderHolder.class);
        final org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider
            provider = mock(
                org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider.class);
        when(provider.getMetricConverts()).thenReturn(Collections.emptyList());
        when(logAnalyzerHolder.provider()).thenReturn(provider);
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

    private void disableSinkListenersOnSpec(final Object filterSpec) {
        try {
            setInternalField(filterSpec, "sinkListenerFactories", Collections.emptyList());
        } catch (Exception e) {
            // Best effort
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
        } catch (Exception e) {
            // ignore
        }
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

    private Map<String, Class<?>> outputBuilderNames;

    private Map<String, Class<?>> outputBuilderNameMap() {
        if (outputBuilderNames == null) {
            outputBuilderNames = new HashMap<>();
            for (final LALOutputBuilder builder :
                    ServiceLoader.load(LALOutputBuilder.class)) {
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
}
