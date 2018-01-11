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
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.apache.skywalking.apm.collector.core.module.ServiceNotProvidedException;
import org.apache.skywalking.apm.collector.storage.StorageException;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationComponentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationMappingUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ICpuMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IGCMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTraceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryPoolMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentCostUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceReferenceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.acp.IApplicationComponentDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.acp.IApplicationComponentHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.acp.IApplicationComponentMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.acp.IApplicationComponentMonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ampp.IApplicationMappingDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ampp.IApplicationMappingHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ampp.IApplicationMappingMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ampp.IApplicationMappingMonthPersistenceDAO;
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
import org.apache.skywalking.apm.collector.storage.dao.impp.IInstanceMappingDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.impp.IInstanceMappingHourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.impp.IInstanceMappingMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.impp.IInstanceMappingMonthPersistenceDAO;
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
import org.apache.skywalking.apm.collector.storage.h2.base.dao.BatchH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2StorageInstaller;
import org.apache.skywalking.apm.collector.storage.h2.dao.ApplicationComponentH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ApplicationMappingH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ApplicationReferenceMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.CpuMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.GCMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.GlobalTraceH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.InstanceH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.InstanceMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.MemoryMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.MemoryPoolMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.SegmentCostH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.SegmentH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ServiceReferenceH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.acp.ApplicationComponentDayH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.acp.ApplicationComponentHourH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.acp.ApplicationComponentMinuteH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.acp.ApplicationComponentMonthH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ampp.ApplicationMappingDayH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ampp.ApplicationMappingHourH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ampp.ApplicationMappingMinuteH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ampp.ApplicationMappingMonthH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cache.ApplicationH2CacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cache.InstanceH2CacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cache.NetworkAddressH2CacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cache.ServiceNameH2CacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cpump.CpuDayMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cpump.CpuHourMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cpump.CpuMinuteMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cpump.CpuMonthMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cpump.CpuSecondMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.gcmp.GCDayMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.gcmp.GCHourMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.gcmp.GCMinuteMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.gcmp.GCMonthMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.gcmp.GCSecondMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.impp.InstanceMappingDayH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.impp.InstanceMappingHourH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.impp.InstanceMappingMinuteH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.impp.InstanceMappingMonthH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.memorymp.MemoryDayMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.memorymp.MemoryHourMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.memorymp.MemoryMinuteMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.memorymp.MemoryMonthMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.memorymp.MemorySecondMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.mpoolmp.MemoryPoolDayMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.mpoolmp.MemoryPoolHourMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.mpoolmp.MemoryPoolMinuteMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.mpoolmp.MemoryPoolMonthMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.mpoolmp.MemoryPoolSecondMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.register.ApplicationRegisterH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.register.InstanceRegisterH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.register.NetworkAddressRegisterH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.register.ServiceNameRegisterH2DAO;
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
        registerAlarmDAO();
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
        this.registerServiceImplementation(INetworkAddressCacheDAO.class, new NetworkAddressH2CacheDAO(h2Client));
    }

    private void registerRegisterDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(INetworkAddressRegisterDAO.class, new NetworkAddressRegisterH2DAO(h2Client));
        this.registerServiceImplementation(IApplicationRegisterDAO.class, new ApplicationRegisterH2DAO(h2Client));
        this.registerServiceImplementation(IInstanceRegisterDAO.class, new InstanceRegisterH2DAO(h2Client));
        this.registerServiceImplementation(IServiceNameRegisterDAO.class, new ServiceNameRegisterH2DAO(h2Client));
    }

    private void registerPersistenceDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(ICpuSecondMetricPersistenceDAO.class, new CpuSecondMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(ICpuMinuteMetricPersistenceDAO.class, new CpuMinuteMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(ICpuHourMetricPersistenceDAO.class, new CpuHourMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(ICpuDayMetricPersistenceDAO.class, new CpuDayMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(ICpuMonthMetricPersistenceDAO.class, new CpuMonthMetricH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IGCSecondMetricPersistenceDAO.class, new GCSecondMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IGCMinuteMetricPersistenceDAO.class, new GCMinuteMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IGCHourMetricPersistenceDAO.class, new GCHourMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IGCDayMetricPersistenceDAO.class, new GCDayMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IGCMonthMetricPersistenceDAO.class, new GCMonthMetricH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IMemorySecondMetricPersistenceDAO.class, new MemorySecondMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IMemoryMinuteMetricPersistenceDAO.class, new MemoryMinuteMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IMemoryHourMetricPersistenceDAO.class, new MemoryHourMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IMemoryDayMetricPersistenceDAO.class, new MemoryDayMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IMemoryMonthMetricPersistenceDAO.class, new MemoryMonthMetricH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IMemoryPoolSecondMetricPersistenceDAO.class, new MemoryPoolSecondMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IMemoryPoolMinuteMetricPersistenceDAO.class, new MemoryPoolMinuteMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IMemoryPoolHourMetricPersistenceDAO.class, new MemoryPoolHourMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IMemoryPoolDayMetricPersistenceDAO.class, new MemoryPoolDayMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IMemoryPoolMonthMetricPersistenceDAO.class, new MemoryPoolMonthMetricH2PersistenceDAO(h2Client));

//        this.registerServiceImplementation(IGlobalTracePersistenceDAO.class, new GlobalTraceH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IApplicationComponentMinutePersistenceDAO.class, new ApplicationComponentMinuteH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationComponentHourPersistenceDAO.class, new ApplicationComponentHourH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationComponentDayPersistenceDAO.class, new ApplicationComponentDayH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationComponentMonthPersistenceDAO.class, new ApplicationComponentMonthH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IApplicationMappingMinutePersistenceDAO.class, new ApplicationMappingMinuteH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationMappingHourPersistenceDAO.class, new ApplicationMappingHourH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationMappingDayPersistenceDAO.class, new ApplicationMappingDayH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationMappingMonthPersistenceDAO.class, new ApplicationMappingMonthH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IInstanceMappingMinutePersistenceDAO.class, new InstanceMappingMinuteH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstanceMappingHourPersistenceDAO.class, new InstanceMappingHourH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstanceMappingDayPersistenceDAO.class, new InstanceMappingDayH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstanceMappingMonthPersistenceDAO.class, new InstanceMappingMonthH2PersistenceDAO(h2Client));

//        this.registerServiceImplementation(IApplicationMinuteMetricPersistenceDAO.class, new ApplicationMinuteMetricH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IApplicationReferenceMinuteMetricPersistenceDAO.class, new ApplicationReferenceMinuteMetricH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(ISegmentCostPersistenceDAO.class, new SegmentCostH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(ISegmentPersistenceDAO.class, new SegmentH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IServiceMinuteMetricPersistenceDAO.class, new ServiceMinuteMetricH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IServiceReferenceMinuteMetricPersistenceDAO.class, new ServiceReferenceMetricH2PersistenceDAO(h2Client));
//
//        this.registerServiceImplementation(IInstanceMinuteMetricPersistenceDAO.class, new InstanceMinuteMetricH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IInstanceReferenceMinuteMetricPersistenceDAO.class, new InstanceReferenceMetricH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IInstanceHeartBeatPersistenceDAO.class, new InstanceHeartBeatH2PersistenceDAO(h2Client));
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
        this.registerServiceImplementation(IServiceReferenceUIDAO.class, new ServiceReferenceH2UIDAO(h2Client));
    }

    private void registerAlarmDAO() throws ServiceNotProvidedException {
//        this.registerServiceImplementation(IServiceReferenceAlarmPersistenceDAO.class, new ServiceReferenceAlarmH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IServiceReferenceAlarmListPersistenceDAO.class, new ServiceReferenceAlarmListH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IInstanceReferenceAlarmPersistenceDAO.class, new InstanceReferenceAlarmH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IInstanceReferenceAlarmListPersistenceDAO.class, new InstanceReferenceAlarmListH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IApplicationReferenceAlarmPersistenceDAO.class, new ApplicationReferenceAlarmH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IApplicationReferenceAlarmListPersistenceDAO.class, new ApplicationReferenceAlarmListH2PersistenceDAO(h2Client));
//
//        this.registerServiceImplementation(IServiceAlarmPersistenceDAO.class, new ServiceAlarmH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IServiceAlarmListPersistenceDAO.class, new ServiceAlarmListH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IInstanceAlarmPersistenceDAO.class, new InstanceAlarmH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IInstanceAlarmListPersistenceDAO.class, new InstanceAlarmListH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IApplicationAlarmPersistenceDAO.class, new ApplicationAlarmH2PersistenceDAO(h2Client));
//        this.registerServiceImplementation(IApplicationAlarmListPersistenceDAO.class, new ApplicationAlarmListH2PersistenceDAO(h2Client));
    }
}
