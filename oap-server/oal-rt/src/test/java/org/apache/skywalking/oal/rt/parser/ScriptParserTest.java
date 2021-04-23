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

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.annotation.AnnotationScan;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ScriptParserTest {

    private static final String TEST_SOURCE_PACKAGE = ScriptParserTest.class.getPackage().getName() + ".test.source.";

    @BeforeClass
    public static void init() throws IOException, StorageException {
        AnnotationScan scopeScan = new AnnotationScan();
        scopeScan.registerListener(new DefaultScopeDefine.Listener());
        scopeScan.scan();
    }

    @AfterClass
    public static void clear() {
        DefaultScopeDefine.reset();
    }

    @Test
    public void testParse() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "Endpoint_avg = from(Endpoint.latency).longAvg(); //comment test" + "\n" + "Service_avg = from(Service.latency).longAvg()",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();

        Assert.assertEquals(2, results.size());

        AnalysisResult endpointAvg = results.get(0);
        Assert.assertEquals("EndpointAvg", endpointAvg.getMetricsName());
        Assert.assertEquals("Endpoint", endpointAvg.getSourceName());
        Assert.assertEquals("[latency]", endpointAvg.getSourceAttribute().toString());
        Assert.assertEquals("longAvg", endpointAvg.getAggregationFunctionName());

        AnalysisResult serviceAvg = results.get(1);
        Assert.assertEquals("ServiceAvg", serviceAvg.getMetricsName());
        Assert.assertEquals("Service", serviceAvg.getSourceName());
        Assert.assertEquals("[latency]", serviceAvg.getSourceAttribute().toString());
        Assert.assertEquals("longAvg", serviceAvg.getAggregationFunctionName());
    }

    @Test
    public void testParse2() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "Endpoint_percent = from(Endpoint.*).percent(status == true);", TEST_SOURCE_PACKAGE);
        List<AnalysisResult> results = parser.parse().getMetricsStmts();

        AnalysisResult endpointPercent = results.get(0);
        Assert.assertEquals("EndpointPercent", endpointPercent.getMetricsName());
        Assert.assertEquals("Endpoint", endpointPercent.getSourceName());
        Assert.assertEquals("[*]", endpointPercent.getSourceAttribute().toString());
        Assert.assertEquals("percent", endpointPercent.getAggregationFunctionName());
        EntryMethod entryMethod = endpointPercent.getEntryMethod();
        List<Object> methodArgsExpressions = entryMethod.getArgsExpressions();
        Assert.assertEquals(1, methodArgsExpressions.size());
    }

    @Test
    public void testParse3() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "Endpoint_percent = from(Endpoint.*).filter(status == true).filter(name == \"/product/abc\").longAvg();",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();

        AnalysisResult endpointPercent = results.get(0);
        Assert.assertEquals("EndpointPercent", endpointPercent.getMetricsName());
        Assert.assertEquals("Endpoint", endpointPercent.getSourceName());
        Assert.assertEquals("[*]", endpointPercent.getSourceAttribute().toString());
        Assert.assertEquals("longAvg", endpointPercent.getAggregationFunctionName());
        List<ConditionExpression> expressions = endpointPercent.getFilterExpressionsParserResult();

        Assert.assertEquals(2, expressions.size());

        ConditionExpression booleanMatchExp = expressions.get(0);
        Assert.assertEquals("[status]", booleanMatchExp.getAttributes().toString());
        Assert.assertEquals("true", booleanMatchExp.getValue());
        Assert.assertEquals("booleanMatch", booleanMatchExp.getExpressionType());

        ConditionExpression stringMatchExp = expressions.get(1);
        Assert.assertEquals("[name]", stringMatchExp.getAttributes().toString());
        Assert.assertEquals("\"/product/abc\"", stringMatchExp.getValue());
        Assert.assertEquals("stringMatch", stringMatchExp.getExpressionType());
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
        Assert.assertEquals("ServiceResponseS1Summary", responseSummary.getMetricsName());
        Assert.assertEquals("Service", responseSummary.getSourceName());
        Assert.assertEquals("[latency]", responseSummary.getSourceAttribute().toString());
        Assert.assertEquals("sum", responseSummary.getAggregationFunctionName());
        List<ConditionExpression> expressions = responseSummary.getFilterExpressionsParserResult();

        Assert.assertEquals(1, expressions.size());

        ConditionExpression booleanMatchExp = expressions.get(0);
        Assert.assertEquals("[latency]", booleanMatchExp.getAttributes().toString());
        Assert.assertEquals("1000", booleanMatchExp.getValue());
        Assert.assertEquals("greaterMatch", booleanMatchExp.getExpressionType());

        responseSummary = results.get(1);
        expressions = responseSummary.getFilterExpressionsParserResult();

        Assert.assertEquals(1, expressions.size());

        booleanMatchExp = expressions.get(0);
        Assert.assertEquals("[latency]", booleanMatchExp.getAttributes().toString());
        Assert.assertEquals("2000", booleanMatchExp.getValue());
        Assert.assertEquals("lessMatch", booleanMatchExp.getExpressionType());

        responseSummary = results.get(2);
        expressions = responseSummary.getFilterExpressionsParserResult();

        Assert.assertEquals(1, expressions.size());

        booleanMatchExp = expressions.get(0);
        Assert.assertEquals("[latency]", booleanMatchExp.getAttributes().toString());
        Assert.assertEquals("3000", booleanMatchExp.getValue());
        Assert.assertEquals("greaterEqualMatch", booleanMatchExp.getExpressionType());

        responseSummary = results.get(3);
        expressions = responseSummary.getFilterExpressionsParserResult();

        Assert.assertEquals(1, expressions.size());

        booleanMatchExp = expressions.get(0);
        Assert.assertEquals("[latency]", booleanMatchExp.getAttributes().toString());
        Assert.assertEquals("4000", booleanMatchExp.getValue());
        Assert.assertEquals("lessEqualMatch", booleanMatchExp.getExpressionType());
    }

    @Test
    public void testParse5() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "service_response_s4_summary = from(Service.latency).rate(param1 == true,param2 == false);",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();
        Assert.assertEquals(1, results.size());
        AnalysisResult result = results.get(0);
        Assert.assertEquals("rate", result.getAggregationFunctionName());
        Assert.assertEquals(2, result.getFuncConditionExpressions().size());

        ConditionExpression expression1 = result.getFuncConditionExpressions().get(0);
        Assert.assertEquals("[param1]", expression1.getAttributes().toString());
        Assert.assertEquals("booleanMatch", expression1.getExpressionType());
        Assert.assertEquals("true", expression1.getValue());

        ConditionExpression expression2 = result.getFuncConditionExpressions().get(1);
        Assert.assertEquals("[param2]", expression2.getAttributes().toString());
        Assert.assertEquals("booleanMatch", expression2.getExpressionType());
        Assert.assertEquals("false", expression2.getValue());
    }

    @Test
    public void testParse6() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "service_response_s4_summary = from(Service.latency).filter(latency like \"%a\").sum();",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();
        Assert.assertEquals(1, results.size());
        AnalysisResult result = results.get(0);
        List<Expression> expressions = result.getFilterExpressions();
        Assert.assertEquals(1, expressions.size());
        Expression expression = expressions.get(0);
        Assert.assertEquals("source.getLatency()", expression.getLeft());
        Assert.assertEquals(
            "org.apache.skywalking.oap.server.core.analysis.metrics.expression.LikeMatch",
            expression.getExpressionObject()
        );
        Assert.assertEquals("\"%a\"", expression.getRight());
    }

    @Test
    public void testParse7() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "service_response_s4_summary = from(Service.latency).filter(latency != 1).filter(latency in [1,2, 3]).sum();",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();
        Assert.assertEquals(1, results.size());
        AnalysisResult result = results.get(0);
        List<Expression> expressions = result.getFilterExpressions();
        Assert.assertEquals(2, expressions.size());
        Expression expression = expressions.get(1);
        Assert.assertEquals("source.getLatency()", expression.getLeft());
        Assert.assertEquals(
            "org.apache.skywalking.oap.server.core.analysis.metrics.expression.InMatch",
            expression.getExpressionObject()
        );
        Assert.assertEquals("new long[]{1,2,3}", expression.getRight());
    }

    @Test
    public void testParse8() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "service_response_s4_summary = from(Service.latency).filter(latency != 1).filter(latency in [\"1\",\"2\", \"3\"]).sum();",
            TEST_SOURCE_PACKAGE
        );
        List<AnalysisResult> results = parser.parse().getMetricsStmts();
        Assert.assertEquals(1, results.size());
        AnalysisResult result = results.get(0);
        List<Expression> expressions = result.getFilterExpressions();
        Assert.assertEquals(2, expressions.size());
        Expression expression = expressions.get(1);
        Assert.assertEquals("source.getLatency()", expression.getLeft());
        Assert.assertEquals(
            "org.apache.skywalking.oap.server.core.analysis.metrics.expression.InMatch",
            expression.getExpressionObject()
        );
        Assert.assertEquals("new Object[]{\"1\",\"2\",\"3\"}", expression.getRight());
    }

    @Test
    public void testParse9() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "ServicePercent = from(Service.sidecar.internalError).filter(sidecar.internalError == \"abc\").percent(sidecar.internalError != \"\");", TEST_SOURCE_PACKAGE);
        List<AnalysisResult> results = parser.parse().getMetricsStmts();

        AnalysisResult servicePercent = results.get(0);
        Assert.assertEquals("ServicePercent", servicePercent.getMetricsName());
        Assert.assertEquals("Service", servicePercent.getSourceName());
        Assert.assertEquals("[sidecar, internalError]", servicePercent.getSourceAttribute().toString());
        final List<Expression> filterExpressions = servicePercent.getFilterExpressions();
        Assert.assertEquals(1, filterExpressions.size());
        Assert.assertEquals("source.getSidecar().getInternalError()", filterExpressions.get(0).getLeft());
        Assert.assertEquals("percent", servicePercent.getAggregationFunctionName());
        EntryMethod entryMethod = servicePercent.getEntryMethod();
        List<Object> methodArgsExpressions = entryMethod.getArgsExpressions();
        Assert.assertEquals(1, methodArgsExpressions.size());
    }

    @Test
    public void testDisable() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText("disable(segment);", TEST_SOURCE_PACKAGE);
        DisableCollection collection = parser.parse().getDisableCollection();
        List<String> sources = collection.getAllDisableSources();
        Assert.assertEquals(1, sources.size());
        Assert.assertEquals("segment", sources.get(0));
    }
}
