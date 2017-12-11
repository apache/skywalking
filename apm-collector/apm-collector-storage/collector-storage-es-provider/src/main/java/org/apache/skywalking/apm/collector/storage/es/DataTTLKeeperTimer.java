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
import org.apache.skywalking.apm.collector.storage.dao.IInstanceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryPoolMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IServiceReferenceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationComponentPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationMappingPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationReferenceMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ICpuMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IGCMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentCostPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;

/**
 * @author peng-yongsheng
 */
public class DataTTLKeeperTimer {

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
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::delete, 1, 8, TimeUnit.HOURS);
    }

    private void delete() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.DAY_OF_MONTH, -daysBefore);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        long startTimestamp = calendar.getTimeInMillis();

        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        long endTimestamp = calendar.getTimeInMillis();

        deleteJVMRelatedData(startTimestamp, endTimestamp);
        deleteTraceRelatedData(startTimestamp, endTimestamp);
    }

    private void deleteJVMRelatedData(long startTimestamp, long endTimestamp) {
        ICpuMetricPersistenceDAO cpuMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(ICpuMetricPersistenceDAO.class);
        cpuMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IGCMetricPersistenceDAO gcMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IGCMetricPersistenceDAO.class);
        gcMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IMemoryMetricPersistenceDAO memoryMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IMemoryMetricPersistenceDAO.class);
        memoryMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IMemoryPoolMetricPersistenceDAO memoryPoolMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IMemoryPoolMetricPersistenceDAO.class);
        memoryPoolMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);
    }

    private void deleteTraceRelatedData(long startTimestamp, long endTimestamp) {
        IGlobalTracePersistenceDAO globalTracePersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IGlobalTracePersistenceDAO.class);
        globalTracePersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IInstanceMetricPersistenceDAO instanceMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceMetricPersistenceDAO.class);
        instanceMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IApplicationComponentPersistenceDAO applicationComponentPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationComponentPersistenceDAO.class);
        applicationComponentPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IApplicationMappingPersistenceDAO applicationMappingPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationMappingPersistenceDAO.class);
        applicationMappingPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IApplicationReferenceMetricPersistenceDAO applicationReferenceMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationReferenceMetricPersistenceDAO.class);
        applicationReferenceMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        ISegmentCostPersistenceDAO segmentCostPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(ISegmentCostPersistenceDAO.class);
        segmentCostPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        ISegmentPersistenceDAO segmentPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(ISegmentPersistenceDAO.class);
        segmentPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IServiceReferenceMetricPersistenceDAO serviceReferencePersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IServiceReferenceMetricPersistenceDAO.class);
        serviceReferencePersistenceDAO.deleteHistory(startTimestamp, endTimestamp);
    }
}
