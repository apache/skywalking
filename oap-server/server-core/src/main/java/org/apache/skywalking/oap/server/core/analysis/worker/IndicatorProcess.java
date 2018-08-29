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
import org.apache.skywalking.oap.server.core.UnexpectedException;
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

    public void in(Indicator indicator) {
        entryWorkers.get(indicator.getClass()).in(indicator);
    }

    public void create(ModuleManager moduleManager, Class<? extends Indicator> indicatorClass) {
        String modelName = StorageEntityAnnotationUtils.getModelName(indicatorClass);
        Class<? extends StorageBuilder> builderClass = StorageEntityAnnotationUtils.getBuilder(indicatorClass);

        StorageDAO storageDAO = moduleManager.find(StorageModule.NAME).getService(StorageDAO.class);
        IIndicatorDAO indicatorDAO;
        try {
            indicatorDAO = storageDAO.newIndicatorDao(builderClass.newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new UnexpectedException("");
        }

        AlarmNotifyWorker alarmNotifyWorker = new AlarmNotifyWorker(WorkerIdGenerator.INSTANCES.generate(), moduleManager);
        WorkerInstances.INSTANCES.put(alarmNotifyWorker.getWorkerId(), alarmNotifyWorker);

        IndicatorPersistentWorker persistentWorker = new IndicatorPersistentWorker(WorkerIdGenerator.INSTANCES.generate(), modelName,
            1000, moduleManager, indicatorDAO, alarmNotifyWorker);
        WorkerInstances.INSTANCES.put(persistentWorker.getWorkerId(), persistentWorker);

        IndicatorRemoteWorker remoteWorker = new IndicatorRemoteWorker(WorkerIdGenerator.INSTANCES.generate(), moduleManager, persistentWorker);
        WorkerInstances.INSTANCES.put(remoteWorker.getWorkerId(), remoteWorker);

        IndicatorAggregateWorker aggregateWorker = new IndicatorAggregateWorker(WorkerIdGenerator.INSTANCES.generate(), remoteWorker);
        WorkerInstances.INSTANCES.put(aggregateWorker.getWorkerId(), aggregateWorker);

        entryWorkers.put(indicatorClass, aggregateWorker);
    }
}
