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
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.storage.annotation.StorageEntityAnnotationUtils;
import org.apache.skywalking.oap.server.core.worker.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @author peng-yongsheng
 */
public enum IndicatorProcess {
    INSTANCE;

    private Map<Class<? extends Indicator>, IndicatorAggregateWorker> entryWorkers = new HashMap<>();
    @Getter private List<IndicatorPersistentWorker> persistentWorkers = new ArrayList<>();

    public void in(Indicator indicator) {
        entryWorkers.get(indicator.getClass()).in(indicator);
    }

    public void create(ModuleManager moduleManager, Class<? extends Indicator> indicatorClass) {
        String modelName = StorageEntityAnnotationUtils.getModelName(indicatorClass);
        Class<? extends StorageBuilder> builderClass = StorageEntityAnnotationUtils.getBuilder(indicatorClass);

        StorageDAO storageDAO = moduleManager.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        IIndicatorDAO indicatorDAO;
        try {
            indicatorDAO = storageDAO.newIndicatorDao(builderClass.newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new UnexpectedException("Create " + builderClass.getSimpleName() + " indicator DAO failure.", e);
        }

        IndicatorPersistentWorker minutePersistentWorker = minutePersistentWorker(moduleManager, indicatorDAO, modelName);
        IndicatorPersistentWorker hourPersistentWorker = worker(moduleManager, indicatorDAO, modelName + Const.ID_SPLIT + Downsampling.Hour.getName());
        IndicatorPersistentWorker dayPersistentWorker = worker(moduleManager, indicatorDAO, modelName + Const.ID_SPLIT + Downsampling.Day.getName());
        IndicatorPersistentWorker monthPersistentWorker = worker(moduleManager, indicatorDAO, modelName + Const.ID_SPLIT + Downsampling.Month.getName());

        IndicatorTransWorker transWorker = new IndicatorTransWorker(moduleManager, modelName, WorkerIdGenerator.INSTANCES.generate(), minutePersistentWorker, hourPersistentWorker, dayPersistentWorker, monthPersistentWorker);
        WorkerInstances.INSTANCES.put(transWorker.getWorkerId(), transWorker);

        IndicatorRemoteWorker remoteWorker = new IndicatorRemoteWorker(WorkerIdGenerator.INSTANCES.generate(), moduleManager, transWorker, modelName);
        WorkerInstances.INSTANCES.put(remoteWorker.getWorkerId(), remoteWorker);

        IndicatorAggregateWorker aggregateWorker = new IndicatorAggregateWorker(moduleManager, WorkerIdGenerator.INSTANCES.generate(), remoteWorker, modelName);
        WorkerInstances.INSTANCES.put(aggregateWorker.getWorkerId(), aggregateWorker);

        entryWorkers.put(indicatorClass, aggregateWorker);
    }

    private IndicatorPersistentWorker minutePersistentWorker(ModuleManager moduleManager,
        IIndicatorDAO indicatorDAO, String modelName) {
        AlarmNotifyWorker alarmNotifyWorker = new AlarmNotifyWorker(WorkerIdGenerator.INSTANCES.generate(), moduleManager);
        WorkerInstances.INSTANCES.put(alarmNotifyWorker.getWorkerId(), alarmNotifyWorker);

        ExportWorker exportWorker = new ExportWorker(WorkerIdGenerator.INSTANCES.generate(), moduleManager);
        WorkerInstances.INSTANCES.put(exportWorker.getWorkerId(), exportWorker);

        IndicatorPersistentWorker minutePersistentWorker = new IndicatorPersistentWorker(WorkerIdGenerator.INSTANCES.generate(), modelName,
            1000, moduleManager, indicatorDAO, alarmNotifyWorker, exportWorker);
        WorkerInstances.INSTANCES.put(minutePersistentWorker.getWorkerId(), minutePersistentWorker);
        persistentWorkers.add(minutePersistentWorker);

        return minutePersistentWorker;
    }

    private IndicatorPersistentWorker worker(ModuleManager moduleManager,
        IIndicatorDAO indicatorDAO, String modelName) {
        IndicatorPersistentWorker persistentWorker = new IndicatorPersistentWorker(WorkerIdGenerator.INSTANCES.generate(), modelName,
            1000, moduleManager, indicatorDAO, null, null);
        WorkerInstances.INSTANCES.put(persistentWorker.getWorkerId(), persistentWorker);
        persistentWorkers.add(persistentWorker);

        return persistentWorker;
    }
}
