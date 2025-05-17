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
 *
 */

package org.apache.skywalking.oal.rt.parser;

import org.apache.skywalking.oap.server.core.analysis.ISourceDecorator;
import org.apache.skywalking.oap.server.core.analysis.SourceDecoratorManager;
import org.apache.skywalking.oap.server.core.annotation.AnnotationScan;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

public class ScriptParserTest {

    private static final String TEST_SOURCE_PACKAGE = ScriptParserTest.class.getPackage().getName() + ".test.source.";

    @BeforeAll
    public static void init() throws IOException, StorageException {
        AnnotationScan scopeScan = new AnnotationScan();
        scopeScan.registerListener(new DefaultScopeDefine.Listener());
        scopeScan.scan();
    }

    @AfterAll
    public static void clear() {
        DefaultScopeDefine.reset();
    }

    @Test
    public void testParse() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "endpoint_resp_time = from(Endpoint.latency).longAvg(); //comment test" + "\n" + "Service_avg = from(Service.latency).longAvg()",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();

        Assertions.assertEquals(2, results.size());

        AnalysisResult endpointAvg = results.get(0);
        Assertions.assertEquals("EndpointRespTime", endpointAvg.getMetricsName());
        Assertions.assertEquals("Endpoint", endpointAvg.getFrom().getSourceName());
        Assertions.assertEquals("[latency]", endpointAvg.getFrom().getSourceAttribute().toString());
        Assertions.assertEquals("longAvg", endpointAvg.getAggregationFuncStmt().getAggregationFunctionName());

        AnalysisResult serviceAvg = results.get(1);
        Assertions.assertEquals("ServiceAvg", serviceAvg.getMetricsName());
        Assertions.assertEquals("Service", serviceAvg.getFrom().getSourceName());
        Assertions.assertEquals("[latency]", serviceAvg.getFrom().getSourceAttribute().toString());
        Assertions.assertEquals("longAvg", serviceAvg.getAggregationFuncStmt().getAggregationFunctionName());
    }

    @Test
    public void testParse2() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "Endpoint_percent = from(Endpoint.*).percent(status == true);", TEST_SOURCE_PACKAGE);
        List<AnalysisResult> results = parser.parse().getMetricsStmts();

        AnalysisResult endpointPercent = results.get(0);
        Assertions.assertEquals("EndpointPercent", endpointPercent.getMetricsName());
        Assertions.assertEquals("Endpoint", endpointPercent.getFrom().getSourceName());
        Assertions.assertEquals("[*]", endpointPercent.getFrom().getSourceAttribute().toString());
        Assertions.assertEquals("percent", endpointPercent.getAggregationFuncStmt().getAggregationFunctionName());
        EntryMethod entryMethod = endpointPercent.getEntryMethod();
        List<Object> methodArgsExpressions = entryMethod.getArgsExpressions();
        Assertions.assertEquals(1, methodArgsExpressions.size());
    }

    @Test
    public void testParse3() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "Endpoint_percent = from(Endpoint.*).filter(status == true).filter(name == \"/product/abc\").longAvg();",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();

        AnalysisResult endpointPercent = results.get(0);
        Assertions.assertEquals("EndpointPercent", endpointPercent.getMetricsName());
        Assertions.assertEquals("Endpoint", endpointPercent.getFrom().getSourceName());
        Assertions.assertEquals("[*]", endpointPercent.getFrom().getSourceAttribute().toString());
        Assertions.assertEquals("longAvg", endpointPercent.getAggregationFuncStmt().getAggregationFunctionName());
        List<ConditionExpression> expressions = endpointPercent.getFilters().getFilterExpressionsParserResult();

        Assertions.assertEquals(2, expressions.size());

        ConditionExpression booleanMatchExp = expressions.get(0);
        Assertions.assertEquals("[status]", booleanMatchExp.getAttributes().toString());
        Assertions.assertEquals("true", booleanMatchExp.getValue());
        Assertions.assertEquals("booleanMatch", booleanMatchExp.getExpressionType());

        ConditionExpression stringMatchExp = expressions.get(1);
        Assertions.assertEquals("[name]", stringMatchExp.getAttributes().toString());
        Assertions.assertEquals("\"/product/abc\"", stringMatchExp.getValue());
        Assertions.assertEquals("stringMatch", stringMatchExp.getExpressionType());
    }

    @Test
    public void testParse4() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "service_response_s1_summary = from(Service.latency).filter(latency > 1000).sum();" + "\n"
                + "service_response_s2_summary = from(Service.latency).filter(latency < 2000).sum();" + "\n"
                + "service_response_s3_summary = from(Service.latency).filter(latency >= 3000).sum();" + "\n"
                + "service_response_s4_summary = from(Service.latency).filter(latency <= 4000).sum();",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();

        AnalysisResult responseSummary = results.get(0);
        Assertions.assertEquals("ServiceResponseS1Summary", responseSummary.getMetricsName());
        Assertions.assertEquals("Service", responseSummary.getFrom().getSourceName());
        Assertions.assertEquals("[latency]", responseSummary.getFrom().getSourceAttribute().toString());
        Assertions.assertEquals("sum", responseSummary.getAggregationFuncStmt().getAggregationFunctionName());
        List<ConditionExpression> expressions = responseSummary.getFilters().getFilterExpressionsParserResult();

        Assertions.assertEquals(1, expressions.size());

        ConditionExpression booleanMatchExp = expressions.get(0);
        Assertions.assertEquals("[latency]", booleanMatchExp.getAttributes().toString());
        Assertions.assertEquals("1000", booleanMatchExp.getValue());
        Assertions.assertEquals("greaterMatch", booleanMatchExp.getExpressionType());

        responseSummary = results.get(1);
        expressions = responseSummary.getFilters().getFilterExpressionsParserResult();

        Assertions.assertEquals(1, expressions.size());

        booleanMatchExp = expressions.get(0);
        Assertions.assertEquals("[latency]", booleanMatchExp.getAttributes().toString());
        Assertions.assertEquals("2000", booleanMatchExp.getValue());
        Assertions.assertEquals("lessMatch", booleanMatchExp.getExpressionType());

        responseSummary = results.get(2);
        expressions = responseSummary.getFilters().getFilterExpressionsParserResult();

        Assertions.assertEquals(1, expressions.size());

        booleanMatchExp = expressions.get(0);
        Assertions.assertEquals("[latency]", booleanMatchExp.getAttributes().toString());
        Assertions.assertEquals("3000", booleanMatchExp.getValue());
        Assertions.assertEquals("greaterEqualMatch", booleanMatchExp.getExpressionType());

        responseSummary = results.get(3);
        expressions = responseSummary.getFilters().getFilterExpressionsParserResult();

        Assertions.assertEquals(1, expressions.size());

        booleanMatchExp = expressions.get(0);
        Assertions.assertEquals("[latency]", booleanMatchExp.getAttributes().toString());
        Assertions.assertEquals("4000", booleanMatchExp.getValue());
        Assertions.assertEquals("lessEqualMatch", booleanMatchExp.getExpressionType());
    }

    @Test
    public void testParse5() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "service_response_s4_summary = from(Service.latency).rate(param1 == true,param2 == false);",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();
        Assertions.assertEquals(1, results.size());
        AnalysisResult result = results.get(0);
        Assertions.assertEquals("rate", result.getAggregationFuncStmt().getAggregationFunctionName());
        Assertions.assertEquals(2, result.getAggregationFuncStmt().getFuncConditionExpressions().size());

        ConditionExpression expression1 = result.getAggregationFuncStmt().getFuncConditionExpressions().get(0);
        Assertions.assertEquals("[param1]", expression1.getAttributes().toString());
        Assertions.assertEquals("booleanMatch", expression1.getExpressionType());
        Assertions.assertEquals("true", expression1.getValue());

        ConditionExpression expression2 = result.getAggregationFuncStmt().getFuncConditionExpressions().get(1);
        Assertions.assertEquals("[param2]", expression2.getAttributes().toString());
        Assertions.assertEquals("booleanMatch", expression2.getExpressionType());
        Assertions.assertEquals("false", expression2.getValue());
    }

    @Test
    public void testParse6() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "service_response_s4_summary = from(Service.latency).filter(latency like \"%a\").sum();",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();
        Assertions.assertEquals(1, results.size());
        AnalysisResult result = results.get(0);
        List<Expression> expressions = result.getFilters().getFilterExpressions();
        Assertions.assertEquals(1, expressions.size());
        Expression expression = expressions.get(0);
        Assertions.assertEquals("source.getLatency()", expression.getLeft());
        Assertions.assertEquals(
            "org.apache.skywalking.oap.server.core.analysis.metrics.expression.LikeMatch",
            expression.getExpressionObject()
        );
        Assertions.assertEquals("\"%a\"", expression.getRight());
    }

    @Test
    public void testParse7() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "service_response_s4_summary = from(Service.latency).filter(latency != 1).filter(latency in [1,2, 3]).sum();",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();
        Assertions.assertEquals(1, results.size());
        AnalysisResult result = results.get(0);
        List<Expression> expressions = result.getFilters().getFilterExpressions();
        Assertions.assertEquals(2, expressions.size());
        Expression expression = expressions.get(1);
        Assertions.assertEquals("source.getLatency()", expression.getLeft());
        Assertions.assertEquals(
            "org.apache.skywalking.oap.server.core.analysis.metrics.expression.InMatch",
            expression.getExpressionObject()
        );
        Assertions.assertEquals("new long[]{1,2,3}", expression.getRight());
    }

    @Test
    public void testParse8() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "service_response_s4_summary = from(Service.latency).filter(latency != 1).filter(latency in [\"1\",\"2\", \"3\"]).sum();",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();
        Assertions.assertEquals(1, results.size());
        AnalysisResult result = results.get(0);
        List<Expression> expressions = result.getFilters().getFilterExpressions();
        Assertions.assertEquals(2, expressions.size());
        Expression expression = expressions.get(1);
        Assertions.assertEquals("source.getLatency()", expression.getLeft());
        Assertions.assertEquals(
            "org.apache.skywalking.oap.server.core.analysis.metrics.expression.InMatch",
            expression.getExpressionObject()
        );
        Assertions.assertEquals("new Object[]{\"1\",\"2\",\"3\"}", expression.getRight());
    }

    @Test
    public void testParse9() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "ServicePercent = from(Service.sidecar.internalError).filter(sidecar.internalError == \"abc\").percent(sidecar.internalError != \"\");",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();

        AnalysisResult servicePercent = results.get(0);
        Assertions.assertEquals("ServicePercent", servicePercent.getMetricsName());
        Assertions.assertEquals("Service", servicePercent.getFrom().getSourceName());
        Assertions.assertEquals("[sidecar, internalError]", servicePercent.getFrom().getSourceAttribute().toString());
        final List<Expression> filterExpressions = servicePercent.getFilters().getFilterExpressions();
        Assertions.assertEquals(1, filterExpressions.size());
        Assertions.assertEquals("source.getSidecar().getInternalError()", filterExpressions.get(0).getLeft());
        Assertions.assertEquals("percent", servicePercent.getAggregationFuncStmt().getAggregationFunctionName());
        EntryMethod entryMethod = servicePercent.getEntryMethod();
        List<Object> methodArgsExpressions = entryMethod.getArgsExpressions();
        Assertions.assertEquals(1, methodArgsExpressions.size());
    }

    @Test
    public void testParse10() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "ClientCpm = from(ServiceInstanceRelation.*).filter(componentId == 7).cpm();", TEST_SOURCE_PACKAGE);
        List<AnalysisResult> results = parser.parse().getMetricsStmts();
        AnalysisResult clientCpm = results.get(0);
        Assertions.assertEquals("ClientCpm", clientCpm.getMetricsName());
        Assertions.assertEquals("ServiceInstanceRelation", clientCpm.getFrom().getSourceName());
        Assertions.assertEquals("[*]", clientCpm.getFrom().getSourceAttribute().toString());
        final List<Expression> filterExpressions = clientCpm.getFilters().getFilterExpressions();
        Assertions.assertEquals(1, filterExpressions.size());
        Assertions.assertEquals("source.getComponentId()", filterExpressions.get(0).getLeft());
        Assertions.assertEquals("cpm", clientCpm.getAggregationFuncStmt().getAggregationFunctionName());
        EntryMethod entryMethod = clientCpm.getEntryMethod();
        List<Object> methodArgsExpressions = entryMethod.getArgsExpressions();
        Assertions.assertEquals(1, methodArgsExpressions.size());
    }

    @Test
    public void testParse11() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "GetCallTraffic = from(Service.*).filter(tag[\"http.method\"] == \"get\").cpm(tag[\"http.method\"]);",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();
        AnalysisResult clientCpm = results.get(0);
        final List<Expression> filterExpressions = clientCpm.getFilters().getFilterExpressions();
        Assertions.assertEquals(1, filterExpressions.size());
        Assertions.assertEquals("source.getTag(\"http.method\")", filterExpressions.get(0).getLeft());
        Assertions.assertEquals(1, clientCpm.getAggregationFuncStmt().getFuncArgs().size());
        Assertions.assertEquals("[tag[\"http.method\"]]", clientCpm.getAggregationFuncStmt().getFuncArgs().get(0).getText().toString());
    }

    @Test
    public void testParse12() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "cast_metrics = from((str->long)Service.tag[\"transmission.latency\"]).filter((str->long)tag[\"transmission.latency\"] > 0).longAvg((str->long)strField1== 1,  (str->long)strField2);",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();
        AnalysisResult castExp = results.get(0);
        Assertions.assertEquals("(str->long)", castExp.getFrom().getSourceCastType());
        final List<Expression> filterExpressions = castExp.getFilters().getFilterExpressions();
        Assertions.assertEquals(1, filterExpressions.size());
        Assertions.assertEquals(
            "Long.parseLong(source.getTag(\"transmission.latency\"))", filterExpressions.get(0).getLeft());
        Assertions.assertEquals("(str->long)", castExp.getAggregationFuncStmt().getFuncConditionExpressions().get(0).getCastType());
        Assertions.assertEquals(EntryMethod.ATTRIBUTE_EXP_TYPE, castExp.getAggregationFuncStmt().getFuncArgs().get(0).getType());
        Assertions.assertEquals("(str->long)", castExp.getAggregationFuncStmt().getFuncArgs().get(0).getCastType());
    }

    @Test
    public void testParse13() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "ClientCpm = from(K8SServiceRelation.*).filter(componentIds contain 7).cpm();", TEST_SOURCE_PACKAGE);
        List<AnalysisResult> results = parser.parse().getMetricsStmts();
        AnalysisResult clientCpm = results.get(0);
        Assertions.assertEquals("ClientCpm", clientCpm.getMetricsName());
        Assertions.assertEquals("K8SServiceRelation", clientCpm.getFrom().getSourceName());
        Assertions.assertEquals("[*]", clientCpm.getFrom().getSourceAttribute().toString());
        final List<Expression> filterExpressions = clientCpm.getFilters().getFilterExpressions();
        Assertions.assertEquals(1, filterExpressions.size());
        Assertions.assertEquals("source.getComponentIds()", filterExpressions.get(0).getLeft());
        Assertions.assertEquals("7", filterExpressions.get(0).getRight());
        Assertions.assertEquals("cpm", clientCpm.getAggregationFuncStmt().getAggregationFunctionName());
        EntryMethod entryMethod = clientCpm.getEntryMethod();
        List<Object> methodArgsExpressions = entryMethod.getArgsExpressions();
        Assertions.assertEquals(1, methodArgsExpressions.size());
    }

    @Test
    public void testParseDecorator() throws IOException {
        SourceDecoratorManager.DECORATOR_MAP.put("ServiceDecorator", new ISourceDecorator<ISource>() {
            @Override
            public int getSourceScope() {
                return DefaultScopeDefine.SERVICE;
            }

            @Override
            public void decorate(final ISource source) {

            }
        });
        ScriptParser parser = ScriptParser.createFromScriptText(
            "service_resp_time = from(Service.latency).longAvg().decorator(\"ServiceDecorator\");",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();
        AnalysisResult castExp = results.get(0);
        Assertions.assertEquals("ServiceDecorator", castExp.getSourceDecorator());
    }

    @Test
    public void testDisable() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText("disable(segment);", TEST_SOURCE_PACKAGE);
        DisableCollection collection = parser.parse().getDisableCollection();
        List<String> sources = collection.getAllDisableSources();
        Assertions.assertEquals(1, sources.size());
        Assertions.assertEquals("segment", sources.get(0));
    }
}
