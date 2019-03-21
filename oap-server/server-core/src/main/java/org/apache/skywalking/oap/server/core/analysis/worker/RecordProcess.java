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
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.storage.annotation.StorageEntityAnnotationUtils;
import org.apache.skywalking.oap.server.core.worker.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @author peng-yongsheng
 */
public enum RecordProcess {
    INSTANCE;

    private Map<Class<? extends Record>, RecordPersistentWorker> workers = new HashMap<>();

    public void in(Record record) {
        workers.get(record.getClass()).in(record);
    }

    @Getter private List<RecordPersistentWorker> persistentWorkers = new ArrayList<>();

    public void create(ModuleManager moduleManager, Class<? extends Record> recordClass) {
        String modelName = StorageEntityAnnotationUtils.getModelName(recordClass);
        Class<? extends StorageBuilder> builderClass = StorageEntityAnnotationUtils.getBuilder(recordClass);

        StorageDAO storageDAO = moduleManager.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        IRecordDAO recordDAO;
        try {
            recordDAO = storageDAO.newRecordDao(builderClass.newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new UnexpectedException("Create " + builderClass.getSimpleName() + " record DAO failure.", e);

        }

        RecordPersistentWorker persistentWorker = new RecordPersistentWorker(WorkerIdGenerator.INSTANCES.generate(), modelName,
            1000, moduleManager, recordDAO);
        WorkerInstances.INSTANCES.put(persistentWorker.getWorkerId(), persistentWorker);
        persistentWorkers.add(persistentWorker);
        workers.put(recordClass, persistentWorker);
    }
}
