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

import org.apache.skywalking.oap.server.core.analysis.metrics.expression.BooleanMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.BooleanNotEqualMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.NotEqualMatch;
import org.apache.skywalking.oap.server.core.analysis.metrics.expression.StringMatch;
import org.apache.skywalking.oap.server.core.annotation.AnnotationScan;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeepAnalysisTest {
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
    public void testServiceAnalysis() {
        AnalysisResult result = new AnalysisResult();
        result.getFrom().setSourceName("Service");
        result.getFrom().getSourceAttribute().add("latency");
        result.setMetricsName("ServiceAvg");
        result.getAggregationFuncStmt().setAggregationFunctionName("longAvg");

        DeepAnalysis analysis = new DeepAnalysis();
        result = analysis.analysis(result);

        EntryMethod method = result.getEntryMethod();
        Assertions.assertEquals("combine", method.getMethodName());
        Assertions.assertEquals("(long)(source.getLatency())", method.getArgsExpressions().get(0));
        Assertions.assertEquals("(long)(1)", method.getArgsExpressions().get(1));

        List<SourceColumn> source = result.getFieldsFromSource();
        Assertions.assertEquals(7, source.size());

        List<DataColumn> persistentFields = result.getPersistentFields();
        Assertions.assertEquals(4, persistentFields.size());
    }

    @Test
    public void testEndpointAnalysis() {
        AnalysisResult result = new AnalysisResult();
        result.getFrom().setSourceName("Endpoint");
        result.getFrom().getSourceAttribute().add("latency");
        result.setMetricsName("EndpointAvg");
        result.getAggregationFuncStmt().setAggregationFunctionName("longAvg");

        DeepAnalysis analysis = new DeepAnalysis();
        result = analysis.analysis(result);

        EntryMethod method = result.getEntryMethod();
        Assertions.assertEquals("combine", method.getMethodName());
        Assertions.assertEquals("(long)(source.getLatency())", method.getArgsExpressions().get(0));
        Assertions.assertEquals("(long)(1)", method.getArgsExpressions().get(1));

        List<SourceColumn> source = result.getFieldsFromSource();
        Assertions.assertEquals(8, source.size());

        List<DataColumn> persistentFields = result.getPersistentFields();
        Assertions.assertEquals(4, persistentFields.size());
    }

    @Test
    public void testFilterAnalysis() {
        AnalysisResult result = new AnalysisResult();
        result.getFrom().setSourceName("Endpoint");
        result.getFrom().getSourceAttribute().add("latency");
        result.setMetricsName("EndpointAvg");
        result.getAggregationFuncStmt().setAggregationFunctionName("longAvg");
        ConditionExpression expression = new ConditionExpression();
        expression.setExpressionType("stringMatch");
        expression.getAttributes().add("name");
        expression.setValue("\"/service/prod/save\"");
        result.getFilters().addFilterExpressionsParserResult(expression);

        DeepAnalysis analysis = new DeepAnalysis();
        result = analysis.analysis(result);

        EntryMethod method = result.getEntryMethod();
        Assertions.assertEquals("combine", method.getMethodName());
        Assertions.assertEquals("(long)(source.getLatency())", method.getArgsExpressions().get(0));
        Assertions.assertEquals("(long)(1)", method.getArgsExpressions().get(1));

        List<SourceColumn> source = result.getFieldsFromSource();
        Assertions.assertEquals(8, source.size());

        List<DataColumn> persistentFields = result.getPersistentFields();
        Assertions.assertEquals(4, persistentFields.size());

        List<Expression> filterExpressions = result.getFilters().getFilterExpressions();
        Assertions.assertEquals(1, filterExpressions.size());
        Expression filterExpression = filterExpressions.get(0);
        Assertions.assertEquals(StringMatch.class.getName(), filterExpression.getExpressionObject());
        Assertions.assertEquals("source.getName()", filterExpression.getLeft());
        Assertions.assertEquals("\"/service/prod/save\"", filterExpression.getRight());
    }

    @Test
    public void shouldUseCorrectMatcher() {

        AnalysisResult result = new AnalysisResult();
        result.getFrom().setSourceName("Endpoint");
        result.getFrom().getSourceAttribute().add("latency");
        result.setMetricsName("EndpointAvg");
        result.getAggregationFuncStmt().setAggregationFunctionName("longAvg");

        DeepAnalysis analysis = new DeepAnalysis();

        result.getFilters().setFilterExpressions(null);
        result.getFilters().setFilterExpressionsParserResult(null);
        result.getFilters().addFilterExpressionsParserResult(new ConditionExpression("booleanMatch", "valid", ""));
        result = analysis.analysis(result);
        assertTrue(result.getFilters().getFilterExpressions().size() > 0);
        assertEquals(BooleanMatch.class.getName(), result.getFilters().getFilterExpressions().get(0).getExpressionObject());
        assertEquals("source.isValid()", result.getFilters().getFilterExpressions().get(0).getLeft());

        result.getFilters().setFilterExpressions(null);
        result.getFilters().setFilterExpressionsParserResult(null);
        result.getFilters().addFilterExpressionsParserResult(new ConditionExpression("stringMatch", "type", ""));
        result = analysis.analysis(result);
        assertTrue(result.getFilters().getFilterExpressions().size() > 0);
        assertEquals(StringMatch.class.getName(), result.getFilters().getFilterExpressions().get(0).getExpressionObject());
        assertEquals("source.getType()", result.getFilters().getFilterExpressions().get(0).getLeft());

        result.getFilters().setFilterExpressions(null);
        result.getFilters().setFilterExpressionsParserResult(null);
        result.getFilters().addFilterExpressionsParserResult(new ConditionExpression("notEqualMatch", "type", ""));
        result = analysis.analysis(result);
        assertTrue(result.getFilters().getFilterExpressions().size() > 0);
        assertEquals(NotEqualMatch.class.getName(), result.getFilters().getFilterExpressions().get(0).getExpressionObject());
        assertEquals("source.getType()", result.getFilters().getFilterExpressions().get(0).getLeft());

        result.getFilters().setFilterExpressions(null);
        result.getFilters().setFilterExpressionsParserResult(null);
        result.getFilters().addFilterExpressionsParserResult(new ConditionExpression("booleanNotEqualMatch", "type", ""));
        result = analysis.analysis(result);
        assertTrue(result.getFilters().getFilterExpressions().size() > 0);
        assertEquals(BooleanNotEqualMatch.class.getName(), result.getFilters().getFilterExpressions().get(0).getExpressionObject());
        assertEquals("source.isType()", result.getFilters().getFilterExpressions().get(0).getLeft());
    }
}
