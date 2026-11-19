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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javassist.ClassPool;
import org.apache.skywalking.oal.v2.generator.CodeGenModel;
import org.apache.skywalking.oal.v2.generator.MetricDefinitionEnricher;
import org.apache.skywalking.oal.v2.generator.OALClassGeneratorV2;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.apache.skywalking.oap.server.core.analysis.SourceDecoratorManager;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

/**
 * The metrics classes OAP generates from the shipped {@code core.oal}, generated the same way at test time, so an IT
 * reads a metric through the schema, the value columns and the name a real OAP has.
 */
final class OALTestMetrics {
    private static final String SOURCE_PACKAGE = "org.apache.skywalking.oap.server.core.source.";
    private static final String METRICS_PACKAGE = "org.apache.skywalking.oap.server.core.source.oal.rt.metrics.";
    private static final String CORE_OAL = "oal/core.oal";
    private static final String[] STARTER_RESOURCES = {
        "../../server-starter/src/main/resources/",
        "oap-server/server-starter/src/main/resources/",
        "../server-starter/src/main/resources/"
    };
    /** The sources {@code core.oal} reads. */
    private static final String[] SOURCES = {
        "Service", "ServiceInstance", "Endpoint", "ServiceRelation", "ServiceInstanceRelation", "EndpointRelation",
        "DatabaseAccess", "CacheAccess", "MQAccess", "MQEndpointAccess"
    };
    private static final String[] DECORATORS = {"ServiceDecorator", "EndpointDecorator"};

    /** Generated once per JVM: a class name can be defined only once in a class loader. */
    private static Map<String, Class<? extends Metrics>> CLASSES;

    private OALTestMetrics() {
    }

    /** The class OAP generates for the {@code core.oal} metric of this name. */
    static synchronized Class<? extends Metrics> metricsClass(String name) throws Exception {
        if (CLASSES == null) {
            CLASSES = generate();
        }
        final Class<? extends Metrics> metricsClass = CLASSES.get(name);
        if (metricsClass == null) {
            throw new IllegalArgumentException("core.oal has no metric " + name);
        }
        return metricsClass;
    }

    static int scopeId(Class<? extends Metrics> metricsClass) {
        return metricsClass.getAnnotation(Stream.class).scopeId();
    }

    @SuppressWarnings("unchecked")
    static StorageBuilder<Metrics> builder(Class<? extends Metrics> metricsClass) throws Exception {
        return (StorageBuilder<Metrics>) metricsClass.getAnnotation(Stream.class).builder()
                                                     .getDeclaredConstructor().newInstance();
    }

    /** A new metrics object with each named property set through its setter. */
    static Metrics newMetrics(Class<? extends Metrics> metricsClass, Object... properties) throws Exception {
        final Metrics metrics = metricsClass.getDeclaredConstructor().newInstance();
        for (int i = 0; i < properties.length; i += 2) {
            set(metrics, (String) properties[i], properties[i + 1]);
        }
        return metrics;
    }

    private static void set(Object target, String property, Object value) throws Exception {
        final String setter = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (final Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(setter) && method.getParameterCount() == 1) {
                    method.setAccessible(true);
                    method.invoke(target, value);
                    return;
                }
            }
        }
        throw new IllegalArgumentException(target.getClass().getSimpleName() + " has no " + setter);
    }

    private static Map<String, Class<? extends Metrics>> generate() throws Exception {
        final DefaultScopeDefine.Listener scopes = new DefaultScopeDefine.Listener();
        for (final String source : SOURCES) {
            try {
                scopes.notify(Class.forName(SOURCE_PACKAGE + source));
            } catch (Exception alreadyRegistered) {
                // Another IT in this JVM registered it first.
            }
        }
        final SourceDecoratorManager decorators = new SourceDecoratorManager();
        for (final String decorator : DECORATORS) {
            decorators.addIfAsSourceDecorator(Class.forName(SOURCE_PACKAGE + decorator));
        }

        final OALDefine define = new OALDefine(CORE_OAL, SOURCE_PACKAGE, "") {
        };
        final List<MetricDefinition> definitions;
        try (Reader reader = new FileReader(coreOAL())) {
            definitions = OALScriptParserV2.parse(reader, CORE_OAL).getMetrics();
        }
        final MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        final List<CodeGenModel> models = new ArrayList<>();
        for (final MetricDefinition definition : definitions) {
            models.add(enricher.enrich(definition));
        }
        final OALClassGeneratorV2 generator = new OALClassGeneratorV2(define, new ClassPool(true));
        generator.setStorageBuilderFactory(new StorageBuilderFactory.Default());
        final List<Class> metricsClasses = new ArrayList<>();
        generator.generateClassAtRuntime(models, new ArrayList<>(), metricsClasses, new ArrayList<>());

        final Map<String, Class<? extends Metrics>> classes = new HashMap<>();
        for (final Class<?> metricsClass : metricsClasses) {
            classes.put(metricsClass.getAnnotation(Stream.class).name(), metricsClass.asSubclass(Metrics.class));
        }
        return classes;
    }

    private static File coreOAL() {
        for (final String resources : STARTER_RESOURCES) {
            final File file = new File(resources + CORE_OAL);
            if (file.isFile()) {
                return file;
            }
        }
        throw new IllegalStateException(
            "Cannot find the shipped " + CORE_OAL + " from " + new File("").getAbsolutePath());
    }
}
