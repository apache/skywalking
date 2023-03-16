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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere;

import org.apache.skywalking.oap.server.core.storage.ShardingAlgorithm;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.powermock.reflect.Whitebox;

import java.util.Arrays;
import java.util.HashSet;

@Disabled
public class ShardingRulesTest {
    private DateTime currentDate;

    @BeforeEach
    public void init() {
        currentDate = DateTimeFormat.forPattern("yyyyMMdd").parseDateTime("20220919");
    }

    @Test
    public void buildTimeRangeShardingRuleTest() throws Exception {
        ShardingRulesOperator rulesOperator = ShardingRulesOperator.INSTANCE;
        ShardingRule.ShardingRuleBuilder builder = ShardingRule.builder();
        builder.table("Test_table");
        builder.operation("CREATE");
        Whitebox.invokeMethod(rulesOperator, "buildShardingRule",
                              builder,
                              "Test_table",
                              new HashSet<>(Arrays.asList("ds_0", "ds_1")),
                              ShardingAlgorithm.TIME_SEC_RANGE_SHARDING_ALGORITHM,
                              "time_bucket",
                              "service_id",
                              3,
                              currentDate);
        String rule = builder.build().toShardingRuleSQL();
        String expectedRule = "CREATE SHARDING TABLE RULE Test_table(" + System.lineSeparator() +
            "DATANODES(\"ds_1.Test_table_20220917\",\"ds_1.Test_table_20220918\",\"ds_1.Test_table_20220919\",\"ds_1.Test_table_20220920\",\"ds_0.Test_table_20220917\",\"ds_0.Test_table_20220918\",\"ds_0.Test_table_20220919\",\"ds_0.Test_table_20220920\")," + System.lineSeparator() +
            "DATABASE_STRATEGY(TYPE=\"standard\"," + System.lineSeparator() +
            "SHARDING_COLUMN=service_id," + System.lineSeparator() +
            "SHARDING_ALGORITHM(TYPE(NAME=\"inline\"," + System.lineSeparator() +
            "PROPERTIES(\"algorithm-expression\"=\"ds_${service_id.hashCode()&Integer.MAX_VALUE%2}\"))))," + System.lineSeparator() +
            "TABLE_STRATEGY(TYPE=\"standard\"," + System.lineSeparator() +
            "SHARDING_COLUMN=time_bucket," + System.lineSeparator() +
            "SHARDING_ALGORITHM(TYPE(NAME=\"interval\"," + System.lineSeparator() +
            "PROPERTIES(\"datetime-pattern\"=\"yyyyMMddHHmmss\",\"datetime-interval-unit\"=\"days\",\"datetime-interval-amount\"=\"1\",\"sharding-suffix-pattern\"=\"yyyyMMdd\",\"datetime-lower\"=\"20220101000000\",\"datetime-upper\"=\"20991201000000\")))))";
        Assertions.assertEquals(expectedRule, rule);
    }

    @Test
    public void buildTimeRelativeIDShardingRuleTest() throws Exception {
        ShardingRulesOperator rulesOperator = ShardingRulesOperator.INSTANCE;
        ShardingRule.ShardingRuleBuilder builder = ShardingRule.builder();
        builder.table("Test_table");
        builder.operation("CREATE");
        Whitebox.invokeMethod(rulesOperator, "buildShardingRule",
                              builder,
                              "Test_table",
                              new HashSet<>(Arrays.asList("ds_0", "ds_1")),
                              ShardingAlgorithm.TIME_RELATIVE_ID_SHARDING_ALGORITHM,
                              "id",
                              "entity_id",
                              3,
                              currentDate);
        String rule = builder.build().toShardingRuleSQL();
        String expectedRule = "CREATE SHARDING TABLE RULE Test_table(" + System.lineSeparator() +
            "DATANODES(\"ds_1.Test_table_20220917\",\"ds_1.Test_table_20220918\",\"ds_1.Test_table_20220919\",\"ds_1.Test_table_20220920\",\"ds_0.Test_table_20220917\",\"ds_0.Test_table_20220918\",\"ds_0.Test_table_20220919\",\"ds_0.Test_table_20220920\")," + System.lineSeparator() +
            "DATABASE_STRATEGY(TYPE=\"standard\"," + System.lineSeparator() +
            "SHARDING_COLUMN=entity_id," + System.lineSeparator() +
            "SHARDING_ALGORITHM(TYPE(NAME=\"inline\"," + System.lineSeparator() +
            "PROPERTIES(\"algorithm-expression\"=\"ds_${entity_id.hashCode()&Integer.MAX_VALUE%2}\"))))," + System.lineSeparator() +
            "TABLE_STRATEGY(TYPE=\"standard\"," + System.lineSeparator() +
            "SHARDING_COLUMN=id," + System.lineSeparator() +
            "SHARDING_ALGORITHM(TYPE(NAME=\"inline\"," + System.lineSeparator() +
            "PROPERTIES(\"allow-range-query-with-inline-sharding\"=\"true\",\"algorithm-expression\"=\"Test_table_${long time_bucket = Long.parseLong(id.substring(0,id.indexOf('_')));" +
            "if (10000000L < time_bucket && time_bucket < 99999999L) {return time_bucket;};" +
            "if (1000000000L < time_bucket && time_bucket < 9999999999L) {return time_bucket.intdiv(100);};" +
            "if (100000000000L < time_bucket && time_bucket < 999999999999L) {return time_bucket.intdiv(100*100);};" +
            "if (10000000000000L < time_bucket && time_bucket < 99999999999999L) {return time_bucket.intdiv(100*100*100);};" +
            "}\")))))";
        Assertions.assertEquals(expectedRule, rule);
    }
}
