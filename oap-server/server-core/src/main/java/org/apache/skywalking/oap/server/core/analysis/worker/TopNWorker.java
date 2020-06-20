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

import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.analysis.data.LimitedSizeBufferedData;
import org.apache.skywalking.oap.server.core.analysis.data.ReadWriteSafeCache;
import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;

/**
 * Top N worker is a persistence worker. Cache and order the data, flush in longer period.
 */
@Slf4j
public class TopNWorker extends PersistenceWorker<TopN> {
    private final IRecordDAO recordDAO;
    private final Model model;
    private final DataCarrier<TopN> dataCarrier;
    private long reportPeriod;
    private volatile long lastReportTimestamp;

    TopNWorker(ModuleDefineHolder moduleDefineHolder, Model model, int topNSize, long reportPeriod,
               IRecordDAO recordDAO) {
        super(
            moduleDefineHolder,
            new ReadWriteSafeCache<>(new LimitedSizeBufferedData<>(topNSize), new LimitedSizeBufferedData<>(topNSize))
        );
        this.recordDAO = recordDAO;
        this.model = model;
        this.dataCarrier = new DataCarrier<>("TopNWorker", 1, 1000);
        this.dataCarrier.consume(new TopNWorker.TopNConsumer(), 1);
        this.lastReportTimestamp = System.currentTimeMillis();
        // Top N persistent works per 10 minutes default.
        this.reportPeriod = reportPeriod;
    }

    /**
     * Force overriding the parent buildBatchRequests. Use its own report period.
     */
    @Override
    public void buildBatchRequests(final List<PrepareRequest> prepareRequests) {
        long now = System.currentTimeMillis();
        if (now - lastReportTimestamp <= reportPeriod) {
            // Only do report in its own report period.
            return;
        }
        lastReportTimestamp = now;
        super.buildBatchRequests(prepareRequests);
    }

    @Override
    public void prepareBatch(Collection<TopN> lastCollection, List<PrepareRequest> prepareRequests) {
        lastCollection.forEach(record -> {
            try {
                prepareRequests.add(recordDAO.prepareBatchInsert(model, record));
            } catch (Throwable t) {
                log.error(t.getMessage(), t);
            }
        });
    }

    /**
     * This method used to clear the expired cache, but TopN is not following it.
     */
    @Override
    public void endOfRound(long tookTime) {
    }

    @Override
    public void in(TopN n) {
        dataCarrier.produce(n);
    }

    private class TopNConsumer implements IConsumer<TopN> {
        @Override
        public void init() {
        }

        @Override
        public void consume(List<TopN> data) {
            TopNWorker.this.onWork(data);
        }

        @Override
        public void onError(List<TopN> data, Throwable t) {
            log.error(t.getMessage(), t);
        }

        @Override
        public void onExit() {

        }
    }
}
