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

package org.apache.skywalking.apm.collector.storage.h2;

import java.util.Properties;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceHeartBeatPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentCostUIDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2StorageInstaller;
import org.apache.skywalking.apm.collector.storage.h2.dao.AlertingListH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.GCMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.InstanceH2CacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.SegmentH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ServiceEntryH2PersistenceDAO;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
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
import org.apache.skywalking.apm.collector.storage.h2.base.dao.BatchH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ApplicationComponentH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ApplicationComponentH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ApplicationH2CacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ApplicationH2RegisterDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ApplicationMappingH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ApplicationMappingH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ApplicationMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ApplicationReferenceMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ApplicationReferenceMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.CpuMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.CpuMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.GCMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.GlobalTraceH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.GlobalTraceH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.InstanceH2RegisterDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.InstanceH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.InstanceHeartBeatH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.InstanceMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.InstanceMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.MemoryMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.MemoryMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.MemoryPoolMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.MemoryPoolMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.SegmentCostH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.SegmentCostH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.SegmentH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ServiceEntryH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ServiceMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ServiceNameH2CacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ServiceNameH2RegisterDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ServiceReferenceH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ServiceReferenceMetricH2PersistenceDAO;
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
        registerAlertingDAO();
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
        this.registerServiceImplementation(IInstanceMetricPersistenceDAO.class, new InstanceMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationComponentPersistenceDAO.class, new ApplicationComponentH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationMappingPersistenceDAO.class, new ApplicationMappingH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationMetricPersistenceDAO.class, new ApplicationMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationReferenceMetricPersistenceDAO.class, new ApplicationReferenceMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(ISegmentCostPersistenceDAO.class, new SegmentCostH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(ISegmentPersistenceDAO.class, new SegmentH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IServiceEntryPersistenceDAO.class, new ServiceEntryH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IServiceMetricPersistenceDAO.class, new ServiceMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IServiceReferenceMetricPersistenceDAO.class, new ServiceReferenceMetricH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IInstanceHeartBeatPersistenceDAO.class, new InstanceHeartBeatH2PersistenceDAO(h2Client));
    }

    private void registerUiDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IInstanceUIDAO.class, new InstanceH2UIDAO(h2Client));

        this.registerServiceImplementation(ICpuMetricUIDAO.class, new CpuMetricH2UIDAO(h2Client));
        this.registerServiceImplementation(IGCMetricUIDAO.class, new GCMetricH2UIDAO(h2Client));
        this.registerServiceImplementation(IMemoryMetricUIDAO.class, new MemoryMetricH2UIDAO(h2Client));
        this.registerServiceImplementation(IMemoryPoolMetricUIDAO.class, new MemoryPoolMetricH2UIDAO(h2Client));

        this.registerServiceImplementation(IGlobalTraceUIDAO.class, new GlobalTraceH2UIDAO(h2Client));
        this.registerServiceImplementation(IInstanceMetricUIDAO.class, new InstanceMetricH2UIDAO(h2Client));
        this.registerServiceImplementation(IApplicationComponentUIDAO.class, new ApplicationComponentH2UIDAO(h2Client));
        this.registerServiceImplementation(IApplicationMappingUIDAO.class, new ApplicationMappingH2UIDAO(h2Client));
        this.registerServiceImplementation(IApplicationReferenceMetricUIDAO.class, new ApplicationReferenceMetricH2UIDAO(h2Client));
        this.registerServiceImplementation(ISegmentCostUIDAO.class, new SegmentCostH2UIDAO(h2Client));
        this.registerServiceImplementation(ISegmentUIDAO.class, new SegmentH2UIDAO(h2Client));
        this.registerServiceImplementation(IServiceEntryUIDAO.class, new ServiceEntryH2UIDAO(h2Client));
        this.registerServiceImplementation(IServiceReferenceUIDAO.class, new ServiceReferenceH2UIDAO(h2Client));
    }

    private void registerAlertingDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IAlertingListPersistenceDAO.class, new AlertingListH2PersistenceDAO(h2Client));
    }
}
