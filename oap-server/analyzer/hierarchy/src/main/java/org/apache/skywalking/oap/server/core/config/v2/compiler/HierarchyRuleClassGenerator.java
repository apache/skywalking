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

package org.apache.skywalking.oap.server.core.config.v2.compiler;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.config.v2.compiler.hierarchy.rule.rt.HierarchyRulePackageHolder;
import org.apache.skywalking.oap.server.core.query.type.Service;

/**
 * Generates {@link BiFunction BiFunction&lt;Service, Service, Boolean&gt;} implementation classes
 * from {@link HierarchyRuleModel} AST using Javassist bytecode generation.
 */
@Slf4j
public final class HierarchyRuleClassGenerator {

    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger(0);

    private static final String PACKAGE_PREFIX =
        "org.apache.skywalking.oap.server.core.config.v2.compiler.hierarchy.rule.rt.";

    private static final java.util.Set<String> USED_CLASS_NAMES =
        java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private final ClassPool classPool;
    private File classOutputDir;
    private String classNameHint;

    public HierarchyRuleClassGenerator() {
        this(ClassPool.getDefault());
    }

    public HierarchyRuleClassGenerator(final ClassPool classPool) {
        this.classPool = classPool;
    }

    public void setClassOutputDir(final File dir) {
        this.classOutputDir = dir;
    }

    public void setClassNameHint(final String hint) {
        this.classNameHint = hint;
    }

    private String makeClassName(final String defaultPrefix) {
        if (classNameHint != null) {
            return dedupClassName(PACKAGE_PREFIX + sanitizeName(classNameHint));
        }
        return PACKAGE_PREFIX + defaultPrefix + CLASS_COUNTER.getAndIncrement();
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

    private static String sanitizeName(final String name) {
        final StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            sb.append(i == 0
                ? (Character.isJavaIdentifierStart(c) ? c : '_')
                : (Character.isJavaIdentifierPart(c) ? c : '_'));
        }
        return sb.length() == 0 ? "Generated" : sb.toString();
    }

    private void writeClassFile(final CtClass ctClass) {
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
            log.warn("Failed to write class file {}: {}", file, e.getMessage());
        }
    }

    private void addLocalVariableTable(final javassist.CtMethod method,
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
            lva.addEntry(0, len,
                cp.addUtf8Info("this"),
                cp.addUtf8Info("L" + className.replace('.', '/') + ";"), 0);
            for (int i = 0; i < vars.length; i++) {
                lva.addEntry(0, len,
                    cp.addUtf8Info(vars[i][0]),
                    cp.addUtf8Info(vars[i][1]), i + 1);
            }
            code.getAttributes().add(lva);
        } catch (Exception e) {
            log.warn("Failed to add LocalVariableTable: {}", e.getMessage());
        }
    }

    /**
     * Compiles a hierarchy rule expression into a BiFunction class.
     *
     * @param ruleName   the rule name (e.g., "name", "short-name")
     * @param expression the rule expression string
     * @return a BiFunction that matches two Service objects
     */
    @SuppressWarnings("unchecked")
    public BiFunction<Service, Service, Boolean> compile(
            final String ruleName, final String expression) throws Exception {
        final HierarchyRuleModel model = HierarchyRuleScriptParser.parse(expression);
        final String saved = classNameHint;
        if (classNameHint == null) {
            classNameHint = ruleName;
        }
        final String className;
        try {
            className = makeClassName("HierarchyRule_");
        } finally {
            classNameHint = saved;
        }

        final CtClass ctClass = classPool.makeClass(className);
        ctClass.addInterface(classPool.get("java.util.function.BiFunction"));

        ctClass.addConstructor(CtNewConstructor.defaultConstructor(ctClass));

        final String applyBody = generateApplyMethod(model);

        if (log.isDebugEnabled()) {
            log.debug("Hierarchy compile [{}] AST: {}", ruleName, model);
            log.debug("Hierarchy compile [{}] apply():\n{}", ruleName, applyBody);
        }

        final javassist.CtMethod applyMethod = CtNewMethod.make(applyBody, ctClass);
        ctClass.addMethod(applyMethod);
        final String svcDesc = "Lorg/apache/skywalking/oap/server/core/query/type/Service;";
        addLocalVariableTable(applyMethod, className, new String[][]{
            {"arg0", "Ljava/lang/Object;"},
            {"arg1", "Ljava/lang/Object;"},
            {model.getUpperParam(), svcDesc},
            {model.getLowerParam(), svcDesc}
        });

        writeClassFile(ctClass);
        final Class<?> clazz = ctClass.toClass(HierarchyRulePackageHolder.class);
        ctClass.detach();
        return (BiFunction<Service, Service, Boolean>) clazz.getDeclaredConstructor().newInstance();
    }

    private String generateApplyMethod(final HierarchyRuleModel model) {
        final StringBuilder sb = new StringBuilder();
        sb.append("public Object apply(Object arg0, Object arg1) {\n");
        sb.append("  org.apache.skywalking.oap.server.core.query.type.Service ");
        sb.append(model.getUpperParam()).append(" = (org.apache.skywalking.oap.server.core.query.type.Service) arg0;\n");
        sb.append("  org.apache.skywalking.oap.server.core.query.type.Service ");
        sb.append(model.getLowerParam()).append(" = (org.apache.skywalking.oap.server.core.query.type.Service) arg1;\n");

        generateRuleBody(sb, model.getBody());

        sb.append("}\n");
        return sb.toString();
    }

    private void generateRuleBody(final StringBuilder sb,
                                  final HierarchyRuleModel.RuleBody body) {
        if (body instanceof HierarchyRuleModel.SimpleComparison) {
            final HierarchyRuleModel.SimpleComparison cmp =
                (HierarchyRuleModel.SimpleComparison) body;
            sb.append("  return Boolean.valueOf(");
            generateComparison(sb, cmp.getLeft(), cmp.getOp(), cmp.getRight());
            sb.append(");\n");
        } else if (body instanceof HierarchyRuleModel.BlockBody) {
            final HierarchyRuleModel.BlockBody block = (HierarchyRuleModel.BlockBody) body;
            for (final HierarchyRuleModel.Statement stmt : block.getStatements()) {
                generateStatement(sb, stmt);
            }
        }
    }

    private void generateStatement(final StringBuilder sb,
                                   final HierarchyRuleModel.Statement stmt) {
        if (stmt instanceof HierarchyRuleModel.IfStatement) {
            generateIfStatement(sb, (HierarchyRuleModel.IfStatement) stmt);
        } else if (stmt instanceof HierarchyRuleModel.ReturnStatement) {
            generateReturnStatement(sb, (HierarchyRuleModel.ReturnStatement) stmt);
        }
    }

    private void generateIfStatement(final StringBuilder sb,
                                     final HierarchyRuleModel.IfStatement ifStmt) {
        sb.append("  if (");
        generateCondition(sb, ifStmt.getCondition());
        sb.append(") {\n");
        for (final HierarchyRuleModel.Statement s : ifStmt.getThenBranch()) {
            generateStatement(sb, s);
        }
        sb.append("  }\n");
        if (ifStmt.getElseBranch() != null && !ifStmt.getElseBranch().isEmpty()) {
            sb.append("  else {\n");
            for (final HierarchyRuleModel.Statement s : ifStmt.getElseBranch()) {
                generateStatement(sb, s);
            }
            sb.append("  }\n");
        }
    }

    private void generateReturnStatement(final StringBuilder sb,
                                         final HierarchyRuleModel.ReturnStatement retStmt) {
        final HierarchyRuleModel.Expr expr = retStmt.getValue();
        if (expr instanceof HierarchyRuleModel.SimpleComparison) {
            final HierarchyRuleModel.SimpleComparison cmp =
                (HierarchyRuleModel.SimpleComparison) expr;
            sb.append("  return Boolean.valueOf(");
            generateComparison(sb, cmp.getLeft(), cmp.getOp(), cmp.getRight());
            sb.append(");\n");
        } else if (expr instanceof HierarchyRuleModel.BoolLiteralExpr) {
            sb.append("  return Boolean.valueOf(")
              .append(((HierarchyRuleModel.BoolLiteralExpr) expr).isValue())
              .append(");\n");
        } else {
            sb.append("  return Boolean.valueOf(");
            generateExpr(sb, expr);
            sb.append(" != null);\n");
        }
    }

    private void generateComparison(final StringBuilder sb,
                                    final HierarchyRuleModel.Expr left,
                                    final HierarchyRuleModel.CompareOp op,
                                    final HierarchyRuleModel.Expr right) {
        switch (op) {
            case EQ:
                sb.append("java.util.Objects.equals(");
                generateExpr(sb, left);
                sb.append(", ");
                generateExpr(sb, right);
                sb.append(")");
                break;
            case NEQ:
                sb.append("!java.util.Objects.equals(");
                generateExpr(sb, left);
                sb.append(", ");
                generateExpr(sb, right);
                sb.append(")");
                break;
            case GT:
                generateExpr(sb, left);
                sb.append(" > ");
                generateExpr(sb, right);
                break;
            case LT:
                generateExpr(sb, left);
                sb.append(" < ");
                generateExpr(sb, right);
                break;
            case GTE:
                generateExpr(sb, left);
                sb.append(" >= ");
                generateExpr(sb, right);
                break;
            case LTE:
                generateExpr(sb, left);
                sb.append(" <= ");
                generateExpr(sb, right);
                break;
            default:
                throw new IllegalArgumentException("Unsupported comparison op: " + op);
        }
    }

    private void generateCondition(final StringBuilder sb,
                                   final HierarchyRuleModel.Condition cond) {
        if (cond instanceof HierarchyRuleModel.ComparisonCondition) {
            final HierarchyRuleModel.ComparisonCondition cc =
                (HierarchyRuleModel.ComparisonCondition) cond;
            generateComparison(sb, cc.getLeft(), cc.getOp(), cc.getRight());
        } else if (cond instanceof HierarchyRuleModel.LogicalCondition) {
            final HierarchyRuleModel.LogicalCondition lc =
                (HierarchyRuleModel.LogicalCondition) cond;
            sb.append("(");
            generateCondition(sb, lc.getLeft());
            sb.append(lc.getOp() == HierarchyRuleModel.LogicalOp.AND ? " && " : " || ");
            generateCondition(sb, lc.getRight());
            sb.append(")");
        } else if (cond instanceof HierarchyRuleModel.NotCondition) {
            sb.append("!(");
            generateCondition(sb, ((HierarchyRuleModel.NotCondition) cond).getInner());
            sb.append(")");
        } else if (cond instanceof HierarchyRuleModel.ExprCondition) {
            generateExpr(sb, ((HierarchyRuleModel.ExprCondition) cond).getExpr());
        }
    }

    private void generateExpr(final StringBuilder sb,
                              final HierarchyRuleModel.Expr expr) {
        if (expr instanceof HierarchyRuleModel.MethodChainExpr) {
            generateMethodChainExpr(sb, (HierarchyRuleModel.MethodChainExpr) expr);
        } else if (expr instanceof HierarchyRuleModel.StringLiteralExpr) {
            sb.append('"')
              .append(escapeJava(((HierarchyRuleModel.StringLiteralExpr) expr).getValue()))
              .append('"');
        } else if (expr instanceof HierarchyRuleModel.NumberLiteralExpr) {
            sb.append(((HierarchyRuleModel.NumberLiteralExpr) expr).getValue());
        } else if (expr instanceof HierarchyRuleModel.BoolLiteralExpr) {
            sb.append(((HierarchyRuleModel.BoolLiteralExpr) expr).isValue());
        } else if (expr instanceof HierarchyRuleModel.BinaryExpr) {
            final HierarchyRuleModel.BinaryExpr bin = (HierarchyRuleModel.BinaryExpr) expr;
            generateExpr(sb, bin.getLeft());
            sb.append(bin.getOp() == HierarchyRuleModel.ArithmeticOp.ADD ? " + " : " - ");
            generateExpr(sb, bin.getRight());
        } else if (expr instanceof HierarchyRuleModel.SimpleComparison) {
            final HierarchyRuleModel.SimpleComparison cmp =
                (HierarchyRuleModel.SimpleComparison) expr;
            generateComparison(sb, cmp.getLeft(), cmp.getOp(), cmp.getRight());
        }
    }

    private void generateMethodChainExpr(final StringBuilder sb,
                                         final HierarchyRuleModel.MethodChainExpr expr) {
        sb.append(expr.getTarget());
        for (final HierarchyRuleModel.ChainSegment seg : expr.getSegments()) {
            sb.append('.');
            if (seg instanceof HierarchyRuleModel.FieldAccess) {
                final String fieldName = ((HierarchyRuleModel.FieldAccess) seg).getName();
                sb.append(toGetter(fieldName)).append("()");
            } else if (seg instanceof HierarchyRuleModel.MethodCallSegment) {
                final HierarchyRuleModel.MethodCallSegment mc =
                    (HierarchyRuleModel.MethodCallSegment) seg;
                sb.append(mc.getName()).append('(');
                final List<HierarchyRuleModel.Expr> args = mc.getArguments();
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    generateExpr(sb, args.get(i));
                }
                sb.append(')');
            }
        }
    }

    private static String toGetter(final String fieldName) {
        if ("name".equals(fieldName)) {
            return "getName";
        } else if ("shortName".equals(fieldName)) {
            return "getShortName";
        }
        return "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    private static String escapeJava(final String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Generates the Java source body of the apply method for debugging/testing.
     */
    public String generateSource(final String expression) {
        final HierarchyRuleModel model = HierarchyRuleScriptParser.parse(expression);
        return generateApplyMethod(model);
    }
}
