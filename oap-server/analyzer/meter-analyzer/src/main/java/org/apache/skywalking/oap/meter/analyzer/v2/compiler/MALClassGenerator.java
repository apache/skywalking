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

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalExpressionPackageHolder;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.DownsamplingType;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.ExpressionMetadata;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalFilter;
import org.apache.skywalking.oap.server.core.WorkPath;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Generates {@link MalExpression} implementation classes from
 * {@link MALExpressionModel} AST using Javassist bytecode generation.
 *
 * <p>Each generated class implements:
 * <pre>
 *   SampleFamily run(Map&lt;String, SampleFamily&gt; samples)
 * </pre>
 */
@Slf4j
public final class MALClassGenerator {

    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger(0);

    private static final String PACKAGE_PREFIX =
        "org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.";

    private static final String SF = "org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily";

    private static final Set<String> USED_CLASS_NAMES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private final ClassPool classPool;
    private List<String> closureFieldNames;
    private int closureFieldIndex;
    private File classOutputDir;
    private String classNameHint;
    private String yamlSource;

    public MALClassGenerator() {
        this(createClassPool());
        if (StringUtil.isNotEmpty(System.getenv("SW_DYNAMIC_CLASS_ENGINE_DEBUG"))) {
            classOutputDir = new File(WorkPath.getPath().getParentFile(), "mal-rt");
        }
    }

    private static ClassPool createClassPool() {
        final ClassPool pool = new ClassPool(true);
        pool.appendClassPath(
            new javassist.LoaderClassPath(
                Thread.currentThread().getContextClassLoader()));
        return pool;
    }

    public MALClassGenerator(final ClassPool classPool) {
        this.classPool = classPool;
    }

    public void setClassOutputDir(final File dir) {
        this.classOutputDir = dir;
    }

    public void setClassNameHint(final String hint) {
        this.classNameHint = hint;
    }

    public void setYamlSource(final String yamlSource) {
        this.yamlSource = yamlSource;
    }

    private String makeClassName(final String defaultPrefix) {
        if (classNameHint != null) {
            return dedupClassName(PACKAGE_PREFIX + buildHintedName());
        }
        return PACKAGE_PREFIX + defaultPrefix + CLASS_COUNTER.getAndIncrement();
    }

    /**
     * Builds class name from {@code yamlSource} + {@code classNameHint}.
     * Pattern: {@code {yamlBaseName}_L{lineNo}_{hint}} when yamlSource is available,
     * falls back to just {@code {hint}} otherwise.
     */
    private String buildHintedName() {
        final String hint = MALCodegenHelper.sanitizeName(classNameHint);
        if (yamlSource == null) {
            return hint;
        }
        String yamlBase = yamlSource;
        String lineNo = null;
        final int colonIdx = yamlSource.lastIndexOf(':');
        if (colonIdx > 0) {
            yamlBase = yamlSource.substring(0, colonIdx);
            lineNo = yamlSource.substring(colonIdx + 1);
        }
        final int dotIdx = yamlBase.lastIndexOf('.');
        if (dotIdx > 0) {
            yamlBase = yamlBase.substring(0, dotIdx);
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(MALCodegenHelper.sanitizeName(yamlBase));
        if (lineNo != null) {
            sb.append("_L").append(lineNo);
        }
        sb.append('_').append(hint);
        return sb.toString();
    }

    private String dedupClassName(final String base) {
        if (USED_CLASS_NAMES.add(base)) {
            return base;
        }
        for (int i = 2; ; i++) {
            final String candidate = base + "_" + i;
            if (USED_CLASS_NAMES.add(candidate)) {
                return candidate;
            }
        }
    }

    void writeClassFile(final CtClass ctClass) {
        if (classOutputDir == null) {
            return;
        }
        if (!classOutputDir.exists()) {
            classOutputDir.mkdirs();
        }
        final File file = new File(classOutputDir, ctClass.getSimpleName() + ".class");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            ctClass.toBytecode(out);
        } catch (Exception e) {
            log.warn("Failed to write class file {}: {}", file, e.getMessage(), e);
        }
    }

    /**
     * Adds a {@code LineNumberTable} attribute to the method by scanning
     * bytecode for store instructions to local variable slots &ge;
     * {@code firstResultSlot}. Each such store marks the end of a
     * source-level statement; the following instruction gets the next
     * sequential line number.
     *
     * <p>This gives meaningful line numbers in stack traces even though
     * the generated Java source is compiled in-memory by Javassist
     * (which does not produce line numbers on its own).
     *
     * @param method          the compiled method
     * @param firstResultSlot the first local variable slot that holds
     *                        a generated result variable (stores to
     *                        earlier slots are parameters and ignored)
     */
    void addLineNumberTable(final javassist.CtMethod method,
                            final int firstResultSlot) {
        try {
            final javassist.bytecode.MethodInfo mi = method.getMethodInfo();
            final javassist.bytecode.CodeAttribute code = mi.getCodeAttribute();
            if (code == null) {
                return;
            }

            final List<int[]> entries = new ArrayList<>();
            int line = 1;
            boolean nextIsNewLine = true;

            final javassist.bytecode.CodeIterator ci = code.iterator();
            while (ci.hasNext()) {
                final int pc = ci.next();
                if (nextIsNewLine) {
                    entries.add(new int[]{pc, line++});
                    nextIsNewLine = false;
                }
                final int op = ci.byteAt(pc) & 0xFF;
                int slot = -1;
                // Compact store opcodes: istore_0(59)..astore_3(78)
                if (op >= 59 && op <= 78) {
                    slot = (op - 59) % 4;
                }
                // Wide store opcodes: istore(54)..astore(58)
                else if (op >= 54 && op <= 58) {
                    slot = ci.byteAt(pc + 1) & 0xFF;
                }
                if (slot >= firstResultSlot) {
                    nextIsNewLine = true;
                }
            }

            if (entries.isEmpty()) {
                return;
            }

            // Build LineNumberTable: u2 count, then (u2 start_pc, u2 line_number)[]
            final javassist.bytecode.ConstPool cp = mi.getConstPool();
            final byte[] info = new byte[2 + entries.size() * 4];
            info[0] = (byte) (entries.size() >> 8);
            info[1] = (byte) entries.size();
            for (int i = 0; i < entries.size(); i++) {
                final int off = 2 + i * 4;
                info[off] = (byte) (entries.get(i)[0] >> 8);
                info[off + 1] = (byte) entries.get(i)[0];
                info[off + 2] = (byte) (entries.get(i)[1] >> 8);
                info[off + 3] = (byte) entries.get(i)[1];
            }
            code.getAttributes().add(
                new javassist.bytecode.AttributeInfo(cp, "LineNumberTable", info));
        } catch (Exception e) {
            log.warn("Failed to add LineNumberTable: {}", e.getMessage());
        }
    }

    /**
     * Builds the SourceFile name for a generated class. When YAML source info
     * is available, produces {@code "spring-sleuth[3](metricName.java)"};
     * otherwise falls back to {@code "metricName.java"}.
     */
    private String formatSourceFileName(final String metricName) {
        final String classFile = metricName + ".java";
        if (yamlSource != null) {
            return "(" + yamlSource + ")" + classFile;
        }
        return classFile;
    }

    /**
     * Sets the {@code SourceFile} attribute of the class to the given name,
     * replacing the default (class name + ".java"). This makes stack traces
     * show the metric/rule name instead of the generated class name.
     */
    private static void setSourceFile(final CtClass ctClass, final String name) {
        try {
            final javassist.bytecode.ClassFile cf = ctClass.getClassFile();
            final javassist.bytecode.AttributeInfo sf = cf.getAttribute("SourceFile");
            if (sf != null) {
                final javassist.bytecode.ConstPool cp = cf.getConstPool();
                final int idx = cp.addUtf8Info(name);
                sf.set(new byte[]{(byte) (idx >> 8), (byte) idx});
            }
        } catch (Exception e) {
            // best-effort — ignore
        }
    }

    void addLocalVariableTable(final javassist.CtMethod method,
                                       final String className,
                                       final String[][] vars) {
        try {
            final javassist.bytecode.MethodInfo mi = method.getMethodInfo();
            final javassist.bytecode.CodeAttribute code = mi.getCodeAttribute();
            if (code == null) {
                return;
            }
            final javassist.bytecode.ConstPool cp = mi.getConstPool();
            final int len = code.getCodeLength();

            final javassist.bytecode.LocalVariableAttribute lva =
                new javassist.bytecode.LocalVariableAttribute(cp);
            int slot = 0;
            lva.addEntry(0, len,
                cp.addUtf8Info("this"),
                cp.addUtf8Info("L" + className.replace('.', '/') + ";"), slot++);
            for (final String[] var : vars) {
                lva.addEntry(0, len,
                    cp.addUtf8Info(var[0]),
                    cp.addUtf8Info(var[1]), slot++);
            }
            code.getAttributes().add(lva);
        } catch (Exception e) {
            log.warn("Failed to add LocalVariableTable: {}", e.getMessage());
        }
    }

    private void addRunLocalVariableTable(final javassist.CtMethod method,
                                          final String className,
                                          final int tempCount) {
        final String sfDesc = "L" + SF.replace('.', '/') + ";";
        final String[][] vars = new String[2 + tempCount][];
        vars[0] = new String[]{"samples", "Ljava/util/Map;"};
        vars[1] = new String[]{RUN_VAR, sfDesc};
        for (int i = 0; i < tempCount; i++) {
            vars[2 + i] = new String[]{"_t" + i, sfDesc};
        }
        addLocalVariableTable(method, className, vars);
    }

    /**
     * Compiles a MAL expression into a MalExpression implementation.
     *
     * @param metricName the metric name (used in the generated class name)
     * @param expression the MAL expression string
     * @return a MalExpression instance
     * @throws Exception if parsing or compilation fails
     */
    public MalExpression compile(final String metricName,
                                 final String expression) throws Exception {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(expression);
        final String saved = classNameHint;
        if (classNameHint == null) {
            classNameHint = metricName;
        }
        try {
            return compileFromModel(metricName, ast);
        } finally {
            classNameHint = saved;
        }
    }

    /**
     * Compiles a MAL filter closure into a {@link MalFilter} implementation.
     *
     * @param filterExpression e.g. {@code "{ tags -> tags.job_name == 'mysql-monitoring' }"}
     * @return a MalFilter instance
     * @throws Exception if parsing or compilation fails
     */
    @SuppressWarnings("unchecked")
    public MalFilter compileFilter(final String filterExpression) throws Exception {
        final MALExpressionModel.ClosureArgument closure =
            MALScriptParser.parseFilter(filterExpression);

        final String className = makeClassName("MalFilter_");

        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalFilter"));

        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        final List<String> params = closure.getParams();
        final String paramName = params.isEmpty() ? "it" : params.get(0);

        final MALClosureCodegen cc = new MALClosureCodegen(classPool, this);
        final StringBuilder sb = new StringBuilder();
        sb.append("public boolean test(java.util.Map ").append(paramName)
          .append(") {\n");

        final List<MALExpressionModel.ClosureStatement> body = closure.getBody();
        if (body.size() == 1
                && body.get(0) instanceof MALExpressionModel.ClosureExprStatement) {
            // Single expression → evaluate as condition and return boolean
            final MALExpressionModel.ClosureExpr expr =
                ((MALExpressionModel.ClosureExprStatement) body.get(0)).getExpr();
            if (expr instanceof MALExpressionModel.ClosureCondition) {
                sb.append("  return ");
                cc.generateClosureCondition(
                    sb, (MALExpressionModel.ClosureCondition) expr, paramName);
                sb.append(";\n");
            } else {
                // Truthy evaluation of the expression
                sb.append("  Object _v = ");
                cc.generateClosureExpr(sb, expr, paramName);
                sb.append(";\n");
                sb.append("  return _v != null && !Boolean.FALSE.equals(_v);\n");
            }
        } else {
            // Multi-statement body — generate statements, last expression is the return
            for (final MALExpressionModel.ClosureStatement stmt : body) {
                cc.generateClosureStatement(sb, stmt, paramName);
            }
            sb.append("  return false;\n");
        }
        sb.append("}\n");

        final String filterBody = sb.toString();
        if (log.isDebugEnabled()) {
            log.debug("MAL compileFilter AST: {}", closure);
            log.debug("MAL compileFilter test():\n{}", filterBody);
        }

        final javassist.CtMethod testMethod =
            CtNewMethod.make(filterBody, ctClass);
        ctClass.addMethod(testMethod);
        addLocalVariableTable(testMethod, className, new String[][]{
            {paramName, "Ljava/util/Map;"}
        });
        addLineNumberTable(testMethod, 2); // slot 0=this, 1=samples
        setSourceFile(ctClass, formatSourceFileName(
            classNameHint != null ? classNameHint : "filter"));

        writeClassFile(ctClass);

        final Class<?> clazz = ctClass.toClass(MalExpressionPackageHolder.class);
        ctClass.detach();
        return (MalFilter) clazz.getDeclaredConstructor().newInstance();
    }

    /**
     * Compiles from a pre-parsed AST model.
     *
     * @param metricName the metric name (used in the generated class name)
     * @param ast the pre-parsed AST model
     * @return a MalExpression instance
     * @throws Exception if compilation fails
     */
    public MalExpression compileFromModel(final String metricName,
                                          final MALExpressionModel.Expr ast) throws Exception {
        final String className = makeClassName("MalExpr_");

        closureFieldIndex = 0;
        final MALClosureCodegen cc = new MALClosureCodegen(classPool, this);
        final List<MALClosureCodegen.ClosureInfo> closures = new ArrayList<>();
        cc.collectClosures(ast, closures);

        // Build closure field names and determine interface types
        final List<String> closureFieldNames = new ArrayList<>();
        final List<String> closureInterfaceTypes = new ArrayList<>();
        final java.util.Map<String, Integer> closureNameCounts = new java.util.HashMap<>();
        for (int i = 0; i < closures.size(); i++) {
            final String purpose = closures.get(i).methodName;
            final int count = closureNameCounts.getOrDefault(purpose, 0);
            closureNameCounts.put(purpose, count + 1);
            final String suffix = count == 0 ? purpose : purpose + "_" + (count + 1);
            closureFieldNames.add("_" + suffix);
            closureInterfaceTypes.add(closures.get(i).interfaceType);
        }

        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get(
            "org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression"));

        // Generate companion classes — one per closure.
        // Each companion directly implements the functional interface with the
        // closure body inlined, so there is no static helper method on the main class.
        final List<CtClass> companionClasses = new ArrayList<>();
        for (int i = 0; i < closures.size(); i++) {
            final CtClass companion = cc.makeCompanionClass(
                ctClass, closureFieldNames.get(i), closures.get(i));
            companionClasses.add(companion);
        }

        // Add public static final fields, one per closure
        for (int i = 0; i < closures.size(); i++) {
            ctClass.addField(javassist.CtField.make(
                "public static final " + closureInterfaceTypes.get(i) + " "
                    + closureFieldNames.get(i) + ";", ctClass));
        }

        // Static initializer: explicitly instantiate each companion class.
        // No method lookup or LambdaMetafactory — the compiler guarantees
        // method existence because it generates both sides in the same pass.
        if (!closures.isEmpty()) {
            final StringBuilder staticInit = new StringBuilder();
            for (int i = 0; i < closures.size(); i++) {
                staticInit.append(closureFieldNames.get(i))
                          .append(" = new ").append(companionClasses.get(i).getName())
                          .append("();\n");
            }
            ctClass.makeClassInitializer().setBody("{ " + staticInit + "}");
        }

        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        this.closureFieldNames = closureFieldNames;
        this.closureFieldIndex = 0;
        final String runBody = generateRunMethod(ast);
        final ExpressionMetadata metadata = extractMetadata(ast);
        final String metadataBody = generateMetadataMethod(metadata);

        if (log.isDebugEnabled()) {
            log.debug("MAL compile [{}] AST: {}", metricName, ast);
            log.debug("MAL compile [{}] run():\n{}", metricName, runBody);
            log.debug("MAL compile [{}] metadata():\n{}", metricName, metadataBody);
        }

        final javassist.CtMethod runMethod = CtNewMethod.make(runBody, ctClass);
        ctClass.addMethod(runMethod);
        addRunLocalVariableTable(runMethod, className, runTempCounter);
        addLineNumberTable(runMethod, 2); // slot 2 = sf
        final javassist.CtMethod metaMethod =
            CtNewMethod.make(metadataBody, ctClass);
        ctClass.addMethod(metaMethod);
        addLocalVariableTable(metaMethod, className, new String[][]{
            {"_samples", "Ljava/util/List;"},
            {"_scopeLabels", "Ljava/util/Set;"},
            {"_aggLabels", "Ljava/util/Set;"},
            {"_pct", "[I"}
        });
        setSourceFile(ctClass, formatSourceFileName(metricName));

        // Load companions before main class — main class static initializer
        // references companion constructors, so companions must be loaded first.
        for (final CtClass companion : companionClasses) {
            writeClassFile(companion);
            companion.toClass(MalExpressionPackageHolder.class);
            companion.detach();
        }

        writeClassFile(ctClass);

        final Class<?> clazz = ctClass.toClass(MalExpressionPackageHolder.class);
        ctClass.detach();

        return (MalExpression) clazz.getDeclaredConstructor().newInstance();
    }

    private static final String RUN_VAR = "sf";

    private int runTempCounter;

    private String generateRunMethod(final MALExpressionModel.Expr ast) {
        runTempCounter = 0;
        final StringBuilder sb = new StringBuilder();
        sb.append("public ").append(SF).append(" run(java.util.Map samples) {\n");
        sb.append("  ").append(SF).append(" ").append(RUN_VAR).append(";\n");
        generateExprStatements(sb, ast);
        sb.append("  return ").append(RUN_VAR).append(";\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String nextTemp() {
        return "_t" + runTempCounter++;
    }

    /**
     * Emits the expression as a series of {@code sf = ...;} reassignment statements,
     * one per chain call. All results are stored in the single {@code sf} variable.
     * For binary SF op SF expressions, a temporary variable saves the left operand.
     */
    private void generateExprStatements(final StringBuilder sb,
                                        final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.MetricExpr) {
            generateMetricExprStatements(
                sb, (MALExpressionModel.MetricExpr) expr);
        } else if (expr instanceof MALExpressionModel.NumberExpr) {
            final double val = ((MALExpressionModel.NumberExpr) expr).getValue();
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(SF).append(".EMPTY.plus(Double.valueOf(")
              .append(val).append("));\n");
        } else if (expr instanceof MALExpressionModel.BinaryExpr) {
            generateBinaryExprStatements(
                sb, (MALExpressionModel.BinaryExpr) expr);
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            generateExprStatements(
                sb, ((MALExpressionModel.UnaryNegExpr) expr).getOperand());
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(RUN_VAR).append(".negative();\n");
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            generateFunctionCallStatements(
                sb, (MALExpressionModel.FunctionCallExpr) expr);
        } else if (expr instanceof MALExpressionModel.ParenChainExpr) {
            generateParenChainStatements(
                sb, (MALExpressionModel.ParenChainExpr) expr);
        } else {
            throw new IllegalArgumentException("Unknown expr type: " + expr);
        }
    }

    private void generateMetricExprStatements(
            final StringBuilder sb, final MALExpressionModel.MetricExpr expr) {
        sb.append("  ").append(RUN_VAR).append(" = ((").append(SF)
          .append(") samples.getOrDefault(\"")
          .append(MALCodegenHelper.escapeJava(expr.getMetricName()))
          .append("\", ").append(SF).append(".EMPTY));\n");
        emitChainStatements(sb, expr.getMethodChain());
    }

    private void generateParenChainStatements(
            final StringBuilder sb, final MALExpressionModel.ParenChainExpr expr) {
        generateExprStatements(sb, expr.getInner());
        emitChainStatements(sb, expr.getMethodChain());
    }

    private void generateFunctionCallStatements(
            final StringBuilder sb, final MALExpressionModel.FunctionCallExpr expr) {
        final String fn = expr.getFunctionName();
        final List<MALExpressionModel.Argument> args = expr.getArguments();

        if (("count".equals(fn) || "topN".equals(fn)) && !args.isEmpty()) {
            final MALExpressionModel.Argument firstArg = args.get(0);
            if (firstArg instanceof MALExpressionModel.ExprArgument) {
                generateExprStatements(
                    sb, ((MALExpressionModel.ExprArgument) firstArg).getExpr());
            }
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(RUN_VAR).append('.').append(fn).append('(');
            for (int i = 1; i < args.size(); i++) {
                if (i > 1) {
                    sb.append(", ");
                }
                generateArgument(sb, args.get(i));
            }
            sb.append(");\n");
        } else {
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(fn).append('(');
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                generateArgument(sb, args.get(i));
            }
            sb.append(");\n");
        }
        emitChainStatements(sb, expr.getMethodChain());
    }

    private void generateBinaryExprStatements(
            final StringBuilder sb, final MALExpressionModel.BinaryExpr expr) {
        final MALExpressionModel.Expr left = expr.getLeft();
        final MALExpressionModel.Expr right = expr.getRight();
        final MALExpressionModel.ArithmeticOp op = expr.getOp();

        final boolean leftIsNumber = left instanceof MALExpressionModel.NumberExpr
            || isScalarFunction(left);
        final boolean rightIsNumber = right instanceof MALExpressionModel.NumberExpr
            || isScalarFunction(right);

        if (leftIsNumber && !rightIsNumber) {
            generateExprStatements(sb, right);
            sb.append("  ").append(RUN_VAR).append(" = ");
            switch (op) {
                case ADD:
                    sb.append(RUN_VAR).append(".plus(Double.valueOf(");
                    generateScalarExpr(sb, left);
                    sb.append("))");
                    break;
                case SUB:
                    sb.append(RUN_VAR).append(".minus(Double.valueOf(");
                    generateScalarExpr(sb, left);
                    sb.append(")).negative()");
                    break;
                case MUL:
                    sb.append(RUN_VAR).append(".multiply(Double.valueOf(");
                    generateScalarExpr(sb, left);
                    sb.append("))");
                    break;
                case DIV:
                    sb.append("org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt")
                      .append(".MalRuntimeHelper.divReverse(");
                    generateScalarExpr(sb, left);
                    sb.append(", ").append(RUN_VAR).append(")");
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported op: " + op);
            }
            sb.append(";\n");
        } else if (!leftIsNumber && rightIsNumber) {
            generateExprStatements(sb, left);
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(RUN_VAR).append(".").append(MALCodegenHelper.opMethodName(op))
              .append("(Double.valueOf(");
            generateScalarExpr(sb, right);
            sb.append("));\n");
        } else {
            // SF op SF: compute left to sf, save to temp, compute right to sf, combine
            generateExprStatements(sb, left);
            final String temp = nextTemp();
            sb.append("  ").append(SF).append(" ").append(temp)
              .append(" = ").append(RUN_VAR).append(";\n");
            generateExprStatements(sb, right);
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(temp).append(".").append(MALCodegenHelper.opMethodName(op))
              .append("(").append(RUN_VAR).append(");\n");
        }
    }

    /**
     * Emits each method chain call as a reassignment of {@code sf}.
     */
    private void emitChainStatements(final StringBuilder sb,
                                     final List<MALExpressionModel.MethodCall> chain) {
        for (final MALExpressionModel.MethodCall mc : chain) {
            sb.append("  ").append(RUN_VAR).append(" = ")
              .append(RUN_VAR).append('.').append(mc.getName()).append('(');
            final List<MALExpressionModel.Argument> args = mc.getArguments();
            if (MALCodegenHelper.VARARGS_STRING_METHODS.contains(mc.getName()) && !args.isEmpty()
                    && allStringArgs(args)) {
                sb.append("new String[]{");
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    generateArgument(sb, args.get(i));
                }
                sb.append('}');
            } else {
                final boolean primitiveDouble =
                    MALCodegenHelper.PRIMITIVE_DOUBLE_METHODS.contains(mc.getName());
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    generateMethodCallArg(sb, args.get(i), primitiveDouble);
                }
            }
            sb.append(");\n");
        }
    }

    private void generateExpr(final StringBuilder sb,
                              final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.MetricExpr) {
            generateMetricExpr(sb, (MALExpressionModel.MetricExpr) expr);
        } else if (expr instanceof MALExpressionModel.NumberExpr) {
            final double val = ((MALExpressionModel.NumberExpr) expr).getValue();
            sb.append(SF).append(".EMPTY.plus(Double.valueOf(").append(val).append("))");
        } else if (expr instanceof MALExpressionModel.BinaryExpr) {
            generateBinaryExpr(sb, (MALExpressionModel.BinaryExpr) expr);
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            sb.append("(");
            generateExpr(sb, ((MALExpressionModel.UnaryNegExpr) expr).getOperand());
            sb.append(").negative()");
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            generateFunctionCallExpr(sb, (MALExpressionModel.FunctionCallExpr) expr);
        } else if (expr instanceof MALExpressionModel.ParenChainExpr) {
            generateParenChainExpr(sb, (MALExpressionModel.ParenChainExpr) expr);
        }
    }

    private void generateMetricExpr(final StringBuilder sb,
                                    final MALExpressionModel.MetricExpr expr) {
        sb.append("((").append(SF)
          .append(") samples.getOrDefault(\"")
          .append(MALCodegenHelper.escapeJava(expr.getMetricName()))
          .append("\", ").append(SF).append(".EMPTY))");
        generateMethodChain(sb, expr.getMethodChain());
    }

    private void generateFunctionCallExpr(final StringBuilder sb,
                                          final MALExpressionModel.FunctionCallExpr expr) {
        // Top-level functions like count(metric), topN(metric, n, Order)
        // These are static-style calls on the first argument (SampleFamily)
        final String fn = expr.getFunctionName();
        final List<MALExpressionModel.Argument> args = expr.getArguments();

        if (("count".equals(fn) || "topN".equals(fn)) && !args.isEmpty()) {
            // First arg is the SampleFamily
            final MALExpressionModel.Argument firstArg = args.get(0);
            if (firstArg instanceof MALExpressionModel.ExprArgument) {
                generateExpr(sb,
                    ((MALExpressionModel.ExprArgument) firstArg).getExpr());
            }
            sb.append('.').append(fn).append('(');
            for (int i = 1; i < args.size(); i++) {
                if (i > 1) {
                    sb.append(", ");
                }
                generateArgument(sb, args.get(i));
            }
            sb.append(')');
        } else {
            // Generic function call
            sb.append(fn).append('(');
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                generateArgument(sb, args.get(i));
            }
            sb.append(')');
        }
        generateMethodChain(sb, expr.getMethodChain());
    }

    private void generateParenChainExpr(final StringBuilder sb,
                                        final MALExpressionModel.ParenChainExpr expr) {
        sb.append("(");
        generateExpr(sb, expr.getInner());
        sb.append(")");
        generateMethodChain(sb, expr.getMethodChain());
    }

    private void generateBinaryExpr(final StringBuilder sb,
                                    final MALExpressionModel.BinaryExpr expr) {
        final MALExpressionModel.Expr left = expr.getLeft();
        final MALExpressionModel.Expr right = expr.getRight();
        final MALExpressionModel.ArithmeticOp op = expr.getOp();

        final boolean leftIsNumber = left instanceof MALExpressionModel.NumberExpr
            || isScalarFunction(left);
        final boolean rightIsNumber = right instanceof MALExpressionModel.NumberExpr
            || isScalarFunction(right);

        if (leftIsNumber && !rightIsNumber) {
            // N op SF -> swap to SF.op(N) with special handling for SUB and DIV
            switch (op) {
                case ADD:
                    sb.append("(");
                    generateExpr(sb, right);
                    sb.append(").plus(Double.valueOf(");
                    generateScalarExpr(sb, left);
                    sb.append("))");
                    break;
                case SUB:
                    sb.append("(");
                    generateExpr(sb, right);
                    sb.append(").minus(Double.valueOf(");
                    generateScalarExpr(sb, left);
                    sb.append(")).negative()");
                    break;
                case MUL:
                    sb.append("(");
                    generateExpr(sb, right);
                    sb.append(").multiply(Double.valueOf(");
                    generateScalarExpr(sb, left);
                    sb.append("))");
                    break;
                case DIV:
                    sb.append("org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt")
                      .append(".MalRuntimeHelper.divReverse(");
                    generateScalarExpr(sb, left);
                    sb.append(", ");
                    generateExpr(sb, right);
                    sb.append(")");
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported op: " + op);
            }
        } else if (!leftIsNumber && rightIsNumber) {
            // SF op N
            sb.append("(");
            generateExpr(sb, left);
            sb.append(").").append(MALCodegenHelper.opMethodName(op))
              .append("(Double.valueOf(");
            generateScalarExpr(sb, right);
            sb.append("))");
        } else {
            // SF op SF (both non-number)
            sb.append("(");
            generateExpr(sb, left);
            sb.append(").").append(MALCodegenHelper.opMethodName(op)).append("(");
            generateExpr(sb, right);
            sb.append(")");
        }
    }

    private void generateMethodChain(final StringBuilder sb,
                                     final List<MALExpressionModel.MethodCall> chain) {
        for (final MALExpressionModel.MethodCall mc : chain) {
            sb.append('.').append(mc.getName()).append('(');
            final List<MALExpressionModel.Argument> args = mc.getArguments();
            if (MALCodegenHelper.VARARGS_STRING_METHODS.contains(mc.getName()) && !args.isEmpty()
                    && allStringArgs(args)) {
                sb.append("new String[]{");
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    generateArgument(sb, args.get(i));
                }
                sb.append('}');
            } else {
                final boolean primitiveDouble =
                    MALCodegenHelper.PRIMITIVE_DOUBLE_METHODS.contains(mc.getName());
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    generateMethodCallArg(sb, args.get(i), primitiveDouble);
                }
            }
            sb.append(')');
        }
    }

    /**
     * Generates a method call argument, handling numeric ExprArgument
     * specially when the target method expects a primitive double.
     */
    private void generateMethodCallArg(final StringBuilder sb,
                                        final MALExpressionModel.Argument arg,
                                        final boolean primitiveDouble) {
        if (primitiveDouble
                && arg instanceof MALExpressionModel.ExprArgument) {
            final MALExpressionModel.Expr innerExpr =
                ((MALExpressionModel.ExprArgument) arg).getExpr();
            if (innerExpr instanceof MALExpressionModel.NumberExpr) {
                // Emit raw double literal for methods taking primitive double
                final double num =
                    ((MALExpressionModel.NumberExpr) innerExpr).getValue();
                sb.append(num);
                return;
            }
        }
        generateArgument(sb, arg);
    }

    private static boolean allStringArgs(final List<MALExpressionModel.Argument> args) {
        for (final MALExpressionModel.Argument arg : args) {
            if (!(arg instanceof MALExpressionModel.StringArgument)
                    && !(arg instanceof MALExpressionModel.NullArgument)) {
                return false;
            }
        }
        return true;
    }

    private void generateArgument(final StringBuilder sb,
                                  final MALExpressionModel.Argument arg) {
        if (arg instanceof MALExpressionModel.StringArgument) {
            sb.append('"')
              .append(MALCodegenHelper.escapeJava(((MALExpressionModel.StringArgument) arg).getValue()))
              .append('"');
        } else if (arg instanceof MALExpressionModel.StringListArgument) {
            final List<String> vals =
                ((MALExpressionModel.StringListArgument) arg).getValues();
            // Use Arrays.asList — Javassist cannot resolve List.of() varargs for >10 elements.
            sb.append("java.util.Arrays.asList(new String[]{");
            for (int i = 0; i < vals.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append('"').append(MALCodegenHelper.escapeJava(vals.get(i))).append('"');
            }
            sb.append("})");
        } else if (arg instanceof MALExpressionModel.NumberListArgument) {
            final List<Double> vals =
                ((MALExpressionModel.NumberListArgument) arg).getValues();
            // Use Arrays.asList — Javassist cannot resolve List.of() varargs for >10 elements.
            sb.append("java.util.Arrays.asList(new Number[]{");
            for (int i = 0; i < vals.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                final double v = vals.get(i);
                if (v == Math.floor(v) && !Double.isInfinite(v)) {
                    sb.append("Integer.valueOf(").append((int) v).append(')');
                } else {
                    sb.append("Double.valueOf(").append(v).append(')');
                }
            }
            sb.append("})");
        } else if (arg instanceof MALExpressionModel.BoolArgument) {
            sb.append(((MALExpressionModel.BoolArgument) arg).isValue());
        } else if (arg instanceof MALExpressionModel.NullArgument) {
            sb.append("null");
        } else if (arg instanceof MALExpressionModel.EnumRefArgument) {
            final MALExpressionModel.EnumRefArgument enumRef =
                (MALExpressionModel.EnumRefArgument) arg;
            final String fqcn = MALCodegenHelper.ENUM_FQCN.get(enumRef.getEnumType());
            if (fqcn != null) {
                sb.append(fqcn);
            } else {
                sb.append(enumRef.getEnumType());
            }
            sb.append('.').append(enumRef.getEnumValue());
        } else if (arg instanceof MALExpressionModel.ExprArgument) {
            final MALExpressionModel.Expr innerExpr =
                ((MALExpressionModel.ExprArgument) arg).getExpr();
            if (innerExpr instanceof MALExpressionModel.NumberExpr) {
                // Numeric literal argument (e.g., valueEqual(1), multiply(100))
                // Emit as Double.valueOf() to match Number parameter types.
                final double num = ((MALExpressionModel.NumberExpr) innerExpr).getValue();
                sb.append("Double.valueOf(").append(num).append(")");
            } else if (innerExpr instanceof MALExpressionModel.MetricExpr
                    && ((MALExpressionModel.MetricExpr) innerExpr).getMethodChain().isEmpty()) {
                // Bare identifier — could be an enum constant like SUM, AVG
                final String name =
                    ((MALExpressionModel.MetricExpr) innerExpr).getMetricName();
                if (MALCodegenHelper.isDownsamplingType(name)) {
                    sb.append(MALCodegenHelper.ENUM_FQCN.get("DownsamplingType")).append('.').append(name);
                } else {
                    // It's a metric reference used as argument (e.g., div(other_metric))
                    generateExpr(sb, innerExpr);
                }
            } else {
                generateExpr(sb, innerExpr);
            }
        } else if (arg instanceof MALExpressionModel.ClosureArgument) {
            generateClosureArgument(sb, (MALExpressionModel.ClosureArgument) arg);
        }
    }

    private void generateClosureArgument(final StringBuilder sb,
                                         final MALExpressionModel.ClosureArgument closure) {
        // Reference static closure field (no `this.` — fields are static final)
        sb.append(closureFieldNames.get(closureFieldIndex++));
    }

    // Closure statement/expr/condition generation delegated to MALClosureCodegen.

    private static void collectSampleNames(final MALExpressionModel.Expr expr,
                                            final Set<String> names) {
        if (expr instanceof MALExpressionModel.MetricExpr) {
            final MALExpressionModel.MetricExpr me = (MALExpressionModel.MetricExpr) expr;
            names.add(me.getMetricName());
            collectSampleNamesFromChain(me.getMethodChain(), names);
        } else if (expr instanceof MALExpressionModel.BinaryExpr) {
            collectSampleNames(((MALExpressionModel.BinaryExpr) expr).getLeft(), names);
            collectSampleNames(((MALExpressionModel.BinaryExpr) expr).getRight(), names);
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            collectSampleNames(
                ((MALExpressionModel.UnaryNegExpr) expr).getOperand(), names);
        } else if (expr instanceof MALExpressionModel.ParenChainExpr) {
            final MALExpressionModel.ParenChainExpr pce =
                (MALExpressionModel.ParenChainExpr) expr;
            collectSampleNames(pce.getInner(), names);
            collectSampleNamesFromChain(pce.getMethodChain(), names);
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            for (final MALExpressionModel.Argument arg :
                    ((MALExpressionModel.FunctionCallExpr) expr).getArguments()) {
                if (arg instanceof MALExpressionModel.ExprArgument) {
                    collectSampleNames(
                        ((MALExpressionModel.ExprArgument) arg).getExpr(), names);
                }
            }
        }
    }

    private static void collectSampleNamesFromChain(
            final List<MALExpressionModel.MethodCall> chain,
            final Set<String> names) {
        for (final MALExpressionModel.MethodCall mc : chain) {
            if ("downsampling".equals(mc.getName())) {
                continue;
            }
            for (final MALExpressionModel.Argument arg : mc.getArguments()) {
                if (arg instanceof MALExpressionModel.ExprArgument) {
                    collectSampleNames(
                        ((MALExpressionModel.ExprArgument) arg).getExpr(), names);
                }
            }
        }
    }

    /**
     * Extracts compile-time metadata from the AST by walking all method chains.
     */
    static ExpressionMetadata extractMetadata(final MALExpressionModel.Expr ast) {
        final Set<String> sampleNames = new LinkedHashSet<>();
        collectSampleNames(ast, sampleNames);

        ScopeType scopeType = null;
        final Set<String> scopeLabels = new LinkedHashSet<>();
        final Set<String> aggregationLabels = new LinkedHashSet<>();
        DownsamplingType downsampling = DownsamplingType.AVG;
        boolean isHistogram = false;
        int[] percentiles = null;

        final List<List<MALExpressionModel.MethodCall>> allChains = new ArrayList<>();
        collectMethodChains(ast, allChains);

        for (final List<MALExpressionModel.MethodCall> chain : allChains) {
            for (final MALExpressionModel.MethodCall mc : chain) {
                final String name = mc.getName();
                switch (name) {
                    case "sum":
                    case "avg":
                    case "max":
                    case "min":
                        addStringListLabels(mc, aggregationLabels);
                        break;
                    case "count":
                        addStringListLabels(mc, aggregationLabels);
                        break;
                    case "service":
                        scopeType = ScopeType.SERVICE;
                        addStringListLabels(mc, scopeLabels);
                        break;
                    case "instance":
                        scopeType = ScopeType.SERVICE_INSTANCE;
                        addAllStringListLabels(mc, scopeLabels);
                        break;
                    case "endpoint":
                        scopeType = ScopeType.ENDPOINT;
                        addAllStringListLabels(mc, scopeLabels);
                        break;
                    case "process":
                        scopeType = ScopeType.PROCESS;
                        addAllStringListLabels(mc, scopeLabels);
                        addStringArgLabels(mc, scopeLabels);
                        break;
                    case "serviceRelation":
                        scopeType = ScopeType.SERVICE_RELATION;
                        addAllStringListLabels(mc, scopeLabels);
                        addStringArgLabels(mc, scopeLabels);
                        break;
                    case "processRelation":
                        scopeType = ScopeType.PROCESS_RELATION;
                        addAllStringListLabels(mc, scopeLabels);
                        addStringArgLabels(mc, scopeLabels);
                        break;
                    case "histogram":
                        isHistogram = true;
                        break;
                    case "histogram_percentile":
                        if (!mc.getArguments().isEmpty()
                                && mc.getArguments().get(0) instanceof MALExpressionModel.NumberListArgument) {
                            final List<Double> vals =
                                ((MALExpressionModel.NumberListArgument) mc.getArguments().get(0)).getValues();
                            percentiles = new int[vals.size()];
                            for (int i = 0; i < vals.size(); i++) {
                                percentiles[i] = vals.get(i).intValue();
                            }
                        }
                        break;
                    case "downsampling":
                        if (!mc.getArguments().isEmpty()) {
                            final MALExpressionModel.Argument dsArg = mc.getArguments().get(0);
                            if (dsArg instanceof MALExpressionModel.EnumRefArgument) {
                                final String val =
                                    ((MALExpressionModel.EnumRefArgument) dsArg).getEnumValue();
                                downsampling = DownsamplingType.valueOf(val);
                            } else if (dsArg instanceof MALExpressionModel.ExprArgument) {
                                final MALExpressionModel.Expr dsExpr =
                                    ((MALExpressionModel.ExprArgument) dsArg).getExpr();
                                if (dsExpr instanceof MALExpressionModel.MetricExpr) {
                                    final String val =
                                        ((MALExpressionModel.MetricExpr) dsExpr).getMetricName();
                                    downsampling = DownsamplingType.valueOf(val);
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        // Validate decorate() usage: must be after service(), not after
        // instance()/endpoint()/etc., and not with histogram metrics
        boolean hasDecorate = false;
        for (final List<MALExpressionModel.MethodCall> chain : allChains) {
            for (final MALExpressionModel.MethodCall mc : chain) {
                if ("decorate".equals(mc.getName())) {
                    hasDecorate = true;
                    break;
                }
            }
            if (hasDecorate) {
                break;
            }
        }
        if (hasDecorate) {
            if (scopeType != null && scopeType != ScopeType.SERVICE) {
                throw new IllegalStateException(
                    "decorate() should be invoked after service()");
            }
            if (isHistogram) {
                throw new IllegalStateException(
                    "decorate() not supported for histogram metrics");
            }
        }

        return new ExpressionMetadata(
            new ArrayList<>(sampleNames),
            scopeType,
            scopeLabels,
            aggregationLabels,
            downsampling,
            isHistogram,
            percentiles
        );
    }

    private static void addStringListLabels(final MALExpressionModel.MethodCall mc,
                                             final Set<String> target) {
        if (!mc.getArguments().isEmpty()
                && mc.getArguments().get(0) instanceof MALExpressionModel.StringListArgument) {
            target.addAll(
                ((MALExpressionModel.StringListArgument) mc.getArguments().get(0)).getValues());
        }
    }

    private static void addAllStringListLabels(final MALExpressionModel.MethodCall mc,
                                                final Set<String> target) {
        for (final MALExpressionModel.Argument arg : mc.getArguments()) {
            if (arg instanceof MALExpressionModel.StringListArgument) {
                target.addAll(((MALExpressionModel.StringListArgument) arg).getValues());
            }
        }
    }

    private static void addStringArgLabels(final MALExpressionModel.MethodCall mc,
                                            final Set<String> target) {
        for (final MALExpressionModel.Argument arg : mc.getArguments()) {
            if (arg instanceof MALExpressionModel.StringArgument) {
                target.add(((MALExpressionModel.StringArgument) arg).getValue());
            }
        }
    }

    private static void collectMethodChains(final MALExpressionModel.Expr expr,
                                             final List<List<MALExpressionModel.MethodCall>> chains) {
        if (expr instanceof MALExpressionModel.MetricExpr) {
            chains.add(((MALExpressionModel.MetricExpr) expr).getMethodChain());
        } else if (expr instanceof MALExpressionModel.BinaryExpr) {
            collectMethodChains(((MALExpressionModel.BinaryExpr) expr).getLeft(), chains);
            collectMethodChains(((MALExpressionModel.BinaryExpr) expr).getRight(), chains);
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            collectMethodChains(((MALExpressionModel.UnaryNegExpr) expr).getOperand(), chains);
        } else if (expr instanceof MALExpressionModel.ParenChainExpr) {
            collectMethodChains(((MALExpressionModel.ParenChainExpr) expr).getInner(), chains);
            chains.add(((MALExpressionModel.ParenChainExpr) expr).getMethodChain());
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            for (final MALExpressionModel.Argument arg :
                    ((MALExpressionModel.FunctionCallExpr) expr).getArguments()) {
                if (arg instanceof MALExpressionModel.ExprArgument) {
                    collectMethodChains(((MALExpressionModel.ExprArgument) arg).getExpr(), chains);
                }
            }
            chains.add(((MALExpressionModel.FunctionCallExpr) expr).getMethodChain());
        }
    }

    private String generateMetadataMethod(final ExpressionMetadata metadata) {
        final StringBuilder sb = new StringBuilder();
        final String mdClass = "org.apache.skywalking.oap.meter.analyzer.v2.dsl.ExpressionMetadata";
        final String scopeTypeClass = "org.apache.skywalking.oap.server.core.analysis.meter.ScopeType";
        final String dsTypeClass = "org.apache.skywalking.oap.meter.analyzer.v2.dsl.DownsamplingType";

        sb.append("public ").append(mdClass).append(" metadata() {\n");

        // samples list
        sb.append("  java.util.List _samples = new java.util.ArrayList();\n");
        for (final String sample : metadata.getSamples()) {
            sb.append("  _samples.add(\"").append(MALCodegenHelper.escapeJava(sample)).append("\");\n");
        }

        // scope labels set
        sb.append("  java.util.Set _scopeLabels = new java.util.LinkedHashSet();\n");
        for (final String label : metadata.getScopeLabels()) {
            sb.append("  _scopeLabels.add(\"").append(MALCodegenHelper.escapeJava(label)).append("\");\n");
        }

        // aggregation labels set
        sb.append("  java.util.Set _aggLabels = new java.util.LinkedHashSet();\n");
        for (final String label : metadata.getAggregationLabels()) {
            sb.append("  _aggLabels.add(\"").append(MALCodegenHelper.escapeJava(label)).append("\");\n");
        }

        // percentiles array
        if (metadata.getPercentiles() != null) {
            sb.append("  int[] _pct = new int[]{");
            for (int i = 0; i < metadata.getPercentiles().length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(metadata.getPercentiles()[i]);
            }
            sb.append("};\n");
        } else {
            sb.append("  int[] _pct = null;\n");
        }

        sb.append("  return new ").append(mdClass).append("(\n");
        sb.append("    _samples,\n");
        if (metadata.getScopeType() != null) {
            sb.append("    ").append(scopeTypeClass).append('.').append(metadata.getScopeType().name()).append(",\n");
        } else {
            sb.append("    null,\n");
        }
        sb.append("    _scopeLabels,\n");
        sb.append("    _aggLabels,\n");
        sb.append("    ").append(dsTypeClass).append('.').append(metadata.getDownsampling().name()).append(",\n");
        sb.append("    ").append(metadata.isHistogram()).append(",\n");
        sb.append("    _pct\n");
        sb.append("  );\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Whether the expression is a scalar (number-producing) function like {@code time()}.
     */
    private static boolean isScalarFunction(final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            final String fn = ((MALExpressionModel.FunctionCallExpr) expr).getFunctionName();
            return "time".equals(fn);
        }
        return false;
    }

    /**
     * Generate code for a scalar expression (literal number or scalar function).
     */
    private void generateScalarExpr(final StringBuilder sb,
                                    final MALExpressionModel.Expr expr) {
        if (expr instanceof MALExpressionModel.NumberExpr) {
            sb.append(((MALExpressionModel.NumberExpr) expr).getValue());
        } else if (isScalarFunction(expr)) {
            final String fn = ((MALExpressionModel.FunctionCallExpr) expr).getFunctionName();
            if ("time".equals(fn)) {
                sb.append("(double) java.time.Instant.now().getEpochSecond()");
            }
        }
    }

    /**
     * Generates the Java source body of the run method for debugging/testing.
     */
    public String generateSource(final String expression) {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(expression);
        final MALClosureCodegen cc = new MALClosureCodegen(classPool, this);
        final List<MALClosureCodegen.ClosureInfo> closures = new ArrayList<>();
        cc.collectClosures(ast, closures);
        // Build field names for source generation
        final List<String> fieldNames = new ArrayList<>();
        final java.util.Map<String, Integer> nameCounts = new java.util.HashMap<>();
        for (final MALClosureCodegen.ClosureInfo ci : closures) {
            final String purpose = ci.methodName;
            final int count = nameCounts.getOrDefault(purpose, 0);
            nameCounts.put(purpose, count + 1);
            final String suffix = count == 0 ? purpose : purpose + "_" + (count + 1);
            fieldNames.add("_" + suffix);
        }
        this.closureFieldNames = fieldNames;
        this.closureFieldIndex = 0;
        return generateRunMethod(ast);
    }

    /**
     * Generates the Java source body of the filter test method for debugging/testing.
     */
    public String generateFilterSource(final String filterExpression) {
        final MALExpressionModel.ClosureArgument closure =
            MALScriptParser.parseFilter(filterExpression);

        final List<String> params = closure.getParams();
        final String paramName = params.isEmpty() ? "it" : params.get(0);

        final MALClosureCodegen cc = new MALClosureCodegen(classPool, this);
        final StringBuilder sb = new StringBuilder();
        sb.append("public boolean test(java.util.Map ").append(paramName)
          .append(") {\n");

        final List<MALExpressionModel.ClosureStatement> body = closure.getBody();
        if (body.size() == 1
                && body.get(0) instanceof MALExpressionModel.ClosureExprStatement) {
            final MALExpressionModel.ClosureExpr expr =
                ((MALExpressionModel.ClosureExprStatement) body.get(0)).getExpr();
            if (expr instanceof MALExpressionModel.ClosureCondition) {
                sb.append("  return ");
                cc.generateClosureCondition(
                    sb, (MALExpressionModel.ClosureCondition) expr, paramName);
                sb.append(";\n");
            } else {
                sb.append("  Object _v = ");
                cc.generateClosureExpr(sb, expr, paramName);
                sb.append(";\n");
                sb.append("  return _v != null && !Boolean.FALSE.equals(_v);\n");
            }
        } else {
            for (final MALExpressionModel.ClosureStatement stmt : body) {
                cc.generateClosureStatement(sb, stmt, paramName);
            }
            sb.append("  return false;\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
