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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.base;

import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.consumer.BulkConsumePool;
import org.apache.skywalking.apm.commons.datacarrier.consumer.ConsumerPoolFactory;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.influxdb.dto.BatchPoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BatchDAO implements IBatchDAO {
    private static final Logger logger = LoggerFactory.getLogger(BatchDAO.class);
    private final DataCarrier<PrepareRequest> dataCarrier;
    private final InfluxClient client;

    public BatchDAO(InfluxClient client) {
        this.client = client;

        String name = "INFLUX_ASYNC_BATCH_PERSISTENT";
        BulkConsumePool.Creator creator = new BulkConsumePool.Creator(name, 1, 20L);

        try {
            ConsumerPoolFactory.INSTANCE.createIfAbsent(name, creator);
        } catch (Exception e) {
            throw new UnexpectedException(e.getMessage(), e);
        }

        this.dataCarrier = new DataCarrier(1, 10000);
        this.dataCarrier.consume(ConsumerPoolFactory.INSTANCE.get(name), new InfluxBatchConsumer(this));
    }

    @Override
    public void asynchronous(InsertRequest insertRequest) {
        dataCarrier.produce(insertRequest);
    }

    @Override
    public void synchronous(List<PrepareRequest> prepareRequests) {
        if (CollectionUtils.isEmpty(prepareRequests)) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("batch sql statements execute, data size: {}", prepareRequests.size());
        }

        final BatchPoints.Builder builder = BatchPoints.builder();
        prepareRequests.forEach(e -> {
            builder.point(((InfluxInsertRequest) e).getPoint());
        });

        client.write(builder.build());
    }

    private class InfluxBatchConsumer implements IConsumer<PrepareRequest> {
        private final BatchDAO batchDAO;

        private InfluxBatchConsumer(BatchDAO batchDAO) {
            this.batchDAO = batchDAO;
        }

        @Override
        public void init() {

        }

        @Override
        public void consume(List<PrepareRequest> prepareRequests) {
            batchDAO.synchronous(prepareRequests);
        }

        @Override
        public void onError(List<PrepareRequest> prepareRequests, Throwable t) {
            logger.error(t.getMessage(), t);
        }

        @Override
        public void onExit() {
        }
    }
}
