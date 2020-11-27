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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.Arrays.asList;
import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE;
import static org.apache.skywalking.oap.server.core.storage.annotation.Column.ValueDataType.LABELED_VALUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(Parameterized.class)
@RequiredArgsConstructor
public class MetricsQueryUtilTest {

    private static final int DEFAULT_VALUE = -1;

    private static final String MODULE_NAME = "meter-test";

    private final List<String> queryConditionLabels;

    private final List<String> datePoints;

    private final Map<String, DataTable> valueColumnData;

    private final String expectedResult;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {
                asList("200", "400"),
                asList("202007291425", "202007291426"),
                of("202007291425", new DataTable("200,1|400,2"), "202007291426", new DataTable("200,3|400,8")),
                "[{\"label\":\"200\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":1},{\"id\":\"202007291426\",\"value\":3}]}}," +
                    "{\"label\":\"400\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":2},{\"id\":\"202007291426\",\"value\":8}]}}]"
            },
            {
                asList("400", "200"),
                asList("202007291425", "202007291426"),
                of("202007291425", new DataTable("200,1|400,2"), "202007291426", new DataTable("200,3|400,8")),
                "[{\"label\":\"200\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":1},{\"id\":\"202007291426\",\"value\":3}]}}," +
                    "{\"label\":\"400\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":2},{\"id\":\"202007291426\",\"value\":8}]}}]"
            },
            {
                Collections.emptyList(),
                asList("202007291425", "202007291426"),
                of("202007291425", new DataTable("200,1|400,2"), "202007291426", new DataTable("200,3|400,8")),
                "[{\"label\":\"200\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":1},{\"id\":\"202007291426\",\"value\":3}]}}," +
                    "{\"label\":\"400\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":2},{\"id\":\"202007291426\",\"value\":8}]}}]"
            },
            {
                Collections.singletonList("200"),
                asList("202007291425", "202007291426"),
                of("202007291425", new DataTable("200,1|400,2"), "202007291426", new DataTable("200,3|400,8")),
                "[{\"label\":\"200\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":1},{\"id\":\"202007291426\",\"value\":3}]}}]"
            },
            {
                asList("200", "400", "500"),
                asList("202007291425", "202007291426"),
                of("202007291425", new DataTable("200,1|400,2"), "202007291426", new DataTable("200,3|400,8")),
                "[{\"label\":\"200\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":1},{\"id\":\"202007291426\",\"value\":3}]}}," +
                    "{\"label\":\"400\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":2},{\"id\":\"202007291426\",\"value\":8}]}}," +
                    "{\"label\":\"500\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":" + DEFAULT_VALUE + "},{\"id\":\"202007291426\",\"value\":" + DEFAULT_VALUE + "}]}}]"
            },
            {
                asList("200", "400"),
                asList("202007291425", "202007291426"),
                of("202007291425", new DataTable("200,1|400,2")),
                "[{\"label\":\"200\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":1},{\"id\":\"202007291426\",\"value\":" + DEFAULT_VALUE + "}]}}," +
                    "{\"label\":\"400\",\"values\":{\"values\":[{\"id\":\"202007291425\",\"value\":2},{\"id\":\"202007291426\",\"value\":" + DEFAULT_VALUE + "}]}}]"
            },
        });
    }

    @Before
    public void setup() {
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            MODULE_NAME, "value", LABELED_VALUE, Function.None, DEFAULT_VALUE, SERVICE
        );
    }

    @Test
    public void testComposeLabelValue() {
        MetricsCondition condition = new MetricsCondition();
        condition.setName(MODULE_NAME);
        List<MetricsValues> result = IMetricsQueryDAO.Util.composeLabelValue(condition, queryConditionLabels, datePoints, valueColumnData);
        assertThat(new Gson().toJson(result), is(expectedResult));
    }

}