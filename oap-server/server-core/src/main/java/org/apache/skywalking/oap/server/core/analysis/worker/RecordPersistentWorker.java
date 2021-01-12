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

import java.io.IOException;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecordPersistentWorker extends AbstractWorker<Record> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecordPersistentWorker.class);

    private final Model model;
    private final IRecordDAO recordDAO;
    private final IBatchDAO batchDAO;

    RecordPersistentWorker(ModuleDefineHolder moduleDefineHolder, Model model, IRecordDAO recordDAO) {
        super(moduleDefineHolder);
        this.model = model;
        this.recordDAO = recordDAO;
        this.batchDAO = moduleDefineHolder.find(StorageModule.NAME).provider().getService(IBatchDAO.class);
    }

    @Override
    public void in(Record record) {
        try {
            InsertRequest insertRequest = recordDAO.prepareBatchInsert(model, record);
            batchDAO.asynchronous(insertRequest);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
