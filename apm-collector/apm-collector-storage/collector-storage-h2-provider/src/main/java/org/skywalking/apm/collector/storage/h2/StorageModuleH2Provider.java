/*
 * Copyright 2017, OpenSkywalking Organization All rights rH2erved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licensH2/LICENSE-2.0
 *
 * UnlH2s required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIH2 OR CONDITIONS OF ANY KIND, either exprH2s or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.h2;

import java.util.Properties;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.module.Module;
import org.skywalking.apm.collector.core.module.ModuleProvider;
import org.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.skywalking.apm.collector.storage.StorageException;
import org.skywalking.apm.collector.storage.StorageModule;
import org.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.skywalking.apm.collector.storage.dao.IApplicationCacheDAO;
import org.skywalking.apm.collector.storage.dao.IApplicationRegisterDAO;
import org.skywalking.apm.collector.storage.dao.ICpuMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.ICpuMetricUIDAO;
import org.skywalking.apm.collector.storage.dao.IGCMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IGCMetricUIDAO;
import org.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IGlobalTraceUIDAO;
import org.skywalking.apm.collector.storage.dao.IInstPerformancePersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IInstPerformanceUIDAO;
import org.skywalking.apm.collector.storage.dao.IInstanceCacheDAO;
import org.skywalking.apm.collector.storage.dao.IInstanceHeartBeatPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IInstanceRegisterDAO;
import org.skywalking.apm.collector.storage.dao.IInstanceUIDAO;
import org.skywalking.apm.collector.storage.dao.IMemoryMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IMemoryMetricUIDAO;
import org.skywalking.apm.collector.storage.dao.IMemoryPoolMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IMemoryPoolMetricUIDAO;
import org.skywalking.apm.collector.storage.dao.INodeComponentPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.INodeComponentUIDAO;
import org.skywalking.apm.collector.storage.dao.INodeMappingPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.INodeMappingUIDAO;
import org.skywalking.apm.collector.storage.dao.INodeReferencePersistenceDAO;
import org.skywalking.apm.collector.storage.dao.INodeReferenceUIDAO;
import org.skywalking.apm.collector.storage.dao.ISegmentCostPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.ISegmentCostUIDAO;
import org.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.ISegmentUIDAO;
import org.skywalking.apm.collector.storage.dao.IServiceEntryPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IServiceEntryUIDAO;
import org.skywalking.apm.collector.storage.dao.IServiceNameCacheDAO;
import org.skywalking.apm.collector.storage.dao.IServiceNameRegisterDAO;
import org.skywalking.apm.collector.storage.dao.IServiceReferencePersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IServiceReferenceUIDAO;
import org.skywalking.apm.collector.storage.h2.base.dao.BatchH2DAO;
import org.skywalking.apm.collector.storage.h2.base.define.H2StorageInstaller;
import org.skywalking.apm.collector.storage.h2.dao.ApplicationH2CacheDAO;
import org.skywalking.apm.collector.storage.h2.dao.ApplicationH2RegisterDAO;
import org.skywalking.apm.collector.storage.h2.dao.CpuMetricH2PersistenceDAO;
import org.skywalking.apm.collector.storage.h2.dao.CpuMetricH2UIDAO;
import org.skywalking.apm.collector.storage.h2.dao.GCMetricH2PersistenceDAO;
import org.skywalking.apm.collector.storage.h2.dao.GCMetricH2UIDAO;
import org.skywalking.apm.collector.storage.h2.dao.GlobalTraceH2PersistenceDAO;
import org.skywalking.apm.collector.storage.h2.dao.GlobalTraceH2UIDAO;
import org.skywalking.apm.collector.storage.h2.dao.InstPerformanceH2PersistenceDAO;
import org.skywalking.apm.collector.storage.h2.dao.InstPerformanceH2UIDAO;
import org.skywalking.apm.collector.storage.h2.dao.InstanceH2CacheDAO;
import org.skywalking.apm.collector.storage.h2.dao.InstanceH2RegisterDAO;
import org.skywalking.apm.collector.storage.h2.dao.InstanceH2UIDAO;
import org.skywalking.apm.collector.storage.h2.dao.InstanceHeartBeatH2PersistenceDAO;
import org.skywalking.apm.collector.storage.h2.dao.MemoryMetricH2PersistenceDAO;
import org.skywalking.apm.collector.storage.h2.dao.MemoryMetricH2UIDAO;
import org.skywalking.apm.collector.storage.h2.dao.MemoryPoolMetricH2PersistenceDAO;
import org.skywalking.apm.collector.storage.h2.dao.MemoryPoolMetricH2UIDAO;
import org.skywalking.apm.collector.storage.h2.dao.NodeComponentH2PersistenceDAO;
import org.skywalking.apm.collector.storage.h2.dao.NodeComponentH2UIDAO;
import org.skywalking.apm.collector.storage.h2.dao.NodeMappingH2PersistenceDAO;
import org.skywalking.apm.collector.storage.h2.dao.NodeMappingH2UIDAO;
import org.skywalking.apm.collector.storage.h2.dao.NodeReferenceH2PersistenceDAO;
import org.skywalking.apm.collector.storage.h2.dao.NodeReferenceH2UIDAO;
import org.skywalking.apm.collector.storage.h2.dao.SegmentCostH2PersistenceDAO;
import org.skywalking.apm.collector.storage.h2.dao.SegmentCostH2UIDAO;
import org.skywalking.apm.collector.storage.h2.dao.SegmentH2PersistenceDAO;
import org.skywalking.apm.collector.storage.h2.dao.SegmentH2UIDAO;
import org.skywalking.apm.collector.storage.h2.dao.ServiceEntryH2PersistenceDAO;
import org.skywalking.apm.collector.storage.h2.dao.ServiceEntryH2UIDAO;
import org.skywalking.apm.collector.storage.h2.dao.ServiceNameH2CacheDAO;
import org.skywalking.apm.collector.storage.h2.dao.ServiceNameH2RegisterDAO;
import org.skywalking.apm.collector.storage.h2.dao.ServiceReferenceH2PersistenceDAO;
import org.skywalking.apm.collector.storage.h2.dao.ServiceReferenceH2UIDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class StorageModuleH2Provider extends ModuleProvider {

    private final Logger logger = LoggerFactory.getLogger(StorageModuleH2Provider.class);

    private static final String URL = "url";
    private static final String USER_NAME = "user_name";
    private static final String PASSWORD = "password";

    private H2Client h2Client;

    @Override public String name() {
        return "h2";
    }

    @Override public Class<? extends Module> module() {
        return StorageModule.class;
    }

    @Override public void prepare(Properties config) throws ServiceNotProvidedException {
        String url = config.getProperty(URL);
        String userName = config.getProperty(USER_NAME);
        String password = config.getProperty(PASSWORD);
        h2Client = new H2Client(url, userName, password);

        this.registerServiceImplementation(IBatchDAO.class, new BatchH2DAO(h2Client));
        registerCacheDAO();
        registerRegisterDAO();
        registerPersistenceDAO();
        registerUiDAO();
    }

    @Override public void start(Properties config) throws ServiceNotProvidedException {
        try {
            h2Client.initialize();

            H2StorageInstaller installer = new H2StorageInstaller();
            installer.install(h2Client);
        } catch (H2ClientException | StorageException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    @Override public String[] requiredModules() {
        return new String[0];
    }

    private void registerCacheDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IApplicationCacheDAO.class, new ApplicationH2CacheDAO(h2Client));
        this.registerServiceImplementation(IInstanceCacheDAO.class, new InstanceH2CacheDAO(h2Client));
        this.registerServiceImplementation(IServiceNameCacheDAO.class, new ServiceNameH2CacheDAO(h2Client));
    }

    private void registerRegisterDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IApplicationRegisterDAO.class, new ApplicationH2RegisterDAO(h2Client));
        this.registerServiceImplementation(IInstanceRegisterDAO.class, new InstanceH2RegisterDAO(h2Client));
        this.registerServiceImplementation(IServiceNameRegisterDAO.class, new ServiceNameH2RegisterDAO(h2Client));
    }

    private void registerPersistenceDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(ICpuMetricPersistenceDAO.class, new CpuMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IGCMetricPersistenceDAO.class, new GCMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IMemoryMetricPersistenceDAO.class, new MemoryMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IMemoryPoolMetricPersistenceDAO.class, new MemoryPoolMetricH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IGlobalTracePersistenceDAO.class, new GlobalTraceH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstPerformancePersistenceDAO.class, new InstPerformanceH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(INodeComponentPersistenceDAO.class, new NodeComponentH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(INodeMappingPersistenceDAO.class, new NodeMappingH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(INodeReferencePersistenceDAO.class, new NodeReferenceH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(ISegmentCostPersistenceDAO.class, new SegmentCostH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(ISegmentPersistenceDAO.class, new SegmentH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IServiceEntryPersistenceDAO.class, new ServiceEntryH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IServiceReferencePersistenceDAO.class, new ServiceReferenceH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IInstanceHeartBeatPersistenceDAO.class, new InstanceHeartBeatH2PersistenceDAO(h2Client));
    }

    private void registerUiDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IInstanceUIDAO.class, new InstanceH2UIDAO(h2Client));

        this.registerServiceImplementation(ICpuMetricUIDAO.class, new CpuMetricH2UIDAO(h2Client));
        this.registerServiceImplementation(IGCMetricUIDAO.class, new GCMetricH2UIDAO(h2Client));
        this.registerServiceImplementation(IMemoryMetricUIDAO.class, new MemoryMetricH2UIDAO(h2Client));
        this.registerServiceImplementation(IMemoryPoolMetricUIDAO.class, new MemoryPoolMetricH2UIDAO(h2Client));

        this.registerServiceImplementation(IGlobalTraceUIDAO.class, new GlobalTraceH2UIDAO(h2Client));
        this.registerServiceImplementation(IInstPerformanceUIDAO.class, new InstPerformanceH2UIDAO(h2Client));
        this.registerServiceImplementation(INodeComponentUIDAO.class, new NodeComponentH2UIDAO(h2Client));
        this.registerServiceImplementation(INodeMappingUIDAO.class, new NodeMappingH2UIDAO(h2Client));
        this.registerServiceImplementation(INodeReferenceUIDAO.class, new NodeReferenceH2UIDAO(h2Client));
        this.registerServiceImplementation(ISegmentCostUIDAO.class, new SegmentCostH2UIDAO(h2Client));
        this.registerServiceImplementation(ISegmentUIDAO.class, new SegmentH2UIDAO(h2Client));
        this.registerServiceImplementation(IServiceEntryUIDAO.class, new ServiceEntryH2UIDAO(h2Client));
        this.registerServiceImplementation(IServiceReferenceUIDAO.class, new ServiceReferenceH2UIDAO(h2Client));
    }
}
