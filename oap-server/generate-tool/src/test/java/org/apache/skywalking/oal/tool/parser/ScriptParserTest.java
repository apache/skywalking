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

package org.apache.skywalking.oal.tool.parser;

import java.io.*;
import java.util.List;
import org.apache.skywalking.oal.tool.meta.*;
import org.apache.skywalking.oap.server.core.annotation.AnnotationScan;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.junit.*;

public class ScriptParserTest {
    @BeforeClass
    public static void init() throws IOException {
        MetaReader reader = new MetaReader();
        InputStream stream = MetaReaderTest.class.getResourceAsStream("/scope-meta.yml");
        MetaSettings metaSettings = reader.read(stream);
        SourceColumnsFactory.setSettings(metaSettings);
        Indicators.init();

        AnnotationScan scopeScan = new AnnotationScan();
        scopeScan.registerListener(new DefaultScopeDefine.Listener());
        scopeScan.scan(null);
    }

    @AfterClass
    public static void clear() {
        DefaultScopeDefine.reset();
    }

    @Test
    public void testParse() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "Endpoint_avg = from(Endpoint.latency).longAvg(); //comment test" + "\n" +
                "Service_avg = from(Service.latency).longAvg()"
        );
        List<AnalysisResult> results = parser.parse().getIndicatorStmts();

        Assert.assertEquals(2, results.size());

        AnalysisResult endpointAvg = results.get(0);
        Assert.assertEquals("EndpointAvg", endpointAvg.getMetricName());
        Assert.assertEquals("Endpoint", endpointAvg.getSourceName());
        Assert.assertEquals("latency", endpointAvg.getSourceAttribute());
        Assert.assertEquals("longAvg", endpointAvg.getAggregationFunctionName());

        AnalysisResult serviceAvg = results.get(1);
        Assert.assertEquals("ServiceAvg", serviceAvg.getMetricName());
        Assert.assertEquals("Service", serviceAvg.getSourceName());
        Assert.assertEquals("latency", serviceAvg.getSourceAttribute());
        Assert.assertEquals("longAvg", serviceAvg.getAggregationFunctionName());
    }

    @Test
    public void testParse2() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "Endpoint_percent = from(Endpoint.*).percent(status == true);"
        );
        List<AnalysisResult> results = parser.parse().getIndicatorStmts();

        AnalysisResult endpointPercent = results.get(0);
        Assert.assertEquals("EndpointPercent", endpointPercent.getMetricName());
        Assert.assertEquals("Endpoint", endpointPercent.getSourceName());
        Assert.assertEquals("*", endpointPercent.getSourceAttribute());
        Assert.assertEquals("percent", endpointPercent.getAggregationFunctionName());
        EntryMethod entryMethod = endpointPercent.getEntryMethod();
        List<String> methodArgsExpressions = entryMethod.getArgsExpressions();
        Assert.assertEquals(3, methodArgsExpressions.size());
        Assert.assertEquals("source.isStatus()", methodArgsExpressions.get(1));
        Assert.assertEquals("true", methodArgsExpressions.get(2));
    }

    @Test
    public void testParse3() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "Endpoint_percent = from(Endpoint.*).filter(status == true).filter(name == \"/product/abc\").longAvg();"
        );
        List<AnalysisResult> results = parser.parse().getIndicatorStmts();

        AnalysisResult endpointPercent = results.get(0);
        Assert.assertEquals("EndpointPercent", endpointPercent.getMetricName());
        Assert.assertEquals("Endpoint", endpointPercent.getSourceName());
        Assert.assertEquals("*", endpointPercent.getSourceAttribute());
        Assert.assertEquals("longAvg", endpointPercent.getAggregationFunctionName());
        List<ConditionExpression> expressions = endpointPercent.getFilterExpressionsParserResult();

        Assert.assertEquals(2, expressions.size());

        ConditionExpression booleanMatchExp = expressions.get(0);
        Assert.assertEquals("status", booleanMatchExp.getAttribute());
        Assert.assertEquals("true", booleanMatchExp.getValue());
        Assert.assertEquals("booleanMatch", booleanMatchExp.getExpressionType());

        ConditionExpression stringMatchExp = expressions.get(1);
        Assert.assertEquals("name", stringMatchExp.getAttribute());
        Assert.assertEquals("\"/product/abc\"", stringMatchExp.getValue());
        Assert.assertEquals("stringMatch", stringMatchExp.getExpressionType());
    }

    @Test
    public void testParse4() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "service_response_s1_summary = from(Service.latency).filter(latency > 1000).sum();" + "\n" +
                "service_response_s2_summary = from(Service.latency).filter(latency < 2000).sum();" + "\n" +
                "service_response_s3_summary = from(Service.latency).filter(latency >= 3000).sum();" + "\n" +
                "service_response_s4_summary = from(Service.latency).filter(latency <= 4000).sum();"
        );
        List<AnalysisResult> results = parser.parse().getIndicatorStmts();

        AnalysisResult responseSummary = results.get(0);
        Assert.assertEquals("ServiceResponseS1Summary", responseSummary.getMetricName());
        Assert.assertEquals("Service", responseSummary.getSourceName());
        Assert.assertEquals("latency", responseSummary.getSourceAttribute());
        Assert.assertEquals("sum", responseSummary.getAggregationFunctionName());
        List<ConditionExpression> expressions = responseSummary.getFilterExpressionsParserResult();

        Assert.assertEquals(1, expressions.size());

        ConditionExpression booleanMatchExp = expressions.get(0);
        Assert.assertEquals("latency", booleanMatchExp.getAttribute());
        Assert.assertEquals("1000", booleanMatchExp.getValue());
        Assert.assertEquals("greaterMatch", booleanMatchExp.getExpressionType());

        responseSummary = results.get(1);
        expressions = responseSummary.getFilterExpressionsParserResult();

        Assert.assertEquals(1, expressions.size());

        booleanMatchExp = expressions.get(0);
        Assert.assertEquals("latency", booleanMatchExp.getAttribute());
        Assert.assertEquals("2000", booleanMatchExp.getValue());
        Assert.assertEquals("lessMatch", booleanMatchExp.getExpressionType());

        responseSummary = results.get(2);
        expressions = responseSummary.getFilterExpressionsParserResult();

        Assert.assertEquals(1, expressions.size());

        booleanMatchExp = expressions.get(0);
        Assert.assertEquals("latency", booleanMatchExp.getAttribute());
        Assert.assertEquals("3000", booleanMatchExp.getValue());
        Assert.assertEquals("greaterEqualMatch", booleanMatchExp.getExpressionType());

        responseSummary = results.get(3);
        expressions = responseSummary.getFilterExpressionsParserResult();

        Assert.assertEquals(1, expressions.size());

        booleanMatchExp = expressions.get(0);
        Assert.assertEquals("latency", booleanMatchExp.getAttribute());
        Assert.assertEquals("4000", booleanMatchExp.getValue());
        Assert.assertEquals("lessEqualMatch", booleanMatchExp.getExpressionType());
    }

    @Test
    public void testDisable() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "disable(segment);");
        DisableCollection collection = parser.parse().getDisableCollection();
        List<String> sources = collection.getAllDisableSources();
        Assert.assertEquals(1, sources.size());
        Assert.assertEquals("segment", sources.get(0));
    }
}
