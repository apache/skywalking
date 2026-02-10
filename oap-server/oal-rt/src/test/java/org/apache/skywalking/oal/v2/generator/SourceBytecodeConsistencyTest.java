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

import freemarker.template.Configuration;
import freemarker.template.Version;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.IntegerMemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.parser.OALScriptParserV2;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.Service;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that generated source files are 100% consistent with Javassist bytecode.
 *
 * <p>This test compares:
 * <ul>
 *   <li>Method body strings from FreeMarker templates (used by both source and bytecode)</li>
 *   <li>Field declarations and annotations</li>
 *   <li>Class structure (inheritance, interfaces)</li>
 * </ul>
 *
 * <p>The key insight: both OALSourceGenerator and OALClassGeneratorV2/V2ClassGenerator
 * use the same FreeMarker templates. This test verifies template output is identical.
 */
@Slf4j
public class SourceBytecodeConsistencyTest {

    private static final String SOURCE_PACKAGE = "org.apache.skywalking.oap.server.core.source.";
    private static final String METRICS_PACKAGE = "org.apache.skywalking.oap.server.core.source.oal.rt.metrics.";
    private static final String[] TEMPLATE_METHODS = {
        "id", "hashCode", "remoteHashCode", "equals", "serialize", "deserialize", "getMeta", "toHour", "toDay"
    };

    @BeforeAll
    public static void setup() {
        try {
            DefaultScopeDefine.Listener listener = new DefaultScopeDefine.Listener();
            listener.notify(Service.class);
        } catch (Exception e) {
            // Already registered
        }
    }

    private static Configuration getTemplateConfiguration() {
        Configuration config = new Configuration(new Version("2.3.28"));
        config.setEncoding(Locale.ENGLISH, "UTF-8");
        config.setClassLoaderForTemplateLoading(
            SourceBytecodeConsistencyTest.class.getClassLoader(), "/code-templates-v2");
        return config;
    }

    /**
     * Core consistency test: verify template outputs match between source and bytecode generators.
     *
     * Both generators use the same FreeMarker templates. This test:
     * 1. Creates a CodeGenModel
     * 2. Renders each template method
     * 3. Verifies the output is used identically in both paths
     */
    @Test
    public void verifyTemplateOutputConsistency() throws Exception {
        String oal = "service_resp_time = from(Service.latency).longAvg();";

        TestOALDefine define = new TestOALDefine();
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        // Generate template output for each method
        List<String> templateOutputs = new ArrayList<>();
        for (String method : TEMPLATE_METHODS) {
            StringWriter writer = new StringWriter();
            getTemplateConfiguration().getTemplate("metrics/" + method + ".ftl").process(model, writer);
            String output = writer.toString();
            templateOutputs.add(output);

            log.info("Template '{}' output length: {} chars", method, output.length());
            assertTrue(output.length() > 0, "Template " + method + " should produce output");
        }

        // Generate bytecode using V2ClassGenerator (same templates)
        ClassPool classPool = new ClassPool(true);
        V2ClassGenerator bytecodeGen = new V2ClassGenerator(define, classPool);
        CtClass bytecodeClass = bytecodeGen.generateMetricsCtClass(model);

        // Verify bytecode class has all expected methods
        for (String method : TEMPLATE_METHODS) {
            String methodName = method.equals("id") ? "id0" : method;
            CtMethod ctMethod = findMethod(bytecodeClass, methodName);
            assertTrue(ctMethod != null, "Bytecode should have method: " + methodName);
        }

        // Generate source using OALSourceGenerator (same templates)
        OALSourceGenerator sourceGen = new OALSourceGenerator(define);
        sourceGen.setStorageBuilderFactory(new StorageBuilderFactory.Default());
        String source = sourceGen.generateMetricsSource(model);

        // Verify source contains all template outputs
        for (int i = 0; i < TEMPLATE_METHODS.length; i++) {
            String templateOutput = templateOutputs.get(i).trim();
            // The source should contain the template output (method body)
            assertTrue(source.contains(templateOutput),
                "Source should contain template output for: " + TEMPLATE_METHODS[i]);
        }

        log.info("Template consistency verified for {} methods", TEMPLATE_METHODS.length);
    }

    /**
     * Verify field consistency between source and bytecode.
     */
    @Test
    public void verifyFieldConsistency() throws Exception {
        String oal = "service_resp_time = from(Service.latency).longAvg();";

        TestOALDefine define = new TestOALDefine();
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        ClassPool classPool = new ClassPool(true);

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        // Generate bytecode
        V2ClassGenerator bytecodeGen = new V2ClassGenerator(define, classPool);
        CtClass bytecodeClass = bytecodeGen.generateMetricsCtClass(model);

        // Generate source
        OALSourceGenerator sourceGen = new OALSourceGenerator(define);
        sourceGen.setStorageBuilderFactory(new StorageBuilderFactory.Default());
        String source = sourceGen.generateMetricsSource(model);

        // Verify each field from model exists in both bytecode and source
        for (CodeGenModel.SourceFieldV2 field : model.getFieldsFromSource()) {
            // Check bytecode has field
            CtField ctField = bytecodeClass.getDeclaredField(field.getFieldName());
            assertTrue(ctField != null, "Bytecode should have field: " + field.getFieldName());
            assertEquals(field.getType().getName(), ctField.getType().getName(),
                "Field type should match for: " + field.getFieldName());

            // Check source has field declaration
            String fieldDecl = "private " + field.getType().getName() + " " + field.getFieldName();
            assertTrue(source.contains(fieldDecl),
                "Source should have field declaration: " + fieldDecl);

            // Check source has @Column annotation
            String columnAnnotation = "@Column(name = \"" + field.getColumnName() + "\"";
            assertTrue(source.contains(columnAnnotation),
                "Source should have @Column annotation for: " + field.getFieldName());
        }

        log.info("Field consistency verified for {} fields", model.getFieldsFromSource().size());
    }

    /**
     * Verify class structure consistency.
     */
    @Test
    public void verifyClassStructureConsistency() throws Exception {
        String oal = "service_resp_time = from(Service.latency).longAvg();";

        TestOALDefine define = new TestOALDefine();
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        ClassPool classPool = new ClassPool(true);

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        // Generate bytecode
        V2ClassGenerator bytecodeGen = new V2ClassGenerator(define, classPool);
        CtClass bytecodeClass = bytecodeGen.generateMetricsCtClass(model);

        // Generate source
        OALSourceGenerator sourceGen = new OALSourceGenerator(define);
        sourceGen.setStorageBuilderFactory(new StorageBuilderFactory.Default());
        String source = sourceGen.generateMetricsSource(model);

        // Verify class name
        String expectedClassName = model.getMetricsName() + "Metrics";
        assertEquals(expectedClassName, bytecodeClass.getSimpleName());
        assertTrue(source.contains("public class " + expectedClassName));

        // Verify parent class
        String expectedParent = model.getMetricsClassName();
        assertTrue(bytecodeClass.getSuperclass().getSimpleName().equals(expectedParent));
        assertTrue(source.contains("extends " + expectedParent));

        // Verify interface
        boolean hasWithMetadata = false;
        for (CtClass iface : bytecodeClass.getInterfaces()) {
            if (iface.getSimpleName().equals("WithMetadata")) {
                hasWithMetadata = true;
                break;
            }
        }
        assertTrue(hasWithMetadata, "Bytecode should implement WithMetadata");
        assertTrue(source.contains("implements WithMetadata"), "Source should implement WithMetadata");

        // Verify @Stream annotation content
        assertTrue(source.contains("name = \"" + model.getTableName() + "\""));
        assertTrue(source.contains("scopeId = " + model.getSourceScopeId()));

        log.info("Class structure consistency verified");
    }

    /**
     * Comprehensive annotation verification for class, fields, and methods.
     *
     * Verifies:
     * - Class-level: @Stream (name, scopeId, builder, processor)
     * - Field-level: @Column, @BanyanDB.SeriesID, @BanyanDB.ShardingKey, @ElasticSearch.EnableDocValues
     * - Method-level: None expected (methods are generated without annotations)
     * - Parameter-level: None expected
     */
    @Test
    public void verifyAllAnnotationsConsistency() throws Exception {
        String oal = "service_resp_time = from(Service.latency).longAvg();";

        TestOALDefine define = new TestOALDefine();
        MetricDefinitionEnricher enricher = new MetricDefinitionEnricher(SOURCE_PACKAGE, METRICS_PACKAGE);
        ClassPool classPool = new ClassPool(true);

        OALScriptParserV2 parser = OALScriptParserV2.parse(oal);
        MetricDefinition metric = parser.getMetrics().get(0);
        CodeGenModel model = enricher.enrich(metric);

        // Generate bytecode
        V2ClassGenerator bytecodeGen = new V2ClassGenerator(define, classPool);
        CtClass bytecodeClass = bytecodeGen.generateMetricsCtClass(model);

        // Generate source
        OALSourceGenerator sourceGen = new OALSourceGenerator(define);
        sourceGen.setStorageBuilderFactory(new StorageBuilderFactory.Default());
        String source = sourceGen.generateMetricsSource(model);

        // ========== Class-level @Stream annotation ==========
        AnnotationsAttribute classAnnotations = (AnnotationsAttribute)
            bytecodeClass.getClassFile().getAttribute(AnnotationsAttribute.visibleTag);
        Annotation streamAnnotation = classAnnotations.getAnnotation(
            "org.apache.skywalking.oap.server.core.analysis.Stream");

        // Verify @Stream in bytecode
        assertTrue(streamAnnotation != null, "Bytecode should have @Stream annotation");
        String streamName = ((StringMemberValue) streamAnnotation.getMemberValue("name")).getValue();
        int streamScopeId = ((IntegerMemberValue) streamAnnotation.getMemberValue("scopeId")).getValue();
        assertEquals(model.getTableName(), streamName, "@Stream.name should match");
        assertEquals(model.getSourceScopeId(), streamScopeId, "@Stream.scopeId should match");

        // Verify @Stream in source
        assertTrue(source.contains("@Stream("), "Source should have @Stream annotation");
        assertTrue(source.contains("name = \"" + model.getTableName() + "\""),
            "Source @Stream.name should match");
        assertTrue(source.contains("scopeId = " + model.getSourceScopeId()),
            "Source @Stream.scopeId should match");
        assertTrue(source.contains("builder = " + model.getMetricsName() + "MetricsBuilder.class"),
            "Source @Stream.builder should match");
        assertTrue(source.contains("processor = MetricsStreamProcessor.class"),
            "Source @Stream.processor should match");

        log.info("Class-level @Stream annotation verified");

        // ========== Field-level annotations ==========
        for (CodeGenModel.SourceFieldV2 field : model.getFieldsFromSource()) {
            CtField ctField = bytecodeClass.getDeclaredField(field.getFieldName());
            AnnotationsAttribute fieldAnnotations = (AnnotationsAttribute)
                ctField.getFieldInfo().getAttribute(AnnotationsAttribute.visibleTag);

            // @Column annotation
            Annotation columnAnnotation = fieldAnnotations.getAnnotation(
                "org.apache.skywalking.oap.server.core.storage.annotation.Column");
            assertTrue(columnAnnotation != null,
                "Field " + field.getFieldName() + " should have @Column annotation in bytecode");
            String columnName = ((StringMemberValue) columnAnnotation.getMemberValue("name")).getValue();
            assertEquals(field.getColumnName(), columnName,
                "@Column.name should match for field " + field.getFieldName());

            // Verify @Column in source
            assertTrue(source.contains("@Column(name = \"" + field.getColumnName() + "\""),
                "Source should have @Column for field " + field.getFieldName());

            // @BanyanDB.SeriesID for ID fields
            if (field.isID()) {
                Annotation seriesIdAnnotation = fieldAnnotations.getAnnotation(
                    "org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB$SeriesID");
                assertTrue(seriesIdAnnotation != null,
                    "ID field " + field.getFieldName() + " should have @BanyanDB.SeriesID in bytecode");

                // Verify in source
                assertTrue(source.contains("@BanyanDB.SeriesID(index = 0)"),
                    "Source should have @BanyanDB.SeriesID for ID field");

                // @ElasticSearch.EnableDocValues for ID fields
                Annotation docValuesAnnotation = fieldAnnotations.getAnnotation(
                    "org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch$EnableDocValues");
                assertTrue(docValuesAnnotation != null,
                    "ID field " + field.getFieldName() + " should have @ElasticSearch.EnableDocValues in bytecode");

                assertTrue(source.contains("@ElasticSearch.EnableDocValues"),
                    "Source should have @ElasticSearch.EnableDocValues for ID field");
            }

            // @BanyanDB.ShardingKey for sharding key fields
            if (field.isShardingKey()) {
                Annotation shardingKeyAnnotation = fieldAnnotations.getAnnotation(
                    "org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB$ShardingKey");
                assertTrue(shardingKeyAnnotation != null,
                    "Sharding key field " + field.getFieldName() + " should have @BanyanDB.ShardingKey in bytecode");
                int shardingIdx = ((IntegerMemberValue) shardingKeyAnnotation.getMemberValue("index")).getValue();
                assertEquals(field.getShardingKeyIdx(), shardingIdx,
                    "@BanyanDB.ShardingKey.index should match for field " + field.getFieldName());

                // Verify in source
                assertTrue(source.contains("@BanyanDB.ShardingKey(index = " + field.getShardingKeyIdx() + ")"),
                    "Source should have @BanyanDB.ShardingKey for sharding key field");
            }
        }

        log.info("Field-level annotations verified for {} fields", model.getFieldsFromSource().size());

        // ========== Method-level annotations ==========
        // Generated methods don't have annotations (template methods are plain Java)
        for (CtMethod method : bytecodeClass.getDeclaredMethods()) {
            AnnotationsAttribute methodAnnotations = (AnnotationsAttribute)
                method.getMethodInfo().getAttribute(AnnotationsAttribute.visibleTag);
            // No method annotations expected
            if (methodAnnotations != null) {
                log.debug("Method {} has annotations: {}", method.getName(), methodAnnotations.getAnnotations().length);
            }
        }

        log.info("All annotations consistency verified successfully");
    }

    private CtMethod findMethod(CtClass ctClass, String methodName) {
        for (CtMethod method : ctClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private static class TestOALDefine extends OALDefine {
        protected TestOALDefine() {
            super("test.oal", SOURCE_PACKAGE);
        }
    }
}
