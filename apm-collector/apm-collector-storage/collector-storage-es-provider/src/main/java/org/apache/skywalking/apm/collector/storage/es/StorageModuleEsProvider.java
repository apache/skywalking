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

import java.util.Properties;
import java.util.UUID;
import org.apache.skywalking.apm.collector.client.ClientException;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentCostUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ApplicationEsCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.SegmentCostEsUIDAO;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.cluster.ClusterModule;
import org.apache.skywalking.apm.collector.cluster.service.ModuleListenerService;
import org.apache.skywalking.apm.collector.cluster.service.ModuleRegisterService;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.apache.skywalking.apm.collector.storage.StorageException;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.apache.skywalking.apm.collector.storage.dao.IAlertingListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationCacheDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationComponentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationComponentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationMappingPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationMappingUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationReferenceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationRegisterDAO;
import org.apache.skywalking.apm.collector.storage.dao.ICpuMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ICpuMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IGCMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IGCMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTraceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceCacheDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceHeartBeatPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceRegisterDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryPoolMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryPoolMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentCostPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceEntryPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceEntryUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceNameCacheDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceNameRegisterDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceReferenceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceReferenceUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.BatchEsDAO;
import org.apache.skywalking.apm.collector.storage.es.base.define.ElasticSearchStorageInstaller;
import org.apache.skywalking.apm.collector.storage.es.dao.AlertingListEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ApplicationComponentEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ApplicationComponentEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ApplicationEsRegisterDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ApplicationMappingEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ApplicationMappingEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ApplicationMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ApplicationReferenceMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ApplicationReferenceMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.CpuMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.CpuMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.GCMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.GCMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.GlobalTraceEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.GlobalTraceEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.InstanceEsCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.InstanceEsRegisterDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.InstanceEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.InstanceHeartBeatEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.InstanceMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.InstanceMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.MemoryMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.MemoryMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.MemoryPoolMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.MemoryPoolMetricEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.SegmentCostEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.SegmentEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.SegmentEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ServiceEntryEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ServiceEntryEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ServiceMetricEsPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ServiceNameEsCacheDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ServiceNameEsRegisterDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ServiceReferenceEsUIDAO;
import org.apache.skywalking.apm.collector.storage.es.dao.ServiceReferenceMetricEsPersistenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class StorageModuleEsProvider extends ModuleProvider {

    private final Logger logger = LoggerFactory.getLogger(StorageModuleEsProvider.class);

    public static final String NAME = "elasticsearch";
    private static final String CLUSTER_NAME = "cluster_name";
    private static final String CLUSTER_TRANSPORT_SNIFFER = "cluster_transport_sniffer";
    private static final String CLUSTER_NODES = "cluster_nodes";
    private static final String INDEX_SHARDS_NUMBER = "index_shards_number";
    private static final String INDEX_REPLICAS_NUMBER = "index_replicas_number";
    private static final String TIME_TO_LIVE_OF_DATA = "ttl";

    private ElasticSearchClient elasticSearchClient;
    private DataTTLKeeperTimer deleteTimer;

    @Override public String name() {
        return NAME;
    }

    @Override public Class<? extends Module> module() {
        return StorageModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        String clusterName = config.getProperty(CLUSTER_NAME);
        Boolean clusterTransportSniffer = (Boolean)config.get(CLUSTER_TRANSPORT_SNIFFER);
        String clusterNodes = config.getProperty(CLUSTER_NODES);
        elasticSearchClient = new ElasticSearchClient(clusterName, clusterTransportSniffer, clusterNodes);

        this.registerServiceImplementation(IBatchDAO.class, new BatchEsDAO(elasticSearchClient));
        registerCacheDAO();
        registerRegisterDAO();
        registerPersistenceDAO();
        registerUiDAO();
        registerAlertingDAO();
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
        Integer indexShardsNumber = (Integer)config.get(INDEX_SHARDS_NUMBER);
        Integer indexReplicasNumber = (Integer)config.get(INDEX_REPLICAS_NUMBER);
        try {
            elasticSearchClient.initialize();

            ElasticSearchStorageInstaller installer = new ElasticSearchStorageInstaller(indexShardsNumber, indexReplicasNumber);
            installer.install(elasticSearchClient);
        } catch (ClientException | StorageException e) {
            logger.error(e.getMessage(), e);
        }

        String uuId = UUID.randomUUID().toString();
        ModuleRegisterService moduleRegisterService = getManager().find(ClusterModule.NAME).getService(ModuleRegisterService.class);
        moduleRegisterService.register(StorageModule.NAME, this.name(), new StorageModuleEsRegistration(uuId, 0));

        StorageModuleEsNamingListener namingListener = new StorageModuleEsNamingListener();
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
        this.registerServiceImplementation(IApplicationCacheDAO.class, new ApplicationEsCacheDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceCacheDAO.class, new InstanceEsCacheDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceNameCacheDAO.class, new ServiceNameEsCacheDAO(elasticSearchClient));
    }

    private void registerRegisterDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IApplicationRegisterDAO.class, new ApplicationEsRegisterDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceRegisterDAO.class, new InstanceEsRegisterDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceNameRegisterDAO.class, new ServiceNameEsRegisterDAO(elasticSearchClient));
    }

    private void registerPersistenceDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(ICpuMetricPersistenceDAO.class, new CpuMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IGCMetricPersistenceDAO.class, new GCMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IMemoryMetricPersistenceDAO.class, new MemoryMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IMemoryPoolMetricPersistenceDAO.class, new MemoryPoolMetricEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IGlobalTracePersistenceDAO.class, new GlobalTraceEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceMetricPersistenceDAO.class, new InstanceMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationComponentPersistenceDAO.class, new ApplicationComponentEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationMappingPersistenceDAO.class, new ApplicationMappingEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationMetricPersistenceDAO.class, new ApplicationMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationReferenceMetricPersistenceDAO.class, new ApplicationReferenceMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(ISegmentCostPersistenceDAO.class, new SegmentCostEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(ISegmentPersistenceDAO.class, new SegmentEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceEntryPersistenceDAO.class, new ServiceEntryEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceMetricPersistenceDAO.class, new ServiceMetricEsPersistenceDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceReferenceMetricPersistenceDAO.class, new ServiceReferenceMetricEsPersistenceDAO(elasticSearchClient));

        this.registerServiceImplementation(IInstanceHeartBeatPersistenceDAO.class, new InstanceHeartBeatEsPersistenceDAO(elasticSearchClient));
    }

    private void registerUiDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IInstanceUIDAO.class, new InstanceEsUIDAO(elasticSearchClient));

        this.registerServiceImplementation(ICpuMetricUIDAO.class, new CpuMetricEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IGCMetricUIDAO.class, new GCMetricEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IMemoryMetricUIDAO.class, new MemoryMetricEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IMemoryPoolMetricUIDAO.class, new MemoryPoolMetricEsUIDAO(elasticSearchClient));

        this.registerServiceImplementation(IGlobalTraceUIDAO.class, new GlobalTraceEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IInstanceMetricUIDAO.class, new InstanceMetricEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationComponentUIDAO.class, new ApplicationComponentEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationMappingUIDAO.class, new ApplicationMappingEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IApplicationReferenceMetricUIDAO.class, new ApplicationReferenceMetricEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(ISegmentCostUIDAO.class, new SegmentCostEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(ISegmentUIDAO.class, new SegmentEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceEntryUIDAO.class, new ServiceEntryEsUIDAO(elasticSearchClient));
        this.registerServiceImplementation(IServiceReferenceUIDAO.class, new ServiceReferenceEsUIDAO(elasticSearchClient));
    }

    private void registerAlertingDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IAlertingListPersistenceDAO.class, new AlertingListEsPersistenceDAO(elasticSearchClient));
    }
}
