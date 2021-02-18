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

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.StreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.storage.INoneStreamDAO;
import org.apache.skywalking.oap.server.core.storage.StorageBuilderFactory;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * none streaming is designed for user operation configuration in UI interface. It uses storage (synchronization)
 * similar to Inventory and supports TTL deletion mode similar to the record.
 */
public class NoneStreamProcessor implements StreamProcessor<NoneStream> {

    private static final NoneStreamProcessor PROCESSOR = new NoneStreamProcessor();

    private Map<Class<? extends NoneStream>, NoneStreamPersistentWorker> workers = new HashMap<>();

    public static NoneStreamProcessor getInstance() {
        return PROCESSOR;
    }

    @Override
    public void in(NoneStream noneStream) {
        final NoneStreamPersistentWorker worker = workers.get(noneStream.getClass());
        if (worker != null) {
            worker.in(noneStream);
        }
    }

    @Override
    public void create(ModuleDefineHolder moduleDefineHolder, Stream stream, Class<? extends NoneStream> streamClass) throws StorageException {
        final StorageBuilderFactory storageBuilderFactory = moduleDefineHolder.find(StorageModule.NAME)
                                                                              .provider()
                                                                              .getService(StorageBuilderFactory.class);
        final Class<? extends StorageBuilder> builder = storageBuilderFactory.builderOf(streamClass, stream.builder());

        StorageDAO storageDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        INoneStreamDAO noneStream;
        try {
            noneStream = storageDAO.newNoneStreamDao(builder.getDeclaredConstructor().newInstance());
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new UnexpectedException("Create " + stream.builder()
                                                            .getSimpleName() + " none stream record DAO failure.", e);
        }

        ModelCreator modelSetter = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ModelCreator.class);
        Model model = modelSetter.add(streamClass, stream.scopeId(), new Storage(stream.name(), DownSampling.Second), true);

        final NoneStreamPersistentWorker persistentWorker = new NoneStreamPersistentWorker(moduleDefineHolder, model, noneStream);
        workers.put(streamClass, persistentWorker);
    }
}
