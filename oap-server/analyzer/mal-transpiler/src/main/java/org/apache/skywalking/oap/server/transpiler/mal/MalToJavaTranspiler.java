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

package org.apache.skywalking.oap.server.transpiler.mal;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.ElvisOperatorExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.TernaryExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.ast.expr.BooleanExpression;
import org.codehaus.groovy.ast.expr.NotExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;

/**
 * Transpiles Groovy MAL expressions to Java source code at build time.
 * Parses expression strings into Groovy AST (via CompilationUnit at CONVERSION phase),
 * walks AST nodes, and produces equivalent Java classes implementing MalExpression or MalFilter.
 *
 * <p>Supported AST patterns:
 * <ul>
 *   <li>Variable references: sample family lookups, DownsamplingType constants, KNOWN_TYPES</li>
 *   <li>Method chains: .sum(), .service(), .tagEqual(), .rate(), .histogram(), etc.</li>
 *   <li>Binary arithmetic with operand-swap logic per upstream ExpandoMetaClass
 *       (N-SF: sf.minus(N).negative(), N/SF: sf.newValue(v-&gt;N/v))</li>
 *   <li>tag() closures: TagFunction lambda (assignment, remove, string concat, if/else)</li>
 *   <li>Filter closures: MalFilter class (==, !=, in, truthiness, negation, &amp;&amp;, ||)</li>
 *   <li>forEach() closures: ForEachFunction lambda (var decls, if/else-if, early return)</li>
 *   <li>instance() with PropertiesExtractor closure: Map.of() from map literals</li>
 *   <li>Elvis (?:), safe navigation (?.), ternary (? :)</li>
 *   <li>Batch compilation via javax.tools.JavaCompiler + manifest writing</li>
 * </ul>
 */
@Slf4j
public class MalToJavaTranspiler {

    static final String GENERATED_PACKAGE =
        "org.apache.skywalking.oap.server.core.source.oal.rt.mal";

    private static final Set<String> DOWNSAMPLING_CONSTANTS = Set.of(
        "AVG", "SUM", "LATEST", "SUM_PER_MIN", "MAX", "MIN"
    );

    private static final Set<String> KNOWN_TYPES = Set.of(
        "Layer", "DetectPoint", "K8sRetagType", "ProcessRegistry", "TimeUnit"
    );

    // ---- Batch state tracking ----

    private final Map<String, String> expressionSources = new LinkedHashMap<>();

    private final Map<String, String> filterSources = new LinkedHashMap<>();

    private final Map<String, String> filterLiteralToClass = new LinkedHashMap<>();

    /**
     * Transpile a MAL expression to a Java class source implementing MalExpression.
     *
     * @param className  simple class name (e.g. "MalExpr_meter_jvm_heap")
     * @param expression the Groovy expression string
     * @return generated Java source code
     */
    public String transpileExpression(final String className, final String expression) {
        final ModuleNode ast = parseToAST(expression);
        final Statement body = extractBody(ast);

        final Set<String> sampleNames = new LinkedHashSet<>();
        collectSampleNames(body, sampleNames);

        final String javaBody = visitStatement(body);

        final StringBuilder sb = new StringBuilder();
        sb.append("package ").append(GENERATED_PACKAGE).append(";\n\n");
        sb.append("import java.util.*;\n");
        sb.append("import org.apache.skywalking.oap.meter.analyzer.dsl.*;\n");
        sb.append("import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyFunctions.*;\n");
        sb.append("import org.apache.skywalking.oap.server.core.analysis.Layer;\n");
        sb.append("import org.apache.skywalking.oap.server.core.source.DetectPoint;\n");
        sb.append("import org.apache.skywalking.oap.meter.analyzer.dsl.tagOpt.K8sRetagType;\n");
        sb.append("import org.apache.skywalking.oap.meter.analyzer.dsl.registry.ProcessRegistry;\n\n");

        sb.append("public class ").append(className).append(" implements MalExpression {\n");
        sb.append("    @Override\n");
        sb.append("    public SampleFamily run(Map<String, SampleFamily> samples) {\n");

        if (!sampleNames.isEmpty()) {
            sb.append("        ExpressionParsingContext.get().ifPresent(ctx -> {\n");
            for (String name : sampleNames) {
                sb.append("            ctx.getSamples().add(\"").append(escapeJava(name)).append("\");\n");
            }
            sb.append("        });\n");
        }

        sb.append("        return ").append(javaBody).append(";\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Transpile a MAL filter literal to a Java class source implementing MalFilter.
     * Filter literals are closures like: { tags -> tags.job_name == 'vm-monitoring' }
     *
     * @param className     simple class name (e.g. "MalFilter_0")
     * @param filterLiteral the Groovy closure literal string
     * @return generated Java source code
     */
    public String transpileFilter(final String className, final String filterLiteral) {
        final ModuleNode ast = parseToAST(filterLiteral);
        final Statement body = extractBody(ast);

        // The filter literal is a closure expression at the top level
        final ClosureExpression closure = extractClosure(body);
        final Parameter[] params = closure.getParameters();
        final String tagsVar = (params != null && params.length > 0) ? params[0].getName() : "tags";

        // Get the body expression — may need to unwrap inner block/closure
        final Expression bodyExpr = extractFilterBodyExpr(closure.getCode(), tagsVar);

        // Generate the boolean condition
        final String condition = visitFilterCondition(bodyExpr, tagsVar);

        final StringBuilder sb = new StringBuilder();
        sb.append("package ").append(GENERATED_PACKAGE).append(";\n\n");
        sb.append("import java.util.*;\n");
        sb.append("import org.apache.skywalking.oap.meter.analyzer.dsl.*;\n\n");

        sb.append("public class ").append(className).append(" implements MalFilter {\n");
        sb.append("    @Override\n");
        sb.append("    public boolean test(Map<String, String> tags) {\n");
        sb.append("        return ").append(condition).append(";\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private ClosureExpression extractClosure(final Statement body) {
        final List<Statement> stmts = getStatements(body);
        if (stmts.size() == 1 && stmts.get(0) instanceof ExpressionStatement) {
            final Expression expr = ((ExpressionStatement) stmts.get(0)).getExpression();
            if (expr instanceof ClosureExpression) {
                return (ClosureExpression) expr;
            }
        }
        throw new IllegalStateException(
            "Filter literal must be a single closure expression, got: "
                + (stmts.isEmpty() ? "empty" : stmts.get(0).getClass().getSimpleName()));
    }

    private Expression extractFilterBodyExpr(final Statement code, final String tagsVar) {
        final List<Statement> stmts = getStatements(code);
        if (stmts.isEmpty()) {
            throw new IllegalStateException("Empty filter closure body");
        }

        final Statement last = stmts.get(stmts.size() - 1);
        Expression expr;
        if (last instanceof ExpressionStatement) {
            expr = ((ExpressionStatement) last).getExpression();
        } else if (last instanceof ReturnStatement) {
            expr = ((ReturnStatement) last).getExpression();
        } else if (last instanceof BlockStatement) {
            return extractFilterBodyExpr(last, tagsVar);
        } else {
            throw new UnsupportedOperationException(
                "Unsupported filter body statement: " + last.getClass().getSimpleName());
        }

        if (expr instanceof ClosureExpression) {
            final ClosureExpression inner = (ClosureExpression) expr;
            return extractFilterBodyExpr(inner.getCode(), tagsVar);
        }

        return expr;
    }

    // ---- AST Parsing ----

    ModuleNode parseToAST(final String expression) {
        final CompilerConfiguration cc = new CompilerConfiguration();
        final CompilationUnit cu = new CompilationUnit(cc);
        cu.addSource("Script", expression);
        cu.compile(Phases.CONVERSION);
        final List<ModuleNode> modules = cu.getAST().getModules();
        if (modules.isEmpty()) {
            throw new IllegalStateException("No AST modules produced for: " + expression);
        }
        return modules.get(0);
    }

    Statement extractBody(final ModuleNode module) {
        final BlockStatement block = module.getStatementBlock();
        if (block != null && !block.getStatements().isEmpty()) {
            return block;
        }
        final List<ClassNode> classes = module.getClasses();
        if (!classes.isEmpty()) {
            return module.getStatementBlock();
        }
        throw new IllegalStateException("Empty AST body");
    }

    // ---- Sample Name Collection ----

    private void collectSampleNames(final Statement stmt, final Set<String> names) {
        if (stmt instanceof BlockStatement) {
            for (Statement s : ((BlockStatement) stmt).getStatements()) {
                collectSampleNames(s, names);
            }
        } else if (stmt instanceof ExpressionStatement) {
            collectSampleNamesFromExpr(((ExpressionStatement) stmt).getExpression(), names);
        } else if (stmt instanceof ReturnStatement) {
            collectSampleNamesFromExpr(((ReturnStatement) stmt).getExpression(), names);
        }
    }

    void collectSampleNamesFromExpr(final Expression expr, final Set<String> names) {
        if (expr instanceof VariableExpression) {
            final String name = ((VariableExpression) expr).getName();
            if (!DOWNSAMPLING_CONSTANTS.contains(name)
                && !KNOWN_TYPES.contains(name)
                && !name.equals("this") && !name.equals("time")) {
                names.add(name);
            }
        } else if (expr instanceof BinaryExpression) {
            final BinaryExpression bin = (BinaryExpression) expr;
            collectSampleNamesFromExpr(bin.getLeftExpression(), names);
            collectSampleNamesFromExpr(bin.getRightExpression(), names);
        } else if (expr instanceof PropertyExpression) {
            final PropertyExpression pe = (PropertyExpression) expr;
            collectSampleNamesFromExpr(pe.getObjectExpression(), names);
        } else if (expr instanceof MethodCallExpression) {
            final MethodCallExpression mce = (MethodCallExpression) expr;
            collectSampleNamesFromExpr(mce.getObjectExpression(), names);
            collectSampleNamesFromExpr(mce.getArguments(), names);
        } else if (expr instanceof ArgumentListExpression) {
            for (Expression e : ((ArgumentListExpression) expr).getExpressions()) {
                collectSampleNamesFromExpr(e, names);
            }
        } else if (expr instanceof TupleExpression) {
            for (Expression e : ((TupleExpression) expr).getExpressions()) {
                collectSampleNamesFromExpr(e, names);
            }
        }
    }

    // ---- Statement Visiting ----

    String visitStatement(final Statement stmt) {
        if (stmt instanceof BlockStatement) {
            final List<Statement> stmts = ((BlockStatement) stmt).getStatements();
            if (stmts.size() == 1) {
                return visitStatement(stmts.get(0));
            }
            // Multi-statement: last one is the return value
            return visitStatement(stmts.get(stmts.size() - 1));
        } else if (stmt instanceof ExpressionStatement) {
            return visitExpression(((ExpressionStatement) stmt).getExpression());
        } else if (stmt instanceof ReturnStatement) {
            return visitExpression(((ReturnStatement) stmt).getExpression());
        }
        throw new UnsupportedOperationException(
            "Unsupported statement: " + stmt.getClass().getSimpleName());
    }

    // ---- Expression Visiting ----

    String visitExpression(final Expression expr) {
        if (expr instanceof VariableExpression) {
            return visitVariable((VariableExpression) expr);
        } else if (expr instanceof ConstantExpression) {
            return visitConstant((ConstantExpression) expr);
        } else if (expr instanceof MethodCallExpression) {
            return visitMethodCall((MethodCallExpression) expr);
        } else if (expr instanceof PropertyExpression) {
            return visitProperty((PropertyExpression) expr);
        } else if (expr instanceof ListExpression) {
            return visitList((ListExpression) expr);
        } else if (expr instanceof BinaryExpression) {
            return visitBinary((BinaryExpression) expr);
        } else if (expr instanceof ClosureExpression) {
            throw new UnsupportedOperationException(
                "Bare ClosureExpression outside method call context: " + expr.getText());
        }
        throw new UnsupportedOperationException(
            "Unsupported expression (not yet implemented): "
                + expr.getClass().getSimpleName() + " = " + expr.getText());
    }

    private String visitVariable(final VariableExpression expr) {
        final String name = expr.getName();
        if (DOWNSAMPLING_CONSTANTS.contains(name)) {
            return "DownsamplingType." + name;
        }
        if (KNOWN_TYPES.contains(name)) {
            return name;
        }
        if (name.equals("this")) {
            return "this";
        }
        // Sample family lookup
        return "samples.getOrDefault(\"" + escapeJava(name) + "\", SampleFamily.EMPTY)";
    }

    private String visitConstant(final ConstantExpression expr) {
        final Object value = expr.getValue();
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + escapeJava((String) value) + "\"";
        }
        if (value instanceof Integer) {
            return value.toString();
        }
        if (value instanceof Long) {
            return value + "L";
        }
        if (value instanceof Double) {
            return value.toString();
        }
        if (value instanceof Float) {
            return value + "f";
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        return value.toString();
    }

    // ---- MethodCall, Property, List ----

    private String visitMethodCall(final MethodCallExpression expr) {
        final String methodName = expr.getMethodAsString();
        final Expression objExpr = expr.getObjectExpression();
        final ArgumentListExpression args = toArgList(expr.getArguments());

        // tag(closure) -> TagFunction lambda
        if ("tag".equals(methodName) && args.getExpressions().size() == 1
            && args.getExpression(0) instanceof ClosureExpression) {
            final String obj = visitExpression(objExpr);
            final String lambda = visitTagClosure((ClosureExpression) args.getExpression(0));
            return obj + ".tag((TagFunction) " + lambda + ")";
        }

        // forEach(list, closure) -> ForEachFunction lambda
        if ("forEach".equals(methodName) && args.getExpressions().size() == 2
            && args.getExpression(1) instanceof ClosureExpression) {
            final String obj = visitExpression(objExpr);
            final String list = visitExpression(args.getExpression(0));
            final String lambda = visitForEachClosure((ClosureExpression) args.getExpression(1));
            return obj + ".forEach(" + list + ", (ForEachFunction) " + lambda + ")";
        }

        // instance(..., closure) -> last arg is PropertiesExtractor lambda
        if ("instance".equals(methodName) && !args.getExpressions().isEmpty()) {
            final Expression lastArg = args.getExpression(args.getExpressions().size() - 1);
            if (lastArg instanceof ClosureExpression) {
                final String obj = visitExpression(objExpr);
                final List<String> argStrs = new ArrayList<>();
                for (int i = 0; i < args.getExpressions().size() - 1; i++) {
                    argStrs.add(visitExpression(args.getExpression(i)));
                }
                final String lambda = visitPropertiesExtractorClosure((ClosureExpression) lastArg);
                argStrs.add("(PropertiesExtractor) " + lambda);
                return obj + ".instance(" + String.join(", ", argStrs) + ")";
            }
        }

        final String obj = visitExpression(objExpr);

        // Static method calls: ClassExpression.method(...)
        if (objExpr instanceof ClassExpression) {
            final String typeName = objExpr.getType().getNameWithoutPackage();
            final List<String> argStrs = visitArgList(args);
            return typeName + "." + methodName + "(" + String.join(", ", argStrs) + ")";
        }

        // Regular instance method call: obj.method(args)
        final List<String> argStrs = visitArgList(args);
        return obj + "." + methodName + "(" + String.join(", ", argStrs) + ")";
    }

    private String visitProperty(final PropertyExpression expr) {
        final Expression obj = expr.getObjectExpression();
        final String prop = expr.getPropertyAsString();

        if (obj instanceof ClassExpression) {
            return obj.getType().getNameWithoutPackage() + "." + prop;
        }
        if (obj instanceof VariableExpression) {
            final String varName = ((VariableExpression) obj).getName();
            if (KNOWN_TYPES.contains(varName)) {
                return varName + "." + prop;
            }
        }

        return visitExpression(obj) + "." + prop;
    }

    private String visitList(final ListExpression expr) {
        final List<String> elements = new ArrayList<>();
        for (Expression e : expr.getExpressions()) {
            elements.add(visitExpression(e));
        }
        return "List.of(" + String.join(", ", elements) + ")";
    }

    // ---- Binary Arithmetic ----

    private String visitBinary(final BinaryExpression expr) {
        final int opType = expr.getOperation().getType();

        if (isArithmetic(opType)) {
            return visitArithmetic(expr.getLeftExpression(), expr.getRightExpression(), opType);
        }

        throw new UnsupportedOperationException(
            "Unsupported binary operator (not yet implemented): "
                + expr.getOperation().getText() + " in " + expr.getText());
    }

    /**
     * Arithmetic with operand-swap logic per upstream ExpandoMetaClass:
     * <pre>
     *   SF + SF  -> left.plus(right)
     *   SF - SF  -> left.minus(right)
     *   SF * SF  -> left.multiply(right)
     *   SF / SF  -> left.div(right)
     *   SF op N  -> sf.op(N)
     *   N + SF   -> sf.plus(N)          (swap)
     *   N - SF   -> sf.minus(N).negative()
     *   N * SF   -> sf.multiply(N)      (swap)
     *   N / SF   -> sf.newValue(v -> N / v)
     *   N op N   -> plain arithmetic
     * </pre>
     */
    private String visitArithmetic(final Expression left, final Expression right, final int opType) {
        final boolean leftNum = isNumberLiteral(left);
        final boolean rightNum = isNumberLiteral(right);
        final String leftStr = visitExpression(left);
        final String rightStr = visitExpression(right);

        if (leftNum && rightNum) {
            return "(" + leftStr + " " + opSymbol(opType) + " " + rightStr + ")";
        }

        if (!leftNum && rightNum) {
            return leftStr + "." + opMethod(opType) + "(" + rightStr + ")";
        }

        if (leftNum && !rightNum) {
            switch (opType) {
                case Types.PLUS:
                    return rightStr + ".plus(" + leftStr + ")";
                case Types.MINUS:
                    return rightStr + ".minus(" + leftStr + ").negative()";
                case Types.MULTIPLY:
                    return rightStr + ".multiply(" + leftStr + ")";
                case Types.DIVIDE:
                    return rightStr + ".newValue(v -> " + leftStr + " / v)";
                default:
                    break;
            }
        }

        // SF op SF
        return leftStr + "." + opMethod(opType) + "(" + rightStr + ")";
    }

    private boolean isNumberLiteral(final Expression expr) {
        if (expr instanceof ConstantExpression) {
            return ((ConstantExpression) expr).getValue() instanceof Number;
        }
        return false;
    }

    private boolean isArithmetic(final int opType) {
        return opType == Types.PLUS || opType == Types.MINUS
            || opType == Types.MULTIPLY || opType == Types.DIVIDE;
    }

    private String opMethod(final int opType) {
        switch (opType) {
            case Types.PLUS: return "plus";
            case Types.MINUS: return "minus";
            case Types.MULTIPLY: return "multiply";
            case Types.DIVIDE: return "div";
            default: return "???";
        }
    }

    private String opSymbol(final int opType) {
        switch (opType) {
            case Types.PLUS: return "+";
            case Types.MINUS: return "-";
            case Types.MULTIPLY: return "*";
            case Types.DIVIDE: return "/";
            default: return "?";
        }
    }

    // ---- tag() Closure ----

    private String visitTagClosure(final ClosureExpression closure) {
        final Parameter[] params = closure.getParameters();
        final String tagsVar = (params != null && params.length > 0) ? params[0].getName() : "tags";
        final List<Statement> stmts = getStatements(closure.getCode());

        final StringBuilder sb = new StringBuilder();
        sb.append("(").append(tagsVar).append(" -> {\n");
        for (Statement s : stmts) {
            sb.append("            ").append(visitTagStatement(s, tagsVar)).append("\n");
        }
        sb.append("            return ").append(tagsVar).append(";\n");
        sb.append("        })");
        return sb.toString();
    }

    private String visitTagStatement(final Statement stmt, final String tagsVar) {
        if (stmt instanceof ExpressionStatement) {
            return visitTagExpr(((ExpressionStatement) stmt).getExpression(), tagsVar) + ";";
        }
        if (stmt instanceof ReturnStatement) {
            return "return " + tagsVar + ";";
        }
        if (stmt instanceof IfStatement) {
            return visitTagIf((IfStatement) stmt, tagsVar);
        }
        throw new UnsupportedOperationException(
            "Unsupported tag closure statement: " + stmt.getClass().getSimpleName());
    }

    // ---- If/Else + Compound Conditions in tag() ----

    private String visitTagIf(final IfStatement ifStmt, final String tagsVar) {
        final String condition = visitTagCondition(ifStmt.getBooleanExpression().getExpression(), tagsVar);
        final List<Statement> ifBody = getStatements(ifStmt.getIfBlock());
        final Statement elseBlock = ifStmt.getElseBlock();

        final StringBuilder sb = new StringBuilder();
        sb.append("if (").append(condition).append(") {\n");
        for (Statement s : ifBody) {
            sb.append("                ").append(visitTagStatement(s, tagsVar)).append("\n");
        }
        sb.append("            }");

        if (elseBlock != null && !(elseBlock instanceof EmptyStatement)) {
            sb.append(" else {\n");
            final List<Statement> elseBody = getStatements(elseBlock);
            for (Statement s : elseBody) {
                sb.append("                ").append(visitTagStatement(s, tagsVar)).append("\n");
            }
            sb.append("            }");
        }

        return sb.toString();
    }

    private String visitTagCondition(final Expression expr, final String tagsVar) {
        if (expr instanceof BinaryExpression) {
            final BinaryExpression bin = (BinaryExpression) expr;
            final int opType = bin.getOperation().getType();

            if (opType == Types.COMPARE_EQUAL) {
                return visitTagEquals(bin.getLeftExpression(), bin.getRightExpression(), tagsVar, false);
            }
            if (opType == Types.COMPARE_NOT_EQUAL) {
                return visitTagEquals(bin.getLeftExpression(), bin.getRightExpression(), tagsVar, true);
            }
            if (opType == Types.LOGICAL_OR) {
                return visitTagCondition(bin.getLeftExpression(), tagsVar)
                    + " || " + visitTagCondition(bin.getRightExpression(), tagsVar);
            }
            if (opType == Types.LOGICAL_AND) {
                return visitTagCondition(bin.getLeftExpression(), tagsVar)
                    + " && " + visitTagCondition(bin.getRightExpression(), tagsVar);
            }
        }
        if (expr instanceof BooleanExpression) {
            return visitTagCondition(((BooleanExpression) expr).getExpression(), tagsVar);
        }
        return visitTagValue(expr, tagsVar);
    }

    private String visitTagEquals(final Expression left, final Expression right,
                                  final String tagsVar, final boolean negate) {
        if (isNullConstant(right)) {
            final String leftStr = visitTagValue(left, tagsVar);
            return negate ? leftStr + " != null" : leftStr + " == null";
        }
        if (isNullConstant(left)) {
            final String rightStr = visitTagValue(right, tagsVar);
            return negate ? rightStr + " != null" : rightStr + " == null";
        }

        final String leftStr = visitTagValue(left, tagsVar);
        final String rightStr = visitTagValue(right, tagsVar);

        if (right instanceof ConstantExpression && ((ConstantExpression) right).getValue() instanceof String) {
            final String result = rightStr + ".equals(" + leftStr + ")";
            return negate ? "!" + result : result;
        }
        if (left instanceof ConstantExpression && ((ConstantExpression) left).getValue() instanceof String) {
            final String result = leftStr + ".equals(" + rightStr + ")";
            return negate ? "!" + result : result;
        }
        final String result = "Objects.equals(" + leftStr + ", " + rightStr + ")";
        return negate ? "!" + result : result;
    }

    // ---- Filter Conditions ----

    private String visitFilterCondition(final Expression expr, final String tagsVar) {
        if (expr instanceof NotExpression) {
            final Expression inner = ((NotExpression) expr).getExpression();
            final String val = visitTagValue(inner, tagsVar);
            return "(" + val + " == null || " + val + ".isEmpty())";
        }

        if (expr instanceof BinaryExpression) {
            final BinaryExpression bin = (BinaryExpression) expr;
            final int opType = bin.getOperation().getType();

            if (opType == Types.COMPARE_EQUAL) {
                return visitTagEquals(bin.getLeftExpression(), bin.getRightExpression(), tagsVar, false);
            }
            if (opType == Types.COMPARE_NOT_EQUAL) {
                return visitTagEquals(bin.getLeftExpression(), bin.getRightExpression(), tagsVar, true);
            }
            if (opType == Types.LOGICAL_OR) {
                return visitFilterCondition(bin.getLeftExpression(), tagsVar)
                    + " || " + visitFilterCondition(bin.getRightExpression(), tagsVar);
            }
            if (opType == Types.LOGICAL_AND) {
                return visitFilterCondition(bin.getLeftExpression(), tagsVar)
                    + " && " + visitFilterCondition(bin.getRightExpression(), tagsVar);
            }
            if (opType == Types.KEYWORD_IN) {
                final String val = visitTagValue(bin.getLeftExpression(), tagsVar);
                final String list = visitTagValue(bin.getRightExpression(), tagsVar);
                return list + ".contains(" + val + ")";
            }
        }

        if (expr instanceof BooleanExpression) {
            return visitFilterCondition(((BooleanExpression) expr).getExpression(), tagsVar);
        }

        final String val = visitTagValue(expr, tagsVar);
        return "(" + val + " != null && !" + val + ".isEmpty())";
    }

    private String visitTagExpr(final Expression expr, final String tagsVar) {
        if (expr instanceof BinaryExpression) {
            final BinaryExpression bin = (BinaryExpression) expr;
            if (bin.getOperation().getType() == Types.ASSIGN) {
                return visitTagAssignment(bin.getLeftExpression(), bin.getRightExpression(), tagsVar);
            }
        }
        if (expr instanceof MethodCallExpression) {
            final MethodCallExpression mce = (MethodCallExpression) expr;
            if ("remove".equals(mce.getMethodAsString()) && isTagsVar(mce.getObjectExpression(), tagsVar)) {
                final ArgumentListExpression args = toArgList(mce.getArguments());
                return tagsVar + ".remove(" + visitTagValue(args.getExpression(0), tagsVar) + ")";
            }
        }
        return visitTagValue(expr, tagsVar);
    }

    private String visitTagAssignment(final Expression left, final Expression right, final String tagsVar) {
        final String val = visitTagValue(right, tagsVar);

        if (left instanceof PropertyExpression) {
            final PropertyExpression prop = (PropertyExpression) left;
            if (isTagsVar(prop.getObjectExpression(), tagsVar)) {
                return tagsVar + ".put(\"" + escapeJava(prop.getPropertyAsString()) + "\", " + val + ")";
            }
        }
        if (left instanceof BinaryExpression) {
            final BinaryExpression sub = (BinaryExpression) left;
            if (sub.getOperation().getType() == Types.LEFT_SQUARE_BRACKET
                && isTagsVar(sub.getLeftExpression(), tagsVar)) {
                final String key = visitTagValue(sub.getRightExpression(), tagsVar);
                return tagsVar + ".put(" + key + ", " + val + ")";
            }
        }
        throw new UnsupportedOperationException(
            "Unsupported tag assignment target: " + left.getClass().getSimpleName() + " = " + left.getText());
    }

    String visitTagValue(final Expression expr, final String tagsVar) {
        if (expr instanceof PropertyExpression) {
            final PropertyExpression prop = (PropertyExpression) expr;
            if (isTagsVar(prop.getObjectExpression(), tagsVar)) {
                return tagsVar + ".get(\"" + escapeJava(prop.getPropertyAsString()) + "\")";
            }
            return visitProperty(prop);
        }
        if (expr instanceof BinaryExpression) {
            final BinaryExpression bin = (BinaryExpression) expr;
            if (bin.getOperation().getType() == Types.LEFT_SQUARE_BRACKET
                && isTagsVar(bin.getLeftExpression(), tagsVar)) {
                return tagsVar + ".get(" + visitTagValue(bin.getRightExpression(), tagsVar) + ")";
            }
            if (bin.getOperation().getType() == Types.PLUS) {
                return visitTagValue(bin.getLeftExpression(), tagsVar)
                    + " + " + visitTagValue(bin.getRightExpression(), tagsVar);
            }
        }
        // Elvis operator — must check BEFORE TernaryExpression since it extends it
        if (expr instanceof ElvisOperatorExpression) {
            final ElvisOperatorExpression elvis = (ElvisOperatorExpression) expr;
            final String val = visitTagValue(elvis.getTrueExpression(), tagsVar);
            final String defaultVal = visitTagValue(elvis.getFalseExpression(), tagsVar);
            return "(" + val + " != null ? " + val + " : " + defaultVal + ")";
        }
        if (expr instanceof TernaryExpression) {
            final TernaryExpression tern = (TernaryExpression) expr;
            final String cond = visitFilterCondition(tern.getBooleanExpression().getExpression(), tagsVar);
            final String trueVal = visitTagValue(tern.getTrueExpression(), tagsVar);
            final String falseVal = visitTagValue(tern.getFalseExpression(), tagsVar);
            return "(" + cond + " ? " + trueVal + " : " + falseVal + ")";
        }
        if (expr instanceof MethodCallExpression) {
            final MethodCallExpression mce = (MethodCallExpression) expr;
            final String obj = visitTagValue(mce.getObjectExpression(), tagsVar);
            final ArgumentListExpression args = toArgList(mce.getArguments());
            final List<String> argStrs = new ArrayList<>();
            for (Expression a : args.getExpressions()) {
                argStrs.add(visitTagValue(a, tagsVar));
            }
            final String call = obj + "." + mce.getMethodAsString() + "(" + String.join(", ", argStrs) + ")";
            if (mce.isSafe()) {
                return "(" + obj + " != null ? " + call + " : null)";
            }
            return call;
        }
        if (expr instanceof VariableExpression) {
            final String name = ((VariableExpression) expr).getName();
            if (name.equals(tagsVar)) {
                return tagsVar;
            }
            return name;
        }
        if (expr instanceof ConstantExpression) {
            return visitConstant((ConstantExpression) expr);
        }
        if (expr instanceof ListExpression) {
            return visitList((ListExpression) expr);
        }
        if (expr instanceof MapExpression) {
            final MapExpression map = (MapExpression) expr;
            final List<String> entries = new ArrayList<>();
            for (MapEntryExpression entry : map.getMapEntryExpressions()) {
                entries.add(visitTagValue(entry.getKeyExpression(), tagsVar));
                entries.add(visitTagValue(entry.getValueExpression(), tagsVar));
            }
            return "Map.of(" + String.join(", ", entries) + ")";
        }
        return visitExpression(expr);
    }

    private boolean isTagsVar(final Expression expr, final String tagsVar) {
        return expr instanceof VariableExpression
            && ((VariableExpression) expr).getName().equals(tagsVar);
    }

    private List<Statement> getStatements(final Statement stmt) {
        if (stmt instanceof BlockStatement) {
            return ((BlockStatement) stmt).getStatements();
        }
        return List.of(stmt);
    }

    // ---- forEach() Closure ----

    private String visitForEachClosure(final ClosureExpression closure) {
        final Parameter[] params = closure.getParameters();
        final String prefixVar = (params != null && params.length > 0) ? params[0].getName() : "prefix";
        final String tagsVar = (params != null && params.length > 1) ? params[1].getName() : "tags";

        final List<Statement> stmts = getStatements(closure.getCode());

        final StringBuilder sb = new StringBuilder();
        sb.append("(").append(prefixVar).append(", ").append(tagsVar).append(") -> {\n");
        for (Statement s : stmts) {
            sb.append("            ").append(visitForEachStatement(s, tagsVar)).append("\n");
        }
        sb.append("        }");
        return sb.toString();
    }

    private String visitForEachStatement(final Statement stmt, final String tagsVar) {
        if (stmt instanceof ExpressionStatement) {
            return visitForEachExpr(((ExpressionStatement) stmt).getExpression(), tagsVar) + ";";
        }
        if (stmt instanceof ReturnStatement) {
            return "return;";
        }
        if (stmt instanceof IfStatement) {
            return visitForEachIf((IfStatement) stmt, tagsVar);
        }
        throw new UnsupportedOperationException(
            "Unsupported forEach closure statement: " + stmt.getClass().getSimpleName());
    }

    private String visitForEachExpr(final Expression expr, final String tagsVar) {
        if (expr instanceof DeclarationExpression) {
            final DeclarationExpression decl = (DeclarationExpression) expr;
            final String typeName = decl.getVariableExpression().getType().getNameWithoutPackage();
            final String varName = decl.getVariableExpression().getName();
            final String init = visitTagValue(decl.getRightExpression(), tagsVar);
            return typeName + " " + varName + " = " + init;
        }
        if (expr instanceof BinaryExpression) {
            final BinaryExpression bin = (BinaryExpression) expr;
            if (bin.getOperation().getType() == Types.ASSIGN) {
                final Expression left = bin.getLeftExpression();
                if (isTagWrite(left, tagsVar)) {
                    return visitTagAssignment(left, bin.getRightExpression(), tagsVar);
                }
                if (left instanceof VariableExpression) {
                    return ((VariableExpression) left).getName()
                        + " = " + visitTagValue(bin.getRightExpression(), tagsVar);
                }
            }
        }
        return visitTagExpr(expr, tagsVar);
    }

    private String visitForEachIf(final IfStatement ifStmt, final String tagsVar) {
        final String condition = visitTagCondition(ifStmt.getBooleanExpression().getExpression(), tagsVar);
        final List<Statement> ifBody = getStatements(ifStmt.getIfBlock());
        final Statement elseBlock = ifStmt.getElseBlock();

        final StringBuilder sb = new StringBuilder();
        sb.append("if (").append(condition).append(") {\n");
        for (Statement s : ifBody) {
            sb.append("                ").append(visitForEachStatement(s, tagsVar)).append("\n");
        }
        sb.append("            }");

        if (elseBlock instanceof IfStatement) {
            sb.append(" else ").append(visitForEachIf((IfStatement) elseBlock, tagsVar));
        } else if (elseBlock != null && !(elseBlock instanceof EmptyStatement)) {
            sb.append(" else {\n");
            for (Statement s : getStatements(elseBlock)) {
                sb.append("                ").append(visitForEachStatement(s, tagsVar)).append("\n");
            }
            sb.append("            }");
        }

        return sb.toString();
    }

    private boolean isTagWrite(final Expression left, final String tagsVar) {
        if (left instanceof PropertyExpression) {
            return isTagsVar(((PropertyExpression) left).getObjectExpression(), tagsVar);
        }
        if (left instanceof BinaryExpression) {
            final BinaryExpression sub = (BinaryExpression) left;
            return sub.getOperation().getType() == Types.LEFT_SQUARE_BRACKET
                && isTagsVar(sub.getLeftExpression(), tagsVar);
        }
        return false;
    }

    private boolean isNullConstant(final Expression expr) {
        return expr instanceof ConstantExpression && ((ConstantExpression) expr).getValue() == null;
    }

    // ---- PropertiesExtractor Closure ----

    private String visitPropertiesExtractorClosure(final ClosureExpression closure) {
        final Parameter[] params = closure.getParameters();
        final String tagsVar = (params != null && params.length > 0) ? params[0].getName() : "tags";
        final List<Statement> stmts = getStatements(closure.getCode());

        final Statement last = stmts.get(stmts.size() - 1);
        Expression bodyExpr;
        if (last instanceof ExpressionStatement) {
            bodyExpr = ((ExpressionStatement) last).getExpression();
        } else if (last instanceof ReturnStatement) {
            bodyExpr = ((ReturnStatement) last).getExpression();
        } else {
            throw new UnsupportedOperationException(
                "Unsupported PropertiesExtractor closure body: " + last.getClass().getSimpleName());
        }
        return "(" + tagsVar + " -> " + visitTagValue(bodyExpr, tagsVar) + ")";
    }

    // ---- Batch Registration, Compilation, and Manifest Writing ----

    public void registerExpression(final String className, final String source) {
        expressionSources.put(className, source);
    }

    public void registerFilter(final String className, final String filterLiteral, final String source) {
        filterSources.put(className, source);
        filterLiteralToClass.put(filterLiteral, GENERATED_PACKAGE + "." + className);
    }

    /**
     * Compile all registered sources using javax.tools.JavaCompiler.
     *
     * @param sourceDir  directory to write .java source files (package dirs created automatically)
     * @param outputDir  directory for compiled .class files
     * @param classpath  classpath for javac (semicolon/colon-separated JAR paths)
     * @throws IOException if file I/O fails
     */
    public void compileAll(final File sourceDir, final File outputDir,
                           final String classpath) throws IOException {
        final Map<String, String> allSources = new LinkedHashMap<>();
        allSources.putAll(expressionSources);
        allSources.putAll(filterSources);

        if (allSources.isEmpty()) {
            log.info("No MAL sources to compile.");
            return;
        }

        final String packageDir = GENERATED_PACKAGE.replace('.', File.separatorChar);
        final File srcPkgDir = new File(sourceDir, packageDir);
        if (!srcPkgDir.exists() && !srcPkgDir.mkdirs()) {
            throw new IOException("Failed to create source dir: " + srcPkgDir);
        }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create output dir: " + outputDir);
        }

        final List<File> javaFiles = new ArrayList<>();
        for (Map.Entry<String, String> entry : allSources.entrySet()) {
            final File javaFile = new File(srcPkgDir, entry.getKey() + ".java");
            Files.writeString(javaFile.toPath(), entry.getValue());
            javaFiles.add(javaFile);
        }

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No Java compiler available — requires JDK");
        }

        final StringWriter errorWriter = new StringWriter();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            final Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromFiles(javaFiles);

            final List<String> options = Arrays.asList(
                "-d", outputDir.getAbsolutePath(),
                "-classpath", classpath
            );

            final JavaCompiler.CompilationTask task = compiler.getTask(
                errorWriter, fileManager, null, options, null, compilationUnits);

            final boolean success = task.call();
            if (!success) {
                throw new RuntimeException(
                    "Java compilation failed for " + javaFiles.size() + " MAL sources:\n"
                        + errorWriter);
            }
        }

        log.info("Compiled {} MAL sources to {}", allSources.size(), outputDir);
    }

    /**
     * Write mal-expressions.txt manifest: one FQCN per line.
     */
    public void writeExpressionManifest(final File outputDir) throws IOException {
        final File manifestDir = new File(outputDir, "META-INF");
        if (!manifestDir.exists() && !manifestDir.mkdirs()) {
            throw new IOException("Failed to create META-INF dir: " + manifestDir);
        }

        final List<String> lines = expressionSources.keySet().stream()
            .map(name -> GENERATED_PACKAGE + "." + name)
            .collect(Collectors.toList());
        Files.write(new File(manifestDir, "mal-expressions.txt").toPath(), lines);
        log.info("Wrote mal-expressions.txt with {} entries", lines.size());
    }

    /**
     * Write mal-filter-expressions.properties manifest: literal=FQCN.
     */
    public void writeFilterManifest(final File outputDir) throws IOException {
        final File manifestDir = new File(outputDir, "META-INF");
        if (!manifestDir.exists() && !manifestDir.mkdirs()) {
            throw new IOException("Failed to create META-INF dir: " + manifestDir);
        }

        final List<String> lines = filterLiteralToClass.entrySet().stream()
            .map(e -> escapeProperties(e.getKey()) + "=" + e.getValue())
            .collect(Collectors.toList());
        Files.write(new File(manifestDir, "mal-filter-expressions.properties").toPath(), lines);
        log.info("Wrote mal-filter-expressions.properties with {} entries", lines.size());
    }

    private static String escapeProperties(final String s) {
        return s.replace("\\", "\\\\")
                .replace("=", "\\=")
                .replace(":", "\\:")
                .replace(" ", "\\ ");
    }

    // ---- Argument Utilities ----

    private ArgumentListExpression toArgList(final Expression args) {
        if (args instanceof ArgumentListExpression) {
            return (ArgumentListExpression) args;
        }
        if (args instanceof TupleExpression) {
            final ArgumentListExpression ale = new ArgumentListExpression();
            for (Expression e : ((TupleExpression) args).getExpressions()) {
                ale.addExpression(e);
            }
            return ale;
        }
        final ArgumentListExpression ale = new ArgumentListExpression();
        ale.addExpression(args);
        return ale;
    }

    private List<String> visitArgList(final ArgumentListExpression args) {
        final List<String> result = new ArrayList<>();
        for (Expression arg : args.getExpressions()) {
            result.add(visitExpression(arg));
        }
        return result;
    }

    // ---- Utility ----

    public static String escapeJava(final String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
