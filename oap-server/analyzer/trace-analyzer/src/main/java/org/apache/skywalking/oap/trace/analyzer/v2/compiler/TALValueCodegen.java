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

import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.BinaryOp;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.CompareOp;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALBoolConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALComparisonCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALExprCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFieldSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALFunctionArg;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALIndexSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALLogicalCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALMethodSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALNotCondition;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALNullConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALNumberConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALStringConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccess;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccessConditionValue;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.TALValueAccessSegment;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.model.LogicalOp;

import java.util.List;
import org.apache.skywalking.oap.server.core.dsl.DslJavaSourceText;
import java.lang.reflect.Method;

/**
 * The expression half of every DSL's code generation: value access, conditions, casts, arithmetic and the
 * reflective getter chains, emitted as Java source for Javassist to compile.
 *
 * <p>What a rule can start an expression with — {@code span}, {@code meta}, {@code attr()} — is supplied by
 * {@link TALRootResolver}. Everything below is independent of spans:
 *
 * <pre>{@code
 * Expression type          Example                            Generated code
 * ─────────────────────── ─────────────────────────────────── ────────────────────────────
 * String literal           "SLOW_SQL"                         "SLOW_SQL"
 * Number literal           500                                Integer.valueOf(500)
 * Typed root chain         span?.status?.message              ((Type)_p).getStatus()...
 * def variable             myVar?.getAsString()               _def_myVar?.getAsString()
 * Parenthesized            (expr as String).trim()            h.toStr(...).trim()
 * String concat            "${meta.service}:${meta.endpoint}" "" + ... + ":" + ...
 * Arithmetic (int)         (a as Integer) + (b as Integer)    Integer.valueOf(h.toInt(..) + h.toInt(..))
 * Arithmetic (mixed)       (a as Integer) * (b as Long)       Long.valueOf((long) h.toInt(..) * h.toLong(..))
 * }</pre>
 *
 * <p>Condition codegen ({@link #generateCondition}) handles comparisons ({@code ==}, {@code !=}, {@code <},
 * {@code >}), logical operators, negation and expression-as-boolean.
 *
 * <p>Sibling implementation: {@code LALValueCodegen} in {@code log-analyzer} compiles the same expression grammar
 * for LAL. The two are deliberately separate copies so that no change here can reach log parsing, which means a
 * fix to a shared defect has to be applied in both. Check the sibling before concluding a bug is TAL-only.
 */
public final class TALValueCodegen {
    private TALValueCodegen() {
        // utility class
    }

    // ==================== Conditions ====================

    /**
     * Generates code for an {@code if} condition expression: a comparison, a logical combination, a negation, or
     * a bare expression read for its truth.
     *
     * <p>A comparison is decided by the static types of its two operands, see {@link #generateComparison}. A bare
     * expression is true when it is present and not empty, or, for a boolean, when it is true.
     */
    public static void generateCondition(final StringBuilder sb,
                                   final TALCondition cond,
                                   final TALGenCtx genCtx) {
        if (cond instanceof TALComparisonCondition) {
            generateComparison(sb, (TALComparisonCondition) cond, genCtx);
        } else if (cond instanceof TALLogicalCondition) {
            final TALLogicalCondition lc =
                (TALLogicalCondition) cond;
            sb.append("(");
            generateCondition(sb, lc.getLeft(), genCtx);
            sb.append(lc.getOp() == LogicalOp.AND
                ? " && " : " || ");
            generateCondition(sb, lc.getRight(), genCtx);
            sb.append(")");
        } else if (cond instanceof TALNotCondition) {
            sb.append("!(");
            generateCondition(sb,
                ((TALNotCondition) cond).getInner(), genCtx);
            sb.append(")");
        } else if (cond instanceof TALExprCondition) {
            final String ct = ((TALExprCondition) cond).getCastType();
            final TALValueAccess expr = ((TALExprCondition) cond).getExpr();
            final String literal = booleanOrNullLiteral(expr);
            if (literal != null) {
                sb.append("null".equals(literal) ? "false" : literal);
                return;
            }
            // A built-in that already returns boolean is the condition. Wrapping it would hand a primitive to
            // an Object overload, which Javassist compiles without autoboxing and the verifier then rejects.
            if (ct == null && expr.getFunctionCallName() != null && expr.getChain().isEmpty()
                    && genCtx.resolver.functionReturnType(expr.getFunctionCallName()) == ExprType.BOOLEAN) {
                generateValueAccess(sb, expr, genCtx);
                return;
            }
            final String method = "Boolean".equals(ct) || "boolean".equals(ct)
                ? ".isTrue(" : ".isNotEmpty(";
            sb.append("h").append(method);
            generateValueAccessObj(sb, expr, ct, genCtx);
            sb.append(")");
        }
    }

    // ==================== Typed comparisons ====================

    /**
     * What a comparison operand is at compile time. A comparison is legal for a pair of kinds only, see
     * {@link #generateComparison}.
     */
    private enum OperandKind {
        NUMBER, BOOLEAN, STRING, OBJECT, RUNTIME, NULL
    }

    /**
     * One side of a comparison, rendered as a boxed Java expression.
     */
    private static final class Operand {
        private final String expr;
        private final OperandKind kind;
        /**
         * The static class for an {@code OBJECT}, so two objects compare only when they are of one type.
         */
        private final Class<?> type;
        /**
         * Whether the expression can be null, which a primitive read cannot.
         */
        private final boolean nullable;
        private final boolean stringLiteral;
        private final String source;

        private Operand(final String expr, final OperandKind kind, final Class<?> type, final boolean nullable,
                        final boolean stringLiteral, final String source) {
            this.expr = expr;
            this.kind = kind;
            this.type = type;
            this.nullable = nullable;
            this.stringLiteral = stringLiteral;
            this.source = source;
        }

        private String describe() {
            switch (kind) {
                case NUMBER:
                    return "a number";
                case BOOLEAN:
                    return "a boolean";
                case STRING:
                    return "a String";
                case OBJECT:
                    return "a " + type.getSimpleName();
                case NULL:
                    return "null";
                case RUNTIME:
                default:
                    return "a value whose type is known only at runtime";
            }
        }
    }

    /**
     * A comparison, decided by the static types of its two operands; there is no implicit parse and no implicit
     * {@code toString}, and {@code as T} is the only conversion.
     *
     * <ul>
     *   <li>number and number: any operator, a primitive comparison with Java's promotion;</li>
     *   <li>boolean and boolean, String and String, two objects of one type: {@code ==} and {@code !=} through
     *       {@code Objects.equals};</li>
     *   <li>a reference and {@code null}: {@code ==} and {@code !=} as a null check;</li>
     *   <li>a value whose type is known only at runtime and a String literal: {@code ==} and {@code !=};</li>
     *   <li>anything else: a compile error naming both types.</li>
     * </ul>
     *
     * <p>An {@code as T} operand that cannot be converted makes that comparison false, whatever the operator;
     * {@code !(x as Integer >= 400)} is therefore true for a value that is not a number.
     */
    private static void generateComparison(final StringBuilder sb,
                                           final TALComparisonCondition cc,
                                           final TALGenCtx genCtx) {
        final Operand left = operand(cc.getLeft(), cc.getLeftCast(), genCtx);
        final Operand right = rightOperand(cc.getRight(), genCtx);
        final CompareOp op = cc.getOp();
        final boolean equality = op == CompareOp.EQ || op == CompareOp.NEQ;

        if (left.kind == OperandKind.NULL || right.kind == OperandKind.NULL) {
            final Operand other = left.kind == OperandKind.NULL ? right : left;
            if (!equality || other.kind == OperandKind.NULL || !other.nullable) {
                throw incomparable(left, op, right, "only a value that can be absent compares with null, by == or !=");
            }
            sb.append("(").append(other.expr).append(op == CompareOp.EQ ? " == null)" : " != null)");
            return;
        }
        if (left.kind == OperandKind.NUMBER && right.kind == OperandKind.NUMBER) {
            sb.append("h.compareNumbers(").append(left.expr).append(", ").append(right.expr).append(", ")
              .append(op.ordinal()).append(")");
            return;
        }
        final boolean sameKind = left.kind == right.kind
            && (left.kind == OperandKind.BOOLEAN || left.kind == OperandKind.STRING
                || left.kind == OperandKind.OBJECT && left.type.equals(right.type));
        final boolean runtimeWithLiteral =
            left.kind == OperandKind.RUNTIME && right.stringLiteral
                || right.kind == OperandKind.RUNTIME && left.stringLiteral;
        if (!sameKind && !runtimeWithLiteral) {
            throw incomparable(left, op, right, "convert one side with `as T`");
        }
        if (!equality) {
            throw incomparable(left, op, right, "only numbers are ordered; convert with `as T`");
        }
        sb.append("h.sameValue(").append(left.expr).append(", ").append(right.expr).append(", ")
          .append(op == CompareOp.EQ).append(")");
    }

    private static IllegalArgumentException incomparable(final Operand left, final CompareOp op,
                                                         final Operand right, final String hint) {
        return new IllegalArgumentException(
            "cannot compare " + left.describe() + " with " + right.describe() + " in `" + left.source + " "
                + symbolOf(op) + " " + right.source + "`: " + hint);
    }

    private static String symbolOf(final CompareOp op) {
        switch (op) {
            case EQ:
                return "==";
            case NEQ:
                return "!=";
            case GT:
                return ">";
            case LT:
                return "<";
            case GTE:
                return ">=";
            case LTE:
            default:
                return "<=";
        }
    }

    private static Operand rightOperand(final TALConditionValue cv, final TALGenCtx genCtx) {
        if (cv instanceof TALStringConditionValue) {
            final String text = ((TALStringConditionValue) cv).getValue();
            return new Operand("\"" + DslJavaSourceText.toLiteral(text) + "\"", OperandKind.STRING, String.class,
                true, true, "\"" + text + "\"");
        }
        if (cv instanceof TALNumberConditionValue) {
            final TALNumberConditionValue ncv = (TALNumberConditionValue) cv;
            final String literal = ncv.getLiteral() != null ? ncv.getLiteral() : String.valueOf(ncv.getValue());
            final NumericLiteral parsed = NumericLiteral.parse(literal);
            return new Operand(parsed.type.wrapperName + ".valueOf(" + parsed.javaText + ")", OperandKind.NUMBER,
                parsed.type.primitiveClass, false, false, literal);
        }
        if (cv instanceof TALBoolConditionValue) {
            final boolean value = ((TALBoolConditionValue) cv).isValue();
            return new Operand(value ? "Boolean.TRUE" : "Boolean.FALSE", OperandKind.BOOLEAN, boolean.class,
                false, false, String.valueOf(value));
        }
        if (cv instanceof TALNullConditionValue) {
            return new Operand("null", OperandKind.NULL, null, true, false, "null");
        }
        final TALValueAccessConditionValue vacv = (TALValueAccessConditionValue) cv;
        return operand(vacv.getValue(), vacv.getCastType(), genCtx);
    }

    /**
     * Render one operand boxed and classify it. An {@code as T} converts through a helper that answers a failure
     * marker instead of throwing, which the comparison helpers read as false.
     */
    private static Operand operand(final TALValueAccess value, final String cast, final TALGenCtx genCtx) {
        final String source = value.toPathString() + (cast == null ? "" : " as " + cast);
        if (cast == null && value.getParenInner() != null && value.getParenCast() != null
            && value.getChain().isEmpty() && value.getConcatParts().isEmpty()) {
            // `(x as Integer)` on its own is the same conversion as `x as Integer`.
            return operand(value.getParenInner(), value.getParenCast(), genCtx);
        }
        final ExprType castType = castToType(cast);
        if (castType.isNumeric() || castType == ExprType.BOOLEAN) {
            final StringBuilder inner = new StringBuilder();
            generateValueAccessObj(inner, value, null, genCtx);
            final String helper = castType == ExprType.BOOLEAN ? "h.asBoolean(" : "h.as" + castType.wrapperName + "(";
            return new Operand(helper + inner + ")",
                castType == ExprType.BOOLEAN ? OperandKind.BOOLEAN : OperandKind.NUMBER,
                castType == ExprType.BOOLEAN ? Boolean.class : castType.primitiveClass, false, false, source);
        }
        if ("null".equals(booleanOrNullLiteral(value))) {
            return new Operand("null", OperandKind.NULL, null, true, false, source);
        }
        final StringBuilder rendered = new StringBuilder();
        generateValueAccessObj(rendered, value, castType == ExprType.STRING ? "String" : null, genCtx);
        final Class<?> type = castType == ExprType.STRING ? String.class : genCtx.lastResolvedType;
        final boolean literal = value.isStringLiteral() && value.getChain().isEmpty() && cast == null;
        if (type == null || type == Object.class) {
            return new Operand(rendered.toString(), OperandKind.RUNTIME, null, true, literal, source);
        }
        if (type.isPrimitive()) {
            final String box = TALCodegenHelper.boxTypeName(type);
            // A primitive read is boxed for the helpers; one read through a null-guarded chain is already boxed.
            final String expr = genCtx.lastRawChain != null && genCtx.lastNullChecks == null && box != null
                ? box + ".valueOf(" + genCtx.lastRawChain + ")" : rendered.toString();
            final boolean guarded = genCtx.lastNullChecks != null;
            return new Operand(expr, type == boolean.class ? OperandKind.BOOLEAN : OperandKind.NUMBER, type,
                guarded, false, source);
        }
        final OperandKind kind = Number.class.isAssignableFrom(type) ? OperandKind.NUMBER
            : type == Boolean.class ? OperandKind.BOOLEAN
            : type == String.class ? OperandKind.STRING
            : OperandKind.OBJECT;
        return new Operand(rendered.toString(), kind, type, true, literal, source);
    }

    /**
     * Emit a primitive widening cast on an already-primitive expression, only
     * when the source type is narrower than the target.
     */
    private static String widenPrimitiveExpr(final String expr,
                                              final ExprType from,
                                              final ExprType to) {
        if (from == to || promote(from, to) == from) {
            return expr;
        }
        return "(" + to.primitiveKeyword + ") " + expr;
    }

    /**
     * Wrap an Object-typed operand of arithmetic in the {@code h.toX()} helper of its numeric type.
     */
    private static String wrapAsPrimitive(final String objectExpr, final ExprType target) {
        final String helper = target.runtimeHelper != null && target.isNumeric()
            ? target.runtimeHelper : "h.toLong";
        return helper + "(" + objectExpr + ")";
    }

    private static boolean isNumericPrimitiveClass(final Class<?> c) {
        return c == int.class || c == long.class
            || c == float.class || c == double.class;
    }

    // ==================== Value access ====================

    /**
     * Generates a value access with an explicit type cast.
     *
     * <p>TAL: {@code meta.latency as Long}
     * <br>Generated: {@code h.toLong(h.mapVal("latency"))}
     *
     * <p>Supported cast types: String, Long, Integer, Boolean.
     */
    public static void generateCastedValueAccess(final StringBuilder sb,
                                           final TALValueAccess value,
                                           final String castType,
                                           final TALGenCtx genCtx) {
        // Each numeric cast produces a primitive expression; record the type
        // and the raw chain so callers (numeric comparisons, arithmetic
        // codegen) can skip a redundant h.toX() wrapper when the operand
        // already matches the target primitive.
        final ExprType castET = castToType(castType);
        if (castET.runtimeHelper == null) {
            // FQCN or no cast — emit the raw value access.
            generateValueAccess(sb, value, genCtx);
            return;
        }
        // Skip h.toStr(<string-literal>) — it's a no-op.
        if (castET == ExprType.STRING && isPlainStringLiteral(value)) {
            generateValueAccess(sb, value, genCtx);
            return;
        }
        final int start = sb.length();
        sb.append(castET.runtimeHelper).append("(");
        generateValueAccess(sb, value, genCtx);
        sb.append(")");
        // Numeric casts produce a primitive — capture the raw chain so the
        // caller can avoid the redundant unbox wrapper.
        if (castET.isNumeric()) {
            recordPrimitiveResult(genCtx, castET.primitiveClass, sb, start);
        }
    }

    private static void recordPrimitiveResult(final TALGenCtx genCtx,
                                                final Class<?> type,
                                                final StringBuilder sb,
                                                final int start) {
        genCtx.lastResolvedType = type;
        genCtx.lastRawChain = sb.substring(start);
        genCtx.lastNullChecks = null;
    }

    /**
     * Generates a value access that must produce a String result (used by
     * tag assignments). For numeric / boolean casts wraps the conversion
     * helper with {@code String.valueOf(...)}; for String (or unspecified)
     * wraps with {@code h.toStr(...)} — except when the value is already
     * a Java {@code String} literal, in which case the wrapper is elided
     * since {@code h.toStr("llm")} just returns {@code "llm"}.
     */
    public static void generateStringValueAccess(final StringBuilder sb,
                                            final TALValueAccess value,
                                            final String castType,
                                            final TALGenCtx genCtx) {
        final ExprType castET = castToType(castType);
        if (castET.isNumeric() || castET == ExprType.BOOLEAN) {
            // String.valueOf(h.toX(value)) — primitive conversion then
            // stringify. Drives off the runtime-helper field on ExprType
            // instead of repeating a parallel switch.
            sb.append("String.valueOf(").append(castET.runtimeHelper).append("(");
            generateValueAccess(sb, value, genCtx);
            sb.append("))");
            return;
        }
        if (isPlainStringLiteral(value)) {
            generateValueAccess(sb, value, genCtx);
            return;
        }
        sb.append("h.toStr(");
        generateValueAccess(sb, value, genCtx);
        sb.append(")");
    }

    /**
     * Generates a value access that produces an Object (boxed) result.
     * Only applies String cast; other casts pass through as-is. Skips
     * the {@code h.toStr()} wrapper when the value is already a Java
     * String literal.
     */
    public static void generateValueAccessObj(final StringBuilder sb,
                                        final TALValueAccess value,
                                        final String castType,
                                        final TALGenCtx genCtx) {
        if ("String".equals(castType) && !isPlainStringLiteral(value)) {
            sb.append("h.toStr(");
            generateValueAccess(sb, value, genCtx);
            sb.append(")");
        } else {
            generateValueAccess(sb, value, genCtx);
        }
    }

    /**
     * True when the operand is a bare Java String literal (no chain,
     * no concat, no paren wrapping). For these the value-access codegen
     * already emits a {@code String}, so a {@code h.toStr()} wrapper
     * adds nothing.
     */
    private static boolean isPlainStringLiteral(final TALValueAccess value) {
        return value.isStringLiteral()
            && value.getChain().isEmpty()
            && value.getConcatParts().isEmpty()
            && value.getParenInner() == null;
    }

    /**
     * Central dispatch for all TAL value-access expressions. Routes each
     * expression to the appropriate codegen path based on its AST type.
     *
     * <p>Dispatch order:
     * <ol>
     *   <li>String concatenation ({@code "..." + expr + "..."})</li>
     *   <li>Parenthesized expression ({@code (expr as Type).chain})</li>
     *   <li>Standalone {@code tag("KEY")} function call</li>
     *   <li>String/number literals</li>
     *   <li>{@code ProcessRegistry.method(...)} static calls</li>
     *   <li>{@code log.X.Y} direct proto getter chains</li>
     *   <li>{@code parsed.X.Y} with compile-time type analysis</li>
     *   <li>{@code def} variable reference with method chain</li>
     *   <li>Fallback: compile-time error</li>
     * </ol>
     *
     * <p>Note: standalone {@code tag("KEY")} and {@code parsed.tag("KEY")}
     * are different AST structures. {@code tag("KEY")} is a top-level
     * function call handled here; {@code parsed.tag("KEY")} is a
     * {@code parsed}-prefixed chain where {@code tag("KEY")} appears as a
     * MethodSegment, handled by the resolver's input-root access.
     */
    public static void generateValueAccess(final StringBuilder sb,
                                     final TALValueAccess value,
                                     final TALGenCtx genCtx) {
        genCtx.clearExtraLogResult();

        // Handle binary expressions (term1 OP term2 OP ...). The decision is
        // delegated to generateBinaryExpression, which performs JLS-style type
        // inference and emits either Java arithmetic, string concatenation, or a
        // compile-time error.
        if (!value.getConcatParts().isEmpty()) {
            generateBinaryExpression(sb, value.getConcatParts(),
                value.getConcatOps(), genCtx);
            return;
        }

        // Handle parenthesized expression: (innerExpr as Type).chain...
        if (value.getParenInner() != null) {
            generateParenAccess(sb, value, genCtx);
            return;
        }

        // Built-in function calls are TAL's own vocabulary: attr(), resource(), component() and the rest.
        // The resolver claims the name or lets the generic paths below continue.
        if (value.getFunctionCallName() != null) {
            final StringBuilder call = new StringBuilder();
            if (genCtx.resolver.generateFunctionCall(call, value, genCtx)) {
                final ExprType returns = genCtx.resolver.functionReturnType(value.getFunctionCallName());
                final boolean returnsString = returns == ExprType.STRING;
                if (value.getChain().isEmpty()) {
                    sb.append(call);
                    // The built-in's declared type, so a comparison or arithmetic around it is typed; a primitive
                    // result is left raw and its raw form recorded for the callers that box or widen it.
                    if (returnsString) {
                        genCtx.lastResolvedType = String.class;
                    } else if (returns != null && returns.primitiveClass != null) {
                        genCtx.lastResolvedType = returns.primitiveClass;
                        genCtx.lastRawChain = call.toString();
                    } else if (returns == ExprType.BOOLEAN) {
                        genCtx.lastResolvedType = boolean.class;
                        genCtx.lastRawChain = call.toString();
                    }
                    return;
                }
                // `attr("x").trim()`: the methods after the call are resolved on what it returns.
                if (!returnsString) {
                    throw new IllegalArgumentException(
                        "only a built-in that returns a string can be followed by a method or field; "
                            + value.getFunctionCallName() + "() does not");
                }
                TALDefCodegen.generateDefVarChain(
                    sb, new TALLocalVar(call.toString(), String.class), value.getChain(), genCtx);
                return;
            }
        }

        // Handle string/number literals
        if (value.isStringLiteral() && value.getChain().isEmpty()) {
            sb.append("\"").append(DslJavaSourceText.toLiteral(value.getSegments().get(0)))
              .append("\"");
            genCtx.lastResolvedType = String.class;
            return;
        }
        final String literal = booleanOrNullLiteral(value);
        if (literal != null) {
            if ("null".equals(literal)) {
                sb.append("null");
            } else {
                sb.append("Boolean.").append(literal.toUpperCase());
                genCtx.lastResolvedType = Boolean.class;
            }
            return;
        }
        if (value.isNumberLiteral() && value.getChain().isEmpty()) {
            final String num = value.getSegments().get(0);
            final NumericLiteral lit = NumericLiteral.parse(num);
            // The literal's own type, so `def n = 1` declares a number and `n + 2` adds rather than concatenates.
            genCtx.lastResolvedType = lit.type.primitiveClass;
            genCtx.lastRawChain = lit.javaText;
            switch (lit.type) {
                case DOUBLE:
                    sb.append("Double.valueOf(").append(lit.javaText).append(")");
                    break;
                case FLOAT:
                    sb.append("Float.valueOf(").append(lit.javaText).append(")");
                    break;
                case LONG:
                    sb.append("Long.valueOf(").append(lit.javaText).append(")");
                    break;
                case INT:
                default:
                    sb.append("Integer.valueOf(").append(lit.javaText).append(")");
                    break;
            }
            return;
        }

        if (value.isStaticRef() && genCtx.resolver.generateStaticRefAccess(sb, value, genCtx)) {
            return;
        }

        final List<? extends TALValueAccessSegment> chain = value.getChain();

        if (value.isMetaRef()) {
            genCtx.resolver.generateMetaRootAccess(sb, chain, genCtx);
            return;
        }

        if (value.isInputRef()) {
            genCtx.resolver.generateInputRootAccess(sb, chain, genCtx);
            return;
        }

        // Check for def variable reference
        if (!value.getSegments().isEmpty()) {
            final String primaryName = value.getSegments().get(0);
            final TALLocalVar localVar =
                genCtx.localVars.get(primaryName);
            if (localVar != null) {
                TALDefCodegen.generateDefVarChain(sb, localVar, chain, genCtx);
                return;
            }
        }

        // No matching codegen path — fail at compile time instead of silently generating null. A chain whose
        // first name is none of the roots is a misspelling such as `mta.kind`, not a field of the input.
        final String desc = value.getFunctionCallName() != null
            ? "function call '" + value.getFunctionCallName() + "(...)'"
            : "'" + value.toPathString() + "'";
        throw new IllegalArgumentException(
            "Cannot resolve " + desc + ": an expression starts with span, meta, a def variable, a function or a "
                + "literal. " + genCtx.resolver.describeContext());
    }

    /**
     * @return {@code "true"}, {@code "false"} or {@code "null"} when the expression is just that literal, else null
     */
    private static String booleanOrNullLiteral(final TALValueAccess value) {
        if (value.getSegments().size() != 1 || !value.getChain().isEmpty() || value.isStringLiteral()
            || value.isNumberLiteral() || value.getFunctionCallName() != null || value.isMetaRef()
            || value.isInputRef() || value.getParenInner() != null || !value.getConcatParts().isEmpty()) {
            return null;
        }
        final String text = value.getSegments().get(0);
        return "true".equals(text) || "false".equals(text) || "null".equals(text) ? text : null;
    }

    // ==================== Parenthesized expression ====================

    /**
     * Generates code for a parenthesized expression with optional type cast
     * and method chain.
     *
     * <p>TAL: {@code (attr("db.system.name") as String).trim()}
     * <br>Generated: {@code h.toStr(h.mapVal("component")).trim()}
     */
    public static void generateParenAccess(final StringBuilder sb,
                                     final TALValueAccess value,
                                     final TALGenCtx genCtx) {
        // Generate the inner expression with cast
        final String castType = value.getParenCast();
        final StringBuilder inner = new StringBuilder();
        if (castType != null) {
            generateCastedValueAccess(inner, value.getParenInner(), castType, genCtx);
        } else {
            generateValueAccess(inner, value.getParenInner(), genCtx);
        }

        // Apply chain segments (methods, fields, index access)
        String current = inner.toString();
        for (final TALValueAccessSegment seg : value.getChain()) {
            if (seg instanceof TALMethodSegment) {
                current = appendMethodSegment(current,
                    (TALMethodSegment) seg, genCtx);
            } else if (seg instanceof TALIndexSegment) {
                current = current + "["
                    + ((TALIndexSegment) seg).getIndex() + "]";
            } else if (seg instanceof TALFieldSegment) {
                final TALFieldSegment fs =
                    (TALFieldSegment) seg;
                if (fs.isSafeNav()) {
                    current = "(" + current + " == null ? null : "
                        + current + "." + fs.getName() + ")";
                } else {
                    current = current + "." + fs.getName();
                }
            }
        }
        sb.append(current);
    }

    /**
     * Generates a reflection-based getter chain on a typed root object, which is what every DSL root that
     * resolves to a class needs: {@code span?.status?.message} on the OTLP span, {@code meta.latency} on the
     * metadata view.
     *
     * <p>Each getter call is validated at compile time via reflection. Safe
     * navigation ({@code ?.}) generates null checks. Intermediate results
     * are cached in local variables ({@code _t0}, {@code _t1}, ...) to avoid
     * redundant getter calls when the same chain prefix appears multiple times.
     */
    /**
     * The same reflective getter walk as {@link #generateTypedChainAccess}, emitted inline instead of through
     * hoisted variables.
     *
     * <p>Caching a chain into a variable declared at the top of the method is only sound while the root cannot
     * change during the rule. {@code meta} can: an extractor's {@code service}, {@code endpoint} and
     * {@code layer} statements overwrite it, and a cached read would hand later statements the value from before
     * the overwrite.
     *
     * @param fieldSegments the field accesses applied to the root
     * @param rootType      the root's class, walked by reflection so a wrong field name fails at compile
     * @param rootExpr      the Java expression producing the root
     * @param genCtx        the generation context
     * @return the Java expression reading the chain
     */
    public static String generateInlineChainAccess(
            final List<? extends TALFieldSegment> fieldSegments,
            final Class<?> rootType,
            final String rootExpr,
            final TALGenCtx genCtx) {
        if (fieldSegments.isEmpty()) {
            return rootExpr;
        }
        String current = rootExpr;
        Class<?> currentType = rootType;
        for (final TALFieldSegment seg : fieldSegments) {
            final String field = seg.getName();
            final String getterName = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
            Method getter;
            try {
                getter = currentType.getMethod(getterName);
            } catch (NoSuchMethodException e) {
                // A boolean field's accessor is isX(), which is how meta.root reads.
                final String isName = "is" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
                try {
                    getter = currentType.getMethod(isName);
                } catch (NoSuchMethodException e2) {
                    throw new IllegalArgumentException(
                        "Cannot resolve " + currentType.getSimpleName() + "." + getterName + "() for type "
                            + rootType.getName() + ". Check the field path in the TAL rule.");
                }
            }
            current = current + "." + getter.getName() + "()";
            currentType = getter.getReturnType();
        }
        genCtx.lastResolvedType = currentType;
        genCtx.lastRawChain = current;
        genCtx.lastNullChecks = null;
        if (currentType.isPrimitive()) {
            final String boxName = TALCodegenHelper.boxTypeName(currentType);
            return boxName == null ? current : boxName + ".valueOf(" + current + ")";
        }
        return current;
    }

    public static String generateTypedChainAccess(
            final List<? extends TALFieldSegment> fieldSegments,
            final Class<?> rootType,
            final String rootExpr,
            final boolean rootCanBeNull,
            final TALGenCtx genCtx) {
        genCtx.usedProtoAccess = true;

        if (fieldSegments.isEmpty()) {
            return rootExpr;
        }

        final String typeName = rootType.getName();
        final StringBuilder chainKey = new StringBuilder();
        String prevVar = rootExpr;
        Class<?> currentType = rootType;
        boolean prevCanBeNull = rootCanBeNull;

        for (int i = 0; i < fieldSegments.size(); i++) {
            final TALFieldSegment seg = fieldSegments.get(i);
            final String field = seg.getName();
            final String getterName = "get" + Character.toUpperCase(field.charAt(0))
                + field.substring(1);

            final Method getter;
            try {
                getter = currentType.getMethod(getterName);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                    "Cannot resolve getter " + currentType.getSimpleName()
                        + "." + getterName + "() for type "
                        + typeName + ". Check the field path in the TAL rule.");
            }
            final Class<?> returnType = getter.getReturnType();

            if (chainKey.length() > 0) {
                chainKey.append(".");
            }
            chainKey.append(field);
            final String key = chainKey.toString();
            final boolean isLast = i == fieldSegments.size() - 1;

            // Primitive final segment: return inline expression, no variable
            if (isLast && returnType.isPrimitive()) {
                final String rawAccess = prevVar + "." + getterName + "()";
                genCtx.lastResolvedType = returnType;
                genCtx.lastRawChain = rawAccess;
                final String boxName = TALCodegenHelper.boxTypeName(returnType);
                if (seg.isSafeNav() && prevCanBeNull) {
                    genCtx.lastNullChecks = prevVar + " == null";
                    return "(" + prevVar + " == null ? null : "
                        + boxName + ".valueOf(" + rawAccess + "))";
                } else {
                    genCtx.lastNullChecks = null;
                    return boxName + ".valueOf(" + rawAccess + ")";
                }
            }

            // Reuse existing variable (dedup)
            final String existingVar = genCtx.protoVars.get(key);
            if (existingVar != null) {
                prevVar = existingVar;
                currentType = returnType;
                prevCanBeNull = true;
                continue;
            }

            // Create new local variable declaration
            final String newVar = "_t" + genCtx.protoVarCounter++;
            final String returnTypeName = returnType.getName();
            if (seg.isSafeNav() && prevCanBeNull) {
                genCtx.protoVarDecls.append("  ").append(returnTypeName)
                    .append(" ").append(newVar).append(" = ")
                    .append(prevVar).append(" == null ? null : ")
                    .append(prevVar).append(".").append(getterName).append("();\n");
                prevCanBeNull = true;
            } else {
                genCtx.protoVarDecls.append("  ").append(returnTypeName)
                    .append(" ").append(newVar).append(" = ")
                    .append(prevVar).append(".").append(getterName).append("();\n");
                prevCanBeNull = !returnType.isPrimitive();
            }
            genCtx.protoVars.put(key, newVar);
            genCtx.protoLvtVars.add(new String[]{
                newVar, "L" + returnTypeName.replace('.', '/') + ";"
            });

            prevVar = newVar;
            currentType = returnType;
        }

        // Non-primitive final result — null checks are in declarations
        genCtx.lastResolvedType = currentType;
        genCtx.lastRawChain = prevVar;
        genCtx.lastNullChecks = null;
        return prevVar;
    }

    // ==================== Utility methods ====================

    // ==================== Binary expression codegen ====================

    /**
     * Parsed representation of a NUMBER literal token. Carries the inferred
     * Java type and the literal text trimmed/suffixed for direct emission as
     * Java source.
     */
    private static final class NumericLiteral {
        final ExprType type;
        /** Java-source representation, e.g. "10000", "10000L", "1.5", "1.5f". */
        final String javaText;

        private NumericLiteral(final ExprType type, final String javaText) {
            this.type = type;
            this.javaText = javaText;
        }

        static NumericLiteral parse(final String numText) {
            String t = numText;
            char suffix = 0;
            if (!t.isEmpty()) {
                final char last = t.charAt(t.length() - 1);
                if (last == 'L' || last == 'l' || last == 'F' || last == 'f'
                        || last == 'D' || last == 'd') {
                    suffix = last;
                    t = t.substring(0, t.length() - 1);
                }
            }
            final boolean fractional = t.contains(".") || t.contains("e") || t.contains("E");
            switch (suffix) {
                case 'L':
                case 'l':
                    // Magnitude-overflow on an L-suffixed literal is caught
                    // by Javassist with a clear "long literal too large"
                    // diagnostic referencing the same token the user wrote,
                    // so we don't duplicate that check here.
                    return new NumericLiteral(ExprType.LONG, t + "L");
                case 'F':
                case 'f':
                    return new NumericLiteral(ExprType.FLOAT,
                        (fractional ? t : (t + ".0")) + "f");
                case 'D':
                case 'd':
                    return new NumericLiteral(ExprType.DOUBLE,
                        (fractional ? t : (t + ".0")) + "d");
                default:
                    if (fractional) {
                        return new NumericLiteral(ExprType.DOUBLE, t);
                    }
                    // Bare integer literal — INT if it fits, else LONG.
                    // Magnitude overflow beyond `long` is caught by
                    // Javassist when it parses our generated `<digits>L`
                    // token, so we don't pre-validate the long range here.
                    try {
                        Integer.parseInt(t);
                        return new NumericLiteral(ExprType.INT, t);
                    } catch (NumberFormatException ignoredInt) {
                        return new NumericLiteral(ExprType.LONG, t + "L");
                    }
            }
        }
    }

    /**
     * Returns the JLS-style binary numeric promotion result for the two operand
     * types. Both must be numeric; otherwise this is a programmer bug (the
     * caller is responsible for the non-numeric error path).
     */
    private static ExprType promote(final ExprType a, final ExprType b) {
        if (a == ExprType.DOUBLE || b == ExprType.DOUBLE) {
            return ExprType.DOUBLE;
        }
        if (a == ExprType.FLOAT || b == ExprType.FLOAT) {
            return ExprType.FLOAT;
        }
        if (a == ExprType.LONG || b == ExprType.LONG) {
            return ExprType.LONG;
        }
        return ExprType.INT;
    }

    /**
     * Compile-time inference of an operand's Java type. Operates on the same
     * AST that codegen will subsequently emit, so the result is consistent.
     */
    private static ExprType inferType(final TALValueAccess part,
                                       final TALGenCtx genCtx) {
        if (part == null) {
            return ExprType.UNKNOWN;
        }
        if (part.isStringLiteral()) {
            return ExprType.STRING;
        }
        if (part.isNumberLiteral() && part.getChain().isEmpty()) {
            return NumericLiteral.parse(part.getSegments().get(0)).type;
        }
        // (expr as Cast) primary — only trustable when no further chain is applied.
        if (part.getParenInner() != null && part.getChain().isEmpty()) {
            if (part.getParenCast() != null) {
                return castToType(part.getParenCast());
            }
            // Pure grouping (no cast): the parens just preserve precedence.
            return inferType(part.getParenInner(), genCtx);
        }
        // Nested binary expression: recompute via its parts/ops.
        if (!part.getConcatParts().isEmpty()) {
            return inferBinaryType(part.getConcatParts(), part.getConcatOps(), genCtx);
        }
        // def variable with a known resolved type.
        if (!part.getSegments().isEmpty()) {
            final TALLocalVar lv =
                genCtx.localVars.get(part.getSegments().get(0));
            if (lv != null && part.getChain().isEmpty()) {
                return exprTypeFromClass(lv.resolvedType);
            }
        }
        // A built-in's return type is the DSL's to declare; without it the expression would fall back to string
        // concatenation and a numeric built-in could not take part in arithmetic.
        if (part.getFunctionCallName() != null) {
            final ExprType declared = genCtx.resolver.functionReturnType(part.getFunctionCallName());
            if (part.getChain().isEmpty()) {
                if (declared != null) {
                    return declared;
                }
            } else if (declared == ExprType.STRING) {
                // `attr("x").length()`: the methods after a String built-in are resolved on String.
                final Class<?> type = walkChainType(part.getChain(), String.class);
                if (type != null) {
                    return exprTypeFromClass(type);
                }
            }
        }
        // meta.* — a numeric field such as meta.latency is a number, so `meta.latency + 1` adds, and a String
        // field is a String.
        if (part.isMetaRef() && genCtx.resolver.metaRootType() != null) {
            final Class<?> type = walkChainType(part.getChain(), genCtx.resolver.metaRootType());
            if (type != null) {
                return exprTypeFromClass(type);
            }
        }
        // parsed.* — when the rule has a typed inputType (no JSON/YAML/TEXT
        // parser), walk the proto getter chain via reflection so primitive
        // numeric leaves participate in arithmetic and don't fall through
        // to string concat. Mirrors {@link #generateTypedChainAccess} but
        // does not emit code or local variables.
        if (part.isInputRef()
                && genCtx.resolver.isTypedInputActive()
                && genCtx.inputType != null) {
            final Class<?> primitive = walkProtoChainPrimitiveType(
                part.getChain(), genCtx.inputType);
            if (primitive != null) {
                final ExprType t = exprTypeFromClass(primitive);
                // Only echo back primitive numeric / boolean leaves —
                // non-primitive return types fall through to UNKNOWN
                // (handled by the original chain-walk codegen at runtime).
                if (t.isNumeric() || t == ExprType.BOOLEAN) {
                    return t;
                }
            }
        }
        return ExprType.UNKNOWN;
    }

    /**
     * Map a Java {@link Class} (primitive or wrapper) to the matching
     * {@link ExprType}. Used when type information arrives as a Class —
     * from {@code def} variable resolution or proto-chain reflection —
     * and replaces what was two parallel if/else ladders.
     */
    private static ExprType exprTypeFromClass(final Class<?> c) {
        if (c == int.class || c == Integer.class) {
            return ExprType.INT;
        }
        if (c == long.class || c == Long.class) {
            return ExprType.LONG;
        }
        if (c == float.class || c == Float.class) {
            return ExprType.FLOAT;
        }
        if (c == double.class || c == Double.class) {
            return ExprType.DOUBLE;
        }
        if (c == String.class) {
            return ExprType.STRING;
        }
        if (c == boolean.class || c == Boolean.class) {
            return ExprType.BOOLEAN;
        }
        return ExprType.OBJECT;
    }

    /**
     * Walk a {@code parsed.*} field-segment chain over the typed proto root
     * via reflection, returning the primitive return type of the final getter
     * if the chain is field-only and ends at a primitive accessor; {@code null}
     * otherwise.
     */
    private static Class<?> walkProtoChainPrimitiveType(
            final List<? extends TALValueAccessSegment> chain,
            final Class<?> rootType) {
        if (chain == null || chain.isEmpty()) {
            return null;
        }
        Class<?> currentType = rootType;
        for (int i = 0; i < chain.size(); i++) {
            final TALValueAccessSegment seg = chain.get(i);
            if (!(seg instanceof TALFieldSegment)) {
                return null;
            }
            final String field = ((TALFieldSegment) seg).getName();
            final String suffix = Character.toUpperCase(field.charAt(0)) + field.substring(1);
            try {
                currentType = currentType.getMethod("get" + suffix).getReturnType();
            } catch (NoSuchMethodException e) {
                // A boolean field's accessor is isX(), as meta.root's is.
                try {
                    currentType = currentType.getMethod("is" + suffix).getReturnType();
                } catch (NoSuchMethodException e2) {
                    return null;
                }
            }
        }
        return currentType.isPrimitive() ? currentType : null;
    }

    /**
     * The static type a chain of field and method segments reaches from a root type, by reflection; null when a
     * segment cannot be resolved, or is an index.
     */
    private static Class<?> walkChainType(final List<? extends TALValueAccessSegment> chain, final Class<?> rootType) {
        Class<?> current = rootType;
        for (final TALValueAccessSegment seg : chain) {
            Method resolved = null;
            if (seg instanceof TALFieldSegment) {
                final String field = ((TALFieldSegment) seg).getName();
                final String suffix = Character.toUpperCase(field.charAt(0)) + field.substring(1);
                for (final String prefix : new String[] {"get", "is"}) {
                    try {
                        resolved = current.getMethod(prefix + suffix);
                        break;
                    } catch (final NoSuchMethodException ignored) {
                        // try the next accessor form
                    }
                }
            } else if (seg instanceof TALMethodSegment) {
                final TALMethodSegment ms = (TALMethodSegment) seg;
                for (final Method m : current.getMethods()) {
                    if (m.getName().equals(ms.getName()) && m.getParameterCount() == ms.getArguments().size()) {
                        resolved = m;
                        break;
                    }
                }
            }
            if (resolved == null) {
                return null;
            }
            current = resolved.getReturnType();
        }
        return current;
    }

    private static ExprType castToType(final String cast) {
        if ("Integer".equals(cast)) {
            return ExprType.INT;
        }
        if ("Long".equals(cast)) {
            return ExprType.LONG;
        }
        if ("Float".equals(cast)) {
            return ExprType.FLOAT;
        }
        if ("Double".equals(cast)) {
            return ExprType.DOUBLE;
        }
        if ("String".equals(cast)) {
            return ExprType.STRING;
        }
        if ("Boolean".equals(cast)) {
            return ExprType.BOOLEAN;
        }
        return ExprType.UNKNOWN;
    }

    /**
     * Walk the operand list applying JLS promotion left-to-right, treating any
     * {@code +} that touches a String operand as string concat (which sticks).
     */
    private static ExprType inferBinaryType(
            final List<? extends TALValueAccess> parts,
            final List<BinaryOp> ops,
            final TALGenCtx genCtx) {
        ExprType acc = inferType(parts.get(0), genCtx);
        for (int i = 1; i < parts.size(); i++) {
            final ExprType rhs = inferType(parts.get(i), genCtx);
            final BinaryOp op = ops.get(i - 1);
            if (op == BinaryOp.PLUS
                    && (acc == ExprType.STRING || rhs == ExprType.STRING)) {
                acc = ExprType.STRING;
                continue;
            }
            if (acc.isNumeric() && rhs.isNumeric()) {
                acc = promote(acc, rhs);
                continue;
            }
            // Anything else for + (object + object) → fallback to string concat
            // (preserves long-standing behaviour for `tag("a") + tag("b")` etc.).
            if (op == BinaryOp.PLUS) {
                acc = ExprType.STRING;
                continue;
            }
            // - * / on non-numeric — caller emits a compile error.
            return ExprType.UNKNOWN;
        }
        return acc;
    }

    /**
     * Top-level binary-expression codegen. Mirrors Java's left-to-right
     * evaluation: adjacent numeric operands accumulate into a primitive
     * arithmetic expression; once a String operand appears, every subsequent
     * {@code +} becomes string concatenation. This preserves the semantic
     * difference between {@code 1 + 2 + "x"} (= {@code "3x"}) and
     * {@code "" + 1 + 2 + "x"} (= {@code "12x"}).
     */
    private static void generateBinaryExpression(
            final StringBuilder sb,
            final List<? extends TALValueAccess> parts,
            final List<BinaryOp> ops,
            final TALGenCtx genCtx) {
        // Render the first operand and seed the accumulator with its type.
        ExprType accType = inferType(parts.get(0), genCtx);
        final StringBuilder accBuf = new StringBuilder();
        appendOperandRaw(accBuf, parts.get(0), accType, genCtx);
        String accExpr = accBuf.toString();

        for (int i = 1; i < parts.size(); i++) {
            final BinaryOp op = ops.get(i - 1);
            final ExprType rhsType = inferType(parts.get(i), genCtx);
            final StringBuilder rhsBuf = new StringBuilder();
            appendOperandRaw(rhsBuf, parts.get(i), rhsType, genCtx);
            final String rhsExpr = rhsBuf.toString();

            if (accType.isNumeric() && rhsType.isNumeric()) {
                final ExprType promoted = promote(accType, rhsType);
                final String widenedAcc = widenPrimitiveExpr(accExpr, accType, promoted);
                final String widenedRhs = widenPrimitiveExpr(rhsExpr, rhsType, promoted);
                accExpr = "(" + widenedAcc + " " + op.symbol() + " " + widenedRhs + ")";
                accType = promoted;
                continue;
            }
            if (op != BinaryOp.PLUS) {
                throw new IllegalArgumentException(
                    "Operator '" + op.symbol() + "' requires numeric operands; "
                        + "got non-numeric expression. Cast operands with "
                        + "'as Integer/Long/Float/Double' to enable arithmetic.");
            }
            // String concat path. Java's `+` only accepts the chain when
            // at least one operand is statically a String; otherwise (e.g.
            // `int + Object` or `Object + Object`) the source won't even
            // compile. Prepend a leading `""` whenever neither side is
            // statically String at the transition point. The numeric
            // arithmetic prefix is already in parens, so `("" + (1 + 2))`
            // still computes the sum first ("3"), preserving the
            // semantics of `1 + 2 + parsed.x` (= "3<obj>").
            if (accType != ExprType.STRING && rhsType != ExprType.STRING) {
                accExpr = "\"\" + " + accExpr;
            }
            accExpr = accExpr + " + " + rhsExpr;
            accType = ExprType.STRING;
        }

        if (accType.isNumeric()) {
            // Expose the primitive form so the caller (numeric comparison)
            // can avoid h.toX() unboxing; emit a boxed form into sb for
            // Object contexts (tag assignments, def initialisers, ...).
            genCtx.lastResolvedType = accType.primitiveClass;
            genCtx.lastRawChain = accExpr;
            genCtx.lastNullChecks = null;
            sb.append(accType.wrapperName).append(".valueOf(").append(accExpr).append(")");
        } else {
            // Non-numeric end state: a String concatenation. Update the
            // genCtx metadata explicitly — otherwise a stale numeric
            // resolved type from an inner operand would leak out and
            // mis-type things like `def msg = "count=" + (tag("n") as Integer)`,
            // which the def codegen reads from lastResolvedType to declare
            // the local variable.
            genCtx.lastResolvedType = String.class;
            genCtx.lastRawChain = null;
            genCtx.lastNullChecks = null;
            sb.append(accExpr);
        }
    }

    /**
     * Render a single operand for use inside a binary expression — returning
     * its raw form (primitive expression for numeric operands, Object/String
     * form otherwise). Number literals emit with their declared suffix,
     * paren-cast operands emit through the matching helper, paren-grouping
     * recurses into the inner expression, and nested binary expressions
     * reuse the inner accumulator's raw chain when numeric.
     */
    private static void appendOperandRaw(final StringBuilder sb,
                                          final TALValueAccess part,
                                          final ExprType partType,
                                          final TALGenCtx genCtx) {
        if (part.isNumberLiteral() && part.getChain().isEmpty()) {
            sb.append(NumericLiteral.parse(part.getSegments().get(0)).javaText);
            return;
        }
        if (part.getParenInner() != null && part.getParenCast() != null
                && part.getChain().isEmpty() && partType.isNumeric()) {
            generateCastedValueAccess(sb, part.getParenInner(),
                partType.wrapperName, genCtx);
            return;
        }
        if (part.getParenInner() != null && part.getParenCast() == null
                && part.getChain().isEmpty()) {
            final StringBuilder inner = new StringBuilder();
            generateValueAccessObj(inner, part.getParenInner(), null, genCtx);
            final Class<?> resolved = genCtx.lastResolvedType;
            if (partType.isNumeric() && resolved != null && resolved.isPrimitive()
                    && genCtx.lastRawChain != null) {
                sb.append(genCtx.lastRawChain);
            } else {
                sb.append(inner);
            }
            return;
        }
        if (!part.getConcatParts().isEmpty()) {
            final StringBuilder inner = new StringBuilder();
            generateBinaryExpression(inner, part.getConcatParts(),
                part.getConcatOps(), genCtx);
            if (partType.isNumeric() && genCtx.lastRawChain != null) {
                sb.append(genCtx.lastRawChain);
            } else {
                sb.append(inner);
            }
            return;
        }
        if (partType.isNumeric()) {
            // Render the operand natively, then prefer the primitive raw
            // chain when one is available — typed-proto safe-nav primitive
            // leaves go through generateTypedChainAccess which sets
            // lastRawChain to the unboxed access. Wrapping the boxed
            // null-safe form with `h.toX()` would silently turn a missing
            // chain into 0 (h.toInt(null) = 0) instead of letting the
            // null guard surface — see {@link #generateTypedChainAccess}.
            // Falls back to {@code h.toX(...)} for untyped operands such
            // as bare {@code tag()} or {@code parsed.x} on a JSON parser.
            final StringBuilder buf = new StringBuilder();
            generateValueAccess(buf, part, genCtx);
            if (genCtx.lastRawChain != null
                    && isNumericPrimitiveClass(genCtx.lastResolvedType)) {
                sb.append(widenPrimitiveExpr(genCtx.lastRawChain,
                    exprTypeFromClass(genCtx.lastResolvedType), partType));
            } else {
                sb.append(wrapAsPrimitive(buf.toString(), partType));
            }
        } else {
            generateValueAccess(sb, part, genCtx);
        }
    }


    /**
     * Appends a method call segment to the current expression chain.
     * Handles safe-navigation ({@code ?.method()}) by wrapping in a null
     * guard. Special-cases {@code ?.toString()} and {@code ?.trim()} to
     * use helper methods for null-safe behavior.
     */
    public static String appendMethodSegment(final String current,
                                       final TALMethodSegment ms,
                                       final TALGenCtx genCtx) {
        final String mn = ms.getName();
        final String args = ms.getArguments().isEmpty()
            ? "" : generateMethodArgs(ms.getArguments(), genCtx);
        if (ms.isSafeNav()) {
            // Special-cased helpers for common safe-nav methods on Object
            if ("toString".equals(mn)) {
                return "h.toString(" + current + ")";
            } else if ("trim".equals(mn)) {
                return "h.trim(" + current + ")";
            }
            // General safe-nav: null guard with ternary
            return "(" + current + " == null ? null : "
                + current + "." + mn + "(" + args + "))";
        } else {
            return current + "." + mn + "(" + args + ")";
        }
    }

    /**
     * Generates the argument list for a method call. Handles string literals,
     * number literals, boolean/null literals, and def variable references.
     */
    public static String generateMethodArgs(
            final List<? extends TALFunctionArg> args,
            final TALGenCtx genCtx) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            final TALFunctionArg arg = args.get(i);
            final TALValueAccess va = arg.getValue();
            // Honour the user's declared cast on the argument
            // (`substring(tag("n") as Integer)`) — generateCastedValueAccess
            // emits the matching `h.toX(...)` helper so the call site
            // sees the requested primitive type, allowing Javassist to
            // resolve primitive-arg overloads.
            final String argCast = arg.getCastType();
            if (argCast != null) {
                generateCastedValueAccess(sb, va, argCast, genCtx);
                continue;
            }
            // Binary expression node — happens when the user writes
            // `foo.bar(1 + 2)` etc. Render via the standard value-access
            // path so arithmetic / string-concat / paren grouping all
            // work. When the result is a numeric primitive, use the raw
            // chain form (`(1 + 2)`) instead of the boxed wrapper
            // (`Integer.valueOf(...)`) — Javassist doesn't auto-unbox
            // when matching method-arg overloads, so a primitive-arg
            // method like {@code String.substring(int)} would otherwise
            // fail to resolve.
            if (!va.getConcatParts().isEmpty()) {
                final StringBuilder buf = new StringBuilder();
                generateValueAccess(buf, va, genCtx);
                if (genCtx.lastRawChain != null) {
                    sb.append(genCtx.lastRawChain);
                } else {
                    sb.append(buf);
                }
                continue;
            }
            if (va.isStringLiteral()) {
                sb.append("\"").append(DslJavaSourceText.toLiteral(
                    va.getSegments().get(0))).append("\"");
            } else if (va.isNumberLiteral()) {
                // Normalise the literal text — e.g. `1f` → `1.0f`, `1d`
                // → `1.0` — so the emitted Java is unambiguous.
                sb.append(NumericLiteral.parse(va.getSegments().get(0)).javaText);
            } else if (!va.getSegments().isEmpty()) {
                final String text = va.getSegments().get(0);
                if ("true".equals(text) || "false".equals(text)
                        || "null".equals(text)) {
                    // Boolean or null literal
                    sb.append(text);
                } else if (genCtx != null
                        && genCtx.localVars.containsKey(text)) {
                    // Local def variable reference
                    sb.append(genCtx.localVars.get(text).javaVarName);
                } else if (genCtx != null
                        && (va.isInputRef() || va.isMetaRef()
                            || !va.getChain().isEmpty()
                            || va.getFunctionCallName() != null
                            || va.getParenInner() != null)) {
                    // tag(), parsed.*, log.*, paren-cast, or any value
                    // access with a chain — render via the standard path.
                    generateValueAccess(sb, va, genCtx);
                } else {
                    throw new IllegalArgumentException(
                        "Unknown identifier used as method argument: '" + text + "'");
                }
            } else {
                sb.append("null");
            }
        }
        return sb.toString();
    }
}
