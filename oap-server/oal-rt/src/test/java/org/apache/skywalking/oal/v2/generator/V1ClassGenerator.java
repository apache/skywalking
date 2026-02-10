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
import java.util.Locale;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ClassMemberValue;
import javassist.bytecode.annotation.IntegerMemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import org.apache.skywalking.oal.rt.OALRuntime;
import org.apache.skywalking.oal.rt.output.DispatcherContext;
import org.apache.skywalking.oal.rt.parser.AnalysisResult;
import org.apache.skywalking.oal.rt.parser.SourceColumn;
import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.oal.rt.OALCompileException;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;

/**
 * V1 class generator that produces CtClass without loading to JVM.
 * Used for bytecode comparison tests.
 */
public class V1ClassGenerator {

    private static final String METRICS_FUNCTION_PACKAGE = "org.apache.skywalking.oap.server.core.analysis.metrics.";
    private static final String WITH_METADATA_INTERFACE = "org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata";
    private static final String DISPATCHER_INTERFACE = "org.apache.skywalking.oap.server.core.analysis.SourceDispatcher";
    private static final String METRICS_STREAM_PROCESSOR = "org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor";
    private static final String[] METRICS_CLASS_METHODS = {
        "id",
        "hashCode",
        "remoteHashCode",
        "equals",
        "serialize",
        "deserialize",
        "getMeta",
        "toHour",
        "toDay"
    };

    private final ClassPool classPool;
    private final OALDefine oalDefine;
    private final Configuration configuration;

    public V1ClassGenerator(OALDefine define, ClassPool classPool) {
        this.classPool = classPool;
        this.oalDefine = define;

        configuration = new Configuration(new Version("2.3.28"));
        configuration.setEncoding(Locale.ENGLISH, "UTF-8");
        configuration.setClassLoaderForTemplateLoading(OALRuntime.class.getClassLoader(), "/code-templates");
    }

    /**
     * Generate metrics CtClass without loading to JVM.
     */
    public CtClass generateMetricsCtClass(AnalysisResult metricsStmt) throws OALCompileException {
        String className = metricsClassName(metricsStmt, false);
        CtClass parentMetricsClass;

        try {
            parentMetricsClass = classPool.get(METRICS_FUNCTION_PACKAGE + metricsStmt.getMetricsClassName());
        } catch (Exception e) {
            throw new OALCompileException("Can't find parent class for " + className, e);
        }

        CtClass metricsClass = classPool.makeClass(metricsClassName(metricsStmt, true), parentMetricsClass);

        try {
            metricsClass.addInterface(classPool.get(WITH_METADATA_INTERFACE));
        } catch (Exception e) {
            throw new OALCompileException("Can't find WithMetadata interface for " + className, e);
        }

        ClassFile metricsClassClassFile = metricsClass.getClassFile();
        ConstPool constPool = metricsClassClassFile.getConstPool();

        // Create empty constructor
        try {
            CtConstructor defaultConstructor = CtNewConstructor.make("public " + className + "() {}", metricsClass);
            metricsClass.addConstructor(defaultConstructor);
        } catch (Exception e) {
            throw new OALCompileException("Can't add empty constructor in " + className, e);
        }

        // Add fields with annotations
        for (SourceColumn field : metricsStmt.getFieldsFromSource()) {
            try {
                CtField newField = CtField.make(
                    "private " + field.getType().getName() + " " + field.getFieldName() + ";", metricsClass);

                metricsClass.addField(newField);
                metricsClass.addMethod(CtNewMethod.getter(field.getFieldGetter(), newField));
                metricsClass.addMethod(CtNewMethod.setter(field.getFieldSetter(), newField));

                AnnotationsAttribute annotationsAttribute = new AnnotationsAttribute(
                    constPool, AnnotationsAttribute.visibleTag);

                // Add @Column annotation
                Annotation columnAnnotation = new Annotation(Column.class.getName(), constPool);
                columnAnnotation.addMemberValue("name", new StringMemberValue(field.getColumnName(), constPool));
                if (field.getType().equals(String.class)) {
                    columnAnnotation.addMemberValue("length", new IntegerMemberValue(constPool, field.getLength()));
                }
                annotationsAttribute.addAnnotation(columnAnnotation);

                if (field.isID()) {
                    Annotation banyanSeriesIDAnnotation = new Annotation(BanyanDB.SeriesID.class.getName(), constPool);
                    banyanSeriesIDAnnotation.addMemberValue("index", new IntegerMemberValue(constPool, 0));
                    annotationsAttribute.addAnnotation(banyanSeriesIDAnnotation);

                    Annotation enableDocValuesAnnotation = new Annotation(ElasticSearch.EnableDocValues.class.getName(), constPool);
                    annotationsAttribute.addAnnotation(enableDocValuesAnnotation);
                }

                if (field.isShardingKey()) {
                    Annotation banyanShardingKeyAnnotation = new Annotation(BanyanDB.ShardingKey.class.getName(), constPool);
                    banyanShardingKeyAnnotation.addMemberValue("index", new IntegerMemberValue(constPool, field.getShardingKeyIdx()));
                    annotationsAttribute.addAnnotation(banyanShardingKeyAnnotation);
                }

                newField.getFieldInfo().addAttribute(annotationsAttribute);
            } catch (Exception e) {
                throw new OALCompileException("Can't add field " + field.getFieldName() + " in " + className, e);
            }
        }

        // Generate methods using V1 templates
        for (String method : METRICS_CLASS_METHODS) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration.getTemplate("metrics/" + method + ".ftl").process(metricsStmt, methodEntity);
                metricsClass.addMethod(CtNewMethod.make(methodEntity.toString(), metricsClass));
            } catch (Exception e) {
                throw new OALCompileException("Can't generate method " + method + " for " + className, e);
            }
        }

        // Add @Stream annotation
        AnnotationsAttribute annotationsAttribute = new AnnotationsAttribute(
            constPool, AnnotationsAttribute.visibleTag);
        Annotation streamAnnotation = new Annotation(Stream.class.getName(), constPool);
        streamAnnotation.addMemberValue("name", new StringMemberValue(metricsStmt.getTableName(), constPool));
        streamAnnotation.addMemberValue("scopeId", new IntegerMemberValue(constPool, metricsStmt.getFrom().getSourceScopeId()));
        streamAnnotation.addMemberValue(
            "builder", new ClassMemberValue(metricsBuilderClassName(metricsStmt, true), constPool));
        streamAnnotation.addMemberValue("processor", new ClassMemberValue(METRICS_STREAM_PROCESSOR, constPool));

        annotationsAttribute.addAnnotation(streamAnnotation);
        metricsClassClassFile.addAttribute(annotationsAttribute);

        return metricsClass;
    }

    /**
     * Generate dispatcher CtClass without loading to JVM.
     */
    public CtClass generateDispatcherCtClass(String scopeName, DispatcherContext dispatcherContext) throws OALCompileException {
        String className = dispatcherClassName(scopeName, false);
        CtClass dispatcherClass = classPool.makeClass(dispatcherClassName(scopeName, true));

        try {
            CtClass dispatcherInterface = classPool.get(DISPATCHER_INTERFACE);
            dispatcherClass.addInterface(dispatcherInterface);

            // Set generic signature
            String sourceClassName = oalDefine.getSourcePackage() + dispatcherContext.getSource();
            SignatureAttribute.ClassSignature dispatcherSignature =
                new SignatureAttribute.ClassSignature(
                    null, null,
                    new SignatureAttribute.ClassType[]{
                        new SignatureAttribute.ClassType(
                            SourceDispatcher.class.getCanonicalName(),
                            new SignatureAttribute.TypeArgument[]{
                                new SignatureAttribute.TypeArgument(
                                    new SignatureAttribute.ClassType(sourceClassName))
                            }
                        )
                    }
                );

            dispatcherClass.setGenericSignature(dispatcherSignature.encode());
        } catch (Exception e) {
            throw new OALCompileException("Can't find Dispatcher interface for " + className, e);
        }

        // Generate doMetrics methods for each metric
        for (AnalysisResult dispatcherContextMetric : dispatcherContext.getMetrics()) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration.getTemplate("dispatcher/doMetrics.ftl").process(dispatcherContextMetric, methodEntity);
                dispatcherClass.addMethod(CtNewMethod.make(methodEntity.toString(), dispatcherClass));
            } catch (Exception e) {
                throw new OALCompileException("Can't generate method do" + dispatcherContextMetric.getMetricsName() + " for " + className, e);
            }
        }

        // Generate dispatch method
        try {
            StringWriter methodEntity = new StringWriter();
            configuration.getTemplate("dispatcher/dispatch.ftl").process(dispatcherContext, methodEntity);
            dispatcherClass.addMethod(CtNewMethod.make(methodEntity.toString(), dispatcherClass));
        } catch (Exception e) {
            throw new OALCompileException("Can't generate method dispatch for " + className, e);
        }

        return dispatcherClass;
    }

    private String metricsClassName(AnalysisResult metricsStmt, boolean fullName) {
        return (fullName ? oalDefine.getDynamicMetricsClassPackage() : "") + metricsStmt.getMetricsName() + "Metrics";
    }

    private String metricsBuilderClassName(AnalysisResult metricsStmt, boolean fullName) {
        return (fullName ? oalDefine.getDynamicMetricsBuilderClassPackage() : "") + metricsStmt.getMetricsName() + "MetricsBuilder";
    }

    private String dispatcherClassName(String scopeName, boolean fullName) {
        return (fullName ? oalDefine.getDynamicDispatcherClassPackage() : "") + scopeName + "Dispatcher";
    }
}
