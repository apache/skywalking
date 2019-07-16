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

package org.apache.skywalking.oal.rt;

import freemarker.template.Configuration;
import freemarker.template.Version;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ClassFilePrinter;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ClassMemberValue;
import javassist.bytecode.annotation.IntegerMemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import org.apache.skywalking.oal.rt.meta.MetaReader;
import org.apache.skywalking.oal.rt.meta.MetaSettings;
import org.apache.skywalking.oal.rt.output.AllDispatcherContext;
import org.apache.skywalking.oal.rt.output.DispatcherContext;
import org.apache.skywalking.oal.rt.output.FileGenerator;
import org.apache.skywalking.oal.rt.parser.AnalysisResult;
import org.apache.skywalking.oal.rt.parser.MetricsHolder;
import org.apache.skywalking.oal.rt.parser.OALScripts;
import org.apache.skywalking.oal.rt.parser.ScriptParser;
import org.apache.skywalking.oal.rt.parser.SourceColumn;
import org.apache.skywalking.oal.rt.parser.SourceColumnsFactory;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.oal.rt.OALCompileException;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngine;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OAL Runtime is the class generation engine, which load the generated classes from OAL scrip definitions. This runtime
 * is loaded dynamically.
 *
 * @author wusheng
 */
public class OALRuntime implements OALEngine {
    private static final Logger logger = LoggerFactory.getLogger(OALRuntime.class);

    private static final Charset CLASS_FILE_CHARSET = Charset.forName("UTF-8");
    private static final String METRICS_FUNCTION_PACKAGE = "org.apache.skywalking.oap.server.core.analysis.metrics.";
    private static final String DYNAMIC_METRICS_CLASS_PACKAGE = "org.apache.skywalking.oal.rt.metrics.";
    private static final String DYNAMIC_METRICS_BUILDER_CLASS_PACKAGE = "org.apache.skywalking.oal.rt.metrics.builder";
    private static final String WITH_METADATA_INTERFACE = "org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata";
    private static final String METRICS_STREAM_PROCESSOR = "org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor";
    private static final String[] METRICS_CLASS_METHODS =
        {"id", "hashCode", "remoteHashCode", "equals", "serialize", "deserialize", "getMeta", "toDay"};
    private final ClassPool classPool;
    private Configuration configuration;
    private AllDispatcherContext allDispatcherContext;

    public OALRuntime() {
        classPool = ClassPool.getDefault();
        configuration = new Configuration(new Version("2.3.28"));
        configuration.setEncoding(Locale.ENGLISH, "UTF-8");
        configuration.setClassLoaderForTemplateLoading(FileGenerator.class.getClassLoader(), "/code-templates");
        allDispatcherContext = new AllDispatcherContext();
    }

    @Override public void start(ClassLoader currentClassLoader) throws ModuleStartException, OALCompileException {
        Reader read;
        try {
            read = ResourceUtils.read("scope-meta.yml");
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Can't locate scope-meta.yml", e);
        }

        MetaReader reader = new MetaReader();
        MetaSettings metaSettings = reader.read(read);
        SourceColumnsFactory.setSettings(metaSettings);

        try {
            MetricsHolder.init();
        } catch (IOException e) {
            throw new ModuleStartException("load metrics functions error.", e);
        }

        try {
            read = ResourceUtils.read("official_analysis.oal");
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Can't locate official_analysis.oal", e);
        }

        OALScripts oalScripts;
        try {
            ScriptParser scriptParser = ScriptParser.createFromFile(read);
            oalScripts = scriptParser.parse();
        } catch (IOException e) {
            throw new ModuleStartException("OAL script parse analysis failure.", e);
        }

        this.generateClassAtRuntime(oalScripts);
    }

    private void generateClassAtRuntime(OALScripts oalScripts) throws OALCompileException {
        List<AnalysisResult> metricsStmts = oalScripts.getMetricsStmts();
        metricsStmts.forEach(this::buildDispatcherContext);

        for (AnalysisResult metricsStmt : metricsStmts) {
            generateMetricsClass(metricsStmt);
        }

    }

    private void generateMetricsClass(AnalysisResult metricsStmt) throws OALCompileException {
        CtClass parentMetricsClass = null;
        try {
            parentMetricsClass = classPool.get(METRICS_FUNCTION_PACKAGE + metricsStmt.getMetricsClassName());
        } catch (NotFoundException e) {
            logger.error("Can't find parent class for " + metricsStmt.getMetricsName() + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }
        CtClass metricsClass = classPool.makeClass(metricsClassName(metricsStmt), parentMetricsClass);
        try {
            metricsClass.addInterface(classPool.get(WITH_METADATA_INTERFACE));
        } catch (NotFoundException e) {
            logger.error("Can't find WithMetadata interface for " + metricsStmt.getMetricsName() + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        ClassFile metricsClassClassFile = metricsClass.getClassFile();
        ConstPool constPool = metricsClassClassFile.getConstPool();

        /**
         * Create empty construct
         */
        try {
            CtConstructor defaultConstructor = CtNewConstructor.make("public " + metricsStmt.getMetricsName() + "Metrics() {}", metricsClass);
            metricsClass.addConstructor(defaultConstructor);
        } catch (CannotCompileException e) {
            logger.error("Can't add empty constructor in " + metricsStmt.getMetricsName() + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        /**
         * Add fields with annotations.
         *
         * private ${sourceField.typeName} ${sourceField.fieldName};
         */
        for (SourceColumn field : metricsStmt.getFieldsFromSource()) {
            try {
                CtField newField = CtField.make("private " + field.getType().getName() + " " + field.getFieldName() + ";", metricsClass);

                metricsClass.addField(newField);

                metricsClass.addMethod(CtNewMethod.getter(field.getFieldGetter(), newField));
                metricsClass.addMethod(CtNewMethod.setter(field.getFieldSetter(), newField));

                AnnotationsAttribute annotationsAttribute = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
                /**
                 * Add @Column(columnName = "${sourceField.columnName}")
                 */
                Annotation columnAnnotation = new Annotation(Column.class.getName(), constPool);
                columnAnnotation.addMemberValue("columnName", new StringMemberValue(field.getColumnName(), constPool));
                annotationsAttribute.addAnnotation(columnAnnotation);

                if (field.isID()) {
                    /**
                     * Add @IDColumn
                     */
                    Annotation idAnnotation = new Annotation(Column.class.getName(), constPool);
                    annotationsAttribute.addAnnotation(idAnnotation);
                }

                newField.getFieldInfo().addAttribute(annotationsAttribute);

            } catch (CannotCompileException e) {
                logger.error("Can't add field(including set/get) " + field.getFieldName() + " in " + metricsStmt.getMetricsName() + ".", e);
                throw new OALCompileException(e.getMessage(), e);
            }
        }

        /**
         * Generate methods
         */
        for (String method : METRICS_CLASS_METHODS) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration.getTemplate("metrics/" + method + ".ftl").process(metricsStmt, methodEntity);
                metricsClass.addMethod(CtNewMethod.make(methodEntity.toString(), metricsClass));
            } catch (Exception e) {
                logger.error("Can't generate method " + method + " for " + metricsStmt.getMetricsName() + ".", e);
                throw new OALCompileException(e.getMessage(), e);
            }
        }


        /**
         * Add following annotation to the metrics class
         *
         * at Stream(name = "${tableName}", scopeId = ${sourceScopeId}, builder = ${metricsName}Metrics.Builder.class, processor = MetricsStreamProcessor.class)
         */
        AnnotationsAttribute annotationsAttribute = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        Annotation streamAnnotation = new Annotation(Stream.class.getName(), constPool);
        streamAnnotation.addMemberValue("name", new StringMemberValue(metricsStmt.getTableName(), constPool));
        streamAnnotation.addMemberValue("scopeId", new IntegerMemberValue(constPool, metricsStmt.getSourceScopeId()));
        streamAnnotation.addMemberValue("builder", new ClassMemberValue(metricsBuilderClassName(metricsStmt), constPool));
        streamAnnotation.addMemberValue("processor", new ClassMemberValue(METRICS_STREAM_PROCESSOR, constPool));

        annotationsAttribute.addAnnotation(streamAnnotation);
        metricsClassClassFile.addAttribute(annotationsAttribute);

        try {
            metricsClass.toClass();
        } catch (CannotCompileException e) {
            logger.error("Can't compile " + metricsStmt.getMetricsName() + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        ClassFilePrinter.print(metricsClassClassFile);
    }

    private String metricsClassName(AnalysisResult metricsStmt) {
        return DYNAMIC_METRICS_CLASS_PACKAGE + metricsStmt.getMetricsName() + "Metrics";
    }

    private String metricsBuilderClassName(AnalysisResult metricsStmt) {
        return DYNAMIC_METRICS_BUILDER_CLASS_PACKAGE + metricsStmt.getMetricsName() + "MetricsBuilder";
    }

    private void buildDispatcherContext(AnalysisResult metricsStmt) {
        String sourceName = metricsStmt.getSourceName();

        DispatcherContext context = allDispatcherContext.getAllContext().get(sourceName);
        if (context == null) {
            context = new DispatcherContext();
            context.setSource(sourceName);
            context.setPackageName(sourceName.toLowerCase());
            allDispatcherContext.getAllContext().put(sourceName, context);
        }
        context.getMetrics().add(metricsStmt);
    }
}
