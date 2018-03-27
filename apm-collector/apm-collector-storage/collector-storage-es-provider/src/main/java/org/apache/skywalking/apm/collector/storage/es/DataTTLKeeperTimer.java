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

import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentDurationPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.acp.IApplicationComponentMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ampp.IApplicationMappingMinutePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.armp.IApplicationReferenceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.cpu.ICpuSecondMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.gc.IGCSecondMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.imp.IInstanceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.memory.IMemorySecondMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.mpool.IMemoryPoolSecondMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.srmp.IServiceReferenceMinuteMetricPersistenceDAO;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class DataTTLKeeperTimer {
    private final Logger logger = LoggerFactory.getLogger(StorageModuleEsProvider.class);

    private final ModuleManager moduleManager;
    private final StorageModuleEsNamingListener namingListener;
    private final String selfAddress;
    private final int daysBefore;

    public DataTTLKeeperTimer(ModuleManager moduleManager,
        StorageModuleEsNamingListener namingListener, String selfAddress, int daysBefore) {
        this.moduleManager = moduleManager;
        this.namingListener = namingListener;
        this.selfAddress = selfAddress;
        this.daysBefore = daysBefore;
    }

    public void start() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
            new RunnableWithExceptionProtection(this::delete,
                t -> logger.error("Remove data in background failure.", t)), 1, 8, TimeUnit.HOURS);
    }

    private void delete() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.add(Calendar.DAY_OF_MONTH, -daysBefore);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        long startTimestamp = calendar.getTimeInMillis();

        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        long endTimestamp = calendar.getTimeInMillis();

        deleteJVMRelatedData(startTimestamp, endTimestamp);
        deleteTraceRelatedData(startTimestamp, endTimestamp);
    }

    private void deleteJVMRelatedData(long startTimestamp, long endTimestamp) {
        ICpuSecondMetricPersistenceDAO cpuMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(ICpuSecondMetricPersistenceDAO.class);
        cpuMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IGCSecondMetricPersistenceDAO gcMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IGCSecondMetricPersistenceDAO.class);
        gcMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IMemorySecondMetricPersistenceDAO memoryMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IMemorySecondMetricPersistenceDAO.class);
        memoryMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IMemoryPoolSecondMetricPersistenceDAO memoryPoolMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IMemoryPoolSecondMetricPersistenceDAO.class);
        memoryPoolMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);
    }

    private void deleteTraceRelatedData(long startTimestamp, long endTimestamp) {
        IGlobalTracePersistenceDAO globalTracePersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IGlobalTracePersistenceDAO.class);
        globalTracePersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IInstanceMinuteMetricPersistenceDAO instanceMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceMinuteMetricPersistenceDAO.class);
        instanceMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IApplicationComponentMinutePersistenceDAO applicationComponentPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationComponentMinutePersistenceDAO.class);
        applicationComponentPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IApplicationMappingMinutePersistenceDAO applicationMappingPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationMappingMinutePersistenceDAO.class);
        applicationMappingPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IApplicationReferenceMinuteMetricPersistenceDAO applicationReferenceMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceMinuteMetricPersistenceDAO.class);
        applicationReferenceMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        ISegmentDurationPersistenceDAO segmentDurationPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(ISegmentDurationPersistenceDAO.class);
        segmentDurationPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        ISegmentPersistenceDAO segmentPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(ISegmentPersistenceDAO.class);
        segmentPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IServiceReferenceMinuteMetricPersistenceDAO serviceReferencePersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IServiceReferenceMinuteMetricPersistenceDAO.class);
        serviceReferencePersistenceDAO.deleteHistory(startTimestamp, endTimestamp);
    }
}
