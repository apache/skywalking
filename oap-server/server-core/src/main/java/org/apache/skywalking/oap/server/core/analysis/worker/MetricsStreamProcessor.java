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
import org.apache.skywalking.oap.server.core.analysis.*;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * @author peng-yongsheng
 */
public class MetricsStreamProcessor implements StreamProcessor<Metrics> {

    private final static MetricsStreamProcessor PROCESSOR = new MetricsStreamProcessor();

    private Map<Class<? extends Metrics>, MetricsAggregateWorker> entryWorkers = new HashMap<>();
    @Getter private List<MetricsPersistentWorker> persistentWorkers = new ArrayList<>();

    public static MetricsStreamProcessor getInstance() {
        return PROCESSOR;
    }

    public void in(Metrics metrics) {
        MetricsAggregateWorker worker = entryWorkers.get(metrics.getClass());
        if (worker != null) {
            worker.in(metrics);
        }
    }

    public void create(ModuleDefineHolder moduleDefineHolder, Stream stream, Class<? extends Metrics> metricsClass) {
        if (DisableRegister.INSTANCE.include(stream.name())) {
            return;
        }

        StorageDAO storageDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        IMetricsDAO metricsDAO;
        try {
            metricsDAO = storageDAO.newMetricsDao(stream.storage().builder().newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new UnexpectedException("Create " + stream.storage().builder().getSimpleName() + " metrics DAO failure.", e);
        }

        MetricsPersistentWorker minutePersistentWorker = minutePersistentWorker(moduleDefineHolder, metricsDAO, stream.name());
        MetricsPersistentWorker hourPersistentWorker = worker(moduleDefineHolder, metricsDAO, stream.name() + Const.ID_SPLIT + Downsampling.Hour.getName());
        MetricsPersistentWorker dayPersistentWorker = worker(moduleDefineHolder, metricsDAO, stream.name() + Const.ID_SPLIT + Downsampling.Day.getName());
        MetricsPersistentWorker monthPersistentWorker = worker(moduleDefineHolder, metricsDAO, stream.name() + Const.ID_SPLIT + Downsampling.Month.getName());

        MetricsTransWorker transWorker = new MetricsTransWorker(moduleDefineHolder, stream.name(), minutePersistentWorker, hourPersistentWorker, dayPersistentWorker, monthPersistentWorker);
        MetricsRemoteWorker remoteWorker = new MetricsRemoteWorker(moduleDefineHolder, transWorker, stream.name());
        MetricsAggregateWorker aggregateWorker = new MetricsAggregateWorker(moduleDefineHolder, remoteWorker, stream.name());

        entryWorkers.put(metricsClass, aggregateWorker);
    }

    private MetricsPersistentWorker minutePersistentWorker(ModuleDefineHolder moduleDefineHolder, IMetricsDAO metricsDAO, String modelName) {
        AlarmNotifyWorker alarmNotifyWorker = new AlarmNotifyWorker(moduleDefineHolder);
        ExportWorker exportWorker = new ExportWorker(moduleDefineHolder);

        MetricsPersistentWorker minutePersistentWorker = new MetricsPersistentWorker(moduleDefineHolder, modelName,
            1000, metricsDAO, alarmNotifyWorker, exportWorker);
        persistentWorkers.add(minutePersistentWorker);

        return minutePersistentWorker;
    }

    private MetricsPersistentWorker worker(ModuleDefineHolder moduleDefineHolder, IMetricsDAO metricsDAO, String modelName) {
        MetricsPersistentWorker persistentWorker = new MetricsPersistentWorker(moduleDefineHolder, modelName,
            1000, metricsDAO, null, null);
        persistentWorkers.add(persistentWorker);

        return persistentWorker;
    }
}
