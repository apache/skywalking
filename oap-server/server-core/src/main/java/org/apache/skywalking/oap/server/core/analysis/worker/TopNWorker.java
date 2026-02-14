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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.data.LimitedSizeBufferedData;
import org.apache.skywalking.oap.server.core.analysis.data.ReadWriteSafeCache;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueue;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueueConfig;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueueManager;
import org.apache.skywalking.oap.server.library.batchqueue.BufferStrategy;
import org.apache.skywalking.oap.server.library.batchqueue.HandlerConsumer;
import org.apache.skywalking.oap.server.library.batchqueue.PartitionPolicy;
import org.apache.skywalking.oap.server.library.batchqueue.ThreadPolicy;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * Top N worker is a persistence worker. Cache and order the data, flush in longer period.
 *
 * <p>All TopN types share a single {@link BatchQueue} with fixed threads.
 * The {@code typeHash()} partition selector ensures same TopN class lands on the same partition,
 * so each worker's {@link LimitedSizeBufferedData} is only accessed by one drain thread.
 */
@Slf4j
public class TopNWorker extends PersistenceWorker<TopN> {
    private static final String TOPN_QUEUE_NAME = "TOPN_PERSISTENCE";
    private static final BatchQueueConfig<TopN> TOPN_QUEUE_CONFIG =
        BatchQueueConfig.<TopN>builder()
            .threads(ThreadPolicy.fixed(1))
            .partitions(PartitionPolicy.adaptive())
            .bufferSize(1_000)
            .strategy(BufferStrategy.BLOCKING)
            .minIdleMs(10)
            .maxIdleMs(100)
            .build();

    private final IRecordDAO recordDAO;
    private final Model model;
    private final BatchQueue<TopN> topNQueue;
    private long reportPeriod;
    private volatile long lastReportTimestamp;

    TopNWorker(ModuleDefineHolder moduleDefineHolder, Model model, int topNSize, long reportPeriod,
               IRecordDAO recordDAO, Class<? extends TopN> topNClass) {
        super(
            moduleDefineHolder,
            new ReadWriteSafeCache<>(new LimitedSizeBufferedData<>(topNSize), new LimitedSizeBufferedData<>(topNSize))
        );
        this.recordDAO = recordDAO;
        this.model = model;
        this.topNQueue = BatchQueueManager.create(TOPN_QUEUE_NAME, TOPN_QUEUE_CONFIG);
        this.lastReportTimestamp = System.currentTimeMillis();
        // Top N persistent works per 10 minutes default.
        this.reportPeriod = reportPeriod;

        topNQueue.addHandler(topNClass, new TopNHandler());
    }

    /**
     * Force overriding the parent buildBatchRequests. Use its own report period.
     */
    @Override
    public List<PrepareRequest> buildBatchRequests() {
        long now = System.currentTimeMillis();
        if (now - lastReportTimestamp <= reportPeriod) {
            // Only do report in its own report period.
            return Collections.EMPTY_LIST;
        }
        lastReportTimestamp = now;

        final List<TopN> lastCollection = getCache().read();

        List<PrepareRequest> prepareRequests = new ArrayList<>(lastCollection.size());
        lastCollection.forEach(record -> {
            try {
                prepareRequests.add(recordDAO.prepareBatchInsert(model, record));
            } catch (Throwable t) {
                log.error(t.getMessage(), t);
            }
        });
        return prepareRequests;
    }

    /**
     * This method used to clear the expired cache, but TopN is not following it.
     */
    @Override
    public void endOfRound() {
    }

    @Override
    public void in(TopN n) {
        topNQueue.produce(n);
    }

    private class TopNHandler implements HandlerConsumer<TopN> {
        @Override
        public void consume(List<TopN> data) {
            onWork(data);
        }
    }
}
