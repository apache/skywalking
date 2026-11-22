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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchyRuleScriptParserTest {

    @Test
    void parseSimpleNameEquality() {
        final HierarchyRuleModel model = HierarchyRuleScriptParser.parse(
            "{ (u, l) -> u.name == l.name }");

        assertEquals("u", model.getUpperParam());
        assertEquals("l", model.getLowerParam());
        assertInstanceOf(HierarchyRuleModel.SimpleComparison.class, model.getBody());

        final HierarchyRuleModel.SimpleComparison cmp =
            (HierarchyRuleModel.SimpleComparison) model.getBody();
        assertEquals(HierarchyRuleModel.CompareOp.EQ, cmp.getOp());

        final HierarchyRuleModel.MethodChainExpr left =
            (HierarchyRuleModel.MethodChainExpr) cmp.getLeft();
        assertEquals("u", left.getTarget());
        assertEquals(1, left.getSegments().size());
        assertEquals("name", left.getSegments().get(0).getName());

        final HierarchyRuleModel.MethodChainExpr right =
            (HierarchyRuleModel.MethodChainExpr) cmp.getRight();
        assertEquals("l", right.getTarget());
        assertEquals("name", right.getSegments().get(0).getName());
    }

    @Test
    void parseShortNameEquality() {
        final HierarchyRuleModel model = HierarchyRuleScriptParser.parse(
            "{ (u, l) -> u.shortName == l.shortName }");

        final HierarchyRuleModel.SimpleComparison cmp =
            (HierarchyRuleModel.SimpleComparison) model.getBody();
        final HierarchyRuleModel.MethodChainExpr left =
            (HierarchyRuleModel.MethodChainExpr) cmp.getLeft();
        assertEquals("shortName", left.getSegments().get(0).getName());
    }

    @Test
    void parseLowerShortNameRemoveNs() {
        // lower-short-name-remove-ns rule
        final String expr =
            "{ (u, l) -> { if(l.shortName.lastIndexOf('.') > 0) "
                + "return u.shortName == l.shortName.substring(0, l.shortName.lastIndexOf('.')); "
                + "return false; } }";

        final HierarchyRuleModel model = HierarchyRuleScriptParser.parse(expr);
        assertEquals("u", model.getUpperParam());
        assertEquals("l", model.getLowerParam());
        assertInstanceOf(HierarchyRuleModel.BlockBody.class, model.getBody());

        final HierarchyRuleModel.BlockBody block =
            (HierarchyRuleModel.BlockBody) model.getBody();
        assertEquals(2, block.getStatements().size());

        // First statement: if
        final HierarchyRuleModel.IfStatement ifStmt =
            (HierarchyRuleModel.IfStatement) block.getStatements().get(0);
        assertInstanceOf(
            HierarchyRuleModel.ComparisonCondition.class, ifStmt.getCondition());
        final HierarchyRuleModel.ComparisonCondition cond =
            (HierarchyRuleModel.ComparisonCondition) ifStmt.getCondition();
        assertEquals(HierarchyRuleModel.CompareOp.GT, cond.getOp());

        // Condition left: l.shortName.lastIndexOf('.')
        final HierarchyRuleModel.MethodChainExpr condLeft =
            (HierarchyRuleModel.MethodChainExpr) cond.getLeft();
        assertEquals("l", condLeft.getTarget());
        assertEquals(2, condLeft.getSegments().size());
        assertEquals("shortName", condLeft.getSegments().get(0).getName());
        assertInstanceOf(
            HierarchyRuleModel.MethodCallSegment.class, condLeft.getSegments().get(1));
        final HierarchyRuleModel.MethodCallSegment lastIndexOf =
            (HierarchyRuleModel.MethodCallSegment) condLeft.getSegments().get(1);
        assertEquals("lastIndexOf", lastIndexOf.getName());
        assertEquals(1, lastIndexOf.getArguments().size());
        assertInstanceOf(
            HierarchyRuleModel.StringLiteralExpr.class, lastIndexOf.getArguments().get(0));
        assertEquals(".",
            ((HierarchyRuleModel.StringLiteralExpr) lastIndexOf.getArguments().get(0)).getValue());

        // Then branch: return u.shortName == l.shortName.substring(0, ...)
        assertEquals(1, ifStmt.getThenBranch().size());
        assertInstanceOf(
            HierarchyRuleModel.ReturnStatement.class, ifStmt.getThenBranch().get(0));

        // Second statement: return false
        final HierarchyRuleModel.ReturnStatement retFalse =
            (HierarchyRuleModel.ReturnStatement) block.getStatements().get(1);
        assertInstanceOf(HierarchyRuleModel.BoolLiteralExpr.class, retFalse.getValue());
        final HierarchyRuleModel.BoolLiteralExpr falseExpr =
            (HierarchyRuleModel.BoolLiteralExpr) retFalse.getValue();
        assertTrue(!falseExpr.isValue());
    }

    @Test
    void parseLowerShortNameWithFqdn() {
        // lower-short-name-with-fqdn rule
        final String expr =
            "{ (u, l) -> { if(u.shortName.lastIndexOf(':') > 0) "
                + "return u.shortName.substring(0, u.shortName.lastIndexOf(':')) "
                + "== l.shortName.concat('.svc.cluster.local'); "
                + "return false; } }";

        final HierarchyRuleModel model = HierarchyRuleScriptParser.parse(expr);
        assertInstanceOf(HierarchyRuleModel.BlockBody.class, model.getBody());

        final HierarchyRuleModel.BlockBody block =
            (HierarchyRuleModel.BlockBody) model.getBody();
        assertEquals(2, block.getStatements().size());

        // Verify the if condition checks u.shortName.lastIndexOf(':') > 0
        final HierarchyRuleModel.IfStatement ifStmt =
            (HierarchyRuleModel.IfStatement) block.getStatements().get(0);
        final HierarchyRuleModel.ComparisonCondition cond =
            (HierarchyRuleModel.ComparisonCondition) ifStmt.getCondition();
        assertEquals(HierarchyRuleModel.CompareOp.GT, cond.getOp());

        // Then branch has a return statement with == comparison
        final HierarchyRuleModel.ReturnStatement retStmt =
            (HierarchyRuleModel.ReturnStatement) ifStmt.getThenBranch().get(0);
        // The return value should be a comparison (u.shortName.substring(...) == l.shortName.concat(...))
        // But since our grammar wraps returns as expressions, check the structure
        assertInstanceOf(HierarchyRuleModel.Expr.class, retStmt.getValue());
    }

    @Test
    void parseSyntaxErrorThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> HierarchyRuleScriptParser.parse("{ invalid }"));
    }

    @Test
    void parseUnrecognizedCharacterThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> HierarchyRuleScriptParser.parse("{ (u, l) -> u.name ` == l.name }"));
    }

    // ==================== Groovy semantics kept, or rejected ====================

    @Test
    void parseElseIfChainKeepsEveryConditionAndBranch() {
        final HierarchyRuleModel.BlockBody block = block(
            "{ (u, l) -> { if (u.name == 'a') return false; else if (u.name == 'b') return false;"
                + " else return true; } }");
        assertEquals(1, block.getStatements().size());

        final HierarchyRuleModel.IfStatement first =
            (HierarchyRuleModel.IfStatement) block.getStatements().get(0);
        assertEquals("a", comparedLiteral(first.getCondition()));
        assertReturnsBool(false, first.getThenBranch());

        assertEquals(1, first.getElseBranch().size());
        final HierarchyRuleModel.IfStatement second =
            (HierarchyRuleModel.IfStatement) first.getElseBranch().get(0);
        assertEquals("b", comparedLiteral(second.getCondition()));
        assertReturnsBool(false, second.getThenBranch());
        assertReturnsBool(true, second.getElseBranch());
    }

    @Test
    void parseElseIfWithoutFinalElseLeavesNoElse() {
        final HierarchyRuleModel.BlockBody block = block(
            "{ (u, l) -> { if (u.name == 'a') return true; else if (u.name == 'b') return true;"
                + " return false; } }");
        assertEquals(2, block.getStatements().size());

        final HierarchyRuleModel.IfStatement first =
            (HierarchyRuleModel.IfStatement) block.getStatements().get(0);
        final HierarchyRuleModel.IfStatement second =
            (HierarchyRuleModel.IfStatement) first.getElseBranch().get(0);
        assertEquals("b", comparedLiteral(second.getCondition()));
        assertReturnsBool(true, second.getThenBranch());
        assertTrue(second.getElseBranch().isEmpty());
    }

    @Test
    void parseEachBranchFromItsOwnChild() {
        final HierarchyRuleModel.BlockBody block = block(
            "{ (u, l) -> { if (u.name == l.name) { return true; } else return false; return false; } }");
        final HierarchyRuleModel.IfStatement ifStmt =
            (HierarchyRuleModel.IfStatement) block.getStatements().get(0);
        assertReturnsBool(true, ifStmt.getThenBranch());
        assertReturnsBool(false, ifStmt.getElseBranch());
    }

    @Test
    void parseKeepsAStringAsWritten() {
        assertEquals("it\\'s", literal("'it\\'s'"));
        assertEquals("a\\\\b\\tc", literal("'a\\\\b\\tc'"));
        assertEquals("plain $ sign", literal("'plain $ sign'"));
    }

    @Test
    void parseGStringInterpolationThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> HierarchyRuleScriptParser.parse("{ (u, l) -> u.name == \"svc-${l.name}\" }"));
        assertThrows(IllegalArgumentException.class,
            () -> HierarchyRuleScriptParser.parse("{ (u, l) -> u.name == \"svc-$l\" }"));
    }

    @Test
    void parseGreaterOrEqualAndLessOrEqual() {
        assertEquals(HierarchyRuleModel.CompareOp.GTE, firstCondition(
            "{ (u, l) -> { if (l.shortName.lastIndexOf('.') >= 0) return true; return false; } }").getOp());
        assertEquals(HierarchyRuleModel.CompareOp.LTE, firstCondition(
            "{ (u, l) -> { if (l.shortName.lastIndexOf('.') <= 0) return true; return false; } }").getOp());
    }

    @Test
    void parseNotBindsTighterThanAndOr() {
        final HierarchyRuleModel.BlockBody block = block(
            "{ (u, l) -> { if (!u.name.isEmpty() && l.name.isEmpty()) return true; return false; } }");
        final HierarchyRuleModel.IfStatement ifStmt =
            (HierarchyRuleModel.IfStatement) block.getStatements().get(0);
        final HierarchyRuleModel.LogicalCondition and =
            (HierarchyRuleModel.LogicalCondition) ifStmt.getCondition();
        assertEquals(HierarchyRuleModel.LogicalOp.AND, and.getOp());
        assertInstanceOf(HierarchyRuleModel.NotCondition.class, and.getLeft());
        assertInstanceOf(HierarchyRuleModel.ExprCondition.class, and.getRight());
    }

    @Test
    void parseNegatedComparisonWithoutParenthesesThrows() {
        // Groovy reads `!u.name == l.name` as `(!u.name) == l.name`.
        assertThrows(IllegalArgumentException.class,
            () -> HierarchyRuleScriptParser.parse(
                "{ (u, l) -> { if (!u.name == l.name) return true; return false; } }"));
        // and `!u.name.length() + 1` as `(!u.name.length()) + 1`
        assertThrows(IllegalArgumentException.class,
            () -> HierarchyRuleScriptParser.parse(
                "{ (u, l) -> { if (!u.name.length() + 1) return true; return false; } }"));
        final HierarchyRuleModel.IfStatement ifStmt = (HierarchyRuleModel.IfStatement) block(
            "{ (u, l) -> { if (!(u.name == l.name)) return true; return false; } }").getStatements().get(0);
        assertInstanceOf(HierarchyRuleModel.NotCondition.class, ifStmt.getCondition());
    }

    @Test
    void parseAdditionAndSubtractionAssociateLeft() {
        final HierarchyRuleModel.SimpleComparison cmp = (HierarchyRuleModel.SimpleComparison)
            HierarchyRuleScriptParser.parse("{ (u, l) -> u.name == 5 - 2 + 1 }").getBody();
        final HierarchyRuleModel.BinaryExpr add = (HierarchyRuleModel.BinaryExpr) cmp.getRight();
        assertEquals(HierarchyRuleModel.ArithmeticOp.ADD, add.getOp());
        final HierarchyRuleModel.BinaryExpr sub = (HierarchyRuleModel.BinaryExpr) add.getLeft();
        assertEquals(HierarchyRuleModel.ArithmeticOp.SUB, sub.getOp());
        assertEquals(5, ((HierarchyRuleModel.NumberLiteralExpr) sub.getLeft()).getValue());
        assertEquals(2, ((HierarchyRuleModel.NumberLiteralExpr) sub.getRight()).getValue());
        assertEquals(1, ((HierarchyRuleModel.NumberLiteralExpr) add.getRight()).getValue());
    }

    @Test
    void parseValueOnTheLineAfterReturnThrows() {
        // Groovy returns null at the line break; `true` below it is never returned.
        assertThrows(IllegalArgumentException.class,
            () -> HierarchyRuleScriptParser.parse("{ (u, l) -> { return\n true } }"));
    }

    @Test
    void parseDuplicateParameterNamesThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> HierarchyRuleScriptParser.parse("{ (u, u) -> u.name == 'x' }"));
    }

    @Test
    void parseIntegerBeyondALongThrows() {
        assertEquals(Long.MAX_VALUE, ((HierarchyRuleModel.NumberLiteralExpr) ((HierarchyRuleModel.SimpleComparison)
            HierarchyRuleScriptParser.parse("{ (u, l) -> u.name == 9223372036854775807 }").getBody()).getRight())
            .getValue());
        for (final String beyond : new String[] {"9223372036854775808", "01000000000000000000000"}) {
            final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> HierarchyRuleScriptParser.parse("{ (u, l) -> u.name == " + beyond + " }"));
            assertTrue(e.getMessage().contains(beyond), e.getMessage());
        }
    }

    @Test
    void parseBlockThatCanFinishWithoutReturnThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> HierarchyRuleScriptParser.parse(
                "{ (u, l) -> { if (u.name == l.name) return true; } }"));
        assertThrows(IllegalArgumentException.class,
            () -> HierarchyRuleScriptParser.parse(
                "{ (u, l) -> { if (u.name == 'a') return true; else if (u.name == 'b') return false; } }"));
        assertThrows(IllegalArgumentException.class,
            () -> HierarchyRuleScriptParser.parse(
                "{ (u, l) -> { if (u.name == 'a') { if (l.name == 'b') return true; } else return false; } }"));
    }

    private static HierarchyRuleModel.BlockBody block(final String expression) {
        return (HierarchyRuleModel.BlockBody) HierarchyRuleScriptParser.parse(expression).getBody();
    }

    private static HierarchyRuleModel.ComparisonCondition firstCondition(final String expression) {
        return (HierarchyRuleModel.ComparisonCondition)
            ((HierarchyRuleModel.IfStatement) block(expression).getStatements().get(0)).getCondition();
    }

    private static String literal(final String quoted) {
        final HierarchyRuleModel.SimpleComparison cmp = (HierarchyRuleModel.SimpleComparison)
            HierarchyRuleScriptParser.parse("{ (u, l) -> u.name == " + quoted + " }").getBody();
        return ((HierarchyRuleModel.StringLiteralExpr) cmp.getRight()).getValue();
    }

    private static String comparedLiteral(final HierarchyRuleModel.Condition condition) {
        final HierarchyRuleModel.ComparisonCondition cmp =
            (HierarchyRuleModel.ComparisonCondition) condition;
        return ((HierarchyRuleModel.StringLiteralExpr) cmp.getRight()).getValue();
    }

    private static void assertReturnsBool(final boolean expected,
                                          final List<HierarchyRuleModel.Statement> branch) {
        assertEquals(1, branch.size());
        final HierarchyRuleModel.ReturnStatement ret = (HierarchyRuleModel.ReturnStatement) branch.get(0);
        assertEquals(expected, ((HierarchyRuleModel.BoolLiteralExpr) ret.getValue()).isValue());
    }
}
