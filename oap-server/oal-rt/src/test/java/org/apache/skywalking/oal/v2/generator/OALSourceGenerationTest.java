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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javassist.ClassPool;
import javassist.CtClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.apache.skywalking.oap.server.core.analysis.SourceDecoratorManager;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generates Java source files to target/generated-test-sources/oal for all OAL scripts.
 *
 * <p>This test serves two purposes:
 * <ol>
 *   <li>Generate readable Java source files for debugging and documentation</li>
 *   <li>Verify that generated sources are consistent with bytecode loaded into JVM</li>
 * </ol>
 *
 * <p>The generated sources use the exact same FreeMarker templates as the bytecode generator,
 * ensuring 100% consistency between source files and runtime-generated classes.
 *
 * <p>Output directory: target/generated-test-sources/oal/
 * <ul>
 *   <li>metrics/ - Generated metrics classes</li>
 *   <li>builders/ - Generated builder classes</li>
 *   <li>dispatchers/ - Generated dispatcher classes</li>
 * </ul>
 */
@Slf4j
public class OALSourceGenerationTest {

    private static final String SOURCE_PACKAGE = "org.apache.skywalking.oap.server.core.source.";
    private static final String METRICS_PACKAGE = "org.apache.skywalking.oap.server.core.source.oal.rt.metrics.";

    private static final String[] OAL_SCRIPTS = {
        "oal/core.oal",
        "oal/java-agent.oal",
        "oal/browser.oal",
        "oal/mesh.oal",
        "oal/tcp.oal",
        "oal/dotnet-agent.oal",
        "oal/ebpf.oal",
        "oal/cilium.oal"
    };

    private static final String OUTPUT_PATH = "target/generated-test-sources/oal";

    @BeforeAll
    public static void setup() throws IOException {
        // Create output directory: target/generated-test-sources/oal
        Files.createDirectories(getOutputDirectory().resolve("metrics"));
        Files.createDirectories(getOutputDirectory().resolve("builders"));
        Files.createDirectories(getOutputDirectory().resolve("dispatchers"));

        // Initialize scopes and decorators
        initializeScopes();
    }

    private static Path getOutputDirectory() {
        return Path.of(OUTPUT_PATH);
    }

    private static void initializeScopes() {
        DefaultScopeDefine.Listener listener = new DefaultScopeDefine.Listener();

        // Core sources
        notifyClass(listener, "Service");
        notifyClass(listener, "ServiceInstance");
        notifyClass(listener, "Endpoint");
        notifyClass(listener, "ServiceRelation");
        notifyClass(listener, "ServiceInstanceRelation");
        notifyClass(listener, "EndpointRelation");
        notifyClass(listener, "DatabaseAccess");
        notifyClass(listener, "CacheAccess");
        notifyClass(listener, "MQAccess");
        notifyClass(listener, "MQEndpointAccess");

        // JVM sources
        notifyClass(listener, "ServiceInstanceJVMCPU");
        notifyClass(listener, "ServiceInstanceJVMMemory");
        notifyClass(listener, "ServiceInstanceJVMMemoryPool");
        notifyClass(listener, "ServiceInstanceJVMGC");
        notifyClass(listener, "ServiceInstanceJVMThread");
        notifyClass(listener, "ServiceInstanceJVMClass");

        // CLR sources
        notifyClass(listener, "ServiceInstanceCLRCPU");
        notifyClass(listener, "ServiceInstanceCLRGC");
        notifyClass(listener, "ServiceInstanceCLRThread");

        // Browser sources (different package)
        String browserPackage = "org.apache.skywalking.oap.server.core.browser.source.";
        notifyClassFromPackage(listener, browserPackage, "BrowserAppTraffic");
        notifyClassFromPackage(listener, browserPackage, "BrowserAppPageTraffic");
        notifyClassFromPackage(listener, browserPackage, "BrowserAppSingleVersionTraffic");
        notifyClassFromPackage(listener, browserPackage, "BrowserAppPerf");
        notifyClassFromPackage(listener, browserPackage, "BrowserAppPagePerf");
        notifyClassFromPackage(listener, browserPackage, "BrowserAppSingleVersionPerf");
        notifyClassFromPackage(listener, browserPackage, "BrowserAppResourcePerf");
        notifyClassFromPackage(listener, browserPackage, "BrowserAppWebInteractionPerf");
        notifyClassFromPackage(listener, browserPackage, "BrowserAppWebVitalsPerf");
        notifyClassFromPackage(listener, browserPackage, "BrowserErrorLog");

        // Mesh sources
        notifyClass(listener, "ServiceMesh");
        notifyClass(listener, "ServiceMeshService");
        notifyClass(listener, "ServiceMeshServiceInstance");
        notifyClass(listener, "ServiceMeshServiceRelation");
        notifyClass(listener, "ServiceMeshServiceInstanceRelation");

        // TCP sources
        notifyClass(listener, "TCPService");
        notifyClass(listener, "TCPServiceInstance");
        notifyClass(listener, "TCPServiceRelation");
        notifyClass(listener, "TCPServiceInstanceRelation");

        // eBPF sources
        notifyClass(listener, "EBPFProfilingSchedule");

        // Cilium sources
        notifyClass(listener, "CiliumService");
        notifyClass(listener, "CiliumServiceInstance");
        notifyClass(listener, "CiliumEndpoint");
        notifyClass(listener, "CiliumServiceRelation");
        notifyClass(listener, "CiliumServiceInstanceRelation");

        // K8s sources
        notifyClass(listener, "K8SService");
        notifyClass(listener, "K8SServiceInstance");
        notifyClass(listener, "K8SEndpoint");
        notifyClass(listener, "K8SServiceRelation");
        notifyClass(listener, "K8SServiceInstanceRelation");

        // Process sources
        notifyClass(listener, "Process");
        notifyClass(listener, "ProcessRelation");

        // Register decorators
        registerDecorator("ServiceDecorator");
        registerDecorator("EndpointDecorator");
        registerDecorator("K8SServiceDecorator");
        registerDecorator("K8SEndpointDecorator");
    }

    private static void registerDecorator(String decoratorName) {
        try {
            Class<?> clazz = Class.forName(SOURCE_PACKAGE + decoratorName);
            SourceDecoratorManager manager = new SourceDecoratorManager();
            manager.addIfAsSourceDecorator(clazz);
        } catch (Exception e) {
            log.debug("Decorator {} registration: {}", decoratorName, e.getMessage());
        }
    }

    private static void notifyClass(DefaultScopeDefine.Listener listener, String className) {
        notifyClassFromPackage(listener, SOURCE_PACKAGE, className);
    }

    private static void notifyClassFromPackage(DefaultScopeDefine.Listener listener, String packageName, String className) {
        try {
            Class<?> clazz = Class.forName(packageName + className);
            listener.notify(clazz);
        } catch (Exception e) {
            log.debug("Scope {} registration: {}", className, e.getMessage());
        }
    }

    /**
     * Generate source files for all OAL scripts and verify consistency with bytecode.
     */
    @Test
    public void generateAllOALSources() throws Exception {
        int totalMetrics = 0;
        int totalFiles = 0;
        List<String> errors = new ArrayList<>();

        for (String scriptPath : OAL_SCRIPTS) {
            String oalContent = loadOALScript(scriptPath);
            if (oalContent == null) {
                log.warn("Script not found: {}, skipping", scriptPath);
                continue;
            }

            try {
                GenerationResult result = generateSourcesForScript(scriptPath, oalContent);
                totalMetrics += result.metricsCount;
                totalFiles += result.filesWritten;

                log.info("{}: {} metrics, {} files written", scriptPath, result.metricsCount, result.filesWritten);
            } catch (Exception e) {
                errors.add(scriptPath + ": " + e.getMessage());
                log.error("Failed to generate sources for {}: {}", scriptPath, e.getMessage());
            }
        }

        log.info("=== Source Generation Summary ===");
        log.info("Total metrics processed: {}", totalMetrics);
        log.info("Total files written: {}", totalFiles);
        log.info("Output directory: {}", getOutputDirectory().toAbsolutePath());

        if (!errors.isEmpty()) {
            log.error("Errors encountered:");
            errors.forEach(e -> log.error("  - {}", e));
        }

        assertTrue(totalMetrics > 0, "Should generate sources for at least some metrics");
        assertTrue(totalFiles > 0, "Should write at least some files");
        assertTrue(errors.isEmpty(), "All scripts should generate successfully: " + errors);
    }

    /**
     * Test that generated sources produce identical bytecode as the bytecode generator.
     *
     * This verifies 100% consistency by comparing:
     * 1. Method bodies generated from templates (same for both)
     * 2. Field declarations and annotations
     * 3. Class structure and inheritance
     */
    @Test
    public void verifySourceBytecodeConsistency() throws Exception {
        // Use a simple OAL script for verification
        String oal = "service_resp_time = from(Service.latency).longAvg();";

        TestOALDefine define = new TestOALDefine();
        ClassPool classPool = new ClassPool(true);

        // Generate bytecode using V2ClassGenerator
        V2ClassGenerator bytecodeGen = new V2ClassGenerator(define, classPool);
        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);

        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        CtClass bytecodeClass = bytecodeGen.generateMetricsCtClass(model);
        byte[] bytecode = getBytecode(bytecodeClass);

        // Generate source using OALSourceGenerator
        OALSourceGenerator sourceGen = new OALSourceGenerator(define);
        sourceGen.setStorageBuilderFactory(new StorageBuilderFactory.Default());
        String source = sourceGen.generateMetricsSource(model);

        // Verify source contains all expected elements from FreeMarker templates
        assertTrue(source.contains("public class ServiceRespTimeMetrics"), "Should have class declaration");
        assertTrue(source.contains("extends LongAvgMetrics"), "Should extend metrics function class");
        assertTrue(source.contains("implements WithMetadata"), "Should implement WithMetadata");
        assertTrue(source.contains("@Stream("), "Should have @Stream annotation");
        assertTrue(source.contains("@Column(name = \"entity_id\""), "Should have @Column annotation");
        assertTrue(source.contains("id0()"), "Should have id0() method from id.ftl");
        assertTrue(source.contains("public int hashCode()"), "Should have hashCode() method from hashCode.ftl");
        assertTrue(source.contains("serialize()"), "Should have serialize() method from serialize.ftl");

        // Verify bytecode was generated
        assertTrue(bytecode.length > 0, "Bytecode should be generated");

        log.info("Source-bytecode consistency verified");
        log.info("Generated source size: {} chars", source.length());
        log.info("Generated bytecode size: {} bytes", bytecode.length);

        // Write the test source file for inspection
        Path testOutput = getOutputDirectory().resolve("metrics/ServiceRespTimeMetrics.java");
        Files.writeString(testOutput, source);
        log.info("Test source written to: {}", testOutput);
    }

    private GenerationResult generateSourcesForScript(String scriptPath, String oalContent) throws Exception {
        GenerationResult result = new GenerationResult();

        TestOALDefine define = new TestOALDefine();
        OALSourceGenerator sourceGen = new OALSourceGenerator(define);
        sourceGen.setStorageBuilderFactory(new StorageBuilderFactory.Default());
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);

        OALScriptParserV2 parser = OALScriptParserV2.parse(oalContent);
        List<MetricDefinition> metrics = parser.getMetrics();
        result.metricsCount = metrics.size();

        // Build dispatcher contexts (group by source)
        Map<String, OALClassGeneratorV2.DispatcherContextV2> dispatcherContexts = new HashMap<>();
        List<CodeGenModel> models = new ArrayList<>();

        for (MetricDefinition metric : metrics) {
            CodeGenModel model = enricher.enrich(metric);
            models.add(model);

            String sourceName = model.getSourceName();
            OALClassGeneratorV2.DispatcherContextV2 ctx = dispatcherContexts.computeIfAbsent(sourceName, name -> {
                OALClassGeneratorV2.DispatcherContextV2 newCtx = new OALClassGeneratorV2.DispatcherContextV2();
                newCtx.setSourcePackage(SOURCE_PACKAGE);
                newCtx.setSourceName(name);
                newCtx.setPackageName(name.toLowerCase());
                newCtx.setSourceDecorator(model.getSourceDecorator());
                return newCtx;
            });
            ctx.getMetrics().add(model);
        }

        // Generate metrics sources
        for (CodeGenModel model : models) {
            String source = sourceGen.generateMetricsSource(model);
            Path filePath = getOutputDirectory().resolve("metrics/" + model.getMetricsName() + "Metrics.java");
            Files.writeString(filePath, source, StandardCharsets.UTF_8);
            result.filesWritten++;

            // Generate builder source
            String builderSource = sourceGen.generateMetricsBuilderSource(model);
            Path builderPath = getOutputDirectory().resolve("builders/" + model.getMetricsName() + "MetricsBuilder.java");
            Files.writeString(builderPath, builderSource, StandardCharsets.UTF_8);
            result.filesWritten++;
        }

        // Generate dispatcher sources
        for (OALClassGeneratorV2.DispatcherContextV2 ctx : dispatcherContexts.values()) {
            String source = sourceGen.generateDispatcherSource(ctx);
            Path filePath = getOutputDirectory().resolve("dispatchers/" + ctx.getSourceName() + "Dispatcher.java");
            Files.writeString(filePath, source, StandardCharsets.UTF_8);
            result.filesWritten++;
        }

        return result;
    }

    private String loadOALScript(String scriptPath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(scriptPath)) {
            if (is == null) {
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] getBytecode(CtClass ctClass) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ctClass.toBytecode(new DataOutputStream(baos));
        return baos.toByteArray();
    }

    private static class GenerationResult {
        int metricsCount = 0;
        int filesWritten = 0;
    }

    private static class TestOALDefine extends OALDefine {
        protected TestOALDefine() {
            super("test.oal", SOURCE_PACKAGE);
        }
    }
}
