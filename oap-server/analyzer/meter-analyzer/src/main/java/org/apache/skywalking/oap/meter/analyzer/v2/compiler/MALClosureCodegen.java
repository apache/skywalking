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

import org.apache.skywalking.oap.server.core.dsl.DslGeneratedFileWriter;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.dsl.DslJavaSourceText;
import org.apache.skywalking.oap.server.core.dsl.DslMethods;
import org.apache.skywalking.oap.server.core.dsl.DslNumbers;
import javassist.CtMethod;

/**
 * Generates closure classes for MAL expressions using Javassist bytecode generation.
 *
 * <p>This class handles all closure-related code generation: collecting closures from
 * the AST, compiling each closure into a separate class implementing the appropriate
 * functional interface, and generating closure statement/expression/condition code.
 */
@Slf4j
final class MALClosureCodegen {

    private final ClassPool classPool;
    private final MALBytecodeHelper bytecodeHelper;
    /** The kind of the closure body being generated. */
    private ClosureKind kind;
    /** Numbers the generated temporaries of a closure body. */
    private int temps;
    /** Declarations of the temporaries, placed at the top of the closure's method, after its parameter. */
    private final StringBuilder hoisted = new StringBuilder();
    /** What the closure body being generated has declared. */
    private final ClosureScope scope = new ClosureScope();
    /** def declarations a later assignment gives a value of another kind: held as Object, as Groovy's def is. */
    private final Set<MALExpressionModel.ClosureVarDecl> objectDefs =
        Collections.newSetFromMap(new IdentityHashMap<>());
    /** The types of the closure's locals, so a value is boxed only where an object is due. */
    private final ClosureTypes types = new ClosureTypes(scope);
    /** The closure body's parameter: the labels, or decorate's bean. */
    private String paramName;
    /** A decorate body: its parameter is the {@code MeterEntity}, read and set through its getters and setters. */
    private boolean beanMode;

    /**
     * Thrown when an assignment shows a def local must be held as Object; the closure body is generated again.
     */
    static final class WidenDef extends RuntimeException {
        private static final long serialVersionUID = 1L;

        WidenDef() {
            super(null, null, false, false);
        }
    }

    MALClosureCodegen(final ClassPool classPool, final MALClassGenerator generator) {
        this.classPool = classPool;
        this.bytecodeHelper = generator.getBytecodeHelper();
    }

    /**
     * The method a closure compiles into, as its kind implements it; generated again while an assignment shows a def
     * local must be held as Object.
     */
    String generateBody(final ClosureKind kind, final MALExpressionModel.ClosureArgument closure) {
        final List<String> params = closure.getParams();
        if (params.size() != kind.arity || new HashSet<>(params).size() != params.size()) {
            // its caller passes exactly that many arguments: another count could not be called
            throw new IllegalArgumentException(kind == ClosureKind.RULE_FILTER
                ? "A filter closure takes one parameter, the labels, not " + params
                : "A closure here takes " + kind.arity + " distinct parameter" + (kind.arity == 1 ? "" : "s")
                    + ", not " + params);
        }
        if (kind.samArgument != null && kind.samArgument.equals(params.get(0))) {
            throw new IllegalArgumentException("This closure's parameter is not named " + kind.samArgument
                + ", which the generated code uses for its argument");
        }
        objectDefs.clear();
        while (true) {
            try {
                begin(kind, params);
                final StringBuilder body = new StringBuilder();
                if (kind.returnMode == ClosureKind.ReturnMode.NOTHING) {
                    for (final MALExpressionModel.ClosureStatement stmt : closure.getBody()) {
                        generateClosureStatement(body, stmt);
                    }
                } else {
                    if (kind.returnMode == ClosureKind.ReturnMode.FILTER && closure.getBody().isEmpty()) {
                        throw new IllegalArgumentException(
                            "A filter closure must end with the condition that decides it");
                    }
                    emitTail(body, closure.getBody());
                }
                // the temporaries after the line binding the parameter, so its slot stays first
                return kind.signature(params) + hoisted + body + "}\n";
            } catch (final WidenDef again) {
                // a def local is now held as Object: generate the body again
            }
        }
    }

    /**
     * Starts a closure body: its locals are its own.
     */
    private void begin(final ClosureKind kind, final List<String> params) {
        this.kind = kind;
        paramName = params.get(params.size() - 1);
        beanMode = kind == ClosureKind.DECORATE;
        types.begin(paramName);
        scope.clear();
        scope.declare(paramName, kind.parameterType);
        if (kind == ClosureKind.FOR_EACH) {
            scope.declare(params.get(0), String.class);
        }
        hoisted.setLength(0);
    }

    /**
     * A temporary holding a value an expression reads twice (a ?: primary, a ?. receiver), so it is evaluated once.
     */
    private String temp(final String name) {
        final String temp = MalGeneratedSourceLines.TEMP_PREFIX + name + (temps++);
        hoisted.append("    Object ").append(temp).append(" = null;\n");
        return temp;
    }

    /**
     * @return whether the expression calls a {@code void} method: no value, as Groovy's null
     */
    private boolean isVoid(final MALExpressionModel.ClosureExpr expr) {
        return types.typeOf(expr) == void.class;
    }

    /**
     * A value assigned to a slot of {@code type}, as Groovy assigns it: Groovy truth into a boolean, a primitive into an
     * object slot boxed, a wrapper into a primitive slot unboxed; a value the slot cannot hold is refused rather than
     * narrowed or left to fail verification.
     */
    private void emitValueAs(final StringBuilder sb, final Class<?> type, final MALExpressionModel.ClosureExpr value) {
        final Class<?> valueType = types.typeOf(value);
        if (type == boolean.class) {
            emitTruth(sb, value);
            return;
        }
        final MALExpressionModel.ClosureExpr[] arms = ClosureTypes.arms(value);
        if (type != null && valueType == null && arms != null) {
            // Arms of different types merge boxed: check each against the slot, then convert the merged value.
            requireArmsFit(type, arms);
            if (type.isPrimitive()) {
                emitNumberAs(sb, type, value);
            } else if (type == String.class) {
                // an arm that is no String (443 from getOrDefault) converts as Groovy converts it
                sb.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".asString(($w) (");
                generateClosureExpr(sb, value);
                sb.append("))");
            } else {
                sb.append("((").append(type.getCanonicalName()).append(") ($w) (");
                generateClosureExpr(sb, value);
                sb.append("))");
            }
            return;
        }
        if (type == null || valueType == null) {
            generateClosureExpr(sb, value);
            return;
        }
        if (type.isPrimitive() && (valueType == Object.class || valueType == BigDecimal.class)
            && ClosureTypes.isPrimitiveNumber(type)) {
            // a value computed at run time (Groovy arithmetic) or a quotient into a declared number: converted as
            // Groovy assigns it (a quotient truncates, ns / 1000000 losing no digit; into an int it wraps)
            emitNumberAs(sb, type, value);
            return;
        }
        if (type.isPrimitive()) {
            final Class<?> primitive = valueType.isPrimitive() ? valueType : DslMethods.unboxed(valueType);
            if (primitive != null && type != boolean.class && primitive != boolean.class
                && ClosureTypes.isPrimitiveNumber(type) && !DslMethods.widens(primitive, type)) {
                // a declared number takes the value converted, as Java narrows it (int bucket = 12.75 is 12)
                sb.append("((").append(type.getName()).append(") (");
                generateClosureExpr(sb, value);
                sb.append(valueType.isPrimitive() ? "))" : ")." + primitive.getName() + "Value())");
                return;
            }
            if (primitive == null || !DslMethods.widens(primitive, type)) {
                throw new IllegalArgumentException("A " + valueType.getSimpleName() + " value does not fit a "
                    + type.getSimpleName() + " local; declare the local's type to match");
            }
            sb.append('(');
            generateClosureExpr(sb, value);
            sb.append(')');
            if (!valueType.isPrimitive()) {
                sb.append('.').append(primitive.getName()).append("Value()");
            }
            return;
        }
        final Class<?> carried = valueType.isPrimitive() ? DslMethods.boxed(valueType) : valueType;
        if (type == String.class && carried != String.class && carried != void.class) {
            // Into a String local Groovy converts the value (443 becomes "443", a Set "[env]"), keeping null.
            sb.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".asString(($w) (");
            generateClosureExpr(sb, value);
            sb.append("))");
            return;
        }
        if (carried != void.class && carried != Object.class && !type.isAssignableFrom(carried)) {
            throw new IllegalArgumentException("A " + valueType.getSimpleName() + " value does not fit a "
                + type.getSimpleName() + " local; declare the local's type to match");
        }
        // An Object into another reference local: Groovy casts, and so does the generated code.
        final boolean cast = carried == Object.class && type != Object.class;
        sb.append(cast ? "((" + type.getCanonicalName() + ") ($w) (" : "($w) (");
        generateClosureExpr(sb, value);
        sb.append(cast ? "))" : ")");
    }

    /**
     * A value tested as a condition: a condition or a boolean as it is, any other value by Groovy truth (null, false,
     * an empty text or container and zero are false).
     */
    private void emitTruth(final StringBuilder sb, final MALExpressionModel.ClosureExpr value) {
        if (value instanceof MALExpressionModel.ClosureCondition
            && !(value instanceof MALExpressionModel.ClosureExprCondition)) {
            generateClosureCondition(sb, (MALExpressionModel.ClosureCondition) value);
        } else if (types.typeOf(value) == boolean.class) {
            generateClosureExpr(sb, value);
        } else {
            sb.append(MALCodegenHelper.VALUES_FQCN).append(".isTruthy(($w) (");
            generateClosureExpr(sb, value);
            sb.append("))");
        }
    }

    /**
     * Each leaf of a ternary, nested ternaries included (a ? x : b ? y : 0), checked against the slot.
     */
    private void requireArmsFit(final Class<?> slot, final MALExpressionModel.ClosureExpr[] arms) {
        for (final MALExpressionModel.ClosureExpr arm : arms) {
            final MALExpressionModel.ClosureExpr[] nested = ClosureTypes.arms(arm);
            if (nested != null && types.typeOf(arm) == null) {
                requireArmsFit(slot, nested);
            } else {
                ClosureTypes.requireFits(slot, arm instanceof MALExpressionModel.ClosureNullLiteral ? null
                    : types.typeOf(arm), arm instanceof MALExpressionModel.ClosureNullLiteral);
            }
        }
    }

    /**
     * A closure argument of the expression, and the method it is passed to.
     */
    static final class ClosureInfo {
        final MALExpressionModel.ClosureArgument closure;
        final ClosureKind kind;
        final String methodName;

        ClosureInfo(final MALExpressionModel.ClosureArgument closure, final String methodName) {
            this.closure = closure;
            this.kind = ClosureKind.of(methodName);
            this.methodName = methodName;
        }
    }

    void collectClosures(final MALExpressionModel.Expr expr,
                         final List<ClosureInfo> closures) {
        if (expr instanceof MALExpressionModel.MetricExpr) {
            collectClosuresFromChain(
                ((MALExpressionModel.MetricExpr) expr).getMethodChain(), closures);
        } else if (expr instanceof MALExpressionModel.BinaryExpr) {
            collectClosures(((MALExpressionModel.BinaryExpr) expr).getLeft(), closures);
            collectClosures(((MALExpressionModel.BinaryExpr) expr).getRight(), closures);
        } else if (expr instanceof MALExpressionModel.UnaryNegExpr) {
            collectClosures(
                ((MALExpressionModel.UnaryNegExpr) expr).getOperand(), closures);
        } else if (expr instanceof MALExpressionModel.ParenChainExpr) {
            collectClosures(
                ((MALExpressionModel.ParenChainExpr) expr).getInner(), closures);
            collectClosuresFromChain(
                ((MALExpressionModel.ParenChainExpr) expr).getMethodChain(), closures);
        } else if (expr instanceof MALExpressionModel.FunctionCallExpr) {
            final MALExpressionModel.FunctionCallExpr fce =
                (MALExpressionModel.FunctionCallExpr) expr;
            collectClosuresFromArgs(fce.getFunctionName(),
                fce.getArguments(), closures);
            collectClosuresFromChain(
                fce.getMethodChain(), closures);
        }
    }

    private void collectClosuresFromChain(final List<MALExpressionModel.MethodCall> chain,
                                          final List<ClosureInfo> closures) {
        for (final MALExpressionModel.MethodCall mc : chain) {
            collectClosuresFromArgs(mc.getName(), mc.getArguments(), closures);
        }
    }

    private void collectClosuresFromArgs(final String methodName,
                                         final List<MALExpressionModel.Argument> args,
                                         final List<ClosureInfo> closures) {
        for (final MALExpressionModel.Argument arg : args) {
            if (arg instanceof MALExpressionModel.ClosureArgument) {
                closures.add(new ClosureInfo((MALExpressionModel.ClosureArgument) arg, methodName));
            } else if (arg instanceof MALExpressionModel.ExprArgument) {
                collectClosures(
                    ((MALExpressionModel.ExprArgument) arg).getExpr(), closures);
            }
        }
    }

    private void generateClosureStatement(final StringBuilder sb, final MALExpressionModel.ClosureStatement stmt) {
        if (stmt instanceof MALExpressionModel.ClosureAssignment) {
            final MALExpressionModel.ClosureAssignment assign =
                (MALExpressionModel.ClosureAssignment) stmt;
            if (beanMode && assign.getMapVar().equals(paramName)) {
                // Bean setter: me.attr0 = 'value' → me.setAttr0("value")
                final String keyText = MALCodegenHelper.extractConstantKey(assign.getKeyExpr());
                if (keyText != null) {
                    MALCallables.require(MeterEntity.class, "set" + Character.toUpperCase(keyText.charAt(0))
                        + keyText.substring(1), 1);
                    sb.append("    ").append(assign.getMapVar()).append(".set")
                      .append(Character.toUpperCase(keyText.charAt(0)))
                      .append(keyText.substring(1)).append("(");
                    generateClosureExpr(sb, assign.getValue());
                    sb.append(");\n");
                } else {
                    throw new IllegalArgumentException("In decorate, a field is set by its name (me.attr0 = ...)");
                }
            } else {
                final boolean label = assign.getMapVar().equals(paramName);
                requireWritable(assign.getMapVar(), true, label);
                if (label) {
                    requireLabelValues(assign.getValue(), false);
                }
                sb.append("    ").append(assign.getMapVar());
                emitPut(sb, assign.getKeyExpr(), assign.getValue(), label);
                sb.append(";\n");
            }
        } else if (stmt instanceof MALExpressionModel.ClosureIfStatement) {
            final MALExpressionModel.ClosureIfStatement ifStmt =
                (MALExpressionModel.ClosureIfStatement) stmt;
            sb.append("    if (");
            generateClosureCondition(sb, ifStmt.getCondition());
            sb.append(") {\n");
            branches(() -> {
                for (final MALExpressionModel.ClosureStatement s : ifStmt.getThenBranch()) {
                    generateClosureStatement(sb, s);
                }
                sb.append("    }\n");
            }, () -> {
                if (!ifStmt.getElseBranch().isEmpty()) {
                    sb.append("    else {\n");
                    for (final MALExpressionModel.ClosureStatement s : ifStmt.getElseBranch()) {
                        generateClosureStatement(sb, s);
                    }
                    sb.append("    }\n");
                }
            });
        } else if (stmt instanceof MALExpressionModel.ClosureReturnStatement) {
            emitReturn(sb, ((MALExpressionModel.ClosureReturnStatement) stmt).getValue());
        } else if (stmt instanceof MALExpressionModel.ClosureVarDecl) {
            final MALExpressionModel.ClosureVarDecl vd =
                (MALExpressionModel.ClosureVarDecl) stmt;
            if (scope.isDeclared(vd.getVarName()) && !paramName.equals(vd.getVarName())) {
                // Groovy and Java refuse a second declaration of a name in scope; one per binding keeps what a name
                // holds, the labels or a map the closure built, plain
                throw new IllegalArgumentException("'" + vd.getVarName() + "' is already declared in this scope");
            }
            scope.setBuiltMap(vd.getVarName(), freshMap(vd.getInitializer()));
            // def takes its initializer's type, as the value Groovy holds; an Object local boxes a primitive.
            Class<?> type = ClosureTypes.forName(vd.getTypeName());
            if ("def".equals(vd.getTypeName())) {
                scope.setDef(vd.getVarName(), vd);
                type = types.typeOf(vd.getInitializer());
                if (type == void.class) {
                    throw new IllegalArgumentException("def " + vd.getVarName() + " is given a void call's value");
                }
                // Typed as its value where the closure shows it; else, or when a later assignment gives it another
                // type (def port = t.port ?: 443, def n = t.n then n = Integer.valueOf(...)), held as Object, as
                // Groovy's def holds anything. An operation needing more than Object refuses it at compile time.
                if (type == null || objectDefs.contains(vd)
                    || vd.getInitializer() instanceof MALExpressionModel.ClosureNullLiteral) {
                    type = Object.class;
                }
            }
            if (paramName.equals(vd.getVarName())) {
                throw new IllegalArgumentException("A local named like the closure parameter " + paramName);
            }
            if (!"def".equals(vd.getTypeName())) {
                // a typed declaration of the name (in another branch) is no def
                scope.setDef(vd.getVarName(), null);
            }
            sb.append("    ").append(ClosureTypes.javaName(type, vd.getTypeName())).append(" ")
              .append(vd.getVarName()).append(" = ");
            emitValueAs(sb, type, vd.getInitializer());
            sb.append(";\n");
            scope.declare(vd.getVarName(), type);
            if (!"def".equals(vd.getTypeName()) && type != null && type.isPrimitive()) {
                scope.declareStatic(vd.getVarName());
            }
        } else if (stmt instanceof MALExpressionModel.ClosureVarAssign) {
            final MALExpressionModel.ClosureVarAssign va =
                (MALExpressionModel.ClosureVarAssign) stmt;
            if (!scope.isDeclared(va.getVarName())) {
                throw new IllegalArgumentException("'" + va.getVarName() + "' is assigned but never declared");
            }
            if (!freshMap(va.getValue())) {
                // it may now hold another map, the labels included: read only from here on
                scope.setBuiltMap(va.getVarName(), false);
            }
            if (paramName.equals(va.getVarName())) {
                // the caller keeps the map it passed in: a new one assigned here would be lost
                throw new IllegalArgumentException("Assigning to the closure parameter " + paramName
                    + " is not supported; change its entries instead");
            }
            final Class<?> declared = scope.localType(va.getVarName());
            final Class<?> assigned = types.typeOf(va.getValue());
            final MALExpressionModel.ClosureVarDecl def = scope.defOf(va.getVarName());
            // a value of another kind, or a quotient where the def held none (or the reverse): the def is held as
            // Object, as its kind then depends on the path taken
            if (def != null && declared != Object.class
                && (!ClosureTypes.holds(declared, assigned, va.getValue())
                    || (assigned == BigDecimal.class) != (declared == BigDecimal.class))
                && objectDefs.add(def)) {
                throw new WidenDef();
            }
            sb.append("    ").append(va.getVarName()).append(" = ");
            emitValueAs(sb, declared, va.getValue());
            sb.append(";\n");
        } else if (stmt instanceof MALExpressionModel.ClosureExprStatement) {
            final MALExpressionModel.ClosureExpr expr = ((MALExpressionModel.ClosureExprStatement) stmt).getExpr();
            final MALExpressionModel.ClosureExpr inner = expr instanceof MALExpressionModel.ClosureExprCondition
                ? ((MALExpressionModel.ClosureExprCondition) expr).getExpr() : expr;
            if (inner instanceof MALExpressionModel.ClosureTernaryExpr) {
                // c ? t.remove('a') : t.remove('b') as a statement: the selected arm runs, once.
                final MALExpressionModel.ClosureExpr[] arms = ClosureTypes.arms(inner);
                sb.append("    if (");
                emitTruth(sb, ((MALExpressionModel.ClosureTernaryExpr) inner).getCondition());
                sb.append(") {\n");
                emitArmStatement(sb, arms[0]);
                sb.append("    } else {\n");
                emitArmStatement(sb, arms[1]);
                sb.append("    }\n");
                return;
            }
            if (inner instanceof MALExpressionModel.ClosureElvisExpr) {
                // t.env ?: t.putIfAbsent('env', ...) as a statement: the fallback runs when the primary is falsy.
                final MALExpressionModel.ClosureElvisExpr elvis = (MALExpressionModel.ClosureElvisExpr) inner;
                sb.append("    if (!(");
                emitTruth(sb, elvis.getPrimary());
                sb.append(")) {\n");
                emitArmStatement(sb, elvis.getFallback());
                sb.append("    }\n");
                return;
            }
            if (isValueOnly(inner)) {
                // Groovy would use it as the closure's value only at the end, which the closure codegen handles.
                throw new IllegalArgumentException("A " + inner.getClass().getSimpleName()
                    + " statement computes a value nothing uses; it is not supported here");
            }
            if (expr instanceof MALExpressionModel.ClosureCondition
                    && !(expr instanceof MALExpressionModel.ClosureExprCondition)) {
                // Evaluated for its side effects, as Groovy does; a bare boolean is no Java statement.
                sb.append("    if (");
                generateClosureCondition(sb, (MALExpressionModel.ClosureCondition) expr);
                sb.append(") { }\n");
            } else {
                sb.append("    ");
                generateClosureExpr(sb, expr);
                sb.append(";\n");
            }
        } else {
            throw new IllegalArgumentException(
                "Unsupported closure statement: " + stmt.getClass().getSimpleName());
        }
    }

    private void generateClosureExpr(final StringBuilder sb, final MALExpressionModel.ClosureExpr expr) {
        if (expr instanceof MALExpressionModel.ClosureStringLiteral) {
            sb.append('"')
              .append(DslJavaSourceText.toLiteral(((MALExpressionModel.ClosureStringLiteral) expr).getValue()))
              .append('"');
        } else if (expr instanceof MALExpressionModel.ClosureNumberLiteral) {
            final MALExpressionModel.ClosureNumberLiteral number = (MALExpressionModel.ClosureNumberLiteral) expr;
            final double val = number.getValue();
            // As Groovy types it: an int, a long past the int range ("3000000000"), a decimal keeping its fraction.
            if (!number.isIntegral()) {
                sb.append(val);
            } else if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                sb.append((int) val);
            } else if ((long) val == Long.MIN_VALUE) {
                sb.append("java.lang.Long.MIN_VALUE");
            } else {
                sb.append((long) val).append('L');
            }
        } else if (expr instanceof MALExpressionModel.ClosureBoolLiteral) {
            sb.append(((MALExpressionModel.ClosureBoolLiteral) expr).isValue());
        } else if (expr instanceof MALExpressionModel.ClosureNullLiteral) {
            sb.append("null");
        } else if (expr instanceof MALExpressionModel.ClosureMapLiteral) {
            final MALExpressionModel.ClosureMapLiteral mapLit =
                (MALExpressionModel.ClosureMapLiteral) expr;
            // mapOf, not Map.of: a label the sample lacks maps to null, and any number of entries.
            sb.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".mapOf(new Object[]{");
            for (int i = 0; i < mapLit.getEntries().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                final MALExpressionModel.MapEntry entry = mapLit.getEntries().get(i);
                sb.append('"').append(DslJavaSourceText.toLiteral(entry.getKey())).append("\", ($w) (");
                generateClosureExpr(sb, entry.getValue());
                sb.append(')');
            }
            sb.append("})");
        } else if (expr instanceof MALExpressionModel.ClosureMethodChain) {
            generateClosureMethodChain(sb,
                (MALExpressionModel.ClosureMethodChain) expr);
        } else if (expr instanceof MALExpressionModel.ClosureBinaryExpr) {
            emitBinary(sb, (MALExpressionModel.ClosureBinaryExpr) expr);
        } else if (expr instanceof MALExpressionModel.ClosureTernaryExpr) {
            final MALExpressionModel.ClosureTernaryExpr ternary =
                (MALExpressionModel.ClosureTernaryExpr) expr;
            // Tested as an if condition is: Groovy truth, so null, false, "" and zero are false.
            sb.append("((");
            emitTruth(sb, ternary.getCondition());
            // Two primitive arms stay primitive (int n = c ? 3 : 0); otherwise both are boxed, so a primitive arm
            // merges with a reference or null one (c ? 1 : null, c ? s.length() : 'missing').
            final Class<?> trueType = types.typeOf(ternary.getTrueExpr());
            final Class<?> falseType = types.typeOf(ternary.getFalseExpr());
            final boolean boxed = !(trueType != null && trueType.isPrimitive() && trueType == falseType);
            sb.append(") ? ");
            emitArm(sb, ternary.getTrueExpr(), boxed);
            sb.append(" : ");
            emitArm(sb, ternary.getFalseExpr(), boxed);
            sb.append(")");
        } else if (expr instanceof MALExpressionModel.ClosureUnaryNegExpr) {
            final MALExpressionModel.ClosureExpr operand = ((MALExpressionModel.ClosureUnaryNegExpr) expr).getOperand();
            final Class<?> type = types.typeOf(operand);
            final Class<?> primitive = ClosureTypes.numericOf(type);
            if (primitive == null) {
                // known only at run time: negated as Groovy negates it (a Float stays a Float, a quotient a BigDecimal)
                final boolean quotient = type == BigDecimal.class;
                sb.append(quotient ? "((java.math.BigDecimal) " : "").append(MALCodegenHelper.RUNTIME_HELPER_FQCN)
                  .append(".negate(($w) (");
                generateClosureExpr(sb, operand);
                sb.append(quotient ? ")))" : "))");
            } else {
                final boolean narrow = primitive == byte.class || primitive == short.class;
                sb.append(narrow ? "((" + primitive.getName() + ") -(" : "(-(");
                generateClosureExpr(sb, operand);
                sb.append(type.isPrimitive() ? "))" : ")." + primitive.getName() + "Value())");
            }
        } else if (expr instanceof MALExpressionModel.ClosureElvisExpr) {
            final MALExpressionModel.ClosureElvisExpr elvis =
                (MALExpressionModel.ClosureElvisExpr) expr;
            // Groovy `?:` evaluates the primary once and the fallback only when the primary is falsy.
            if (isVoid(elvis.getPrimary())) {
                throw new IllegalArgumentException("A void call has no value for ?: to test");
            }
            final Class<?> elvisType = types.typeOf(elvis);
            final String held = temp("elvis");
            // a Groovy decimal (a quotient) is held as the BigDecimal it is, so narrowing it later wraps
            sb.append("(").append(MALCodegenHelper.VALUES_FQCN).append(".isTruthy(").append(held)
              .append(" = ");
            emitArm(sb, elvis.getPrimary(), true);
            sb.append(") ? ");
            if (elvisType == null || elvisType == Object.class) {
                sb.append(held).append(" : ");
                emitArm(sb, elvis.getFallback(), true);
                sb.append(")");
                return;
            }
            if (elvisType == boolean.class) {
                sb.append("((Boolean) ").append(held).append(").booleanValue() : (");
            } else if (elvisType.isPrimitive()) {
                sb.append("((Number) ").append(held).append(").").append(elvisType.getName()).append("Value() : (");
            } else {
                final String name = elvisType.getCanonicalName();
                sb.append("(").append(name).append(") ").append(held).append(" : (").append(name).append(") ($w) (");
            }
            generateClosureExpr(sb, elvis.getFallback());
            sb.append("))");
        } else if (expr instanceof MALExpressionModel.ClosureInterpolation) {
            sb.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".gstring(($w) (");
            generateClosureExpr(sb, ((MALExpressionModel.ClosureInterpolation) expr).getExpr());
            sb.append("))");
        } else if (expr instanceof MALExpressionModel.ClosureRegexMatchExpr) {
            final MALExpressionModel.ClosureRegexMatchExpr rm =
                (MALExpressionModel.ClosureRegexMatchExpr) expr;
            sb.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".regexMatch(String.valueOf(");
            generateClosureExpr(sb, rm.getTarget());
            sb.append("), \"").append(DslJavaSourceText.toLiteral(rm.getPattern())).append("\")");
        } else if (expr instanceof MALExpressionModel.ClosureExprChain) {
            final MALExpressionModel.ClosureExprChain chain = (MALExpressionModel.ClosureExprChain) expr;
            requirePlainSegments(chain.getSegments(), "a parenthesised expression", true);
            final Class<?> baseType = types.typeOf(chain.getBase());
            final StringBuilder base = new StringBuilder();
            if (chain.getSegments().stream().anyMatch(s -> s instanceof MALExpressionModel.ClosureMethodCallSeg)) {
                // A method resolves on the receiver's static type: cast the value to the type it has.
                if (baseType == null || baseType == void.class) {
                    throw new IllegalArgumentException("The type of a parenthesised value a method is called on is "
                        + "not known here; assign it to a typed local first");
                }
                final Class<?> receiver = baseType.isPrimitive() ? DslMethods.boxed(baseType) : baseType;
                base.append("((").append(receiver.getCanonicalName()).append(") ($w) (");
                generateClosureExpr(base, chain.getBase());
                base.append("))");
            } else {
                base.append("(");
                generateClosureExpr(base, chain.getBase());
                base.append(")");
            }
            emitChain(sb, ChainRoot.EXPRESSION, null, base.toString(), baseType, chain.getSegments());
        } else if (expr instanceof MALExpressionModel.ClosureExprCondition) {
            // A bare condition expression used as a statement (e.g., tags.remove('x')
            // parsed as closureCondition → conditionExpr).  Unwrap and emit the inner
            // expression directly — this is a side-effect call, not a boolean test.
            generateClosureExpr(sb,
                ((MALExpressionModel.ClosureExprCondition) expr).getExpr());
        } else if (expr instanceof MALExpressionModel.ClosureCondition) {
            generateClosureCondition(sb, (MALExpressionModel.ClosureCondition) expr);
        } else {
            throw new IllegalArgumentException(
                "Unsupported closure expression: " + expr.getClass().getSimpleName());
        }
    }

    /**
     * {@code + - * /} as Groovy computes them: a + with a text joins, numbers compute as their static types, values
     * typed only at run time and arithmetic with a quotient through MalRuntimeHelper.
     */
    private void emitBinary(final StringBuilder sb, final MALExpressionModel.ClosureBinaryExpr bin) {
        final Class<?> result = types.typeOf(bin);
        final Class<?> leftType = types.typeOf(bin.getLeft());
        final Class<?> rightType = types.typeOf(bin.getRight());
        final boolean concat = bin.getOp() == MALExpressionModel.ArithmeticOp.ADD && result == String.class;
        if (bin.getOp() == MALExpressionModel.ArithmeticOp.MUL && (leftType == String.class
            || rightType == String.class)) {
            throw new IllegalArgumentException("A string times a number is not supported (Groovy repeats the "
                + "string); build the string with a loop");
        }
        if (concat && !DslNumbers.mayJoinText(leftType)) {
            // Groovy has + with a text after it for a String, a number, a character (the one-character string it is)
            // and null only (MissingMethodException)
            throw new IllegalArgumentException("A " + leftType.getSimpleName() + " + a string has no meaning in "
                + "Groovy; convert it first, e.g. String.valueOf(...) + ...");
        }
        if (bin.getOp() == MALExpressionModel.ArithmeticOp.ADD && ClosureTypes.isContainer(leftType)) {
            throw new IllegalArgumentException("+ on an array, a collection or a map (Groovy appends or merges) is "
                + "not supported in a closure");
        }
        if (!concat && (result == BigDecimal.class || result == double.class)
            && (leftType == BigDecimal.class || rightType == BigDecimal.class)) {
            // arithmetic with a quotient, as Groovy computes it: exact, or in double beside a floating number
            emitDynamicOperation(sb, bin, result);
            return;
        }
        if (!concat && result == Object.class) {
            // not statically numbers: Groovy's arithmetic at run time (Long - Long stays a Long, a Character counts by
            // its code, a String concatenates)
            emitDynamicOperation(sb, bin, result);
            return;
        }
        // The operands first, so a nested operation's own refusal (a whole-number division) is the one reported.
        final StringBuilder left = new StringBuilder();
        final StringBuilder right = new StringBuilder();
        if (concat && !ClosureTypes.joinsText(leftType)) {
            // a value only the run decides joins a text if it is one Groovy has that + for
            left.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".concatLeft(($w) (");
            generateClosureExpr(left, bin.getLeft());
            left.append("))");
        } else {
            emitOperand(left, bin.getLeft(), concat);
        }
        emitOperand(right, bin.getRight(), concat);
        if (concat && DslNumbers.isItems(rightType)) {
            // an array, a collection or a map is no text, on either side of a text +
            throw DslNumbers.cannotJoinText(rightType);
        }
        if (!concat && bin.getOp() != MALExpressionModel.ArithmeticOp.DIV && result == null) {
            // Javassist would concatenate an Object operand, or fail to verify: Groovy would compute.
            throw new IllegalArgumentException("The operands of " + bin.getOp() + " here are not known to be "
                + "numbers; assign them to typed locals first");
        }
        if (bin.getOp() == MALExpressionModel.ArithmeticOp.DIV && result == BigDecimal.class) {
            // two whole numbers: the BigDecimal quotient Groovy computes; a zero divisor throws
            sb.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".quotient((long) ").append(left)
              .append(", (long) ").append(right).append(')');
            return;
        }
        sb.append("(").append(left);
        switch (bin.getOp()) {
            case ADD:
                sb.append(" + ");
                break;
            case SUB:
                sb.append(" - ");
                break;
            case MUL:
                sb.append(" * ");
                break;
            case DIV:
                // with a floating operand or a decimal literal, in double; two whole numbers divide through quotient
                // (emitted above)
                sb.append(" / ");
                break;
            default:
                throw new IllegalArgumentException("Unsupported closure operator: " + bin.getOp());
        }
        sb.append(right).append(")");
    }

    /**
     * An operation MalRuntimeHelper computes as Groovy does, its result cast to the type the closure gives it.
     */
    private void emitDynamicOperation(final StringBuilder sb, final MALExpressionModel.ClosureBinaryExpr bin,
                                      final Class<?> result) {
        sb.append(result == BigDecimal.class ? "((java.math.BigDecimal) " : result == double.class ? "((Number) " : "")
          .append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append('.').append(dynamicOperation(bin.getOp()))
          .append("(($w) (");
        generateClosureExpr(sb, bin.getLeft());
        sb.append("), ($w) (");
        generateClosureExpr(sb, bin.getRight());
        sb.append(result == BigDecimal.class ? ")))" : result == double.class ? "))).doubleValue()" : "))");
    }

    /**
     * A chain on a name: the labels, decorate's bean, a local, or a class of the static calls (by its simple or its
     * full name).
     */
    private void generateClosureMethodChain(final StringBuilder sb, final MALExpressionModel.ClosureMethodChain chain) {
        final String target = chain.getTarget();
        final List<MALExpressionModel.ClosureChainSegment> segs = chain.getSegments();
        if (!scope.isDeclared(target)) {
            // a name that is no parameter or local would reach the generated method's own scope
            final ClosureTypes.StaticRoot root = ClosureTypes.resolveStatic(target, segs);
            if (root == null) {
                throw MALCallables.get().unknownName(target, "its parameters and locals");
            }
            final List<MALExpressionModel.ClosureChainSegment> rest = segs.subList(root.consumed, segs.size());
            if (rest.isEmpty()) {
                throw new IllegalArgumentException("The class " + root.type.getSimpleName() + " is no value: a closure "
                    + "calls its listed static methods or reads its listed static fields");
            }
            if (rest.get(0) instanceof MALExpressionModel.ClosureIndexAccess) {
                throw new IllegalArgumentException("The class " + root.type.getSimpleName() + " takes no index; an "
                    + "index reads a list, an array or a text");
            }
            if (rest.get(0) instanceof MALExpressionModel.ClosureFieldAccess) {
                MALCallables.get().requireStaticField(root.type,
                    ((MALExpressionModel.ClosureFieldAccess) rest.get(0)).getName());
            }
            requirePlainSegments(segs, target, true);
            emitChain(sb, ChainRoot.CLASS, target, root.type.getName(), root.type, rest);
            return;
        }
        if (target.equals(paramName) && !beanMode) {
            // t?.env is t.env (the labels are never null); a call made null-safe on the map itself is not supported.
            if (!segs.isEmpty() && segs.get(0) instanceof MALExpressionModel.ClosureMethodCallSeg
                && ((MALExpressionModel.ClosureMethodCallSeg) segs.get(0)).isSafeNav()) {
                throw new IllegalArgumentException("?. on the closure's map parameter itself is not supported");
            }
            emitChain(sb, ChainRoot.LABELS, target, target, Map.class, segs);
            return;
        }
        requirePlainSegments(segs, target, true);
        emitChain(sb, target.equals(paramName) ? ChainRoot.BEAN : ChainRoot.LOCAL, target, target,
            scope.localType(target), segs);
    }

    /**
     * What a chain starts at, which decides how its first segment reads and what it may write.
     */
    private enum ChainRoot {
        /** The labels: a field or an index reads a label, a call on them reads or writes one. */
        LABELS,
        /** Decorate's MeterEntity: a field reads its getter. */
        BEAN,
        LOCAL,
        /** A class of the static calls: the first call is a static one. */
        CLASS,
        /** A parenthesised value: a map is written by its name, not through it. */
        EXPRESSION
    }

    /**
     * The segments of a chain after its root, each on the value before it as {@link ClosureTypes#receiverTypes} types
     * it.
     *
     * @param rootName the name the chain starts at, which a write to a map is made through
     * @param rootText the root as generated code
     */
    private void emitChain(final StringBuilder sb, final ChainRoot root, final String rootName, final String rootText,
                           final Class<?> rootType, final List<MALExpressionModel.ClosureChainSegment> segs) {
        final Class<?>[] receivers = types.receiverTypes(rootType, root == ChainRoot.LABELS, root == ChainRoot.CLASS,
            segs);
        final StringBuilder local = new StringBuilder(rootText);
        for (int i = 0; i < segs.size(); i++) {
            final MALExpressionModel.ClosureChainSegment seg = segs.get(i);
            final boolean labelLookup = root == ChainRoot.LABELS && i == 0;
            if (seg instanceof MALExpressionModel.ClosureFieldAccess) {
                final MALExpressionModel.ClosureFieldAccess field = (MALExpressionModel.ClosureFieldAccess) seg;
                if (labelLookup) {
                    wrap(local, "((String) ", ".get(\"" + DslJavaSourceText.toLiteral(field.getName()) + "\"))");
                } else if (field.isSafeNav()) {
                    throw new IllegalArgumentException("?." + field.getName() + " after the map lookup is not "
                        + "supported; only ?. on a method call there is");
                } else if (root == ChainRoot.CLASS && i == 0) {
                    // a static field, which the list names one by one (checked where the class is resolved)
                    local.append('.').append(field.getName());
                } else if (receivers[i] != null && receivers[i].isArray() && "length".equals(field.getName())) {
                    local.append(".length");
                } else {
                    // a property of a value is read through its getter, which the list allows or refuses
                    final String getter = ClosureTypes.getterOf(receivers[i], field.getName());
                    if (getter == null) {
                        throw new IllegalArgumentException(receivers[i].getSimpleName() + "." + field.getName()
                            + " reads no property: a closure reads one through its getter, and a "
                            + receivers[i].getSimpleName() + " has no get or is method for " + field.getName());
                    }
                    MALCallables.require(receivers[i], getter, 0);
                    local.append('.').append(getter).append("()");
                }
            } else if (seg instanceof MALExpressionModel.ClosureIndexAccess) {
                final MALExpressionModel.ClosureExpr index = ((MALExpressionModel.ClosureIndexAccess) seg).getIndex();
                if (labelLookup) {
                    wrap(local, "((String) ", ".get(");
                    generateClosureExpr(local, index);
                    local.append("))");
                } else {
                    // Groovy reads a negative index from the end (p[-1]).
                    wrap(local, MALCodegenHelper.RUNTIME_HELPER_FQCN + ".at(", ", ");
                    appendIndex(local, index);
                    local.append(')');
                    castElement(local, receivers[i]);
                }
            } else {
                final MALExpressionModel.ClosureMethodCallSeg call = (MALExpressionModel.ClosureMethodCallSeg) seg;
                if (call.isSafeNav()) {
                    emitSafeCall(local, receivers[i], receivers[i + 1], call);
                } else {
                    emitCall(local, root, rootName, i == 0, receivers[i], call);
                }
            }
        }
        sb.append(local);
    }

    /**
     * Puts a prefix before and a suffix after the code a builder holds.
     */
    private static void wrap(final StringBuilder code, final String prefix, final String suffix) {
        code.insert(0, prefix).append(suffix);
    }

    /**
     * A call on the value in {@code local}: a label read or write on the labels, an array's size() as its length,
     * toString() as Groovy prints the value.
     *
     * @param onRoot the call is made on the chain's root itself
     */
    private void emitCall(final StringBuilder local, final ChainRoot root, final String rootName, final boolean onRoot,
                          final Class<?> receiver, final MALExpressionModel.ClosureMethodCallSeg call) {
        final boolean onClass = root == ChainRoot.CLASS && onRoot;
        if (onClass) {
            // a class name calls only its listed static methods, never the value calls below (Integer.toString())
            MALCallables.get().requireStatic(receiver, call.getName());
        }
        final boolean onLabels = root == ChainRoot.LABELS && onRoot;
        final boolean labelWrite = onLabels && "put".equals(call.getName()) && call.getArguments().size() == 2;
        if (labelWrite) {
            requireLabelValues(call.getArguments().get(1), false);
        }
        // a label, as tags.key reads it: a string the chain can call on
        final boolean labelRead = onLabels && types.isLabelRead(call);
        if (labelRead) {
            local.insert(0, "((String) ");
        }
        if ("size".equals(call.getName()) && call.getArguments().isEmpty() && receiver != null && receiver.isArray()) {
            // Groovy .size() on an array (t.raw.split('-').size()) is Java's .length
            local.append(".length");
            return;
        }
        if (groovyToString(local, receiver, call)) {
            return;
        }
        prepareReceiver(local, receiver, call);
        if (!onClass) {
            MALCallables.require(receiver, call.getName(), call.getArguments().size());
        }
        if (isMapWrite(call) && receiver != null && Map.class.isAssignableFrom(receiver)) {
            requireWritable(root == ChainRoot.EXPRESSION ? null : rootName, onRoot, onLabels);
        }
        if (labelWrite) {
            emitPut(local, call.getArguments().get(0), call.getArguments().get(1), true);
        } else {
            local.append('.').append(call.getName()).append('(');
            appendArguments(local, receiver, call);
            local.append(')');
        }
        if (labelRead) {
            local.append(')');
        }
    }

    /**
     * {@code ?.} on a call after the labels' lookup: the receiver evaluated once, into a temporary; the result boxed,
     * so a primitive one (?.length()) and null share one type.
     *
     * @param result the call's type, so an array result is a typed null ({@code ?.split(',')})
     */
    private void emitSafeCall(final StringBuilder local, final Class<?> receiver, final Class<?> result,
                              final MALExpressionModel.ClosureMethodCallSeg call) {
        final String prior = local.toString();
        local.setLength(0);
        final String held = temp("nav");
        final String evaluated = "((" + held + " = ($w) (" + prior + ")) == null ? ";
        if ("size".equals(call.getName()) && call.getArguments().isEmpty() && receiver != null && receiver.isArray()) {
            // ?.size() on an array: its length, or null
            local.append(evaluated).append("null : Integer.valueOf(((").append(receiver.getCanonicalName())
              .append(") ").append(held).append(").length))");
            return;
        }
        final Class<?> receiverClass = receiver == null
            || ClosureTypes.stringReceiver(receiver, call.getName(), call.getArguments().size())
            ? String.class : receiver.isPrimitive() ? DslMethods.boxed(receiver) : receiver;
        if ("toString".equals(call.getName()) && call.getArguments().isEmpty() && receiverClass != String.class) {
            // ?.toString() on an array or collection: as Groovy prints it
            local.append(evaluated).append("null : ").append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".gstring(")
              .append(held).append("))");
            return;
        }
        // Javassist types the null arm only when it is cast
        final String none = result != null && result.isArray() ? "(" + result.getCanonicalName() + ") null" : "null";
        local.append(evaluated).append(none).append(" : ($w) (((").append(receiverClass.getCanonicalName())
          .append(") ").append(held).append(").").append(call.getName()).append('(');
        MALCallables.require(receiverClass, call.getName(), call.getArguments().size());
        if (isMapWrite(call) && Map.class.isAssignableFrom(receiverClass)) {
            throw new IllegalArgumentException("A map is written by its name, without ?. (" + call.getName() + ")");
        }
        appendArguments(local, receiverClass, call);
        local.append(")))");
    }

    /**
     * Compiles a closure into its companion class: the class implements the closure kind's interface, the closure's
     * body inlined in the interface's method.
     */
    CtClass makeCompanionClass(final CtClass mainClass,
                               final String fieldName,
                               final ClosureInfo info) throws Exception {
        final String companionName = mainClass.getName() + "$" + fieldName;
        final CtClass companion = classPool.makeClass(companionName);
        companion.addInterface(classPool.get(info.kind.interfaceType));
        companion.addConstructor(CtNewConstructor.defaultConstructor(companion));

        final String methodBody = generateBody(info.kind, info.closure);
        if (log.isDebugEnabled()) {
            log.debug("Companion class [{}] apply():\n{}", companionName, methodBody);
        }
        final CtMethod m = CtNewMethod.make(methodBody, companion);
        companion.addMethod(m);
        DslGeneratedFileWriter.addLocalVariableTable(m, m.getDeclaringClass().getName(),
            info.kind.localVariables(info.closure.getParams()));
        // A companion is its own class file: its own .java, its SourceFile naming it (Javassist's own default loses the
        // rule), and one LineNumberTable entry at its method's signature, as a closure body stores to no result slot.
        final String companionSource = wrapCompanionSource(companion, info, methodBody);
        DslGeneratedFileWriter.setSourceFile(companion, bytecodeHelper.sourceFileNameOf(companion));
        bytecodeHelper.writeSourceFile(companion, companionSource);
        DslGeneratedFileWriter.attachSignatureLine(m,
            DslGeneratedFileWriter.lineOfMethod(companionSource, info.kind.samName));
        return companion;
    }

    /**
     * A receiver as the call needs it: a primitive boxed, as Groovy calls the method on its box; an Object-typed one (a
     * label read with an Object fallback, a def held as Object) calling a String method cast to String, as Groovy would
     * call it on the string it holds (a ClassCastException if it holds no string).
     */
    private static void prepareReceiver(final StringBuilder local, final Class<?> receiver,
                                        final MALExpressionModel.ClosureMethodCallSeg mc) {
        if (receiver != null && receiver.isPrimitive()) {
            final String prior = local.toString();
            local.setLength(0);
            local.append("(($w) (").append(prior).append("))");
            return;
        }
        if (ClosureTypes.stringReceiver(receiver, mc.getName(), mc.getArguments().size())) {
            final String prior = local.toString();
            local.setLength(0);
            local.append("((String) ").append(prior).append(')');
        }
    }

    /**
     * Two primitives compared as Java does, except an int or long against a float, which Groovy compares in double
     * (16777217 is not 16777216f).
     */
    private void emitPrimitiveComparison(final StringBuilder sb, final MALExpressionModel.ClosureComparison cc,
                                         final String operator) {
        final Class<?> l = ClosureTypes.numericOf(types.typeOf(cc.getLeft()));
        final Class<?> r = ClosureTypes.numericOf(types.typeOf(cc.getRight()));
        final boolean wide = (l == float.class) != (r == float.class)
            && (ClosureTypes.isIntegral(l) || ClosureTypes.isIntegral(r));
        sb.append(wide ? "((double) (" : "(");
        generateClosureExpr(sb, cc.getLeft());
        sb.append(wide ? "))" : ")").append(operator).append(wide ? "((double) (" : "(");
        generateClosureExpr(sb, cc.getRight());
        sb.append(wide ? "))" : ")");
    }

    /**
     * An int index: a statically whole one cast, one known only at run time checked (Groovy's getAt takes no Long or
     * decimal on an array).
     */
    private void appendIndex(final StringBuilder local, final MALExpressionModel.ClosureExpr index) {
        requireIndex(index);
        final Class<?> type = types.typeOf(index);
        final boolean runtime = type == null || !type.isPrimitive();
        local.append(runtime ? MALCodegenHelper.RUNTIME_HELPER_FQCN + ".index(($w) (" : "(int) (");
        generateClosureExpr(local, index);
        local.append(runtime ? "))" : ")");
    }

    private void requireIndex(final MALExpressionModel.ClosureExpr index) {
        final Class<?> type = types.typeOf(index);
        if (type == char.class || type == Character.class) {
            throw new IllegalArgumentException("A character is no index (Groovy refuses it); convert it first, e.g. "
                + "Integer.parseInt(String.valueOf(...))");
        }
        if (type != null && type != Object.class && ClosureTypes.numericOf(type) == null) {
            throw new IllegalArgumentException("A " + type.getSimpleName() + " is no index (Groovy refuses it)");
        }
        if (ClosureTypes.isFloating(ClosureTypes.numericOf(type))) {
            // a decimal (1.5) would be truncated into an index
            throw new IllegalArgumentException("A decimal is no index; convert it first, e.g. (int) or as Integer");
        }
        if (type == long.class || type == Long.class) {
            // (int) would wrap it; Groovy refuses a Long index on an array
            throw new IllegalArgumentException("A long is no index; convert it first, e.g. (int) or as Integer");
        }
    }

    /**
     * An element of an array of a reference type other than Object or String keeps its type for the chain after it.
     */
    private static void castElement(final StringBuilder local, final Class<?> array) {
        if (array == null || !array.isArray() || array.getComponentType().isPrimitive()
            || array.getComponentType() == Object.class || array.getComponentType() == String.class
            || array.getComponentType().isArray()) {
            return;
        }
        final String element = local.toString();
        local.setLength(0);
        local.append("((").append(array.getComponentType().getCanonicalName()).append(") ").append(element).append(')');
    }

    private static void requireNumericLiteral(final MALExpressionModel.ClosureExpr operand, final Class<?> other) {
        if (operand instanceof MALExpressionModel.ClosureStringLiteral
            && (ClosureTypes.isNumber(other) || other == BigDecimal.class)
            && DslNumbers.numberIn(((MALExpressionModel.ClosureStringLiteral) operand).getValue()) == null) {
            throw new IllegalArgumentException("The string '" + ((MALExpressionModel.ClosureStringLiteral) operand)
                .getValue() + "' compared with a number is not a number");
        }
    }

    /**
     * A value converted to a declared number as Groovy assigns it (MalRuntimeHelper.number: a Character by its code).
     */
    private void emitNumberAs(final StringBuilder sb, final Class<?> type, final MALExpressionModel.ClosureExpr value) {
        if (type == char.class) {
            // a character, a one-character string ('0') or a number (its code), as Groovy assigns a char
            sb.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".toChar(($w) (");
            generateClosureExpr(sb, value);
            sb.append("))");
            return;
        }
        // the value's runtime type decides, as in Groovy: a BigDecimal (a quotient) wraps, a Double saturates
        sb.append("(").append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".number(($w) (");
        generateClosureExpr(sb, value);
        sb.append(")).").append(type.getName()).append("Value())");
    }

    /**
     * A ternary arm; boxed, a quotient stays the BigDecimal it is to Groovy, so narrowing it later wraps
     * (BigDecimal.intValue) where a Double saturates.
     */
    private void emitArm(final StringBuilder sb, final MALExpressionModel.ClosureExpr arm, final boolean boxed) {
        sb.append(boxed ? "($w) (" : "(");
        generateClosureExpr(sb, arm);
        sb.append(')');
    }

    private static String dynamicOperation(final MALExpressionModel.ArithmeticOp op) {
        switch (op) {
            case ADD:
                return "plus";
            case SUB:
                return "minus";
            case MUL:
                return "multiply";
            case DIV:
                return "divide";
            default:
                throw new IllegalArgumentException("Unsupported closure operator: " + op);
        }
    }

    /**
     * toString() on a map, collection or array: Groovy's text ([host:web]), not the JVM's.
     */
    private static boolean groovyToString(final StringBuilder local, final Class<?> receiver,
                                          final MALExpressionModel.ClosureMethodCallSeg mc) {
        if (!"toString".equals(mc.getName()) || !mc.getArguments().isEmpty() || receiver == String.class) {
            return false;
        }
        // as Groovy prints it: an array or collection as [a, b], a primitive boxed first; a null receiver fails
        final String prior = local.toString();
        local.setLength(0);
        local.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".text(($w) (").append(prior).append("))");
        return true;
    }

    private static boolean arrayTyped(final Class<?> type) {
        return type != null && type.isArray() && !type.getComponentType().isPrimitive();
    }

    private void appendVarargs(final StringBuilder sb, final List<MALExpressionModel.ClosureExpr> args) {
        sb.append("new Object[]{");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("($w) (");
            generateClosureExpr(sb, args.get(i));
            sb.append(')');
        }
        sb.append('}');
    }

    /**
     * A call's arguments; a primitive one widened to the parameter of the overload Java would choose, which Javassist
     * does not do (Long.valueOf(int) is valueOf(long)).
     */
    private void appendArguments(final StringBuilder sb, final Class<?> owner,
                                 final MALExpressionModel.ClosureMethodCallSeg mc) {
        final List<MALExpressionModel.ClosureExpr> args = mc.getArguments();
        if (owner == Arrays.class && "asList".equals(mc.getName())
            && !(args.size() == 1 && arrayTyped(types.typeOf(args.get(0))))) {
            // Arrays.asList('prod', 'qa') packs its arguments; a single array at run time is the arguments, as Java
            // and Groovy pass varargs
            sb.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".varargs(");
            appendVarargs(sb, args);
            sb.append(')');
            return;
        }
        final ClosureTypes.ArgumentPlan plan = types.argumentPlan(owner, mc.getName(), args);
        for (int i = 0; i < args.size(); i++) {
            if (plan.unresolved[i]) {
                throw new IllegalArgumentException("Argument " + (i + 1) + " of " + owner.getSimpleName() + "."
                    + mc.getName() + "(...) is known only at run time (its kind depends on the path taken); "
                    + "convert it first or declare its type");
            }
        }
        final Method chosen = owner == null ? null
            : DslMethods.overload(
                ClosureTypes.stringReceiver(owner, mc.getName(), args.size()) ? String.class : owner,
                mc.getName(), plan.types);
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            final Class<?> param = chosen == null ? null : chosen.getParameterTypes()[i];
            final Class<?> actual = types.typeOf(args.get(i));
            if (plan.converted[i] != null) {
                final Class<?> as = param != null && param.isPrimitive() ? param : plan.converted[i];
                emitNumberAs(sb, as, args.get(i));
            } else if (param != null && !param.isPrimitive() && actual != null && actual.isPrimitive()) {
                // a primitive into a reference parameter: boxed, as Groovy passes it
                sb.append("($w) (");
                generateClosureExpr(sb, args.get(i));
                sb.append(')');
            } else if (param != null && param.isPrimitive() && actual != null && DslMethods.unboxed(actual) != null) {
                // a wrapper into a primitive parameter: unboxed (a null fails), then widened
                sb.append("((").append(param.getName()).append(") (");
                generateClosureExpr(sb, args.get(i));
                sb.append(").").append(DslMethods.unboxed(actual).getName()).append("Value())");
            } else if (param != null && !param.isPrimitive() && param != Object.class
                && (actual == null || actual == Object.class)) {
                // a value known only at run time into a typed parameter (startsWith(def)): cast as Groovy would
                sb.append("((").append(param.getCanonicalName()).append(") ($w) (");
                generateClosureExpr(sb, args.get(i));
                sb.append("))");
            } else if (param != null && param.isPrimitive() && actual != null && actual.isPrimitive()
                && param != actual) {
                sb.append("((").append(param.getName()).append(") (");
                generateClosureExpr(sb, args.get(i));
                sb.append("))");
            } else {
                generateClosureExpr(sb, args.get(i));
            }
        }
    }

    /**
     * A ternary or ?: arm used as a statement: it runs (a nested ternary picks again); a plain value does nothing.
     */
    private void emitArmStatement(final StringBuilder sb, final MALExpressionModel.ClosureExpr arm) {
        if (arm instanceof MALExpressionModel.ClosureTernaryExpr
            || arm instanceof MALExpressionModel.ClosureElvisExpr || !isValueOnly(arm)) {
            generateClosureStatement(sb, new MALExpressionModel.ClosureExprStatement(arm));
        } else if (arm instanceof MALExpressionModel.ClosureMethodChain) {
            final String name = ((MALExpressionModel.ClosureMethodChain) arm).getTarget();
            if (scope.localType(name) == null && !name.equals(paramName) && MALCallables.listedClass(name) == null) {
                // a name nothing declares: Groovy fails when the arm runs
                throw new IllegalArgumentException("Unknown name " + name + " in a ternary arm");
            }
        } else if (!(arm instanceof MALExpressionModel.ClosureStringLiteral
            || arm instanceof MALExpressionModel.ClosureNumberLiteral
            || arm instanceof MALExpressionModel.ClosureBoolLiteral
            || arm instanceof MALExpressionModel.ClosureNullLiteral)) {
            // ['old': t.remove('token')]: evaluated, so the calls inside run; the value is discarded
            sb.append("    ").append(temp("discard")).append(" = ($w) (");
            generateClosureExpr(sb, arm);
            sb.append(");\n");
        }
    }

    /**
     * An expression that only yields a value: as a statement before the end of a closure, nothing uses it.
     */
    private static boolean isValueOnly(final MALExpressionModel.ClosureExpr expr) {
        return expr instanceof MALExpressionModel.ClosureMapLiteral
            || expr instanceof MALExpressionModel.ClosureElvisExpr
            || expr instanceof MALExpressionModel.ClosureRegexMatchExpr
            || expr instanceof MALExpressionModel.ClosureTernaryExpr
            || expr instanceof MALExpressionModel.ClosureStringLiteral
            || expr instanceof MALExpressionModel.ClosureNumberLiteral
            || expr instanceof MALExpressionModel.ClosureBoolLiteral
            || expr instanceof MALExpressionModel.ClosureNullLiteral
            || expr instanceof MALExpressionModel.ClosureBinaryExpr
            || expr instanceof MALExpressionModel.ClosureUnaryNegExpr
            || expr instanceof MALExpressionModel.ClosureMethodChain
                && ((MALExpressionModel.ClosureMethodChain) expr).getSegments().isEmpty();
    }

    /**
     * An operand of + - *: in a concatenation, a value other than a string or a primitive prints as Groovy prints it,
     * an array, a collection or a map failing; in arithmetic, a boxed number is unboxed (Javassist would concatenate).
     */
    private void emitOperand(final StringBuilder sb, final MALExpressionModel.ClosureExpr operand,
                             final boolean concat) {
        final Class<?> type = types.typeOf(operand);
        if (concat && type != String.class && (type == null || !type.isPrimitive())) {
            sb.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".concatRight(($w) (");
            generateClosureExpr(sb, operand);
            sb.append("))");
            return;
        }
        final Class<?> primitive = !concat && type != null ? DslMethods.unboxed(type) : null;
        // Groovy computes float arithmetic in double.
        final boolean promote = !concat && (type == float.class || type == Float.class);
        sb.append(promote ? "((double) (" : "(");
        generateClosureExpr(sb, operand);
        sb.append(')');
        if (primitive != null && !promote) {
            sb.append('.').append(primitive.getName()).append("Value()");
        } else if (promote) {
            sb.append(type == Float.class ? ".floatValue())" : ")");
        }
    }

    /**
     * Labels are strings: a value of another known type written to them would break every later read. Each arm of a
     * ternary or ?: is checked; a map that becomes the labels has the values of each map literal checked here, any
     * other map entry by entry when written.
     *
     * @param labelMap the value is a map that becomes the labels, not a label's value
     */
    private void requireLabelValues(final MALExpressionModel.ClosureExpr value, final boolean labelMap) {
        final MALExpressionModel.ClosureExpr[] arms = ClosureTypes.arms(value);
        if (arms != null) {
            for (final MALExpressionModel.ClosureExpr arm : arms) {
                if (!(arm instanceof MALExpressionModel.ClosureNullLiteral)) {
                    requireLabelValues(arm, labelMap);
                }
            }
            return;
        }
        if (labelMap) {
            if (value instanceof MALExpressionModel.ClosureMapLiteral) {
                for (final MALExpressionModel.MapEntry entry
                    : ((MALExpressionModel.ClosureMapLiteral) value).getEntries()) {
                    requireLabelValues(entry.getValue(), false);
                }
            }
            return;
        }
        final Class<?> type = types.typeOf(value);
        // A number or boolean is written as its string; Object (def x = null, given a value later) is checked when
        // written; any other known type is refused.
        if (type != null && type != String.class && type != Object.class && !ClosureTypes.labelConvertible(type)) {
            throw new IllegalArgumentException("A label value is a string; a number, boolean or character is written "
                + "as its string, a " + type.getSimpleName() + " is not");
        }
    }

    /**
     * The two branches of an if, each in its own scope as Java's: what one declares is its own, and after them a
     * local holds a map the closure built only where it did before and after each branch.
     */
    private void branches(final Runnable thenBranch, final Runnable elseBranch) {
        final ClosureScope.Snapshot before = scope.snapshot();
        thenBranch.run();
        final ClosureScope.Snapshot afterThen = scope.snapshot();
        scope.restore(before);
        elseBranch.run();
        scope.endBranches(before, afterThen);
    }

    private static boolean isMapWrite(final MALExpressionModel.ClosureMethodCallSeg call) {
        return "put".equals(call.getName()) || "remove".equals(call.getName());
    }

    /**
     * Refuses a write the closure may not make: the labels are written outside a filter only, any other map only when
     * it is a local the closure built and the write is made on it by its name.
     *
     * @param target  the name the map is reached from, null for a parenthesised value ((t).put(...))
     * @param viaName the write is made on the named map itself, not on a map it leads to
     * @param labels  the map written is the labels
     */
    private void requireWritable(final String target, final boolean viaName, final boolean labels) {
        if (target == null) {
            throw new IllegalArgumentException("A map is written by its name (" + paramName + ".put(...), " + paramName
                + ".x = ...), not through an expression");
        }
        if (labels) {
            if (kind.returnMode == ClosureKind.ReturnMode.FILTER) {
                throw new IllegalArgumentException("A filter reads the labels; write them in a tag closure");
            }
            return;
        }
        if (!viaName || !scope.isBuiltMap(target)) {
            throw new IllegalArgumentException("Only the labels or a map the closure built are written, not "
                + (viaName ? target : "a map " + target + " leads to"));
        }
    }

    /**
     * {@code .put(key, value)} on the map before it: into the labels a string key and the value as a label's string,
     * into a map the closure built the value boxed.
     */
    private void emitPut(final StringBuilder sb, final MALExpressionModel.ClosureExpr key,
                         final MALExpressionModel.ClosureExpr value, final boolean labels) {
        sb.append(".put(");
        if (labels) {
            emitLabelKey(sb, key);
            sb.append(", ");
            emitLabelValue(sb, value);
        } else {
            generateClosureExpr(sb, key);
            sb.append(", ($w) (");
            generateClosureExpr(sb, value);
            sb.append(')');
        }
        sb.append(')');
    }

    /**
     * @return whether the value is a map the closure builds here ([:], [k: v])
     */
    private static boolean freshMap(final MALExpressionModel.ClosureExpr value) {
        final MALExpressionModel.ClosureExpr bare = value instanceof MALExpressionModel.ClosureExprChain
            && ((MALExpressionModel.ClosureExprChain) value).getSegments().isEmpty()
            ? ((MALExpressionModel.ClosureExprChain) value).getBase() : value;
        return bare instanceof MALExpressionModel.ClosureMapLiteral;
    }

    /**
     * A label's name: a string (a number or boolean key would break every later read of the labels).
     */
    private void emitLabelKey(final StringBuilder sb, final MALExpressionModel.ClosureExpr key) {
        final Class<?> type = types.typeOf(key);
        if (type != null && type != String.class && type != Object.class) {
            throw new IllegalArgumentException("A label's name is a string, not a " + type.getSimpleName());
        }
        if (type == String.class) {
            generateClosureExpr(sb, key);
            return;
        }
        // known only at run time: checked when written
        sb.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".labelName(($w) (");
        generateClosureExpr(sb, key);
        sb.append("))");
    }

    /**
     * A value written into the labels: a string as it is; anything else through MalRuntimeHelper.labelValue (a number
     * or boolean becomes its string, another value is refused when written).
     */
    private void emitLabelValue(final StringBuilder sb, final MALExpressionModel.ClosureExpr value) {
        if (value instanceof MALExpressionModel.ClosureNullLiteral || types.typeOf(value) == String.class) {
            sb.append("($w) (");
            generateClosureExpr(sb, value);
            sb.append(')');
            return;
        }
        sb.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append(".labelValue(($w) (");
        generateClosureExpr(sb, value);
        sb.append("))");
    }

    /**
     * A chain on anything but the labels (a local, a class, a parenthesised value, decorate's bean) honours no
     * {@code ?.}, and an index only where it takes one: refused instead of dropped.
     */
    private static void requirePlainSegments(final List<MALExpressionModel.ClosureChainSegment> segs,
                                             final String receiver,
                                             final boolean indexable) {
        for (final MALExpressionModel.ClosureChainSegment seg : segs) {
            final boolean safeNav =
                seg instanceof MALExpressionModel.ClosureFieldAccess
                    && ((MALExpressionModel.ClosureFieldAccess) seg).isSafeNav()
                || seg instanceof MALExpressionModel.ClosureMethodCallSeg
                    && ((MALExpressionModel.ClosureMethodCallSeg) seg).isSafeNav();
            if (safeNav) {
                throw new IllegalArgumentException(
                    "?. after " + receiver + " is not supported; only the closure's map parameter takes it");
            }
            if (seg instanceof MALExpressionModel.ClosureIndexAccess && !indexable) {
                throw new IllegalArgumentException(
                    "Unsupported access on " + receiver + ": " + seg.getClass().getSimpleName());
            }
        }
    }

    /**
     * A closure body whose end is its value, as Groovy's: the last expression (a map literal, a variable, a call; in a
     * filter a condition too), the variable a last declaration sets, in a filter the value a last map assignment
     * stores, or, for a last if, what each branch ends with; for the closure kind's caller ({@link #emitReturn}).
     */
    private void emitTail(final StringBuilder sb, final List<MALExpressionModel.ClosureStatement> body) {
        final MALExpressionModel.ClosureStatement last = body.isEmpty() ? null : body.get(body.size() - 1);
        final MALExpressionModel.ClosureExpr value = trailingValue(body);
        final boolean assignedValue = kind.returnMode == ClosureKind.ReturnMode.FILTER
            && last instanceof MALExpressionModel.ClosureAssignment;
        final int statements = value != null && last instanceof MALExpressionModel.ClosureExprStatement
            || last instanceof MALExpressionModel.ClosureIfStatement || assignedValue ? body.size() - 1 : body.size();
        for (int i = 0; i < statements; i++) {
            generateClosureStatement(sb, body.get(i));
        }
        if (assignedValue) {
            // flags.keep = t.job == 'api': the value assigned decides (Map.put returns the previous one), evaluated
            // once, before the key
            final MALExpressionModel.ClosureAssignment assign = (MALExpressionModel.ClosureAssignment) last;
            requireWritable(assign.getMapVar(), true, assign.getMapVar().equals(paramName));
            final String assigned = temp("assigned");
            sb.append("    ").append(assigned).append(" = ($w) (");
            generateClosureExpr(sb, assign.getValue());
            sb.append(");\n    ").append(assign.getMapVar()).append(".put(");
            generateClosureExpr(sb, assign.getKeyExpr());
            sb.append(", ").append(assigned).append(");\n    return ")
              .append(MALCodegenHelper.VALUES_FQCN).append(".isTruthy(").append(assigned).append(");\n");
        } else if (value != null) {
            emitReturn(sb, value);
        } else if (last instanceof MALExpressionModel.ClosureIfStatement) {
            final MALExpressionModel.ClosureIfStatement tail = (MALExpressionModel.ClosureIfStatement) last;
            sb.append("    if (");
            generateClosureCondition(sb, tail.getCondition());
            sb.append(") {\n");
            branches(() -> {
                emitTail(sb, tail.getThenBranch());
                sb.append("    }\n");
            }, () -> {
                if (!tail.getElseBranch().isEmpty()) {
                    sb.append("    else {\n");
                    emitTail(sb, tail.getElseBranch());
                    sb.append("    }\n");
                }
            });
            // Without an else, or past branches that both returned (dead code then): Groovy's null.
            emitReturn(sb, null);
        } else if (!(last instanceof MALExpressionModel.ClosureReturnStatement)) {
            if (kind.returnMode == ClosureKind.ReturnMode.PROPERTIES && last != null) {
                // Groovy returns the assigned value or the condition's Boolean: no map.
                throw new IllegalArgumentException("An instance properties closure must end with the map it returns,"
                    + " not a " + last.getClass().getSimpleName());
            }
            emitReturn(sb, null);
        }
    }

    /**
     * A {@code return}, or the value a closure ends with, as the closure's caller takes it: {@code tag} the map
     * (else the labels), {@code instance} the map (null for none), a filter its truth.
     */
    private void emitReturn(final StringBuilder sb,
                            final MALExpressionModel.ClosureExpr value) {
        if (value != null && isVoid(value)) {
            // return me.setAttr0(...): the call runs; its value is Groovy's null.
            sb.append("    ");
            generateClosureExpr(sb, value);
            sb.append(";\n");
            emitReturn(sb, null);
            return;
        }
        if (value != null && (kind.returnMode == ClosureKind.ReturnMode.TAG
            || kind.returnMode == ClosureKind.ReturnMode.PROPERTIES)) {
            // the labels it returns: every map literal among its arms holds values a label can hold
            requireLabelValues(value, true);
            final StringBuilder code = new StringBuilder("($w) (");
            generateClosureExpr(code, value);
            emitReturnCode(sb, code.append(')').toString());
            return;
        }
        switch (kind.returnMode) {
            case TAG:
                sb.append("    return ").append(paramName).append(";\n");
                break;
            case PROPERTIES:
                sb.append("    return null;\n");
                break;
            case FILTER:
                sb.append("    return ");
                if (value == null) {
                    sb.append("false");
                } else {
                    emitTruth(sb, value);
                }
                sb.append(";\n");
                break;
            default:
                if (value != null) {
                    throw new IllegalArgumentException("This closure returns nothing; a return value would be lost");
                }
                sb.append("    return;\n");
        }
    }

    /**
     * Returns an object value from a {@code tag} or {@code instance} closure, as its caller takes it.
     */
    private void emitReturnCode(final StringBuilder sb, final String valueCode) {
        sb.append("    return ").append(MALCodegenHelper.RUNTIME_HELPER_FQCN);
        if (kind.returnMode == ClosureKind.ReturnMode.TAG) {
            sb.append(".tagResult(").append(valueCode).append(", ").append(paramName).append(");\n");
        } else {
            sb.append(".propertiesResult(").append(valueCode).append(");\n");
        }
    }

    /**
     * @return the value a closure body ends with, as Groovy returns it: the last expression unless it is a condition
     *     outside a filter (a Boolean, never a map), or the variable the last declaration or assignment sets
     */
    private MALExpressionModel.ClosureExpr trailingValue(final List<MALExpressionModel.ClosureStatement> body) {
        if (body.isEmpty()) {
            return null;
        }
        final MALExpressionModel.ClosureStatement last = body.get(body.size() - 1);
        if (last instanceof MALExpressionModel.ClosureVarDecl) {
            return new MALExpressionModel.ClosureMethodChain(
                ((MALExpressionModel.ClosureVarDecl) last).getVarName(), Collections.emptyList());
        }
        if (last instanceof MALExpressionModel.ClosureVarAssign) {
            return new MALExpressionModel.ClosureMethodChain(
                ((MALExpressionModel.ClosureVarAssign) last).getVarName(), Collections.emptyList());
        }
        if (!(last instanceof MALExpressionModel.ClosureExprStatement)) {
            return null;
        }
        MALExpressionModel.ClosureExpr expr = ((MALExpressionModel.ClosureExprStatement) last).getExpr();
        if (expr instanceof MALExpressionModel.ClosureExprCondition) {
            expr = ((MALExpressionModel.ClosureExprCondition) expr).getExpr();
        }
        if (expr instanceof MALExpressionModel.ClosureCondition && kind.returnMode != ClosureKind.ReturnMode.FILTER
            || isVoid(expr)) {
            return null;
        }
        return expr;
    }

    /**
     * The companion's generated {@code .java}: the same envelope handed to Javassist, so
     * source-attach renders exactly the code that was compiled.
     *
     * @param companion  the companion class being generated
     * @param info       the closure it implements, for the {@code implements} clause
     * @param methodBody the SAM method source fed to Javassist
     * @return the full source of the companion's generated .java
     */
    private static String wrapCompanionSource(final CtClass companion,
                                              final ClosureInfo info,
                                              final String methodBody) {
        final StringBuilder sb = new StringBuilder();
        sb.append("package ").append(companion.getPackageName()).append(";\n\n");
        sb.append("public class ").append(companion.getSimpleName())
          .append(" implements ").append(info.kind.interfaceType).append(" {\n\n");
        sb.append("    ").append(methodBody.replace("\n", "\n    ")).append("\n");
        sb.append("}\n");
        return sb.toString();
    }

    private void generateClosureCondition(final StringBuilder sb, final MALExpressionModel.ClosureCondition cond) {
        if (cond instanceof MALExpressionModel.ClosureComparison) {
            final MALExpressionModel.ClosureComparison cc =
                (MALExpressionModel.ClosureComparison) cond;
            switch (cc.getOp()) {
                case EQ:
                case NEQ:
                    if (types.bothGroovyStatic(cc)) {
                        // two primitives Groovy types statically (declared locals, whole literals): Java's ==, so
                        // NaN is unequal to itself and -0.0 equals 0.0
                        emitPrimitiveComparison(sb, cc,
                            cc.getOp() == MALExpressionModel.CompareOp.NEQ ? " != " : " == ");
                        break;
                    }
                    // Boxed ($w): one Groovy-style comparison for every pairing of primitive and object operands.
                    sb.append(cc.getOp() == MALExpressionModel.CompareOp.NEQ ? "!" : "")
                      .append(MALCodegenHelper.VALUES_FQCN).append(".equal(($w) (");
                    generateClosureExpr(sb, cc.getLeft());
                    sb.append("), ($w) (");
                    generateClosureExpr(sb, cc.getRight());
                    sb.append("))");
                    break;
                default:
                    final Class<?> leftType = types.typeOf(cc.getLeft());
                    final Class<?> rightType = types.typeOf(cc.getRight());
                    // a string or a character against a number is read as a number ("3" < 5, '1' < 2): a literal that
                    // is none is refused here
                    requireNumericLiteral(cc.getLeft(), rightType);
                    requireNumericLiteral(cc.getRight(), leftType);
                    // two primitives of one type, or two whole numbers, as Java's primitives; any other pair, a boxed or
                    // a nullable operand, through MalRuntimeHelper (null lowest), which also compares numbers as Java
                    // does: a NaN false, -0.0 equal to 0.0
                    if (!ClosureTypes.isCharacter(leftType) && !ClosureTypes.isCharacter(rightType)
                        && (ClosureTypes.isIntegral(leftType) && ClosureTypes.isIntegral(rightType)
                        || leftType == rightType && leftType != null && leftType.isPrimitive()
                        && ClosureTypes.isPrimitiveNumber(leftType) || types.bothGroovyStatic(cc))) {
                        emitPrimitiveComparison(sb, cc, MALCodegenHelper.comparisonOperator(cc.getOp()));
                    } else {
                        // A boxed or nullable operand (Integer n, t.a?.length()): Groovy's ordering, null lowest.
                        sb.append(MALCodegenHelper.RUNTIME_HELPER_FQCN).append('.')
                          .append(cc.getOp().name().toLowerCase(Locale.ROOT)).append("(($w) (");
                        generateClosureExpr(sb, cc.getLeft());
                        sb.append("), ($w) (");
                        generateClosureExpr(sb, cc.getRight());
                        sb.append("))");
                    }
                    break;
            }
        } else if (cond instanceof MALExpressionModel.ClosureLogical) {
            final MALExpressionModel.ClosureLogical lc =
                (MALExpressionModel.ClosureLogical) cond;
            final String operator;
            if (lc.getOp() == MALExpressionModel.LogicalOp.AND) {
                operator = " && ";
            } else if (lc.getOp() == MALExpressionModel.LogicalOp.OR) {
                operator = " || ";
            } else {
                throw new IllegalArgumentException("Unsupported logical operator: " + lc.getOp());
            }
            sb.append("(");
            generateClosureCondition(sb, lc.getLeft());
            sb.append(operator);
            generateClosureCondition(sb, lc.getRight());
            sb.append(")");
        } else if (cond instanceof MALExpressionModel.ClosureNot) {
            sb.append("!(");
            generateClosureCondition(sb,
                ((MALExpressionModel.ClosureNot) cond).getInner());
            sb.append(")");
        } else if (cond instanceof MALExpressionModel.ClosureExprCondition) {
            emitTruth(sb, ((MALExpressionModel.ClosureExprCondition) cond).getExpr());
        } else if (cond instanceof MALExpressionModel.ClosureInCondition) {
            final MALExpressionModel.ClosureInCondition ic =
                (MALExpressionModel.ClosureInCondition) cond;
            // Arrays.asList: contains(null) is false, where List.of throws for a missing label.
            sb.append("java.util.Arrays.asList(new String[]{");
            for (int i = 0; i < ic.getValues().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append('"').append(DslJavaSourceText.toLiteral(ic.getValues().get(i))).append('"');
            }
            sb.append("}).contains(");
            generateClosureExpr(sb, ic.getExpr());
            sb.append(")");
        } else {
            throw new IllegalArgumentException(
                "Unsupported closure condition: " + cond.getClass().getSimpleName());
        }
    }
}
