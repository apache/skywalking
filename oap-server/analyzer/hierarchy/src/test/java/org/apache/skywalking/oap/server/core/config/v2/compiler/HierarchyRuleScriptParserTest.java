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
}
