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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.StreamProcessor;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.storage.IRegisterDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.INewModel;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceSetter;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * InventoryStreamProcessor represents the entrance and creator of the inventory register work flow.
 *
 * Method #in provides the major entrance for inventory streaming merge, eventually add or update the
 * inventory data in the storage.
 *
 * Method #create creates the workers and work flow for every inventory.
 */
public class InventoryStreamProcessor implements StreamProcessor<RegisterSource> {
    /**
     * Singleton instance.
     */
    private static final InventoryStreamProcessor PROCESSOR = new InventoryStreamProcessor();

    /**
     * Worker table hosts all entrance workers.
     */
    private Map<Class<? extends RegisterSource>, RegisterDistinctWorker> entryWorkers = new HashMap<>();

    public static InventoryStreamProcessor getInstance() {
        return PROCESSOR;
    }

    public void in(RegisterSource registerSource) {
        entryWorkers.get(registerSource.getClass()).in(registerSource);
    }

    /**
     * Create the workers and work flow for every inventory.
     *
     * @param moduleDefineHolder pointer of the module define.
     * @param stream             definition of the inventory class.
     * @param inventoryClass     data type of the inventory.
     */
    @SuppressWarnings("unchecked")
    public void create(ModuleDefineHolder moduleDefineHolder, Stream stream,
                       Class<? extends RegisterSource> inventoryClass) {
        StorageDAO storageDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        IRegisterDAO registerDAO;
        try {
            registerDAO = storageDAO.newRegisterDao(stream.builder().newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new UnexpectedException("Create " + stream.builder().getSimpleName() + " register DAO failure.", e);
        }

        INewModel modelSetter = moduleDefineHolder.find(CoreModule.NAME).provider().getService(INewModel.class);
        Model model = modelSetter.add(
            inventoryClass, stream.scopeId(), new Storage(stream.name(), false, false, Downsampling.None), false);

        RegisterPersistentWorker persistentWorker = new RegisterPersistentWorker(
            moduleDefineHolder, model.getName(), registerDAO, stream
            .scopeId());

        String remoteReceiverWorkerName = stream.name() + "_rec";
        IWorkerInstanceSetter workerInstanceSetter = moduleDefineHolder.find(CoreModule.NAME)
                                                                       .provider()
                                                                       .getService(IWorkerInstanceSetter.class);
        workerInstanceSetter.put(remoteReceiverWorkerName, persistentWorker, inventoryClass);

        RegisterRemoteWorker remoteWorker = new RegisterRemoteWorker(moduleDefineHolder, remoteReceiverWorkerName);

        RegisterDistinctWorker distinctWorker = new RegisterDistinctWorker(moduleDefineHolder, remoteWorker);

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
