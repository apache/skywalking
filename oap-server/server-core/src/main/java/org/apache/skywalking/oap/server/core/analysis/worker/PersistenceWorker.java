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
import org.apache.skywalking.oap.server.core.analysis.data.Window;
import org.apache.skywalking.oap.server.core.storage.*;
import org.apache.skywalking.oap.server.core.worker.AbstractWorker;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public abstract class PersistenceWorker<INPUT extends StorageData, CACHE extends Window<INPUT>> extends AbstractWorker<INPUT> {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceWorker.class);

    private final int batchSize;
    private final IBatchDAO batchDAO;

    PersistenceWorker(ModuleManager moduleManager, int workerId, int batchSize) {
        super(workerId);
        this.batchSize = batchSize;
        this.batchDAO = moduleManager.find(StorageModule.NAME).provider().getService(IBatchDAO.class);
    }

    void onWork(INPUT input) {
        if (getCache().currentCollectionSize() >= batchSize) {
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

    public abstract void cacheData(INPUT input);

    public abstract CACHE getCache();

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

    public abstract List<Object> prepareBatch(CACHE cache);

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
                batchCollection = prepareBatch(getCache());
            }
        } finally {
            getCache().finishReadingLast();
        }
        return batchCollection;
    }
}
