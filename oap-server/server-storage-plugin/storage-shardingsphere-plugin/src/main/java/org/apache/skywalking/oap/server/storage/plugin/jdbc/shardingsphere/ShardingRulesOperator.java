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

import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.storage.ShardingAlgorithm;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.SQLDatabaseModelExtension;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.SQLBuilder;
import org.joda.time.DateTime;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public enum ShardingRulesOperator {
    INSTANCE;

    private final Map<String, ShardingRule> modelShardingRules = new HashMap<>();

    private static final String TIME_RELATIVE_ID_SHARDING_EXPRESSION =
        "${long time_bucket = Long.parseLong(id.substring(0,id.indexOf('_')));" +
        "if (10000000L < time_bucket && time_bucket < 99999999L) {return time_bucket;};" +
        "if (1000000000L < time_bucket && time_bucket < 9999999999L) {return time_bucket.intdiv(100);};" +
        "if (100000000000L < time_bucket && time_bucket < 999999999999L) {return time_bucket.intdiv(100*100);};" +
        "if (10000000000000L < time_bucket && time_bucket < 99999999999999L) {return time_bucket.intdiv(100*100*100);};" +
        "}";

    private static final String TIME_SEC_RANGE_SHARDING_EXPRESSION =
        "\"datetime-pattern\"=\"yyyyMMddHHmmss\"," +
        "\"datetime-interval-unit\"=\"days\"," +
        "\"datetime-interval-amount\"=\"1\"," +
        "\"sharding-suffix-pattern\"=\"yyyyMMdd\"," +
        "\"datetime-lower\"=\"20220101000000\"," +
        "\"datetime-upper\"=\"20991201000000\"";

    private static final String TIME_MIN_RANGE_SHARDING_EXPRESSION =
        "\"datetime-pattern\"=\"yyyyMMddHHmm\"," +
        "\"datetime-interval-unit\"=\"days\"," +
        "\"datetime-interval-amount\"=\"1\"," +
        "\"sharding-suffix-pattern\"=\"yyyyMMdd\"," +
        "\"datetime-lower\"=\"202201010000\"," +
        "\"datetime-upper\"=\"209912010000\"";

    private static final String TIME_BUCKET_SHARDING_EXPRESSION =
        "${" +
        "if (10000000L < time_bucket && time_bucket < 99999999L) {return time_bucket;};" +
        "if (1000000000L < time_bucket && time_bucket < 9999999999L) {return time_bucket.intdiv(100);};" +
        "if (100000000000L < time_bucket && time_bucket < 999999999999L) {return time_bucket.intdiv(100*100);};" +
        "if (10000000000000L < time_bucket && time_bucket < 99999999999999L) {return time_bucket.intdiv(100*100*100);};" +
        "}";

    public void start(JDBCClient client) throws IOException, SQLException, StorageException {
        initShardingRules(client);
    }

    public boolean createOrUpdateShardingRule(JDBCClient client, Model model, Set<String> dataSources, int ttl) throws SQLException {
        boolean isExecuted;
        ShardingRule.ShardingRuleBuilder builder = ShardingRule.builder();
        builder.table(model.getName());
        String tableName = model.getName();
        SQLDatabaseModelExtension.Sharding sharding = model.getSqlDBModelExtension().getSharding().orElseThrow(
            () -> new UnexpectedException("Sharding should not be empty."));
        isExecuted = executeShardingRule(
            buildShardingRule(builder, tableName, dataSources, sharding.getShardingAlgorithm(),
                               sharding.getTableShardingColumn(),
                               sharding.getDataSourceShardingColumn(),
                               ttl,
                               DateTime.now()
            ),
            client,
            tableName
        );
        // additional tables
        for (String additionalTable : model.getSqlDBModelExtension().getAdditionalTables().keySet()) {
            ShardingRule.ShardingRuleBuilder additionalBuilder = ShardingRule.builder();
            additionalBuilder.table(additionalTable);
            isExecuted = executeShardingRule(
                buildShardingRule(additionalBuilder, additionalTable, dataSources, sharding.getShardingAlgorithm(),
                                  sharding.getTableShardingColumn(),
                                  sharding.getDataSourceShardingColumn(), ttl, DateTime.now()
                ), client, additionalTable
            );
        }

        return isExecuted;
    }

    @SneakyThrows
    private void initShardingRules(JDBCClient client) {
        SQLBuilder sql = new SQLBuilder("SHOW SHARDING TABLE RULES");
        client.executeQuery(sql.toString(), resultSet -> {
            while (resultSet.next()) {
                ShardingRule.ShardingRuleBuilder builder = ShardingRule.builder();
                builder.table(resultSet.getString("TABLE"));
                builder.actualDataNodes(resultSet.getString("ACTUAL_DATA_NODES"));
                builder.actualDataSources(resultSet.getString("ACTUAL_DATA_SOURCES"));
                // todo: shardingsphere-5.1.2 response DATABASE_STRATEGY and TABLE_STRATEGY type "inline" but should "standard"
                builder.databaseStrategyType("standard");
                builder.databaseShardingColumn(resultSet.getString("DATABASE_SHARDING_COLUMN"));
                builder.databaseShardingAlgorithmType(resultSet.getString("DATABASE_SHARDING_ALGORITHM_TYPE"));
                builder.databaseShardingAlgorithmProps(resultSet.getString("DATABASE_SHARDING_ALGORITHM_PROPS"));
                // todo: shardingsphere-5.1.2 response DATABASE_STRATEGY and TABLE_STRATEGY type "inline" but should "standard"
                builder.tableStrategyType("standard");
                builder.tableShardingColumn(resultSet.getString("TABLE_SHARDING_COLUMN"));
                builder.tableShardingAlgorithmType(resultSet.getString("TABLE_SHARDING_ALGORITHM_TYPE"));
                builder.tableShardingAlgorithmProps(resultSet.getString("TABLE_SHARDING_ALGORITHM_PROPS"));
                builder.keyGenerateColumn(resultSet.getString("KEY_GENERATE_COLUMN"));
                builder.keyGeneratorType(resultSet.getString("KEY_GENERATOR_TYPE"));
                builder.keyGeneratorProps(resultSet.getString("KEY_GENERATOR_PROPS"));

                ShardingRule shardingRule = builder.build();
                modelShardingRules.put(shardingRule.getTable(), shardingRule);
            }
            return null;
        });
    }

    private void registerShardingRule(String tableName, ShardingRule rule) {
        modelShardingRules.put(tableName, rule);
    }

    private ShardingRule.ShardingRuleBuilder buildShardingRule(ShardingRule.ShardingRuleBuilder builder,
                                      String tableName,
                                      Set<String> dataSources,
                                      ShardingAlgorithm shardingAlgorithm,
                                      String tableShardingColumn,
                                      String dsShardingColumn,
                                      int ttl,
                                      DateTime currentDate) {
        buildDataNodes(builder, tableName, dataSources, ttl, currentDate);
        buildDatabaseStrategy(builder, dsShardingColumn, dataSources.size());

//        switch (shardingAlgorithm) {
//            case TIME_SEC_RANGE_SHARDING_ALGORITHM:
//                buildTimeRangeTableStrategy(builder, tableShardingColumn, TIME_SEC_RANGE_SHARDING_EXPRESSION);
//                break;
//            case TIME_MIN_RANGE_SHARDING_ALGORITHM:
//                buildTimeRangeTableStrategy(builder, tableShardingColumn, TIME_MIN_RANGE_SHARDING_EXPRESSION);
//                break;
//            case TIME_RELATIVE_ID_SHARDING_ALGORITHM:
//                buildExpressionTableStrategy(builder, tableName, tableShardingColumn,
//                                             TIME_RELATIVE_ID_SHARDING_EXPRESSION);
//                break;
//            case TIME_BUCKET_SHARDING_ALGORITHM:
//                buildExpressionTableStrategy(builder, tableName, tableShardingColumn, TIME_BUCKET_SHARDING_EXPRESSION);
//                break;
//            default:
//                throw new UnexpectedException("Unsupported sharding algorithm " + shardingAlgorithm);
//        }
        return builder;
    }

    private void buildDataNodes(ShardingRule.ShardingRuleBuilder builder,
                                String tableName,
                                Set<String> dataSources,
                                int ttl,
                                DateTime currentDate) {
        StringBuilder nodesBuilder = new StringBuilder();
        dataSources.forEach(dataSource -> {
            nodesBuilder.append("\"")
                        .append(dataSource)
                        .append(".")
                        .append(tableName)
                        .append("\"")
                        .append(",");
        });
        builder.actualDataNodes(nodesBuilder.substring(0, nodesBuilder.length() - 1));
    }

    private void buildDatabaseStrategy(ShardingRule.ShardingRuleBuilder builder,
                                       String shardingColumn,
                                       int dsCount) {
        StringBuilder propsBuilder = new StringBuilder();
        propsBuilder.append("\"algorithm-expression\"=\"ds_${")
                  .append(shardingColumn)
                  .append(".hashCode()&Integer.MAX_VALUE%")
                  .append(dsCount)
                  .append("}\"");

        builder.databaseStrategyType("\"standard\"")
               .databaseShardingColumn(shardingColumn)
               .databaseShardingAlgorithmType("\"inline\"")
               .databaseShardingAlgorithmProps(propsBuilder.toString());

    }

    private void buildTimeRangeTableStrategy(ShardingRule.ShardingRuleBuilder builder,
                                             String tableShardingColumn, String algorithmExpression) {
        builder.tableStrategyType("\"standard\"")
               .tableShardingColumn(tableShardingColumn)
               .tableShardingAlgorithmType("\"interval\"")
               .tableShardingAlgorithmProps(algorithmExpression);
    }

    private void buildExpressionTableStrategy(ShardingRule.ShardingRuleBuilder builder,
                                              String tableName,
                                              String tableShardingColumn,
                                              String algorithmExpression
                                                                          ) {
        StringBuilder propsBuilder = new StringBuilder();
        propsBuilder.append("\"allow-range-query-with-inline-sharding\"=\"true\",")
                    .append("\"algorithm-expression\"=\"")
                    .append(tableName)
                    .append("_")
                    .append(algorithmExpression)
                    .append("\"");

        builder.tableStrategyType("\"standard\"")
               .tableShardingColumn(tableShardingColumn)
               .tableShardingAlgorithmType("\"inline\"")
               .tableShardingAlgorithmProps(propsBuilder.toString());

    }

    private boolean executeShardingRule(ShardingRule.ShardingRuleBuilder builder,
                                     JDBCClient client,
                                     String tableName) throws SQLException {
        ShardingRule existRule = modelShardingRules.get(tableName);
        ShardingRule shardingRule;
        String shardingRuleSQL;
        if (existRule == null) {
            builder.operation("CREATE");
            shardingRule = builder.build();
            shardingRuleSQL = shardingRule.toShardingRuleSQL();
        } else {
            builder.operation("ALTER");
            existRule.setOperation("ALTER");
            shardingRule = builder.build();
            shardingRuleSQL = shardingRule.toShardingRuleSQL();
            if (existRule.toShardingRuleSQL().equals(shardingRuleSQL.replaceAll("\"", ""))) {
                return false;
            }
        }

        SQLBuilder ruleSQL = new SQLBuilder(shardingRuleSQL);
        client.execute(ruleSQL.toString());
        registerShardingRule(tableName, shardingRule);

        return true;
    }
}
