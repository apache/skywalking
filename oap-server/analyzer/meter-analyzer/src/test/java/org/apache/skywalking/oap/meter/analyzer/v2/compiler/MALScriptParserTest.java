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

import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.BinaryExpr;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ClosureArgument;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.EnumRefArgument;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ExprArgument;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.MetricExpr;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.NumberExpr;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.NumberListArgument;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.ParenChainExpr;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.StringArgument;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALExpressionModel.StringListArgument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MALScriptParserTest {

    @Test
    void parseSimpleMetric() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "instance_golang_heap_alloc");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        assertEquals("instance_golang_heap_alloc", metric.getMetricName());
        assertEquals(0, metric.getMethodChain().size());
    }

    @Test
    void parseMethodChain() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "jvm_memory_bytes_used.sum(['service', 'host_name', 'area'])");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        assertEquals("jvm_memory_bytes_used", metric.getMetricName());
        assertEquals(1, metric.getMethodChain().size());
        assertEquals("sum", metric.getMethodChain().get(0).getName());

        final StringListArgument sl =
            (StringListArgument) metric.getMethodChain().get(0).getArguments().get(0);
        assertEquals(List.of("service", "host_name", "area"), sl.getValues());
    }

    @Test
    void parseTagEqualWithRateAndService() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "mysql_global_status_commands_total"
                + ".tagEqual('command','insert')"
                + ".sum(['service_instance_id','host_name'])"
                + ".rate('PT1M')");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        assertEquals(3, metric.getMethodChain().size());
        assertEquals("tagEqual", metric.getMethodChain().get(0).getName());
        assertEquals("sum", metric.getMethodChain().get(1).getName());
        assertEquals("rate", metric.getMethodChain().get(2).getName());

        // Check tagEqual arguments
        final StringArgument arg0 =
            (StringArgument) metric.getMethodChain().get(0).getArguments().get(0);
        assertEquals("command", arg0.getValue());
        final StringArgument arg1 =
            (StringArgument) metric.getMethodChain().get(0).getArguments().get(1);
        assertEquals("insert", arg1.getValue());
    }

    @Test
    void parseHistogramPercentile() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "metric.sum(['le']).histogram().histogram_percentile([50,75,90,95,99])");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        assertEquals(3, metric.getMethodChain().size());
        assertEquals("histogram", metric.getMethodChain().get(1).getName());
        assertEquals("histogram_percentile", metric.getMethodChain().get(2).getName());

        final NumberListArgument nl =
            (NumberListArgument) metric.getMethodChain().get(2).getArguments().get(0);
        assertEquals(List.of(50.0, 75.0, 90.0, 95.0, 99.0), nl.getValues());
    }

    @Test
    void parseArithmeticAdd() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse("metric1 + metric2");
        assertInstanceOf(BinaryExpr.class, ast);
        final BinaryExpr bin = (BinaryExpr) ast;
        assertEquals(MALExpressionModel.ArithmeticOp.ADD, bin.getOp());
        assertEquals("metric1", ((MetricExpr) bin.getLeft()).getMetricName());
        assertEquals("metric2", ((MetricExpr) bin.getRight()).getMetricName());
    }

    @Test
    void parseArithmeticMultiply() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "(process_cpu_seconds_total * 100).sum(['service', 'host_name']).rate('PT1M')");
        assertInstanceOf(ParenChainExpr.class, ast);
        final ParenChainExpr parenChain = (ParenChainExpr) ast;

        // Inner expression is (metric * 100)
        assertInstanceOf(BinaryExpr.class, parenChain.getInner());
        final BinaryExpr inner = (BinaryExpr) parenChain.getInner();
        assertEquals(MALExpressionModel.ArithmeticOp.MUL, inner.getOp());
        assertEquals("process_cpu_seconds_total", ((MetricExpr) inner.getLeft()).getMetricName());
        assertEquals(100.0, ((NumberExpr) inner.getRight()).getValue());

        // Method chain: .sum(['service', 'host_name']).rate('PT1M')
        assertEquals(2, parenChain.getMethodChain().size());
        assertEquals("sum", parenChain.getMethodChain().get(0).getName());
        assertEquals("rate", parenChain.getMethodChain().get(1).getName());
    }

    @Test
    void parseNumberTimesMetric() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "100 * metrics_aggregation_queue_used_percentage"
                + ".sum(['service', 'host_name', 'level', 'slot'])");
        assertInstanceOf(BinaryExpr.class, ast);
        final BinaryExpr bin = (BinaryExpr) ast;
        assertEquals(MALExpressionModel.ArithmeticOp.MUL, bin.getOp());
        assertInstanceOf(NumberExpr.class, bin.getLeft());
        assertEquals(100.0, ((NumberExpr) bin.getLeft()).getValue());
        assertInstanceOf(MetricExpr.class, bin.getRight());
    }

    @Test
    void parseEnumRefArgument() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "metric.service(['svc'], Layer.GENERAL)");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        final EnumRefArgument enumRef =
            (EnumRefArgument) metric.getMethodChain().get(0).getArguments().get(1);
        assertEquals("Layer", enumRef.getEnumType());
        assertEquals("GENERAL", enumRef.getEnumValue());
    }

    @Test
    void parseDownsampling() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "metric.histogram().histogram_percentile([50,75,90,95,99]).downsampling(SUM)");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        assertEquals(3, metric.getMethodChain().size());
        assertEquals("downsampling", metric.getMethodChain().get(2).getName());
        // SUM is parsed as an expression argument (identifier)
        // In the grammar, it matches additiveExpression -> metric reference
        final ExprArgument exprArg =
            (ExprArgument) metric.getMethodChain().get(2).getArguments().get(0);
        assertInstanceOf(MetricExpr.class, exprArg.getExpr());
        assertEquals("SUM", ((MetricExpr) exprArg.getExpr()).getMetricName());
    }

    @Test
    void parseValueEqual() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "kube_node_status_condition.valueEqual(1).sum(['cluster','node','condition'])");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        assertEquals(2, metric.getMethodChain().size());
        assertEquals("valueEqual", metric.getMethodChain().get(0).getName());
    }

    @Test
    void parseDivTwoMetrics() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "metric_sum.div(metric_count)");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        assertEquals(1, metric.getMethodChain().size());
        assertEquals("div", metric.getMethodChain().get(0).getName());
        final ExprArgument divArg =
            (ExprArgument) metric.getMethodChain().get(0).getArguments().get(0);
        assertInstanceOf(MetricExpr.class, divArg.getExpr());
        assertEquals("metric_count", ((MetricExpr) divArg.getExpr()).getMetricName());
    }

    @Test
    void parseClosureTag() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "metric.tag({tags -> tags.service_name = 'svc1'})");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        assertEquals(1, metric.getMethodChain().size());
        assertEquals("tag", metric.getMethodChain().get(0).getName());

        final ClosureArgument closure =
            (ClosureArgument) metric.getMethodChain().get(0).getArguments().get(0);
        assertEquals(List.of("tags"), closure.getParams());
        assertEquals(1, closure.getBody().size());
    }

    @Test
    void parseRetagByK8sMeta() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "kube_pod_status_phase"
                + ".retagByK8sMeta('service', K8sRetagType.Pod2Service, 'pod', 'namespace')"
                + ".tagNotEqual('service', '')"
                + ".valueEqual(1)"
                + ".sum(['cluster', 'service', 'phase'])");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        assertEquals(4, metric.getMethodChain().size());
        assertEquals("retagByK8sMeta", metric.getMethodChain().get(0).getName());

        // Check K8sRetagType.Pod2Service argument
        final EnumRefArgument enumArg =
            (EnumRefArgument) metric.getMethodChain().get(0).getArguments().get(1);
        assertEquals("K8sRetagType", enumArg.getEnumType());
        assertEquals("Pod2Service", enumArg.getEnumValue());
    }

    @Test
    void parseTagAssignmentExtractsCorrectKey() {
        // Issue: tags.cluster = expr should produce key "cluster", not "tags.cluster"
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "metric.tag({tags -> tags.cluster = 'activemq::' + tags.cluster})");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        final ClosureArgument closure =
            (ClosureArgument) metric.getMethodChain().get(0).getArguments().get(0);
        assertEquals(1, closure.getBody().size());

        final MALExpressionModel.ClosureAssignment assign =
            (MALExpressionModel.ClosureAssignment) closure.getBody().get(0);
        assertEquals("tags", assign.getMapVar());
        // Key should be "cluster", not "tags.cluster"
        assertInstanceOf(MALExpressionModel.ClosureStringLiteral.class, assign.getKeyExpr());
        assertEquals("cluster",
            ((MALExpressionModel.ClosureStringLiteral) assign.getKeyExpr()).getValue());
    }

    @Test
    void parseTagBracketAssignment() {
        // tags[prefix + '_process_id'] = expr
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "metric.tag({prefix, tags -> tags[prefix + '_id'] = 'val'})");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        final ClosureArgument closure =
            (ClosureArgument) metric.getMethodChain().get(0).getArguments().get(0);
        assertEquals(List.of("prefix", "tags"), closure.getParams());

        final MALExpressionModel.ClosureAssignment assign =
            (MALExpressionModel.ClosureAssignment) closure.getBody().get(0);
        assertEquals("tags", assign.getMapVar());
        // Key is a binary expression (prefix + '_id')
        assertInstanceOf(MALExpressionModel.ClosureBinaryExpr.class, assign.getKeyExpr());
    }

    @Test
    void parseForEachClosure() {
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "metric.forEach(['client', 'server'], {prefix, tags -> tags.key = prefix})");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        assertEquals("forEach", metric.getMethodChain().get(0).getName());

        final ClosureArgument closure =
            (ClosureArgument) metric.getMethodChain().get(0).getArguments().get(1);
        assertEquals(List.of("prefix", "tags"), closure.getParams());
    }

    @Test
    void parseVariableDeclaration() {
        // String result = "" — Groovy local variable declaration
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "metric.forEach(['x'], {key, tags ->\n"
            + "  String result = \"\"\n"
            + "  tags[key] = result\n"
            + "})");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        final ClosureArgument closure =
            (ClosureArgument) metric.getMethodChain().get(0).getArguments().get(1);
        assertEquals(2, closure.getBody().size());
        // First statement: variable declaration
        assertInstanceOf(MALExpressionModel.ClosureVarDecl.class, closure.getBody().get(0));
        final MALExpressionModel.ClosureVarDecl vd =
            (MALExpressionModel.ClosureVarDecl) closure.getBody().get(0);
        assertEquals("String", vd.getTypeName());
        assertEquals("result", vd.getVarName());
    }

    @Test
    void parseBareReturn() {
        // return with no expression (void return)
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "metric.forEach(['x'], {prefix, tags ->\n"
            + "  if (tags[prefix + '_id'] != null) {\n"
            + "    return\n"
            + "  }\n"
            + "  tags[prefix + '_id'] = 'default'\n"
            + "})");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        final ClosureArgument closure =
            (ClosureArgument) metric.getMethodChain().get(0).getArguments().get(1);
        // First statement is if, which contains a bare return
        assertInstanceOf(MALExpressionModel.ClosureIfStatement.class, closure.getBody().get(0));
        final MALExpressionModel.ClosureIfStatement ifStmt =
            (MALExpressionModel.ClosureIfStatement) closure.getBody().get(0);
        assertInstanceOf(MALExpressionModel.ClosureReturnStatement.class,
            ifStmt.getThenBranch().get(0));
        final MALExpressionModel.ClosureReturnStatement ret =
            (MALExpressionModel.ClosureReturnStatement) ifStmt.getThenBranch().get(0);
        // Bare return — value should be null
        assertEquals(null, ret.getValue());
    }

    @Test
    void parseStaticMethodCall() {
        // ProcessRegistry.generateVirtualLocalProcess(tags.service, tags.instance)
        final MALExpressionModel.Expr ast = MALScriptParser.parse(
            "metric.tag({tags -> "
            + "tags.pid = ProcessRegistry.generateVirtualLocalProcess(tags.service, tags.instance)"
            + "})");
        assertInstanceOf(MetricExpr.class, ast);
        final MetricExpr metric = (MetricExpr) ast;
        final ClosureArgument closure =
            (ClosureArgument) metric.getMethodChain().get(0).getArguments().get(0);
        final MALExpressionModel.ClosureAssignment assign =
            (MALExpressionModel.ClosureAssignment) closure.getBody().get(0);
        // RHS should be a ClosureMethodChain with target "ProcessRegistry"
        assertInstanceOf(MALExpressionModel.ClosureMethodChain.class, assign.getValue());
        final MALExpressionModel.ClosureMethodChain chain =
            (MALExpressionModel.ClosureMethodChain) assign.getValue();
        assertEquals("ProcessRegistry", chain.getTarget());
        assertEquals(1, chain.getSegments().size());
        assertInstanceOf(MALExpressionModel.ClosureMethodCallSeg.class,
            chain.getSegments().get(0));
    }

    @Test
    void parseSyntaxErrorThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> MALScriptParser.parse("metric.sum("));
    }
}
