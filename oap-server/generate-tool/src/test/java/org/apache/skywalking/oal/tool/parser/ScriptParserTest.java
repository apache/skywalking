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

import java.io.IOException;
import java.util.List;
import org.junit.*;

public class ScriptParserTest {
    @BeforeClass
    public static void init() throws IOException {
        Indicators.init();
    }

    @Test
    public void testParse() throws IOException {
        ScriptParser parser = ScriptParser.createFromScriptText(
            "Endpoint_avg = from(Endpoint.latency).longAvg(); //comment test" + "\n" +
                "Service_avg = from(Service.latency).longAvg()"
        );
        List<AnalysisResult> results = parser.parse();

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
        List<AnalysisResult> results = parser.parse();

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
        List<AnalysisResult> results = parser.parse();

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
}
