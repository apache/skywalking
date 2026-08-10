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
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
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
import org.apache.skywalking.oal.v2.model.SourceLocation;
import org.apache.skywalking.oap.server.core.WorkPath;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.dsl.DslGeneratedFileWriter;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.dsl.debug.DSLDebugCodegenSwitch;
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
import org.apache.skywalking.oap.server.core.dsl.DslJavaSourceText;

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
    private static final String DEBUG_HOLDER_PROVIDER_INTERFACE =
        "org.apache.skywalking.oap.server.core.dsl.debug.DebugHolderProvider";
    private static final String GATE_HOLDER_CLASS =
        "org.apache.skywalking.oap.server.core.dsl.debug.GateHolder";
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
        "toDay",
        // Emits the source's @ScopeDefaultColumn fields (the codegen-
        // materialised dynamic @Column fields on the generated subclass)
        // onto the dsl-debugging `output` sample. For a Service-style
        // scope that is exactly entityId + attr0..attr5 — nothing about
        // the metric's own value, which is delegated upward via
        // super.appendDebugFields(obj) to the family parent's override
        // (CPMMetrics emits total + value, SumMetrics emits value,
        // LongAvgMetrics emits summation + count + value, …).
        "appendDebugFields"
    };
    private static final String[] METRICS_BUILDER_CLASS_METHODS = {
        "entity2Storage",
        "storage2Entity"
    };

    private static final String CLASS_FILE_CHARSET = "UTF-8";

    private static boolean IS_RT_TEMP_FOLDER_INIT_COMPLETED = false;

    private boolean openEngineDebug;
    private final ClassPool classPool;
    private final OALDefine oalDefine;
    private Configuration configuration;
    private StorageBuilderFactory storageBuilderFactory;
    private static String GENERATED_FILE_PATH;
    // Per-metric content for dsl-debugging holders is read from each
    // CodeGenModel's metricSourceText field (populated by the parser via
    // ANTLR Interval slice), so no whole-file content is held here.

    public OALClassGeneratorV2(OALDefine define) {
        this(define, ClassPool.getDefault());
    }

    /**
     * Constructor with custom ClassPool for test isolation.
     */
    public OALClassGeneratorV2(OALDefine define, ClassPool classPool) {
        openEngineDebug = StringUtil.isNotEmpty(System.getenv("SW_DYNAMIC_CLASS_ENGINE_DEBUG"));
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
        final StringBuilder sourceMethods = new StringBuilder();
        final List<CtMethod> generatedMethods = new ArrayList<>();
        for (String method : METRICS_CLASS_METHODS) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration.getTemplate("metrics/" + method + ".ftl").process(model, methodEntity);
                final String body = methodEntity.toString();
                CtMethod m = CtNewMethod.make(body, metricsClass);
                metricsClass.addMethod(m);
                generatedMethods.add(m);
                sourceMethods.append("    ").append(body.replace("\n", "\n    ")).append("\n\n");
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

        // Leads with the rule, then the generated file name: the .java exists only under
        // SW_DYNAMIC_CLASS_ENGINE_DEBUG, so naming it alone would address nothing in production.
        final DslSourceRef metricsRef = refOf(model.getMetricDefinition().getLocation(), false);
        DslGeneratedFileWriter.setSourceFile(metricsClass, metricsRef.sourceFileOf(metricsClass.getSimpleName()));

        // Assemble the generated source file text BEFORE toClass(), so each method can be pointed at its own
        // signature line inside it. One entry per method: OAL bodies come from templates and are
        // largely void invocations, so there is no sound per-statement boundary to detect.
        final String metricsSource =
            wrapMetricsClassSource(metricsClass, model, sourceMethods.toString());
        for (final CtMethod m : generatedMethods) {
            DslGeneratedFileWriter.attachSignatureLine(m, DslGeneratedFileWriter.lineOfMethod(metricsSource, m.getName()));
        }

        Class targetClass;
        try {
            targetClass = metricsClass.toClass(MetricClassPackageHolder.class);
        } catch (CannotCompileException e) {
            log.error("Can't compile/load " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        log.debug("Generated V2 metrics class: " + metricsClass.getName());
        writeGeneratedFile(metricsClass, "metrics");
        writeGeneratedSourceFile(metricsClass, "metrics", metricsSource);

        return targetClass;
    }

    /** Wraps generated method bodies + fields as a compilable class envelope. */
    private String wrapMetricsClassSource(final CtClass ctClass, final CodeGenModel model, final String methods) {
        final StringBuilder sb = new StringBuilder();
        sb.append("package ").append(ctClass.getPackageName()).append(";\n\n");
        sb.append("public class ").append(ctClass.getSimpleName())
          .append(" extends ").append(model.getMetricsClassName())
          .append(" implements ").append(WITH_METADATA_INTERFACE).append(" {\n\n");
        for (final CodeGenModel.SourceFieldV2 field : model.getFieldsFromSource()) {
            sb.append("    private ").append(field.getType().getName()).append(' ')
              .append(field.getFieldName()).append(";\n");
        }
        sb.append('\n');
        sb.append(methods);
        sb.append("}\n");
        return sb.toString();
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
                CtMethod m = CtNewMethod.make(methodEntity.toString(), metricsBuilderClass);
                metricsBuilderClass.addMethod(m);
            } catch (Exception e) {
                log.error("Can't generate method " + method + " for " + className + ".", e);
                throw new OALCompileException(e.getMessage(), e);
            }
        }

        // The builder writes no generated source file in ANY mode (writeGeneratedFile below has no
        // writeGeneratedSourceFile beside it), so its own name would never resolve. The rule
        // provenance is the only part of this a reader can act on, hence no LineNumberTable
        // either -- see OALSourceAttributionTest. Add the plain file name only together with a
        // builder source file that makes it resolve.
        DslGeneratedFileWriter.setSourceFile(metricsBuilderClass,
            refOf(model.getMetricDefinition().getLocation(), false)
                .sourceFileOf(metricsBuilderClass.getSimpleName()));

        try {
            metricsBuilderClass.toClass(MetricBuilderClassPackageHolder.class);
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
            // DebugHolderProvider is added below only when dsl-debugging injection is
            // enabled at boot — paired with the per-source `debug` field and accessors.

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

        // OAL gate is PER-METRIC: one GateHolder per do<Metric>() in the dispatcher.
        // Each metric's session sees only its own rule's pipeline — the other
        // metrics on the same source dispatcher don't fire any probes because
        // their gates stay off. Symmetric with MAL/LAL session granularity.
        //
        // Emitted only when dsl-debugging is enabled at boot. When off, the
        // dispatcher does not implement DebugHolderProvider, the fields are
        // absent, and doMetrics.ftl skips every probe call site — bytecode
        // matches a build without SWIP-13.
        if (DSLDebugCodegenSwitch.isInjectionEnabled()) {
            final String holderFqcn = "org.apache.skywalking.oap.server.core.dsl.debug.GateHolder";
            try {
                dispatcherClass.addInterface(classPool.get(DEBUG_HOLDER_PROVIDER_INTERFACE));

                // One `debug_<oalRuleName>` GateHolder field per metric. The OAL
                // rule's user-facing name is snake_case (e.g. service_relation_server_cpm)
                // and is what operators type in `.oal` files — that's the lookup key
                // we expose. Each holder carries its own metric's verbatim ANTLR slice
                // as content (the single-line OAL statement) so dsl-debugging records
                // render the exact rule source inline. The snake_case name is a valid
                // Java identifier, so we splice it straight into the field name —
                // `debug_service_relation_server_cpm`.
                for (CodeGenModel metric : dispatcherContext.getMetrics()) {
                    final String perMetricContent = DslJavaSourceText.toLiteral(metric.getMetricSourceText());
                    // Use the GateHolder.withMetadata factory so each holder is
                    // stamped with {ruleName, sourceLine} at instance-init time
                    // — the dsl-debugging records carry a structured per-rule
                    // envelope alongside the verbatim dsl source.
                    dispatcherClass.addField(javassist.CtField.make(
                        "public final " + holderFqcn + " debug_" + metric.getTableName()
                            + " = " + holderFqcn + ".withMetadata(\""
                            + perMetricContent + "\", \""
                            + DslJavaSourceText.toLiteral(metric.getTableName()) + "\", "
                            + metric.getSourceLine() + ");",
                        dispatcherClass));
                }

                // debugHolder(String) returns the right field by OAL rule name.
                // If/else chain — typical dispatcher has 2-10 metrics; binary
                // search isn't worth the codegen complexity at this scale.
                final StringBuilder lookupBody = new StringBuilder();
                lookupBody.append("public ").append(holderFqcn)
                          .append(" debugHolder(String metricName) {\n");
                for (CodeGenModel metric : dispatcherContext.getMetrics()) {
                    lookupBody.append("  if (\"")
                              .append(DslJavaSourceText.toLiteral(metric.getTableName()))
                              .append("\".equals(metricName)) return this.debug_")
                              .append(metric.getTableName()).append(";\n");
                }
                lookupBody.append("  return null;\n}\n");
                dispatcherClass.addMethod(CtNewMethod.make(lookupBody.toString(), dispatcherClass));

                final StringBuilder namesBody = new StringBuilder();
                namesBody.append("public String[] debugRuleNames() {\n")
                         .append("  return new String[] {");
                boolean first = true;
                for (CodeGenModel metric : dispatcherContext.getMetrics()) {
                    if (!first) {
                        namesBody.append(", ");
                    }
                    // Surface the snake_case OAL rule name — that's what operators
                    // see in their .oal files and what they pass on the install API.
                    namesBody.append("\"").append(DslJavaSourceText.toLiteral(metric.getTableName())).append("\"");
                    first = false;
                }
                namesBody.append("};\n}\n");
                dispatcherClass.addMethod(CtNewMethod.make(namesBody.toString(), dispatcherClass));
            } catch (CannotCompileException e) {
                log.error("Can't add DebugHolderProvider members on " + className + ".", e);
                throw new OALCompileException(e.getMessage(), e);
            } catch (NotFoundException nfe) {
                log.error("DebugHolderProvider interface missing for " + className + ".", nfe);
                throw new OALCompileException(nfe.getMessage(), nfe);
            }
        }

        // Generate do<Metric>() per metric. Two FTL paths:
        //   - dispatcher/doMetrics.ftl           — pre-SWIP-13 logic, byte-identical
        //                                          to a build without dsl-debugging
        //   - dispatcher/doMetricsWithDebug.ftl  — same logic + per-stage probe call
        //                                          sites guarded on this.debug.isGateOn()
        // Each FTL takes the CodeGenModel as-is — no extra context fields, no model
        // wrapper. The codegen picks one or the other based on whether injection is
        // enabled at boot; only one template runs per dispatcher.
        final String doMetricsTemplate = DSLDebugCodegenSwitch.isInjectionEnabled()
            ? "dispatcher/doMetricsWithDebug.ftl"
            : "dispatcher/doMetrics.ftl";
        final StringBuilder dispatcherSourceMethods = new StringBuilder();
        final List<CtMethod> dispatcherMethods = new ArrayList<>();
        for (CodeGenModel metric : dispatcherContext.getMetrics()) {
            StringWriter methodEntity = new StringWriter();
            try {
                configuration.getTemplate(doMetricsTemplate).process(metric, methodEntity);
                final String body = methodEntity.toString();
                CtMethod m = CtNewMethod.make(body, dispatcherClass);
                dispatcherClass.addMethod(m);
                dispatcherMethods.add(m);
                dispatcherSourceMethods.append("    ").append(body.replace("\n", "\n    ")).append("\n\n");
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
            final String body = methodEntity.toString();
            CtMethod m = CtNewMethod.make(body, dispatcherClass);
            dispatcherClass.addMethod(m);
            dispatcherMethods.add(m);
            dispatcherSourceMethods.append("    ").append(body.replace("\n", "\n    ")).append("\n\n");
        } catch (Exception e) {
            log.error("Can't generate method dispatch for " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        // A dispatcher combines every metric of one scope into one class, so no single rule LINE
        // is true for the CLASS -- it names the .oal file without one. Each do<Metric> method does
        // map to exactly one rule, and its entry addresses the generated .java, so per-method
        // attribution is both possible and correct. The per-metric .oal line is carried separately,
        // by the GateHolder metadata the debug path reads.
        DslGeneratedFileWriter.setSourceFile(dispatcherClass,
            refOf(dispatcherContext.getMetrics().isEmpty() ? null
                    : dispatcherContext.getMetrics().get(0).getMetricDefinition().getLocation(),
                true).sourceFileOf(dispatcherClass.getSimpleName()));

        // Assemble before toClass(), so each do<Metric> can be pointed at its own signature line.
        final String dispatcherSource = wrapDispatcherClassSource(
            dispatcherClass, dispatcherContext, dispatcherSourceMethods.toString());
        for (final CtMethod m : dispatcherMethods) {
            DslGeneratedFileWriter.attachSignatureLine(m,
                DslGeneratedFileWriter.lineOfMethod(dispatcherSource, m.getName()));
        }

        Class targetClass;
        try {
            targetClass = dispatcherClass.toClass(DispatcherClassPackageHolder.class);
        } catch (CannotCompileException e) {
            log.error("Can't compile/load " + className + ".", e);
            throw new OALCompileException(e.getMessage(), e);
        }

        writeGeneratedFile(dispatcherClass, "dispatcher");
        writeGeneratedSourceFile(dispatcherClass, "dispatcher", dispatcherSource);
        return targetClass;
    }

    /**
     * Wraps the dispatcher's generated method bodies + GateHolder fields
     * as a compilable class envelope. Renders the {@code debug_<metric>}
     * fields when injection is enabled so the source mirrors the bytecode.
     */
    private String wrapDispatcherClassSource(final CtClass ctClass,
                                             final DispatcherContextV2 ctx,
                                             final String methods) {
        final StringBuilder sb = new StringBuilder();
        sb.append("package ").append(ctClass.getPackageName()).append(";\n\n");
        sb.append("public class ").append(ctClass.getSimpleName())
          .append(" implements org.apache.skywalking.oap.server.core.analysis.SourceDispatcher<")
          .append(ctx.getSourcePackage()).append(ctx.getSourceName()).append("> {\n\n");
        if (DSLDebugCodegenSwitch.isInjectionEnabled()) {
            // The .java generated source file must compile cleanly for IDE source-attach. Final
            // fields without initializers and stub method bodies would both
            // reject — emit `null` initializers + return-null bodies. The actual
            // bytecode this stands in for is produced by Javassist with full
            // initializers in <clinit> and full method bodies; the generated source file only
            // needs to round-trip through javac so source viewers can read it.
            for (CodeGenModel metric : ctx.getMetrics()) {
                sb.append("    public final ").append(GATE_HOLDER_CLASS).append(' ')
                  .append("debug_").append(metric.getTableName()).append(" = null;\n");
            }
            sb.append("    public ").append(GATE_HOLDER_CLASS)
              .append(" debugHolder(String ruleName) { return null; }\n");
            sb.append("    public String[] debugRuleNames() { return new String[0]; }\n");
            sb.append('\n');
        }
        sb.append(methods);
        sb.append("}\n");
        return sb.toString();
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
        if (!IS_RT_TEMP_FOLDER_INIT_COMPLETED && openEngineDebug) {
            File workPath = WorkPath.getPath();
            File folder = new File(workPath.getParentFile(), "oal-rt/");
            if (folder.exists()) {
                try {
                    // Clean contents instead of deleting folder (handles Docker volume mounts)
                    FileUtils.cleanDirectory(folder);
                } catch (IOException e) {
                    log.warn("Can't clean " + folder.getAbsolutePath() + " temp folder.", e);
                }
            } else {
                folder.mkdirs();
            }
            IS_RT_TEMP_FOLDER_INIT_COMPLETED = true;
        }
    }

    /**
     * Sets the {@code SourceFile} attribute of the class to the given name.
     */


    /**
     * @param ctClass  the generated class
     * @param loc      originating OAL location
     * @param lineless true to name the file without a line, for classes shared by several rules
     * @return the attribute value
     */
    /**
     * Adapts an OAL {@link SourceLocation} to the shared
     * source-coordinate model used by every DSL.
     *
     * @param loc      the rule's location, may be null or UNKNOWN
     * @param lineless true for a class shared by several rules, where no single line is true
     * @return the ref
     */
    private static DslSourceRef refOf(final SourceLocation loc,
                                      final boolean lineless) {
        if (loc == null || loc == SourceLocation.UNKNOWN) {
            return DslSourceRef.ofRule(null, 0);
        }
        return DslSourceRef.ofRule(loc.getFileName(), lineless ? 0 : loc.getLine());
    }

    private void writeGeneratedFile(final CtClass ctClass, final String type) {
        if (!openEngineDebug) {
            return;
        }
        // Same operation as every other DSL: make the directory, write <SimpleName>.class. OAL
        // writes into a per-type subdirectory, which is a different PATH, not a different write.
        DslGeneratedFileWriter.writeClassFile(
            new File(getGeneratedFilePath() + File.separator + type), ctClass);
    }

    /**
     * Sibling of {@link #writeGeneratedFile} that writes the Javassist-input
     * Java source as {@code <ClassName>.java} alongside the {@code .class}.
     * IDE source-attach renders this directly so the user sees the EXACT
     * code Javassist compiled rather than a FernFlower decompile (which
     * frequently bails on Javassist's bytecode patterns and leaves
     * "compiled code" stubs). Caller assembles the Java source in their
     * own buffer during method-body generation.
     */
    private void writeGeneratedSourceFile(final CtClass ctClass, final String type, final String javaSource) {
        if (!openEngineDebug) {
            return;
        }
        // Shared writer, not a local copy: DslGeneratedFileWriter.lineOfMethod counts from its preamble, so
        // a second implementation here would silently shift every OAL line number if it drifted.
        DslGeneratedFileWriter.writeSourceFile(
            new File(getGeneratedFilePath() + File.separator + type),
            ctClass.getSimpleName(), javaSource);
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

    public void setOpenEngineDebug(boolean debug) {
        this.openEngineDebug = debug;
    }

    /**
     * V2 dispatcher context for grouping metrics by source.
     */
    public static class DispatcherContextV2 {
        private String sourcePackage;
        private String sourceName;
        private String packageName;
        private String sourceDecorator;
        private List<CodeGenModel> metrics = new ArrayList<>();

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
