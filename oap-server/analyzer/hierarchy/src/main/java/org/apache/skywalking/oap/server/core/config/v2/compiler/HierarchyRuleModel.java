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

import java.util.Collections;
import java.util.List;
import lombok.Getter;

/**
 * Immutable AST model for hierarchy matching rule expressions.
 * Represents parsed expressions like:
 * <pre>
 *   { (u, l) -> u.name == l.name }
 *   { (u, l) -> { if(l.shortName.lastIndexOf('.') > 0) return ...; return false; } }
 * </pre>
 */
@Getter
public final class HierarchyRuleModel {
    private final String upperParam;
    private final String lowerParam;
    private final RuleBody body;

    private HierarchyRuleModel(final String upperParam, final String lowerParam, final RuleBody body) {
        this.upperParam = upperParam;
        this.lowerParam = lowerParam;
        this.body = body;
    }

    public static HierarchyRuleModel of(final String upperParam, final String lowerParam, final RuleBody body) {
        return new HierarchyRuleModel(upperParam, lowerParam, body);
    }

    /**
     * Rule body — either a simple comparison or a block with if/return statements.
     */
    public interface RuleBody {
    }

    /**
     * Simple comparison body: {@code u.name == l.name}
     */
    @Getter
    public static final class SimpleComparison implements RuleBody, Expr {
        private final Expr left;
        private final CompareOp op;
        private final Expr right;

        public SimpleComparison(final Expr left, final CompareOp op, final Expr right) {
            this.left = left;
            this.op = op;
            this.right = right;
        }
    }

    /**
     * Block body with multiple statements: {@code { if(...) return ...; return false; }}
     */
    @Getter
    public static final class BlockBody implements RuleBody {
        private final List<Statement> statements;

        public BlockBody(final List<Statement> statements) {
            this.statements = Collections.unmodifiableList(statements);
        }
    }

    // ==================== Statements ====================

    public interface Statement {
    }

    @Getter
    public static final class IfStatement implements Statement {
        private final Condition condition;
        private final List<Statement> thenBranch;
        private final List<Statement> elseBranch;

        public IfStatement(final Condition condition,
                           final List<Statement> thenBranch,
                           final List<Statement> elseBranch) {
            this.condition = condition;
            this.thenBranch = Collections.unmodifiableList(thenBranch);
            this.elseBranch = elseBranch != null
                ? Collections.unmodifiableList(elseBranch) : Collections.emptyList();
        }
    }

    @Getter
    public static final class ReturnStatement implements Statement {
        private final Expr value;

        public ReturnStatement(final Expr value) {
            this.value = value;
        }
    }

    // ==================== Conditions ====================

    public interface Condition {
    }

    @Getter
    public static final class ComparisonCondition implements Condition {
        private final Expr left;
        private final CompareOp op;
        private final Expr right;

        public ComparisonCondition(final Expr left, final CompareOp op, final Expr right) {
            this.left = left;
            this.op = op;
            this.right = right;
        }
    }

    @Getter
    public static final class LogicalCondition implements Condition {
        private final Condition left;
        private final LogicalOp op;
        private final Condition right;

        public LogicalCondition(final Condition left, final LogicalOp op, final Condition right) {
            this.left = left;
            this.op = op;
            this.right = right;
        }
    }

    @Getter
    public static final class NotCondition implements Condition {
        private final Condition inner;

        public NotCondition(final Condition inner) {
            this.inner = inner;
        }
    }

    @Getter
    public static final class ExprCondition implements Condition {
        private final Expr expr;

        public ExprCondition(final Expr expr) {
            this.expr = expr;
        }
    }

    // ==================== Expressions ====================

    public interface Expr {
    }

    /**
     * Method chain: {@code u.name}, {@code l.shortName.lastIndexOf('.')},
     * {@code u.shortName.substring(0, l.shortName.lastIndexOf(':'))}
     */
    @Getter
    public static final class MethodChainExpr implements Expr {
        private final String target;
        private final List<ChainSegment> segments;

        public MethodChainExpr(final String target, final List<ChainSegment> segments) {
            this.target = target;
            this.segments = Collections.unmodifiableList(segments);
        }
    }

    @Getter
    public static final class StringLiteralExpr implements Expr {
        private final String value;

        public StringLiteralExpr(final String value) {
            this.value = value;
        }
    }

    @Getter
    public static final class NumberLiteralExpr implements Expr {
        private final long value;

        public NumberLiteralExpr(final long value) {
            this.value = value;
        }
    }

    @Getter
    public static final class BoolLiteralExpr implements Expr {
        private final boolean value;

        public BoolLiteralExpr(final boolean value) {
            this.value = value;
        }
    }

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

    // ==================== Chain segments ====================

    public interface ChainSegment {
        String getName();
    }

    @Getter
    public static final class FieldAccess implements ChainSegment {
        private final String name;

        public FieldAccess(final String name) {
            this.name = name;
        }
    }

    @Getter
    public static final class MethodCallSegment implements ChainSegment {
        private final String name;
        private final List<Expr> arguments;

        public MethodCallSegment(final String name, final List<Expr> arguments) {
            this.name = name;
            this.arguments = Collections.unmodifiableList(arguments);
        }
    }

    // ==================== Enums ====================

    public enum CompareOp {
        EQ, NEQ, GT, LT, GTE, LTE
    }

    public enum LogicalOp {
        AND, OR
    }

    public enum ArithmeticOp {
        ADD, SUB
    }
}
