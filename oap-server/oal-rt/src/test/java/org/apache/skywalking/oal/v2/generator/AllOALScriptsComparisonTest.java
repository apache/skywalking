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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javassist.ClassPool;
import javassist.CtClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oal.rt.parser.AnalysisResult;
import org.apache.skywalking.oal.rt.parser.OALScripts;
import org.apache.skywalking.oal.rt.parser.ScriptParser;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.apache.skywalking.oap.server.core.analysis.SourceDecoratorManager;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive comparison test using all OAL scripts in the project.
 *
 * This test loads every OAL script from test resources and verifies that
 * V1 and V2 engines generate byte-for-byte identical bytecode.
 */
@Slf4j
public class AllOALScriptsComparisonTest {

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

    @BeforeAll
    public static void initializeScopes() {
        // Initialize all scope classes needed by the OAL scripts
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

        // JVM sources (for java-agent.oal)
        notifyClass(listener, "ServiceInstanceJVMCPU");
        notifyClass(listener, "ServiceInstanceJVMMemory");
        notifyClass(listener, "ServiceInstanceJVMMemoryPool");
        notifyClass(listener, "ServiceInstanceJVMGC");
        notifyClass(listener, "ServiceInstanceJVMThread");
        notifyClass(listener, "ServiceInstanceJVMClass");

        // CLR sources (for dotnet-agent.oal)
        notifyClass(listener, "ServiceInstanceCLRCPU");
        notifyClass(listener, "ServiceInstanceCLRGC");
        notifyClass(listener, "ServiceInstanceCLRThread");

        // Browser sources (different package: browser.source)
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

        // Register decorators (for core.oal, ebpf.oal)
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
        } catch (ClassNotFoundException e) {
            log.debug("Decorator class not found: {}, skipping", decoratorName);
        } catch (IllegalAccessException | InstantiationException e) {
            log.debug("Decorator {} instantiation error: {}", decoratorName, e.getMessage());
        } catch (RuntimeException e) {
            // Decorator may already be registered or other error
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
        } catch (ClassNotFoundException e) {
            log.debug("Source class not found: {}{}, skipping", packageName, className);
        } catch (RuntimeException e) {
            // Scope may already be registered by other tests (UnexpectedException)
            log.debug("Scope {} already registered or error: {}", className, e.getMessage());
        }
    }

    @Test
    public void compareAllOALScripts() throws Exception {
        int totalMetrics = 0;
        int passedMetrics = 0;
        int skippedScripts = 0;
        List<String> failures = new ArrayList<>();
        List<String> successfulScripts = new ArrayList<>();

        for (String scriptPath : OAL_SCRIPTS) {
            log.info("=== Testing {} ===", scriptPath);

            String oalContent = loadOALScript(scriptPath);
            if (oalContent == null) {
                log.warn("Script not found: {}, skipping", scriptPath);
                skippedScripts++;
                continue;
            }

            try {
                ComparisonResult result = compareOALScript(scriptPath, oalContent);
                totalMetrics += result.totalMetrics;
                passedMetrics += result.passedMetrics;
                failures.addAll(result.failures);

                if (result.passedMetrics == result.totalMetrics && result.totalMetrics > 0) {
                    successfulScripts.add(scriptPath + " (" + result.totalMetrics + " metrics)");
                }

                log.info("{}: {}/{} metrics passed",
                    scriptPath, result.passedMetrics, result.totalMetrics);
            } catch (Exception e) {
                // Scripts that fail to parse due to missing decorators/scopes are skipped
                failures.add("SKIPPED " + scriptPath + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                skippedScripts++;
            }
        }

        log.info("=== Summary ===");
        log.info("Successful scripts: {}", successfulScripts.size());
        for (String script : successfulScripts) {
            log.info("  - {}", script);
        }
        log.info("Total metrics compared: {}", totalMetrics);
        log.info("Passed metrics: {}", passedMetrics);
        log.info("Skipped scripts (missing deps): {}", skippedScripts);

        if (!failures.isEmpty()) {
            log.error("Failures:");
            for (String failure : failures) {
                log.error("  - {}", failure);
            }
        }

        // Build summary for assertion message
        StringBuilder summary = new StringBuilder();
        summary.append("\n=== V1 vs V2 OAL Comparison Summary ===\n");
        summary.append("Successful scripts: ").append(successfulScripts.size()).append("\n");
        for (String script : successfulScripts) {
            summary.append("  - ").append(script).append("\n");
        }
        summary.append("Total metrics compared: ").append(totalMetrics).append("\n");
        summary.append("Passed metrics: ").append(passedMetrics).append("\n");
        summary.append("Skipped scripts: ").append(skippedScripts).append("\n");
        if (!failures.isEmpty()) {
            summary.append("Failures: ").append(failures).append("\n");
        }

        // At least some metrics should be tested
        Assertions.assertTrue(totalMetrics > 0, "At least some metrics should be testable. " + summary);

        // All testable metrics must produce identical bytecode
        Assertions.assertEquals(totalMetrics, passedMetrics,
            "All testable metrics should produce identical bytecode. " + summary);

        // All scripts should be testable now that we've registered scopes and decorators
        Assertions.assertEquals(0, skippedScripts,
            "All scripts should be testable. Skips: " + failures);
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

    private ComparisonResult compareOALScript(String scriptPath, String oalContent) throws Exception {
        ComparisonResult result = new ComparisonResult();

        // Parse with V1
        ClassPool v1Pool = new ClassPool(true);
        ScriptParser v1Parser = ScriptParser.createFromScriptText(oalContent, SOURCE_PACKAGE);
        OALScripts v1Scripts = v1Parser.parse();
        List<AnalysisResult> v1Results = v1Scripts.getMetricsStmts();

        // Parse with V2
        ClassPool v2Pool = new ClassPool(true);
        OALScriptParserV2 v2Parser = OALScriptParserV2.parse(oalContent);
        List<MetricDefinition> v2Metrics = v2Parser.getMetrics();

        // Verify same number of metrics parsed
        Assertions.assertEquals(v1Results.size(), v2Metrics.size(),
            scriptPath + ": V1 and V2 should parse same number of metrics");

        result.totalMetrics = v1Results.size();

        // Enrich V1 results
        for (AnalysisResult metricsStmt : v1Results) {
            metricsStmt.setMetricsClassPackage(METRICS_PACKAGE);
            metricsStmt.setSourcePackage(SOURCE_PACKAGE);
        }

        // Enrich V2 models
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        List<CodeGenModel> v2Models = new ArrayList<>();
        for (MetricDefinition metric : v2Metrics) {
            v2Models.add(enricher.enrich(metric));
        }

        // Generate and compare each metric
        TestOALDefine define = new TestOALDefine();
        V1ClassGenerator v1Gen = new V1ClassGenerator(define, v1Pool);
        V2ClassGenerator v2Gen = new V2ClassGenerator(define, v2Pool);

        for (int i = 0; i < v1Results.size(); i++) {
            AnalysisResult v1Result = v1Results.get(i);
            CodeGenModel v2Model = v2Models.get(i);

            String metricsName = v1Result.getMetricsName();

            try {
                // Generate metrics classes
                CtClass v1MetricsClass = v1Gen.generateMetricsCtClass(v1Result);
                CtClass v2MetricsClass = v2Gen.generateMetricsCtClass(v2Model);

                // Compare bytecode
                boolean match = compareBytecode(v1MetricsClass, v2MetricsClass);
                if (match) {
                    result.passedMetrics++;
                } else {
                    result.failures.add(scriptPath + ":" + metricsName + " - bytecode mismatch");
                }
            } catch (Exception e) {
                result.failures.add(scriptPath + ":" + metricsName + " - " + e.getMessage());
            }
        }

        return result;
    }

    private boolean compareBytecode(CtClass v1Class, CtClass v2Class) throws Exception {
        ByteArrayOutputStream v1Baos = new ByteArrayOutputStream();
        ByteArrayOutputStream v2Baos = new ByteArrayOutputStream();

        v1Class.toBytecode(new DataOutputStream(v1Baos));
        v2Class.toBytecode(new DataOutputStream(v2Baos));

        byte[] v1Bytecode = v1Baos.toByteArray();
        byte[] v2Bytecode = v2Baos.toByteArray();

        return Arrays.equals(v1Bytecode, v2Bytecode);
    }

    private static class ComparisonResult {
        int totalMetrics = 0;
        int passedMetrics = 0;
        List<String> failures = new ArrayList<>();
    }

    private static class TestOALDefine extends OALDefine {
        protected TestOALDefine() {
            super("test.oal", SOURCE_PACKAGE);
        }
    }
}
