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

package org.apache.skywalking.promql.rt.parser;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.SneakyThrows;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.skywalking.oap.query.graphql.resolver.MetricsQuery;
import org.apache.skywalking.oap.query.graphql.resolver.RecordsQuery;
import org.apache.skywalking.oap.query.promql.handler.PromQLApiHandler;
import org.apache.skywalking.oap.query.promql.rt.result.ParseResultType;
import org.apache.skywalking.oap.query.promql.rt.result.MetricsRangeResult;
import org.apache.skywalking.oap.query.promql.rt.result.ParseResult;
import org.apache.skywalking.oap.query.promql.rt.PromQLExprQueryVisitor;
import org.apache.skywalking.oap.query.promql.rt.result.ScalarResult;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.query.type.KVInt;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.promql.rt.grammar.PromQLLexer;
import org.apache.skywalking.promql.rt.grammar.PromQLParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

public class PromQLExprQueryVisitorTest {
    private MetricsQuery metricsQuery;
    private RecordsQuery recordsQuery;
    private Duration duration;

    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                "ScalarBinaryOp",
                PromQLApiHandler.QueryType.RANGE,
                "200 / 2 - 2 * 6 + 2 * 6",
                ParseResultType.scalar,
                "100"
            },
            {
                "ScalarCompareOp",
                PromQLApiHandler.QueryType.RANGE,
                "2 > bool 1",
                ParseResultType.scalar,
                "1"
            },
            {
                "Metrics",
                PromQLApiHandler.QueryType.RANGE,
                "service_cpm{service='serviceA', layer='GENERAL'}",
                ParseResultType.metrics_range,
                "[MetricRangeData(values=[TimeValuePair(time=1676858400, value=0), TimeValuePair(time=1676862000, value=1), TimeValuePair(time=1676865600, value=2)])]"
            },
            {
                "MetricsScalarBinaryOp",
                PromQLApiHandler.QueryType.RANGE,
                "service_cpm{service='serviceA', layer='GENERAL'} + 100",
                ParseResultType.metrics_range,
                "[MetricRangeData(values=[TimeValuePair(time=1676858400, value=100), TimeValuePair(time=1676862000, value=101), TimeValuePair(time=1676865600, value=102)])]"
            },
            {
                "MetricsBinaryOp",
                PromQLApiHandler.QueryType.RANGE,
                "service_cpm{service='serviceA', layer='GENERAL'} + service_cpm{service='serviceA', layer='GENERAL'}",
                ParseResultType.metrics_range,
                "[MetricRangeData(values=[TimeValuePair(time=1676858400, value=0), TimeValuePair(time=1676862000, value=2), TimeValuePair(time=1676865600, value=4)])]"
            },
            {
                "MetricsScalarCompareOp",
                PromQLApiHandler.QueryType.RANGE,
                "service_cpm{service='serviceA', layer='GENERAL'} > 1",
                ParseResultType.metrics_range,
                "[MetricRangeData(values=[TimeValuePair(time=1676865600, value=2)])]"
            }
        });
    }

    @SneakyThrows
    @BeforeEach
    public void setup() {
        ValueColumnMetadata.INSTANCE.putIfAbsent("service_cpm", "value", Column.ValueDataType.COMMON_VALUE,
                                                 Function.Avg, 0,
                                                 DefaultScopeDefine.SERVICE
        );
        metricsQuery = mock(MetricsQuery.class);
        recordsQuery = mock(RecordsQuery.class);
        duration = new Duration();
        duration.setStep(Step.HOUR);
        duration.setStart("2023-02-20 10");
        duration.setEnd("2023-02-20 12");
        Mockito.doReturn(mockMetricsValues())
               .when(metricsQuery)
               .readMetricsValues(any(MetricsCondition.class), any(Duration.class));
    }

    private MetricsValues mockMetricsValues() {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        MetricsValues values = new MetricsValues();
        for (int i = 0; i < pointOfTimes.size(); i++) {
            final KVInt kvInt = new KVInt();
            kvInt.setId(String.valueOf(pointOfTimes.get(i).getPoint()));
            kvInt.setValue(i);
            values.getValues().addKVInt(kvInt);
        }
        return values;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void test(String name,
                     PromQLApiHandler.QueryType queryType,
                     String expression,
                     ParseResultType wantType,
                     String wantResultValues) {
        PromQLLexer lexer = new PromQLLexer(CharStreams.fromString(expression));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PromQLParser parser = new PromQLParser(tokens);
        ParseTree tree = parser.expression();
        PromQLExprQueryVisitor visitor = new PromQLExprQueryVisitor(metricsQuery, recordsQuery, duration, queryType);
        ParseResult parseResult = visitor.visit(tree);
        Assertions.assertEquals(wantType, parseResult.getResultType());
        switch (parseResult.getResultType()) {
            case scalar:
                ScalarResult scalarResult = (ScalarResult) parseResult;
                Assertions.assertEquals(
                    Double.valueOf(wantResultValues),
                    scalarResult.getValue()
                );
                break;
            case metrics_range:
                MetricsRangeResult metricsRangeResult = (MetricsRangeResult) parseResult;
                Assertions.assertEquals(
                    wantResultValues,
                    metricsRangeResult.getMetricDataList().toString()
                );
                break;
            default:
                Assertions.fail();
        }
    }
}
