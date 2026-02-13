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

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.CountMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongAvgMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MaxLongMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.SumMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;
import org.apache.skywalking.oap.server.core.oal.rt.OALCompileException;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.Endpoint;
import org.apache.skywalking.oap.server.core.source.K8SService;
import org.apache.skywalking.oap.server.core.source.K8SServiceInstance;
import org.apache.skywalking.oap.server.core.source.Service;
import org.apache.skywalking.oap.server.core.source.ServiceRelation;
import org.apache.skywalking.oap.server.core.source.TCPService;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for OAL V2 code generation.
 *
 * Tests the full pipeline:
 * 1. Parse OAL scripts using OALScriptParserV2
 * 2. Enrich models using MetricDefinitionEnricher
 * 3. Generate Java classes using FreeMarker templates
 * 4. Load generated classes in JVM and verify they work correctly
 *
 * Note: All tests generating Service-based metrics are consolidated into a single test
 * to avoid Javassist "frozen class" issues with the ServiceDispatcher.
 */
public class OALClassGeneratorV2Test {

    private static final String SOURCE_PACKAGE = "org.apache.skywalking.oap.server.core.source.";
    private static final String METRICS_PACKAGE = "org.apache.skywalking.oap.server.core.source.oal.rt.metrics.";
    private static final String METRICS_BUILDER_PACKAGE = "org.apache.skywalking.oap.server.core.source.oal.rt.metrics.builder.";

    @BeforeAll
    public static void initializeScopes() {
        try {
            DefaultScopeDefine.Listener listener = new DefaultScopeDefine.Listener();
            listener.notify(Service.class);
            listener.notify(Endpoint.class);
            listener.notify(ServiceRelation.class);
            listener.notify(K8SService.class);
            listener.notify(K8SServiceInstance.class);
            listener.notify(TCPService.class);
        } catch (RuntimeException e) {
            // Scopes may already be registered by other tests
        }
    }

    /**
     * Comprehensive test for Service-based metrics generation.
     *
     * This test validates:
     * - Metrics class generation with different aggregation functions (longAvg, count, sum, max)
     * - Dispatcher class generation with multiple doMetrics methods
     * - Builder class generation
     * - All generated methods (id, hashCode, equals, serialize, deserialize, toHour, toDay, getMeta)
     * - Class instantiation and method invocation
     */
    @Test
    public void testServiceMetricsGeneration() throws Exception {
        // Use test_ prefix to avoid conflicts with RuntimeOALGenerationTest
        String oal = "test_service_resp_time = from(Service.latency).longAvg();\n" +
            "test_service_calls = from(Service.*).count();\n" +
            "test_service_latency_sum = from(Service.latency).sum();\n" +
            "test_service_latency_max = from(Service.latency).max();";

        OALClassGeneratorV2 generator = createGenerator();
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);

        List<Class> metricsClasses = new ArrayList<>();
        List<Class> dispatcherClasses = new ArrayList<>();

        generateClasses(oal, generator, enricher, metricsClasses, dispatcherClasses);

        // Verify 4 metrics classes generated
        assertEquals(4, metricsClasses.size());

        // Verify 1 dispatcher class for Service source
        assertEquals(1, dispatcherClasses.size());

        // Find specific metrics classes
        Class<?> respTimeClass = findClassBySimpleName(metricsClasses, "TestServiceRespTimeMetrics");
        Class<?> callsClass = findClassBySimpleName(metricsClasses, "TestServiceCallsMetrics");
        Class<?> sumClass = findClassBySimpleName(metricsClasses, "TestServiceLatencySumMetrics");
        Class<?> maxClass = findClassBySimpleName(metricsClasses, "TestServiceLatencyMaxMetrics");

        assertNotNull(respTimeClass, "TestServiceRespTimeMetrics should be generated");
        assertNotNull(callsClass, "TestServiceCallsMetrics should be generated");
        assertNotNull(sumClass, "TestServiceLatencySumMetrics should be generated");
        assertNotNull(maxClass, "TestServiceLatencyMaxMetrics should be generated");

        // Verify inheritance
        assertTrue(LongAvgMetrics.class.isAssignableFrom(respTimeClass));
        assertTrue(CountMetrics.class.isAssignableFrom(callsClass));
        assertTrue(SumMetrics.class.isAssignableFrom(sumClass));
        assertTrue(MaxLongMetrics.class.isAssignableFrom(maxClass));

        // Verify interfaces
        assertTrue(WithMetadata.class.isAssignableFrom(respTimeClass));
        assertTrue(Metrics.class.isAssignableFrom(respTimeClass));

        // Verify @Stream annotation
        Stream streamAnnotation = respTimeClass.getAnnotation(Stream.class);
        assertNotNull(streamAnnotation);
        assertEquals("test_service_resp_time", streamAnnotation.name());

        // Test instantiation of all metrics classes
        for (Class<?> metricsClass : metricsClasses) {
            Object instance = metricsClass.getDeclaredConstructor().newInstance();
            assertNotNull(instance);
            assertTrue(instance instanceof Metrics);
        }

        // Verify required methods on respTimeClass
        verifyMetricsClassMethods(respTimeClass);

        // Test id() method generation
        Object respTimeInstance = respTimeClass.getDeclaredConstructor().newInstance();
        Method setTimeBucket = respTimeClass.getMethod("setTimeBucket", long.class);
        Method setEntityId = respTimeClass.getMethod("setEntityId", String.class);
        setTimeBucket.invoke(respTimeInstance, 202301011200L);
        setEntityId.invoke(respTimeInstance, "test-service");
        Metrics metrics = (Metrics) respTimeInstance;
        assertNotNull(metrics.id());

        // Test hashCode() and equals()
        Object instance1 = respTimeClass.getDeclaredConstructor().newInstance();
        Object instance2 = respTimeClass.getDeclaredConstructor().newInstance();
        setTimeBucket.invoke(instance1, 202301011200L);
        setEntityId.invoke(instance1, "test-service");
        setTimeBucket.invoke(instance2, 202301011200L);
        setEntityId.invoke(instance2, "test-service");
        assertEquals(instance1.hashCode(), instance2.hashCode());
        assertEquals(instance1, instance2);

        setEntityId.invoke(instance2, "different-service");
        assertFalse(instance1.equals(instance2));

        // Test serialize/deserialize
        Method serializeMethod = respTimeClass.getMethod("serialize");
        Object remoteDataBuilder = serializeMethod.invoke(respTimeInstance);
        assertNotNull(remoteDataBuilder);
        assertTrue(remoteDataBuilder instanceof org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.Builder);

        Method deserializeMethod = respTimeClass.getMethod("deserialize",
            org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.class);
        assertNotNull(deserializeMethod);

        // Test toHour/toDay
        Object instance3 = respTimeClass.getDeclaredConstructor().newInstance();
        setTimeBucket.invoke(instance3, 202301011230L); // minute bucket
        setEntityId.invoke(instance3, "test-service");
        Method toHourMethod = respTimeClass.getMethod("toHour");
        Object hourInstance = toHourMethod.invoke(instance3);
        assertNotNull(hourInstance);
        assertTrue(hourInstance instanceof Metrics);
        Method toDayMethod = respTimeClass.getMethod("toDay");
        Object dayInstance = toDayMethod.invoke(instance3);
        assertNotNull(dayInstance);
        assertTrue(dayInstance instanceof Metrics);

        // Test getMeta()
        Object instance4 = respTimeClass.getDeclaredConstructor().newInstance();
        setEntityId.invoke(instance4, "test-service");
        WithMetadata withMetadata = (WithMetadata) instance4;
        assertNotNull(withMetadata.getMeta());
        assertEquals("test_service_resp_time", withMetadata.getMeta().getMetricsName());

        // Verify dispatcher class (uses "Test" catalog prefix)
        Class<?> dispatcherClass = dispatcherClasses.get(0);
        assertEquals("TestServiceDispatcher", dispatcherClass.getSimpleName());
        assertTrue(SourceDispatcher.class.isAssignableFrom(dispatcherClass));

        Method dispatchMethod = dispatcherClass.getMethod("dispatch",
            org.apache.skywalking.oap.server.core.source.ISource.class);
        assertNotNull(dispatchMethod);

        // Verify all doMetrics methods exist
        assertNotNull(findMethodByName(dispatcherClass, "doTestServiceRespTime"));
        assertNotNull(findMethodByName(dispatcherClass, "doTestServiceCalls"));
        assertNotNull(findMethodByName(dispatcherClass, "doTestServiceLatencySum"));
        assertNotNull(findMethodByName(dispatcherClass, "doTestServiceLatencyMax"));

        // Verify doMetrics methods are private
        Method doRespTime = findMethodByName(dispatcherClass, "doTestServiceRespTime");
        assertTrue(Modifier.isPrivate(doRespTime.getModifiers()));

        // Test builder class generation
        String builderClassName = METRICS_BUILDER_PACKAGE + "TestServiceRespTimeMetricsBuilder";
        Class<?> builderClass = Class.forName(builderClassName);
        assertNotNull(builderClass);
        assertTrue(StorageBuilder.class.isAssignableFrom(builderClass));

        Object builderInstance = builderClass.getDeclaredConstructor().newInstance();
        assertNotNull(builderInstance);

        assertNotNull(builderClass.getMethod("entity2Storage",
            org.apache.skywalking.oap.server.core.storage.StorageData.class,
            org.apache.skywalking.oap.server.core.storage.type.Convert2Storage.class));
        assertNotNull(builderClass.getMethod("storage2Entity",
            org.apache.skywalking.oap.server.core.storage.type.Convert2Entity.class));
    }

    /**
     * Test code generation with Endpoint source.
     * This test is separate from Service tests to demonstrate multi-source support.
     */
    @Test
    public void testEndpointMetricsGeneration() throws Exception {
        // Use test_ prefix to avoid conflicts with RuntimeOALGenerationTest
        String oal = "test_endpoint_resp_time = from(Endpoint.latency).longAvg();\n" +
            "test_endpoint_calls = from(Endpoint.*).count();";

        OALClassGeneratorV2 generator = createGenerator();
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);

        List<Class> metricsClasses = new ArrayList<>();
        List<Class> dispatcherClasses = new ArrayList<>();

        generateClasses(oal, generator, enricher, metricsClasses, dispatcherClasses);

        // Verify metrics classes
        assertEquals(2, metricsClasses.size());
        assertEquals(1, dispatcherClasses.size());

        Class<?> respTimeClass = findClassBySimpleName(metricsClasses, "TestEndpointRespTimeMetrics");
        Class<?> callsClass = findClassBySimpleName(metricsClasses, "TestEndpointCallsMetrics");

        assertNotNull(respTimeClass, "TestEndpointRespTimeMetrics should be generated");
        assertNotNull(callsClass, "TestEndpointCallsMetrics should be generated");

        assertTrue(LongAvgMetrics.class.isAssignableFrom(respTimeClass));
        assertTrue(CountMetrics.class.isAssignableFrom(callsClass));

        // Test instantiation
        assertNotNull(respTimeClass.getDeclaredConstructor().newInstance());
        assertNotNull(callsClass.getDeclaredConstructor().newInstance());

        // Verify dispatcher class (uses "Test" catalog prefix)
        Class<?> dispatcherClass = dispatcherClasses.get(0);
        assertEquals("TestEndpointDispatcher", dispatcherClass.getSimpleName());
        assertTrue(SourceDispatcher.class.isAssignableFrom(dispatcherClass));

        assertNotNull(findMethodByName(dispatcherClass, "doTestEndpointRespTime"));
        assertNotNull(findMethodByName(dispatcherClass, "doTestEndpointCalls"));
    }

    /**
     * Test full code generation for metrics with filter expressions.
     * Uses ServiceRelation source to avoid frozen class conflicts with other tests.
     *
     * This test validates:
     * - Filter expressions are correctly passed to templates
     * - Generated dispatcher contains filter logic
     * - Multiple filters in chain are handled correctly
     */
    @Test
    public void testMetricsWithFilterGeneration() throws Exception {
        // Use test_ prefix to avoid conflicts with RuntimeOALGenerationTest
        String oal = "test_service_relation_client_cpm = from(ServiceRelation.*).filter(detectPoint == DetectPoint.CLIENT).cpm();\n" +
            "test_service_relation_server_cpm = from(ServiceRelation.*).filter(detectPoint == DetectPoint.SERVER).cpm();\n" +
            "test_service_relation_client_call_sla = from(ServiceRelation.*).filter(detectPoint == DetectPoint.CLIENT).filter(status == true).percent(status == true);\n" +
            "test_service_relation_client_resp_time = from(ServiceRelation.latency).filter(detectPoint == DetectPoint.CLIENT).longAvg();";

        OALClassGeneratorV2 generator = createGenerator();
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);

        List<Class> metricsClasses = new ArrayList<>();
        List<Class> dispatcherClasses = new ArrayList<>();

        generateClasses(oal, generator, enricher, metricsClasses, dispatcherClasses);

        // Verify 4 metrics classes generated
        assertEquals(4, metricsClasses.size());

        // Verify 1 dispatcher class for ServiceRelation source
        assertEquals(1, dispatcherClasses.size());

        Class<?> dispatcherClass = dispatcherClasses.get(0);
        assertEquals("TestServiceRelationDispatcher", dispatcherClass.getSimpleName());
        assertTrue(SourceDispatcher.class.isAssignableFrom(dispatcherClass));

        // Verify all doMetrics methods exist
        Method doClientCpm = findMethodByName(dispatcherClass, "doTestServiceRelationClientCpm");
        Method doServerCpm = findMethodByName(dispatcherClass, "doTestServiceRelationServerCpm");
        Method doClientCallSla = findMethodByName(dispatcherClass, "doTestServiceRelationClientCallSla");
        Method doClientRespTime = findMethodByName(dispatcherClass, "doTestServiceRelationClientRespTime");

        assertNotNull(doClientCpm, "doTestServiceRelationClientCpm should be generated");
        assertNotNull(doServerCpm, "doTestServiceRelationServerCpm should be generated");
        assertNotNull(doClientCallSla, "doTestServiceRelationClientCallSla should be generated");
        assertNotNull(doClientRespTime, "doTestServiceRelationClientRespTime should be generated");

        // Verify filter expressions were correctly enriched
        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);

        // Check single filter metric
        MetricDefinition clientCpmMetric = parser.getMetrics().get(0);
        CodeGenModel clientCpmModel = enricher.enrich(clientCpmMetric);
        assertEquals(1, clientCpmModel.getFilterExpressions().size());
        assertTrue(clientCpmModel.getFilterExpressions().get(0).getExpressionObject().contains("Match"));
        assertTrue(clientCpmModel.getFilterExpressions().get(0).getLeft().contains("getDetectPoint"));
        assertTrue(clientCpmModel.getFilterExpressions().get(0).getRight().contains("DetectPoint.CLIENT"));

        // Check multi-filter metric (filter chain)
        MetricDefinition clientCallSlaMetric = parser.getMetrics().get(2);
        CodeGenModel clientCallSlaModel = enricher.enrich(clientCallSlaMetric);
        assertEquals(2, clientCallSlaModel.getFilterExpressions().size(),
            "Multi-filter metric should have 2 filter expressions");

        // First filter: detectPoint == DetectPoint.CLIENT
        CodeGenModel.FilterExpressionV2 filter1 = clientCallSlaModel.getFilterExpressions().get(0);
        assertTrue(filter1.getLeft().contains("getDetectPoint"));

        // Second filter: status == true (boolean)
        CodeGenModel.FilterExpressionV2 filter2 = clientCallSlaModel.getFilterExpressions().get(1);
        assertTrue(filter2.getLeft().contains("isStatus") || filter2.getLeft().contains("getStatus"));
        assertEquals("true", filter2.getRight());

        // Verify metrics classes are properly generated
        Class<?> clientCpmClass = findClassBySimpleName(metricsClasses, "TestServiceRelationClientCpmMetrics");
        Class<?> slaClass = findClassBySimpleName(metricsClasses, "TestServiceRelationClientCallSlaMetrics");

        assertNotNull(clientCpmClass);
        assertNotNull(slaClass);

        // Test instantiation
        Object clientCpmInstance = clientCpmClass.getDeclaredConstructor().newInstance();
        assertNotNull(clientCpmInstance);
        assertTrue(clientCpmInstance instanceof Metrics);
    }

    /**
     * Test code generation with `in` filter operator using array values.
     * This verifies that `in [...]` syntax generates correct bytecode that can be loaded by JVM.
     *
     * Examples from OAL doc:
     * - filter(name in ["Endpoint1", "Endpoint2"]) - string array
     * - filter(httpResponseStatusCode in [404, 500, 503]) - number array
     * - filter(type in [RequestType.RPC, RequestType.gRPC]) - enum array
     */
    @Test
    public void testInFilterOperatorGeneration() throws Exception {
        // Use test_ prefix to avoid conflicts with RuntimeOALGenerationTest
        String oal = "test_tcp_service_tls_mode_filtered = from(TCPService.*)" +
            ".filter(tlsMode in [\"STRICT\", \"PERMISSIVE\", \"DISABLED\"]).count();";

        OALClassGeneratorV2 generator = createGenerator();
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);

        List<Class> metricsClasses = new ArrayList<>();
        List<Class> dispatcherClasses = new ArrayList<>();

        generateClasses(oal, generator, enricher, metricsClasses, dispatcherClasses);

        // Verify metrics class generated
        assertEquals(1, metricsClasses.size());
        Class<?> metricsClass = metricsClasses.get(0);
        assertEquals("TestTcpServiceTlsModeFilteredMetrics", metricsClass.getSimpleName());
        assertTrue(CountMetrics.class.isAssignableFrom(metricsClass));

        // Verify dispatcher generated with filter logic (uses "Test" catalog prefix)
        assertEquals(1, dispatcherClasses.size());
        Class<?> dispatcherClass = dispatcherClasses.get(0);
        assertEquals("TestTCPServiceDispatcher", dispatcherClass.getSimpleName());
        assertTrue(SourceDispatcher.class.isAssignableFrom(dispatcherClass));

        // Verify the doMetrics method exists (contains the InMatch filter logic)
        Method doMethod = findMethodByName(dispatcherClass, "doTestTcpServiceTlsModeFiltered");
        assertNotNull(doMethod, "doTestTcpServiceTlsModeFiltered should be generated");

        // Verify class can be instantiated
        Object metricsInstance = metricsClass.getDeclaredConstructor().newInstance();
        assertNotNull(metricsInstance);
        assertTrue(metricsInstance instanceof Metrics);
    }

    /**
     * Create a configured OALClassGeneratorV2 instance.
     */
    private OALClassGeneratorV2 createGenerator() {
        TestOALDefine oalDefine = new TestOALDefine();
        OALClassGeneratorV2 generator = new OALClassGeneratorV2(oalDefine);
        generator.setStorageBuilderFactory(new StorageBuilderFactory.Default());
        generator.prepareRTTempFolder();
        return generator;
    }

    /**
     * Helper method to generate classes from OAL script.
     */
    private void generateClasses(String oal, OALClassGeneratorV2 generator, MetricDefinitionEnricher enricher,
                                  List<Class> metricsClasses, List<Class> dispatcherClasses)
        throws IOException, OALCompileException {

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);

        List<CodeGenModel> codeGenModels = new ArrayList<>();
        for (MetricDefinition metric : parser.getMetrics()) {
            CodeGenModel model = enricher.enrich(metric);
            codeGenModels.add(model);
        }

        generator.generateClassAtRuntime(
            codeGenModels,
            parser.getDisabledSources(),
            metricsClasses,
            dispatcherClasses
        );
    }

    /**
     * Test code generation with nested boolean attribute as function argument.
     * Uses K8SServiceInstance source with apdex(serviceName, protocol.success).
     *
     * This is a regression test for the bug where nested boolean attributes were not
     * handled correctly in function arguments. The bug generated invalid code like:
     *   source.isProtocol.success()
     * instead of:
     *   source.getProtocol().isSuccess()
     *
     * @throws Exception if code generation fails
     */
    @Test
    public void testNestedBooleanAttributeInFunctionArgument() throws Exception {
        // Use test_ prefix to avoid conflicts with RuntimeOALGenerationTest
        // This OAL pattern uses apdex(serviceName, protocol.success) where:
        // - protocol is a nested object (Protocol class)
        // - success is a boolean field on Protocol
        // The generated code must be: source.getProtocol().isSuccess()
        String oal = "test_kubernetes_service_instance_apdex = from(K8SServiceInstance.protocol.http.latency)" +
            ".filter(detectPoint == DetectPoint.SERVER)" +
            ".filter(type == \"protocol\")" +
            ".filter(protocol.type == \"http\")" +
            ".apdex(serviceName, protocol.success);";

        OALClassGeneratorV2 generator = createGenerator();
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);

        List<Class> metricsClasses = new ArrayList<>();
        List<Class> dispatcherClasses = new ArrayList<>();

        // This will throw OALCompileException if the generated code is invalid
        // (e.g., "no such class: source.isProtocol")
        generateClasses(oal, generator, enricher, metricsClasses, dispatcherClasses);

        // Verify metrics class generated
        assertEquals(1, metricsClasses.size());
        assertEquals(1, dispatcherClasses.size());

        Class<?> apdexClass = findClassBySimpleName(metricsClasses, "TestKubernetesServiceInstanceApdexMetrics");
        assertNotNull(apdexClass, "TestKubernetesServiceInstanceApdexMetrics should be generated");

        // Verify dispatcher class (uses "Test" catalog prefix)
        Class<?> dispatcherClass = dispatcherClasses.get(0);
        assertEquals("TestK8SServiceInstanceDispatcher", dispatcherClass.getSimpleName());
        assertTrue(SourceDispatcher.class.isAssignableFrom(dispatcherClass));

        // Verify doMetrics method exists
        Method doApdex = findMethodByName(dispatcherClass, "doTestKubernetesServiceInstanceApdex");
        assertNotNull(doApdex, "doTestKubernetesServiceInstanceApdex method should be generated");
        assertTrue(Modifier.isPrivate(doApdex.getModifiers()));
    }

    /**
     * Verify all required methods exist on a metrics class.
     */
    private void verifyMetricsClassMethods(Class<?> metricsClass) throws NoSuchMethodException {
        assertNotNull(metricsClass.getMethod("id"));
        assertNotNull(metricsClass.getMethod("hashCode"));
        assertNotNull(metricsClass.getMethod("remoteHashCode"));
        assertNotNull(metricsClass.getMethod("equals", Object.class));
        assertNotNull(metricsClass.getMethod("serialize"));
        assertNotNull(metricsClass.getMethod("deserialize",
            org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData.class));
        assertNotNull(metricsClass.getMethod("toHour"));
        assertNotNull(metricsClass.getMethod("toDay"));
    }

    /**
     * Find a class by simple name.
     */
    private Class<?> findClassBySimpleName(List<Class> classes, String simpleName) {
        for (Class<?> clazz : classes) {
            if (clazz.getSimpleName().equals(simpleName)) {
                return clazz;
            }
        }
        return null;
    }

    /**
     * Find a method by name (including private methods).
     */
    private Method findMethodByName(Class<?> clazz, String methodName) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Test OAL define for unit tests.
     * Uses "Test" catalog to create unique dispatcher class names
     * (e.g., TestServiceDispatcher instead of ServiceDispatcher).
     */
    private static class TestOALDefine extends OALDefine {
        protected TestOALDefine() {
            super("test.oal", "org.apache.skywalking.oap.server.core.source", "Test");
        }
    }
}
