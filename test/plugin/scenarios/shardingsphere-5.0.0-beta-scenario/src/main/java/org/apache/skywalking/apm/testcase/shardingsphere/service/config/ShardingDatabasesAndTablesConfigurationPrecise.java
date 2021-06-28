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

package org.apache.skywalking.apm.testcase.shardingsphere.service.config;

import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.ShardingSphereAlgorithmConfiguration;
import org.apache.shardingsphere.infra.config.properties.ConfigurationPropertyKey;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.keygen.KeyGenerateStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.apache.skywalking.apm.testcase.shardingsphere.service.utility.config.DataSourceUtil;
import org.apache.skywalking.apm.testcase.shardingsphere.service.utility.config.ExampleConfiguration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class ShardingDatabasesAndTablesConfigurationPrecise implements ExampleConfiguration {
    
    private static DataSource DATA_SOURCE;
    
    @Override
    public synchronized DataSource createDataSource() throws SQLException {
        if (null == DATA_SOURCE) {
            initDataSource();
        }
        return DATA_SOURCE;
    }
    
    private void initDataSource() throws SQLException {
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();
        shardingRuleConfig.getTables().add(getOrderTableRuleConfiguration());
        shardingRuleConfig.getTables().add(getOrderItemTableRuleConfiguration());
        shardingRuleConfig.getBindingTableGroups().add("t_order, t_order_item");
        shardingRuleConfig.getShardingAlgorithms().put("demo_ds_inline", createDbShardingAlgorithmConfiguration());
        shardingRuleConfig.setDefaultDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("user_id", "demo_ds_inline"));
        Properties tableOrderInlineProperties = new Properties();
        tableOrderInlineProperties.setProperty("algorithm-expression", "t_order_${order_id % 2}");
        shardingRuleConfig.getShardingAlgorithms().put("t_order_inline", new ShardingSphereAlgorithmConfiguration("INLINE", tableOrderInlineProperties));
        Properties tableOrderItemInlineProperties = new Properties();
        tableOrderItemInlineProperties.setProperty("algorithm-expression", "t_order_item_${order_id % 2}");
        shardingRuleConfig.getShardingAlgorithms().put("t_order_item_inline", new ShardingSphereAlgorithmConfiguration("INLINE", tableOrderItemInlineProperties));
        Properties properties = new Properties();
        properties.setProperty(ConfigurationPropertyKey.MAX_CONNECTIONS_SIZE_PER_QUERY.getKey(), "16");
        properties.setProperty(ConfigurationPropertyKey.SQL_SHOW.getKey(), "true");
        DATA_SOURCE = ShardingSphereDataSourceFactory.createDataSource(createDataSourceMap(), Collections.singletonList(shardingRuleConfig), properties);
    }
    
    private static ShardingTableRuleConfiguration getOrderTableRuleConfiguration() {
        ShardingTableRuleConfiguration result = new ShardingTableRuleConfiguration("t_order", "demo_ds_${0..1}.t_order_${[0, 1]}");
        result.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("order_id", "SNOWFLAKE"));
        result.setTableShardingStrategy(new StandardShardingStrategyConfiguration("order_id", "t_order_inline"));
        return result;
    }
    
    private static ShardingTableRuleConfiguration getOrderItemTableRuleConfiguration() {
        ShardingTableRuleConfiguration result = new ShardingTableRuleConfiguration("t_order_item", "demo_ds_${0..1}.t_order_item_${[0, 1]}");
        result.setTableShardingStrategy(new StandardShardingStrategyConfiguration("order_id", "t_order_item_inline"));
        return result;
    }
    
    private ShardingSphereAlgorithmConfiguration createDbShardingAlgorithmConfiguration() {
        Properties dbShardingAlgorithmrProps = new Properties();
        dbShardingAlgorithmrProps.setProperty("algorithm-expression", "demo_ds_${user_id % 2}");
        return new ShardingSphereAlgorithmConfiguration("INLINE", dbShardingAlgorithmrProps);
    }
    
    private static Map<String, DataSource> createDataSourceMap() {
        Map<String, DataSource> result = new HashMap<>();
        result.put("demo_ds_0", DataSourceUtil.getDataSource("demo_ds_0"));
        result.put("demo_ds_1", DataSourceUtil.getDataSource("demo_ds_1"));
        return result;
    }
}
