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

package org.apache.skywalking.apm.collector.analysis.worker.model.impl;

import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorker;
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.data.DataCache;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.base.dao.IBatchDAO;
import org.apache.skywalking.apm.collector.storage.base.dao.IPersistenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.util.Objects.nonNull;

/**
 * @author peng-yongsheng
 */
public abstract class PersistenceWorker<INPUT_AND_OUTPUT extends StreamData> extends AbstractLocalAsyncWorker<INPUT_AND_OUTPUT, INPUT_AND_OUTPUT> {

    private final Logger logger = LoggerFactory.getLogger(PersistenceWorker.class);

    private final DataCache<INPUT_AND_OUTPUT> dataCache;
    private final IBatchDAO batchDAO;

    public PersistenceWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.dataCache = new DataCache<>();
        this.batchDAO = moduleManager.find(StorageModule.NAME).getService(IBatchDAO.class);
    }

    public boolean flushAndSwitch() {
        boolean isSwitch;
        try {
            if (isSwitch = dataCache.trySwitchPointer()) {
                dataCache.switchPointer();
            }
        } finally {
            dataCache.trySwitchPointerFinally();
        }
        return isSwitch;
    }

    @Override protected void onWork(INPUT_AND_OUTPUT input) {
        if (dataCache.currentCollectionSize() >= 520000) {
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
        aggregate(input);
    }

    @GraphComputingMetric(name = "/persistence/buildBatchCollection/")
    public final List<?> buildBatchCollection() {
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

    private List<Object> prepareBatch(Map<String, INPUT_AND_OUTPUT> dataMap) {
        List<Object> insertBatchCollection = new LinkedList<>();
        List<Object> updateBatchCollection = new LinkedList<>();
        dataMap.forEach((id, data) -> {
            if (needMergeDBData()) {
                INPUT_AND_OUTPUT dbData = persistenceDAO().get(id);
                if (nonNull(dbData)) {
                    dbData.mergeAndFormulaCalculateData(data);
                    try {
                        updateBatchCollection.add(persistenceDAO().prepareBatchUpdate(dbData));
                        onNext(dbData);
                    } catch (Throwable t) {
                        logger.error(t.getMessage(), t);
                    }
                } else {
                    try {
                        insertBatchCollection.add(persistenceDAO().prepareBatchInsert(data));
                        onNext(data);
                    } catch (Throwable t) {
                        logger.error(t.getMessage(), t);
                    }
                }
            } else {
                try {
                    insertBatchCollection.add(persistenceDAO().prepareBatchInsert(data));
                    onNext(data);
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                }
            }
        });

        insertBatchCollection.addAll(updateBatchCollection);
        return insertBatchCollection;
    }

    private void aggregate(INPUT_AND_OUTPUT input) {
        dataCache.writing();
        if (dataCache.containsKey(input.getId())) {
            dataCache.get(input.getId()).mergeAndFormulaCalculateData(input);
        } else {
            dataCache.put(input.getId(), input);
        }

        dataCache.finishWriting();
    }

    protected abstract IPersistenceDAO<?, ?, INPUT_AND_OUTPUT> persistenceDAO();

    protected abstract boolean needMergeDBData();
}
