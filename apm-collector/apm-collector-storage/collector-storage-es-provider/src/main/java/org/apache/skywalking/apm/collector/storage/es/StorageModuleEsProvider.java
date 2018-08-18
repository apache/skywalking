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
import org.apache.skywalking.apm.collector.client.*;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.cluster.ClusterModule;
import org.apache.skywalking.apm.collector.cluster.service.*;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.ICollectorConfig;
import org.apache.skywalking.apm.collector.core.module.*;
import org.apache.skywalking.apm.collector.remote.RemoteModule;
import org.apache.skywalking.apm.collector.storage.*;
import org.apache.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.apache.skywalking.apm.collector.storage.dao.*;
import org.apache.skywalking.apm.collector.storage.dao.acp.*;
import org.apache.skywalking.apm.collector.storage.dao.alarm.*;
import org.apache.skywalking.apm.collector.storage.dao.amp.*;
import org.apache.skywalking.apm.collector.storage.dao.ampp.*;
import org.apache.skywalking.apm.collector.storage.dao.armp.*;
import org.apache.skywalking.apm.collector.storage.dao.cache.*;
import org.apache.skywalking.apm.collector.storage.dao.cpu.*;
import org.apache.skywalking.apm.collector.storage.dao.gc.*;
import org.apache.skywalking.apm.collector.storage.dao.imp.*;
import org.apache.skywalking.apm.collector.storage.dao.impp.*;
import org.apache.skywalking.apm.collector.storage.dao.irmp.*;
import org.apache.skywalking.apm.collector.storage.dao.memory.*;
import org.apache.skywalking.apm.collector.storage.dao.mpool.*;
import org.apache.skywalking.apm.collector.storage.dao.register.*;
import org.apache.skywalking.apm.collector.storage.dao.rtd.*;
import org.apache.skywalking.apm.collector.storage.dao.smp.*;
import org.apache.skywalking.apm.collector.storage.dao.srmp.*;
import org.apache.skywalking.apm.collector.storage.dao.ui.*;
import org.apache.skywalking.apm.collector.storage.es.base.dao.BatchProcessEsDAO;
import org.apache.skywalking.apm.collector.storage.es.base.define.ElasticSearchStorageInstaller;
import org.apache.skywalking.apm.collector.storage.es.dao.*;
import org.apache.skywalking.apm.collector.storage.es.dao.acp.*;
import org.apache.skywalking.apm.collector.storage.es.dao.alarm.*;
import org.apache.skywalking.apm.collector.storage.es.dao.amp.*;
import org.apache.skywalking.apm.collector.storage.es.dao.ampp.*;
import org.apache.skywalking.apm.collector.storage.es.dao.armp.*;
import org.apache.skywalking.apm.collector.storage.es.dao.cache.*;
import org.apache.skywalking.apm.collector.storage.es.dao.cpu.*;
import org.apache.skywalking.apm.collector.storage.es.dao.gc.*;
import org.apache.skywalking.apm.collector.storage.es.dao.imp.*;
import org.apache.skywalking.apm.collector.storage.es.dao.impp.*;
import org.apache.skywalking.apm.collector.storage.es.dao.irmp.*;
import org.apache.skywalking.apm.collector.storage.es.dao.memory.*;
import org.apache.skywalking.apm.collector.storage.es.dao.mpool.*;
import org.apache.skywalking.apm.collector.storage.es.dao.register.*;
import org.apache.skywalking.apm.collector.storage.es.dao.rtd.*;
import org.apache.skywalking.apm.collector.storage.es.dao.smp.*;
import org.apache.skywalking.apm.collector.storage.es.dao.srmp.*;
import org.apache.skywalking.apm.collector.storage.es.dao.ui.*;
import org.apache.skywalking.apm.collector.storage.es.ttl.TTLConfigService;
import org.apache.skywalking.apm.collector.storage.ttl.ITTLConfigService;

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

    @Override public Class<? extends ModuleDefine> module() {
        return StorageModule.class;
    }

    @Override public ModuleConfig createConfigBeanIfAbsent() {
        return config;
    }

    @Override public void prepare() throws ServiceNotProvidedException {
        elasticSearchClient = new ElasticSearchClient(config.getClusterName(), config.getClusterTransportSniffer(), config.getClusterNodes(), nameSpace);

        this.registerServiceImplementation(ITTLConfigService.class, new TTLConfigService(config));
        this.registerServiceImplementation(IBatchDAO.class, new BatchProcessEsDAO(elasticSearchClient, config.getBulkActions(), config.getBulkSize(), config.getFlushInterval(), config.getConcurrentRequests()));

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

        ModuleRegisterService moduleRegisterService = getManager().find(ClusterModule.NAME).getService(ModuleRegisterService.class);

        StorageModuleEsRegistration esRegistration = new StorageModuleEsRegistration(UUID.randomUUID().toString(), 0);
        moduleRegisterService.register(StorageModule.NAME, this.name(), esRegistration);

        StorageModuleEsNamingListener namingListener = new StorageModuleEsNamingListener();
        ModuleListenerService moduleListenerService = getManager().find(ClusterModule.NAME).getService(ModuleListenerService.class);
        moduleListenerService.addListener(namingListener);

        deleteTimer = new DataTTLKeeperTimer(getManager(), namingListener, esRegistration.buildValue().getHostPort(), config);
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
        this.registerServiceImplementation(IServiceNameHeartBeatPersistenceDAO.class, new ServiceNameHeartBeatEsPersistenceDAO(elasticSearchClient));
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
