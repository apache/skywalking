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


package org.apache.skywalking.apm.collector.stream.worker.impl;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.collector.core.data.Data;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.apache.skywalking.apm.collector.stream.worker.base.AbstractLocalAsyncWorker;
import org.apache.skywalking.apm.collector.stream.worker.base.WorkerException;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.apache.skywalking.apm.collector.stream.worker.impl.data.DataCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public abstract class PersistenceWorker<INPUT extends Data, OUTPUT extends Data> extends AbstractLocalAsyncWorker<INPUT, OUTPUT> {

    private final Logger logger = LoggerFactory.getLogger(PersistenceWorker.class);

    private final DataCache<OUTPUT> dataCache;
    private final IBatchDAO batchDAO;

    public PersistenceWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.dataCache = new DataCache<>();
        this.batchDAO = moduleManager.find(StorageModule.NAME).getService(IBatchDAO.class);
    }

    public final void flushAndSwitch() {
        try {
            if (dataCache.trySwitchPointer()) {
                dataCache.switchPointer();
            }
        } finally {
            dataCache.trySwitchPointerFinally();
        }
    }

    @Override protected final void onWork(INPUT message) throws WorkerException {
        if (dataCache.currentCollectionSize() >= 5000) {
            try {
                if (dataCache.trySwitchPointer()) {
                    dataCache.switchPointer();

                    List<?> collection = buildBatchCollection();
                    batchDAO.batchPersistence(collection);
                }
            } finally {
                dataCache.trySwitchPointerFinally();
            }
        }
        aggregate(message);
    }

    public final List<?> buildBatchCollection() throws WorkerException {
        List<?> batchCollection = new LinkedList<>();
        try {
            while (dataCache.getLast().isWriting()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    logger.warn("thread wake up");
                }
            }

            if (dataCache.getLast().collection() != null) {
                batchCollection = prepareBatch(dataCache.getLast().collection());
            }
        } finally {
            dataCache.finishReadingLast();
        }
        return batchCollection;
    }

    protected final List<Object> prepareBatch(Map<String, OUTPUT> dataMap) {
        List<Object> insertBatchCollection = new LinkedList<>();
        List<Object> updateBatchCollection = new LinkedList<>();
        dataMap.forEach((id, data) -> {
            if (needMergeDBData()) {
                Data dbData = persistenceDAO().get(id);
                if (ObjectUtils.isNotEmpty(dbData)) {
                    dbData.mergeData(data);
                    try {
                        updateBatchCollection.add(persistenceDAO().prepareBatchUpdate(dbData));
                    } catch (Throwable t) {
                        logger.error(t.getMessage(), t);
                    }
                } else {
                    try {
                        insertBatchCollection.add(persistenceDAO().prepareBatchInsert(data));
                    } catch (Throwable t) {
                        logger.error(t.getMessage(), t);
                    }
                }
            } else {
                try {
                    insertBatchCollection.add(persistenceDAO().prepareBatchInsert(data));
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                }
            }
        });

        insertBatchCollection.addAll(updateBatchCollection);
        return insertBatchCollection;
    }

    private void aggregate(Object message) {
        dataCache.writing();
        OUTPUT newData = (OUTPUT)message;

        if (dataCache.containsKey(newData.getId())) {
            dataCache.get(newData.getId()).mergeData(newData);
        } else {
            dataCache.put(newData.getId(), newData);
        }

        dataCache.finishWriting();
    }

    protected abstract IPersistenceDAO persistenceDAO();

    protected abstract boolean needMergeDBData();
}
