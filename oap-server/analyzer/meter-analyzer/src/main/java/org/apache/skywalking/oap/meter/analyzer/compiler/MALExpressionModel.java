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

package org.apache.skywalking.oap.meter.analyzer.compiler;

import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Immutable AST model for MAL (Meter Analysis Language) expressions.
 *
 * <p>Represents parsed expressions like:
 * <pre>
 *   metric_name.tagEqual("k","v").sum(["tag"]).rate("PT1M").service(["svc"], Layer.GENERAL)
 *   (metric1 + metric2) * 100
 *   metric.tag({tags -> tags.key = "val"}).histogram().histogram_percentile([50,75,90,95,99])
 * </pre>
 */
public final class MALExpressionModel {

    // ==================== Expression nodes ====================

    /**
     * Base interface for all expression AST nodes.
     */
    public interface Expr {
    }

    /**
     * Metric reference with optional method chain:
     * {@code metric_name} or {@code metric_name.sum(["tag"]).rate("PT1M")}
     */
    @Getter
    public static final class MetricExpr implements Expr {
        private final String metricName;
        private final List<MethodCall> methodChain;

        public MetricExpr(final String metricName, final List<MethodCall> methodChain) {
            this.metricName = metricName;
            this.methodChain = Collections.unmodifiableList(methodChain);
        }
    }

    /**
     * Top-level function call: {@code count(metric)}, {@code topN(metric, 10, Order.ASC)}
     */
    @Getter
    public static final class FunctionCallExpr implements Expr {
        private final String functionName;
        private final List<Argument> arguments;
        private final List<MethodCall> methodChain;

        public FunctionCallExpr(final String functionName,
                                final List<Argument> arguments,
                                final List<MethodCall> methodChain) {
            this.functionName = functionName;
            this.arguments = Collections.unmodifiableList(arguments);
            this.methodChain = Collections.unmodifiableList(methodChain);
        }
    }

    /**
     * Parenthesized expression with method chain:
     * {@code (metric * 100).sum(['tag']).rate('PT1M')}
     */
    @Getter
    public static final class ParenChainExpr implements Expr {
        private final Expr inner;
        private final List<MethodCall> methodChain;

        public ParenChainExpr(final Expr inner, final List<MethodCall> methodChain) {
            this.inner = inner;
            this.methodChain = Collections.unmodifiableList(methodChain);
        }
    }

    /**
     * Binary arithmetic: {@code metric1 + metric2}, {@code (metric * 100)}
     */
    @Getter
    public static final class BinaryExpr implements Expr {
        private final Expr left;
        private final ArithmeticOp op;
        private final Expr right;

        public BinaryExpr(final Expr left, final ArithmeticOp op, final Expr right) {
            this.left = left;
            this.op = op;
            this.right = right;
        }
    }

    /**
     * Unary negation: {@code -metric}
     */
    @Getter
    public static final class UnaryNegExpr implements Expr {
        private final Expr operand;

        public UnaryNegExpr(final Expr operand) {
            this.operand = operand;
        }
    }

    /**
     * Numeric literal: {@code 100}, {@code 3.14}
     */
    @Getter
    public static final class NumberExpr implements Expr {
        private final double value;

        public NumberExpr(final double value) {
            this.value = value;
        }
    }

    // ==================== Method calls ====================

    /**
     * A method call in a chain: {@code .sum(["tag"])}, {@code .rate("PT1M")}
     */
    @Getter
    public static final class MethodCall {
        private final String name;
        private final List<Argument> arguments;

        public MethodCall(final String name, final List<Argument> arguments) {
            this.name = name;
            this.arguments = Collections.unmodifiableList(arguments);
        }
    }

    // ==================== Arguments ====================

    /**
     * Base interface for method/function arguments.
     */
    public interface Argument {
    }

    /**
     * Expression argument (metric ref, number, arithmetic).
     */
    @Getter
    public static final class ExprArgument implements Argument {
        private final Expr expr;

        public ExprArgument(final Expr expr) {
            this.expr = expr;
        }
    }

    /**
     * String list: {@code ["tag1", "tag2"]}
     */
    @Getter
    public static final class StringListArgument implements Argument {
        private final List<String> values;

        public StringListArgument(final List<String> values) {
            this.values = Collections.unmodifiableList(values);
        }
    }

    /**
     * Number list: {@code [50, 75, 90, 95, 99]}
     */
    @Getter
    public static final class NumberListArgument implements Argument {
        private final List<Double> values;

        public NumberListArgument(final List<Double> values) {
            this.values = Collections.unmodifiableList(values);
        }
    }

    /**
     * String literal: {@code "PT1M"}, {@code 'command'}
     */
    @Getter
    public static final class StringArgument implements Argument {
        private final String value;

        public StringArgument(final String value) {
            this.value = value;
        }
    }

    /**
     * Boolean literal: {@code true}, {@code false}
     */
    @Getter
    public static final class BoolArgument implements Argument {
        private final boolean value;

        public BoolArgument(final boolean value) {
            this.value = value;
        }
    }

    /**
     * Enum reference: {@code Layer.GENERAL}, {@code K8sRetagType.Pod2Service}
     */
    @Getter
    public static final class EnumRefArgument implements Argument {
        private final String enumType;
        private final String enumValue;

        public EnumRefArgument(final String enumType, final String enumValue) {
            this.enumType = enumType;
            this.enumValue = enumValue;
        }
    }

    /**
     * Closure expression: {@code {tags -> tags.key = "val"}}
     */
    @Getter
    public static final class ClosureArgument implements Argument {
        private final List<String> params;
        private final List<ClosureStatement> body;

        public ClosureArgument(final List<String> params, final List<ClosureStatement> body) {
            this.params = Collections.unmodifiableList(params);
            this.body = Collections.unmodifiableList(body);
        }
    }

    // ==================== Closure statements ====================

    public interface ClosureStatement {
    }

    @Getter
    public static final class ClosureIfStatement implements ClosureStatement {
        private final ClosureCondition condition;
        private final List<ClosureStatement> thenBranch;
        private final List<ClosureStatement> elseBranch;

        public ClosureIfStatement(final ClosureCondition condition,
                                  final List<ClosureStatement> thenBranch,
                                  final List<ClosureStatement> elseBranch) {
            this.condition = condition;
            this.thenBranch = Collections.unmodifiableList(thenBranch);
            this.elseBranch = elseBranch != null
                ? Collections.unmodifiableList(elseBranch) : Collections.emptyList();
        }
    }

    @Getter
    public static final class ClosureReturnStatement implements ClosureStatement {
        private final ClosureExpr value;

        public ClosureReturnStatement(final ClosureExpr value) {
            this.value = value;
        }
    }

    @Getter
    public static final class ClosureAssignment implements ClosureStatement {
        private final String target;
        private final ClosureExpr value;

        public ClosureAssignment(final String target, final ClosureExpr value) {
            this.target = target;
            this.value = value;
        }
    }

    @Getter
    public static final class ClosureExprStatement implements ClosureStatement {
        private final ClosureExpr expr;

        public ClosureExprStatement(final ClosureExpr expr) {
            this.expr = expr;
        }
    }

    // ==================== Closure expressions ====================

    public interface ClosureExpr {
    }

    @Getter
    public static final class ClosureStringLiteral implements ClosureExpr {
        private final String value;

        public ClosureStringLiteral(final String value) {
            this.value = value;
        }
    }

    @Getter
    public static final class ClosureNumberLiteral implements ClosureExpr {
        private final double value;

        public ClosureNumberLiteral(final double value) {
            this.value = value;
        }
    }

    @Getter
    public static final class ClosureBoolLiteral implements ClosureExpr {
        private final boolean value;

        public ClosureBoolLiteral(final boolean value) {
            this.value = value;
        }
    }

    public static final class ClosureNullLiteral implements ClosureExpr {
    }

    /**
     * Method chain in closure: {@code tags.service_name}, {@code tags['key']},
     * {@code tags.service?.trim()}
     */
    @Getter
    public static final class ClosureMethodChain implements ClosureExpr {
        private final String target;
        private final List<ClosureChainSegment> segments;

        public ClosureMethodChain(final String target,
                                  final List<ClosureChainSegment> segments) {
            this.target = target;
            this.segments = Collections.unmodifiableList(segments);
        }
    }

    @Getter
    public static final class ClosureBinaryExpr implements ClosureExpr {
        private final ClosureExpr left;
        private final ArithmeticOp op;
        private final ClosureExpr right;

        public ClosureBinaryExpr(final ClosureExpr left,
                                 final ArithmeticOp op,
                                 final ClosureExpr right) {
            this.left = left;
            this.op = op;
            this.right = right;
        }
    }

    // ==================== Closure chain segments ====================

    public interface ClosureChainSegment {
    }

    @Getter
    public static final class ClosureFieldAccess implements ClosureChainSegment {
        private final String name;
        private final boolean safeNav;

        public ClosureFieldAccess(final String name, final boolean safeNav) {
            this.name = name;
            this.safeNav = safeNav;
        }
    }

    @Getter
    public static final class ClosureMethodCallSeg implements ClosureChainSegment {
        private final String name;
        private final List<ClosureExpr> arguments;
        private final boolean safeNav;

        public ClosureMethodCallSeg(final String name,
                                    final List<ClosureExpr> arguments,
                                    final boolean safeNav) {
            this.name = name;
            this.arguments = Collections.unmodifiableList(arguments);
            this.safeNav = safeNav;
        }
    }

    @Getter
    public static final class ClosureIndexAccess implements ClosureChainSegment {
        private final ClosureExpr index;

        public ClosureIndexAccess(final ClosureExpr index) {
            this.index = index;
        }
    }

    // ==================== Closure conditions ====================

    public interface ClosureCondition extends ClosureExpr {
    }

    @Getter
    public static final class ClosureComparison implements ClosureCondition {
        private final ClosureExpr left;
        private final CompareOp op;
        private final ClosureExpr right;

        public ClosureComparison(final ClosureExpr left,
                                 final CompareOp op,
                                 final ClosureExpr right) {
            this.left = left;
            this.op = op;
            this.right = right;
        }
    }

    @Getter
    public static final class ClosureLogical implements ClosureCondition {
        private final ClosureCondition left;
        private final LogicalOp op;
        private final ClosureCondition right;

        public ClosureLogical(final ClosureCondition left,
                              final LogicalOp op,
                              final ClosureCondition right) {
            this.left = left;
            this.op = op;
            this.right = right;
        }
    }

    @Getter
    public static final class ClosureNot implements ClosureCondition {
        private final ClosureCondition inner;

        public ClosureNot(final ClosureCondition inner) {
            this.inner = inner;
        }
    }

    @Getter
    public static final class ClosureExprCondition implements ClosureCondition {
        private final ClosureExpr expr;

        public ClosureExprCondition(final ClosureExpr expr) {
            this.expr = expr;
        }
    }

    @Getter
    public static final class ClosureInCondition implements ClosureCondition {
        private final ClosureExpr expr;
        private final List<String> values;

        public ClosureInCondition(final ClosureExpr expr, final List<String> values) {
            this.expr = expr;
            this.values = Collections.unmodifiableList(values);
        }
    }

    // ==================== Enums ====================

    public enum ArithmeticOp {
        ADD, SUB, MUL, DIV
    }

    public enum CompareOp {
        EQ, NEQ, GT, LT, GTE, LTE
    }

    public enum LogicalOp {
        AND, OR
    }

    private MALExpressionModel() {
    }
}
