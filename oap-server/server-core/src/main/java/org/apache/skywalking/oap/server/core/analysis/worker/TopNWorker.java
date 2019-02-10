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
import org.apache.skywalking.oap.server.core.analysis.data.LimitedSizeDataCache;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.*;

/**
 * Top N worker is a persistence worker, but no
 *
 * @author wusheng
 */
public class TopNWorker extends PersistenceWorker<TopN, LimitedSizeDataCache<TopN>> {
    private static final Logger logger = LoggerFactory.getLogger(TopNWorker.class);
    private final LimitedSizeDataCache<TopN> limitedSizeDataCache;
    private final IRecordDAO recordDAO;
    private final String modelName;
    private final DataCarrier<TopN> dataCarrier;
    private long reportCycle;
    private volatile long lastReportTimestamp;

    public TopNWorker(int workerId, String modelName, ModuleManager moduleManager,
        int topNSize,
        IRecordDAO recordDAO) {
        super(moduleManager, workerId, -1);
        this.limitedSizeDataCache = new LimitedSizeDataCache<>(topNSize);
        this.recordDAO = recordDAO;
        this.modelName = modelName;
        this.dataCarrier = new DataCarrier<>(1, 10000);
        this.dataCarrier.consume(new TopNWorker.TopNConsumer(), 1);
        this.lastReportTimestamp = System.currentTimeMillis();
        // Top N persistent only works per 10 minutes.
        this.reportCycle = 10 * 60 * 1000L;
    }

    @Override void onWork(TopN data) {
        limitedSizeDataCache.writing();
        try {
            limitedSizeDataCache.add(data);
        } finally {
            limitedSizeDataCache.finishWriting();
        }
    }

    /**
     * TopN is not following the batch size trigger mode. The memory cost of this worker is limited always.
     *
     * `onWork` method has been override, so this method would never be executed. No need to implement this method,
     */
    @Override public void cacheData(TopN data) {

    }

    @Override public LimitedSizeDataCache<TopN> getCache() {
        return limitedSizeDataCache;
    }

    /**
     * The top N worker persistent cycle is much less than the others, override `flushAndSwitch` to extend the execute
     * time windows.
     *
     * Switch and persistent attempt happens based on reportCycle.
     *
     * @return
     */
    @Override public boolean flushAndSwitch() {
        long now = System.currentTimeMillis();
        if (now - lastReportTimestamp <= reportCycle) {
            return false;
        }
        lastReportTimestamp = now;
        return super.flushAndSwitch();
    }

    @Override public List<Object> prepareBatch(LimitedSizeDataCache<TopN> cache) {
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

    @Override public void in(TopN n) {
        dataCarrier.produce(n);
    }

    private class TopNConsumer implements IConsumer<TopN> {
        @Override public void init() {

        }

        @Override public void consume(List<TopN> data) {
            /**
             * TopN is not following the batch size trigger mode.
             * No need to implement this method, the memory size is limited always.
             */
            data.forEach(row -> onWork(row));
        }

        @Override public void onError(List<TopN> data, Throwable t) {
            logger.error(t.getMessage(), t);
        }

        @Override public void onExit() {

        }
    }
}
