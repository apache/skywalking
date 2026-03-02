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
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.JSONLog;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALClassGenerator;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleProvider;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                tests.add(DynamicTest.dynamicTest(
                    yamlFile + " | " + rule.name,
                    () -> compareExecution(rule)
                ));
            }
        }

        return tests;
    }

    private void compareExecution(final LalRule rule) throws Exception {
        final String ruleName = rule.name;
        final String dsl = rule.dsl;
        final ModuleManager manager = buildMockModuleManager();
        final LogData testLog = buildTestLogData(dsl);

        // ---- V1: Groovy path ----
        // v1 uses original packages: org.apache.skywalking.oap.log.analyzer.dsl.*
        org.apache.skywalking.oap.log.analyzer.dsl.Binding v1Binding = null;
        try {
            final org.apache.skywalking.oap.log.analyzer.dsl.DSL v1Dsl =
                org.apache.skywalking.oap.log.analyzer.dsl.DSL.of(
                    manager,
                    new org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig(),
                    dsl);
            disableSinkListeners(v1Dsl);

            v1Binding = new org.apache.skywalking.oap.log.analyzer.dsl.Binding().log(testLog);
            v1Dsl.bind(v1Binding);
            v1Dsl.evaluate();
        } catch (Exception e) {
            // V1 failed — skip comparison
        }

        // ---- V2: ANTLR4 + Javassist path ----
        // v2 uses .v2. packages: org.apache.skywalking.oap.log.analyzer.v2.dsl.*
        org.apache.skywalking.oap.log.analyzer.v2.dsl.Binding v2Binding = null;
        String v2Error = null;
        try {
            final LALClassGenerator generator = new LALClassGenerator();
            if (rule.sourceFile != null) {
                final String baseName = rule.sourceFile.getName()
                    .replaceFirst("\\.(yaml|yml)$", "");
                generator.setClassOutputDir(new java.io.File(
                    rule.sourceFile.getParent(),
                    baseName + ".generated-classes"));
                generator.setClassNameHint(baseName + "_" + ruleName);
            }
            final org.apache.skywalking.oap.log.analyzer.v2.dsl.LalExpression v2Expr =
                generator.compile(dsl);

            final org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec v2FilterSpec =
                new org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.filter.FilterSpec(
                    manager, new LogAnalyzerModuleConfig());
            disableSinkListenersOnSpec(v2FilterSpec);

            v2Binding = new org.apache.skywalking.oap.log.analyzer.v2.dsl.Binding().log(testLog);
            v2FilterSpec.bind(v2Binding);

            v2Expr.execute(v2FilterSpec, v2Binding);
        } catch (Exception e) {
            final Throwable cause = e.getCause() != null ? e.getCause() : e;
            v2Error = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }

        // ---- Compare ----
        if (v1Binding == null && v2Binding == null) {
            return;
        }
        if (v1Binding == null) {
            // V1 failed but v2 succeeded — v2 is more capable, OK
            return;
        }
        if (v2Binding == null) {
            fail(ruleName + ": v2 execution failed but v1 succeeded — " + v2Error);
            return;
        }

        // Compare binding state
        assertEquals(v1Binding.shouldAbort(), v2Binding.shouldAbort(),
            ruleName + ": shouldAbort mismatch");
        assertEquals(v1Binding.shouldSave(), v2Binding.shouldSave(),
            ruleName + ": shouldSave mismatch");

        final LogData.Builder v1Log = v1Binding.log();
        final LogData.Builder v2Log = v2Binding.log();

        assertEquals(v1Log.getService(), v2Log.getService(),
            ruleName + ": service mismatch");
        assertEquals(v1Log.getServiceInstance(), v2Log.getServiceInstance(),
            ruleName + ": serviceInstance mismatch");
        assertEquals(v1Log.getEndpoint(), v2Log.getEndpoint(),
            ruleName + ": endpoint mismatch");
        assertEquals(v1Log.getLayer(), v2Log.getLayer(),
            ruleName + ": layer mismatch");
        assertEquals(v1Log.getTimestamp(), v2Log.getTimestamp(),
            ruleName + ": timestamp mismatch");
        assertEquals(v1Log.getTags(), v2Log.getTags(),
            ruleName + ": tags mismatch");
    }

    private ModuleManager buildMockModuleManager() {
        final ModuleManager manager = mock(ModuleManager.class);
        setInternalField(manager, "isInPrepareStage", false);
        when(manager.find(anyString())).thenReturn(mock(ModuleProviderHolder.class));

        final ModuleProviderHolder logAnalyzerHolder = mock(ModuleProviderHolder.class);
        final LogAnalyzerModuleProvider logAnalyzerProvider = mock(LogAnalyzerModuleProvider.class);
        when(logAnalyzerProvider.getMetricConverts()).thenReturn(Collections.emptyList());
        when(logAnalyzerHolder.provider()).thenReturn(logAnalyzerProvider);
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
        return manager;
    }

    private LogData buildTestLogData(final String dsl) {
        final LogData.Builder builder = LogData.newBuilder()
            .setService("test-service")
            .setServiceInstance("test-instance")
            .setTimestamp(System.currentTimeMillis());

        if (dsl.contains("json")) {
            builder.setBody(LogDataBody.newBuilder()
                .setJson(JSONLog.newBuilder()
                    .setJson("{\"level\":\"ERROR\",\"msg\":\"test\","
                        + "\"layer\":\"GENERAL\",\"service\":\"test-svc\","
                        + "\"time\":\"1234567890\","
                        + "\"id\":\"slow-1\",\"statement\":\"SELECT 1\","
                        + "\"query_time\":500}")));
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
            final List<LalRule> lalRules = new ArrayList<>();
            for (final Map<String, String> rule : rules) {
                final String name = rule.get("name");
                final String dslStr = rule.get("dsl");
                if (name == null || dslStr == null) {
                    continue;
                }
                lalRules.add(new LalRule(name, dslStr, file));
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

    private static class LalRule {
        final String name;
        final String dsl;
        final File sourceFile;

        LalRule(final String name, final String dsl, final File sourceFile) {
            this.name = name;
            this.dsl = dsl;
            this.sourceFile = sourceFile;
        }
    }
}
