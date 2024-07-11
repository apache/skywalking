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
import org.apache.skywalking.oap.query.promql.entity.TimeValuePair;
import org.apache.skywalking.oap.query.promql.handler.PromQLApiHandler;
import org.apache.skywalking.oap.query.promql.rt.result.ParseResultType;
import org.apache.skywalking.oap.query.promql.rt.result.MetricsRangeResult;
import org.apache.skywalking.oap.query.promql.rt.result.ParseResult;
import org.apache.skywalking.oap.query.promql.rt.PromQLExprQueryVisitor;
import org.apache.skywalking.oap.query.promql.rt.result.ScalarResult;
import org.apache.skywalking.oap.server.core.query.AggregationQueryService;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.MetricsQueryService;
import org.apache.skywalking.oap.server.core.query.PointOfTime;
import org.apache.skywalking.oap.server.core.query.RecordQueryService;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
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
    private MetricsQueryService metricsQueryService;
    private RecordQueryService recordQueryService;
    private AggregationQueryService aggregationQueryService;
    private Duration duration;
    private static final long TIME_2023022010 = DurationUtils.INSTANCE.parseToDateTime(
                                                           Step.HOUR, 2023022010)
                                                                .getMillis() / 1000;
    private static final long TIME_2023022011 = DurationUtils.INSTANCE.parseToDateTime(
                                                           Step.HOUR, 2023022011)
                                                                .getMillis() / 1000;
    private static final long TIME_2023022012 = DurationUtils.INSTANCE.parseToDateTime(
                                                                Step.HOUR, 2023022012)
                                                                     .getMillis() / 1000;

    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                "ScalarBinaryOp",
                PromQLApiHandler.QueryType.RANGE,
                "200 / 2 - 2 * 6 + 2 * 6",
                ParseResultType.SCALAR,
                "100"
            },
            {
                "ScalarCompareOp",
                PromQLApiHandler.QueryType.RANGE,
                "2 > bool 1",
                ParseResultType.SCALAR,
                "1"
            },
            {
                "Metrics",
                PromQLApiHandler.QueryType.RANGE,
                "service_cpm{service='serviceA', layer='GENERAL'}",
                ParseResultType.METRICS_RANGE,
                List.of(new TimeValuePair(TIME_2023022010, "0"), new TimeValuePair(TIME_2023022011, "1"),
                        new TimeValuePair(TIME_2023022012, "2"))
            },
            {
                "MetricsScalarBinaryOp",
                PromQLApiHandler.QueryType.RANGE,
                "service_cpm{service='serviceA', layer='GENERAL'} + 100",
                ParseResultType.METRICS_RANGE,
                List.of(new TimeValuePair(TIME_2023022010, "100"), new TimeValuePair(TIME_2023022011, "101"),
                        new TimeValuePair(TIME_2023022012, "102"))
            },
            {
                "MetricsBinaryOp",
                PromQLApiHandler.QueryType.RANGE,
                "service_cpm{service='serviceA', layer='GENERAL'} + service_cpm{service='serviceA', layer='GENERAL'}",
                ParseResultType.METRICS_RANGE,
                List.of(new TimeValuePair(TIME_2023022010, "0"), new TimeValuePair(TIME_2023022011, "2"),
                        new TimeValuePair(TIME_2023022012, "4"))
            },
            {
                "MetricsScalarCompareOp",
                PromQLApiHandler.QueryType.RANGE,
                "service_cpm{service='serviceA', layer='GENERAL'} > 1",
                ParseResultType.METRICS_RANGE,
                List.of(new TimeValuePair(TIME_2023022012, "2"))
            },
            {
                "MetricsAggregationOpSum",
                PromQLApiHandler.QueryType.RANGE,
                "sum by(service_instance_id) (http_requests_total{service='serviceA', layer='GENERAL'})",
                ParseResultType.METRICS_RANGE,
                List.of(new TimeValuePair(TIME_2023022010, "0.0"), new TimeValuePair(TIME_2023022011, "2.0"),
                        new TimeValuePair(TIME_2023022012, "4.0"))
            },
            {
                "MetricsAggregationOpAvg",
                PromQLApiHandler.QueryType.RANGE,
                "avg by(service_instance_id) (http_requests_total{service='serviceA', layer='GENERAL'})",
                ParseResultType.METRICS_RANGE,
                List.of(new TimeValuePair(TIME_2023022010, "0.0"), new TimeValuePair(TIME_2023022011, "1.0"),
                        new TimeValuePair(TIME_2023022012, "2.0"))
            }
        });
    }

    @SneakyThrows
    @BeforeEach
    public void setup() {
        ValueColumnMetadata.INSTANCE.putIfAbsent("service_cpm", "value", Column.ValueDataType.COMMON_VALUE,
                                                 0,
                                                 DefaultScopeDefine.SERVICE
        );
        ValueColumnMetadata.INSTANCE.putIfAbsent("http_requests_total", "value", Column.ValueDataType.LABELED_VALUE,
                                                 0,
                                                 DefaultScopeDefine.SERVICE
        );
        metricsQueryService = mock(MetricsQueryService.class);
        recordQueryService = mock(RecordQueryService.class);
        aggregationQueryService = mock(AggregationQueryService.class);
        duration = new Duration();
        duration.setStep(Step.HOUR);
        duration.setStart("2023-02-20 10");
        duration.setEnd("2023-02-20 12");
        Mockito.doReturn(mockMetricsValues())
               .when(metricsQueryService)
               .readMetricsValues(any(MetricsCondition.class), any(Duration.class));

        Mockito.doReturn(mockLabeledMetricsValues())
               .when(metricsQueryService)
               .readLabeledMetricsValues(any(MetricsCondition.class), any(), any(Duration.class));
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

    private List<MetricsValues> mockLabeledMetricsValues() {
        final List<PointOfTime> pointOfTimes = duration.assembleDurationPoints();
        MetricsValues values1 = new MetricsValues();
        values1.setLabel("{\"service_instance_id\": \"a\"}");
        for (int i = 0; i < pointOfTimes.size(); i++) {
            final KVInt kvInt = new KVInt();
            kvInt.setId(String.valueOf(pointOfTimes.get(i).getPoint()));
            kvInt.setValue(i);
            values1.getValues().addKVInt(kvInt);
        }

        MetricsValues values2 = new MetricsValues();
        values2.setLabel("{\"service_instance_id\": \"b\"}");
        for (int i = 0; i < pointOfTimes.size(); i++) {
            final KVInt kvInt = new KVInt();
            kvInt.setId(String.valueOf(pointOfTimes.get(i).getPoint()));
            kvInt.setValue(i);
            values2.getValues().addKVInt(kvInt);
        }
        return List.of(values1, values2);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void test(String name,
                     PromQLApiHandler.QueryType queryType,
                     String expression,
                     ParseResultType wantType,
                     Object wantResultValues) {
        PromQLLexer lexer = new PromQLLexer(CharStreams.fromString(expression));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PromQLParser parser = new PromQLParser(tokens);
        ParseTree tree = parser.expression();
        PromQLExprQueryVisitor visitor = new PromQLExprQueryVisitor(metricsQueryService, recordQueryService, aggregationQueryService, duration, queryType);
        ParseResult parseResult = visitor.visit(tree);
        Assertions.assertEquals(wantType, parseResult.getResultType());
        switch (parseResult.getResultType()) {
            case SCALAR:
                ScalarResult scalarResult = (ScalarResult) parseResult;
                Assertions.assertEquals(
                    Double.parseDouble(String.valueOf(wantResultValues)),
                    scalarResult.getValue()
                );
                break;
            case METRICS_RANGE:
                MetricsRangeResult metricsRangeResult = (MetricsRangeResult) parseResult;
                Assertions.assertEquals(metricsRangeResult.getMetricDataList().get(0).getValues(), wantResultValues);
                break;
            default:
                Assertions.fail();
        }
    }
}
