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
import org.apache.skywalking.oap.query.promql.handler.PromQLApiHandler;
import org.apache.skywalking.oap.query.promql.rt.result.ParseResultType;
import org.apache.skywalking.oap.query.promql.rt.result.ParseResult;
import org.apache.skywalking.oap.query.promql.rt.PromQLExprQueryVisitor;
import org.apache.skywalking.oap.server.core.annotation.AnnotationScan;
import org.apache.skywalking.oap.server.core.query.AggregationQueryService;
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
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.promql.rt.grammar.PromQLLexer;
import org.apache.skywalking.promql.rt.grammar.PromQLParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PromQLRelationExprTest {
    private ModuleManager moduleManager = mock(ModuleManager.class);
    private ModuleProviderHolder moduleProviderHolder = mock(ModuleProviderHolder.class);
    private ModuleServiceHolder moduleServiceHolder = mock(ModuleServiceHolder.class);
    private MetricsQueryService metricsQueryService;
    private RecordQueryService recordQueryService = mock(RecordQueryService.class);
    private AggregationQueryService aggregationQueryService = mock(AggregationQueryService.class);
    private IMetricsQueryDAO metricQueryDAO = mock(IMetricsQueryDAO.class);
    private Duration duration;

    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {
                        "ServiceRelationTest(missing labels)",
                        PromQLApiHandler.QueryType.RANGE,
                        "service_relation_score{service='foo',layer='MYSQL',dest_service='bar'}",
                        null
                },
                {
                        "ServiceRelationTest",
                        PromQLApiHandler.QueryType.RANGE,
                        "service_relation_score{service='foo',layer='MYSQL',dest_service='bar',dest_layer='MYSQL'}",
                        ParseResultType.METRICS_RANGE,
                },
                {
                        "ServiceRelationTest(missing labels)",
                        PromQLApiHandler.QueryType.RANGE,
                        "service_instance_relation_score{service='mysql::root[root]',layer='MYSQL',dest_service_instance='foo',service_instance='bar'}",
                        null
                },
                {
                        "ServiceRelationTest",
                        PromQLApiHandler.QueryType.RANGE,
                        "service_instance_relation_score{service='mysql::root[root]',layer='MYSQL',dest_service=localhost:9104',dest_service_instance='foo',service_instance='bar',dest_layer='MYSQL'}",
                        ParseResultType.METRICS_RANGE,
                },
                {
                        "ServiceRelationTest(missing labels)",
                        PromQLApiHandler.QueryType.RANGE,
                        "endpoint_relation_score{service='mysql::root[root]',layer='MYSQL',dest_service='localhost:9104'}",
                        null
                },
                {
                        "ServiceRelationTest",
                        PromQLApiHandler.QueryType.RANGE,
                        "endpoint_relation_score{service='mysql::root[root]',layer='MYSQL',dest_service='localhost:9104',dest_layer='MYSQL',dest_endpoint='foo',endpoint='bar'}",
                        ParseResultType.METRICS_RANGE,
                },
        });
    }

    @BeforeAll
    @SneakyThrows
    public static void set() {
        AnnotationScan annotationScan = new AnnotationScan();
        annotationScan.registerListener(new DefaultScopeDefine.Listener());
        annotationScan.scan();
    }

    @SneakyThrows
    @BeforeEach
    public void setup() {
        ValueColumnMetadata.INSTANCE.putIfAbsent("service_relation_score", "value", Column.ValueDataType.COMMON_VALUE, 0, DefaultScopeDefine.SERVICE_RELATION);
        ValueColumnMetadata.INSTANCE.putIfAbsent("service_instance_relation_score", "value", Column.ValueDataType.COMMON_VALUE, 0, DefaultScopeDefine.SERVICE_INSTANCE_RELATION);
        ValueColumnMetadata.INSTANCE.putIfAbsent("endpoint_relation_score", "value", Column.ValueDataType.COMMON_VALUE, 0, DefaultScopeDefine.ENDPOINT_RELATION);
        duration = new Duration();
        duration.setStep(Step.HOUR);
        duration.setStart("2023-02-20 10");
        duration.setEnd("2023-02-20 12");

        when(moduleManager.find(any())).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);
        when(moduleServiceHolder.getService(IMetricsQueryDAO.class)).thenReturn(metricQueryDAO);

        Mockito.doReturn(mockMetricsValues())
                .when(metricQueryDAO)
                .readMetricsValues(any(MetricsCondition.class), any(String.class), any(Duration.class));
        metricsQueryService = new MetricsQueryService(moduleManager);
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
                     ParseResultType wantType) {
        PromQLLexer lexer = new PromQLLexer(CharStreams.fromString(expression));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PromQLParser parser = new PromQLParser(tokens);
        ParseTree tree = parser.expression();
        PromQLExprQueryVisitor visitor = new PromQLExprQueryVisitor(metricsQueryService, recordQueryService, aggregationQueryService, duration, queryType);
        ParseResult parseResult = visitor.visit(tree);
        Assertions.assertEquals(wantType, parseResult.getResultType());
    }
}
