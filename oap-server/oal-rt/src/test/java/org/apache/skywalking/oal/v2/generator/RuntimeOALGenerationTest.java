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

package org.apache.skywalking.oal.v2.generator;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javassist.ClassPool;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.apache.skywalking.oap.server.core.analysis.SourceDecoratorManager;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test OAL V2 generation with all runtime OAL scripts.
 *
 * <p>This test loads all OAL scripts defined via OALDefine implementations and generates
 * classes exactly as the runtime does. Generated classes are written to target/generated-test-sources
 * for IDE inspection.
 *
 * <p>Key behaviors:
 * <ul>
 *   <li>Scans all OALDefine implementations to map OAL scripts</li>
 *   <li>Verifies OAL scripts exist and can be parsed</li>
 *   <li>Detects dispatcher conflicts when same source appears in multiple OAL files</li>
 *   <li>Generates classes with correct package structure matching runtime</li>
 *   <li>Allows developers to inspect generated sources via IDE decompiler</li>
 * </ul>
 */
@Slf4j
public class RuntimeOALGenerationTest {

    private static final String SOURCE_PACKAGE = "org.apache.skywalking.oap.server.core.source.";
    private static final String BROWSER_SOURCE_PACKAGE = "org.apache.skywalking.oap.server.core.browser.source.";
    private static final String METRICS_PACKAGE = "org.apache.skywalking.oap.server.core.source.oal.rt.metrics.";

    private static final String[] POSSIBLE_PATHS = {
        "oap-server/server-starter/src/main/resources/",
        "../server-starter/src/main/resources/",
        "../../server-starter/src/main/resources/"
    };

    /**
     * All known OALDefine implementations mapped to their OAL script paths.
     */
    private static final Map<String, OALDefine> OAL_DEFINES = new HashMap<>();

    @BeforeAll
    public static void setup() {
        // Initialize all scopes
        initializeScopes();

        // Register all known OALDefine implementations (matching runtime OALDefine classes)
        // CoreOALDefine - no catalog
        registerOALDefine("core", createOALDefine("oal/core.oal", SOURCE_PACKAGE, ""));
        // JVMOALDefine - no catalog
        registerOALDefine("java-agent", createOALDefine("oal/java-agent.oal", SOURCE_PACKAGE, ""));
        // CLROALDefine - no catalog
        registerOALDefine("dotnet-agent", createOALDefine("oal/dotnet-agent.oal", SOURCE_PACKAGE, ""));
        // BrowserOALDefine - different source package, no catalog
        registerOALDefine("browser", createOALDefine("oal/browser.oal", BROWSER_SOURCE_PACKAGE, ""));
        // MeshOALDefine - catalog: "ServiceMesh"
        registerOALDefine("mesh", createOALDefine("oal/mesh.oal", SOURCE_PACKAGE, "ServiceMesh"));
        // TCPOALDefine - catalog: "EnvoyTCP"
        registerOALDefine("tcp", createOALDefine("oal/tcp.oal", SOURCE_PACKAGE, "EnvoyTCP"));
        // EBPFOALDefine - no catalog
        registerOALDefine("ebpf", createOALDefine("oal/ebpf.oal", SOURCE_PACKAGE, ""));
        // CiliumOALDefine - no catalog
        registerOALDefine("cilium", createOALDefine("oal/cilium.oal", SOURCE_PACKAGE, ""));
        // DisableOALDefine - no catalog
        registerOALDefine("disable", createOALDefine("oal/disable.oal", SOURCE_PACKAGE, ""));

        // Set generated file path for IDE inspection
        OALClassGeneratorV2.setGeneratedFilePath("target/test-classes");
    }

    private static void registerOALDefine(String name, OALDefine define) {
        OAL_DEFINES.put(name, define);
    }

    private static OALDefine createOALDefine(String configFile, String sourcePackage, String catalog) {
        return new OALDefine(configFile, sourcePackage, catalog) {
        };
    }

    private static void initializeScopes() {
        DefaultScopeDefine.Listener listener = new DefaultScopeDefine.Listener();

        // Core sources
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

        // JVM sources
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceJVMCPU");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceJVMMemory");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceJVMMemoryPool");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceJVMGC");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceJVMThread");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceJVMClass");

        // CLR sources
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceCLRCPU");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceCLRGC");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceInstanceCLRThread");

        // Browser sources
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

        // Mesh sources
        notifyClass(listener, SOURCE_PACKAGE, "ServiceMesh");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceMeshService");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceMeshServiceInstance");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceMeshServiceRelation");
        notifyClass(listener, SOURCE_PACKAGE, "ServiceMeshServiceInstanceRelation");

        // TCP sources
        notifyClass(listener, SOURCE_PACKAGE, "TCPService");
        notifyClass(listener, SOURCE_PACKAGE, "TCPServiceInstance");
        notifyClass(listener, SOURCE_PACKAGE, "TCPServiceRelation");
        notifyClass(listener, SOURCE_PACKAGE, "TCPServiceInstanceRelation");

        // eBPF sources
        notifyClass(listener, SOURCE_PACKAGE, "EBPFProfilingSchedule");

        // Cilium sources
        notifyClass(listener, SOURCE_PACKAGE, "CiliumService");
        notifyClass(listener, SOURCE_PACKAGE, "CiliumServiceInstance");
        notifyClass(listener, SOURCE_PACKAGE, "CiliumEndpoint");
        notifyClass(listener, SOURCE_PACKAGE, "CiliumServiceRelation");
        notifyClass(listener, SOURCE_PACKAGE, "CiliumServiceInstanceRelation");

        // K8s sources
        notifyClass(listener, SOURCE_PACKAGE, "K8SService");
        notifyClass(listener, SOURCE_PACKAGE, "K8SServiceInstance");
        notifyClass(listener, SOURCE_PACKAGE, "K8SEndpoint");
        notifyClass(listener, SOURCE_PACKAGE, "K8SServiceRelation");
        notifyClass(listener, SOURCE_PACKAGE, "K8SServiceInstanceRelation");

        // Process sources
        notifyClass(listener, SOURCE_PACKAGE, "Process");
        notifyClass(listener, SOURCE_PACKAGE, "ProcessRelation");

        // Register decorators
        registerDecorator(SOURCE_PACKAGE, "ServiceDecorator");
        registerDecorator(SOURCE_PACKAGE, "EndpointDecorator");
        registerDecorator(SOURCE_PACKAGE, "K8SServiceDecorator");
        registerDecorator(SOURCE_PACKAGE, "K8SEndpointDecorator");
    }

    private static void notifyClass(DefaultScopeDefine.Listener listener, String packageName, String className) {
        try {
            Class<?> clazz = Class.forName(packageName + className);
            listener.notify(clazz);
        } catch (Exception e) {
            log.debug("Scope {} registration: {}", className, e.getMessage());
        }
    }

    private static void registerDecorator(String packageName, String decoratorName) {
        try {
            Class<?> clazz = Class.forName(packageName + decoratorName);
            SourceDecoratorManager manager = new SourceDecoratorManager();
            manager.addIfAsSourceDecorator(clazz);
        } catch (Exception e) {
            log.debug("Decorator {} registration: {}", decoratorName, e.getMessage());
        }
    }

    /**
     * Test that all OAL scripts can be loaded, parsed, and classes generated.
     *
     * <p>This test validates:
     * <ul>
     *   <li>All OAL script files exist</li>
     *   <li>All scripts parse successfully</li>
     *   <li>Classes can be generated without conflicts</li>
     *   <li>Generated classes match runtime structure</li>
     * </ul>
     */
    @Test
    public void testGenerateAllRuntimeOALScripts() throws Exception {
        // Single ClassPool for all generated classes
        // No conflicts because catalog prefix creates unique dispatcher class names
        // (e.g., ServiceDispatcher vs ServiceMeshServiceDispatcher)
        ClassPool classPool = new ClassPool(true);

        int totalMetrics = 0;
        int totalDispatchers = 0;
        int totalGeneratedClasses = 0;
        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, OALDefine> entry : OAL_DEFINES.entrySet()) {
            String oalName = entry.getKey();
            OALDefine define = entry.getValue();

            File oalFile = findOALFile(define.getConfigFile());
            assertNotNull(oalFile, "OAL file not found: " + define.getConfigFile() +
                ". Tried paths: " + String.join(", ", POSSIBLE_PATHS));

            try (FileReader reader = new FileReader(oalFile)) {
                // Parse OAL script
                OALScriptParserV2 parser = OALScriptParserV2.parse(reader, define.getConfigFile());
                List<MetricDefinition> metrics = parser.getMetrics();
                List<String> disabledSources = parser.getDisabledSources();
                totalMetrics += metrics.size();

                // Handle OAL files with only disable statements (no metrics)
                if (metrics.isEmpty()) {
                    log.info("{}: 0 metrics, {} disabled sources",
                        oalName, disabledSources.size());
                    // Still need to process disabled sources
                    if (!disabledSources.isEmpty()) {
                        OALClassGeneratorV2 generator = new OALClassGeneratorV2(define, classPool);
                        generator.generateClassAtRuntime(
                            new ArrayList<>(), disabledSources, new ArrayList<>(), new ArrayList<>());
                    }
                    continue;
                }

                // Enrich metrics to build code generation models
                MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(
                    define.getSourcePackage(),
                    METRICS_PACKAGE
                );

                List<CodeGenModel> models = new ArrayList<>();
                for (MetricDefinition metric : metrics) {
                    CodeGenModel model = enricher.enrich(metric);
                    models.add(model);
                }

                // Create generator with OALDefine (catalog determines dispatcher class name prefix)
                OALClassGeneratorV2 generator = new OALClassGeneratorV2(define, classPool);
                generator.setOpenEngineDebug(true);
                generator.setStorageBuilderFactory(new StorageBuilderFactory.Default());

                // Generate classes
                List<Class> metricsClasses = new ArrayList<>();
                List<Class> dispatcherClasses = new ArrayList<>();

                generator.generateClassAtRuntime(models, disabledSources, metricsClasses, dispatcherClasses);

                totalDispatchers += dispatcherClasses.size();
                totalGeneratedClasses += metricsClasses.size() + dispatcherClasses.size();

                // Extract catalog name for logging
                String catalogInfo = define.getDynamicDispatcherClassPackage()
                    .replace("org.apache.skywalking.oap.server.core.source.oal.rt.dispatcher.", "");
                if (catalogInfo.isEmpty()) {
                    catalogInfo = "(none)";
                }

                log.info("{}: {} metrics -> {} metrics classes, {} dispatchers (catalog: {})",
                    oalName, metrics.size(), metricsClasses.size(), dispatcherClasses.size(), catalogInfo);

            } catch (Exception e) {
                errors.add(oalName + ": " + e.getMessage());
                log.error("Failed to generate classes for {}: {}", oalName, e.getMessage(), e);
            }
        }

        // Report summary
        log.info("=== Runtime OAL Generation Summary ===");
        log.info("Total OAL scripts processed: {}", OAL_DEFINES.size());
        log.info("Total metrics parsed: {}", totalMetrics);
        log.info("Total dispatchers generated: {}", totalDispatchers);
        log.info("Total classes generated: {}", totalGeneratedClasses);
        log.info("Generated files written to: target/test-classes/");

        if (!errors.isEmpty()) {
            log.error("Errors encountered:");
            errors.forEach(e -> log.error("  - {}", e));
            fail("Errors occurred during OAL class generation: " + errors);
        }

        assertTrue(totalMetrics > 100, "Should have at least 100 metrics across all OAL files, got: " + totalMetrics);
        assertTrue(totalGeneratedClasses > 100, "Should generate at least 100 classes, got: " + totalGeneratedClasses);
    }

    /**
     * Find OAL script file using multiple search paths.
     *
     * @param scriptPath Path from OALDefine.getConfigFile()
     * @return File if found, null otherwise
     */
    private File findOALFile(String scriptPath) {
        for (String basePath : POSSIBLE_PATHS) {
            File file = new File(basePath + scriptPath);
            if (file.exists() && file.isFile()) {
                log.debug("Found OAL file at: {}", file.getAbsolutePath());
                return file;
            }
        }
        return null;
    }
}
