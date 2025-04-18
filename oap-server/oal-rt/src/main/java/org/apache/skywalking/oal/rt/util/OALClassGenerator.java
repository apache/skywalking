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

package org.apache.skywalking.oal.rt.util;

import freemarker.template.Configuration;
import freemarker.template.Version;
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
import org.apache.skywalking.oal.rt.OALRuntime;
import org.apache.skywalking.oal.rt.output.AllDispatcherContext;
import org.apache.skywalking.oal.rt.output.DispatcherContext;
import org.apache.skywalking.oal.rt.parser.AnalysisResult;
import org.apache.skywalking.oal.rt.parser.OALScripts;
import org.apache.skywalking.oal.rt.parser.SourceColumn;
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

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
public class OALClassGenerator {

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

    private AllDispatcherContext allDispatcherContext;

    private final ClassPool classPool;

    private final OALDefine oalDefine;

    private Configuration configuration;

    private ClassLoader currentClassLoader;

    private StorageBuilderFactory storageBuilderFactory;

    private static String GENERATED_FILE_PATH;

    public OALClassGenerator(OALDefine define) {
        openEngineDebug = StringUtil.isNotEmpty(System.getenv("SW_OAL_ENGINE_DEBUG"));
        allDispatcherContext = new AllDispatcherContext();
        classPool = ClassPool.getDefault();
        oalDefine = define;

        configuration = new Configuration(new Version("2.3.28"));
        configuration.setEncoding(Locale.ENGLISH, CLASS_FILE_CHARSET);
        configuration.setClassLoaderForTemplateLoading(OALRuntime.class.getClassLoader(), "/code-templates");
    }

    public void generateClassAtRuntime(OALScripts oalScripts, List<Class> metricsClasses, List<Class> dispatcherClasses) throws OALCompileException {
        List<AnalysisResult> metricsStmts = oalScripts.getMetricsStmts();
        metricsStmts.forEach(this::buildDispatcherContext);

        for (AnalysisResult metricsStmt : metricsStmts) {
            metricsClasses.add(generateMetricsClass(metricsStmt));
            generateMetricsBuilderClass(metricsStmt);
        }

        for (Map.Entry<String, DispatcherContext> entry : allDispatcherContext.getAllContext().entrySet()) {
            dispatcherClasses.add(generateDispatcherClass(entry.getKey(), entry.getValue()));
        }

        oalScripts.getDisableCollection().getAllDisableSources().forEach(disable -> {
            DisableRegister.INSTANCE.add(disable);
        });
    }

    /**
     * Generate metrics class, and inject it to classloader
     */
    private Class generateMetricsClass(AnalysisResult metricsStmt) throws OALCompileException {
        String className = metricsClassName(metricsStmt, false);
        CtClass parentMetricsClass = null;
        try {
            parentMetricsClass = classPool.get(METRICS_FUNCTION_PACKAGE + metricsStmt.getMetricsClassName());
        } catch (NotFoundException e) {
            log.error("Can't find parent class for " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }
        CtClass metricsClass = classPool.makeClass(metricsClassName(metricsStmt, true), parentMetricsClass);
        try {
            metricsClass.addInterface(classPool.get(WITH_METADATA_INTERFACE));
        } catch (NotFoundException e) {
            log.error("Can't find WithMetadata interface for " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        ClassFile metricsClassClassFile = metricsClass.getClassFile();
        ConstPool constPool = metricsClassClassFile.getConstPool();

        /**
         * Create empty construct
         */
        try {
            CtConstructor defaultConstructor = CtNewConstructor.make("public " + className + "() {}", metricsClass);
            metricsClass.addConstructor(defaultConstructor);
        } catch (CannotCompileException e) {
            log.error("Can't add empty constructor in " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        /**
         * Add fields with annotations.
         *
         * private ${sourceField.typeName} ${sourceField.fieldName};
         */
        for (SourceColumn field : metricsStmt.getFieldsFromSource()) {
            try {
                CtField newField = CtField.make(
                        "private " + field.getType()
                                .getName() + " " + field.getFieldName() + ";", metricsClass);

                metricsClass.addField(newField);

                metricsClass.addMethod(CtNewMethod.getter(field.getFieldGetter(), newField));
                metricsClass.addMethod(CtNewMethod.setter(field.getFieldSetter(), newField));

                AnnotationsAttribute annotationsAttribute = new AnnotationsAttribute(
                        constPool, AnnotationsAttribute.visibleTag);
                /**
                 * Add @Column(name = "${sourceField.columnName}")
                 */
                Annotation columnAnnotation = new Annotation(Column.class.getName(), constPool);
                columnAnnotation.addMemberValue("name", new StringMemberValue(field.getColumnName(), constPool));
                if (field.getType().equals(String.class)) {
                    columnAnnotation.addMemberValue("length", new IntegerMemberValue(constPool, field.getLength()));
                }
                annotationsAttribute.addAnnotation(columnAnnotation);
                if (field.isID()) {
                    // Add SeriesID = 0 annotation to ID field.
                    Annotation banyanSeriesIDAnnotation = new Annotation(BanyanDB.SeriesID.class.getName(), constPool);
                    banyanSeriesIDAnnotation.addMemberValue("index", new IntegerMemberValue(constPool, 0));
                    annotationsAttribute.addAnnotation(banyanSeriesIDAnnotation);

                    // Entity id field should enable doc values.
                    final var enableDocValuesAnnotation = new Annotation(ElasticSearch.EnableDocValues.class.getName(), constPool);
                    annotationsAttribute.addAnnotation(enableDocValuesAnnotation);
                }

                if (field.isGroupByCondInTopN()) {
                    Annotation banyanTopNAggregationAnnotation = new Annotation(BanyanDB.TopNAggregation.class.getName(), constPool);
                    annotationsAttribute.addAnnotation(banyanTopNAggregationAnnotation);
                    // If TopN, add ShardingKey to group field.
                    Annotation banyanShardingKeyAnnotation = new Annotation(BanyanDB.ShardingKey.class.getName(), constPool);
                    banyanShardingKeyAnnotation.addMemberValue("index", new IntegerMemberValue(constPool, 0));
                    annotationsAttribute.addAnnotation(banyanShardingKeyAnnotation);
                }

                newField.getFieldInfo().addAttribute(annotationsAttribute);
            } catch (CannotCompileException e) {
                log.error(
                        "Can't add field(including set/get) " + field.getFieldName() + " in " + className + ".", e);
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
                log.error("Can't generate method " + method + " for " + className + ".", e);
                throw new OALCompileException(e.getMessage(), e);
            }
        }

        /**
         * Add following annotation to the metrics class
         *
         * at Stream(name = "${tableName}", scopeId = ${sourceScopeId}, builder = ${metricsName}Metrics.Builder.class, processor = MetricsStreamProcessor.class)
         */
        AnnotationsAttribute annotationsAttribute = new AnnotationsAttribute(
                constPool, AnnotationsAttribute.visibleTag);
        Annotation streamAnnotation = new Annotation(Stream.class.getName(), constPool);
        streamAnnotation.addMemberValue("name", new StringMemberValue(metricsStmt.getTableName(), constPool));
        streamAnnotation.addMemberValue(
                "scopeId", new IntegerMemberValue(constPool, metricsStmt.getFrom().getSourceScopeId()));
        streamAnnotation.addMemberValue(
                "builder", new ClassMemberValue(metricsBuilderClassName(metricsStmt, true), constPool));
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

        log.debug("Generate metrics class, " + metricsClass.getName());
        writeGeneratedFile(metricsClass, "metrics");

        return targetClass;
    }

    /**
     * Generate metrics class builder and inject it to classloader
     */
    private void generateMetricsBuilderClass(AnalysisResult metricsStmt) throws OALCompileException {
        String className = metricsBuilderClassName(metricsStmt, false);
        CtClass metricsBuilderClass = classPool.makeClass(metricsBuilderClassName(metricsStmt, true));
        try {
            metricsBuilderClass.addInterface(classPool.get(storageBuilderFactory.builderTemplate().getSuperClass()));
        } catch (NotFoundException e) {
            log.error("Can't find StorageBuilder interface for " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        /**
         * Create empty construct
         */
        try {
            CtConstructor defaultConstructor = CtNewConstructor.make(
                    "public " + className + "() {}", metricsBuilderClass);
            metricsBuilderClass.addConstructor(defaultConstructor);
        } catch (CannotCompileException e) {
            log.error("Can't add empty constructor in " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        /**
         * Generate methods
         */
        for (String method : METRICS_BUILDER_CLASS_METHODS) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration
                        .getTemplate(storageBuilderFactory.builderTemplate().getTemplatePath() + "/" + method + ".ftl")
                        .process(metricsStmt, methodEntity);
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

        writeGeneratedFile(metricsBuilderClass,  "metrics/builder");
    }

    /**
     * Generate SourceDispatcher class and inject it to classloader
     */
    private Class generateDispatcherClass(String scopeName,
                                          DispatcherContext dispatcherContext) throws OALCompileException {

        String className = dispatcherClassName(scopeName, false);
        CtClass dispatcherClass = classPool.makeClass(dispatcherClassName(scopeName, true));
        try {
            CtClass dispatcherInterface = classPool.get(DISPATCHER_INTERFACE);

            dispatcherClass.addInterface(dispatcherInterface);

            /**
             * Set generic signature
             */
            String sourceClassName = oalDefine.getSourcePackage() + dispatcherContext.getSource();
            SignatureAttribute.ClassSignature dispatcherSignature =
                    new SignatureAttribute.ClassSignature(
                            null, null,
                            // Set interface and its generic params
                            new SignatureAttribute.ClassType[] {
                                    new SignatureAttribute.ClassType(
                                            SourceDispatcher.class
                                                    .getCanonicalName(),
                                            new SignatureAttribute.TypeArgument[] {
                                                    new SignatureAttribute.TypeArgument(
                                                            new SignatureAttribute.ClassType(
                                                                    sourceClassName))
                                            }
                                    )
                            }
                    );

            dispatcherClass.setGenericSignature(dispatcherSignature.encode());
        } catch (NotFoundException e) {
            log.error("Can't find Dispatcher interface for " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        /**
         * Generate methods
         */
        for (AnalysisResult dispatcherContextMetric : dispatcherContext.getMetrics()) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration.getTemplate("dispatcher/doMetrics.ftl").process(dispatcherContextMetric, methodEntity);
                dispatcherClass.addMethod(CtNewMethod.make(methodEntity.toString(), dispatcherClass));
            } catch (Exception e) {
                log.error(
                        "Can't generate method do" + dispatcherContextMetric.getMetricsName() + " for " + className + ".",
                        e
                );
                log.error("Method body as following" + System.lineSeparator() + "{}", methodEntity);
                throw new OALCompileException(e.getMessage(), e);
            }
        }

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

    private String metricsClassName(AnalysisResult metricsStmt, boolean fullName) {
        return (fullName ? oalDefine.getDynamicMetricsClassPackage() : "") + metricsStmt.getMetricsName() + "Metrics";
    }

    private String metricsBuilderClassName(AnalysisResult metricsStmt, boolean fullName) {
        return (fullName ? oalDefine.getDynamicMetricsBuilderClassPackage() : "") + metricsStmt.getMetricsName() + "MetricsBuilder";
    }

    private String dispatcherClassName(String scopeName, boolean fullName) {
        return (fullName ? oalDefine.getDynamicDispatcherClassPackage() : "") + scopeName + "Dispatcher";
    }

    private void buildDispatcherContext(AnalysisResult metricsStmt) {
        String sourceName = metricsStmt.getFrom().getSourceName();

        DispatcherContext context = allDispatcherContext.getAllContext().computeIfAbsent(sourceName, name -> {
            DispatcherContext absent = new DispatcherContext();
            absent.setSourcePackage(oalDefine.getSourcePackage());
            absent.setSource(name);
            absent.setPackageName(name.toLowerCase());
            absent.setSourceDecorator(metricsStmt.getSourceDecorator());
            return absent;
        });
        metricsStmt.setMetricsClassPackage(oalDefine.getDynamicMetricsClassPackage());
        metricsStmt.setSourcePackage(oalDefine.getSourcePackage());
        context.getMetrics().add(metricsStmt);
    }

    public void prepareRTTempFolder() {
        if (openEngineDebug) {
            File workPath = WorkPath.getPath();
            File folder = new File(workPath.getParentFile(), "oal-rt/");
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

    private void writeGeneratedFile(CtClass metricsClass, String type) throws OALCompileException {
        if (openEngineDebug) {
            String className = metricsClass.getSimpleName();
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
                metricsClass.toBytecode(printWriter);
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
            return String.valueOf(new File(WorkPath.getPath().getParentFile(), "oal-rt/"));
        }
        return GENERATED_FILE_PATH;
    }

    public OALDefine getOalDefine() {
        return oalDefine;
    }

    public void setOpenEngineDebug(boolean debug) {
        this.openEngineDebug = debug;
    }

}
