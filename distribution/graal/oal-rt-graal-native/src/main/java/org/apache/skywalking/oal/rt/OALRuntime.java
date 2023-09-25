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
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.apache.skywalking.oal.rt.output.AllDispatcherContext;
import org.apache.skywalking.oal.rt.output.DispatcherContext;
import org.apache.skywalking.oal.rt.parser.AnalysisResult;
import org.apache.skywalking.oal.rt.parser.OALScripts;
import org.apache.skywalking.oal.rt.parser.ScriptParser;
import org.apache.skywalking.oal.rt.parser.SourceColumn;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.DispatcherDetectorListener;
import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.StreamAnnotationListener;
import org.apache.skywalking.oap.server.core.oal.rt.OALCompileException;
import org.apache.skywalking.oap.server.core.oal.rt.OALDefine;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngine;
import org.apache.skywalking.oap.server.core.source.oal.rt.dispatcher.DispatcherClassPackageHolder;
import org.apache.skywalking.oap.server.core.source.oal.rt.metrics.MetricClassPackageHolder;
import org.apache.skywalking.oap.server.core.source.oal.rt.metrics.builder.MetricBuilderClassPackageHolder;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The old logic of this class used runtime class generation, which is not supported by native-image.
 * Therefore, in the new logic, class files are generated during the compilation process, see (@link org.apache.skywalking.graal.Generator) and loaded at runtime.
 */
@Slf4j
public class OALRuntime implements OALEngine {

    private static final String CLASS_FILE_CHARSET = "UTF-8";
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
    private final OALDefine oalDefine;
    private final ClassPool classPool;
    private ClassLoader currentClassLoader;
    private Configuration configuration;
    private AllDispatcherContext allDispatcherContext;
    private StreamAnnotationListener streamAnnotationListener;
    private DispatcherDetectorListener dispatcherDetectorListener;
    private StorageBuilderFactory storageBuilderFactory;
    private final List<Class> metricsClasses;
    private final List<Class> dispatcherClasses;
    private final boolean openEngineDebug;
    // change
    private static boolean INITIALED = false;
    //change end

    public OALRuntime(OALDefine define) {
        oalDefine = define;
        classPool = ClassPool.getDefault();
        configuration = new Configuration(new Version("2.3.28"));
        configuration.setEncoding(Locale.ENGLISH, CLASS_FILE_CHARSET);
        configuration.setClassLoaderForTemplateLoading(OALRuntime.class.getClassLoader(), "/code-templates");
        allDispatcherContext = new AllDispatcherContext();
        metricsClasses = new ArrayList<>();
        dispatcherClasses = new ArrayList<>();
        // change
        openEngineDebug = true;
        // change end
    }

    @Override
    public void setStreamListener(StreamAnnotationListener listener) throws ModuleStartException {
        this.streamAnnotationListener = listener;
    }

    @Override
    public void setDispatcherListener(DispatcherDetectorListener listener) throws ModuleStartException {
        dispatcherDetectorListener = listener;
    }

    @Override
    public void setStorageBuilderFactory(final StorageBuilderFactory factory) {
        storageBuilderFactory = factory;
    }

    @Override
    public void notifyAllListeners() throws ModuleStartException {
        for (Class metricsClass : metricsClasses) {
            try {
                streamAnnotationListener.notify(metricsClass);
            } catch (StorageException e) {
                throw new ModuleStartException(e.getMessage(), e);
            }
        }
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
                    Annotation banyanShardingKeyAnnotation = new Annotation(BanyanDB.SeriesID.class.getName(), constPool);
                    banyanShardingKeyAnnotation.addMemberValue("index", new IntegerMemberValue(constPool, 0));
                    annotationsAttribute.addAnnotation(banyanShardingKeyAnnotation);
                }

                if (field.isGroupByCondInTopN()) {
                    Annotation banyanTopNAggregationAnnotation = new Annotation(BanyanDB.TopNAggregation.class.getName(), constPool);
                    annotationsAttribute.addAnnotation(banyanTopNAggregationAnnotation);
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
            return absent;
        });
        metricsStmt.setMetricsClassPackage(oalDefine.getDynamicMetricsClassPackage());
        metricsStmt.setSourcePackage(oalDefine.getSourcePackage());
        context.getMetrics().add(metricsStmt);
    }

    private void writeGeneratedFile(CtClass metricsClass, String type) throws OALCompileException {
        if (openEngineDebug) {
            String className = metricsClass.getSimpleName();
            DataOutputStream printWriter = null;
            try {
                File currentFolder = new File(GeneratedFileConfiguration.getGeneratedFilePath());
                File folder = new File(currentFolder, type);
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

    // ------------------------------------ substituted method ------------------------------------
    public void generateOALClassFiles(ClassLoader currentClassLoader, String rootPath) throws ModuleStartException, OALCompileException {
        this.currentClassLoader = currentClassLoader;
        Reader read;

        try {
            String root = rootPath + File.separator + ".." + File.separator +
                    ".." + File.separator + ".." + File.separator + ".." + File.separator +
                    "oap-server" + File.separator + "server-starter" + File.separator +
                    "target" + File.separator + "classes" + File.separator;
            String configFile = oalDefine.getConfigFile();
            read = new FileReader(root + configFile);
        } catch (FileNotFoundException e) {
            throw new ModuleStartException("Can't locate " + oalDefine.getConfigFile(), e);
        }

        OALScripts oalScripts;
        try {
            ScriptParser scriptParser = ScriptParser.createFromFile(read, oalDefine.getSourcePackage());
            oalScripts = scriptParser.parse();
        } catch (IOException e) {
            throw new ModuleStartException("OAL script parse analysis failure.", e);
        }

        this.generateClassAtRuntime(oalScripts);
    }

    @Override
    public void start(ClassLoader currentClassLoader) {
        if (INITIALED) {
            return;
        }
        if (Objects.equals(System.getProperty("org.graalvm.nativeimage.imagecode"), "runtime")) {
            startByNativeImage(currentClassLoader);
        } else {
            startByJar(currentClassLoader);
        }
        INITIALED = true;
    }

    private void startByNativeImage(ClassLoader currentClassLoader) {
        try (FileSystem  fileSystem = FileSystems.newFileSystem(URI.create("resource:/"), Map.of(), currentClassLoader)) {
            Path metrics = fileSystem.getPath("org/apache/skywalking/oap/server/core/source/oal/rt/metrics");
            Path dispatcher = fileSystem.getPath("org/apache/skywalking/oap/server/core/source/oal/rt/dispatcher");

            try (java.util.stream.Stream<Path> files = Files.walk(metrics)) {
                files.forEach(file -> {
                    if (!file.toString().endsWith(".class")) {
                        return;
                    }
                    String name = file.toString().replace(File.separator, ".");

                    name = name.substring(0, name.length() - ".class".length());
                    if (name.startsWith("org.apache.skywalking.oap.server.core.source.oal.rt.metrics.builder")) {
                        return;
                    }
                    try {
                        Class<?> aClass = Class.forName(name);
                        if (!aClass.isAnnotationPresent(Stream.class)) {
                            return;
                        }
                        metricsClasses.add(aClass);
                    } catch (ClassNotFoundException e) {
                        // should not reach here
                        log.error(e.getMessage());
                    }
                });
            }
            catch (IOException e) {
                log.error("Failed to walk the path: " + metrics, e);
            }
            try (java.util.stream.Stream<Path> files = Files.walk(dispatcher)) {
                files.forEach(file -> {
                    if (!file.toString().endsWith(".class")) {
                        return;
                    }
                    String name = file.toString().replace(File.separator, ".");
                    name = name.substring(0, name.length() - ".class".length());
                    try {
                        dispatcherClasses.add(Class.forName(name));
                    } catch (ClassNotFoundException e) {
                        // should not reach here
                        log.error(e.getMessage());
                    }
                });
            }
            catch (IOException e) {
                log.error("Failed to walk the path: " + dispatcher, e);
            }
        } catch (IOException e) {
            // should not reach here
            log.error("Failed to create FileSystem", e);
        }
    }

    private void startByJar(ClassLoader currentClassLoader) {
        String metricsPath = "org/apache/skywalking/oap/server/core/source/oal/rt/metrics";
        String dispatcherPath = "org/apache/skywalking/oap/server/core/source/oal/rt/dispatcher";

        try {
            Enumeration<URL> metricsResources = currentClassLoader.getResources(metricsPath);
            while (metricsResources.hasMoreElements()) {
                URL resource = metricsResources.nextElement();
                processResourcePath(resource, metricsClasses, "org.apache.skywalking.oap.server.core.source.oal.rt.metrics.builder");
            }
        } catch (IOException e) {
            log.error("Failed to locate resource " + metricsPath + " on classpath");
        }

        try {
            Enumeration<URL> dispatcherResources = currentClassLoader.getResources(dispatcherPath);
            while (dispatcherResources.hasMoreElements()) {
                URL resource = dispatcherResources.nextElement();
                processResourcePath(resource, dispatcherClasses, null);
            }
        } catch (IOException e) {
            log.error("Failed to locate resource " + dispatcherPath + " on classpath");
        }
    }

    private void processResourcePath(URL resource, List<Class> classes, String excludeStartWith) {
        try {
            String protocol = resource.getProtocol();
            if ("file".equals(protocol)) {
                File dir = new File(resource.getFile());
                if (dir.isDirectory()) {
                    for (File file : Objects.requireNonNull(dir.listFiles())) {
                        if (file.getName().endsWith(".class")) {
                            String className = file.getPath().replace(File.separatorChar, '.');
                            className = className.substring(0, className.length() - ".class".length());
                            if (excludeStartWith != null && className.startsWith(excludeStartWith)) {
                                continue;
                            }
                            Class<?> aClass = Class.forName(className);
                            if (aClass.isAnnotationPresent(Stream.class)) {
                                classes.add(aClass);
                            }
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            // should not reach here
            log.error(e.getMessage());
        }
    }

}
