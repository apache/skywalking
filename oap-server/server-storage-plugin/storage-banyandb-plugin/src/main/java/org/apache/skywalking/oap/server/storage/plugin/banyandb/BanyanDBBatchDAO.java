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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import org.apache.skywalking.banyandb.v1.client.MeasureBulkWriteProcessor;
import org.apache.skywalking.banyandb.v1.client.StreamBulkWriteProcessor;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMeasureInsertRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMeasureUpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBStreamInsertRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class BanyanDBBatchDAO extends AbstractDAO<BanyanDBStorageClient> implements IBatchDAO {
    private StreamBulkWriteProcessor streamBulkWriteProcessor;

    private MeasureBulkWriteProcessor measureBulkWriteProcessor;

    private final int maxBulkSize;

    private final int flushInterval;

    private final int concurrency;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public BanyanDBBatchDAO(BanyanDBStorageClient client, int maxBulkSize, int flushInterval, int concurrency) {
        super(client);
        this.maxBulkSize = maxBulkSize;
        this.flushInterval = flushInterval;
        this.concurrency = concurrency;
    }

    @Override
    public void insert(InsertRequest insertRequest) {
        if (initialized.compareAndSet(false, true)) {
            this.streamBulkWriteProcessor = getClient().createStreamBulkProcessor(maxBulkSize, flushInterval, concurrency);
            this.measureBulkWriteProcessor = getClient().createMeasureBulkProcessor(maxBulkSize, flushInterval, concurrency);
        }
        if (insertRequest instanceof BanyanDBStreamInsertRequest) {
            this.streamBulkWriteProcessor.add(((BanyanDBStreamInsertRequest) insertRequest).getStreamWrite());
        } else if (insertRequest instanceof BanyanDBMeasureInsertRequest) {
            this.measureBulkWriteProcessor.add(((BanyanDBMeasureInsertRequest) insertRequest).getMeasureWrite());
        }
    }

    @Override
    public CompletableFuture<Void> flush(List<PrepareRequest> prepareRequests) {
        if (initialized.compareAndSet(false, true)) {
            this.streamBulkWriteProcessor = getClient().createStreamBulkProcessor(maxBulkSize, flushInterval, concurrency);
            this.measureBulkWriteProcessor = getClient().createMeasureBulkProcessor(maxBulkSize, flushInterval, concurrency);
        }

        if (CollectionUtils.isNotEmpty(prepareRequests)) {
            for (final PrepareRequest r : prepareRequests) {
                if (r instanceof BanyanDBStreamInsertRequest) {
                    // TODO: return CompletableFuture<Void>
                    this.streamBulkWriteProcessor.add(((BanyanDBStreamInsertRequest) r).getStreamWrite());
                } else if (r instanceof BanyanDBMeasureInsertRequest) {
                    this.measureBulkWriteProcessor.add(((BanyanDBMeasureInsertRequest) r).getMeasureWrite());
                } else if (r instanceof BanyanDBMeasureUpdateRequest) {
                    this.measureBulkWriteProcessor.add(((BanyanDBMeasureUpdateRequest) r).getMeasureWrite());
                }
            }
        }

        return CompletableFuture.completedFuture(null);
    }
}
