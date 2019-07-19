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
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
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
import org.apache.commons.io.FileUtils;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oal.rt.meta.MetaReader;
import org.apache.skywalking.oal.rt.meta.MetaSettings;
import org.apache.skywalking.oal.rt.output.AllDispatcherContext;
import org.apache.skywalking.oal.rt.output.DispatcherContext;
import org.apache.skywalking.oal.rt.parser.AnalysisResult;
import org.apache.skywalking.oal.rt.parser.MetricsHolder;
import org.apache.skywalking.oal.rt.parser.OALScripts;
import org.apache.skywalking.oal.rt.parser.ScriptParser;
import org.apache.skywalking.oal.rt.parser.SourceColumn;
import org.apache.skywalking.oal.rt.parser.SourceColumnsFactory;
import org.apache.skywalking.oap.server.core.WorkPath;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.DispatcherDetectorListener;
import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.StreamAnnotationListener;
import org.apache.skywalking.oap.server.core.oal.rt.OALCompileException;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngine;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.IDColumn;
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

    private static final String CLASS_FILE_CHARSET = "UTF-8";
    private static final String METRICS_FUNCTION_PACKAGE = "org.apache.skywalking.oap.server.core.analysis.metrics.";
    private static final String DYNAMIC_METRICS_CLASS_PACKAGE = "org.apache.skywalking.oal.rt.metrics.";
    private static final String DYNAMIC_METRICS_BUILDER_CLASS_PACKAGE = "org.apache.skywalking.oal.rt.metrics.builder.";
    private static final String DYNAMIC_DISPATCHER_CLASS_PACKAGE = "org.apache.skywalking.oal.rt.dispatcher.";
    private static final String WITH_METADATA_INTERFACE = "org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata";
    private static final String STORAGE_BUILDER_INTERFACE = "org.apache.skywalking.oap.server.core.storage.StorageBuilder";
    private static final String DISPATCHER_INTERFACE = "org.apache.skywalking.oap.server.core.analysis.SourceDispatcher";
    private static final String SOURCE_PACKAGE = "org.apache.skywalking.oap.server.core.source.";
    private static final String METRICS_STREAM_PROCESSOR = "org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor";
    private static final String[] METRICS_CLASS_METHODS =
        {"id", "hashCode", "remoteHashCode", "equals", "serialize", "deserialize", "getMeta", "toHour", "toDay", "toMonth"};
    private static final String[] METRICS_BUILDER_CLASS_METHODS =
        {"data2Map", "map2Data"};
    private final ClassPool classPool;
    private ClassLoader currentClassLoader;
    private Configuration configuration;
    private AllDispatcherContext allDispatcherContext;
    private StreamAnnotationListener streamAnnotationListener;
    private DispatcherDetectorListener dispatcherDetectorListener;
    private final List<Class> metricsClasses;
    private final List<Class> dispatcherClasses;
    private final boolean openEngineDebug;

    public OALRuntime() {
        classPool = ClassPool.getDefault();
        configuration = new Configuration(new Version("2.3.28"));
        configuration.setEncoding(Locale.ENGLISH, CLASS_FILE_CHARSET);
        configuration.setClassLoaderForTemplateLoading(OALRuntime.class.getClassLoader(), "/code-templates");
        allDispatcherContext = new AllDispatcherContext();
        metricsClasses = new ArrayList<>();
        dispatcherClasses = new ArrayList<>();
        openEngineDebug = !StringUtil.isEmpty(System.getenv("SW_OAL_ENGINE_DEBUG"));
    }

    @Override public void setStreamListener(StreamAnnotationListener listener) throws ModuleStartException {
        this.streamAnnotationListener = listener;
    }

    @Override public void setDispatcherListener(DispatcherDetectorListener listener) throws ModuleStartException {
        dispatcherDetectorListener = listener;
    }

    @Override public void start(ClassLoader currentClassLoader) throws ModuleStartException, OALCompileException {
        prepareRTTempFolder();

        this.currentClassLoader = currentClassLoader;
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

    @Override public void notifyAllListeners() throws ModuleStartException {
        metricsClasses.forEach(streamAnnotationListener::notify);
        for (Class dispatcherClass : dispatcherClasses) {
            try {
                dispatcherDetectorListener.addIfAsSourceDispatcher(dispatcherClass);
            } catch (Exception e) {
                throw new ModuleStartException(e.getMessage(), e);
            }
        }
    }

    private void generateClassAtRuntime(OALScripts oalScripts) throws OALCompileException {
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
     *
     * @param metricsStmt
     * @return
     * @throws OALCompileException
     */
    private Class generateMetricsClass(AnalysisResult metricsStmt) throws OALCompileException {
        String className = metricsClassName(metricsStmt, false);
        CtClass parentMetricsClass = null;
        try {
            parentMetricsClass = classPool.get(METRICS_FUNCTION_PACKAGE + metricsStmt.getMetricsClassName());
        } catch (NotFoundException e) {
            logger.error("Can't find parent class for " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }
        CtClass metricsClass = classPool.makeClass(metricsClassName(metricsStmt, true), parentMetricsClass);
        try {
            metricsClass.addInterface(classPool.get(WITH_METADATA_INTERFACE));
        } catch (NotFoundException e) {
            logger.error("Can't find WithMetadata interface for " + className + ".", e);
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
            logger.error("Can't add empty constructor in " + className + ".", e);
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
                    Annotation idAnnotation = new Annotation(IDColumn.class.getName(), constPool);
                    annotationsAttribute.addAnnotation(idAnnotation);
                }

                newField.getFieldInfo().addAttribute(annotationsAttribute);

            } catch (CannotCompileException e) {
                logger.error("Can't add field(including set/get) " + field.getFieldName() + " in " + className + ".", e);
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
                logger.error("Can't generate method " + method + " for " + className + ".", e);
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
        streamAnnotation.addMemberValue("builder", new ClassMemberValue(metricsBuilderClassName(metricsStmt, true), constPool));
        streamAnnotation.addMemberValue("processor", new ClassMemberValue(METRICS_STREAM_PROCESSOR, constPool));

        annotationsAttribute.addAnnotation(streamAnnotation);
        metricsClassClassFile.addAttribute(annotationsAttribute);

        Class targetClass;
        try {
            targetClass = metricsClass.toClass(currentClassLoader, null);
        } catch (CannotCompileException e) {
            logger.error("Can't compile/load " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        logger.debug("Generate metrics class, " + metricsClass.getName());
        writeGeneratedFile(metricsClass, metricsClass.getSimpleName(), "metrics");

        return targetClass;
    }

    /**
     * Generate metrics class builder and inject it to classloader
     *
     * @param metricsStmt
     * @throws OALCompileException
     */
    private void generateMetricsBuilderClass(AnalysisResult metricsStmt) throws OALCompileException {
        String className = metricsBuilderClassName(metricsStmt, false);
        CtClass metricsBuilderClass = classPool.makeClass(metricsBuilderClassName(metricsStmt, true));
        try {
            metricsBuilderClass.addInterface(classPool.get(STORAGE_BUILDER_INTERFACE));
        } catch (NotFoundException e) {
            logger.error("Can't find StorageBuilder interface for " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        ClassFile metricsClassClassFile = metricsBuilderClass.getClassFile();
        ConstPool constPool = metricsClassClassFile.getConstPool();

        /**
         * Create empty construct
         */
        try {
            CtConstructor defaultConstructor = CtNewConstructor.make("public " + className + "() {}", metricsBuilderClass);
            metricsBuilderClass.addConstructor(defaultConstructor);
        } catch (CannotCompileException e) {
            logger.error("Can't add empty constructor in " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        /**
         * Generate methods
         */
        for (String method : METRICS_BUILDER_CLASS_METHODS) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration.getTemplate("metrics-builder/" + method + ".ftl").process(metricsStmt, methodEntity);
                metricsBuilderClass.addMethod(CtNewMethod.make(methodEntity.toString(), metricsBuilderClass));
            } catch (Exception e) {
                logger.error("Can't generate method " + method + " for " + className + ".", e);
                throw new OALCompileException(e.getMessage(), e);
            }
        }

        try {
            metricsBuilderClass.toClass(currentClassLoader, null);
        } catch (CannotCompileException e) {
            logger.error("Can't compile/load " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        writeGeneratedFile(metricsBuilderClass, className, "metrics/builder");
    }

    /**
     * Generate SourceDispatcher class and inject it to classloader
     *
     * @throws OALCompileException
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
            String sourceClassName = SOURCE_PACKAGE + dispatcherContext.getSource();
            SignatureAttribute.ClassSignature dispatcherSignature = new SignatureAttribute.ClassSignature(null, null,
                // Set interface and its generic params
                new SignatureAttribute.ClassType[] {
                    new SignatureAttribute.ClassType(SourceDispatcher.class.getCanonicalName(),
                        new SignatureAttribute.TypeArgument[] {new SignatureAttribute.TypeArgument(new SignatureAttribute.ClassType(sourceClassName))}
                    )});

            dispatcherClass.setGenericSignature(dispatcherSignature.encode());
        } catch (NotFoundException e) {
            logger.error("Can't find Dispatcher interface for " + className + ".", e);
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
                logger.error("Can't generate method do" + dispatcherContextMetric.getMetricsName() + " for " + className + ".", e);
                logger.error("Method body as following" + System.lineSeparator() + "{}", methodEntity);
                throw new OALCompileException(e.getMessage(), e);
            }
        }

        try {
            StringWriter methodEntity = new StringWriter();
            configuration.getTemplate("dispatcher/dispatch.ftl").process(dispatcherContext, methodEntity);
            dispatcherClass.addMethod(CtNewMethod.make(methodEntity.toString(), dispatcherClass));
        } catch (Exception e) {
            logger.error("Can't generate method dispatch for " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        Class targetClass;
        try {
            targetClass = dispatcherClass.toClass(currentClassLoader, null);
        } catch (CannotCompileException e) {
            logger.error("Can't compile/load " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        writeGeneratedFile(dispatcherClass, className, "dispatcher");
        return targetClass;
    }

    private String metricsClassName(AnalysisResult metricsStmt, boolean fullName) {
        return (fullName ? DYNAMIC_METRICS_CLASS_PACKAGE : "") + metricsStmt.getMetricsName() + "Metrics";
    }

    private String metricsBuilderClassName(AnalysisResult metricsStmt, boolean fullName) {
        return (fullName ? DYNAMIC_METRICS_BUILDER_CLASS_PACKAGE : "") + metricsStmt.getMetricsName() + "MetricsBuilder";
    }

    private String dispatcherClassName(String scopeName, boolean fullName) {
        return (fullName ? DYNAMIC_DISPATCHER_CLASS_PACKAGE : "") + scopeName + "Dispatcher";
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

    private void prepareRTTempFolder() {
        if (openEngineDebug) {
            File workPath = WorkPath.getPath();
            File folder = new File(workPath.getParentFile(), "oal-rt/");
            if (folder.exists()) {
                try {
                    FileUtils.deleteDirectory(folder);
                } catch (IOException e) {
                    logger.warn("Can't delete " + folder.getAbsolutePath() + " temp folder.", e);
                }
            }
            folder.mkdirs();
        }
    }

    private void writeGeneratedFile(CtClass metricsClass, String className, String type) throws OALCompileException {
        if (openEngineDebug) {
            DataOutputStream printWriter = null;
            try {
                File workPath = WorkPath.getPath();
                File folder = new File(workPath.getParentFile(), "oal-rt/" + type);
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
                logger.warn("Can't create " + className + ".txt, ignore.", e);
                return;
            } catch (CannotCompileException e) {
                logger.warn("Can't compile " + className + ".class(should not happen), ignore.", e);
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
}
