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
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.analysis.data.NonMergeDataCache;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class RecordPersistentWorker extends PersistenceWorker<Record, NonMergeDataCache<Record>> {

    private static final Logger logger = LoggerFactory.getLogger(RecordPersistentWorker.class);

    private final String modelName;
    private final NonMergeDataCache<Record> nonMergeDataCache;
    private final IRecordDAO recordDAO;
    private final DataCarrier<Record> dataCarrier;

    RecordPersistentWorker(int workerId, String modelName, int batchSize, ModuleManager moduleManager,
        IRecordDAO recordDAO) {
        super(moduleManager, workerId, batchSize);
        this.modelName = modelName;
        this.nonMergeDataCache = new NonMergeDataCache<>();
        this.recordDAO = recordDAO;
        this.dataCarrier = new DataCarrier<>(1, 10000);
        this.dataCarrier.consume(new RecordPersistentWorker.PersistentConsumer(this), 1);
    }

    @Override public void in(Record record) {
        dataCarrier.produce(record);
    }

    @Override public NonMergeDataCache<Record> getCache() {
        return nonMergeDataCache;
    }

    @Override public List<Object> prepareBatch(NonMergeDataCache<Record> cache) {
        List<Object> batchCollection = new LinkedList<>();
        cache.getLast().collection().forEach(record -> {
            try {
                batchCollection.add(recordDAO.prepareBatchInsert(modelName, record));
            } catch (Throwable t) {
                logger.error(t.getMessage(), t);
            }
        });
        return batchCollection;
    }

    @Override public void cacheData(Record input) {
        nonMergeDataCache.writing();
        nonMergeDataCache.add(input);
        nonMergeDataCache.finishWriting();
    }

    private class PersistentConsumer implements IConsumer<Record> {

        private final RecordPersistentWorker persistent;

        private PersistentConsumer(RecordPersistentWorker persistent) {
            this.persistent = persistent;
        }

        @Override public void init() {

        }

        @Override public void consume(List<Record> data) {
            for (Record record : data) {
                persistent.onWork(record);
            }
        }

        @Override public void onError(List<Record> data, Throwable t) {
            logger.error(t.getMessage(), t);
        }

        @Override public void onExit() {
        }
    }
}
