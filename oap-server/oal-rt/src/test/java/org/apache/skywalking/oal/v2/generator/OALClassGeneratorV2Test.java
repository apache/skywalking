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
import org.apache.skywalking.oap.server.core.source.Service;
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
        DefaultScopeDefine.Listener listener = new DefaultScopeDefine.Listener();
        listener.notify(Service.class);
        listener.notify(Endpoint.class);
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
        String oal = "service_resp_time = from(Service.latency).longAvg();\n" +
            "service_calls = from(Service.*).count();\n" +
            "service_latency_sum = from(Service.latency).sum();\n" +
            "service_latency_max = from(Service.latency).max();";

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
        Class<?> respTimeClass = findClassBySimpleName(metricsClasses, "ServiceRespTimeMetrics");
        Class<?> callsClass = findClassBySimpleName(metricsClasses, "ServiceCallsMetrics");
        Class<?> sumClass = findClassBySimpleName(metricsClasses, "ServiceLatencySumMetrics");
        Class<?> maxClass = findClassBySimpleName(metricsClasses, "ServiceLatencyMaxMetrics");

        assertNotNull(respTimeClass, "ServiceRespTimeMetrics should be generated");
        assertNotNull(callsClass, "ServiceCallsMetrics should be generated");
        assertNotNull(sumClass, "ServiceLatencySumMetrics should be generated");
        assertNotNull(maxClass, "ServiceLatencyMaxMetrics should be generated");

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
        assertEquals("service_resp_time", streamAnnotation.name());

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
        assertEquals("service_resp_time", withMetadata.getMeta().getMetricsName());

        // Verify dispatcher class
        Class<?> dispatcherClass = dispatcherClasses.get(0);
        assertEquals("ServiceDispatcher", dispatcherClass.getSimpleName());
        assertTrue(SourceDispatcher.class.isAssignableFrom(dispatcherClass));

        Method dispatchMethod = dispatcherClass.getMethod("dispatch",
            org.apache.skywalking.oap.server.core.source.ISource.class);
        assertNotNull(dispatchMethod);

        // Verify all doMetrics methods exist
        assertNotNull(findMethodByName(dispatcherClass, "doServiceRespTime"));
        assertNotNull(findMethodByName(dispatcherClass, "doServiceCalls"));
        assertNotNull(findMethodByName(dispatcherClass, "doServiceLatencySum"));
        assertNotNull(findMethodByName(dispatcherClass, "doServiceLatencyMax"));

        // Verify doMetrics methods are private
        Method doRespTime = findMethodByName(dispatcherClass, "doServiceRespTime");
        assertTrue(Modifier.isPrivate(doRespTime.getModifiers()));

        // Test builder class generation
        String builderClassName = METRICS_BUILDER_PACKAGE + "ServiceRespTimeMetricsBuilder";
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
        String oal = "endpoint_resp_time = from(Endpoint.latency).longAvg();\n" +
            "endpoint_calls = from(Endpoint.*).count();";

        OALClassGeneratorV2 generator = createGenerator();
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);

        List<Class> metricsClasses = new ArrayList<>();
        List<Class> dispatcherClasses = new ArrayList<>();

        generateClasses(oal, generator, enricher, metricsClasses, dispatcherClasses);

        // Verify metrics classes
        assertEquals(2, metricsClasses.size());
        assertEquals(1, dispatcherClasses.size());

        Class<?> respTimeClass = findClassBySimpleName(metricsClasses, "EndpointRespTimeMetrics");
        Class<?> callsClass = findClassBySimpleName(metricsClasses, "EndpointCallsMetrics");

        assertNotNull(respTimeClass, "EndpointRespTimeMetrics should be generated");
        assertNotNull(callsClass, "EndpointCallsMetrics should be generated");

        assertTrue(LongAvgMetrics.class.isAssignableFrom(respTimeClass));
        assertTrue(CountMetrics.class.isAssignableFrom(callsClass));

        // Test instantiation
        assertNotNull(respTimeClass.getDeclaredConstructor().newInstance());
        assertNotNull(callsClass.getDeclaredConstructor().newInstance());

        // Verify dispatcher class
        Class<?> dispatcherClass = dispatcherClasses.get(0);
        assertEquals("EndpointDispatcher", dispatcherClass.getSimpleName());
        assertTrue(SourceDispatcher.class.isAssignableFrom(dispatcherClass));

        assertNotNull(findMethodByName(dispatcherClass, "doEndpointRespTime"));
        assertNotNull(findMethodByName(dispatcherClass, "doEndpointCalls"));
    }

    /**
     * Create a configured OALClassGeneratorV2 instance.
     */
    private OALClassGeneratorV2 createGenerator() {
        TestOALDefine oalDefine = new TestOALDefine();
        OALClassGeneratorV2 generator = new OALClassGeneratorV2(oalDefine);
        generator.setCurrentClassLoader(OALClassGeneratorV2Test.class.getClassLoader());
        generator.setStorageBuilderFactory(new StorageBuilderFactory.Default());
        generator.setOpenEngineDebug(false);
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
     */
    private static class TestOALDefine extends OALDefine {
        protected TestOALDefine() {
            super("test.oal", "org.apache.skywalking.oap.server.core.source");
        }
    }
}
