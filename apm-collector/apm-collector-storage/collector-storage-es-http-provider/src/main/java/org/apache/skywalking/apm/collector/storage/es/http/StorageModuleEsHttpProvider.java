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

package org.apache.skywalking.apm.collector.storage.es.http;

import java.util.Properties;
import java.util.UUID;
import org.apache.skywalking.apm.collector.client.ClientException;
import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.cluster.ClusterModule;
import org.apache.skywalking.apm.collector.cluster.service.ModuleListenerService;
import org.apache.skywalking.apm.collector.cluster.service.ModuleRegisterService;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmListPersistenceDAO;
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
import org.apache.skywalking.apm.collector.storage.dao.cpump.ICpuDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.cpump.ICpuHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.cpump.ICpuMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.cpump.ICpuMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.cpump.ICpuSecondMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gcmp.IGCDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gcmp.IGCHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gcmp.IGCMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gcmp.IGCMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gcmp.IGCSecondMetricPersistenceDAO;
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
import org.apache.skywalking.apm.collector.storage.dao.memorymp.IMemoryDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.memorymp.IMemoryHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.memorymp.IMemoryMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.memorymp.IMemoryMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.memorymp.IMemorySecondMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpoolmp.IMemoryPoolDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpoolmp.IMemoryPoolHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpoolmp.IMemoryPoolMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpoolmp.IMemoryPoolMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpoolmp.IMemoryPoolSecondMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.register.IApplicationRegisterDAO;
import org.apache.skywalking.apm.collector.storage.dao.register.IInstanceRegisterDAO;
import org.apache.skywalking.apm.collector.storage.dao.register.INetworkAddressRegisterDAO;
import org.apache.skywalking.apm.collector.storage.dao.register.IServiceNameRegisterDAO;
import org.apache.skywalking.apm.collector.storage.dao.smp.IServiceDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.smp.IServiceHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.smp.IServiceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.smp.IServiceMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.srmp.IServiceReferenceDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.srmp.IServiceReferenceHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.srmp.IServiceReferenceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.srmp.IServiceReferenceMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationComponentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMappingUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.ICpuMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGCMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGlobalTraceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IMemoryMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IMemoryPoolMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.INetworkAddressUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentDurationUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceNameServiceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.BatchEsDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.define.ElasticSearchStorageInstaller;
import org.apache.skywalking.apm.collector.storage.es.http.dao.GlobalTraceEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.InstanceHeartBeatEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.SegmentDurationEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.SegmentEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.acp.ApplicationComponentDayEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.acp.ApplicationComponentHourEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.acp.ApplicationComponentMinuteEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.acp.ApplicationComponentMonthEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.alarm.ApplicationAlarmEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.alarm.ApplicationAlarmListEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.alarm.ApplicationReferenceAlarmEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.alarm.ApplicationReferenceAlarmListEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.alarm.InstanceAlarmEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.alarm.InstanceAlarmListEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.alarm.InstanceReferenceAlarmEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.alarm.InstanceReferenceAlarmListEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.alarm.ServiceAlarmEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.alarm.ServiceAlarmListEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.alarm.ServiceReferenceAlarmEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.alarm.ServiceReferenceAlarmListEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.amp.ApplicationDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.amp.ApplicationHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.amp.ApplicationMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.amp.ApplicationMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ampp.ApplicationMappingDayEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ampp.ApplicationMappingHourEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ampp.ApplicationMappingMinuteEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ampp.ApplicationMappingMonthEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.armp.ApplicationReferenceDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.armp.ApplicationReferenceHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.armp.ApplicationReferenceMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.armp.ApplicationReferenceMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.cache.ApplicationEsCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.cache.InstanceEsCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.cache.NetworkAddressEsCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.cache.ServiceNameEsCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.cpump.CpuDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.cpump.CpuHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.cpump.CpuMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.cpump.CpuMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.cpump.CpuSecondMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.gcmp.GCDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.gcmp.GCHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.gcmp.GCMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.gcmp.GCMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.gcmp.GCSecondMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.imp.InstanceDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.imp.InstanceHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.imp.InstanceMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.imp.InstanceMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.impp.InstanceMappingDayEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.impp.InstanceMappingHourEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.impp.InstanceMappingMinuteEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.impp.InstanceMappingMonthEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.irmp.InstanceReferenceDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.irmp.InstanceReferenceHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.irmp.InstanceReferenceMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.irmp.InstanceReferenceMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.memorymp.MemoryDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.memorymp.MemoryHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.memorymp.MemoryMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.memorymp.MemoryMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.memorymp.MemorySecondMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.mpoolmp.MemoryPoolDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.mpoolmp.MemoryPoolHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.mpoolmp.MemoryPoolMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.mpoolmp.MemoryPoolMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.mpoolmp.MemoryPoolSecondMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.register.ApplicationRegisterEsDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.register.InstanceRegisterEsDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.register.NetworkAddressRegisterEsDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.register.ServiceNameRegisterEsDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.smp.ServiceDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.smp.ServiceHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.smp.ServiceMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.smp.ServiceMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.srmp.ServiceReferenceDayMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.srmp.ServiceReferenceHourMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.srmp.ServiceReferenceMinuteMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.srmp.ServiceReferenceMonthMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.ApplicationComponentEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.ApplicationMappingEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.ApplicationMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.ApplicationReferenceMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.CpuMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.GCMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.GlobalTraceEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.InstanceEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.InstanceMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.MemoryMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.MemoryPoolMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.NetworkAddressEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.SegmentDurationEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.SegmentEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.ServiceMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.ServiceNameServiceEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.dao.ui.ServiceReferenceEsMetricUIDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author cyberdak
 */
public class StorageModuleEsHttpProvider extends ModuleProvider {

    private final Logger logger = LoggerFactory.getLogger(StorageModuleEsHttpProvider.class);

    static final String NAME = "elasticsearch-http";
    private static final String CLUSTER_NAME = "cluster_name";
    private static final String CLUSTER_NODES = "cluster_nodes";
    private static final String INDEX_SHARDS_NUMBER = "index_shards_number";
    private static final String INDEX_REPLICAS_NUMBER = "index_replicas_number";
    private static final String TIME_TO_LIVE_OF_DATA = "ttl";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String SSL = "ssl";

    private ElasticSearchHttpClient elasticSearchHttpClient;
    private DataTTLKeeperTimer deleteTimer;

    @Override public String name() {
        return NAME;
    }

    @Override public Class<? extends Module> module() {
        return StorageModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        String clusterName = config.getProperty(CLUSTER_NAME);
        String userName =  config.getProperty(USERNAME);
        String password =  config.getProperty(PASSWORD);
        Boolean ssl = (Boolean)config.get(SSL);
        String clusterNodes = config.getProperty(CLUSTER_NODES);
        elasticSearchHttpClient = new ElasticSearchHttpClient(clusterName, clusterNodes,ssl,userName,password);

        this.registerServiceImplementation(IBatchDAO.class, new BatchEsDAO(elasticSearchHttpClient));
        registerCacheDAO();
        registerRegisterDAO();
        registerPersistenceDAO();
        registerUiDAO();
        registerAlarmDAO();
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
        Integer indexShardsNumber = (Integer)config.get(INDEX_SHARDS_NUMBER);
        Integer indexReplicasNumber = (Integer)config.get(INDEX_REPLICAS_NUMBER);
        try {
            elasticSearchHttpClient.initialize();

            ElasticSearchStorageInstaller installer = new ElasticSearchStorageInstaller(indexShardsNumber, indexReplicasNumber);
            installer.install(elasticSearchHttpClient);
        } catch (ClientException | StorageException e) {
            logger.error(e.getMessage(), e);
        }

        String uuId = UUID.randomUUID().toString();
        ModuleRegisterService moduleRegisterService = getManager().find(ClusterModule.NAME).getService(ModuleRegisterService.class);
        moduleRegisterService.register(StorageModule.NAME, this.name(), new StorageModuleEsHttpRegistration(uuId, 0));

        StorageModuleEsHttpNamingListener namingListener = new StorageModuleEsHttpNamingListener();
        ModuleListenerService moduleListenerService = getManager().find(ClusterModule.NAME).getService(ModuleListenerService.class);
        moduleListenerService.addListener(namingListener);

        Integer beforeDay = (Integer)config.getOrDefault(TIME_TO_LIVE_OF_DATA, 3);
        deleteTimer = new DataTTLKeeperTimer(getManager(), namingListener, uuId + 0, beforeDay);
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {
        deleteTimer.start();
    }

    @Override public String[] requiredModules() {
        return new String[] {ClusterModule.NAME};
    }

    private void registerCacheDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IApplicationCacheDAO.class, new ApplicationEsCacheDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceCacheDAO.class, new InstanceEsCacheDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IServiceNameCacheDAO.class, new ServiceNameEsCacheDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(INetworkAddressCacheDAO.class, new NetworkAddressEsCacheDAO(elasticSearchHttpClient));
    }

    private void registerRegisterDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(INetworkAddressRegisterDAO.class, new NetworkAddressRegisterEsDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationRegisterDAO.class, new ApplicationRegisterEsDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceRegisterDAO.class, new InstanceRegisterEsDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IServiceNameRegisterDAO.class, new ServiceNameRegisterEsDAO(elasticSearchHttpClient));
    }

    private void registerPersistenceDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(ICpuSecondMetricPersistenceDAO.class, new CpuSecondMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(ICpuMinuteMetricPersistenceDAO.class, new CpuMinuteMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(ICpuHourMetricPersistenceDAO.class, new CpuHourMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(ICpuDayMetricPersistenceDAO.class, new CpuDayMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(ICpuMonthMetricPersistenceDAO.class, new CpuMonthMetricEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IGCSecondMetricPersistenceDAO.class, new GCSecondMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IGCMinuteMetricPersistenceDAO.class, new GCMinuteMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IGCHourMetricPersistenceDAO.class, new GCHourMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IGCDayMetricPersistenceDAO.class, new GCDayMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IGCMonthMetricPersistenceDAO.class, new GCMonthMetricEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IMemorySecondMetricPersistenceDAO.class, new MemorySecondMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IMemoryMinuteMetricPersistenceDAO.class, new MemoryMinuteMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IMemoryHourMetricPersistenceDAO.class, new MemoryHourMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IMemoryDayMetricPersistenceDAO.class, new MemoryDayMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IMemoryMonthMetricPersistenceDAO.class, new MemoryMonthMetricEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IMemoryPoolSecondMetricPersistenceDAO.class, new MemoryPoolSecondMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IMemoryPoolMinuteMetricPersistenceDAO.class, new MemoryPoolMinuteMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IMemoryPoolHourMetricPersistenceDAO.class, new MemoryPoolHourMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IMemoryPoolDayMetricPersistenceDAO.class, new MemoryPoolDayMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IMemoryPoolMonthMetricPersistenceDAO.class, new MemoryPoolMonthMetricEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IApplicationComponentMinutePersistenceDAO.class, new ApplicationComponentMinuteEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationComponentHourPersistenceDAO.class, new ApplicationComponentHourEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationComponentDayPersistenceDAO.class, new ApplicationComponentDayEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationComponentMonthPersistenceDAO.class, new ApplicationComponentMonthEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IApplicationMappingMinutePersistenceDAO.class, new ApplicationMappingMinuteEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationMappingHourPersistenceDAO.class, new ApplicationMappingHourEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationMappingDayPersistenceDAO.class, new ApplicationMappingDayEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationMappingMonthPersistenceDAO.class, new ApplicationMappingMonthEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IInstanceMappingMinutePersistenceDAO.class, new InstanceMappingMinuteEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceMappingHourPersistenceDAO.class, new InstanceMappingHourEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceMappingDayPersistenceDAO.class, new InstanceMappingDayEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceMappingMonthPersistenceDAO.class, new InstanceMappingMonthEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IGlobalTracePersistenceDAO.class, new GlobalTraceEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IApplicationMinuteMetricPersistenceDAO.class, new ApplicationMinuteMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationHourMetricPersistenceDAO.class, new ApplicationHourMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationDayMetricPersistenceDAO.class, new ApplicationDayMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationMonthMetricPersistenceDAO.class, new ApplicationMonthMetricEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IApplicationReferenceMinuteMetricPersistenceDAO.class, new ApplicationReferenceMinuteMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationReferenceHourMetricPersistenceDAO.class, new ApplicationReferenceHourMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationReferenceDayMetricPersistenceDAO.class, new ApplicationReferenceDayMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationReferenceMonthMetricPersistenceDAO.class, new ApplicationReferenceMonthMetricEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(ISegmentDurationPersistenceDAO.class, new SegmentDurationEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(ISegmentPersistenceDAO.class, new SegmentEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IServiceMinuteMetricPersistenceDAO.class, new ServiceMinuteMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IServiceHourMetricPersistenceDAO.class, new ServiceHourMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IServiceDayMetricPersistenceDAO.class, new ServiceDayMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IServiceMonthMetricPersistenceDAO.class, new ServiceMonthMetricEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IServiceReferenceMinuteMetricPersistenceDAO.class, new ServiceReferenceMinuteMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IServiceReferenceHourMetricPersistenceDAO.class, new ServiceReferenceHourMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IServiceReferenceDayMetricPersistenceDAO.class, new ServiceReferenceDayMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IServiceReferenceMonthMetricPersistenceDAO.class, new ServiceReferenceMonthMetricEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IInstanceMinuteMetricPersistenceDAO.class, new InstanceMinuteMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceHourMetricPersistenceDAO.class, new InstanceHourMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceDayMetricPersistenceDAO.class, new InstanceDayMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceMonthMetricPersistenceDAO.class, new InstanceMonthMetricEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IInstanceReferenceMinuteMetricPersistenceDAO.class, new InstanceReferenceMinuteMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceReferenceHourMetricPersistenceDAO.class, new InstanceReferenceHourMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceReferenceDayMetricPersistenceDAO.class, new InstanceReferenceDayMetricEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceReferenceMonthMetricPersistenceDAO.class, new InstanceReferenceMonthMetricEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IInstanceHeartBeatPersistenceDAO.class, new InstanceHeartBeatEsPersistenceDAO(elasticSearchHttpClient));
    }

    private void registerUiDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IInstanceUIDAO.class, new InstanceEsUIDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(INetworkAddressUIDAO.class, new NetworkAddressEsUIDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IServiceNameServiceUIDAO.class, new ServiceNameServiceEsUIDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IServiceMetricUIDAO.class, new ServiceMetricEsUIDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(ICpuMetricUIDAO.class, new CpuMetricEsUIDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IGCMetricUIDAO.class, new GCMetricEsUIDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IMemoryMetricUIDAO.class, new MemoryMetricEsUIDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IMemoryPoolMetricUIDAO.class, new MemoryPoolMetricEsUIDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IGlobalTraceUIDAO.class, new GlobalTraceEsUIDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceMetricUIDAO.class, new InstanceMetricEsUIDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationComponentUIDAO.class, new ApplicationComponentEsUIDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationMappingUIDAO.class, new ApplicationMappingEsUIDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationMetricUIDAO.class, new ApplicationMetricEsUIDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationReferenceMetricUIDAO.class, new ApplicationReferenceMetricEsUIDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(ISegmentDurationUIDAO.class, new SegmentDurationEsUIDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(ISegmentUIDAO.class, new SegmentEsUIDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IServiceReferenceMetricUIDAO.class, new ServiceReferenceEsMetricUIDAO(elasticSearchHttpClient));
    }

    private void registerAlarmDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IServiceReferenceAlarmPersistenceDAO.class, new ServiceReferenceAlarmEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IServiceReferenceAlarmListPersistenceDAO.class, new ServiceReferenceAlarmListEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceReferenceAlarmPersistenceDAO.class, new InstanceReferenceAlarmEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceReferenceAlarmListPersistenceDAO.class, new InstanceReferenceAlarmListEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationReferenceAlarmPersistenceDAO.class, new ApplicationReferenceAlarmEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationReferenceAlarmListPersistenceDAO.class, new ApplicationReferenceAlarmListEsPersistenceDAO(elasticSearchHttpClient));

        this.registerServiceImplementation(IServiceAlarmPersistenceDAO.class, new ServiceAlarmEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IServiceAlarmListPersistenceDAO.class, new ServiceAlarmListEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceAlarmPersistenceDAO.class, new InstanceAlarmEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IInstanceAlarmListPersistenceDAO.class, new InstanceAlarmListEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationAlarmPersistenceDAO.class, new ApplicationAlarmEsPersistenceDAO(elasticSearchHttpClient));
        this.registerServiceImplementation(IApplicationAlarmListPersistenceDAO.class, new ApplicationAlarmListEsPersistenceDAO(elasticSearchHttpClient));
    }
}
