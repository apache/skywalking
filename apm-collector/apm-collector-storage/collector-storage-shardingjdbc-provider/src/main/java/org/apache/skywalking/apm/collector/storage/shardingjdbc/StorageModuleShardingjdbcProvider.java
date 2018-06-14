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

package org.apache.skywalking.apm.collector.storage.shardingjdbc;

import io.shardingjdbc.core.api.config.ShardingRuleConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientConfig;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.cluster.ClusterModule;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.core.module.ModuleConfig;
import org.apache.skywalking.apm.collector.core.module.ModuleDefine;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.storage.StorageException;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceHeartBeatPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentDurationPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.acp.IApplicationComponentDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.acp.IApplicationComponentHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.acp.IApplicationComponentMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.acp.IApplicationComponentMonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmListDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmListHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmListMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmListMonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.amp.IApplicationDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.amp.IApplicationHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.amp.IApplicationMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.amp.IApplicationMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ampp.IApplicationMappingDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ampp.IApplicationMappingHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ampp.IApplicationMappingMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ampp.IApplicationMappingMonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.armp.IApplicationReferenceDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.armp.IApplicationReferenceHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.armp.IApplicationReferenceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.armp.IApplicationReferenceMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.cache.IApplicationCacheDAO;
import org.apache.skywalking.apm.collector.storage.dao.cache.IInstanceCacheDAO;
import org.apache.skywalking.apm.collector.storage.dao.cache.INetworkAddressCacheDAO;
import org.apache.skywalking.apm.collector.storage.dao.cache.IServiceNameCacheDAO;
import org.apache.skywalking.apm.collector.storage.dao.cpu.ICpuDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.cpu.ICpuHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.cpu.ICpuMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.cpu.ICpuMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gc.IGCDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gc.IGCHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gc.IGCMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gc.IGCMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.imp.IInstanceDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.imp.IInstanceHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.imp.IInstanceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.imp.IInstanceMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.impp.IInstanceMappingDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.impp.IInstanceMappingHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.impp.IInstanceMappingMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.impp.IInstanceMappingMonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.irmp.IInstanceReferenceDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.irmp.IInstanceReferenceHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.irmp.IInstanceReferenceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.irmp.IInstanceReferenceMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.memory.IMemoryDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.memory.IMemoryHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.memory.IMemoryMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.memory.IMemoryMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpool.IMemoryPoolDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpool.IMemoryPoolHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpool.IMemoryPoolMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpool.IMemoryPoolMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.register.IApplicationRegisterDAO;
import org.apache.skywalking.apm.collector.storage.dao.register.IInstanceRegisterDAO;
import org.apache.skywalking.apm.collector.storage.dao.register.INetworkAddressRegisterDAO;
import org.apache.skywalking.apm.collector.storage.dao.register.IServiceNameRegisterDAO;
import org.apache.skywalking.apm.collector.storage.dao.rtd.IResponseTimeDistributionDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.rtd.IResponseTimeDistributionHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.rtd.IResponseTimeDistributionMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.rtd.IResponseTimeDistributionMonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.smp.IServiceDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.smp.IServiceHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.smp.IServiceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.smp.IServiceMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.srmp.IServiceReferenceDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.srmp.IServiceReferenceHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.srmp.IServiceReferenceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.srmp.IServiceReferenceMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.*;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.BatchShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcStorageInstaller;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.GlobalTraceShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.InstanceHeartBeatShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.SegmentDurationShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.SegmentShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.acp.ApplicationComponentDayShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.acp.ApplicationComponentHourShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.acp.ApplicationComponentMinuteShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.acp.ApplicationComponentMonthShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.ApplicationAlarmListShardingjdbcDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.ApplicationAlarmListShardingjdbcHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.ApplicationAlarmListShardingjdbcMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.ApplicationAlarmListShardingjdbcMonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.ApplicationAlarmShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.ApplicationReferenceAlarmListShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.ApplicationReferenceAlarmShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.InstanceAlarmListShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.InstanceAlarmShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.InstanceReferenceAlarmListShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.InstanceReferenceAlarmShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.ServiceAlarmListShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.ServiceAlarmShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.ServiceReferenceAlarmListShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm.ServiceReferenceAlarmShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.amp.ApplicationDayMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.amp.ApplicationHourMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.amp.ApplicationMinuteMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.amp.ApplicationMonthMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.ampp.ApplicationMappingDayShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.ampp.ApplicationMappingHourShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.ampp.ApplicationMappingMinuteShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.ampp.ApplicationMappingMonthShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.armp.ApplicationReferenceDayMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.armp.ApplicationReferenceHourMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.armp.ApplicationReferenceMinuteMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.armp.ApplicationReferenceMonthMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.cache.ApplicationShardingjdbcCacheDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.cache.InstanceShardingjdbcCacheDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.cache.NetworkAddressShardingjdbcCacheDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.cache.ServiceNameShardingjdbcCacheDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.cpu.CpuDayMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.cpu.CpuHourMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.cpu.CpuMinuteMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.cpu.CpuMonthMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.gc.GCDayMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.gc.GCHourMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.gc.GCMinuteMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.gc.GCMonthMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.imp.InstanceDayMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.imp.InstanceHourMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.imp.InstanceMinuteMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.imp.InstanceMonthMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.impp.InstanceMappingDayShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.impp.InstanceMappingHourShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.impp.InstanceMappingMinuteShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.impp.InstanceMappingMonthShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.irmp.InstanceReferenceDayMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.irmp.InstanceReferenceHourMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.irmp.InstanceReferenceMinuteMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.irmp.InstanceReferenceMonthMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.memory.MemoryDayMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.memory.MemoryHourMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.memory.MemoryMinuteMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.memory.MemoryMonthMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.mpool.MemoryPoolDayMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.mpool.MemoryPoolHourMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.mpool.MemoryPoolMinuteMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.mpool.MemoryPoolMonthMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.register.ApplicationRegisterShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.register.InstanceRegisterShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.register.NetworkAddressRegisterShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.register.ServiceNameRegisterShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.rtd.ResponseTimeDistributionDayShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.rtd.ResponseTimeDistributionHourShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.rtd.ResponseTimeDistributionMinuteShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.rtd.ResponseTimeDistributionMonthShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.smp.ServiceDayMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.smp.ServiceHourMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.smp.ServiceMinuteMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.smp.ServiceMonthMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.srmp.ServiceReferenceDayMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.srmp.ServiceReferenceHourMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.srmp.ServiceReferenceMinuteMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.srmp.ServiceReferenceMonthMetricShardingjdbcPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.ui.*;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.strategy.ShardingjdbcStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author linjiaqi
 */
public class StorageModuleShardingjdbcProvider extends ModuleProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(StorageModuleShardingjdbcProvider.class);
    
    private ShardingjdbcClient shardingjdbcClient;
    private final StorageModuleShardingjdbcConfig config;
    
    public StorageModuleShardingjdbcProvider() {
        this.config = new StorageModuleShardingjdbcConfig();
    }
    
    @Override
    public String name() {
        return "shardingjdbc";
    }
    
    @Override
    public Class<? extends ModuleDefine> module() {
        return StorageModule.class;
    }
    
    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }
    
    @Override
    public void prepare() throws ServiceNotProvidedException {
        Map<String, ShardingjdbcClientConfig> shardingjdbcClientConfigs = createShardingjdbcClientConfigs();
        ShardingRuleConfiguration shardingRuleConfig = createShardingRule(shardingjdbcClientConfigs.size());
        shardingjdbcClient = new ShardingjdbcClient(shardingjdbcClientConfigs, shardingRuleConfig);
        
        this.registerServiceImplementation(IBatchDAO.class, new BatchShardingjdbcDAO(shardingjdbcClient));
        registerCacheDAO();
        registerRegisterDAO();
        registerPersistenceDAO();
        registerUiDAO();
        registerAlarmDAO();
    }
    
    @Override
    public void start() {
        try {
            shardingjdbcClient.initialize();
            
            ShardingjdbcStorageInstaller installer = new ShardingjdbcStorageInstaller(false);
            installer.install(shardingjdbcClient);
        } catch (ShardingjdbcClientException | StorageException e) {
            logger.error(e.getMessage(), e);
        }
    }
    
    @Override
    public void notifyAfterCompleted() {
    }
    
    @Override
    public String[] requiredModules() {
        return new String[]{ClusterModule.NAME, ConfigurationModule.NAME, RemoteModule.NAME};
    }
    
    private Map<String, ShardingjdbcClientConfig> createShardingjdbcClientConfigs() {
        Map<String, ShardingjdbcClientConfig> shardingjdbcClientConfigs = new HashMap<String, ShardingjdbcClientConfig>();
        if (StringUtils.isEmpty(config.getUrl())) {
            return shardingjdbcClientConfigs;
        }
        String driverClass = config.getDriverClass();
        String[] url = config.getUrl().split(",");
        String[] userName = config.getUserName().split(",");
        String[] password = config.getPassword().split(",");
        for (int i = 0; i < url.length; i++) {
            shardingjdbcClientConfigs.put(ShardingjdbcStrategy.SHARDING_DS_PREFIX + i, new ShardingjdbcClientConfig(driverClass, url[i].trim(), userName[i].trim(), password[i].trim()));
            logger.info("create datasource: {}, url: {}", ShardingjdbcStrategy.SHARDING_DS_PREFIX + i, url[i].trim());
        }
        return shardingjdbcClientConfigs;
    }
    
    private ShardingRuleConfiguration createShardingRule(int shardingNodeSize) {
        ShardingjdbcStrategy strategy = new ShardingjdbcStrategy(shardingNodeSize);
        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();
        shardingRuleConfig.getTableRuleConfigs().addAll(strategy.tableRules());
        shardingRuleConfig.setDefaultDatabaseShardingStrategyConfig(strategy.defaultDatabaseSharding());
        return shardingRuleConfig;
    }
    
    private void registerCacheDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IApplicationCacheDAO.class, new ApplicationShardingjdbcCacheDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceCacheDAO.class, new InstanceShardingjdbcCacheDAO(shardingjdbcClient));
        this.registerServiceImplementation(IServiceNameCacheDAO.class, new ServiceNameShardingjdbcCacheDAO(shardingjdbcClient));
        this.registerServiceImplementation(INetworkAddressCacheDAO.class, new NetworkAddressShardingjdbcCacheDAO(shardingjdbcClient));
    }
    
    private void registerRegisterDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(INetworkAddressRegisterDAO.class, new NetworkAddressRegisterShardingjdbcDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationRegisterDAO.class, new ApplicationRegisterShardingjdbcDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceRegisterDAO.class, new InstanceRegisterShardingjdbcDAO(shardingjdbcClient));
        this.registerServiceImplementation(IServiceNameRegisterDAO.class, new ServiceNameRegisterShardingjdbcDAO(shardingjdbcClient));
    }
    
    private void registerPersistenceDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(ICpuMinuteMetricPersistenceDAO.class, new CpuMinuteMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(ICpuHourMetricPersistenceDAO.class, new CpuHourMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(ICpuDayMetricPersistenceDAO.class, new CpuDayMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(ICpuMonthMetricPersistenceDAO.class, new CpuMonthMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IGCMinuteMetricPersistenceDAO.class, new GCMinuteMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IGCHourMetricPersistenceDAO.class, new GCHourMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IGCDayMetricPersistenceDAO.class, new GCDayMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IGCMonthMetricPersistenceDAO.class, new GCMonthMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IMemoryMinuteMetricPersistenceDAO.class, new MemoryMinuteMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IMemoryHourMetricPersistenceDAO.class, new MemoryHourMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IMemoryDayMetricPersistenceDAO.class, new MemoryDayMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IMemoryMonthMetricPersistenceDAO.class, new MemoryMonthMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IMemoryPoolMinuteMetricPersistenceDAO.class, new MemoryPoolMinuteMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IMemoryPoolHourMetricPersistenceDAO.class, new MemoryPoolHourMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IMemoryPoolDayMetricPersistenceDAO.class, new MemoryPoolDayMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IMemoryPoolMonthMetricPersistenceDAO.class, new MemoryPoolMonthMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IGlobalTracePersistenceDAO.class, new GlobalTraceShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IResponseTimeDistributionMinutePersistenceDAO.class, new ResponseTimeDistributionMinuteShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IResponseTimeDistributionHourPersistenceDAO.class, new ResponseTimeDistributionHourShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IResponseTimeDistributionDayPersistenceDAO.class, new ResponseTimeDistributionDayShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IResponseTimeDistributionMonthPersistenceDAO.class, new ResponseTimeDistributionMonthShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(ISegmentDurationPersistenceDAO.class, new SegmentDurationShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(ISegmentPersistenceDAO.class, new SegmentShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceHeartBeatPersistenceDAO.class, new InstanceHeartBeatShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IApplicationComponentMinutePersistenceDAO.class, new ApplicationComponentMinuteShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationComponentHourPersistenceDAO.class, new ApplicationComponentHourShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationComponentDayPersistenceDAO.class, new ApplicationComponentDayShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationComponentMonthPersistenceDAO.class, new ApplicationComponentMonthShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IApplicationMappingMinutePersistenceDAO.class, new ApplicationMappingMinuteShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationMappingHourPersistenceDAO.class, new ApplicationMappingHourShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationMappingDayPersistenceDAO.class, new ApplicationMappingDayShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationMappingMonthPersistenceDAO.class, new ApplicationMappingMonthShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IInstanceMappingMinutePersistenceDAO.class, new InstanceMappingMinuteShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceMappingHourPersistenceDAO.class, new InstanceMappingHourShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceMappingDayPersistenceDAO.class, new InstanceMappingDayShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceMappingMonthPersistenceDAO.class, new InstanceMappingMonthShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IApplicationMinuteMetricPersistenceDAO.class, new ApplicationMinuteMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationHourMetricPersistenceDAO.class, new ApplicationHourMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationDayMetricPersistenceDAO.class, new ApplicationDayMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationMonthMetricPersistenceDAO.class, new ApplicationMonthMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IApplicationReferenceMinuteMetricPersistenceDAO.class, new ApplicationReferenceMinuteMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationReferenceHourMetricPersistenceDAO.class, new ApplicationReferenceHourMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationReferenceDayMetricPersistenceDAO.class, new ApplicationReferenceDayMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationReferenceMonthMetricPersistenceDAO.class, new ApplicationReferenceMonthMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IInstanceMinuteMetricPersistenceDAO.class, new InstanceMinuteMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceHourMetricPersistenceDAO.class, new InstanceHourMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceDayMetricPersistenceDAO.class, new InstanceDayMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceMonthMetricPersistenceDAO.class, new InstanceMonthMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IInstanceReferenceMinuteMetricPersistenceDAO.class, new InstanceReferenceMinuteMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceReferenceHourMetricPersistenceDAO.class, new InstanceReferenceHourMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceReferenceDayMetricPersistenceDAO.class, new InstanceReferenceDayMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceReferenceMonthMetricPersistenceDAO.class, new InstanceReferenceMonthMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IServiceMinuteMetricPersistenceDAO.class, new ServiceMinuteMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IServiceHourMetricPersistenceDAO.class, new ServiceHourMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IServiceDayMetricPersistenceDAO.class, new ServiceDayMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IServiceMonthMetricPersistenceDAO.class, new ServiceMonthMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IServiceReferenceMinuteMetricPersistenceDAO.class, new ServiceReferenceMinuteMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IServiceReferenceHourMetricPersistenceDAO.class, new ServiceReferenceHourMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IServiceReferenceDayMetricPersistenceDAO.class, new ServiceReferenceDayMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IServiceReferenceMonthMetricPersistenceDAO.class, new ServiceReferenceMonthMetricShardingjdbcPersistenceDAO(shardingjdbcClient));
    }
    
    private void registerUiDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IInstanceUIDAO.class, new InstanceShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(INetworkAddressUIDAO.class, new NetworkAddressShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(IServiceNameServiceUIDAO.class, new ServiceNameServiceShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(IServiceMetricUIDAO.class, new ServiceMetricShardingjdbcUIDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(ICpuMetricUIDAO.class, new CpuMetricShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(IGCMetricUIDAO.class, new GCMetricShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(IMemoryMetricUIDAO.class, new MemoryMetricShardingjdbcUIDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IGlobalTraceUIDAO.class, new GlobalTraceShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceMetricUIDAO.class, new InstanceMetricShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationComponentUIDAO.class, new ApplicationComponentShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationMappingUIDAO.class, new ApplicationMappingShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationMetricUIDAO.class, new ApplicationMetricShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationReferenceMetricUIDAO.class, new ApplicationReferenceMetricShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(ISegmentDurationUIDAO.class, new SegmentDurationShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(ISegmentUIDAO.class, new SegmentShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(IServiceReferenceMetricUIDAO.class, new ServiceReferenceShardingjdbcMetricUIDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IApplicationAlarmUIDAO.class, new ApplicationAlarmShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceAlarmUIDAO.class, new InstanceAlarmShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(IServiceAlarmUIDAO.class, new ServiceAlarmShardingjdbcUIDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IApplicationAlarmListUIDAO.class, new ApplicationAlarmListShardingjdbcUIDAO(shardingjdbcClient));
        this.registerServiceImplementation(IResponseTimeDistributionUIDAO.class, new ResponseTimeDistributionShardingjdbcUIDAO(shardingjdbcClient));
    }
    
    private void registerAlarmDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IServiceReferenceAlarmPersistenceDAO.class, new ServiceReferenceAlarmShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IServiceReferenceAlarmListPersistenceDAO.class, new ServiceReferenceAlarmListShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceReferenceAlarmPersistenceDAO.class, new InstanceReferenceAlarmShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceReferenceAlarmListPersistenceDAO.class, new InstanceReferenceAlarmListShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationReferenceAlarmPersistenceDAO.class, new ApplicationReferenceAlarmShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationReferenceAlarmListPersistenceDAO.class, new ApplicationReferenceAlarmListShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IServiceAlarmPersistenceDAO.class, new ServiceAlarmShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IServiceAlarmListPersistenceDAO.class, new ServiceAlarmListShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceAlarmPersistenceDAO.class, new InstanceAlarmShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IInstanceAlarmListPersistenceDAO.class, new InstanceAlarmListShardingjdbcPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationAlarmPersistenceDAO.class, new ApplicationAlarmShardingjdbcPersistenceDAO(shardingjdbcClient));
        
        this.registerServiceImplementation(IApplicationAlarmListMinutePersistenceDAO.class, new ApplicationAlarmListShardingjdbcMinutePersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationAlarmListHourPersistenceDAO.class, new ApplicationAlarmListShardingjdbcHourPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationAlarmListDayPersistenceDAO.class, new ApplicationAlarmListShardingjdbcDayPersistenceDAO(shardingjdbcClient));
        this.registerServiceImplementation(IApplicationAlarmListMonthPersistenceDAO.class, new ApplicationAlarmListShardingjdbcMonthPersistenceDAO(shardingjdbcClient));
    }
}
