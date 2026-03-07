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
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.SampledTraceBuilder;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
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
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
                    tests.add(DynamicTest.dynamicTest(
                        yamlFile + " | " + rule.name,
                        () -> compareExecution(rule.name, rule.dsl, null, expr, err)
                    ));
                } else {
                    for (int i = 0; i < rule.inputs.size(); i++) {
                        final int idx = i;
                        final org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression expr = v2Expr;
                        final String err = v2CompileError;
                        final String testName = rule.inputs.size() == 1
                            ? rule.name : rule.name + " [" + i + "]";
                        tests.add(DynamicTest.dynamicTest(
                            yamlFile + " | " + testName,
                            () -> compareExecution(testName, rule.dsl,
                                rule.inputs.get(idx), expr, err)
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
        if (rule.extraLogType != null) {
            generator.setExtraLogType(Class.forName(rule.extraLogType));
        } else if (rule.layer != null) {
            generator.setExtraLogType(spiExtraLogTypes().get(rule.layer));
        } else {
            generator.setExtraLogType(null);
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
            final String v2CompileError) throws Exception {

        final LogData testLog = buildLogData(inputData, dsl);

        // Build proto extraLog from input data if available
        final Message extraLog = buildExtraLog(inputData);

        // ---- V1: Groovy path ----
        // v1 uses original packages: org.apache.skywalking.oap.log.analyzer.dsl.*
        final ModuleManager v1Manager = buildMockModuleManager(true);
        final org.apache.skywalking.oap.log.analyzer.dsl.Binding v1Ctx;
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
            fail(testName + ": v2 execution failed but v1 succeeded — " + v2Error);
            return;
        }

        // Compare binding state
        assertEquals(v1Ctx.shouldAbort(), v2Ctx.shouldAbort(),
            testName + ": shouldAbort mismatch");
        assertEquals(v1Ctx.shouldSave(), v2Ctx.shouldSave(),
            testName + ": shouldSave mismatch");

        final LogData.Builder v1Log = v1Ctx.log();
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

        // Compare sampledTrace builder state
        // v1 Groovy Binding throws MissingPropertyException if sampledTrace was never set
        SampledTraceBuilder v1St = null;
        try {
            v1St = v1Ctx.sampledTraceBuilder();
        } catch (Exception ignored) {
            // Not set — rule has no sampledTrace block
        }
        final SampledTraceBuilder v2St = v2Ctx.sampledTraceBuilder();
        if (v1St != null || v2St != null) {
            if (v1St == null) {
                fail(testName + ": v1 has no sampledTrace but v2 does");
            }
            if (v2St == null) {
                fail(testName + ": v2 has no sampledTrace but v1 does");
            }
            // Fields set by prepareSampledTrace() from log context
            assertEquals(v1St.getTraceId(), v2St.getTraceId(),
                testName + ": sampledTrace.traceId mismatch");
            assertEquals(v1St.getServiceName(), v2St.getServiceName(),
                testName + ": sampledTrace.serviceName mismatch");
            assertEquals(v1St.getServiceInstanceName(), v2St.getServiceInstanceName(),
                testName + ": sampledTrace.serviceInstanceName mismatch");
            assertEquals(v1St.getLayer(), v2St.getLayer(),
                testName + ": sampledTrace.layer mismatch");
            assertEquals(v1St.getTimestamp(), v2St.getTimestamp(),
                testName + ": sampledTrace.timestamp mismatch");

            // Verify traceId came from the log (not empty/fabricated)
            assertEquals(testLog.getTraceContext().getTraceId(),
                v2St.getTraceId(),
                testName + ": sampledTrace.traceId should match log traceId");

            // Fields set by DSL closure body
            assertEquals(v1St.getLatency(), v2St.getLatency(),
                testName + ": sampledTrace.latency mismatch");
            assertEquals(v1St.getUri(), v2St.getUri(),
                testName + ": sampledTrace.uri mismatch");
            assertEquals(v1St.getReason(), v2St.getReason(),
                testName + ": sampledTrace.reason mismatch");
            assertEquals(v1St.getProcessId(), v2St.getProcessId(),
                testName + ": sampledTrace.processId mismatch");
            assertEquals(v1St.getDestProcessId(), v2St.getDestProcessId(),
                testName + ": sampledTrace.destProcessId mismatch");
            assertEquals(v1St.getDetectPoint(), v2St.getDetectPoint(),
                testName + ": sampledTrace.detectPoint mismatch");
            assertEquals(v1St.getComponentId(), v2St.getComponentId(),
                testName + ": sampledTrace.componentId mismatch");

            // Verify builder.toRecord() produces valid Record for RecordStreamProcessor
            // (submitSampledTrace already called validate + toRecord + RecordStreamProcessor.in
            // during execution; this explicitly confirms toRecord consistency)
            final Record v1Record = v1St.toRecord();
            final Record v2Record = v2St.toRecord();
            assertNotNull(v1Record, testName + ": v1 toRecord() returned null");
            assertNotNull(v2Record, testName + ": v2 toRecord() returned null");
            assertEquals(v1Record.getClass(), v2Record.getClass(),
                testName + ": toRecord() type mismatch");

            // Verify v2 actually dispatched the trace via sourceReceiver.receive()
            final SourceReceiver v2Receiver = v2Manager.find(CoreModule.NAME)
                .provider().getService(SourceReceiver.class);
            verify(v2Receiver, atLeastOnce()).receive(any());
        }

        // ---- Validate expected section ----
        if (inputData != null) {
            @SuppressWarnings("unchecked")
            final Map<String, Object> expect =
                (Map<String, Object>) inputData.get("expect");
            if (expect != null) {
                validateExpected(testName, v2Ctx, v2Log, expect);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateExpected(final String ruleName,
                                  final org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext ctx,
                                  final LogData.Builder logBuilder,
                                  final Map<String, Object> expect) {
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
                    assertEquals(expected, logBuilder.getService(),
                        ruleName + ": expect.service mismatch");
                    break;
                case "instance":
                    assertEquals(expected, logBuilder.getServiceInstance(),
                        ruleName + ": expect.instance mismatch");
                    break;
                case "endpoint":
                    assertEquals(expected, logBuilder.getEndpoint(),
                        ruleName + ": expect.endpoint mismatch");
                    break;
                case "layer":
                    assertEquals(expected, logBuilder.getLayer(),
                        ruleName + ": expect.layer mismatch");
                    break;
                case "timestamp":
                    assertEquals(Long.parseLong(expected), logBuilder.getTimestamp(),
                        ruleName + ": expect.timestamp mismatch");
                    break;
                default:
                    if (key.startsWith("tag.")) {
                        final String tagKey = key.substring(4);
                        final String actual = logBuilder.getTags().getDataList().stream()
                            .filter(kv -> kv.getKey().equals(tagKey))
                            .map(KeyStringValuePair::getValue)
                            .findFirst().orElse("");
                        assertEquals(expected, actual,
                            ruleName + ": expect." + key + " mismatch");
                    } else if (key.startsWith("sampledTrace.")) {
                        final String field = key.substring("sampledTrace.".length());
                        final SampledTraceBuilder st = ctx.sampledTraceBuilder();
                        assertNotNull(st, ruleName + ": expect sampledTrace but builder is null");
                        validateSampledTraceField(ruleName, field, expected, st);
                    }
                    break;
            }
        }
    }

    private void validateSampledTraceField(final String ruleName,
                                           final String field, final String expected,
                                           final SampledTraceBuilder st) {
        switch (field) {
            case "traceId":
                assertEquals(expected, st.getTraceId(),
                    ruleName + ": expect.sampledTrace.traceId mismatch");
                break;
            case "serviceName":
                assertEquals(expected, st.getServiceName(),
                    ruleName + ": expect.sampledTrace.serviceName mismatch");
                break;
            case "serviceInstanceName":
                assertEquals(expected, st.getServiceInstanceName(),
                    ruleName + ": expect.sampledTrace.serviceInstanceName mismatch");
                break;
            case "timestamp":
                assertEquals(Long.parseLong(expected), st.getTimestamp(),
                    ruleName + ": expect.sampledTrace.timestamp mismatch");
                break;
            case "latency":
                assertEquals(Integer.parseInt(expected), st.getLatency(),
                    ruleName + ": expect.sampledTrace.latency mismatch");
                break;
            case "uri":
                assertEquals(expected, st.getUri(),
                    ruleName + ": expect.sampledTrace.uri mismatch");
                break;
            case "reason":
                assertNotNull(st.getReason(),
                    ruleName + ": expect.sampledTrace.reason is null");
                assertEquals(expected, st.getReason().name(),
                    ruleName + ": expect.sampledTrace.reason mismatch");
                break;
            case "processId":
                assertEquals(expected, st.getProcessId(),
                    ruleName + ": expect.sampledTrace.processId mismatch");
                break;
            case "destProcessId":
                assertEquals(expected, st.getDestProcessId(),
                    ruleName + ": expect.sampledTrace.destProcessId mismatch");
                break;
            case "detectPoint":
                assertNotNull(st.getDetectPoint(),
                    ruleName + ": expect.sampledTrace.detectPoint is null");
                assertEquals(expected, st.getDetectPoint().name(),
                    ruleName + ": expect.sampledTrace.detectPoint mismatch");
                break;
            case "componentId":
                assertEquals(Integer.parseInt(expected), st.getComponentId(),
                    ruleName + ": expect.sampledTrace.componentId mismatch");
                break;
            default:
                break;
        }
    }

    private ModuleManager buildMockModuleManager(final boolean isV1) {
        final ModuleManager manager = mock(ModuleManager.class);
        setInternalField(manager, "isInPrepareStage", false);
        when(manager.find(anyString())).thenReturn(mock(ModuleProviderHolder.class));

        // v1 and v2 have different LogAnalyzerModuleProvider classes that ExtractorSpec casts to.
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
                final String extraLogType = rule.get("extraLogType");
                final String layer = rule.get("layer");
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
                    name, dslStr, extraLogType, layer, inputs, file, lineNo));
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

    private Map<String, Class<?>> spiExtraLogTypes() {
        if (spiTypes == null) {
            spiTypes = new HashMap<>();
            for (final LALSourceTypeProvider p :
                    ServiceLoader.load(LALSourceTypeProvider.class)) {
                spiTypes.put(p.layer().name(), p.extraLogType());
            }
        }
        return spiTypes;
    }

    // ==================== Inner classes ====================

    private static class LalRule {
        final String name;
        final String dsl;
        final String extraLogType;
        final String layer;
        final List<Map<String, Object>> inputs;
        final File sourceFile;
        final int lineNo;

        LalRule(final String name, final String dsl,
                final String extraLogType, final String layer,
                final List<Map<String, Object>> inputs,
                final File sourceFile, final int lineNo) {
            this.name = name;
            this.dsl = dsl;
            this.extraLogType = extraLogType;
            this.layer = layer;
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
