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

package org.apache.skywalking.oap.server.starter;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javassist.ClassPool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oal.v2.generator.CodeGenModel;
import org.apache.skywalking.oal.v2.generator.MetricDefinitionEnricher;
import org.apache.skywalking.oal.v2.generator.OALClassGeneratorV2;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.apache.skywalking.oap.log.analyzer.v2.compiler.LALClassGenerator;
import org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALClassGenerator;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.SourceDecoratorManager;
import org.apache.skywalking.oap.server.core.config.v2.compiler.HierarchyRuleClassGenerator;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.LALOutputBuilder;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Compiles all DSL scripts (OAL, MAL, LAL, Hierarchy) from server-starter
 * resources and dumps generated .class files to
 * {@code target/generated-dsl-classes/} for inspection.
 *
 * <p>This does not start the OAP server or the module system. It only parses
 * and compiles the DSL scripts, producing bytecode that can be decompiled
 * with IDE tools or {@code javap}.
 */
@Slf4j
public class DSLClassGeneratorTest {

    private static final String SOURCE_PACKAGE = "org.apache.skywalking.oap.server.core.source.";
    private static final String BROWSER_SOURCE_PACKAGE = "org.apache.skywalking.oap.server.core.browser.source.";
    private static final String METRICS_PACKAGE = "org.apache.skywalking.oap.server.core.source.oal.rt.metrics.";

    private static final String[] POSSIBLE_PATHS = {
        "oap-server/server-starter/src/main/resources/",
        "../server-starter/src/main/resources/",
        "src/main/resources/"
    };

    private static final String[] MAL_DIRS = {
        "otel-rules",
        "meter-analyzer-config",
        "envoy-metrics-rules",
        "log-mal-rules"
    };

    private static File RESOURCES_DIR;
    private static File OUTPUT_BASE;

    @BeforeAll
    public static void setup() {
        RESOURCES_DIR = resolveResourcesDir();
        assertNotNull(RESOURCES_DIR, "Cannot find server-starter resources directory. "
            + "Tried: " + String.join(", ", POSSIBLE_PATHS));

        OUTPUT_BASE = new File("target/generated-dsl-classes");
        OUTPUT_BASE.mkdirs();

        log.info("Resources: {}", RESOURCES_DIR.getAbsolutePath());
        log.info("Output:    {}", OUTPUT_BASE.getAbsolutePath());
    }

    @Test
    public void generateAllDSLClasses() {
        final List<String> errors = new ArrayList<>();
        int oalClasses = 0;
        int malExpressions = 0;
        int malFilters = 0;
        int lalExpressions = 0;
        int hierarchyRules = 0;

        // ── OAL ──
        log.info("--- OAL ---");
        initializeScopes();

        final File oalDir = new File(OUTPUT_BASE, "oal");
        oalDir.mkdirs();
        OALClassGeneratorV2.setGeneratedFilePath(oalDir.getAbsolutePath());

        final Map<String, OALDefine> defines = buildOALDefines();
        final ClassPool oalClassPool = new ClassPool(true);

        for (final Map.Entry<String, OALDefine> entry : defines.entrySet()) {
            final String name = entry.getKey();
            final OALDefine define = entry.getValue();
            final File file = new File(RESOURCES_DIR, define.getConfigFile());
            if (!file.isFile()) {
                errors.add("OAL: file not found: " + file);
                continue;
            }
            try (FileReader reader = new FileReader(file)) {
                final OALScriptParserV2 parser =
                    OALScriptParserV2.parse(reader, define.getConfigFile());
                final List<MetricDefinition> metrics = parser.getMetrics();
                final List<String> disabled = parser.getDisabledSources();

                if (metrics.isEmpty()) {
                    log.info("  {}: 0 metrics, {} disabled sources", name, disabled.size());
                    continue;
                }

                final MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(
                    define.getSourcePackage(), METRICS_PACKAGE
                );
                final List<CodeGenModel> models = new ArrayList<>();
                for (final MetricDefinition m : metrics) {
                    models.add(enricher.enrich(m));
                }

                final OALClassGeneratorV2 gen = new OALClassGeneratorV2(define, oalClassPool);
                gen.setOpenEngineDebug(true);
                gen.setStorageBuilderFactory(new StorageBuilderFactory.Default());

                final List<Class> metricsClasses = new ArrayList<>();
                final List<Class> dispatcherClasses = new ArrayList<>();
                gen.generateClassAtRuntime(models, disabled, metricsClasses, dispatcherClasses);

                final int count = metricsClasses.size() + dispatcherClasses.size();
                oalClasses += count;
                log.info("  {}: {} metrics -> {} classes", name, metrics.size(), count);
            } catch (Exception e) {
                errors.add("OAL/" + name + ": " + e.getMessage());
                log.error("  {}: FAILED - {}", name, e.getMessage());
            }
        }

        // ── MAL ──
        log.info("--- MAL ---");
        final File malDir = new File(OUTPUT_BASE, "mal");
        malDir.mkdirs();

        final MALClassGenerator malGen = new MALClassGenerator();
        malGen.setClassOutputDir(malDir);

        for (final String dirName : MAL_DIRS) {
            final File dir = new File(RESOURCES_DIR, dirName);
            if (!dir.isDirectory()) {
                log.info("  {}: directory not found, skipping", dirName);
                continue;
            }

            for (final File yamlFile : findYamlFiles(dir)) {
                final int[] counts = compileMALFile(malGen, dir, yamlFile, errors);
                malExpressions += counts[0];
                malFilters += counts[1];
            }
        }
        log.info("  Total: {} expressions, {} filters", malExpressions, malFilters);

        // ── LAL ──
        log.info("--- LAL ---");
        final File lalDir = new File(OUTPUT_BASE, "lal");
        lalDir.mkdirs();

        lalExpressions = compileLAL(lalDir, errors);
        log.info("  Total: {} expressions", lalExpressions);

        // ── Hierarchy ──
        log.info("--- Hierarchy ---");
        final File hierarchyDir = new File(OUTPUT_BASE, "hierarchy");
        hierarchyDir.mkdirs();

        hierarchyRules = compileHierarchy(hierarchyDir, errors);
        log.info("  Total: {} rules", hierarchyRules);

        // ── Summary ──
        log.info("=== Summary ===");
        log.info("OAL:       {} classes", oalClasses);
        log.info("MAL:       {} expressions, {} filters", malExpressions, malFilters);
        log.info("LAL:       {} expressions", lalExpressions);
        log.info("Hierarchy: {} rules", hierarchyRules);
        log.info("Output:    {}", OUTPUT_BASE.getAbsolutePath());

        if (!errors.isEmpty()) {
            log.error("=== {} Failures ===", errors.size());
            errors.forEach(e -> log.error("  - {}", e));
            fail("DSL compilation failures: " + errors.size() + "\n"
                + String.join("\n", errors));
        }

        assertTrue(oalClasses > 100, "Expected > 100 OAL classes, got " + oalClasses);
        assertTrue(malExpressions > 100, "Expected > 100 MAL expressions, got " + malExpressions);
        assertTrue(lalExpressions > 0, "Expected > 0 LAL expressions, got " + lalExpressions);
        assertTrue(hierarchyRules > 0, "Expected > 0 hierarchy rules, got " + hierarchyRules);
    }

    // ──────────────────────────── MAL ────────────────────────────

    @SuppressWarnings("unchecked")
    private static int[] compileMALFile(final MALClassGenerator generator,
                                        final File baseDir,
                                        final File yamlFile,
                                        final List<String> errors) {
        int expressions = 0;
        int filters = 0;

        try (Reader reader = new FileReader(yamlFile)) {
            final Map<String, Object> config = new Yaml().load(reader);
            if (config == null) {
                return new int[]{0, 0};
            }

            final String relPath = baseDir.toPath().relativize(yamlFile.toPath()).toString();
            final String sourceName = relPath.substring(0, relPath.lastIndexOf('.'));
            final String yamlSource = yamlFile.getName();

            final String expPrefix = (String) config.get("expPrefix");
            final String expSuffix = (String) config.get("expSuffix");
            final String metricPrefix = (String) config.get("metricPrefix");
            final String filterText = (String) config.get("filter");

            // Compile filter
            if (filterText != null && !filterText.trim().isEmpty()) {
                try {
                    generator.setClassNameHint("filter");
                    generator.setYamlSource(yamlSource);
                    generator.compileFilter(filterText);
                    filters++;
                } catch (Exception e) {
                    errors.add("MAL-filter/" + sourceName + ": " + e.getMessage());
                    log.error("  {}: filter FAILED - {}", sourceName, e.getMessage());
                } finally {
                    generator.setClassNameHint(null);
                    generator.setYamlSource(null);
                }
            }

            // Compile expression rules
            List<Map<String, String>> rules =
                (List<Map<String, String>>) config.get("metricsRules");
            if (rules == null) {
                rules = (List<Map<String, String>>) config.get("metrics");
            }
            if (rules == null) {
                return new int[]{expressions, filters};
            }

            for (int i = 0; i < rules.size(); i++) {
                final Map<String, String> rule = rules.get(i);
                final String ruleName = rule.get("name");
                final String exp = rule.get("exp");
                if (exp == null || exp.trim().isEmpty()) {
                    continue;
                }

                final String fullExp = formatExp(expPrefix, expSuffix, exp);
                final String metricName = metricPrefix != null
                    ? metricPrefix + "_" + ruleName : ruleName;

                try {
                    generator.setClassNameHint(ruleName);
                    generator.setYamlSource(yamlSource + ":" + i);
                    generator.compile(metricName, fullExp);
                    expressions++;
                } catch (Exception e) {
                    errors.add("MAL/" + sourceName + "/" + ruleName + ": " + e.getMessage());
                    log.error("  {}/{}: FAILED - {}", sourceName, ruleName, e.getMessage());
                } finally {
                    generator.setClassNameHint(null);
                    generator.setYamlSource(null);
                }
            }
        } catch (Exception e) {
            errors.add("MAL/" + yamlFile.getName() + ": " + e.getMessage());
            log.error("  {}: FAILED - {}", yamlFile.getName(), e.getMessage());
        }

        return new int[]{expressions, filters};
    }

    // ──────────────────────────── LAL ────────────────────────────

    @SuppressWarnings("unchecked")
    private static int compileLAL(final File lalDir, final List<String> errors) {
        int count = 0;

        // SPI: inputType/outputType per layer
        final Map<Layer, LALSourceTypeProvider> spiProviders = new HashMap<>();
        for (final LALSourceTypeProvider p : ServiceLoader.load(LALSourceTypeProvider.class)) {
            spiProviders.put(p.layer(), p);
            log.info("  SPI: layer={}, inputType={}", p.layer().name(), p.inputType().getName());
        }

        // SPI: LALOutputBuilder short names
        final Map<String, Class<?>> outputBuilderNames = new HashMap<>();
        for (final LALOutputBuilder builder : ServiceLoader.load(LALOutputBuilder.class)) {
            outputBuilderNames.put(builder.name(), builder.getClass());
        }

        final File lalResDir = new File(RESOURCES_DIR, "lal");
        if (!lalResDir.isDirectory()) {
            log.info("  lal/ directory not found, skipping");
            return 0;
        }

        final File[] yamlFiles = lalResDir.listFiles(
            (dir, name) -> name.endsWith(".yaml") || name.endsWith(".yml")
        );
        if (yamlFiles == null) {
            return 0;
        }

        for (final File yamlFile : yamlFiles) {
            try (Reader reader = new FileReader(yamlFile)) {
                final Map<String, Object> config = new Yaml().load(reader);
                if (config == null) {
                    continue;
                }

                final List<Map<String, Object>> rules =
                    (List<Map<String, Object>>) config.get("rules");
                if (rules == null) {
                    continue;
                }

                for (final Map<String, Object> rule : rules) {
                    final String ruleName = (String) rule.get("name");
                    final String dsl = (String) rule.get("dsl");
                    final String layerStr = (String) rule.get("layer");
                    final String inputTypeStr = (String) rule.get("inputType");
                    final String outputTypeStr = (String) rule.get("outputType");

                    if (dsl == null || dsl.trim().isEmpty()) {
                        continue;
                    }

                    try {
                        final LALClassGenerator gen = new LALClassGenerator();
                        gen.setClassOutputDir(lalDir);
                        gen.setClassNameHint(ruleName);
                        gen.setYamlSource(yamlFile.getName());
                        gen.setInputType(resolveInputType(inputTypeStr, layerStr, spiProviders));
                        gen.setOutputType(resolveOutputType(
                            outputTypeStr, layerStr, spiProviders, outputBuilderNames));

                        gen.compile(dsl);
                        count++;
                    } catch (Exception e) {
                        errors.add("LAL/" + yamlFile.getName() + "/" + ruleName
                            + ": " + e.getMessage());
                        log.error("  {}/{}: FAILED - {}",
                            yamlFile.getName(), ruleName, e.getMessage());
                    }
                }
            } catch (Exception e) {
                errors.add("LAL/" + yamlFile.getName() + ": " + e.getMessage());
                log.error("  {}: FAILED - {}", yamlFile.getName(), e.getMessage());
            }
        }
        return count;
    }

    // ──────────────────────────── Hierarchy ────────────────────────────

    @SuppressWarnings("unchecked")
    private static int compileHierarchy(final File hierarchyDir, final List<String> errors) {
        int count = 0;
        final File hierarchyYml = new File(RESOURCES_DIR, "hierarchy-definition.yml");
        if (!hierarchyYml.isFile()) {
            log.info("  hierarchy-definition.yml not found, skipping");
            return 0;
        }

        try (Reader reader = new FileReader(hierarchyYml)) {
            final Map<String, Map> config = new Yaml().loadAs(reader, Map.class);
            final Map<String, String> ruleExpressions =
                (Map<String, String>) config.get("auto-matching-rules");

            if (ruleExpressions == null || ruleExpressions.isEmpty()) {
                log.info("  No auto-matching-rules found");
                return 0;
            }

            final HierarchyRuleClassGenerator gen = new HierarchyRuleClassGenerator();
            gen.setClassOutputDir(hierarchyDir);
            gen.setYamlSource("hierarchy-definition.yml");

            for (final Map.Entry<String, String> entry : ruleExpressions.entrySet()) {
                final String ruleName = entry.getKey();
                try {
                    gen.setClassNameHint(ruleName);
                    gen.compile(ruleName, entry.getValue());
                    count++;
                } catch (Exception e) {
                    errors.add("Hierarchy/" + ruleName + ": " + e.getMessage());
                    log.error("  {}: FAILED - {}", ruleName, e.getMessage());
                }
            }
        } catch (Exception e) {
            errors.add("Hierarchy: " + e.getMessage());
            log.error("  FAILED - {}", e.getMessage());
        }
        return count;
    }

    // ──────────────────────────── Helpers ────────────────────────────

    private static Map<String, OALDefine> buildOALDefines() {
        final Map<String, OALDefine> defines = new LinkedHashMap<>();
        defines.put("core", oalDefine("oal/core.oal", SOURCE_PACKAGE, ""));
        defines.put("java-agent", oalDefine("oal/java-agent.oal", SOURCE_PACKAGE, ""));
        defines.put("dotnet-agent", oalDefine("oal/dotnet-agent.oal", SOURCE_PACKAGE, ""));
        defines.put("browser", oalDefine("oal/browser.oal", BROWSER_SOURCE_PACKAGE, ""));
        defines.put("mesh", oalDefine("oal/mesh.oal", SOURCE_PACKAGE, "ServiceMesh"));
        defines.put("tcp", oalDefine("oal/tcp.oal", SOURCE_PACKAGE, "EnvoyTCP"));
        defines.put("ebpf", oalDefine("oal/ebpf.oal", SOURCE_PACKAGE, ""));
        defines.put("cilium", oalDefine("oal/cilium.oal", SOURCE_PACKAGE, ""));
        defines.put("disable", oalDefine("oal/disable.oal", SOURCE_PACKAGE, ""));
        return defines;
    }

    private static OALDefine oalDefine(final String configFile,
                                       final String sourcePackage,
                                       final String catalog) {
        return new OALDefine(configFile, sourcePackage, catalog) {
        };
    }

    private static File resolveResourcesDir() {
        for (final String path : POSSIBLE_PATHS) {
            final File dir = new File(path);
            if (dir.isDirectory() && new File(dir, "oal").isDirectory()) {
                return dir;
            }
        }
        return null;
    }

    private static List<File> findYamlFiles(final File dir) {
        try (Stream<Path> stream = Files.walk(dir.toPath())) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> {
                    final String name = p.getFileName().toString();
                    return (name.endsWith(".yaml") || name.endsWith(".yml"))
                        && !name.endsWith(".data.yaml");
                })
                .map(Path::toFile)
                .sorted()
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to walk directory: {}", dir, e);
            return new ArrayList<>();
        }
    }

    /**
     * Replicates {@code MetricConvert.formatExp()}.
     */
    private static String formatExp(final String expPrefix,
                                    final String expSuffix,
                                    final String exp) {
        String ret = exp;
        if (expPrefix != null && !expPrefix.isEmpty()) {
            ret = String.format("(%s.%s)", StringUtils.substringBefore(exp, "."), expPrefix);
            final String after = StringUtils.substringAfter(exp, ".");
            if (after != null && !after.isEmpty()) {
                ret = ret + "." + after;
            }
        }
        if (expSuffix != null && !expSuffix.isEmpty()) {
            final int insertIdx = ret.indexOf('.');
            if (insertIdx > 0) {
                ret = ret.substring(0, insertIdx + 1) + expSuffix + "."
                    + ret.substring(insertIdx + 1);
            }
        }
        return ret;
    }

    private static Class<?> resolveInputType(
            final String yamlType,
            final String layerStr,
            final Map<Layer, LALSourceTypeProvider> spiProviders) {
        if (yamlType != null && !yamlType.isEmpty()) {
            try {
                return Class.forName(yamlType);
            } catch (ClassNotFoundException e) {
                log.warn("inputType class not found: {}", yamlType);
            }
        }
        if (layerStr != null) {
            final Layer layer = Layer.nameOf(layerStr);
            final LALSourceTypeProvider spi = spiProviders.get(layer);
            if (spi != null) {
                return spi.inputType();
            }
        }
        return null;
    }

    private static Class<?> resolveOutputType(
            final String yamlType,
            final String layerStr,
            final Map<Layer, LALSourceTypeProvider> spiProviders,
            final Map<String, Class<?>> outputBuilderNames) {
        if (yamlType != null && !yamlType.isEmpty()) {
            if (!yamlType.contains(".")) {
                final Class<?> byName = outputBuilderNames.get(yamlType);
                if (byName != null) {
                    return byName;
                }
            }
            try {
                return Class.forName(yamlType);
            } catch (ClassNotFoundException e) {
                log.warn("outputType class not found: {}", yamlType);
            }
        }
        if (layerStr != null) {
            final Layer layer = Layer.nameOf(layerStr);
            final LALSourceTypeProvider spi = spiProviders.get(layer);
            if (spi != null) {
                return spi.outputType();
            }
        }
        return null;
    }

    /**
     * Register all OAL source scopes and decorators.
     */
    private static void initializeScopes() {
        final DefaultScopeDefine.Listener listener = new DefaultScopeDefine.Listener();

        notifyClass(listener, SOURCE_PACKAGE, "Service");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstance");
        notifyClass(listener, SOURCE_PACKAGE, "Endpoint");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceRelation");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceRelation");
        notifyClass(listener, SOURCE_PACKAGE, "EndpointRelation");
        notifyClass(listener, SOURCE_PACKAGE, "DatabaseAccess");
        notifyClass(listener, SOURCE_PACKAGE, "CacheAccess");
        notifyClass(listener, SOURCE_PACKAGE, "MQAccess");
        notifyClass(listener, SOURCE_PACKAGE, "MQEndpointAccess");

        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceJVMCPU");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceJVMMemory");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceJVMMemoryPool");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceJVMGC");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceJVMThread");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceJVMClass");

        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceCLRCPU");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceCLRGC");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceCLRThread");

        notifyClass(listener, BROWSER_SOURCE_PACKAGE, "BrowserAppTraffic");
        notifyClass(listener, BROWSER_SOURCE_PACKAGE, "BrowserAppPageTraffic");
        notifyClass(listener, BROWSER_SOURCE_PACKAGE, "BrowserAppSingleVersionTraffic");
        notifyClass(listener, BROWSER_SOURCE_PACKAGE, "BrowserAppPerf");
        notifyClass(listener, BROWSER_SOURCE_PACKAGE, "BrowserAppPagePerf");
        notifyClass(listener, BROWSER_SOURCE_PACKAGE, "BrowserAppSingleVersionPerf");
        notifyClass(listener, BROWSER_SOURCE_PACKAGE, "BrowserAppResourcePerf");
        notifyClass(listener, BROWSER_SOURCE_PACKAGE, "BrowserAppWebInteractionPerf");
        notifyClass(listener, BROWSER_SOURCE_PACKAGE, "BrowserAppWebVitalsPerf");
        notifyClass(listener, BROWSER_SOURCE_PACKAGE, "BrowserErrorLog");

        notifyClass(listener, SOURCE_PACKAGE, "ServiceMesh");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceMeshService");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceMeshServiceInstance");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceMeshServiceRelation");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceMeshServiceInstanceRelation");

        notifyClass(listener, SOURCE_PACKAGE, "TCPService");
        notifyClass(listener, SOURCE_PACKAGE, "TCPServiceInstance");
        notifyClass(listener, SOURCE_PACKAGE, "TCPServiceRelation");
        notifyClass(listener, SOURCE_PACKAGE, "TCPServiceInstanceRelation");

        notifyClass(listener, SOURCE_PACKAGE, "EBPFProfilingSchedule");

        notifyClass(listener, SOURCE_PACKAGE, "CiliumService");
        notifyClass(listener, SOURCE_PACKAGE, "CiliumServiceInstance");
        notifyClass(listener, SOURCE_PACKAGE, "CiliumEndpoint");
        notifyClass(listener, SOURCE_PACKAGE, "CiliumServiceRelation");
        notifyClass(listener, SOURCE_PACKAGE, "CiliumServiceInstanceRelation");

        notifyClass(listener, SOURCE_PACKAGE, "K8SService");
        notifyClass(listener, SOURCE_PACKAGE, "K8SServiceInstance");
        notifyClass(listener, SOURCE_PACKAGE, "K8SEndpoint");
        notifyClass(listener, SOURCE_PACKAGE, "K8SServiceRelation");
        notifyClass(listener, SOURCE_PACKAGE, "K8SServiceInstanceRelation");

        notifyClass(listener, SOURCE_PACKAGE, "Process");
        notifyClass(listener, SOURCE_PACKAGE, "ProcessRelation");

        registerDecorator(SOURCE_PACKAGE, "ServiceDecorator");
        registerDecorator(SOURCE_PACKAGE, "EndpointDecorator");
        registerDecorator(SOURCE_PACKAGE, "K8SServiceDecorator");
        registerDecorator(SOURCE_PACKAGE, "K8SEndpointDecorator");
    }

    private static void notifyClass(final DefaultScopeDefine.Listener listener,
                                    final String packageName,
                                    final String className) {
        try {
            listener.notify(Class.forName(packageName + className));
        } catch (Exception e) {
            log.debug("Scope {} registration: {}", className, e.getMessage());
        }
    }

    private static void registerDecorator(final String packageName,
                                          final String decoratorName) {
        try {
            final SourceDecoratorManager manager = new SourceDecoratorManager();
            manager.addIfAsSourceDecorator(Class.forName(packageName + decoratorName));
        } catch (Exception e) {
            log.debug("Decorator {} registration: {}", decoratorName, e.getMessage());
        }
    }
}
