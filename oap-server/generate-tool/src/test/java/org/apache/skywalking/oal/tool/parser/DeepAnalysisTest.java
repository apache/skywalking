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

public class DeepAnalysisTest {
    @BeforeClass
    public static void init() throws IOException {
        Indicators.init();
    }

    @Test
    public void testServiceAnalysis() {
        AnalysisResult result = new AnalysisResult();
        result.setSourceName("Service");
        result.setPackageName("service.serviceavg");
        result.setSourceAttribute("latency");
        result.setMetricName("ServiceAvg");
        result.setAggregationFunctionName("longAvg");

        DeepAnalysis analysis = new DeepAnalysis();
        result = analysis.analysis(result);

        EntryMethod method = result.getEntryMethod();
        Assert.assertEquals("combine", method.getMethodName());
        Assert.assertEquals("source.getLatency()", method.getArgsExpressions().get(0));
        Assert.assertEquals("1", method.getArgsExpressions().get(1));

        List<SourceColumn> source = result.getFieldsFromSource();
        Assert.assertEquals(1, source.size());

        List<DataColumn> persistentFields = result.getPersistentFields();
        Assert.assertEquals(4, persistentFields.size());
    }

    @Test
    public void testEndpointAnalysis() {
        AnalysisResult result = new AnalysisResult();
        result.setSourceName("Endpoint");
        result.setPackageName("endpoint.endpointavg");
        result.setSourceAttribute("latency");
        result.setMetricName("EndpointAvg");
        result.setAggregationFunctionName("longAvg");

        DeepAnalysis analysis = new DeepAnalysis();
        result = analysis.analysis(result);

        EntryMethod method = result.getEntryMethod();
        Assert.assertEquals("combine", method.getMethodName());
        Assert.assertEquals("source.getLatency()", method.getArgsExpressions().get(0));
        Assert.assertEquals("1", method.getArgsExpressions().get(1));

        List<SourceColumn> source = result.getFieldsFromSource();
        Assert.assertEquals(3, source.size());

        List<DataColumn> persistentFields = result.getPersistentFields();
        Assert.assertEquals(4, persistentFields.size());
    }

    @Test
    public void testFilterAnalysis() {
        AnalysisResult result = new AnalysisResult();
        result.setSourceName("Endpoint");
        result.setPackageName("endpoint.endpointavg");
        result.setSourceAttribute("latency");
        result.setMetricName("EndpointAvg");
        result.setAggregationFunctionName("longAvg");
        ConditionExpression expression = new ConditionExpression();
        expression.setExpressionType("stringMatch");
        expression.setAttribute("name");
        expression.setValue("\"/service/prod/save\"");
        result.addFilterExpressionsParserResult(expression);

        DeepAnalysis analysis = new DeepAnalysis();
        result = analysis.analysis(result);

        EntryMethod method = result.getEntryMethod();
        Assert.assertEquals("combine", method.getMethodName());
        Assert.assertEquals("source.getLatency()", method.getArgsExpressions().get(0));
        Assert.assertEquals("1", method.getArgsExpressions().get(1));

        List<SourceColumn> source = result.getFieldsFromSource();
        Assert.assertEquals(3, source.size());

        List<DataColumn> persistentFields = result.getPersistentFields();
        Assert.assertEquals(4, persistentFields.size());

        List<FilterExpression> filterExpressions = result.getFilterExpressions();
        Assert.assertEquals(1, filterExpressions.size());
        FilterExpression filterExpression = filterExpressions.get(0);
        Assert.assertEquals("EqualMatch", filterExpression.getExpressionObject());
        Assert.assertEquals("source.getName()", filterExpression.getLeft());
        Assert.assertEquals("\"/service/prod/save\"", filterExpression.getRight());
    }
}
