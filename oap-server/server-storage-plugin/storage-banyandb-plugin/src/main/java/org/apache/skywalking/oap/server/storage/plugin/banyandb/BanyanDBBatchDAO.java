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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.bulk.MeasureBulkWriteProcessor;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.bulk.StreamBulkWriteProcessor;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.bulk.TraceBulkWriteProcessor;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMeasureInsertRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.measure.BanyanDBMeasureUpdateRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBStreamInsertRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.trace.BanyanDBTraceInsertRequest;

public class BanyanDBBatchDAO extends AbstractDAO<BanyanDBStorageClient> implements IBatchDAO {
    private static final Object STREAM_SYNCHRONIZER = new Object();
    private static final Object MEASURE_SYNCHRONIZER = new Object();
    private static final Object TRACE_SYNCHRONIZER = new Object();
    private StreamBulkWriteProcessor streamBulkWriteProcessor;

    private MeasureBulkWriteProcessor measureBulkWriteProcessor;

    private TraceBulkWriteProcessor traceBulkWriteProcessor;

    private final int maxBulkSize;

    private final int flushInterval;

    private final int concurrency;

    public BanyanDBBatchDAO(BanyanDBStorageClient client, int maxBulkSize, int flushInterval, int concurrency) {
        super(client);
        this.maxBulkSize = maxBulkSize;
        this.flushInterval = flushInterval;
        this.concurrency = concurrency;
    }

    @Override
    public void insert(InsertRequest insertRequest) {
        if (insertRequest instanceof BanyanDBStreamInsertRequest) {
            getStreamBulkWriteProcessor().add(((BanyanDBStreamInsertRequest) insertRequest).getStreamWrite());
        } else if (insertRequest instanceof BanyanDBMeasureInsertRequest) {
            getMeasureBulkWriteProcessor().add(((BanyanDBMeasureInsertRequest) insertRequest).getMeasureWrite());
        } else  if (insertRequest instanceof BanyanDBTraceInsertRequest) {
            getTraceBulkWriteProcessor().add(((BanyanDBTraceInsertRequest) insertRequest).getTraceWrite());
        }
    }

    @Override
    public CompletableFuture<Void> flush(List<PrepareRequest> prepareRequests) {
        if (CollectionUtils.isNotEmpty(prepareRequests)) {
            return CompletableFuture.allOf(prepareRequests.stream().map((Function<PrepareRequest, CompletableFuture<Void>>) r -> {
                if (r instanceof BanyanDBStreamInsertRequest) {
                    return getStreamBulkWriteProcessor().add(((BanyanDBStreamInsertRequest) r).getStreamWrite());
                } else if (r instanceof BanyanDBMeasureInsertRequest) {
                    return getMeasureBulkWriteProcessor().add(((BanyanDBMeasureInsertRequest) r).getMeasureWrite())
                                                         .whenComplete((v, throwable) -> {
                                                             if (throwable == null) {
                                                                 // Insert completed
                                                                 ((BanyanDBMeasureInsertRequest) r).onInsertCompleted();
                                                             }
                                                         });
                } else if (r instanceof BanyanDBMeasureUpdateRequest) {
                    return getMeasureBulkWriteProcessor().add(((BanyanDBMeasureUpdateRequest) r).getMeasureWrite());
                } else  if (r instanceof BanyanDBTraceInsertRequest) {
                    return getTraceBulkWriteProcessor().add(((BanyanDBTraceInsertRequest) r).getTraceWrite());
                }
                return CompletableFuture.completedFuture(null);
            }).toArray(CompletableFuture[]::new));
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * The persistence timer has queued its round's measures: send them now. Left to the bulk's own schedule they
     * wait up to flushInterval, longer than the timer's own wait for their acknowledgement, and the round would
     * end before the session cache is filled. Streams and traces are not written by the timer and keep their
     * schedule. The flush returns when BanyanDB has answered.
     */
    @Override
    public void endOfFlush() {
        if (measureBulkWriteProcessor != null) {
            measureBulkWriteProcessor.flush();
        }
    }

    private StreamBulkWriteProcessor getStreamBulkWriteProcessor() {
        if (streamBulkWriteProcessor == null) {
            synchronized (STREAM_SYNCHRONIZER) {
                if (streamBulkWriteProcessor == null) {
                    this.streamBulkWriteProcessor = getClient().createStreamBulkProcessor(maxBulkSize, flushInterval, concurrency);
                }
            }
        }
        return streamBulkWriteProcessor;
    }

    private MeasureBulkWriteProcessor getMeasureBulkWriteProcessor() {
        if (measureBulkWriteProcessor == null) {
            synchronized (MEASURE_SYNCHRONIZER) {
                if (measureBulkWriteProcessor == null) {
                    this.measureBulkWriteProcessor = getClient().createMeasureBulkProcessor(maxBulkSize, flushInterval, concurrency);
                }
            }
        }
        return measureBulkWriteProcessor;
    }

    private TraceBulkWriteProcessor getTraceBulkWriteProcessor() {
        if (traceBulkWriteProcessor == null) {
            synchronized (TRACE_SYNCHRONIZER) {
                if (traceBulkWriteProcessor == null) {
                    this.traceBulkWriteProcessor = getClient().createTraceBulkProcessor(maxBulkSize, flushInterval, concurrency);
                }
            }
        }
        return traceBulkWriteProcessor;
    }
}
