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
import org.apache.skywalking.apm.collector.analysis.worker.model.impl.data.*;
import org.apache.skywalking.apm.collector.core.cache.Window;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.slf4j.*;

import static java.util.Objects.nonNull;

/**
 * @author peng-yongsheng
 */
public abstract class MergePersistenceWorker<INPUT_AND_OUTPUT extends StreamData> extends PersistenceWorker<INPUT_AND_OUTPUT, MergeDataCollection<INPUT_AND_OUTPUT>> {

    private static final Logger logger = LoggerFactory.getLogger(MergePersistenceWorker.class);

    private final MergeDataCache<INPUT_AND_OUTPUT> mergeDataCache;

    public MergePersistenceWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.mergeDataCache = new MergeDataCache<>();
    }

    @Override protected Window<MergeDataCollection<INPUT_AND_OUTPUT>> getCache() {
        return mergeDataCache;
    }

    @Override protected List<Object> prepareBatch(MergeDataCollection<INPUT_AND_OUTPUT> collection) {
        List<Object> batchCollection = new LinkedList<>();
        collection.collection().forEach((id, data) -> {
            if (needMergeDBData()) {
                INPUT_AND_OUTPUT dbData = persistenceDAO().get(id);
                if (nonNull(dbData)) {
                    dbData.mergeAndFormulaCalculateData(data);
                    try {
                        batchCollection.add(persistenceDAO().prepareBatchUpdate(dbData));
                        onNext(dbData);
                    } catch (Throwable t) {
                        logger.error(t.getMessage(), t);
                    }
                } else {
                    try {
                        batchCollection.add(persistenceDAO().prepareBatchInsert(data));
                        onNext(data);
                    } catch (Throwable t) {
                        logger.error(t.getMessage(), t);
                    }
                }
            } else {
                try {
                    batchCollection.add(persistenceDAO().prepareBatchInsert(data));
                    onNext(data);
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                }
            }
        });

        return batchCollection;
    }

    @Override protected void cacheData(INPUT_AND_OUTPUT input) {
        mergeDataCache.writing();
        if (mergeDataCache.containsKey(input.getId())) {
            mergeDataCache.get(input.getId()).mergeAndFormulaCalculateData(input);
        } else {
            input.calculateFormula();
            mergeDataCache.put(input.getId(), input);
        }

        mergeDataCache.finishWriting();
    }
}
