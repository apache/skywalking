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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javassist.ClassPool;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.apache.skywalking.oap.server.core.analysis.SourceDecoratorManager;
import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsAggregateWorker;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.source.CiliumService;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.Endpoint;
import org.apache.skywalking.oap.server.core.source.EndpointDecorator;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.MQAccess;
import org.apache.skywalking.oap.server.core.source.Service;
import org.apache.skywalking.oap.server.core.source.ServiceDecorator;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceJVMCPU;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceJVMMemory;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Compiles an OAL script the way the OAP does and runs sources through the generated dispatcher, handing back the
 * metrics it would send downstream. Metric names must be unique in the test JVM: the generated classes share one
 * package and class loader with every other OAL test.
 */
final class OALScriptRunner {
    static final String SOURCE_PACKAGE = "org.apache.skywalking.oap.server.core.source.";
    private static final String METRICS_PACKAGE = "org.apache.skywalking.oap.server.core.source.oal.rt.metrics.";
    private static final AtomicInteger CATALOGS = new AtomicInteger();
    private static final ClassPool CLASS_POOL = new ClassPool(true);

    static {
        final DefaultScopeDefine.Listener scopes = new DefaultScopeDefine.Listener();
        for (final Class<?> scope : List.of(Service.class, Endpoint.class, MQAccess.class, CiliumService.class,
                                            ServiceInstanceJVMCPU.class, ServiceInstanceJVMMemory.class)) {
            try {
                scopes.notify(scope);
            } catch (RuntimeException alreadyRegistered) {
                // Registered by another test in this JVM.
            }
        }
        final SourceDecoratorManager decorators = new SourceDecoratorManager();
        for (final Class<?> decorator : List.of(ServiceDecorator.class, EndpointDecorator.class)) {
            try {
                decorators.addIfAsSourceDecorator(decorator);
            } catch (Exception alreadyRegistered) {
                // Registered by another test in this JVM.
            }
        }
    }

    private final List<Class> metricsClasses;
    private final SourceDispatcher<ISource> dispatcher;

    private OALScriptRunner(List<Class> metricsClasses, SourceDispatcher<ISource> dispatcher) {
        this.metricsClasses = metricsClasses;
        this.dispatcher = dispatcher;
    }

    /**
     * Parse and enrich only, as far as a compile error can come from the script.
     */
    static List<CodeGenModel> enrich(String script) throws Exception {
        return enrich(script, SOURCE_PACKAGE);
    }

    static List<CodeGenModel> enrich(String script, String sourcePackage) throws Exception {
        final MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(sourcePackage, METRICS_PACKAGE);
        final List<CodeGenModel> models = new ArrayList<>();
        for (final MetricDefinition metric : OALScriptParserV2.parse(script, "checks.oal").getMetrics()) {
            models.add(enricher.enrich(metric));
        }
        return models;
    }

    /**
     * Compile a script whose metrics all read one source.
     */
    static OALScriptRunner compile(String script) throws Exception {
        return compile(enrich(script), OALScriptParserV2.parse(script, "checks.oal").getDisabledSources());
    }

    /**
     * Compile a script on the sources of another package, with the disable statements it holds.
     */
    @SuppressWarnings("unchecked")
    static OALScriptRunner compile(String script, String sourcePackage) throws Exception {
        final List<Class> metricsClasses = new ArrayList<>();
        final List<Class> dispatcherClasses = new ArrayList<>();
        generator(sourcePackage).generateClassAtRuntime(
            enrich(script, sourcePackage), OALScriptParserV2.parse(script, "checks.oal").getDisabledSources(),
            metricsClasses, dispatcherClasses);
        return new OALScriptRunner(
            metricsClasses, (SourceDispatcher<ISource>) dispatcherClasses.get(0).getDeclaredConstructor().newInstance());
    }

    /**
     * Compile and register the disable statements of a script with no metrics.
     */
    static void compileDisables(String script) throws Exception {
        generator().generateClassAtRuntime(
            new ArrayList<>(), OALScriptParserV2.parse(script, "checks.oal").getDisabledSources(),
            new ArrayList<>(), new ArrayList<>());
    }

    private static OALClassGeneratorV2 generator() {
        return generator(SOURCE_PACKAGE);
    }

    private static OALClassGeneratorV2 generator(String sourcePackage) {
        final OALDefine define = new OALDefine("checks.oal", sourcePackage, "Checks" + CATALOGS.incrementAndGet()) {
        };
        final OALClassGeneratorV2 generator = new OALClassGeneratorV2(define, CLASS_POOL);
        generator.setStorageBuilderFactory(new StorageBuilderFactory.Default());
        return generator;
    }

    /**
     * Generate, define and instantiate the classes of a script of any number of sources.
     *
     * @return the generated metrics and dispatcher classes.
     */
    static int generate(List<CodeGenModel> models) throws Exception {
        final List<Class> metricsClasses = new ArrayList<>();
        final List<Class> dispatcherClasses = new ArrayList<>();
        generator().generateClassAtRuntime(models, new ArrayList<>(), metricsClasses, dispatcherClasses);
        return metricsClasses.size() + dispatcherClasses.size();
    }

    static OALScriptRunner compile(List<CodeGenModel> models) throws Exception {
        return compile(models, new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    private static OALScriptRunner compile(List<CodeGenModel> models, List<String> disabled) throws Exception {
        final List<Class> metricsClasses = new ArrayList<>();
        final List<Class> dispatcherClasses = new ArrayList<>();
        generator().generateClassAtRuntime(models, disabled, metricsClasses, dispatcherClasses);
        if (dispatcherClasses.size() != 1) {
            throw new IllegalStateException("expected one source, got dispatchers " + dispatcherClasses);
        }
        return new OALScriptRunner(
            metricsClasses, (SourceDispatcher<ISource>) dispatcherClasses.get(0).getDeclaredConstructor().newInstance());
    }

    static ClassPool classPool() {
        return CLASS_POOL;
    }

    List<Class> metricsClasses() {
        return metricsClasses;
    }

    /**
     * @return the metrics the dispatcher hands to the stream processor for this source.
     */
    @SuppressWarnings("unchecked")
    List<Metrics> dispatch(ISource source) throws Exception {
        final Field field = MetricsStreamProcessor.class.getDeclaredField("entryWorkers");
        field.setAccessible(true);
        final Map<Class<? extends Metrics>, MetricsAggregateWorker> entryWorkers =
            (Map<Class<? extends Metrics>, MetricsAggregateWorker>) field.get(MetricsStreamProcessor.getInstance());
        final List<Metrics> received = new ArrayList<>();
        final MetricsAggregateWorker worker = mock(MetricsAggregateWorker.class);
        doAnswer(invocation -> received.add(invocation.getArgument(0))).when(worker).in(any());
        for (final Class metricsClass : metricsClasses) {
            entryWorkers.put(metricsClass, worker);
        }
        try {
            dispatcher.dispatch(source);
        } finally {
            for (final Class metricsClass : metricsClasses) {
                entryWorkers.remove(metricsClass);
            }
        }
        return received;
    }
}
