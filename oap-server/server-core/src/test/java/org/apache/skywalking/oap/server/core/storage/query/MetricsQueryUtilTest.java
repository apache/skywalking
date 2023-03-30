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

package org.apache.skywalking.oap.server.core.storage.query;

import com.google.gson.Gson;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.Arrays.asList;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE;
import static org.apache.skywalking.oap.server.core.storage.annotation.Column.ValueDataType.LABELED_VALUE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class MetricsQueryUtilTest {

    private static final int DEFAULT_VALUE = -1;

    private static final String MODULE_NAME = "meter-test";

    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                asList("200", "400"),
                asList("202007291425", "202007291426"),
                of("202007291425", new DataTable("200,1|400,2"), "202007291426", new DataTable("200,3|400,8")),
                "[{\"label\":\"200\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":1,\"isEmptyValue\":false},{\"id\":\"202007291426\",\"value\":3,\"isEmptyValue\":false}]}}," +
                    "{\"label\":\"400\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":2,\"isEmptyValue\":false},{\"id\":\"202007291426\",\"value\":8,\"isEmptyValue\":false}]}}]"
            },
            {
                asList("400", "200"),
                asList("202007291425", "202007291426"),
                of("202007291425", new DataTable("200,1|400,2"), "202007291426", new DataTable("200,3|400,8")),
                "[{\"label\":\"200\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":1,\"isEmptyValue\":false},{\"id\":\"202007291426\",\"value\":3,\"isEmptyValue\":false}]}}," +
                    "{\"label\":\"400\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":2,\"isEmptyValue\":false},{\"id\":\"202007291426\",\"value\":8,\"isEmptyValue\":false}]}}]"
            },
            {
                Collections.emptyList(),
                asList("202007291425", "202007291426"),
                of("202007291425", new DataTable("200,1|400,2"), "202007291426", new DataTable("200,3|400,8")),
                "[{\"label\":\"200\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":1,\"isEmptyValue\":false},{\"id\":\"202007291426\",\"value\":3,\"isEmptyValue\":false}]}}," +
                    "{\"label\":\"400\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":2,\"isEmptyValue\":false},{\"id\":\"202007291426\",\"value\":8,\"isEmptyValue\":false}]}}]"
            },
            {
                Collections.singletonList("200"),
                asList("202007291425", "202007291426"),
                of("202007291425", new DataTable("200,1|400,2"), "202007291426", new DataTable("200,3|400,8")),
                "[{\"label\":\"200\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":1,\"isEmptyValue\":false},{\"id\":\"202007291426\",\"value\":3,\"isEmptyValue\":false}]}}]"
            },
            {
                asList("200", "400", "500"),
                asList("202007291425", "202007291426"),
                of("202007291425", new DataTable("200,1|400,2"), "202007291426", new DataTable("200,3|400,8")),
                "[{\"label\":\"200\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":1,\"isEmptyValue\":false},{\"id\":\"202007291426\",\"value\":3,\"isEmptyValue\":false}]}}," +
                    "{\"label\":\"400\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":2,\"isEmptyValue\":false},{\"id\":\"202007291426\",\"value\":8,\"isEmptyValue\":false}]}}," +
                    "{\"label\":\"500\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":" + DEFAULT_VALUE + ",\"isEmptyValue\":true},{\"id\":\"202007291426\",\"value\":" + DEFAULT_VALUE + ",\"isEmptyValue\":true}]}}]"
            },
            {
                asList("200", "400"),
                asList("202007291425", "202007291426"),
                of("202007291425", new DataTable("200,1|400,2")),
                "[{\"label\":\"200\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":1,\"isEmptyValue\":false},{\"id\":\"202007291426\",\"value\":" + DEFAULT_VALUE + ",\"isEmptyValue\":true}]}}," +
                    "{\"label\":\"400\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":2,\"isEmptyValue\":false},{\"id\":\"202007291426\",\"value\":" + DEFAULT_VALUE + ",\"isEmptyValue\":true}]}}]"
            },
        });
    }

    @BeforeEach
    public void setup() {
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            MODULE_NAME, "value", LABELED_VALUE, Function.None, DEFAULT_VALUE, SERVICE
        );
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testComposeLabelValue(final List<String> queryConditionLabels,
                                      final List<String> datePoints,
                                      final Map<String, DataTable> valueColumnData,
                                      final String expectedResult) {
        MetricsCondition condition = new MetricsCondition();
        condition.setName(MODULE_NAME);
        List<MetricsValues> result = IMetricsQueryDAO.Util.sortValues(
            IMetricsQueryDAO.Util.composeLabelValue(condition, queryConditionLabels, datePoints, valueColumnData),
            datePoints, DEFAULT_VALUE
        );
        assertThat(new Gson().toJson(result)).isEqualTo(expectedResult);
    }

}
