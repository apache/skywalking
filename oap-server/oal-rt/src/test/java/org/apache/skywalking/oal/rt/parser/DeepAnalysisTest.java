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
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.BooleanMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.BooleanNotEqualMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.EqualMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.NotEqualMatch;
import org.apache.skywalking.oap.server.core.annotation.AnnotationScan;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DeepAnalysisTest {
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
    public void testServiceAnalysis() {
        AnalysisResult result = new AnalysisResult();
        result.setSourceName("Service");
        result.setPackageName("service.serviceavg");
        result.getSourceAttribute().add("latency");
        result.setMetricsName("ServiceAvg");
        result.setAggregationFunctionName("longAvg");

        DeepAnalysis analysis = new DeepAnalysis();
        result = analysis.analysis(result);

        EntryMethod method = result.getEntryMethod();
        Assert.assertEquals("combine", method.getMethodName());
        Assert.assertEquals("(long)(source.getLatency())", method.getArgsExpressions().get(0));
        Assert.assertEquals("(long)(1)", method.getArgsExpressions().get(1));

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
        result.getSourceAttribute().add("latency");
        result.setMetricsName("EndpointAvg");
        result.setAggregationFunctionName("longAvg");

        DeepAnalysis analysis = new DeepAnalysis();
        result = analysis.analysis(result);

        EntryMethod method = result.getEntryMethod();
        Assert.assertEquals("combine", method.getMethodName());
        Assert.assertEquals("(long)(source.getLatency())", method.getArgsExpressions().get(0));
        Assert.assertEquals("(long)(1)", method.getArgsExpressions().get(1));

        List<SourceColumn> source = result.getFieldsFromSource();
        Assert.assertEquals(2, source.size());

        List<DataColumn> persistentFields = result.getPersistentFields();
        Assert.assertEquals(4, persistentFields.size());
    }

    @Test
    public void testFilterAnalysis() {
        AnalysisResult result = new AnalysisResult();
        result.setSourceName("Endpoint");
        result.setPackageName("endpoint.endpointavg");
        result.getSourceAttribute().add("latency");
        result.setMetricsName("EndpointAvg");
        result.setAggregationFunctionName("longAvg");
        ConditionExpression expression = new ConditionExpression();
        expression.setExpressionType("stringMatch");
        expression.getAttributes().add("name");
        expression.setValue("\"/service/prod/save\"");
        result.addFilterExpressionsParserResult(expression);

        DeepAnalysis analysis = new DeepAnalysis();
        result = analysis.analysis(result);

        EntryMethod method = result.getEntryMethod();
        Assert.assertEquals("combine", method.getMethodName());
        Assert.assertEquals("(long)(source.getLatency())", method.getArgsExpressions().get(0));
        Assert.assertEquals("(long)(1)", method.getArgsExpressions().get(1));

        List<SourceColumn> source = result.getFieldsFromSource();
        Assert.assertEquals(2, source.size());

        List<DataColumn> persistentFields = result.getPersistentFields();
        Assert.assertEquals(4, persistentFields.size());

        List<Expression> filterExpressions = result.getFilterExpressions();
        Assert.assertEquals(1, filterExpressions.size());
        Expression filterExpression = filterExpressions.get(0);
        Assert.assertEquals(EqualMatch.class.getName(), filterExpression.getExpressionObject());
        Assert.assertEquals("source.getName()", filterExpression.getLeft());
        Assert.assertEquals("\"/service/prod/save\"", filterExpression.getRight());
    }

    @Test
    public void shouldUseCorrectMatcher() {

        AnalysisResult result = new AnalysisResult();
        result.setSourceName("Endpoint");
        result.setPackageName("endpoint.endpointavg");
        result.getSourceAttribute().add("latency");
        result.setMetricsName("EndpointAvg");
        result.setAggregationFunctionName("longAvg");

        DeepAnalysis analysis = new DeepAnalysis();

        result.setFilterExpressions(null);
        result.setFilterExpressionsParserResult(null);
        result.addFilterExpressionsParserResult(new ConditionExpression("booleanMatch", "valid", ""));
        result = analysis.analysis(result);
        assertTrue(result.getFilterExpressions().size() > 0);
        assertEquals(BooleanMatch.class.getName(), result.getFilterExpressions().get(0).getExpressionObject());
        assertEquals("source.isValid()", result.getFilterExpressions().get(0).getLeft());

        result.setFilterExpressions(null);
        result.setFilterExpressionsParserResult(null);
        result.addFilterExpressionsParserResult(new ConditionExpression("stringMatch", "type", ""));
        result = analysis.analysis(result);
        assertTrue(result.getFilterExpressions().size() > 0);
        assertEquals(EqualMatch.class.getName(), result.getFilterExpressions().get(0).getExpressionObject());
        assertEquals("source.getType()", result.getFilterExpressions().get(0).getLeft());

        result.setFilterExpressions(null);
        result.setFilterExpressionsParserResult(null);
        result.addFilterExpressionsParserResult(new ConditionExpression("notEqualMatch", "type", ""));
        result = analysis.analysis(result);
        assertTrue(result.getFilterExpressions().size() > 0);
        assertEquals(NotEqualMatch.class.getName(), result.getFilterExpressions().get(0).getExpressionObject());
        assertEquals("source.getType()", result.getFilterExpressions().get(0).getLeft());

        result.setFilterExpressions(null);
        result.setFilterExpressionsParserResult(null);
        result.addFilterExpressionsParserResult(new ConditionExpression("booleanNotEqualMatch", "type", ""));
        result = analysis.analysis(result);
        assertTrue(result.getFilterExpressions().size() > 0);
        assertEquals(BooleanNotEqualMatch.class.getName(), result.getFilterExpressions().get(0).getExpressionObject());
        assertEquals("source.isType()", result.getFilterExpressions().get(0).getLeft());
    }
}
