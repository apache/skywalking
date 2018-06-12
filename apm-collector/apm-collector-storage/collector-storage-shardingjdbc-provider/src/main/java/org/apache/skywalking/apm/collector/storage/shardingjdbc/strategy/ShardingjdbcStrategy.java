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


package org.apache.skywalking.apm.collector.storage.shardingjdbc.strategy;

import java.util.ArrayList;
import java.util.List;

import org.apache.skywalking.apm.collector.core.data.CommonTable;
import org.apache.skywalking.apm.collector.core.storage.TimePyramid;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmListTable;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmTable;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarmListTable;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarmTable;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmListTable;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmTable;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarmListTable;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarmTable;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarmListTable;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarmTable;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmListTable;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmTable;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponentTable;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMappingTable;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetricTable;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationReferenceMetricTable;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTraceTable;
import org.apache.skywalking.apm.collector.storage.table.global.ResponseTimeDistributionTable;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMappingTable;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetricTable;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetricTable;
import org.apache.skywalking.apm.collector.storage.table.jvm.CpuMetricTable;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetricTable;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryMetricTable;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetricTable;
import org.apache.skywalking.apm.collector.storage.table.register.ApplicationTable;
import org.apache.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddressTable;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceNameTable;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentDurationTable;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentTable;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceReferenceMetricTable;

import io.shardingjdbc.core.api.config.TableRuleConfiguration;
import io.shardingjdbc.core.api.config.strategy.InlineShardingStrategyConfiguration;
import io.shardingjdbc.core.api.config.strategy.ShardingStrategyConfiguration;

/**
 * @author linjiaqi
 */
public class ShardingjdbcStrategy {
    
    public final static String SHARDING_DS_PREFIX = "skywalking_ds_";
    
    private int shardingNodeSize;
    
    private String actualDataNodesPrefix;
    
    private String strategyConfigPrefix;
    
    private List<TableRuleConfiguration> tableRules = new ArrayList<TableRuleConfiguration>();
    
    public ShardingjdbcStrategy(int shardingNodeSize) {
        this.shardingNodeSize = shardingNodeSize;
        this.actualDataNodesPrefix = SHARDING_DS_PREFIX + "${0.." + (shardingNodeSize - 1) + "}.";
        this.strategyConfigPrefix = SHARDING_DS_PREFIX + "${";
        
        tableRules.add(tableRule(GlobalTraceTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(SegmentDurationTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(SegmentTable.TABLE, CommonTable.ID.getName()));
        // acp
        tableRules.add(tableRule(ApplicationComponentTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationComponentTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationComponentTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationComponentTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
        // alarm
        tableRules.add(tableRule(ApplicationAlarmListTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationAlarmListTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationAlarmListTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationAlarmListTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationAlarmTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationReferenceAlarmListTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationReferenceAlarmTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(InstanceAlarmListTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(InstanceAlarmTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(InstanceReferenceAlarmListTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(InstanceReferenceAlarmTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(ServiceAlarmListTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(ServiceAlarmTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(ServiceReferenceAlarmListTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(ServiceReferenceAlarmTable.TABLE, CommonTable.ID.getName()));
        // amp
        tableRules.add(tableRule(ApplicationMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
        // ampp
        tableRules.add(tableRule(ApplicationMappingTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationMappingTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationMappingTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationMappingTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
        // armp
        tableRules.add(tableRule(ApplicationReferenceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationReferenceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationReferenceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ApplicationReferenceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
        // cpu
        tableRules.add(tableRule(CpuMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(CpuMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(CpuMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(CpuMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
        // gc
        tableRules.add(tableRule(GCMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(GCMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(GCMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(GCMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
        // imp
        tableRules.add(tableRule(InstanceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(InstanceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(InstanceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(InstanceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
        // impp
        tableRules.add(tableRule(InstanceMappingTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(InstanceMappingTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(InstanceMappingTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(InstanceMappingTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
        // irmp
        tableRules.add(tableRule(InstanceReferenceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(InstanceReferenceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(InstanceReferenceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(InstanceReferenceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
        // memory
        tableRules.add(tableRule(MemoryMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(MemoryMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(MemoryMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(MemoryMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
        // mpool
        tableRules.add(tableRule(MemoryPoolMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(MemoryPoolMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(MemoryPoolMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(MemoryPoolMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
        // register
        tableRules.add(tableRule(ApplicationTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(InstanceTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(NetworkAddressTable.TABLE, CommonTable.ID.getName()));
        tableRules.add(tableRule(ServiceNameTable.TABLE, CommonTable.ID.getName()));
        // rtd
        tableRules.add(tableRule(ResponseTimeDistributionTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ResponseTimeDistributionTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ResponseTimeDistributionTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ResponseTimeDistributionTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
        // smp
        tableRules.add(tableRule(ServiceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ServiceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ServiceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ServiceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
        // srmp
        tableRules.add(tableRule(ServiceReferenceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Day.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ServiceReferenceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Hour.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ServiceReferenceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Minute.getName(), CommonTable.ID.getName()));
        tableRules.add(tableRule(ServiceReferenceMetricTable.TABLE + Const.ID_SPLIT + TimePyramid.Month.getName(), CommonTable.ID.getName()));
    }

    private TableRuleConfiguration tableRule(String tableName, String columnName) {
        TableRuleConfiguration tableRuleConfiguration = new TableRuleConfiguration();
        tableRuleConfiguration.setLogicTable(tableName);
        tableRuleConfiguration.setActualDataNodes(actualDataNodesPrefix + tableName);
        ShardingStrategyConfiguration configuration = new InlineShardingStrategyConfiguration(columnName, strategyConfigPrefix + columnName.hashCode() + " % " + shardingNodeSize + "}");
        tableRuleConfiguration.setDatabaseShardingStrategyConfig(configuration);
        return tableRuleConfiguration;
    }
    
    public List<TableRuleConfiguration> tableRules() {
        return tableRules;
    }
    
    public ShardingStrategyConfiguration defaultDatabaseSharding() {
        return new InlineShardingStrategyConfiguration(CommonTable.ID.getName(), strategyConfigPrefix + CommonTable.ID.getName().hashCode() + " % " + shardingNodeSize + "}");
    }
}
