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

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;

@Getter
@Setter
@Builder(toBuilder = true)
public class ShardingRule {
    private String operation;
    private String table;
    private String actualDataNodes;
    private String actualDataSources;
    private String databaseStrategyType;
    private String databaseShardingColumn;
    private String databaseShardingAlgorithmType;
    private String databaseShardingAlgorithmProps;
    private String tableStrategyType;
    private String tableShardingColumn;
    private String tableShardingAlgorithmType;
    private String tableShardingAlgorithmProps;
    private String keyGenerateColumn;
    private String keyGeneratorType;
    private String keyGeneratorProps;

   public String toShardingRuleSQL() {
        SQLBuilder ruleSQL = new SQLBuilder();
        ruleSQL.append(operation);
        ruleSQL.append(" SHARDING TABLE RULE ");
        ruleSQL.append(table).appendLine("(");
        ruleSQL.append("DATANODES(").append(actualDataNodes).appendLine("),");
        ruleSQL.append("DATABASE_STRATEGY(TYPE=").append(databaseStrategyType).appendLine(",");
        ruleSQL.append("SHARDING_COLUMN=").append(databaseShardingColumn).appendLine(",");
        ruleSQL.append("SHARDING_ALGORITHM(TYPE(NAME=").append(databaseShardingAlgorithmType).appendLine(",");
        ruleSQL.append("PROPERTIES(").append(databaseShardingAlgorithmProps);
        ruleSQL.appendLine(")))),");
        ruleSQL.append("TABLE_STRATEGY(TYPE=").append(tableStrategyType).appendLine(",");
        ruleSQL.append("SHARDING_COLUMN=").append(tableShardingColumn).appendLine(",");
        ruleSQL.append("SHARDING_ALGORITHM(TYPE(NAME=").append(tableShardingAlgorithmType).appendLine(",");
        ruleSQL.append("PROPERTIES(").append(tableShardingAlgorithmProps);
        ruleSQL.append(")))))");
        return ruleSQL.toString();
    }
}
