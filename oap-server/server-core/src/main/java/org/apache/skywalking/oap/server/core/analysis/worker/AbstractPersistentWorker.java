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

import java.util.*;
import org.apache.skywalking.oap.server.core.analysis.data.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

import static java.util.Objects.nonNull;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractPersistentWorker<INPUT extends Indicator> extends Worker<INPUT> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractPersistentWorker.class);

    private final MergeDataCache<INPUT> mergeDataCache;
    private final IBatchDAO batchDAO;
    private final IPersistenceDAO<?, ?, INPUT> persistenceDAO;
    private final int blockBatchPersistenceSize = 1000;

    public AbstractPersistentWorker(ModuleManager moduleManager) {
        this.mergeDataCache = new MergeDataCache<>();
        this.batchDAO = moduleManager.find(StorageModule.NAME).getService(IBatchDAO.class);
        this.persistenceDAO = moduleManager.find(StorageModule.NAME).getService(IPersistenceDAO.class);
    }

    public final Window<MergeDataCollection<INPUT>> getCache() {
        return mergeDataCache;
    }

    @Override public final void in(INPUT input) {
        if (getCache().currentCollectionSize() >= blockBatchPersistenceSize) {
            try {
                if (getCache().trySwitchPointer()) {
                    getCache().switchPointer();

                    List<?> collection = buildBatchCollection();
                    batchDAO.batchPersistence(collection);
                }
            } finally {
                getCache().trySwitchPointerFinally();
            }
        }
        cacheData(input);
    }

    public final List<?> buildBatchCollection() {
        List<?> batchCollection = new LinkedList<>();
        try {
            while (getCache().getLast().isWriting()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    logger.warn("thread wake up");
                }
            }

            if (getCache().getLast().collection() != null) {
                batchCollection = prepareBatch(getCache().getLast());
            }
        } finally {
            getCache().finishReadingLast();
        }
        return batchCollection;
    }

    private List<Object> prepareBatch(MergeDataCollection<INPUT> collection) {
        List<Object> batchCollection = new LinkedList<>();
        collection.collection().forEach((id, data) -> {
            if (needMergeDBData()) {
                INPUT dbData = null;
                try {
                    dbData = persistenceDAO.get(data);
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                }
                if (nonNull(dbData)) {
                    dbData.combine(data);
                    try {
                        batchCollection.add(persistenceDAO.prepareBatchUpdate(dbData));
                    } catch (Throwable t) {
                        logger.error(t.getMessage(), t);
                    }
                } else {
                    try {
                        batchCollection.add(persistenceDAO.prepareBatchInsert(data));
                    } catch (Throwable t) {
                        logger.error(t.getMessage(), t);
                    }
                }
            } else {
                try {
                    batchCollection.add(persistenceDAO.prepareBatchInsert(data));
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                }
            }
        });

        return batchCollection;
    }

    private void cacheData(INPUT input) {
        mergeDataCache.writing();
        if (mergeDataCache.containsKey(input)) {
            mergeDataCache.get(input).combine(input);
        } else {
            mergeDataCache.put(input);
        }

        mergeDataCache.finishWriting();
    }

    protected abstract boolean needMergeDBData();
}
