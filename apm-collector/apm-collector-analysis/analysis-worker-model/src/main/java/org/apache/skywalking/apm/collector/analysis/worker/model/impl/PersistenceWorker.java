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

import java.util.*;
import org.apache.skywalking.apm.collector.analysis.worker.model.base.AbstractLocalAsyncWorker;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IWorkerCacheSizeConfig;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.cache.Collection;
import org.apache.skywalking.apm.collector.core.cache.*;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.base.dao.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public abstract class PersistenceWorker<INPUT_AND_OUTPUT extends StreamData, COLLECTION extends Collection> extends AbstractLocalAsyncWorker<INPUT_AND_OUTPUT, INPUT_AND_OUTPUT> {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceWorker.class);

    private final IBatchDAO batchDAO;
    private final int blockBatchPersistenceSize;

    PersistenceWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.batchDAO = moduleManager.find(StorageModule.NAME).getService(IBatchDAO.class);
        this.blockBatchPersistenceSize = moduleManager.find(ConfigurationModule.NAME).getService(IWorkerCacheSizeConfig.class).cacheSize();
    }

    public boolean flushAndSwitch() {
        boolean isSwitch;
        try {
            if (isSwitch = getCache().trySwitchPointer()) {
                getCache().switchPointer();
            }
        } finally {
            getCache().trySwitchPointerFinally();
        }
        return isSwitch;
    }

    @Override protected void onWork(INPUT_AND_OUTPUT input) {
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

    @GraphComputingMetric(name = "/persistence/buildBatchCollection/")
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

    protected abstract List<Object> prepareBatch(COLLECTION collection);

    protected abstract Window<COLLECTION> getCache();

    protected abstract IPersistenceDAO<?, ?, INPUT_AND_OUTPUT> persistenceDAO();

    protected abstract boolean needMergeDBData();

    protected abstract void cacheData(INPUT_AND_OUTPUT input);
}
