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
import org.apache.skywalking.oap.server.core.analysis.management.ManagementData;
import org.apache.skywalking.oap.server.core.storage.IManagementDAO;
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
 * ManagementProcessor represents the UI/CLI interactive process. They are management data, which size is not huge and
 * time serious.
 *
 * @since 8.0.0
 */
public class ManagementStreamProcessor implements StreamProcessor<ManagementData> {
    private static final ManagementStreamProcessor PROCESSOR = new ManagementStreamProcessor();
    private Map<Class<? extends ManagementData>, ManagementPersistentWorker> workers = new HashMap<>();

    public static ManagementStreamProcessor getInstance() {
        return PROCESSOR;
    }

    @Override
    public void in(final ManagementData managementData) {
        final ManagementPersistentWorker worker = workers.get(managementData.getClass());
        if (worker != null) {
            worker.in(managementData);
        }
    }

    @Override
    public void create(final ModuleDefineHolder moduleDefineHolder, final Stream stream, final Class<? extends ManagementData> streamClass) throws StorageException {
        final StorageBuilderFactory storageBuilderFactory = moduleDefineHolder.find(StorageModule.NAME)
                                                                              .provider()
                                                                              .getService(StorageBuilderFactory.class);
        final Class<? extends StorageBuilder> builder = storageBuilderFactory.builderOf(streamClass, stream.builder());

        StorageDAO storageDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        IManagementDAO managementDAO;
        try {
            managementDAO = storageDAO.newManagementDao(builder.getDeclaredConstructor().newInstance());
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new UnexpectedException("Create " + stream.builder()
                    .getSimpleName() + " none stream record DAO failure.", e);
        }

        ModelCreator modelSetter = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ModelCreator.class);
        // Management stream doesn't read data from database during the persistent process. Keep the timeRelativeID == false always.
        Model model = modelSetter.add(streamClass, stream.scopeId(), new Storage(stream.name(), false, DownSampling.None), false);

        final ManagementPersistentWorker persistentWorker = new ManagementPersistentWorker(moduleDefineHolder, model, managementDAO);
        workers.put(streamClass, persistentWorker);
    }
}
