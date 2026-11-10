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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.compiler;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.apache.skywalking.oap.server.core.dsl.DslClassNaming;
import org.apache.skywalking.oap.server.core.dsl.DslGeneratedFileWriter;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.dsl.classloader.BytecodeClassDefiner;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalExpressionPackageHolder;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalExpression;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfigs;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.PairingFact;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.TALConstruct;

/**
 * Compiles one TAL rule into a class implementing {@link TalExpression}.
 *
 * <p>The pipeline is the one every SkyWalking DSL uses: the parsed model becomes Java source, Javassist compiles
 * it, and the class is loaded with a local-variable table and a source reference so a stack trace names the rule
 * file. Blocks become private methods guarded by the abort flag; every method takes the context explicitly, so
 * nothing rides on a {@code ThreadLocal}.
 */
@Slf4j
public final class TALClassGenerator {
    private static final String PACKAGE = "org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.";
    private static final String FILTER_SPEC = "org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.FilterSpec";
    private static final String EXEC_CTX = "org.apache.skywalking.oap.trace.analyzer.v2.dsl.ExecutionContext";
    private static final String HELPER = "org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt.TalRuntimeHelper";
    private static final String EXPRESSION = "org.apache.skywalking.oap.trace.analyzer.v2.dsl.TalExpression";
    /**
     * The static field every generated class holds its script's logger in, for {@code log} statements and the
     * functions that log.
     */
    static final String SCRIPT_LOGGER = "_LOG";
    /**
     * The logger name of a script, {@code <prefix><script name>}, the script being the rule file without its
     * extension. Not configurable.
     */
    public static final String SCRIPT_LOGGER_PREFIX = "org.apache.skywalking.trace.analysis.script.";
    /**
     * The caller half a {@code buildNetworkAlias} address reads.
     */
    private static final String PAIRING = "pairing";
    private static final Set<String> PAIRING_FIELDS = Set.of("service", "peer");
    private static final String CALLER_SCOPED_ADDRESS = "callerScopedAddress";
    /**
     * The parameters of every method that runs rule statements, and their local-variable table entries.
     */
    private static final String RUN_PARAMETERS = FILTER_SPEC + " filterSpec, " + EXEC_CTX + " ctx";
    /**
     * How many times a rule is generated to learn its {@code segmentDef} types before it is refused. Each generation
     * settles what the previous one's types decide, so a rule needs about one per variable a read depends on.
     */
    private static final int MAX_SEGMENT_DEF_GENERATIONS = 32;
    private static final List<String[]> RUN_LVT = List.of(
        new String[] {"filterSpec", "L" + FILTER_SPEC.replace('.', '/') + ";"},
        new String[] {"ctx", "L" + EXEC_CTX.replace('.', '/') + ";"});

    private final ClassPool classPool;
    private final ClassLoader targetClassLoader;
    /**
     * Where the generated {@code .class} and the Java source Javassist compiled are written. Null in normal
     * operation: {@code resolveClassDumpDir} returns null unless {@code SW_DYNAMIC_CLASS_ENGINE_DEBUG} is set,
     * and it builds its path from the OAP install directory, which a test JVM does not have. A test that wants
     * the dump sets it explicitly through {@link #setClassOutputDir}.
     */
    private File classOutputDir;

    /**
     * The rule's declared input type, which the {@code span} root resolves against.
     */
    @Setter
    private Class<?> inputType;

    /**
     * The alias of the rule's input, such as {@code otlp} or {@code sw-native}, which the input-scoped functions and
     * constructs are checked against.
     */
    @Setter
    private String inputAlias;

    /**
     * The bound function providers a rule's calls resolve against; null for {@link TALFunctionRegistry#defaults()},
     * which is then resolved only when the rule compiles.
     */
    @Setter
    private TALFunctionRegistry functionRegistry;

    /**
     * Write the generated class and the Java source Javassist compiled to this directory, whatever the
     * environment says. Meant for tests: {@code SW_DYNAMIC_CLASS_ENGINE_DEBUG} resolves its path from the OAP
     * install directory, which a test JVM does not have.
     *
     * @param dir the directory; the writer creates it on first write
     */
    public void setClassOutputDir(final File dir) {
        this.classOutputDir = dir;
    }

    @Setter
    private String classNameHint;

    @Setter
    private DslSourceRef sourceRef;

    @Setter
    private String metricPrefix;

    /**
     * The verbatim rule text, stamped on debug captures so the UI can render the source inline.
     */
    @Setter
    private String content;

    /**
     * Every metric the compiled rule declares, available after {@link #compile}.
     */
    @Getter
    private List<TALMetricDeclaration> metricDeclarations = List.of();

    public TALClassGenerator() {
        this(ClassPool.getDefault(), null);
    }

    /**
     * For a caller that supplies its own pool, scoped to its own loader, so the generated class is defined there
     * rather than in the OAP app loader.
     *
     * @param classPool         the pool to make the class in
     * @param targetClassLoader the loader to define it in, or null for the app loader
     */
    public TALClassGenerator(final ClassPool classPool, final ClassLoader targetClassLoader) {
        this.classPool = classPool;
        this.targetClassLoader = targetClassLoader;
        this.classOutputDir = DslGeneratedFileWriter.resolveClassDumpDir("tal");
    }

    /**
     * Parse, generate and load one rule.
     *
     * @param dsl the rule's DSL text
     * @return the compiled rule, ready to run against spans
     * @throws Exception when the rule does not parse, does not validate, or does not compile
     */
    public TalExpression compile(final String dsl) throws Exception {
        final TALScriptModel model = TALScriptParser.parse(dsl);
        // A first {} written directly in filter {} is a run of its own, generated as its own method; anywhere else
        // it is a compile error, see TALBlockCodegen.
        final List<TALScriptModel.FilterStatement> regular = new ArrayList<>();
        final List<TALScriptModel.FirstBlock> firsts = new ArrayList<>();
        for (final TALScriptModel.FilterStatement stmt : model.getStatements()) {
            if (stmt instanceof TALScriptModel.FirstBlock) {
                TALInputs.require(TALConstruct.FIRST, inputAlias);
                firsts.add((TALScriptModel.FirstBlock) stmt);
            } else {
                regular.add(stmt);
            }
        }
        final Map<String, Integer> segmentDefSlots = new LinkedHashMap<>();
        collectSegmentDefs(model.getStatements(), segmentDefSlots);

        final GeneratedRule rule = generateRule(regular, firsts, segmentDefSlots);
        final TALGenCtx genCtx = rule.genCtx;
        this.metricDeclarations = List.copyOf(dedupeDeclarations(genCtx.metricDeclarations));

        final String className = DslClassNaming.allocate(
            PACKAGE + DslClassNaming.stem(sourceRef, genCtx.ruleName, TALConfigs.TAL_CATALOG),
            targetClassLoader == null);

        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(EXPRESSION));
        ctClass.addField(CtField.make(
            "private static final org.slf4j.Logger " + SCRIPT_LOGGER
                + " = org.slf4j.LoggerFactory.getLogger(\"" + scriptLoggerName(sourceRef) + "\");", ctClass));
        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        final List<GeneratedMethod> methods = new ArrayList<>(rule.methods);
        if (genCtx.networkAliasBuild != null) {
            methods.add(new GeneratedMethod("networkAliasAddress",
                generateNetworkAliasMethod(genCtx.networkAliasBuild, genCtx.classMembers), null));
        }
        // Before the methods, which Javassist resolves the fields for when it compiles them.
        final String memberFields = memberFields(genCtx.classMembers);
        for (final String field : memberFields.split("\n")) {
            if (!field.isEmpty()) {
                ctClass.addField(CtField.make(field.trim(), ctClass));
            }
        }
        final StringBuilder bodies = new StringBuilder();
        final Map<String, CtMethod> made = new LinkedHashMap<>();
        // In the order the list holds them: a build block's method before executeBuild, which calls it, since
        // Javassist resolves a call when it compiles the caller.
        for (final GeneratedMethod method : methods) {
            if (log.isDebugEnabled()) {
                log.debug("TAL compile {}():\n{}", method.name, method.source);
            }
            final CtMethod ctMethod = CtNewMethod.make(method.source, ctClass);
            ctClass.addMethod(ctMethod);
            if (method.lvt != null) {
                DslGeneratedFileWriter.addLocalVariableTable(
                    ctMethod, className, method.lvt.toArray(new String[0][]));
                made.put(method.name, ctMethod);
            }
            bodies.append(method.source);
        }

        final String sourceText = buildSourceText(ctClass, memberFields, bodies.toString());
        if (sourceRef != null) {
            DslGeneratedFileWriter.setSourceFile(ctClass, sourceRef.sourceFileOf(ctClass.getSimpleName()));
        }
        made.forEach((name, ctMethod) -> DslGeneratedFileWriter.attachSignatureLine(
            ctMethod, DslGeneratedFileWriter.lineOfMethod(sourceText, name)));

        DslGeneratedFileWriter.writeClassFile(classOutputDir, ctClass);
        DslGeneratedFileWriter.writeSourceFile(classOutputDir, ctClass.getSimpleName(), sourceText);

        final Class<?> clazz = BytecodeClassDefiner.define(
            ctClass, targetClassLoader, TalExpressionPackageHolder.class);
        ctClass.detach();
        final TalExpression expression = (TalExpression) clazz.getDeclaredConstructor().newInstance();
        final TALFunctionRegistry registry = functionRegistry == null ? TALFunctionRegistry.defaults() : functionRegistry;
        for (final Map.Entry<String, String[]> function : genCtx.classMembers.functionFields.entrySet()) {
            final Field field = clazz.getDeclaredField(function.getValue()[0]);
            field.setAccessible(true);
            field.set(expression, registry.instance(function.getKey()));
        }
        return expression;
    }

    /**
     * The declarations of a generated class's member fields, one per line: the function objects, set by
     * {@link #compile} once the class is made.
     */
    private static String memberFields(final TALGenCtx.ClassMembers members) {
        final StringBuilder fields = new StringBuilder();
        members.functionFields.values().forEach(field -> fields.append("  private ").append(field[1]).append(' ')
                                                              .append(field[0]).append(";\n"));
        return fields.toString();
    }

    /**
     * Generate the rule's methods once the {@code segmentDef} types are known. A {@code segmentDef} can be read before
     * the statement that types it, in an earlier block of the rule's text or in another method, so the types are
     * learned first: each discovery generation declares the types the previous one learned, an {@code Object} for a
     * name not learned yet, and skips a statement that fails, still visiting the statements inside it, so a declaration
     * after or inside a failing read is reached. Once a discovery generation learns nothing new, the generation that
     * counts runs with those types and reports every error; it is kept only when the types it learns are the ones it
     * declared. A rule without {@code segmentDef} is generated once.
     */
    private GeneratedRule generateRule(final List<TALScriptModel.FilterStatement> regular,
                                       final List<TALScriptModel.FirstBlock> firsts,
                                       final Map<String, Integer> segmentDefSlots) {
        if (segmentDefSlots.isEmpty()) {
            return generateMethods(regular, firsts, newGenCtx(segmentDefSlots, Map.of()));
        }
        Map<String, List<Object>> types = new HashMap<>();
        for (int generation = 0; generation < MAX_SEGMENT_DEF_GENERATIONS; generation++) {
            final TALGenCtx discovery = newGenCtx(segmentDefSlots, types);
            discovery.discoveringTypes = true;
            generateMethods(regular, firsts, discovery);
            final Map<String, List<Object>> learned = new HashMap<>(types);
            learned.putAll(discovery.learnedSegmentDefTypes);
            if (learned.equals(types)) {
                final TALGenCtx strict = newGenCtx(segmentDefSlots, types);
                final GeneratedRule rule = generateMethods(regular, firsts, strict);
                learned.putAll(strict.learnedSegmentDefTypes);
                if (learned.equals(types)) {
                    return rule;
                }
            }
            types = learned;
        }
        throw new IllegalArgumentException(
            "the types of the segmentDef variables " + segmentDefSlots.keySet() + " do not settle after "
                + MAX_SEGMENT_DEF_GENERATIONS + " generations; declare them with as <Type>");
    }

    private TALGenCtx newGenCtx(final Map<String, Integer> segmentDefSlots, final Map<String, List<Object>> types) {
        final TALGenCtx genCtx = new TALGenCtx(
            new TALSpanRootResolver(inputType, functionRegistry, inputAlias), inputType);
        genCtx.inputAlias = inputAlias;
        genCtx.metricPrefix = metricPrefix;
        genCtx.ruleName = classNameHint == null ? "tal" : classNameHint;
        genCtx.segmentDefSlots = segmentDefSlots;
        genCtx.segmentDefTypes = types;
        return genCtx;
    }

    private GeneratedRule generateMethods(final List<TALScriptModel.FilterStatement> regular,
                                          final List<TALScriptModel.FirstBlock> firsts,
                                          final TALGenCtx genCtx) {
        final List<GeneratedMethod> methods = new ArrayList<>();
        // Everything a run does is one method, so an abort {} anywhere returns from it and nothing after it runs,
        // and a filter-level def is in scope inside the extractor.
        genCtx.beginMethod();
        final StringBuilder execute = new StringBuilder();
        TALBlockCodegen.generateStatements(execute, regular, genCtx);
        final GeneratedMethod executeMethod = runMethod(
            "public void execute", RUN_PARAMETERS, RUN_LVT, execute, genCtx);
        GeneratedMethod firstMethod = null;
        if (!firsts.isEmpty()) {
            genCtx.beginMethod();
            final StringBuilder first = new StringBuilder();
            for (final TALScriptModel.FirstBlock block : firsts) {
                first.append("  {\n");
                TALBlockCodegen.generateStatements(first, block.getStatements(), genCtx);
                first.append("  }\n");
            }
            firstMethod = runMethod("public void executeFirst", RUN_PARAMETERS, RUN_LVT, first, genCtx);
        }
        // A build {} cannot hold another, so generating the blocks queues none.
        final StringBuilder dispatch = new StringBuilder();
        for (int i = 0; i < genCtx.builds.size(); i++) {
            methods.add(generateBuildMethod(i, genCtx.builds.get(i), genCtx));
            dispatch.append("  if (index == ").append(i).append(") {\n")
                    .append("    build_").append(i).append("(filterSpec, ctx, captured);\n")
                    .append("    return;\n")
                    .append("  }\n");
        }
        if (!genCtx.builds.isEmpty()) {
            methods.add(new GeneratedMethod("executeBuild",
                "public void executeBuild(int index, " + FILTER_SPEC + " filterSpec, " + EXEC_CTX + " ctx, "
                    + "Object[] captured) {\n" + dispatch
                    + "  throw new IllegalArgumentException(\"the rule queues no build {} block \" + "
                    + "String.valueOf(index));\n}\n", null));
        }
        methods.add(executeMethod);
        if (firstMethod != null) {
            methods.add(firstMethod);
            methods.add(new GeneratedMethod("hasFirst", "public boolean hasFirst() {\n  return true;\n}\n", null));
        }
        if (!genCtx.segmentDefSlots.isEmpty()) {
            methods.add(new GeneratedMethod("segmentDefCount",
                "public int segmentDefCount() {\n  return " + genCtx.segmentDefSlots.size() + ";\n}\n", null));
        }
        return new GeneratedRule(genCtx, methods);
    }

    /**
     * A queued {@code build {}} block's own method. It reads the values its run captured into locals of the same
     * names and types, so an assignment changes this block's copy only, and runs at the extractor level, inside the
     * block, where a native rule writes its outputs.
     */
    private GeneratedMethod generateBuildMethod(final int index, final TALGenCtx.BuildSite site,
                                                final TALGenCtx genCtx) {
        genCtx.beginMethod();
        int slot = 0;
        for (final Map.Entry<String, TALLocalVar> captured : site.captured.entrySet()) {
            final TALLocalVar var = captured.getValue();
            final String typeName = TALCodegenHelper.javaTypeName(var.resolvedType);
            genCtx.localVars.put(captured.getKey(),
                new TALLocalVar(var.javaVarName, var.resolvedType, var.elementType));
            genCtx.declaredJavaNames.add(var.javaVarName);
            genCtx.localVarDecls.append("  ").append(typeName).append(" ").append(var.javaVarName).append(" = (")
                                .append(typeName).append(") captured[").append(slot++).append("];\n");
            genCtx.localVarLvtVars.add(new String[] {var.javaVarName, var.descriptor});
        }
        genCtx.entityScopeTypes.putAll(site.capturedScopes);
        genCtx.extractorDepth = 1;
        genCtx.buildDepth = 1;
        final List<TALScriptModel.FilterStatement> statements = new ArrayList<>();
        for (final TALScriptModel.ExtractorStatement stmt : site.block.getStatements()) {
            statements.add((TALScriptModel.FilterStatement) stmt);
        }
        final StringBuilder body = new StringBuilder();
        TALBlockCodegen.generateStatements(body, statements, genCtx);
        final List<String[]> lvt = new ArrayList<>(RUN_LVT);
        lvt.add(new String[] {"captured", "[Ljava/lang/Object;"});
        return runMethod("private void build_" + index, RUN_PARAMETERS + ", Object[] captured", lvt, body, genCtx);
    }

    /**
     * A method that runs rule statements: the helper, the input cast and the chain caches, the locals, then the
     * body.
     *
     * @param declaration the method's modifiers, return type and name, such as {@code public void execute}
     */
    private GeneratedMethod runMethod(final String declaration, final String parameters,
                                      final List<String[]> parameterLvt, final CharSequence body,
                                      final TALGenCtx genCtx) {
        final List<String[]> lvt = new ArrayList<>(parameterLvt);
        final StringBuilder sb = new StringBuilder();
        sb.append(declaration).append("(").append(parameters).append(") {\n");
        sb.append("  ").append(HELPER).append(" h = new ").append(HELPER)
          .append("(ctx, filterSpec, ").append(SCRIPT_LOGGER).append(");\n");
        lvt.add(new String[] {"h", "L" + HELPER.replace('.', '/') + ";"});
        if (genCtx.usedProtoAccess) {
            if (inputType != null) {
                final String typeName = inputType.getName();
                sb.append("  ").append(typeName).append(" _p = (")
                  .append(typeName).append(") ctx.getInput();\n");
                lvt.add(new String[] {"_p", "L" + typeName.replace('.', '/') + ";"});
            }
            sb.append(genCtx.protoVarDecls);
            lvt.addAll(genCtx.protoLvtVars);
        }
        sb.append(genCtx.localVarDecls);
        lvt.addAll(genCtx.localVarLvtVars);
        sb.append(body);
        sb.append("}\n");
        final String name = declaration.substring(declaration.lastIndexOf(' ') + 1);
        return new GeneratedMethod(name, sb.toString(), lvt);
    }

    /**
     * Every {@code segmentDef} name of the rule, wherever it stands, each to its slot in the order the rule's text
     * first declares it.
     */
    private void collectSegmentDefs(final List<?> statements, final Map<String, Integer> slots) {
        for (final Object stmt : statements) {
            if (stmt instanceof TALScriptModel.SegmentDefStatement) {
                TALInputs.require(TALConstruct.SEGMENT_DEF, inputAlias);
                slots.putIfAbsent(((TALScriptModel.SegmentDefStatement) stmt).getVarName(), slots.size());
            } else if (stmt instanceof TALScriptModel.IfBlock) {
                collectSegmentDefs(((TALScriptModel.IfBlock) stmt).getThenStatements(), slots);
                collectSegmentDefs(((TALScriptModel.IfBlock) stmt).getElseStatements(), slots);
            } else if (stmt instanceof TALScriptModel.ForeachBlock) {
                collectSegmentDefs(((TALScriptModel.ForeachBlock) stmt).getBody(), slots);
            } else if (stmt instanceof TALScriptModel.ExtractorBlock) {
                collectSegmentDefs(((TALScriptModel.ExtractorBlock) stmt).getStatements(), slots);
            } else if (stmt instanceof TALScriptModel.FirstBlock) {
                collectSegmentDefs(((TALScriptModel.FirstBlock) stmt).getStatements(), slots);
            } else if (stmt instanceof TALScriptModel.BuildBlock) {
                collectSegmentDefs(((TALScriptModel.BuildBlock) stmt).getStatements(), slots);
            } else if (stmt instanceof TALScriptModel.PersistBlock) {
                collectSegmentDefs(((TALScriptModel.PersistBlock) stmt).getStatements(), slots);
            } else if (stmt instanceof TALScriptModel.SamplerBlock) {
                collectSegmentDefs(((TALScriptModel.SamplerBlock) stmt).getContents(), slots);
            }
        }
    }

    /**
     * Install the storage model for every metric the rule declared. Runs before the rule sees a span; on the
     * boot path {@code create} is create-if-absent, as it is for MAL.
     *
     * @param meterSystem the meter system to register with
     */
    public void createMetrics(final MeterSystem meterSystem) {
        for (final TALMetricDeclaration declaration : metricDeclarations) {
            meterSystem.create(declaration.fullName, declaration.function,
                ScopeType.valueOf(declaration.scopeType));
            if (declaration.labelLimit > 0) {
                meterSystem.limitLabels(declaration.fullName, declaration.labelLimit);
            }
        }
    }

    /**
     * Two blocks declaring the same metric with the same shape share one metric, which is how {@code if}
     * branches feed one series; the same name with a different function or scope is a compile error naming both.
     */
    private static List<TALMetricDeclaration> dedupeDeclarations(final List<TALMetricDeclaration> declarations) {
        final Map<String, TALMetricDeclaration> byName = new LinkedHashMap<>();
        final Set<TALMetricDeclaration> distinct = new LinkedHashSet<>(declarations);
        for (final TALMetricDeclaration declaration : distinct) {
            final TALMetricDeclaration previous = byName.put(declaration.fullName, declaration);
            if (previous != null) {
                throw new IllegalArgumentException(
                    "metric " + declaration.fullName + " is declared twice with different shapes: "
                        + previous + " and " + declaration);
            }
        }
        return new ArrayList<>(byName.values());
    }

    /**
     * {@code networkAliasAddress}, from the rule's {@code buildNetworkAlias}: it runs on the trace's owner after a
     * call's two halves matched, where there is no span, only the caller half as {@code pairing}. So the expression
     * may read {@code pairing.*}, literals and {@code callerScopedAddress(...)}, and nothing of a span.
     */
    private String generateNetworkAliasMethod(final TALScriptModel.NetworkAliasStatement build,
                                              final TALGenCtx.ClassMembers members) {
        requirePairingOnly(build.getValue());
        final TALGenCtx aliasCtx = new TALGenCtx(
            new TALSpanRootResolver(inputType, functionRegistry, inputAlias), inputType);
        aliasCtx.inputAlias = inputAlias;
        aliasCtx.classMembers = members;
        aliasCtx.localVars.put(PAIRING, new TALLocalVar(PAIRING, PairingFact.class));
        final StringBuilder address = new StringBuilder();
        TALValueCodegen.generateStringValueAccess(address, build.getValue(), build.getCastType(), aliasCtx);
        return "public String networkAliasAddress(" + FILTER_SPEC + " filterSpec, " + EXEC_CTX + " ctx, "
            + PairingFact.class.getName() + " " + PAIRING + ") {\n"
            + "  " + HELPER + " h = new " + HELPER + "(ctx, filterSpec);\n"
            + aliasCtx.localVarDecls
            + "  return " + address + ";\n"
            + "}\n";
    }

    private static void requirePairingOnly(final TALScriptModel.ValueAccess value) {
        if (value == null || value.isStringLiteral() || value.isNumberLiteral()) {
            return;
        }
        if (value.getFunctionCallName() != null) {
            if (!CALLER_SCOPED_ADDRESS.equals(value.getFunctionCallName())) {
                throw new IllegalArgumentException(
                    "buildNetworkAlias runs on the trace's owner with the caller half only; it may call "
                        + CALLER_SCOPED_ADDRESS + "(...), not " + value.getFunctionCallName() + "(...)");
            }
            for (final TALScriptModel.FunctionArg arg : value.getFunctionCallArgs()) {
                requirePairingOnly(arg.getValue());
            }
            return;
        }
        if (!value.getConcatParts().isEmpty()) {
            value.getConcatParts().forEach(TALClassGenerator::requirePairingOnly);
            return;
        }
        if (value.getParenInner() != null) {
            requirePairingOnly(value.getParenInner());
            return;
        }
        // Only the caller and the peer it dialed: a per-call field, as pairing.spanId, would write a row per call.
        if (value.isMetaRef() || value.isInputRef() || value.getSegments().size() != 2
            || !PAIRING.equals(value.getSegments().get(0))
            || !PAIRING_FIELDS.contains(value.getSegments().get(1))) {
            throw new IllegalArgumentException(
                "buildNetworkAlias runs on the trace's owner with the caller half only, so its address reads "
                    + "pairing.service and pairing.peer, not '" + value.toPathString() + "'");
        }
    }

    /**
     * @param sourceRef where the rule came from, or null
     * @return {@code org.apache.skywalking.trace.analysis.script.<script name>}, the script being the rule file's
     * name without directory and extension, {@code unknown} when the rule has no file
     */
    public static String scriptLoggerName(final DslSourceRef sourceRef) {
        String file = sourceRef == null || sourceRef.getYamlFile() == null ? "unknown" : sourceRef.getYamlFile();
        final int slash = file.lastIndexOf('/');
        if (slash >= 0) {
            file = file.substring(slash + 1);
        }
        final int dot = file.lastIndexOf('.');
        if (dot > 0) {
            file = file.substring(0, dot);
        }
        return SCRIPT_LOGGER_PREFIX + file;
    }

    /**
     * Assemble the exact Java source handed to Javassist, so the line numbers attached to the methods refer to
     * text that exists rather than to an envelope reconstructed later.
     */
    private String buildSourceText(final CtClass ctClass, final String memberFields, final String executeBody) {
        final StringBuilder out = new StringBuilder();
        out.append("// generated from ")
           .append(sourceRef == null ? "<unknown>" : sourceRef.toString()).append('\n');
        out.append("public class ").append(ctClass.getSimpleName())
           .append(" implements ").append(EXPRESSION).append(" {\n");
        out.append("  private static final org.slf4j.Logger ").append(SCRIPT_LOGGER)
           .append(" = org.slf4j.LoggerFactory.getLogger(\"").append(scriptLoggerName(sourceRef)).append("\");\n");
        out.append(memberFields);
        out.append(executeBody);
        out.append("}\n");
        return out.toString();
    }


    /**
     * One generated method: its name, its source, and its local-variable table, null for a method without one.
     */
    private static final class GeneratedMethod {
        private final String name;
        private final String source;
        private final List<String[]> lvt;

        private GeneratedMethod(final String name, final String source, final List<String[]> lvt) {
            this.name = name;
            this.source = source;
            this.lvt = lvt;
        }
    }

    /**
     * A rule's methods, in the order they are added to the class, and the generation state they came from.
     */
    private static final class GeneratedRule {
        private final TALGenCtx genCtx;
        private final List<GeneratedMethod> methods;

        private GeneratedRule(final TALGenCtx genCtx, final List<GeneratedMethod> methods) {
            this.genCtx = genCtx;
            this.methods = methods;
        }
    }
}
