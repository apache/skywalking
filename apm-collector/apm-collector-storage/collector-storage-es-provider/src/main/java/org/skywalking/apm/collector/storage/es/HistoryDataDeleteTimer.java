/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.es;

import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.skywalking.apm.collector.core.module.ModuleManager;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.skywalking.apm.collector.storage.StorageModule;
import org.skywalking.apm.collector.storage.dao.ICpuMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IGCMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IGlobalTracePersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IInstPerformancePersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IMemoryMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IMemoryPoolMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.INodeComponentPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.INodeMappingPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.INodeReferencePersistenceDAO;
import org.skywalking.apm.collector.storage.dao.ISegmentCostPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.ISegmentPersistenceDAO;
import org.skywalking.apm.collector.storage.dao.IServiceReferencePersistenceDAO;

/**
 * @author peng-yongsheng
 */
public class HistoryDataDeleteTimer {

    private final ModuleManager moduleManager;
    private final StorageModuleEsNamingListener namingListener;
    private final String selfAddress;
    private final int daysBefore;

    public HistoryDataDeleteTimer(ModuleManager moduleManager,
        StorageModuleEsNamingListener namingListener, String selfAddress, int daysBefore) {
        this.moduleManager = moduleManager;
        this.namingListener = namingListener;
        this.selfAddress = selfAddress;
        this.daysBefore = daysBefore;
    }

    public void start() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::delete, 1, 8, TimeUnit.HOURS);
    }

    private void tryDelete() {
        if (CollectionUtils.isNotEmpty(namingListener.getAddresses())) {
            String firstAddress = namingListener.getAddresses().iterator().next();
            if (firstAddress.equals(selfAddress)) {
                delete();
            }
        }
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

        deleteJVMMetricData(startTimestamp, endTimestamp);
        deleteTraceMetricData(startTimestamp, endTimestamp);
    }

    private void deleteJVMMetricData(long startTimestamp, long endTimestamp) {
        ICpuMetricPersistenceDAO cpuMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(ICpuMetricPersistenceDAO.class);
        cpuMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IGCMetricPersistenceDAO gcMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IGCMetricPersistenceDAO.class);
        gcMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IMemoryMetricPersistenceDAO memoryMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IMemoryMetricPersistenceDAO.class);
        memoryMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IMemoryPoolMetricPersistenceDAO memoryPoolMetricPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IMemoryPoolMetricPersistenceDAO.class);
        memoryPoolMetricPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);
    }

    private void deleteTraceMetricData(long startTimestamp, long endTimestamp) {
        IGlobalTracePersistenceDAO globalTracePersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IGlobalTracePersistenceDAO.class);
        globalTracePersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IInstPerformancePersistenceDAO instPerformancePersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IInstPerformancePersistenceDAO.class);
        instPerformancePersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        INodeComponentPersistenceDAO nodeComponentPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(INodeComponentPersistenceDAO.class);
        nodeComponentPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        INodeMappingPersistenceDAO nodeMappingPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(INodeMappingPersistenceDAO.class);
        nodeMappingPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        INodeReferencePersistenceDAO nodeReferencePersistenceDAO = moduleManager.find(StorageModule.NAME).getService(INodeReferencePersistenceDAO.class);
        nodeReferencePersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        ISegmentCostPersistenceDAO segmentCostPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(ISegmentCostPersistenceDAO.class);
        segmentCostPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        ISegmentPersistenceDAO segmentPersistenceDAO = moduleManager.find(StorageModule.NAME).getService(ISegmentPersistenceDAO.class);
        segmentPersistenceDAO.deleteHistory(startTimestamp, endTimestamp);

        IServiceReferencePersistenceDAO serviceReferencePersistenceDAO = moduleManager.find(StorageModule.NAME).getService(IServiceReferencePersistenceDAO.class);
        serviceReferencePersistenceDAO.deleteHistory(startTimestamp, endTimestamp);
    }
}
