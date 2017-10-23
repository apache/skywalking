/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.stream.worker.impl;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.core.queue.EndOfBatchCommand;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.dao.IBatchDAO;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorker;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.DataCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public abstract class PersistenceWorker extends AbstractLocalAsyncWorker {

    private final Logger logger = LoggerFactory.getLogger(PersistenceWorker.class);

    private DataCache dataCache;

    public PersistenceWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
        dataCache = new DataCache();
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected final void onWork(Object message) throws WorkerException {
        if (message instanceof FlushAndSwitch) {
            try {
                if (dataCache.trySwitchPointer()) {
                    dataCache.switchPointer();
                }
            } finally {
                dataCache.trySwitchPointerFinally();
            }
        } else if (message instanceof EndOfBatchCommand) {
        } else {
            if (dataCache.currentCollectionSize() >= 5000) {
                try {
                    if (dataCache.trySwitchPointer()) {
                        dataCache.switchPointer();

                        List<?> collection = buildBatchCollection();
                        IBatchDAO dao = (IBatchDAO)DAOContainer.INSTANCE.get(IBatchDAO.class.getName());
                        dao.batchPersistence(collection);
                    }
                } finally {
                    dataCache.trySwitchPointerFinally();
                }
            }
            aggregate(message);
        }
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

            if (dataCache.getLast().asMap() != null) {
                batchCollection = prepareBatch(dataCache.getLast().asMap());
            }
        } finally {
            dataCache.finishReadingLast();
        }
        return batchCollection;
    }

    protected final List<Object> prepareBatch(Map<String, Data> dataMap) {
        List<Object> insertBatchCollection = new LinkedList<>();
        List<Object> updateBatchCollection = new LinkedList<>();
        dataMap.forEach((id, data) -> {
            if (needMergeDBData()) {
                Data dbData = persistenceDAO().get(id, getRole().dataDefine());
                if (ObjectUtils.isNotEmpty(dbData)) {
                    getRole().dataDefine().mergeData(data, dbData);
                    try {
                        updateBatchCollection.add(persistenceDAO().prepareBatchUpdate(data));
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
        Data data = (Data)message;

        if (dataCache.containsKey(data.id())) {
            getRole().dataDefine().mergeData(dataCache.get(data.id()), data);
        } else {
            dataCache.put(data.id(), data);
        }

        dataCache.finishWriting();
    }

    protected abstract IPersistenceDAO persistenceDAO();

    protected abstract boolean needMergeDBData();
}
