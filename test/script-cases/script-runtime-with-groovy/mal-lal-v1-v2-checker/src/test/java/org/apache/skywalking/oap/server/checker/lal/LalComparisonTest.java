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
import org.apache.skywalking.oap.server.core.source.LALOutputBuilder;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Dual-path comparison test for LAL (Log Analysis Language) scripts.
 * <ul>
 *   <li>Path A (v1): Groovy via {@code org.apache.skywalking.oap.log.analyzer.dsl.DSL}</li>
 *   <li>Path B (v2): ANTLR4 + Javassist via {@link LALClassGenerator}</li>
 * </ul>
 * Both paths are fed the same mock LogData and the resulting Binding state is compared.
 *
 * <p>v1 classes use original package {@code org.apache.skywalking.oap.log.analyzer.dsl.*},
 * v2 classes use {@code org.apache.skywalking.oap.log.analyzer.v2.dsl.*}.
 */
class LalComparisonTest {

    @TestFactory
    Collection<DynamicTest> lalScriptsCompileAndExecute() throws Exception {
        final List<DynamicTest> tests = new ArrayList<>();
        final Map<String, List<LalRule>> yamlRules = loadAllLalYamlFiles();

        for (final Map.Entry<String, List<LalRule>> entry : yamlRules.entrySet()) {
            final String yamlFile = entry.getKey();
            for (final LalRule rule : entry.getValue()) {
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

                if (rule.inputs.isEmpty()) {
                    final org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression expr = v2Expr;
                    final String err = v2CompileError;
                    final boolean v2Only = rule.outputType != null || rule.v2Only;
                    tests.add(DynamicTest.dynamicTest(
                        yamlFile + " | " + rule.name,
                        () -> compareExecution(rule.name, rule.dsl,
                            null, expr, err, v2Only)
                    ));
                } else {
                    for (int i = 0; i < rule.inputs.size(); i++) {
                        final int idx = i;
                        final org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression expr = v2Expr;
                        final String err = v2CompileError;
                        final boolean v2Only = rule.outputType != null || rule.v2Only;
                        final String testName = rule.inputs.size() == 1
                            ? rule.name : rule.name + " [" + i + "]";
                        tests.add(DynamicTest.dynamicTest(
                            yamlFile + " | " + testName,
                            () -> compareExecution(testName, rule.dsl,
                                rule.inputs.get(idx), expr, err, v2Only)
                        ));
                    }
                }
            }
        }

        return tests;
    }

    private org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression compileV2(
            final LalRule rule) throws Exception {
        final LALClassGenerator generator = new LALClassGenerator();
        if (rule.inputType != null) {
            generator.setInputType(Class.forName(rule.inputType));
        } else if (rule.layer != null) {
            generator.setInputType(spiInputTypes().get(rule.layer));
        } else {
            generator.setInputType(null);
        }
        final Class<?> resolvedOutput = resolveOutputType(rule.outputType);
        if (resolvedOutput != null) {
            generator.setOutputType(resolvedOutput);
        }
        if (rule.sourceFile != null) {
            final String baseName = rule.sourceFile.getName()
                .replaceFirst("\\.(yaml|yml)$", "");
            generator.setClassOutputDir(new java.io.File(
                rule.sourceFile.getParent(),
                baseName + ".generated-classes"));
            generator.setClassNameHint(rule.name);
            generator.setYamlSource(rule.lineNo > 0
                ? rule.sourceFile.getName() + ":" + rule.lineNo
                : rule.sourceFile.getName());
        }
        return generator.compile(rule.dsl);
    }

    private void compareExecution(
            final String testName, final String dsl,
            final Map<String, Object> inputData,
            final org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression v2Expr,
            final String v2CompileError,
            final boolean v2Only) throws Exception {

        final LogData testLog = buildLogData(inputData, dsl);

        // Build proto extraLog from input data if available
        final Message extraLog = buildExtraLog(inputData);

        // ---- V1: Groovy path (skipped for v2-only rules like outputType) ----
        org.apache.skywalking.oap.log.analyzer.dsl.Binding v1Ctx = null;
        if (!v2Only) {
            // v1 uses original packages: org.apache.skywalking.oap.log.analyzer.dsl.*
            final ModuleManager v1Manager = buildMockModuleManager(true);
            try {
                final org.apache.skywalking.oap.log.analyzer.dsl.DSL v1Dsl =
                    org.apache.skywalking.oap.log.analyzer.dsl.DSL.of(
                        v1Manager,
                        new org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig(),
                        dsl);
                disableSinkListeners(v1Dsl);

                v1Ctx = new org.apache.skywalking.oap.log.analyzer.dsl.Binding().log(testLog);
                if (extraLog != null) {
                    v1Ctx.extraLog(extraLog);
                }
                v1Dsl.bind(v1Ctx);
                v1Dsl.evaluate();
            } catch (Exception e) {
                final Throwable cause = e.getCause() != null ? e.getCause() : e;
                fail(testName + ": v1 (Groovy) failed — "
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                return;
            }
        }

        // ---- V2: ANTLR4 + Javassist path ----
        // v2 expression is pre-compiled (one compile per rule, multiple executions)
        final ModuleManager v2Manager = buildMockModuleManager(false);
        org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext v2Ctx = null;
        String v2Error = v2CompileError;
        if (v2Expr != null) {
            try {
                final org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec v2FilterSpec =
                    new org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec(
                        v2Manager, new LogAnalyzerModuleConfig());
                disableSinkListenersOnSpec(v2FilterSpec);

                v2Ctx = new org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext().log(testLog);
                if (extraLog != null) {
                    v2Ctx.extraLog(extraLog);
                }

                v2Expr.execute(v2FilterSpec, v2Ctx);
            } catch (Exception e) {
                final Throwable cause = e.getCause() != null ? e.getCause() : e;
                v2Error = cause.getClass().getSimpleName() + ": " + cause.getMessage();
            }
        }

        // ---- Compare ----
        if (v2Ctx == null) {
            final String context = v2Only ? "v2 execution failed" : "v2 execution failed but v1 succeeded";
            fail(testName + ": " + context + " — " + v2Error);
            return;
        }

        if (v1Ctx != null) {
            // Compare binding state
            assertEquals(v1Ctx.shouldAbort(), v2Ctx.shouldAbort(),
                testName + ": shouldAbort mismatch");
            assertEquals(v1Ctx.shouldSave(), v2Ctx.shouldSave(),
                testName + ": shouldSave mismatch");

            final LogData.Builder v1Log = v1Ctx.log();

            // v2 routes standard fields to the output builder, not LogData.Builder.
            // If the extractor sets a field, v1 puts it on LogData, v2 puts it on LogBuilder.
            // If the extractor doesn't set a field, both keep the original LogData value.
            final Object v2Output = v2Ctx.output();
            if (v2Output instanceof org.apache.skywalking.oap.server.core.source.LogBuilder) {
                final org.apache.skywalking.oap.server.core.source.LogBuilder v2Builder =
                    (org.apache.skywalking.oap.server.core.source.LogBuilder) v2Output;
                assertV2Field(testName, "service", v1Log.getService(),
                    getFieldValue(v2Builder, "service", null), v2Ctx.log().getService());
                assertV2Field(testName, "serviceInstance", v1Log.getServiceInstance(),
                    getFieldValue(v2Builder, "serviceInstance", null),
                    v2Ctx.log().getServiceInstance());
                assertV2Field(testName, "endpoint", v1Log.getEndpoint(),
                    getFieldValue(v2Builder, "endpoint", null), v2Ctx.log().getEndpoint());
                assertV2Field(testName, "layer", v1Log.getLayer(),
                    getFieldValue(v2Builder, "layer", null), v2Ctx.log().getLayer());
                final long v2Ts = v2Builder.getTimestamp();
                assertEquals(v1Log.getTimestamp(), v2Ts != 0 ? v2Ts : v2Ctx.log().getTimestamp(),
                    testName + ": timestamp mismatch");
                // Compare tags: v1 stores in LogData.tags, v2 stores in LogBuilder.lalTags
                compareV1TagsToV2Builder(testName, v1Log, v2Builder);
            } else {
                final LogData.Builder v2Log = v2Ctx.log();
                assertEquals(v1Log.getService(), v2Log.getService(),
                    testName + ": service mismatch");
                assertEquals(v1Log.getServiceInstance(), v2Log.getServiceInstance(),
                    testName + ": serviceInstance mismatch");
                assertEquals(v1Log.getEndpoint(), v2Log.getEndpoint(),
                    testName + ": endpoint mismatch");
                assertEquals(v1Log.getLayer(), v2Log.getLayer(),
                    testName + ": layer mismatch");
                assertEquals(v1Log.getTimestamp(), v2Log.getTimestamp(),
                    testName + ": timestamp mismatch");
                assertEquals(v1Log.getTags(), v2Log.getTags(),
                    testName + ": tags mismatch");
            }
        }

        // ---- Validate expected section ----
        if (inputData != null) {
            @SuppressWarnings("unchecked")
            final Map<String, Object> expect =
                (Map<String, Object>) inputData.get("expect");
            if (expect != null) {
                final LogData.Builder v2Log = v2Ctx.log();
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

    @SuppressWarnings("unchecked")
    private void compareV1TagsToV2Builder(
            final String testName,
            final LogData.Builder v1Log,
            final org.apache.skywalking.oap.server.core.source.LogBuilder v2Builder) {
        // v1 adds LAL-set tags to LogData.tags, but original input tags are also in LogData.tags.
        // v2 only adds LAL-set tags to lalTags; original tags stay in LogData.
        // Compare only LAL-added tags: v1 tags minus the original input tags.
        // For simplicity, compare the v2 lalTags against v1 LogData.tags that were ADDED by the extractor.
        // Since both v1 and v2 start with the same LogData, the difference is the extractor-added tags.
        try {
            final Field f = v2Builder.getClass().getDeclaredField("lalTags");
            f.setAccessible(true);
            final List<String[]> lalTags = (List<String[]>) f.get(v2Builder);
            // Compare count of LAL-added tags
            // v1 adds tags to LogData.tags in addition to original input tags.
            // For now, just verify each v2 lalTag key-value exists in v1 LogData.tags
            for (final String[] kv : lalTags) {
                final boolean found = v1Log.getTags().getDataList().stream()
                    .anyMatch(v1Kv -> v1Kv.getKey().equals(kv[0])
                        && v1Kv.getValue().equals(kv[1]));
                assertTrue(found,
                    testName + ": v2 lalTag " + kv[0] + "=" + kv[1]
                    + " not found in v1 LogData.tags");
            }
        } catch (final Exception e) {
            // Best effort — lalTags field may not exist on non-LogBuilder outputs
        }
    }

    private void assertV2Field(final String testName, final String fieldName,
                               final String v1Value, final String v2OutputValue,
                               final String v2LogValue) {
        // If v2 output has the field, use it; otherwise fall back to LogData (unmodified original)
        final String v2Value = v2OutputValue != null ? v2OutputValue : v2LogValue;
        assertEquals(v1Value, v2Value,
            testName + ": " + fieldName + " mismatch");
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

    private ModuleManager buildMockModuleManager(final boolean isV1) {
        final ModuleManager manager = mock(ModuleManager.class);
        setInternalField(manager, "isInPrepareStage", false);
        when(manager.find(anyString())).thenReturn(mock(ModuleProviderHolder.class));

        // v1 and v2 have different LogAnalyzerModuleProvider classes that MetricExtractor casts to.
        // Each path needs its own manager with the correct provider type.
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
                    .setJson("{\"level\":\"ERROR\",\"msg\":\"test\","
                        + "\"layer\":\"GENERAL\",\"service\":\"test-svc\","
                        + "\"instance\":\"test-inst\",\"endpoint\":\"test-ep\","
                        + "\"time\":\"1234567890\","
                        + "\"id\":\"slow-1\",\"statement\":\"SELECT 1\","
                        + "\"query_time\":500,\"code\":200,"
                        + "\"env\":\"prod\",\"region\":\"us-east\","
                        + "\"skip\":\"false\","
                        + "\"data\":{\"name\":\"test-value\"},"
                        + "\"latency\":100,\"uri\":\"/api/test\","
                        + "\"reason\":\"SLOW\",\"pid\":\"proc-1\","
                        + "\"dpid\":\"proc-2\",\"dp\":\"CLIENT\"}")));
        }

        if (dsl.contains("LOG_KIND")) {
            builder.setTags(LogTags.newBuilder()
                .addData(KeyStringValuePair.newBuilder()
                    .setKey("LOG_KIND").setValue("SLOW_SQL")));
        }

        return builder.build();
    }

    private void disableSinkListeners(final Object dsl) {
        try {
            final Object filterSpec = getInternalField(dsl, "filterSpec");
            setInternalField(filterSpec, "sinkListenerFactories", Collections.emptyList());
        } catch (Exception e) {
            // Best effort
        }
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
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<LalRule>> loadAllLalYamlFiles() throws Exception {
        final Map<String, List<LalRule>> result = new HashMap<>();
        final Yaml yaml = new Yaml();

        final Path scriptsDir = findScriptsDir("lal");
        if (scriptsDir == null) {
            return result;
        }
        final Path lalDir = scriptsDir.resolve("test-lal");
        if (!Files.isDirectory(lalDir)) {
            return result;
        }

        // Scan top-level and subdirectories (oap-cases/, feature-cases/)
        final List<File> yamlFiles = new ArrayList<>();
        collectYamlFiles(lalDir.toFile(), yamlFiles);

        for (final File file : yamlFiles) {
            final String content = Files.readString(file.toPath());
            final Map<String, Object> config = yaml.load(content);
            if (config == null || !config.containsKey("rules")) {
                continue;
            }
            final List<Map<String, String>> rules =
                (List<Map<String, String>>) config.get("rules");
            if (rules == null) {
                continue;
            }

            // Load matching .data.yaml file if present
            final String baseName = file.getName()
                .replaceFirst("\\.(yaml|yml)$", "");
            final File inputDataFile = new File(file.getParent(),
                baseName + ".data.yaml");
            Map<String, Map<String, Object>> inputData = null;
            if (inputDataFile.exists()) {
                inputData = yaml.load(
                    Files.readString(inputDataFile.toPath()));
            }

            final List<LalRule> lalRules = new ArrayList<>();
            final String[] lines = content.split("\n");
            final Map<String, Integer> nameCount = new HashMap<>();
            for (final Map<String, String> rule : rules) {
                final String name = rule.get("name");
                final String dslStr = rule.get("dsl");
                if (name == null || dslStr == null) {
                    continue;
                }
                final String inputType = rule.get("inputType");
                final String outputType = rule.get("outputType");
                final String layer = rule.get("layer");
                final boolean v2OnlyFlag = Boolean.TRUE.equals(rule.get("v2Only"));
                final int count = nameCount.merge(name, 1, Integer::sum);
                final int lineNo = findRuleLine(lines, name, count);

                final Object ruleInput = inputData != null
                    ? inputData.get(name) : null;

                final List<Map<String, Object>> inputs;
                if (ruleInput instanceof List) {
                    inputs = (List<Map<String, Object>>) ruleInput;
                } else if (ruleInput instanceof Map) {
                    inputs = Collections.singletonList(
                        (Map<String, Object>) ruleInput);
                } else {
                    inputs = Collections.emptyList();
                }
                lalRules.add(new LalRule(
                    name, dslStr, inputType, outputType, layer,
                    v2OnlyFlag, inputs, file, lineNo));
            }

            if (!lalRules.isEmpty()) {
                final String relative = lalDir.relativize(file.toPath()).toString();
                result.put("lal/" + relative, lalRules);
            }
        }
        return result;
    }

    private Path findScriptsDir(final String language) {
        final String[] candidates = {
            "test/script-cases/scripts/" + language,
            "../../scripts/" + language
        };
        for (final String candidate : candidates) {
            final Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return null;
    }

    private static void collectYamlFiles(final File dir,
                                            final List<File> result) {
        final File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (final File child : children) {
            if (child.isDirectory()) {
                collectYamlFiles(child, result);
            } else if (child.getName().endsWith(".yaml")
                    || child.getName().endsWith(".yml")) {
                result.add(child);
            }
        }
    }

    // ==================== Proto extraLog builder ====================

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

    // ==================== Inner classes ====================

    private static class LalRule {
        final String name;
        final String dsl;
        final String inputType;
        final String outputType;
        final String layer;
        final boolean v2Only;
        final List<Map<String, Object>> inputs;
        final File sourceFile;
        final int lineNo;

        LalRule(final String name, final String dsl,
                final String inputType, final String outputType,
                final String layer, final boolean v2Only,
                final List<Map<String, Object>> inputs,
                final File sourceFile, final int lineNo) {
            this.name = name;
            this.dsl = dsl;
            this.inputType = inputType;
            this.outputType = outputType;
            this.layer = layer;
            this.v2Only = v2Only;
            this.inputs = inputs;
            this.sourceFile = sourceFile;
            this.lineNo = lineNo;
        }
    }

    /**
     * Find the 1-based line number of the Nth occurrence of {@code name: <value>} in YAML.
     */
    private static int findRuleLine(final String[] lines, final String name,
                                    final int occurrence) {
        int found = 0;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("- ")) {
                trimmed = trimmed.substring(2);
            }
            if (trimmed.equals("name: " + name)
                    || trimmed.equals("name: '" + name + "'")
                    || trimmed.equals("name: \"" + name + "\"")) {
                found++;
                if (found == occurrence) {
                    return i + 1;
                }
            }
        }
        return 0;
    }
}
