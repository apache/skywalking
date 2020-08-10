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

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.StreamProcessor;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.model.ModelCreator;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

public class RecordStreamProcessor implements StreamProcessor<Record> {

    private final static RecordStreamProcessor PROCESSOR = new RecordStreamProcessor();

    private Map<Class<? extends Record>, RecordPersistentWorker> workers = new HashMap<>();

    public static RecordStreamProcessor getInstance() {
        return PROCESSOR;
    }

    public void in(Record record) {
        RecordPersistentWorker worker = workers.get(record.getClass());
        if (worker != null) {
            worker.in(record);
        }
    }

    @SuppressWarnings("unchecked")
    public void create(ModuleDefineHolder moduleDefineHolder, Stream stream, Class<? extends Record> recordClass) throws StorageException {
        if (DisableRegister.INSTANCE.include(stream.name())) {
            return;
        }

        StorageDAO storageDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(StorageDAO.class);
        IRecordDAO recordDAO;
        try {
            recordDAO = storageDAO.newRecordDao(stream.builder().newInstance());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new UnexpectedException("Create " + stream.builder().getSimpleName() + " record DAO failure.", e);
        }

        ModelCreator modelSetter = moduleDefineHolder.find(CoreModule.NAME).provider().getService(ModelCreator.class);
        Model model = modelSetter.add(
            recordClass, stream.scopeId(), new Storage(stream.name(), DownSampling.Second), true);
        RecordPersistentWorker persistentWorker = new RecordPersistentWorker(moduleDefineHolder, model, recordDAO);

        workers.put(recordClass, persistentWorker);
    }
}
