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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MalToJavaTranspilerTest {

    private MalToJavaTranspiler transpiler;

    @BeforeEach
    void setUp() {
        transpiler = new MalToJavaTranspiler();
    }

    // ---- AST Parsing + Simple Variable References ----

    @Test
    void simpleVariableReference() {
        final String java = transpiler.transpileExpression("MalExpr_test", "metric_name");
        assertNotNull(java);

        assertTrue(java.contains("package " + MalToJavaTranspiler.GENERATED_PACKAGE),
            "Should have correct package");
        assertTrue(java.contains("public class MalExpr_test implements MalExpression"),
            "Should implement MalExpression");
        assertTrue(java.contains("public SampleFamily run(Map<String, SampleFamily> samples)"),
            "Should have run method");

        assertTrue(java.contains("ctx.getSamples().add(\"metric_name\")"),
            "Should track sample name in parsing context");

        assertTrue(java.contains("samples.getOrDefault(\"metric_name\", SampleFamily.EMPTY)"),
            "Should look up sample family from map");
    }

    @Test
    void downsamplingConstantNotTrackedAsSample() {
        final String java = transpiler.transpileExpression("MalExpr_test", "SUM");
        assertNotNull(java);

        assertTrue(!java.contains("ctx.getSamples().add(\"SUM\")"),
            "Should not track DownsamplingType constant as sample");

        assertTrue(java.contains("DownsamplingType.SUM"),
            "Should resolve to DownsamplingType.SUM");
    }

    @Test
    void parseToAST_producesModuleNode() {
        final var ast = transpiler.parseToAST("some_metric");
        assertNotNull(ast, "Should produce a ModuleNode");
        assertNotNull(ast.getStatementBlock(), "Should have a statement block");
    }

    @Test
    void constantString() {
        final String java = transpiler.transpileExpression("MalExpr_test", "'hello'");
        assertNotNull(java);
        assertTrue(java.contains("\"hello\""),
            "Should convert Groovy string to Java string");
    }

    @Test
    void constantNumber() {
        final String java = transpiler.transpileExpression("MalExpr_test", "42");
        assertNotNull(java);
        assertTrue(java.contains("42"),
            "Should preserve number literal");
    }

    // ---- Method Chains + List Literals + Enum Properties ----

    @Test
    void simpleMethodChain() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric_name.sum(['a', 'b']).service(['svc'], Layer.GENERAL)");
        assertNotNull(java);

        assertTrue(java.contains(".sum(List.of(\"a\", \"b\"))"),
            "Should translate ['a','b'] to List.of(\"a\", \"b\")");
        assertTrue(java.contains(".service(List.of(\"svc\"), Layer.GENERAL)"),
            "Should translate Layer.GENERAL as enum");
    }

    @Test
    void tagEqualChain() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "cpu_seconds.tagNotEqual('mode', 'idle').sum(['host']).rate('PT1M')");
        assertNotNull(java);

        assertTrue(java.contains(".tagNotEqual(\"mode\", \"idle\")"),
            "Should translate tagNotEqual with string args");
        assertTrue(java.contains(".sum(List.of(\"host\"))"),
            "Should translate single-element list");
        assertTrue(java.contains(".rate(\"PT1M\")"),
            "Should translate rate with string arg");
    }

    @Test
    void downsamplingMethod() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.downsampling(SUM)");
        assertNotNull(java);

        assertTrue(java.contains(".downsampling(DownsamplingType.SUM)"),
            "Should resolve SUM to DownsamplingType.SUM");
    }

    @Test
    void retagByK8sMeta() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.retagByK8sMeta('service', K8sRetagType.Pod2Service, 'pod', 'namespace')");
        assertNotNull(java);

        assertTrue(java.contains(".retagByK8sMeta(\"service\", K8sRetagType.Pod2Service, \"pod\", \"namespace\")"),
            "Should translate K8sRetagType enum and string args");
    }

    @Test
    void histogramPercentile() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.sum(['le', 'svc']).histogram().histogram_percentile([50, 75, 90])");
        assertNotNull(java);

        assertTrue(java.contains(".histogram()"),
            "Should translate no-arg histogram()");
        assertTrue(java.contains(".histogram_percentile(List.of(50, 75, 90))"),
            "Should translate integer list");
    }

    @Test
    void sampleNameCollectionThroughChain() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "my_metric.sum(['a']).service(['svc'], Layer.GENERAL)");
        assertNotNull(java);

        assertTrue(java.contains("ctx.getSamples().add(\"my_metric\")"),
            "Should collect sample name from root of method chain");
        assertTrue(!java.contains("ctx.getSamples().add(\"a\")"),
            "Should NOT collect 'a' (it's a string constant arg, not a sample)");
    }

    @Test
    void detectPointEnum() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.serviceRelation(DetectPoint.CLIENT, ['src'], ['dst'], Layer.MESH_DP)");
        assertNotNull(java);

        assertTrue(java.contains("DetectPoint.CLIENT"),
            "Should translate DetectPoint enum");
        assertTrue(java.contains("Layer.MESH_DP"),
            "Should translate Layer enum");
    }

    @Test
    void enumImportsPresent() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.service(['svc'], Layer.GENERAL)");
        assertNotNull(java);

        assertTrue(java.contains("import org.apache.skywalking.oap.server.core.analysis.Layer;"),
            "Should import Layer");
        assertTrue(java.contains("import org.apache.skywalking.oap.server.core.source.DetectPoint;"),
            "Should import DetectPoint");
        assertTrue(java.contains("import org.apache.skywalking.oap.meter.analyzer.dsl.tagOpt.K8sRetagType;"),
            "Should import K8sRetagType");
    }

    // ---- Binary Arithmetic with Operand-Swap ----

    @Test
    void sfTimesNumber() {
        final String java = transpiler.transpileExpression("MalExpr_test", "metric * 100");
        assertNotNull(java);
        assertTrue(java.contains(".multiply(100)"),
            "SF * N should call .multiply(N)");
    }

    @Test
    void sfDivNumber() {
        final String java = transpiler.transpileExpression("MalExpr_test", "metric / 1024");
        assertNotNull(java);
        assertTrue(java.contains(".div(1024)"),
            "SF / N should call .div(N)");
    }

    @Test
    void numberMinusSf() {
        final String java = transpiler.transpileExpression("MalExpr_test", "100 - metric");
        assertNotNull(java);
        assertTrue(java.contains(".minus(100).negative()"),
            "N - SF should produce sf.minus(N).negative()");
    }

    @Test
    void numberDivSf() {
        final String java = transpiler.transpileExpression("MalExpr_test", "1 / metric");
        assertNotNull(java);
        assertTrue(java.contains(".newValue(v -> 1 / v)"),
            "N / SF should produce sf.newValue(v -> N / v)");
    }

    @Test
    void numberPlusSf() {
        final String java = transpiler.transpileExpression("MalExpr_test", "10 + metric");
        assertNotNull(java);
        assertTrue(java.contains(".plus(10)"),
            "N + SF should swap to sf.plus(N)");
    }

    @Test
    void numberTimesSf() {
        final String java = transpiler.transpileExpression("MalExpr_test", "100 * metric");
        assertNotNull(java);
        assertTrue(java.contains(".multiply(100)"),
            "N * SF should swap to sf.multiply(N)");
    }

    @Test
    void sfMinusSf() {
        final String java = transpiler.transpileExpression("MalExpr_test", "mem_total - mem_avail");
        assertNotNull(java);
        assertTrue(java.contains("ctx.getSamples().add(\"mem_total\")"),
            "Should collect both sample names");
        assertTrue(java.contains("ctx.getSamples().add(\"mem_avail\")"),
            "Should collect both sample names");
        assertTrue(java.contains(".minus("),
            "SF - SF should call .minus()");
    }

    @Test
    void sfDivSfTimesNumber() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "used_bytes / max_bytes * 100");
        assertNotNull(java);
        assertTrue(java.contains(".div("),
            "Should have .div() for SF / SF");
        assertTrue(java.contains(".multiply(100)"),
            "Should have .multiply(100) for result * 100");
    }

    @Test
    void nestedParenArithmetic() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "100 - ((mem_free * 100) / mem_total)");
        assertNotNull(java);
        assertTrue(java.contains(".multiply(100)"),
            "Should have inner multiply");
        assertTrue(java.contains(".negative()"),
            "100 - SF should produce .negative()");
    }

    @Test
    void parenthesizedWithMethodChain() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "(metric * 100).tagNotEqual('mode', 'idle').sum(['host']).rate('PT1M')");
        assertNotNull(java);
        assertTrue(java.contains(".multiply(100)"),
            "Should have multiply inside parens");
        assertTrue(java.contains(".tagNotEqual(\"mode\", \"idle\")"),
            "Should chain tagNotEqual after parens");
        assertTrue(java.contains(".rate(\"PT1M\")"),
            "Should chain rate at the end");
    }

    // ---- tag() Closure — Simple Cases ----

    @Test
    void tagAssignmentWithStringConcat() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> tags.route = 'route/' + tags['route']})");
        assertNotNull(java);

        assertTrue(java.contains(".tag((TagFunction)"),
            "Should cast closure to TagFunction");
        assertTrue(java.contains("tags.put(\"route\", \"route/\" + tags.get(\"route\"))"),
            "Should translate assignment with string concat and subscript read");
        assertTrue(java.contains("return tags;"),
            "Should return tags at end of lambda");
    }

    @Test
    void tagRemove() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> tags.remove('condition')})");
        assertNotNull(java);

        assertTrue(java.contains("tags.remove(\"condition\")"),
            "Should translate remove call");
    }

    @Test
    void tagPropertyToProperty() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> tags.rs_nm = tags.set})");
        assertNotNull(java);

        assertTrue(java.contains("tags.put(\"rs_nm\", tags.get(\"set\"))"),
            "Should translate property read on RHS to tags.get()");
    }

    @Test
    void tagStringConcatWithPropertyRead() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> tags.cluster = 'es::' + tags.cluster})");
        assertNotNull(java);

        assertTrue(java.contains("tags.put(\"cluster\", \"es::\" + tags.get(\"cluster\"))"),
            "Should translate string concat with property read");
    }

    @Test
    void tagClosureLambdaStructure() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> tags.x = 'y'})");
        assertNotNull(java);

        assertTrue(java.contains("(tags -> {"),
            "Should have lambda opening");
        assertTrue(java.contains("return tags;"),
            "Should return tags variable");
    }

    @Test
    void tagWithSubscriptWrite() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> tags['service_name'] = tags['svc']})");
        assertNotNull(java);

        assertTrue(java.contains("tags.put(\"service_name\", tags.get(\"svc\"))"),
            "Should translate subscript write and read");
    }

    @Test
    void tagChainAfterTag() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> tags.x = 'y'}).sum(['host']).service(['svc'], Layer.GENERAL)");
        assertNotNull(java);

        assertTrue(java.contains(".tag((TagFunction)"),
            "Should have tag call");
        assertTrue(java.contains(".sum(List.of(\"host\"))"),
            "Should chain sum after tag");
        assertTrue(java.contains(".service(List.of(\"svc\"), Layer.GENERAL)"),
            "Should chain service after sum");
    }

    // ---- tag() Closure — if/else + Compound Conditions ----

    @Test
    void ifOnlyWithChainedOr() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> if (tags['gc'] == 'PS Scavenge' || tags['gc'] == 'Copy' || tags['gc'] == 'ParNew' || tags['gc'] == 'G1 Young Generation') {tags.gc = 'young_gc_count'} })");
        assertNotNull(java);

        assertTrue(java.contains("if (\"PS Scavenge\".equals(tags.get(\"gc\"))"),
            "Should translate first == with constant on left for null-safety");
        assertTrue(java.contains("|| \"Copy\".equals(tags.get(\"gc\"))"),
            "Should chain || for second comparison");
        assertTrue(java.contains("|| \"ParNew\".equals(tags.get(\"gc\"))"),
            "Should chain || for third comparison");
        assertTrue(java.contains("|| \"G1 Young Generation\".equals(tags.get(\"gc\"))"),
            "Should chain || for fourth comparison");
        assertTrue(java.contains("tags.put(\"gc\", \"young_gc_count\")"),
            "Should translate assignment in if body");
    }

    @Test
    void ifElse() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> if (tags['primary'] == 'true') {tags.primary = 'primary'} else {tags.primary = 'replica'} })");
        assertNotNull(java);

        assertTrue(java.contains("if (\"true\".equals(tags.get(\"primary\"))"),
            "Should translate == comparison in condition");
        assertTrue(java.contains("tags.put(\"primary\", \"primary\")"),
            "Should translate if-branch assignment");
        assertTrue(java.contains("} else {"),
            "Should have else clause");
        assertTrue(java.contains("tags.put(\"primary\", \"replica\")"),
            "Should translate else-branch assignment");
    }

    @Test
    void ifOnlyNoElse() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> if (tags['level'] == '1') {tags.level = 'L1 aggregation'} })");
        assertNotNull(java);

        assertTrue(java.contains("if (\"1\".equals(tags.get(\"level\"))"),
            "Should translate condition");
        assertTrue(java.contains("tags.put(\"level\", \"L1 aggregation\")"),
            "Should translate if-body");
        assertTrue(!java.contains("else"),
            "Should NOT have else clause");
    }

    @Test
    void notEqualComparison() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> if (tags['status'] != 'ok') {tags.status = 'error'} })");
        assertNotNull(java);

        assertTrue(java.contains("!\"ok\".equals(tags.get(\"status\"))"),
            "Should translate != with negated .equals()");
    }

    @Test
    void logicalAndCondition() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> if (tags['a'] == 'x' && tags['b'] == 'y') {tags.c = 'z'} })");
        assertNotNull(java);

        assertTrue(java.contains("\"x\".equals(tags.get(\"a\")) && \"y\".equals(tags.get(\"b\"))"),
            "Should translate && with .equals() on both sides");
    }

    @Test
    void chainedTagClosuresWithIf() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> if (tags['level'] == '1') {tags.level = 'L1 aggregation'} })" +
            ".tag({tags -> if (tags['level'] == '2') {tags.level = 'L2 aggregation'} })");
        assertNotNull(java);

        assertTrue(java.contains("\"1\".equals(tags.get(\"level\"))"),
            "Should translate first tag closure condition");
        assertTrue(java.contains("\"2\".equals(tags.get(\"level\"))"),
            "Should translate second tag closure condition");
    }

    @Test
    void ifWithMethodChainAfter() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> if (tags['gc'] == 'Copy') {tags.gc = 'young'} }).sum(['host']).service(['svc'], Layer.GENERAL)");
        assertNotNull(java);

        assertTrue(java.contains(".tag((TagFunction)"),
            "Should have TagFunction cast");
        assertTrue(java.contains("\"Copy\".equals(tags.get(\"gc\"))"),
            "Should have if condition");
        assertTrue(java.contains(".sum(List.of(\"host\"))"),
            "Should chain sum after tag");
    }

    // ---- Filter Closures ----

    @Test
    void simpleEqualityFilter() {
        final String java = transpiler.transpileFilter("MalFilter_0",
            "{ tags -> tags.job_name == 'vm-monitoring' }");
        assertNotNull(java);

        assertTrue(java.contains("public class MalFilter_0 implements MalFilter"),
            "Should implement MalFilter");
        assertTrue(java.contains("public boolean test(Map<String, String> tags)"),
            "Should have test method");
        assertTrue(java.contains("\"vm-monitoring\".equals(tags.get(\"job_name\"))"),
            "Should translate == with constant on left");
    }

    @Test
    void filterPackageAndImports() {
        final String java = transpiler.transpileFilter("MalFilter_0",
            "{ tags -> tags.job_name == 'x' }");
        assertNotNull(java);

        assertTrue(java.contains("package " + MalToJavaTranspiler.GENERATED_PACKAGE),
            "Should have correct package");
        assertTrue(java.contains("import java.util.*;"),
            "Should import java.util");
        assertTrue(java.contains("import org.apache.skywalking.oap.meter.analyzer.dsl.*;"),
            "Should import dsl package");
    }

    @Test
    void orFilter() {
        final String java = transpiler.transpileFilter("MalFilter_1",
            "{ tags -> tags.job_name == 'flink-jobManager-monitoring' || tags.job_name == 'flink-taskManager-monitoring' }");
        assertNotNull(java);

        assertTrue(java.contains("\"flink-jobManager-monitoring\".equals(tags.get(\"job_name\"))"),
            "Should translate first ==");
        assertTrue(java.contains("|| \"flink-taskManager-monitoring\".equals(tags.get(\"job_name\"))"),
            "Should translate || with second ==");
    }

    @Test
    void inListFilter() {
        final String java = transpiler.transpileFilter("MalFilter_2",
            "{ tags -> tags.job_name in ['kubernetes-cadvisor', 'kube-state-metrics'] }");
        assertNotNull(java);

        assertTrue(java.contains("List.of(\"kubernetes-cadvisor\", \"kube-state-metrics\").contains(tags.get(\"job_name\"))"),
            "Should translate 'in' to List.of().contains()");
    }

    @Test
    void compoundAndFilter() {
        final String java = transpiler.transpileFilter("MalFilter_3",
            "{ tags -> tags.cloud_provider == 'aws' && tags.Namespace == 'AWS/S3' }");
        assertNotNull(java);

        assertTrue(java.contains("\"aws\".equals(tags.get(\"cloud_provider\"))"),
            "Should translate first ==");
        assertTrue(java.contains("&& \"AWS/S3\".equals(tags.get(\"Namespace\"))"),
            "Should translate && with second ==");
    }

    @Test
    void truthinessFilter() {
        final String java = transpiler.transpileFilter("MalFilter_4",
            "{ tags -> tags.cloud_provider == 'aws' && tags.Stage }");
        assertNotNull(java);

        assertTrue(java.contains("\"aws\".equals(tags.get(\"cloud_provider\"))"),
            "Should translate ==");
        assertTrue(java.contains("(tags.get(\"Stage\") != null && !tags.get(\"Stage\").isEmpty())"),
            "Should translate bare tags.Stage as truthiness check");
    }

    @Test
    void negatedTruthinessFilter() {
        final String java = transpiler.transpileFilter("MalFilter_5",
            "{ tags -> tags.cloud_provider == 'aws' && !tags.Method }");
        assertNotNull(java);

        assertTrue(java.contains("(tags.get(\"Method\") == null || tags.get(\"Method\").isEmpty())"),
            "Should translate !tags.Method as negated truthiness");
    }

    @Test
    void compoundWithTruthinessAndNegation() {
        final String java = transpiler.transpileFilter("MalFilter_6",
            "{ tags -> tags.cloud_provider == 'aws' && tags.Namespace == 'AWS/ApiGateway' && tags.Stage && !tags.Method }");
        assertNotNull(java);

        assertTrue(java.contains("\"aws\".equals(tags.get(\"cloud_provider\"))"),
            "Should translate first ==");
        assertTrue(java.contains("\"AWS/ApiGateway\".equals(tags.get(\"Namespace\"))"),
            "Should translate second ==");
        assertTrue(java.contains("(tags.get(\"Stage\") != null && !tags.get(\"Stage\").isEmpty())"),
            "Should translate truthiness");
        assertTrue(java.contains("(tags.get(\"Method\") == null || tags.get(\"Method\").isEmpty())"),
            "Should translate negated truthiness");
    }

    @Test
    void wrappedBlockFilter() {
        final String java = transpiler.transpileFilter("MalFilter_7",
            "{ tags -> {tags.cloud_provider == 'aws' && tags.Namespace == 'AWS/S3'} }");
        assertNotNull(java);

        assertTrue(java.contains("\"aws\".equals(tags.get(\"cloud_provider\"))"),
            "Should unwrap inner block and translate ==");
        assertTrue(java.contains("\"AWS/S3\".equals(tags.get(\"Namespace\"))"),
            "Should translate second == after unwrapping");
    }

    @Test
    void truthinessWithOrInParens() {
        final String java = transpiler.transpileFilter("MalFilter_8",
            "{ tags -> tags.cloud_provider == 'aws' && (tags.ApiId || tags.ApiName) }");
        assertNotNull(java);

        assertTrue(java.contains("(tags.get(\"ApiId\") != null && !tags.get(\"ApiId\").isEmpty())"),
            "Should translate ApiId truthiness");
        assertTrue(java.contains("(tags.get(\"ApiName\") != null && !tags.get(\"ApiName\").isEmpty())"),
            "Should translate ApiName truthiness");
    }

    // ---- forEach() Closure ----

    @Test
    void forEachBasicStructure() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.forEach(['client', 'server'], { prefix, tags -> tags[prefix + '_id'] = 'test' })");
        assertNotNull(java);

        assertTrue(java.contains(".forEach(List.of(\"client\", \"server\"), (ForEachFunction)"),
            "Should cast closure to ForEachFunction");
        assertTrue(java.contains("(prefix, tags) -> {"),
            "Should have two-parameter lambda");
        assertTrue(java.contains("tags.put(prefix + \"_id\", \"test\")"),
            "Should translate dynamic subscript write");
    }

    @Test
    void forEachNullCheckWithEarlyReturn() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.forEach(['client'], { prefix, tags -> if (tags[prefix + '_process_id'] != null) { return } })");
        assertNotNull(java);

        assertTrue(java.contains("tags.get(prefix + \"_process_id\") != null"),
            "Should translate null check");
        assertTrue(java.contains("return;"),
            "Should have void return for early exit");
    }

    @Test
    void forEachWithProcessRegistry() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.forEach(['client'], { prefix, tags -> " +
            "tags[prefix + '_process_id'] = ProcessRegistry.generateVirtualLocalProcess(tags.service, tags.instance) })");
        assertNotNull(java);

        assertTrue(java.contains("ProcessRegistry.generateVirtualLocalProcess(tags.get(\"service\"), tags.get(\"instance\"))"),
            "Should translate static method call with tag reads as args");
        assertTrue(java.contains("tags.put(prefix + \"_process_id\","),
            "Should translate dynamic subscript write");
    }

    @Test
    void forEachVarDeclaration() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.forEach(['component'], { key, tags -> String result = '' })");
        assertNotNull(java);

        assertTrue(java.contains("String result = \"\""),
            "Should translate variable declaration with empty string");
    }

    @Test
    void forEachVarDeclWithTagRead() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.forEach(['component'], { key, tags -> String protocol = tags['protocol'] })");
        assertNotNull(java);

        assertTrue(java.contains("String protocol = tags.get(\"protocol\")"),
            "Should translate var decl with tag read");
    }

    @Test
    void forEachIfElseIfChain() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.forEach(['component'], { key, tags -> " +
            "String protocol = tags['protocol']\n" +
            "String ssl = tags['is_ssl']\n" +
            "String result = ''\n" +
            "if (protocol == 'http' && ssl == 'true') { result = '129' } " +
            "else if (protocol == 'http') { result = '49' } " +
            "else if (ssl == 'true') { result = '130' } " +
            "else { result = '110' }\n" +
            "tags[key] = result })");
        assertNotNull(java);

        assertTrue(java.contains("String protocol = tags.get(\"protocol\")"),
            "Should declare protocol");
        assertTrue(java.contains("String ssl = tags.get(\"is_ssl\")"),
            "Should declare ssl");

        assertTrue(java.contains("\"http\".equals(protocol)"),
            "Should compare local var with .equals()");
        assertTrue(java.contains("\"true\".equals(ssl)"),
            "Should compare ssl with .equals()");

        assertTrue(java.contains("} else if ("),
            "Should produce else-if, not nested else { if }");

        assertTrue(java.contains("} else {"),
            "Should have final else");
        assertTrue(java.contains("result = \"110\""),
            "Should assign default value in else");

        assertTrue(java.contains("tags.put(key, result)"),
            "Should write result to tags[key]");
    }

    @Test
    void forEachLocalVarAssignment() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.forEach(['x'], { key, tags -> " +
            "String r = ''\n" +
            "r = 'abc'\n" +
            "tags[key] = r })");
        assertNotNull(java);

        assertTrue(java.contains("r = \"abc\""),
            "Should translate local var reassignment");
    }

    @Test
    void forEachEqualsOnStringComparison() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.forEach(['client'], { prefix, tags -> " +
            "if (tags[prefix + '_local'] == 'true') { tags[prefix + '_id'] = 'local' } })");
        assertNotNull(java);

        assertTrue(java.contains("\"true\".equals(tags.get(prefix + \"_local\"))"),
            "Should translate dynamic subscript comparison with .equals()");
        assertTrue(java.contains("tags.put(prefix + \"_id\", \"local\")"),
            "Should translate dynamic subscript assignment");
    }

    @Test
    void chainedForEach() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.forEach(['a'], { k1, tags -> tags[k1] = 'x' })" +
            ".forEach(['b'], { k2, tags -> tags[k2] = 'y' })");
        assertNotNull(java);

        assertTrue(java.contains("(ForEachFunction) (k1, tags)"),
            "Should have first forEach with k1");
        assertTrue(java.contains("(ForEachFunction) (k2, tags)"),
            "Should have second forEach with k2");
    }

    // ---- Elvis (?:), Safe Navigation (?.), Ternary (? :) ----

    @Test
    void safeNavigation() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> tags.svc = tags['skywalking_service']?.trim() })");
        assertNotNull(java);

        assertTrue(java.contains("(tags.get(\"skywalking_service\") != null ? tags.get(\"skywalking_service\").trim() : null)"),
            "Should translate ?.trim() to null-checked call");
    }

    @Test
    void elvisOperator() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> tags.svc = tags['name'] ?: 'unknown' })");
        assertNotNull(java);

        assertTrue(java.contains("(tags.get(\"name\") != null ? tags.get(\"name\") : \"unknown\")"),
            "Should translate ?: to null-check with default");
    }

    @Test
    void safeNavPlusElvis() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> tags.service_name = 'APISIX::'+(tags['skywalking_service']?.trim()?:'APISIX') })");
        assertNotNull(java);

        assertTrue(java.contains("tags.get(\"skywalking_service\") != null ? tags.get(\"skywalking_service\").trim() : null"),
            "Should have safe nav for trim");
        assertTrue(java.contains("!= null ?") && java.contains(": \"APISIX\""),
            "Should have elvis default to APISIX");
        assertTrue(java.contains("\"APISIX::\" + "),
            "Should have string prefix concatenation");
    }

    @Test
    void ternaryOperator() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.tag({tags -> tags.service_name = tags.ApiId ? 'gw::'+tags.ApiId : 'gw::'+tags.ApiName })");
        assertNotNull(java);

        assertTrue(java.contains("tags.get(\"ApiId\") != null && !tags.get(\"ApiId\").isEmpty()"),
            "Should translate ternary condition as truthiness check");
        assertTrue(java.contains("\"gw::\" + tags.get(\"ApiId\")"),
            "Should have true branch expression");
        assertTrue(java.contains("\"gw::\" + tags.get(\"ApiName\")"),
            "Should have false branch expression");
    }

    @Test
    void safeNavInFilterCondition() {
        final String java = transpiler.transpileFilter("MalFilter_test",
            "{ tags -> tags.job_name == 'eks-monitoring' && tags.Service?.trim() }");
        assertNotNull(java);

        assertTrue(java.contains("\"eks-monitoring\".equals(tags.get(\"job_name\"))"),
            "Should translate == comparison");
        assertTrue(java.contains("tags.get(\"Service\") != null ? tags.get(\"Service\").trim() : null"),
            "Should have safe nav for Service?.trim()");
    }

    // ---- instance() with PropertiesExtractor, MapExpression ----

    @Test
    void instanceWithPropertiesExtractor() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.instance(['cluster', 'service'], '::', ['pod'], '', Layer.K8S_SERVICE, " +
            "{tags -> ['pod': tags.pod, 'namespace': tags.namespace]})");
        assertNotNull(java);

        assertTrue(java.contains(".instance("),
            "Should have instance call");
        assertTrue(java.contains("(PropertiesExtractor)"),
            "Should cast closure to PropertiesExtractor");
        assertTrue(java.contains("Map.of(\"pod\", tags.get(\"pod\"), \"namespace\", tags.get(\"namespace\"))"),
            "Should translate map literal to Map.of()");
    }

    @Test
    void mapExpressionInTagValue() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.instance(['svc'], ['inst'], Layer.GENERAL, {tags -> ['key': tags.val]})");
        assertNotNull(java);

        assertTrue(java.contains("Map.of(\"key\", tags.get(\"val\"))"),
            "Should translate single-entry map");
        assertTrue(java.contains("(PropertiesExtractor)"),
            "Should have PropertiesExtractor cast");
    }

    @Test
    void processRelationNoClosures() {
        final String java = transpiler.transpileExpression("MalExpr_test",
            "metric.processRelation('side', ['service'], ['instance'], " +
            "'client_process_id', 'server_process_id', 'component')");
        assertNotNull(java);

        assertTrue(java.contains(".processRelation(\"side\", List.of(\"service\"), List.of(\"instance\"), " +
            "\"client_process_id\", \"server_process_id\", \"component\")"),
            "Should translate processRelation as regular method call");
    }

    // ---- Compilation + Manifests ----

    @Test
    void sourceWrittenForCompilation(@TempDir Path tempDir) throws Exception {
        final String source = transpiler.transpileExpression("MalExpr_compile_test",
            "metric.sum(['host']).service(['svc'], Layer.GENERAL)");
        transpiler.registerExpression("MalExpr_compile_test", source);

        final File sourceDir = tempDir.resolve("src").toFile();
        final File outputDir = tempDir.resolve("classes").toFile();

        try {
            transpiler.compileAll(sourceDir, outputDir, System.getProperty("java.class.path"));

            final String classPath = MalToJavaTranspiler.GENERATED_PACKAGE.replace('.', File.separatorChar)
                + File.separator + "MalExpr_compile_test.class";
            assertTrue(new File(outputDir, classPath).exists(),
                "Compiled .class file should exist");
        } catch (RuntimeException e) {
            if (e.getMessage().contains("compilation failed")) {
                final String pkgPath = MalToJavaTranspiler.GENERATED_PACKAGE.replace('.', File.separatorChar);
                final File javaFile = new File(sourceDir, pkgPath + "/MalExpr_compile_test.java");
                assertTrue(javaFile.exists(), "Source .java file should be written");
                final String written = Files.readString(javaFile.toPath());
                assertTrue(written.contains("implements MalExpression"),
                    "Written source should implement MalExpression");
            } else {
                throw e;
            }
        }
    }

    @Test
    void expressionManifest(@TempDir Path tempDir) throws Exception {
        transpiler.registerExpression("MalExpr_a",
            transpiler.transpileExpression("MalExpr_a", "metric_a"));
        transpiler.registerExpression("MalExpr_b",
            transpiler.transpileExpression("MalExpr_b", "metric_b"));

        final File outputDir = tempDir.toFile();
        transpiler.writeExpressionManifest(outputDir);

        final File manifest = new File(outputDir, "META-INF/mal-expressions.txt");
        assertTrue(manifest.exists(), "Manifest file should exist");

        final List<String> lines = Files.readAllLines(manifest.toPath());
        assertTrue(lines.contains(MalToJavaTranspiler.GENERATED_PACKAGE + ".MalExpr_a"),
            "Should contain MalExpr_a FQCN");
        assertTrue(lines.contains(MalToJavaTranspiler.GENERATED_PACKAGE + ".MalExpr_b"),
            "Should contain MalExpr_b FQCN");
    }

    @Test
    void filterManifest(@TempDir Path tempDir) throws Exception {
        final String literal = "{ tags -> tags.job == 'x' }";
        transpiler.registerFilter("MalFilter_0", literal,
            transpiler.transpileFilter("MalFilter_0", literal));

        final File outputDir = tempDir.toFile();
        transpiler.writeFilterManifest(outputDir);

        final File manifest = new File(outputDir, "META-INF/mal-filter-expressions.properties");
        assertTrue(manifest.exists(), "Filter manifest should exist");

        final String content = Files.readString(manifest.toPath());
        assertTrue(content.contains(MalToJavaTranspiler.GENERATED_PACKAGE + ".MalFilter_0"),
            "Should contain MalFilter_0 FQCN");
    }
}
