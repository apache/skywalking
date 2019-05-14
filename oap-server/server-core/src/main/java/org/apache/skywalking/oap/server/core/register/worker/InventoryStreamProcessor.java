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
import org.apache.skywalking.oap.server.core.analysis.*;
import org.apache.skywalking.oap.server.core.register.RegisterSource;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * @author peng-yongsheng
 */
public class InventoryStreamProcessor implements StreamProcessor<RegisterSource> {

    private static final InventoryStreamProcessor PROCESSOR = new InventoryStreamProcessor();

    private Map<Class<? extends RegisterSource>, RegisterDistinctWorker> entryWorkers = new HashMap<>();

    public static InventoryStreamProcessor getInstance() {
        return PROCESSOR;
    }

    public void in(RegisterSource registerSource) {
        entryWorkers.get(registerSource.getClass()).in(registerSource);
    }

    public void create(ModuleDefineHolder moduleDefineHolder, Stream stream, Class<? extends RegisterSource> inventoryClass) {
        StorageDAO storageDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        IRegisterDAO registerDAO;
        try {
            registerDAO = storageDAO.newRegisterDao(stream.storage().builder().newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new UnexpectedException("Create " + stream.storage().builder().getSimpleName() + " register DAO failure.", e);
        }

        RegisterPersistentWorker persistentWorker = new RegisterPersistentWorker(moduleDefineHolder, stream.name(), registerDAO, stream.scopeId());

        RegisterRemoteWorker remoteWorker = new RegisterRemoteWorker(moduleDefineHolder, persistentWorker);

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
