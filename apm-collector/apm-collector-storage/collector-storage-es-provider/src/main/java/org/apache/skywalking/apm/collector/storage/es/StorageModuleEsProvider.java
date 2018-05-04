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

package org.apache.skywalking.apm.collector.storage.es;

import java.util.UUID;
import org.apache.skywalking.apm.collector.client.ClientException;
import org.apache.skywalking.apm.collector.client.NameSpace;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.cluster.ClusterModule;
import org.apache.skywalking.apm.collector.cluster.service.ModuleListenerService;
import org.apache.skywalking.apm.collector.cluster.service.ModuleRegisterService;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.ICollectorConfig;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.core.module.ModuleConfig;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ModuleStartException;
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
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationAlarmListUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationAlarmUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationComponentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMappingUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.ICpuMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGCMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGlobalTraceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceAlarmUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IMemoryMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.INetworkAddressUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IResponseTimeDistributionUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentDurationUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceAlarmUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceNameServiceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.BatchEsDAO;
import org.apache.skywalking.apm.collector.storage.es.base.define.ElasticSearchStorageInstaller;
import org.apache.skywalking.apm.collector.storage.es.dao.GlobalTraceEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.InstanceHeartBeatEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.SegmentDurationEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.SegmentEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.acp.ApplicationComponentDayEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.acp.ApplicationComponentHourEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.acp.ApplicationComponentMinuteEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.acp.ApplicationComponentMonthEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.ApplicationAlarmEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.ApplicationAlarmListEsDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.ApplicationAlarmListEsHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.ApplicationAlarmListEsMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.ApplicationAlarmListEsMonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.ApplicationReferenceAlarmEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.ApplicationReferenceAlarmListEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.InstanceAlarmEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.InstanceAlarmListEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.InstanceReferenceAlarmEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.InstanceReferenceAlarmListEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.ServiceAlarmEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.ServiceAlarmListEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.ServiceReferenceAlarmEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.ServiceReferenceAlarmListEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.amp.ApplicationDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.amp.ApplicationHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.amp.ApplicationMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.amp.ApplicationMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ampp.ApplicationMappingDayEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ampp.ApplicationMappingHourEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ampp.ApplicationMappingMinuteEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ampp.ApplicationMappingMonthEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.armp.ApplicationReferenceDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.armp.ApplicationReferenceHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.armp.ApplicationReferenceMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.armp.ApplicationReferenceMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.cache.ApplicationEsCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.cache.InstanceEsCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.cache.NetworkAddressEsCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.cache.ServiceNameEsCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.cpu.CpuDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.cpu.CpuHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.cpu.CpuMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.cpu.CpuMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.gc.GCDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.gc.GCHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.gc.GCMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.gc.GCMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.imp.InstanceDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.imp.InstanceHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.imp.InstanceMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.imp.InstanceMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.impp.InstanceMappingDayEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.impp.InstanceMappingHourEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.impp.InstanceMappingMinuteEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.impp.InstanceMappingMonthEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.irmp.InstanceReferenceDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.irmp.InstanceReferenceHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.irmp.InstanceReferenceMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.irmp.InstanceReferenceMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.memory.MemoryDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.memory.MemoryHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.memory.MemoryMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.memory.MemoryMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.mpool.MemoryPoolDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.mpool.MemoryPoolHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.mpool.MemoryPoolMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.mpool.MemoryPoolMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.register.ApplicationRegisterEsDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.register.InstanceRegisterEsDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.register.NetworkAddressRegisterEsDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.register.ServiceNameRegisterEsDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.rtd.ResponseTimeDistributionDayEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.rtd.ResponseTimeDistributionHourEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.rtd.ResponseTimeDistributionMinuteEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.rtd.ResponseTimeDistributionMonthEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.smp.ServiceDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.smp.ServiceHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.smp.ServiceMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.smp.ServiceMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.srmp.ServiceReferenceDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.srmp.ServiceReferenceHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.srmp.ServiceReferenceMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.srmp.ServiceReferenceMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.ApplicationAlarmEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.ApplicationAlarmListEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.ApplicationComponentEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.ApplicationMappingEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.ApplicationMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.ApplicationReferenceMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.CpuMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.GCMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.GlobalTraceEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.InstanceAlarmEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.InstanceEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.InstanceMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.MemoryMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.NetworkAddressEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.ResponseTimeDistributionEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.SegmentDurationEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.SegmentEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.ServiceAlarmEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.ServiceMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.ServiceNameServiceEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.ServiceReferenceEsMetricUIDAO;

/**
 * @author peng-yongsheng
 */
public class StorageModuleEsProvider extends ModuleProvider {

    static final String NAME = "elasticsearch";
    private final StorageModuleEsConfig config;
    private final NameSpace nameSpace;
    private ElasticSearchClient elasticSearchClient;
    private DataTTLKeeperTimer deleteTimer;

    public StorageModuleEsProvider() {
        super();
        this.config = new StorageModuleEsConfig();
        this.nameSpace = new NameSpace();
    }

    @Override public String name() {
        return NAME;
    }

    @Override public Class<? extends Module> module() {
        return StorageModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        elasticSearchClient = new ElasticSearchClient(config.getClusterName(), config.getClusterTransportSniffer(), config.getClusterNodes(), nameSpace);

        this.registerServiceImplementation(IBatchDAO.class, new BatchEsDAO(elasticSearchClient));
        registerCacheDAO();
        registerRegisterDAO();
        registerPersistenceDAO();
        registerUiDAO();
        registerAlarmDAO();
    }

    @Override
    public void start() throws ModuleStartException {
        try {
            String namespace = getManager().find(ConfigurationModule.NAME).getService(ICollectorConfig.class).getNamespace();
            nameSpace.setNameSpace(namespace);
            elasticSearchClient.initialize();

            ElasticSearchStorageInstaller installer = new ElasticSearchStorageInstaller(config.getIndexShardsNumber(), config.getIndexReplicasNumber(), config.isHighPerformanceMode());
            installer.install(elasticSearchClient);
        } catch (ClientException | StorageException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }

        String uuId = UUID.randomUUID().toString();
        ModuleRegisterService moduleRegisterService = getManager().find(ClusterModule.NAME).getService(ModuleRegisterService.class);
        moduleRegisterService.register(StorageModule.NAME, this.name(), new StorageModuleEsRegistration(uuId, 0));

        StorageModuleEsNamingListener namingListener = new StorageModuleEsNamingListener();
        ModuleListenerService moduleListenerService = getManager().find(ClusterModule.NAME).getService(ModuleListenerService.class);
        moduleListenerService.addListener(namingListener);

        Integer beforeDay = config.getTtl() == 0 ? 3 : config.getTtl();
        deleteTimer = new DataTTLKeeperTimer(getManager(), namingListener, uuId + 0, beforeDay);
    }

    @Override
    public void notifyAfterCompleted() {
        deleteTimer.start();
    }

    @Override
    public String[] requiredModules() {
        return new String[] {ClusterModule.NAME, ConfigurationModule.NAME, RemoteModule.NAME};
    }

    private void registerCacheDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IApplicationCacheDAO.class, new ApplicationEsCacheDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceCacheDAO.class, new InstanceEsCacheDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceNameCacheDAO.class, new ServiceNameEsCacheDAO(elasticSearchClient));
        this.registerServiceImplementation(INetworkAddressCacheDAO.class, new NetworkAddressEsCacheDAO(elasticSearchClient));
    }

    private void registerRegisterDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(INetworkAddressRegisterDAO.class, new NetworkAddressRegisterEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationRegisterDAO.class, new ApplicationRegisterEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceRegisterDAO.class, new InstanceRegisterEsDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceNameRegisterDAO.class, new ServiceNameRegisterEsDAO(elasticSearchClient));
    }

    private void registerPersistenceDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(ICpuMinuteMetricPersistenceDAO.class, new CpuMinuteMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(ICpuHourMetricPersistenceDAO.class, new CpuHourMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(ICpuDayMetricPersistenceDAO.class, new CpuDayMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(ICpuMonthMetricPersistenceDAO.class, new CpuMonthMetricEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IGCMinuteMetricPersistenceDAO.class, new GCMinuteMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IGCHourMetricPersistenceDAO.class, new GCHourMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IGCDayMetricPersistenceDAO.class, new GCDayMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IGCMonthMetricPersistenceDAO.class, new GCMonthMetricEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IMemoryMinuteMetricPersistenceDAO.class, new MemoryMinuteMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IMemoryHourMetricPersistenceDAO.class, new MemoryHourMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IMemoryDayMetricPersistenceDAO.class, new MemoryDayMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IMemoryMonthMetricPersistenceDAO.class, new MemoryMonthMetricEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IMemoryPoolMinuteMetricPersistenceDAO.class, new MemoryPoolMinuteMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IMemoryPoolHourMetricPersistenceDAO.class, new MemoryPoolHourMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IMemoryPoolDayMetricPersistenceDAO.class, new MemoryPoolDayMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IMemoryPoolMonthMetricPersistenceDAO.class, new MemoryPoolMonthMetricEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IApplicationComponentMinutePersistenceDAO.class, new ApplicationComponentMinuteEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationComponentHourPersistenceDAO.class, new ApplicationComponentHourEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationComponentDayPersistenceDAO.class, new ApplicationComponentDayEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationComponentMonthPersistenceDAO.class, new ApplicationComponentMonthEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IApplicationMappingMinutePersistenceDAO.class, new ApplicationMappingMinuteEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationMappingHourPersistenceDAO.class, new ApplicationMappingHourEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationMappingDayPersistenceDAO.class, new ApplicationMappingDayEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationMappingMonthPersistenceDAO.class, new ApplicationMappingMonthEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IInstanceMappingMinutePersistenceDAO.class, new InstanceMappingMinuteEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceMappingHourPersistenceDAO.class, new InstanceMappingHourEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceMappingDayPersistenceDAO.class, new InstanceMappingDayEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceMappingMonthPersistenceDAO.class, new InstanceMappingMonthEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IGlobalTracePersistenceDAO.class, new GlobalTraceEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IResponseTimeDistributionMinutePersistenceDAO.class, new ResponseTimeDistributionMinuteEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IResponseTimeDistributionHourPersistenceDAO.class, new ResponseTimeDistributionHourEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IResponseTimeDistributionDayPersistenceDAO.class, new ResponseTimeDistributionDayEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IResponseTimeDistributionMonthPersistenceDAO.class, new ResponseTimeDistributionMonthEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IApplicationMinuteMetricPersistenceDAO.class, new ApplicationMinuteMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationHourMetricPersistenceDAO.class, new ApplicationHourMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationDayMetricPersistenceDAO.class, new ApplicationDayMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationMonthMetricPersistenceDAO.class, new ApplicationMonthMetricEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IApplicationReferenceMinuteMetricPersistenceDAO.class, new ApplicationReferenceMinuteMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationReferenceHourMetricPersistenceDAO.class, new ApplicationReferenceHourMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationReferenceDayMetricPersistenceDAO.class, new ApplicationReferenceDayMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationReferenceMonthMetricPersistenceDAO.class, new ApplicationReferenceMonthMetricEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(ISegmentDurationPersistenceDAO.class, new SegmentDurationEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(ISegmentPersistenceDAO.class, new SegmentEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IServiceMinuteMetricPersistenceDAO.class, new ServiceMinuteMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceHourMetricPersistenceDAO.class, new ServiceHourMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceDayMetricPersistenceDAO.class, new ServiceDayMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceMonthMetricPersistenceDAO.class, new ServiceMonthMetricEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IServiceReferenceMinuteMetricPersistenceDAO.class, new ServiceReferenceMinuteMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceReferenceHourMetricPersistenceDAO.class, new ServiceReferenceHourMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceReferenceDayMetricPersistenceDAO.class, new ServiceReferenceDayMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceReferenceMonthMetricPersistenceDAO.class, new ServiceReferenceMonthMetricEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IInstanceMinuteMetricPersistenceDAO.class, new InstanceMinuteMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceHourMetricPersistenceDAO.class, new InstanceHourMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceDayMetricPersistenceDAO.class, new InstanceDayMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceMonthMetricPersistenceDAO.class, new InstanceMonthMetricEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IInstanceReferenceMinuteMetricPersistenceDAO.class, new InstanceReferenceMinuteMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceReferenceHourMetricPersistenceDAO.class, new InstanceReferenceHourMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceReferenceDayMetricPersistenceDAO.class, new InstanceReferenceDayMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceReferenceMonthMetricPersistenceDAO.class, new InstanceReferenceMonthMetricEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IInstanceHeartBeatPersistenceDAO.class, new InstanceHeartBeatEsPersistenceDAO(elasticSearchClient));
    }

    private void registerUiDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IInstanceUIDAO.class, new InstanceEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(INetworkAddressUIDAO.class, new NetworkAddressEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceNameServiceUIDAO.class, new ServiceNameServiceEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceMetricUIDAO.class, new ServiceMetricEsUIDAO(elasticSearchClient));

        this.registerServiceImplementation(ICpuMetricUIDAO.class, new CpuMetricEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IGCMetricUIDAO.class, new GCMetricEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IMemoryMetricUIDAO.class, new MemoryMetricEsUIDAO(elasticSearchClient));

        this.registerServiceImplementation(IGlobalTraceUIDAO.class, new GlobalTraceEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceMetricUIDAO.class, new InstanceMetricEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationComponentUIDAO.class, new ApplicationComponentEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationMappingUIDAO.class, new ApplicationMappingEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationMetricUIDAO.class, new ApplicationMetricEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationReferenceMetricUIDAO.class, new ApplicationReferenceMetricEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(ISegmentDurationUIDAO.class, new SegmentDurationEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(ISegmentUIDAO.class, new SegmentEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceReferenceMetricUIDAO.class, new ServiceReferenceEsMetricUIDAO(elasticSearchClient));

        this.registerServiceImplementation(IApplicationAlarmUIDAO.class, new ApplicationAlarmEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceAlarmUIDAO.class, new InstanceAlarmEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceAlarmUIDAO.class, new ServiceAlarmEsUIDAO(elasticSearchClient));

        this.registerServiceImplementation(IApplicationAlarmListUIDAO.class, new ApplicationAlarmListEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IResponseTimeDistributionUIDAO.class, new ResponseTimeDistributionEsUIDAO(elasticSearchClient));
    }

    private void registerAlarmDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IServiceReferenceAlarmPersistenceDAO.class, new ServiceReferenceAlarmEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceReferenceAlarmListPersistenceDAO.class, new ServiceReferenceAlarmListEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceReferenceAlarmPersistenceDAO.class, new InstanceReferenceAlarmEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceReferenceAlarmListPersistenceDAO.class, new InstanceReferenceAlarmListEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationReferenceAlarmPersistenceDAO.class, new ApplicationReferenceAlarmEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationReferenceAlarmListPersistenceDAO.class, new ApplicationReferenceAlarmListEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IServiceAlarmPersistenceDAO.class, new ServiceAlarmEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceAlarmListPersistenceDAO.class, new ServiceAlarmListEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceAlarmPersistenceDAO.class, new InstanceAlarmEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceAlarmListPersistenceDAO.class, new InstanceAlarmListEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationAlarmPersistenceDAO.class, new ApplicationAlarmEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IApplicationAlarmListMinutePersistenceDAO.class, new ApplicationAlarmListEsMinutePersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationAlarmListHourPersistenceDAO.class, new ApplicationAlarmListEsHourPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationAlarmListDayPersistenceDAO.class, new ApplicationAlarmListEsDayPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationAlarmListMonthPersistenceDAO.class, new ApplicationAlarmListEsMonthPersistenceDAO(elasticSearchClient));
    }
}
