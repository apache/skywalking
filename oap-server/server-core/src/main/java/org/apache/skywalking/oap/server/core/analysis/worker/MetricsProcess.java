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

package org.apache.skywalking.oap.server.core.analysis.worker;

import java.util.*;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.*;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.storage.annotation.StorageEntityAnnotationUtils;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @author peng-yongsheng
 */
public enum MetricsProcess {
    INSTANCE;

    private Map<Class<? extends Metrics>, MetricsAggregateWorker> entryWorkers = new HashMap<>();
    @Getter private List<MetricsPersistentWorker> persistentWorkers = new ArrayList<>();

    public void in(Metrics metrics) {
        MetricsAggregateWorker worker = entryWorkers.get(metrics.getClass());
        if (worker != null) {
            worker.in(metrics);
        }
    }

    public void create(ModuleManager moduleManager, Class<? extends Metrics> metricsClass) {
        String modelName = StorageEntityAnnotationUtils.getModelName(metricsClass);

        if (DisableRegister.INSTANCE.include(modelName)) {
            return;
        }

        Class<? extends StorageBuilder> builderClass = StorageEntityAnnotationUtils.getBuilder(metricsClass);

        StorageDAO storageDAO = moduleManager.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        IMetricsDAO metricsDAO;
        try {
            metricsDAO = storageDAO.newMetricsDao(builderClass.newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new UnexpectedException("Create " + builderClass.getSimpleName() + " metrics DAO failure.", e);
        }

        MetricsPersistentWorker minutePersistentWorker = minutePersistentWorker(moduleManager, metricsDAO, modelName);
        MetricsPersistentWorker hourPersistentWorker = worker(moduleManager, metricsDAO, modelName + Const.ID_SPLIT + Downsampling.Hour.getName());
        MetricsPersistentWorker dayPersistentWorker = worker(moduleManager, metricsDAO, modelName + Const.ID_SPLIT + Downsampling.Day.getName());
        MetricsPersistentWorker monthPersistentWorker = worker(moduleManager, metricsDAO, modelName + Const.ID_SPLIT + Downsampling.Month.getName());

        MetricsTransWorker transWorker = new MetricsTransWorker(moduleManager, modelName, minutePersistentWorker, hourPersistentWorker, dayPersistentWorker, monthPersistentWorker);
        MetricsRemoteWorker remoteWorker = new MetricsRemoteWorker(moduleManager, transWorker, modelName);
        MetricsAggregateWorker aggregateWorker = new MetricsAggregateWorker(moduleManager, remoteWorker, modelName);

        entryWorkers.put(metricsClass, aggregateWorker);
    }

    private MetricsPersistentWorker minutePersistentWorker(ModuleManager moduleManager,
        IMetricsDAO metricsDAO, String modelName) {
        AlarmNotifyWorker alarmNotifyWorker = new AlarmNotifyWorker(moduleManager);
        ExportWorker exportWorker = new ExportWorker(moduleManager);

        MetricsPersistentWorker minutePersistentWorker = new MetricsPersistentWorker(moduleManager, modelName,
            1000, metricsDAO, alarmNotifyWorker, exportWorker);
        persistentWorkers.add(minutePersistentWorker);

        return minutePersistentWorker;
    }

    private MetricsPersistentWorker worker(ModuleManager moduleManager,
        IMetricsDAO metricsDAO, String modelName) {
        MetricsPersistentWorker persistentWorker = new MetricsPersistentWorker(moduleManager, modelName,
            1000, metricsDAO, null, null);
        persistentWorkers.add(persistentWorker);

        return persistentWorker;
    }
}
