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

package org.apache.skywalking.oap.server.core.alarm.provider;

import java.lang.reflect.Field;
import java.util.Map;
import org.apache.skywalking.mqe.rt.exception.IllegalExpressionException;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AlarmRuleTest {
    @BeforeEach
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            "service_percent", "testColumn", Column.ValueDataType.COMMON_VALUE, Function.Avg, 0,
            Scope.Service.getScopeId()
        );
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            "endpoint_percent", "testColumn", Column.ValueDataType.COMMON_VALUE, Function.Avg, 0,
            Scope.Endpoint.getScopeId()
        );
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            "record", "testColumn", Column.ValueDataType.SAMPLED_RECORD, Function.Avg, 0,
            Scope.Endpoint.getScopeId()
        );
        Field serviceField = DefaultScopeDefine.class.getDeclaredField("SERVICE_CATALOG");
        serviceField.setAccessible(true);
        Map<Integer, Boolean> serviceCatalog = (Map<Integer, Boolean>) serviceField.get(null);
        serviceCatalog.put(Scope.Service.getScopeId(), true);
        Field endpointField = DefaultScopeDefine.class.getDeclaredField("ENDPOINT_CATALOG");
        endpointField.setAccessible(true);
        Map<Integer, Boolean> endpointCatalog = (Map<Integer, Boolean>) endpointField.get(null);
        endpointCatalog.put(Scope.Endpoint.getScopeId(), true);
    }

    @Test
    public void testExpressionVerify() throws IllegalExpressionException {
        AlarmRule rule = new AlarmRule();
        //normal
        rule.setExpression("sum(service_percent < 85) >= 3");

        //illegal expression
        Assertions.assertThrows(IllegalExpressionException.class, () -> {
            rule.setExpression("what? sum(service_percent < 85) >= 3");
        });

        //not exist metric
        Assertions.assertEquals(
            "Metric: [service_percent111] dose not exist.",
            Assertions.assertThrows(IllegalExpressionException.class, () -> {
                rule.setExpression("sum(service_percent111 < 85) >= 3");
            }).getMessage()
        );

        //root operation is not a Compare Operation
        Assertions.assertEquals(
            "Expression: sum(service_percent < 85) + 3 root operation is not a Compare Operation.",
            Assertions.assertThrows(IllegalExpressionException.class, () -> {
                rule.setExpression("sum(service_percent < 85) + 3");
            }).getMessage()
        );

        //not a SINGLE_VALUE result expression
        Assertions.assertEquals(
            "Expression: service_percent < 85 is not a SINGLE_VALUE result expression.",
            Assertions.assertThrows(IllegalExpressionException.class, () -> {
                rule.setExpression("service_percent < 85");
            }).getMessage()
        );

        //not a common or labeled metric
        Assertions.assertEquals(
            "Metric dose not supported in alarm, metric: [record] is not a common or labeled metric.",
            Assertions.assertThrows(IllegalExpressionException.class, () -> {
                rule.setExpression("sum(record < 85) > 1");
            }).getMessage()
        );

        //metrics in expression must have the same scope level
        Assertions.assertTrue(Assertions.assertThrows(IllegalExpressionException.class, () -> {
            rule.setExpression("sum(service_percent > endpoint_percent) >= 1");
        }).getMessage().contains("The metrics in expression: sum(service_percent > endpoint_percent) >= 1 must have the same scope level, but got:"));

    }
}
