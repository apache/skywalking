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
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import javassist.bytecode.ConstPool;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ClassMemberValue;
import javassist.bytecode.annotation.IntegerMemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.apache.skywalking.oap.server.core.WorkPath;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.oal.rt.OALCompileException;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.source.oal.rt.dispatcher.DispatcherClassPackageHolder;
import org.apache.skywalking.oap.server.core.source.oal.rt.metrics.MetricClassPackageHolder;
import org.apache.skywalking.oap.server.core.source.oal.rt.metrics.builder.MetricBuilderClassPackageHolder;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * V2 OAL class generator.
 *
 * Generates metrics, builder, and dispatcher classes using V2 models and templates.
 */
@Slf4j
public class OALClassGeneratorV2 {

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
    private static final String[] METRICS_BUILDER_CLASS_METHODS = {
        "entity2Storage",
        "storage2Entity"
    };

    private static final String CLASS_FILE_CHARSET = "UTF-8";

    private boolean openEngineDebug;
    private final ClassPool classPool;
    private final OALDefine oalDefine;
    private Configuration configuration;
    private ClassLoader currentClassLoader;
    private StorageBuilderFactory storageBuilderFactory;
    private static String GENERATED_FILE_PATH;

    public OALClassGeneratorV2(OALDefine define) {
        this(define, ClassPool.getDefault());
    }

    /**
     * Constructor with custom ClassPool for test isolation.
     */
    public OALClassGeneratorV2(OALDefine define, ClassPool classPool) {
        openEngineDebug = StringUtil.isNotEmpty(System.getenv("SW_OAL_ENGINE_DEBUG"));
        this.classPool = classPool;
        oalDefine = define;

        configuration = new Configuration(new Version("2.3.28"));
        configuration.setEncoding(Locale.ENGLISH, CLASS_FILE_CHARSET);
        // Use V2-specific FreeMarker templates
        configuration.setClassLoaderForTemplateLoading(OALClassGeneratorV2.class.getClassLoader(), "/code-templates-v2");
    }

    /**
     * Generate classes from V2 code generation models.
     */
    public void generateClassAtRuntime(
        List<CodeGenModel> codeGenModels,
        List<String> disabledSources,
        List<Class> metricsClasses,
        List<Class> dispatcherClasses) throws OALCompileException {

        // Build dispatcher context (group by source)
        Map<String, DispatcherContextV2> allDispatcherContext = buildDispatcherContext(codeGenModels);

        // Generate metrics and builder classes
        for (CodeGenModel model : codeGenModels) {
            metricsClasses.add(generateMetricsClass(model));
            generateMetricsBuilderClass(model);
        }

        // Generate dispatcher classes
        for (Map.Entry<String, DispatcherContextV2> entry : allDispatcherContext.entrySet()) {
            dispatcherClasses.add(generateDispatcherClass(entry.getKey(), entry.getValue()));
        }

        // Register disabled sources
        for (String disabledSource : disabledSources) {
            DisableRegister.INSTANCE.add(disabledSource);
        }
    }

    /**
     * Build dispatcher context from code generation models.
     */
    private Map<String, DispatcherContextV2> buildDispatcherContext(List<CodeGenModel> codeGenModels) {
        Map<String, DispatcherContextV2> contextMap = new HashMap<>();

        for (CodeGenModel model : codeGenModels) {
            String sourceName = model.getSourceName();

            DispatcherContextV2 context = contextMap.computeIfAbsent(sourceName, name -> {
                DispatcherContextV2 ctx = new DispatcherContextV2();
                ctx.setSourcePackage(oalDefine.getSourcePackage());
                ctx.setSourceName(name);
                ctx.setPackageName(name.toLowerCase());
                ctx.setSourceDecorator(model.getSourceDecorator());
                return ctx;
            });

            context.getMetrics().add(model);
        }

        return contextMap;
    }

    /**
     * Generate metrics class using V2 model and templates.
     */
    private Class generateMetricsClass(CodeGenModel model) throws OALCompileException {
        String className = metricsClassName(model, false);
        CtClass parentMetricsClass;

        try {
            parentMetricsClass = classPool.get(METRICS_FUNCTION_PACKAGE + model.getMetricsClassName());
        } catch (NotFoundException e) {
            log.error("Can't find parent class for " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        CtClass metricsClass = classPool.makeClass(metricsClassName(model, true), parentMetricsClass);

        try {
            metricsClass.addInterface(classPool.get(WITH_METADATA_INTERFACE));
        } catch (NotFoundException e) {
            log.error("Can't find WithMetadata interface for " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        ClassFile metricsClassClassFile = metricsClass.getClassFile();
        ConstPool constPool = metricsClassClassFile.getConstPool();

        // Create empty constructor
        try {
            CtConstructor defaultConstructor = CtNewConstructor.make("public " + className + "() {}", metricsClass);
            metricsClass.addConstructor(defaultConstructor);
        } catch (CannotCompileException e) {
            log.error("Can't add empty constructor in " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        // Add fields with annotations
        for (CodeGenModel.SourceFieldV2 field : model.getFieldsFromSource()) {
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
                    // Add SeriesID annotation
                    Annotation banyanSeriesIDAnnotation = new Annotation(BanyanDB.SeriesID.class.getName(), constPool);
                    banyanSeriesIDAnnotation.addMemberValue("index", new IntegerMemberValue(constPool, 0));
                    annotationsAttribute.addAnnotation(banyanSeriesIDAnnotation);

                    // Enable doc values
                    Annotation enableDocValuesAnnotation = new Annotation(ElasticSearch.EnableDocValues.class.getName(), constPool);
                    annotationsAttribute.addAnnotation(enableDocValuesAnnotation);
                }

                if (field.isShardingKey()) {
                    Annotation banyanShardingKeyAnnotation = new Annotation(BanyanDB.ShardingKey.class.getName(), constPool);
                    banyanShardingKeyAnnotation.addMemberValue("index", new IntegerMemberValue(constPool, field.getShardingKeyIdx()));
                    annotationsAttribute.addAnnotation(banyanShardingKeyAnnotation);
                }

                newField.getFieldInfo().addAttribute(annotationsAttribute);
            } catch (CannotCompileException e) {
                log.error("Can't add field " + field.getFieldName() + " in " + className + ".", e);
                throw new OALCompileException(e.getMessage(), e);
            }
        }

        // Generate methods using V2 templates
        for (String method : METRICS_CLASS_METHODS) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration.getTemplate("metrics/" + method + ".ftl").process(model, methodEntity);
                metricsClass.addMethod(CtNewMethod.make(methodEntity.toString(), metricsClass));
            } catch (Exception e) {
                log.error("Can't generate method " + method + " for " + className + ".", e);
                throw new OALCompileException(e.getMessage(), e);
            }
        }

        // Add @Stream annotation
        AnnotationsAttribute annotationsAttribute = new AnnotationsAttribute(
            constPool, AnnotationsAttribute.visibleTag);
        Annotation streamAnnotation = new Annotation(Stream.class.getName(), constPool);
        streamAnnotation.addMemberValue("name", new StringMemberValue(model.getTableName(), constPool));
        streamAnnotation.addMemberValue("scopeId", new IntegerMemberValue(constPool, model.getSourceScopeId()));
        streamAnnotation.addMemberValue(
            "builder", new ClassMemberValue(metricsBuilderClassName(model, true), constPool));
        streamAnnotation.addMemberValue("processor", new ClassMemberValue(METRICS_STREAM_PROCESSOR, constPool));

        annotationsAttribute.addAnnotation(streamAnnotation);
        metricsClassClassFile.addAttribute(annotationsAttribute);

        Class targetClass;
        try {
            if (SystemUtils.isJavaVersionAtMost(JavaVersion.JAVA_1_8)) {
                targetClass = metricsClass.toClass(currentClassLoader, null);
            } else {
                targetClass = metricsClass.toClass(MetricClassPackageHolder.class);
            }
        } catch (CannotCompileException e) {
            log.error("Can't compile/load " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        log.debug("Generated V2 metrics class: " + metricsClass.getName());
        writeGeneratedFile(metricsClass, "metrics");
        writeSourceFile(model, "metrics");

        return targetClass;
    }

    /**
     * Generate metrics builder class using V2 model and templates.
     */
    private void generateMetricsBuilderClass(CodeGenModel model) throws OALCompileException {
        String className = metricsBuilderClassName(model, false);
        CtClass metricsBuilderClass = classPool.makeClass(metricsBuilderClassName(model, true));

        try {
            metricsBuilderClass.addInterface(classPool.get(storageBuilderFactory.builderTemplate().getSuperClass()));
        } catch (NotFoundException e) {
            log.error("Can't find StorageBuilder interface for " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        // Create empty constructor
        try {
            CtConstructor defaultConstructor = CtNewConstructor.make(
                "public " + className + "() {}", metricsBuilderClass);
            metricsBuilderClass.addConstructor(defaultConstructor);
        } catch (CannotCompileException e) {
            log.error("Can't add empty constructor in " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        // Generate methods using V2 templates
        for (String method : METRICS_BUILDER_CLASS_METHODS) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration
                    .getTemplate(storageBuilderFactory.builderTemplate().getTemplatePath() + "/" + method + ".ftl")
                    .process(model, methodEntity);
                metricsBuilderClass.addMethod(CtNewMethod.make(methodEntity.toString(), metricsBuilderClass));
            } catch (Exception e) {
                log.error("Can't generate method " + method + " for " + className + ".", e);
                throw new OALCompileException(e.getMessage(), e);
            }
        }

        try {
            if (SystemUtils.isJavaVersionAtMost(JavaVersion.JAVA_1_8)) {
                metricsBuilderClass.toClass(currentClassLoader, null);
            } else {
                metricsBuilderClass.toClass(MetricBuilderClassPackageHolder.class);
            }
        } catch (CannotCompileException e) {
            log.error("Can't compile/load " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        writeGeneratedFile(metricsBuilderClass, "metrics/builder");
    }

    /**
     * Generate dispatcher class using V2 model and templates.
     */
    private Class generateDispatcherClass(String scopeName, DispatcherContextV2 dispatcherContext) throws OALCompileException {
        String className = dispatcherClassName(scopeName, false);
        CtClass dispatcherClass = classPool.makeClass(dispatcherClassName(scopeName, true));

        try {
            CtClass dispatcherInterface = classPool.get(DISPATCHER_INTERFACE);
            dispatcherClass.addInterface(dispatcherInterface);

            // Set generic signature
            String sourceClassName = oalDefine.getSourcePackage() + dispatcherContext.getSourceName();
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
        } catch (NotFoundException e) {
            log.error("Can't find Dispatcher interface for " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        // Generate doMetrics methods for each metric
        for (CodeGenModel metric : dispatcherContext.getMetrics()) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration.getTemplate("dispatcher/doMetrics.ftl").process(metric, methodEntity);
                dispatcherClass.addMethod(CtNewMethod.make(methodEntity.toString(), dispatcherClass));
            } catch (Exception e) {
                log.error("Can't generate method do" + metric.getMetricsName() + " for " + className + ".", e);
                log.error("Method body: {}", methodEntity);
                throw new OALCompileException(e.getMessage(), e);
            }
        }

        // Generate dispatch method
        try {
            StringWriter methodEntity = new StringWriter();
            configuration.getTemplate("dispatcher/dispatch.ftl").process(dispatcherContext, methodEntity);
            dispatcherClass.addMethod(CtNewMethod.make(methodEntity.toString(), dispatcherClass));
        } catch (Exception e) {
            log.error("Can't generate method dispatch for " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        Class targetClass;
        try {
            if (SystemUtils.isJavaVersionAtMost(JavaVersion.JAVA_1_8)) {
                targetClass = dispatcherClass.toClass(currentClassLoader, null);
            } else {
                targetClass = dispatcherClass.toClass(DispatcherClassPackageHolder.class);
            }
        } catch (CannotCompileException e) {
            log.error("Can't compile/load " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        writeGeneratedFile(dispatcherClass, "dispatcher");
        return targetClass;
    }

    private String metricsClassName(CodeGenModel model, boolean fullName) {
        return (fullName ? oalDefine.getDynamicMetricsClassPackage() : "") + model.getMetricsName() + "Metrics";
    }

    private String metricsBuilderClassName(CodeGenModel model, boolean fullName) {
        return (fullName ? oalDefine.getDynamicMetricsBuilderClassPackage() : "") + model.getMetricsName() + "MetricsBuilder";
    }

    private String dispatcherClassName(String scopeName, boolean fullName) {
        return (fullName ? oalDefine.getDynamicDispatcherClassPackage() : "") + scopeName + "Dispatcher";
    }

    public void prepareRTTempFolder() {
        if (openEngineDebug) {
            File workPath = WorkPath.getPath();
            File folder = new File(workPath.getParentFile(), "oal-rt-v2/");
            if (folder.exists()) {
                try {
                    FileUtils.deleteDirectory(folder);
                } catch (IOException e) {
                    log.warn("Can't delete " + folder.getAbsolutePath() + " temp folder.", e);
                }
            }
            folder.mkdirs();
        }
    }

    private void writeGeneratedFile(CtClass ctClass, String type) throws OALCompileException {
        if (openEngineDebug) {
            String className = ctClass.getSimpleName();
            DataOutputStream printWriter = null;
            try {
                File folder = new File(getGeneratedFilePath() + File.separator + type);
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                File file = new File(folder, className + ".class");
                if (file.exists()) {
                    file.delete();
                }
                file.createNewFile();

                printWriter = new DataOutputStream(new FileOutputStream(file));
                ctClass.toBytecode(printWriter);
                printWriter.flush();
            } catch (IOException e) {
                log.warn("Can't create " + className + ".txt, ignore.", e);
                return;
            } catch (CannotCompileException e) {
                log.warn("Can't compile " + className + ".class(should not happen), ignore.", e);
                return;
            } finally {
                if (printWriter != null) {
                    try {
                        printWriter.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            }
        }
    }

    public void setCurrentClassLoader(ClassLoader currentClassLoader) {
        this.currentClassLoader = currentClassLoader;
    }

    public void setStorageBuilderFactory(StorageBuilderFactory storageBuilderFactory) {
        this.storageBuilderFactory = storageBuilderFactory;
    }

    public static void setGeneratedFilePath(String generatedFilePath) {
        GENERATED_FILE_PATH = generatedFilePath;
    }

    public static String getGeneratedFilePath() {
        if (GENERATED_FILE_PATH == null) {
            return String.valueOf(new File(WorkPath.getPath().getParentFile(), "oal-rt-v2/"));
        }
        return GENERATED_FILE_PATH;
    }

    public void setOpenEngineDebug(boolean debug) {
        this.openEngineDebug = debug;
    }

    /**
     * Generate complete Java source code for a metrics class.
     * This is useful for comparing V1 vs V2 generated code.
     *
     * @param model The code generation model
     * @return Complete Java source code as a string
     */
    public String generateMetricsClassSourceCode(CodeGenModel model) throws OALCompileException {
        StringBuilder source = new StringBuilder();

        // Package declaration
        source.append("package ").append(oalDefine.getDynamicMetricsClassPackage()).append(";\n\n");

        // Imports
        source.append("import org.apache.skywalking.oap.server.core.analysis.Stream;\n");
        source.append("import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;\n");
        source.append("import org.apache.skywalking.oap.server.core.analysis.metrics.").append(model.getMetricsClassName()).append(";\n");
        source.append("import org.apache.skywalking.oap.server.core.storage.annotation.*;\n");
        source.append("import lombok.Getter;\n");
        source.append("import lombok.Setter;\n\n");

        // Class-level @Stream annotation
        source.append("@Stream(\n");
        source.append("    name = \"").append(model.getTableName()).append("\",\n");
        source.append("    scopeId = ").append(model.getSourceScopeId()).append(",\n");
        source.append("    builder = ").append(metricsBuilderClassName(model, false)).append(".class,\n");
        source.append("    processor = ").append(METRICS_STREAM_PROCESSOR).append(".class\n");
        source.append(")\n");

        // Class declaration
        String className = metricsClassName(model, false);
        source.append("public class ").append(className)
            .append(" extends ").append(model.getMetricsClassName())
            .append(" implements WithMetadata {\n\n");

        // Fields with annotations
        for (CodeGenModel.SourceFieldV2 field : model.getFieldsFromSource()) {
            source.append("    @Column(name = \"").append(field.getColumnName()).append("\"");
            if (field.getType().equals(String.class)) {
                source.append(", length = ").append(field.getLength());
            }
            source.append(")\n");

            if (field.isID()) {
                source.append("    @BanyanDB.SeriesID(index = 0)\n");
                source.append("    @ElasticSearch.EnableDocValues\n");
            }

            if (field.isShardingKey()) {
                source.append("    @BanyanDB.ShardingKey(index = ").append(field.getShardingKeyIdx()).append(")\n");
            }

            source.append("    @Getter @Setter\n");
            source.append("    private ").append(field.getType().getSimpleName())
                .append(" ").append(field.getFieldName()).append(";\n\n");
        }

        // Constructor
        source.append("    public ").append(className).append("() {\n");
        source.append("    }\n\n");

        // Methods from templates
        for (String method : METRICS_CLASS_METHODS) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration.getTemplate("metrics/" + method + ".ftl").process(model, methodEntity);
                source.append("    ").append(methodEntity.toString()).append("\n\n");
            } catch (Exception e) {
                log.error("Can't generate method " + method + " for source code.", e);
                throw new OALCompileException(e.getMessage(), e);
            }
        }

        source.append("}\n");

        return source.toString();
    }

    /**
     * Write complete Java source file to disk for comparison.
     */
    private void writeSourceFile(CodeGenModel model, String type) throws OALCompileException {
        if (openEngineDebug) {
            String className = metricsClassName(model, false);
            try {
                File folder = new File(getGeneratedFilePath() + File.separator + type + "-source");
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                File file = new File(folder, className + ".java");
                if (file.exists()) {
                    file.delete();
                }

                String sourceCode = generateMetricsClassSourceCode(model);
                FileUtils.writeStringToFile(file, sourceCode, CLASS_FILE_CHARSET);

                log.debug("Wrote source file: " + file.getAbsolutePath());
            } catch (IOException e) {
                log.warn("Can't write source file for " + className + ", ignore.", e);
            }
        }
    }

    /**
     * V2 dispatcher context for grouping metrics by source.
     */
    public static class DispatcherContextV2 {
        private String sourcePackage;
        private String sourceName;
        private String packageName;
        private String sourceDecorator;
        private List<CodeGenModel> metrics = new java.util.ArrayList<>();

        public String getSourcePackage() {
            return sourcePackage;
        }

        public void setSourcePackage(String sourcePackage) {
            this.sourcePackage = sourcePackage;
        }

        public String getSourceName() {
            return sourceName;
        }

        public void setSourceName(String sourceName) {
            this.sourceName = sourceName;
        }

        public String getPackageName() {
            return packageName;
        }

        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        public String getSourceDecorator() {
            return sourceDecorator;
        }

        public void setSourceDecorator(String sourceDecorator) {
            this.sourceDecorator = sourceDecorator;
        }

        public List<CodeGenModel> getMetrics() {
            return metrics;
        }

        public void setMetrics(List<CodeGenModel> metrics) {
            this.metrics = metrics;
        }
    }
}
