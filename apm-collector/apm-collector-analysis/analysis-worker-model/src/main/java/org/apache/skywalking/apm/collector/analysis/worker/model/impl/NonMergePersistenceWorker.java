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

/**
 * @author peng-yongsheng
 */
public abstract class NonMergePersistenceWorker<INPUT_AND_OUTPUT extends StreamData> extends PersistenceWorker<INPUT_AND_OUTPUT, NonMergeDataCollection<INPUT_AND_OUTPUT>> {

    private static final Logger logger = LoggerFactory.getLogger(NonMergePersistenceWorker.class);

    private final NonMergeDataCache<INPUT_AND_OUTPUT> mergeDataCache;

    public NonMergePersistenceWorker(ModuleManager moduleManager) {
        super(moduleManager);
        this.mergeDataCache = new NonMergeDataCache<>();
    }

    @Override protected Window<NonMergeDataCollection<INPUT_AND_OUTPUT>> getCache() {
        return mergeDataCache;
    }

    @Override protected void cacheData(INPUT_AND_OUTPUT input) {
        mergeDataCache.writing();
        mergeDataCache.add(input);
        mergeDataCache.finishWriting();
    }

    @Override protected List<Object> prepareBatch(NonMergeDataCollection<INPUT_AND_OUTPUT> collection) {
        List<Object> insertBatchCollection = new ArrayList<>(collection.collection().size());
        collection.collection().forEach(data -> {
            try {
                insertBatchCollection.add(persistenceDAO().prepareBatchInsert(data));
                onNext(data);
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
            }
        });
        return insertBatchCollection;
    }
}
