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

package org.apache.skywalking.oap.server.core.register.worker;

import java.util.*;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.storage.annotation.StorageEntityAnnotationUtils;
import org.apache.skywalking.oap.server.core.worker.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * @author peng-yongsheng
 */
public enum InventoryProcess {
    INSTANCE;

    private Map<Class<? extends RegisterSource>, RegisterDistinctWorker> entryWorkers = new HashMap<>();

    public void in(RegisterSource registerSource) {
        entryWorkers.get(registerSource.getClass()).in(registerSource);
    }

    public void create(ModuleManager moduleManager, Class<? extends RegisterSource> inventoryClass) {
        String modelName = StorageEntityAnnotationUtils.getModelName(inventoryClass);
        int scopeId = StorageEntityAnnotationUtils.getSourceScope(inventoryClass);

        Class<? extends StorageBuilder> builderClass = StorageEntityAnnotationUtils.getBuilder(inventoryClass);

        StorageDAO storageDAO = moduleManager.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        IRegisterDAO registerDAO;
        try {
            registerDAO = storageDAO.newRegisterDao(builderClass.newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new UnexpectedException("Create " + builderClass.getSimpleName() + " register DAO failure.", e);
        }

        RegisterPersistentWorker persistentWorker = new RegisterPersistentWorker(WorkerIdGenerator.INSTANCES.generate(), modelName, moduleManager, registerDAO, scopeId);
        WorkerInstances.INSTANCES.put(persistentWorker.getWorkerId(), persistentWorker);

        RegisterRemoteWorker remoteWorker = new RegisterRemoteWorker(WorkerIdGenerator.INSTANCES.generate(), moduleManager, persistentWorker);
        WorkerInstances.INSTANCES.put(remoteWorker.getWorkerId(), remoteWorker);

        RegisterDistinctWorker distinctWorker = new RegisterDistinctWorker(WorkerIdGenerator.INSTANCES.generate(), remoteWorker);
        WorkerInstances.INSTANCES.put(distinctWorker.getWorkerId(), distinctWorker);

        entryWorkers.put(inventoryClass, distinctWorker);
    }

    /**
     * @return all register sourceScopeId class types
     */
    public List<Class> getAllRegisterSources() {
        List allSources = new ArrayList<>();
        entryWorkers.keySet().forEach(allSources::add);
        return allSources;
    }
}
