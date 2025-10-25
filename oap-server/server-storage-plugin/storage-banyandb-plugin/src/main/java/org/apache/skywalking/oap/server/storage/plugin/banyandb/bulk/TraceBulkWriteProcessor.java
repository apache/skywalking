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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.bulk;

import io.grpc.stub.StreamObserver;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.annotation.concurrent.ThreadSafe;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.trace.v1.BanyandbTrace;
import org.apache.skywalking.banyandb.trace.v1.TraceServiceGrpc;
import org.apache.skywalking.banyandb.v1.client.BanyanDBClient;
import org.apache.skywalking.banyandb.v1.client.Options;
import org.apache.skywalking.banyandb.v1.client.grpc.exception.BanyanDBException;
import org.apache.skywalking.banyandb.v1.client.util.StatusUtil;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;

/**
 * TraceBulkWriteProcessor works for trace flush.
 */
@Slf4j
@ThreadSafe
public class TraceBulkWriteProcessor extends AbstractBulkWriteProcessor<BanyandbTrace.WriteRequest,
        TraceServiceGrpc.TraceServiceStub> {
    private final BanyanDBClient client;
    private final HistogramMetrics writeHistogram;
    private final Options options;

    /**
     * Create the processor.
     *
     * @param client        the client
     * @param maxBulkSize   the max bulk size for the flush operation
     * @param flushInterval if given maxBulkSize is not reached in this period, the flush would be trigger
     *                      automatically. Unit is second.
     * @param timeout       network timeout threshold in seconds.
     * @param concurrency   the number of concurrency would run for the flush max.
     */
    public TraceBulkWriteProcessor(
            final BanyanDBClient client,
            final int maxBulkSize,
            final int flushInterval,
            final int concurrency,
            final int timeout,
            final HistogramMetrics writeHistogram,
            final Options options) {
        super(client.getTraceServiceStub(), "TraceBulkWriteProcessor", maxBulkSize, flushInterval, concurrency, timeout);
        this.client = client;
        this.writeHistogram = writeHistogram;
        this.options = options;
    }

    @Override
    protected StreamObserver<BanyandbTrace.WriteRequest> buildStreamObserver(TraceServiceGrpc.TraceServiceStub stub, CompletableFuture<Void> batch) {
        return stub.write(
                new StreamObserver<BanyandbTrace.WriteResponse>() {
                    private final Set<String> schemaExpired = new HashSet<>();

                    @Override
                    public void onNext(BanyandbTrace.WriteResponse writeResponse) {
                        BanyandbModel.Status status = StatusUtil.convertStringToStatus(writeResponse.getStatus());
                        switch (status) {
                            case STATUS_SUCCEED:
                                break;
                            case STATUS_EXPIRED_SCHEMA:
                                BanyandbCommon.Metadata metadata = writeResponse.getMetadata();
                                String schemaKey = metadata.getGroup() + "." + metadata.getName();
                                if (!schemaExpired.contains(schemaKey)) {
                                    log.warn("The trace schema {} is expired, trying update the schema...", schemaKey);
                                    try {
                                        client.updateTraceMetadataCacheFromServer(metadata.getGroup(), metadata.getName());
                                        schemaExpired.add(schemaKey);
                                    } catch (BanyanDBException e) {
                                        log.error(e.getMessage(), e);
                                    }
                                }
                                break;
                            default:
                                log.warn("Write trace failed with status: {}", status);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        batch.completeExceptionally(t);
                        log.error("Error occurs in flushing traces", t);
                    }

                    @Override
                    public void onCompleted() {
                        batch.complete(null);
                    }
                });
    }

    @Override
    protected CompletableFuture<Void> doObservedFlush(final List<Holder> data) {
        HistogramMetrics.Timer timer = writeHistogram.createTimer();
        return super.doFlush(data, timer);
    }
}