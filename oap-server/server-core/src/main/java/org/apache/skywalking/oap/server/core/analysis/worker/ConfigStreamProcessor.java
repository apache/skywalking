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

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.Downsampling;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.StreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.config.Config;
import org.apache.skywalking.oap.server.core.storage.IConfigDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.IModelSetter;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * config is designed for user operation configuration in UI interface. It uses storage (synchronization) similar to Inventory and supports TTL deletion mode similar to the record.
 *
 * @author MrPro
 */
public class ConfigStreamProcessor implements StreamProcessor<Config> {

    private static final ConfigStreamProcessor PROCESSOR = new ConfigStreamProcessor();

    private Map<Class<? extends Config>, ConfigPersistentWorker> workers = new HashMap<>();

    public static ConfigStreamProcessor getInstance() {
        return PROCESSOR;
    }

    @Override
    public void in(Config config) {
        final ConfigPersistentWorker worker = workers.get(config.getClass());
        if (worker != null) {
            worker.in(config);
        }
    }

    @Override
    public void create(ModuleDefineHolder moduleDefineHolder, Stream stream, Class<? extends Config> streamClass) {
        if (DisableRegister.INSTANCE.include(stream.name())) {
            return;
        }

        StorageDAO storageDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        IConfigDAO configDAO;
        try {
            configDAO = storageDAO.newConfigDao(stream.builder().newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new UnexpectedException("Create " + stream.builder().getSimpleName() + " config record DAO failure.", e);
        }

        IModelSetter modelSetter = moduleDefineHolder.find(CoreModule.NAME).provider().getService(IModelSetter.class);
        Model model = modelSetter.putIfAbsent(streamClass, stream.scopeId(), new Storage(stream.name(), false, true, Downsampling.None), true);

        final ConfigPersistentWorker persistentWorker = new ConfigPersistentWorker(moduleDefineHolder, model, configDAO);
        workers.put(streamClass, persistentWorker);
    }
}
