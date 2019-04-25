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
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.storage.annotation.StorageEntityAnnotationUtils;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @author peng-yongsheng
 */
public enum IndicatorProcess {
    INSTANCE;

    private Map<Class<? extends Indicator>, IndicatorAggregateWorker> entryWorkers = new HashMap<>();
    @Getter private List<IndicatorPersistentWorker> persistentWorkers = new ArrayList<>();

    public void in(Indicator indicator) {
        IndicatorAggregateWorker worker = entryWorkers.get(indicator.getClass());
        if (worker != null) {
            worker.in(indicator);
        }
    }

    public void create(ModuleManager moduleManager, Class<? extends Indicator> indicatorClass) {
        String modelName = StorageEntityAnnotationUtils.getModelName(indicatorClass);

        if (DisableRegister.INSTANCE.include(modelName)) {
            return;
        }

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

        IndicatorTransWorker transWorker = new IndicatorTransWorker(moduleManager, modelName, minutePersistentWorker, hourPersistentWorker, dayPersistentWorker, monthPersistentWorker);
        IndicatorRemoteWorker remoteWorker = new IndicatorRemoteWorker(moduleManager, transWorker, modelName);
        IndicatorAggregateWorker aggregateWorker = new IndicatorAggregateWorker(moduleManager, remoteWorker, modelName);

        entryWorkers.put(indicatorClass, aggregateWorker);
    }

    private IndicatorPersistentWorker minutePersistentWorker(ModuleManager moduleManager,
        IIndicatorDAO indicatorDAO, String modelName) {
        AlarmNotifyWorker alarmNotifyWorker = new AlarmNotifyWorker(moduleManager);
        ExportWorker exportWorker = new ExportWorker(moduleManager);

        IndicatorPersistentWorker minutePersistentWorker = new IndicatorPersistentWorker(moduleManager, modelName,
            1000, indicatorDAO, alarmNotifyWorker, exportWorker);
        persistentWorkers.add(minutePersistentWorker);

        return minutePersistentWorker;
    }

    private IndicatorPersistentWorker worker(ModuleManager moduleManager,
        IIndicatorDAO indicatorDAO, String modelName) {
        IndicatorPersistentWorker persistentWorker = new IndicatorPersistentWorker(moduleManager, modelName,
            1000, indicatorDAO, null, null);
        persistentWorkers.add(persistentWorker);

        return persistentWorker;
    }
}
