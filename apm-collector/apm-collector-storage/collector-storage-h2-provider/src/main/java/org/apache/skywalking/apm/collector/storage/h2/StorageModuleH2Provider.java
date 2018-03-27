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
import org.apache.skywalking.apm.collector.storage.dao.cpu.ICpuSecondMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gc.IGCDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gc.IGCHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gc.IGCMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gc.IGCMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gc.IGCSecondMetricPersistenceDAO;
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
import org.apache.skywalking.apm.collector.storage.dao.memory.IMemorySecondMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpool.IMemoryPoolDayMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpool.IMemoryPoolHourMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpool.IMemoryPoolMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpool.IMemoryPoolMonthMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpool.IMemoryPoolSecondMetricPersistenceDAO;
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
import org.apache.skywalking.apm.collector.storage.dao.ui.IMemoryPoolMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.INetworkAddressUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentDurationUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceAlarmUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceNameServiceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.BatchH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2StorageInstaller;
import org.apache.skywalking.apm.collector.storage.h2.dao.GlobalTraceH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.InstanceHeartBeatH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.SegmentDurationH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.SegmentH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.acp.ApplicationComponentDayH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.acp.ApplicationComponentHourH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.acp.ApplicationComponentMinuteH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.acp.ApplicationComponentMonthH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.ApplicationAlarmH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.ApplicationAlarmListH2DayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.ApplicationAlarmListH2HourPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.ApplicationAlarmListH2MinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.ApplicationAlarmListH2MonthPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.ApplicationReferenceAlarmH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.ApplicationReferenceAlarmListH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.InstanceAlarmH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.InstanceAlarmListH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.InstanceReferenceAlarmH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.InstanceReferenceAlarmListH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.ServiceAlarmH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.ServiceAlarmListH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.ServiceReferenceAlarmH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.alarm.ServiceReferenceAlarmListH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.amp.ApplicationDayMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.amp.ApplicationHourMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.amp.ApplicationMinuteMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.amp.ApplicationMonthMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ampp.ApplicationMappingDayH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ampp.ApplicationMappingHourH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ampp.ApplicationMappingMinuteH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ampp.ApplicationMappingMonthH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.armp.ApplicationReferenceDayMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.armp.ApplicationReferenceHourMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.armp.ApplicationReferenceMinuteMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.armp.ApplicationReferenceMonthMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cache.ApplicationH2CacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cache.InstanceH2CacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cache.NetworkAddressH2CacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cache.ServiceNameH2CacheDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cpu.CpuDayMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cpu.CpuHourMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cpu.CpuMinuteMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cpu.CpuMonthMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.cpu.CpuSecondMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.gcmp.GCDayMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.gcmp.GCHourMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.gcmp.GCMinuteMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.gcmp.GCMonthMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.gcmp.GCSecondMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.imp.InstanceDayMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.imp.InstanceHourMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.imp.InstanceMinuteMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.imp.InstanceMonthMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.impp.InstanceMappingDayH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.impp.InstanceMappingHourH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.impp.InstanceMappingMinuteH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.impp.InstanceMappingMonthH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.irmp.InstanceReferenceDayMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.irmp.InstanceReferenceHourMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.irmp.InstanceReferenceMinuteMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.irmp.InstanceReferenceMonthMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.memory.MemoryDayMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.memory.MemoryHourMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.memory.MemoryMinuteMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.memory.MemoryMonthMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.memory.MemorySecondMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.mpool.MemoryPoolDayMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.mpool.MemoryPoolHourMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.mpool.MemoryPoolMinuteMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.mpool.MemoryPoolMonthMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.mpool.MemoryPoolSecondMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.register.ApplicationRegisterH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.register.InstanceRegisterH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.register.NetworkAddressRegisterH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.register.ServiceNameRegisterH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.smp.ServiceDayMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.smp.ServiceHourMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.smp.ServiceMinuteMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.smp.ServiceMonthMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.srmp.ServiceReferenceDayMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.srmp.ServiceReferenceHourMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.srmp.ServiceReferenceMinuteMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.srmp.ServiceReferenceMonthMetricH2PersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.ApplicationAlarmH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.ApplicationAlarmListH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.ApplicationComponentH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.ApplicationMappingH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.ApplicationMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.ApplicationReferenceMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.CpuMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.GCMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.GlobalTraceH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.InstanceAlarmH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.InstanceH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.InstanceMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.MemoryMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.MemoryPoolMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.NetworkAddressH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.SegmentDurationH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.SegmentH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.ServiceAlarmH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.ServiceMetricH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.ServiceNameServiceH2UIDAO;
import org.apache.skywalking.apm.collector.storage.h2.dao.ui.ServiceReferenceH2MetricUIDAO;
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

        this.registerServiceImplementation(IGlobalTracePersistenceDAO.class, new GlobalTraceH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(ISegmentDurationPersistenceDAO.class, new SegmentDurationH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(ISegmentPersistenceDAO.class, new SegmentH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstanceHeartBeatPersistenceDAO.class, new InstanceHeartBeatH2PersistenceDAO(h2Client));

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

        this.registerServiceImplementation(IApplicationMinuteMetricPersistenceDAO.class, new ApplicationMinuteMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationHourMetricPersistenceDAO.class, new ApplicationHourMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationDayMetricPersistenceDAO.class, new ApplicationDayMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationMonthMetricPersistenceDAO.class, new ApplicationMonthMetricH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IApplicationReferenceMinuteMetricPersistenceDAO.class, new ApplicationReferenceMinuteMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationReferenceHourMetricPersistenceDAO.class, new ApplicationReferenceHourMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationReferenceDayMetricPersistenceDAO.class, new ApplicationReferenceDayMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationReferenceMonthMetricPersistenceDAO.class, new ApplicationReferenceMonthMetricH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IInstanceMinuteMetricPersistenceDAO.class, new InstanceMinuteMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstanceHourMetricPersistenceDAO.class, new InstanceHourMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstanceDayMetricPersistenceDAO.class, new InstanceDayMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstanceMonthMetricPersistenceDAO.class, new InstanceMonthMetricH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IInstanceReferenceMinuteMetricPersistenceDAO.class, new InstanceReferenceMinuteMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstanceReferenceHourMetricPersistenceDAO.class, new InstanceReferenceHourMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstanceReferenceDayMetricPersistenceDAO.class, new InstanceReferenceDayMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstanceReferenceMonthMetricPersistenceDAO.class, new InstanceReferenceMonthMetricH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IServiceMinuteMetricPersistenceDAO.class, new ServiceMinuteMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IServiceHourMetricPersistenceDAO.class, new ServiceHourMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IServiceDayMetricPersistenceDAO.class, new ServiceDayMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IServiceMonthMetricPersistenceDAO.class, new ServiceMonthMetricH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IServiceReferenceMinuteMetricPersistenceDAO.class, new ServiceReferenceMinuteMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IServiceReferenceHourMetricPersistenceDAO.class, new ServiceReferenceHourMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IServiceReferenceDayMetricPersistenceDAO.class, new ServiceReferenceDayMetricH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IServiceReferenceMonthMetricPersistenceDAO.class, new ServiceReferenceMonthMetricH2PersistenceDAO(h2Client));
    }

    private void registerUiDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IInstanceUIDAO.class, new InstanceH2UIDAO(h2Client));
        this.registerServiceImplementation(INetworkAddressUIDAO.class, new NetworkAddressH2UIDAO(h2Client));
        this.registerServiceImplementation(IServiceNameServiceUIDAO.class, new ServiceNameServiceH2UIDAO(h2Client));
        this.registerServiceImplementation(IServiceMetricUIDAO.class, new ServiceMetricH2UIDAO(h2Client));

        this.registerServiceImplementation(ICpuMetricUIDAO.class, new CpuMetricH2UIDAO(h2Client));
        this.registerServiceImplementation(IGCMetricUIDAO.class, new GCMetricH2UIDAO(h2Client));
        this.registerServiceImplementation(IMemoryMetricUIDAO.class, new MemoryMetricH2UIDAO(h2Client));
        this.registerServiceImplementation(IMemoryPoolMetricUIDAO.class, new MemoryPoolMetricH2UIDAO(h2Client));

        this.registerServiceImplementation(IGlobalTraceUIDAO.class, new GlobalTraceH2UIDAO(h2Client));
        this.registerServiceImplementation(IInstanceMetricUIDAO.class, new InstanceMetricH2UIDAO(h2Client));
        this.registerServiceImplementation(IApplicationComponentUIDAO.class, new ApplicationComponentH2UIDAO(h2Client));
        this.registerServiceImplementation(IApplicationMappingUIDAO.class, new ApplicationMappingH2UIDAO(h2Client));
        this.registerServiceImplementation(IApplicationMetricUIDAO.class, new ApplicationMetricH2UIDAO(h2Client));
        this.registerServiceImplementation(IApplicationReferenceMetricUIDAO.class, new ApplicationReferenceMetricH2UIDAO(h2Client));
        this.registerServiceImplementation(ISegmentDurationUIDAO.class, new SegmentDurationH2UIDAO(h2Client));
        this.registerServiceImplementation(ISegmentUIDAO.class, new SegmentH2UIDAO(h2Client));
        this.registerServiceImplementation(IServiceReferenceMetricUIDAO.class, new ServiceReferenceH2MetricUIDAO(h2Client));

        this.registerServiceImplementation(IApplicationAlarmUIDAO.class, new ApplicationAlarmH2UIDAO(h2Client));
        this.registerServiceImplementation(IInstanceAlarmUIDAO.class, new InstanceAlarmH2UIDAO(h2Client));
        this.registerServiceImplementation(IServiceAlarmUIDAO.class, new ServiceAlarmH2UIDAO(h2Client));

        this.registerServiceImplementation(IApplicationAlarmListUIDAO.class, new ApplicationAlarmListH2UIDAO(h2Client));
    }

    private void registerAlarmDAO() throws ServiceNotProvidedException {
        this.registerServiceImplementation(IServiceReferenceAlarmPersistenceDAO.class, new ServiceReferenceAlarmH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IServiceReferenceAlarmListPersistenceDAO.class, new ServiceReferenceAlarmListH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstanceReferenceAlarmPersistenceDAO.class, new InstanceReferenceAlarmH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstanceReferenceAlarmListPersistenceDAO.class, new InstanceReferenceAlarmListH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationReferenceAlarmPersistenceDAO.class, new ApplicationReferenceAlarmH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationReferenceAlarmListPersistenceDAO.class, new ApplicationReferenceAlarmListH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IServiceAlarmPersistenceDAO.class, new ServiceAlarmH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IServiceAlarmListPersistenceDAO.class, new ServiceAlarmListH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstanceAlarmPersistenceDAO.class, new InstanceAlarmH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IInstanceAlarmListPersistenceDAO.class, new InstanceAlarmListH2PersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationAlarmPersistenceDAO.class, new ApplicationAlarmH2PersistenceDAO(h2Client));

        this.registerServiceImplementation(IApplicationAlarmListMinutePersistenceDAO.class, new ApplicationAlarmListH2MinutePersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationAlarmListHourPersistenceDAO.class, new ApplicationAlarmListH2HourPersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationAlarmListDayPersistenceDAO.class, new ApplicationAlarmListH2DayPersistenceDAO(h2Client));
        this.registerServiceImplementation(IApplicationAlarmListMonthPersistenceDAO.class, new ApplicationAlarmListH2MonthPersistenceDAO(h2Client));
    }
}
